package com.nl.growwa.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.AppKey
import com.nl.growwa.core.model.AppShortcut
import com.nl.growwa.core.toImageBitmap
import com.nl.growwa.data.iconpack.IconPackRepository
import com.nl.growwa.data.privatespace.PrivateSpaceManager
import com.nl.growwa.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Single source of truth for installed launchable apps across all user profiles.
 * Backed by the LauncherApps system service; refreshes live on package changes.
 */
class AppRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val iconPackRepository: IconPackRepository,
    private val settingsRepository: SettingsRepository,
    private val privateSpaceManager: PrivateSpaceManager,
) {
    @Volatile
    private var iconPackPkg: String = ""
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager

    /** Standard launcher icon size in px. */
    private val iconSizePx: Int =
        appContext.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            .coerceAtLeast(96)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    /** Emits whenever any package is installed/removed/changed for any profile. */
    private val packageEvents: Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(p0: Array<out String>?, p1: UserHandle?, p2: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(p0: Array<out String>?, p1: UserHandle?, p2: Boolean) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    init {
        // Reload whenever the selected icon pack changes (also fires the initial load).
        scope.launch {
            settingsRepository.settings
                .map { it.iconPack }
                .distinctUntilChanged()
                .collect { pkg ->
                    iconPackPkg = pkg
                    refresh()
                }
        }
        scope.launch {
            packageEvents.collect { refresh() }
        }
        // Reload when Private Space is locked/unlocked or added/removed.
        privateSpaceManager.addChangeListener { scope.launch { refresh() } }
    }

    private fun userSerial(user: UserHandle): Long =
        userManager.getSerialNumberForUser(user)

    suspend fun refresh() = withContext(Dispatchers.Default) {
        val pack = iconPackPkg.takeIf { it.isNotBlank() }?.let { iconPackRepository.load(it) }
        val privateUser = privateSpaceManager.privateUser()
        val profiles = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
        val result = mutableListOf<AppInfo>()
        for (user in profiles) {
            val isPrivateProfile = privateUser != null && user == privateUser
            val activities = runCatching { launcherApps.getActivityList(null, user) }
                .getOrDefault(emptyList())
            for (activity in activities) {
                // Themed (icon pack) drawable if available; badge it for private/work profiles
                // since the system badge isn't applied to pack icons. Fall back to the system's
                // already-badged activity icon when the pack doesn't theme this app.
                val themedDrawable = pack?.drawableFor(activity.componentName)
                val icon = if (themedDrawable != null) {
                    val badged =
                        if (isPrivateProfile) pm.getUserBadgedIcon(themedDrawable, user) else themedDrawable
                    runCatching { badged.toImageBitmap(iconSizePx) }.getOrNull()
                } else {
                    runCatching { activity.getBadgedIcon(0).toImageBitmap(iconSizePx) }.getOrNull()
                } ?: continue
                result += AppInfo(
                    key = AppKey(
                        packageName = activity.applicationInfo.packageName,
                        className = activity.componentName.className,
                        userSerial = userSerial(user),
                    ),
                    label = activity.label?.toString().orEmpty(),
                    componentName = activity.componentName,
                    userHandle = user,
                    icon = icon,
                    isPrivate = isPrivateProfile,
                )
            }
        }
        result.sortBy { it.label.lowercase() }
        _apps.value = result
    }

    /** Launch an app for its specific profile. */
    fun launch(app: AppInfo, sourceBounds: android.graphics.Rect? = null) {
        runCatching {
            launcherApps.startMainActivity(
                app.componentName, app.userHandle, sourceBounds, null
            )
        }
    }

    /** Launch the system uninstall flow for an app. */
    fun uninstall(app: AppInfo) {
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.key.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Open the system App Info screen for an app. */
    fun openAppInfo(app: AppInfo) {
        runCatching {
            launcherApps.startAppDetailsActivity(app.componentName, app.userHandle, null, null)
        }
    }

    /**
     * Shortcuts shown on long-press. The system list is only available to the default launcher,
     * so static shortcuts are also read from the app's own manifest.
     */
    fun shortcuts(app: AppInfo): List<AppShortcut> {
        if (launcherApps.hasShortcutHostPermission()) {
            val hosted = hostShortcuts(app)
            if (hosted.isNotEmpty()) return hosted
        }
        return manifestShortcuts(app)
    }

    private fun hostShortcuts(app: AppInfo): List<AppShortcut> {
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(app.packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        }
        val found = runCatching { launcherApps.getShortcuts(query, app.userHandle) }.getOrNull().orEmpty()
        return found.sortedBy { it.rank }.take(6).map { shortcut ->
            val icon = runCatching {
                launcherApps.getShortcutIconDrawable(shortcut, 0)?.toImageBitmap(iconSizePx)
            }.getOrNull()
            AppShortcut(shortcut.id, shortcut.shortLabel?.toString().orEmpty(), icon)
        }
    }

    fun launchShortcut(app: AppInfo, shortcut: AppShortcut) {
        val pending = shortcut.intent
        if (pending != null) {
            runCatching { appContext.startActivity(Intent(pending).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching {
            launcherApps.startShortcut(app.packageName, shortcut.id, null, null, app.userHandle)
        }
    }

    private fun manifestShortcuts(app: AppInfo): List<AppShortcut> {
        val resId = shortcutXmlId(app) ?: return emptyList()
        val resources = runCatching { pm.getResourcesForApplication(app.packageName) }.getOrNull()
            ?: return emptyList()
        val parser = runCatching { resources.getXml(resId) }.getOrNull() ?: return emptyList()
        return runCatching { readShortcutXml(parser, resources, app.packageName) }.getOrDefault(emptyList())
            .also { runCatching { parser.close() } }
    }

    private fun shortcutXmlId(app: AppInfo): Int? {
        fun idOf(meta: android.os.Bundle?) = meta?.getInt("android.app.shortcuts")?.takeIf { it != 0 }
        val direct = runCatching {
            pm.getActivityInfo(app.componentName, PackageManager.GET_META_DATA)
        }.getOrNull()
        idOf(direct?.metaData)?.let { return it }
        val probe = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(app.packageName)
        val activities = runCatching {
            pm.queryIntentActivities(probe, PackageManager.GET_META_DATA)
        }.getOrNull().orEmpty()
        return activities.firstNotNullOfOrNull { idOf(it.activityInfo.metaData) }
    }

    private fun readShortcutXml(
        parser: XmlResourceParser,
        resources: Resources,
        pkg: String,
    ): List<AppShortcut> {
        val out = ArrayList<AppShortcut>(4)
        while (parser.next() != XmlPullParser.END_DOCUMENT && out.size < 6) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "shortcut") continue
            val enabled = parser.getAttributeBooleanValue(ANDROID_NS, "enabled", true)
            val id = xmlAttr(parser, "shortcutId").orEmpty()
            val label = xmlText(parser, resources, "shortcutShortLabel").ifBlank {
                xmlText(parser, resources, "shortcutLongLabel")
            }
            val icon = xmlRes(parser, "icon").takeIf { it != 0 }?.let { res ->
                runCatching { resources.getDrawable(res, null).toImageBitmap(iconSizePx) }.getOrNull()
            }
            val intent = shortcutIntent(parser, pkg)
            if (enabled && label.isNotBlank() && intent != null) {
                out += AppShortcut(id.ifBlank { label }, label, icon, intent)
            }
        }
        return out
    }

    /** Reads the shortcut's destination. The last nested intent is the one that opens. */
    private fun shortcutIntent(parser: XmlResourceParser, pkg: String): Intent? {
        var intent: Intent? = null
        val depth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                intent = intentFromShortcut(parser, pkg) ?: intent
            }
        }
        return intent
    }

    private fun intentFromShortcut(parser: XmlPullParser, pkg: String): Intent? {
        val action = xmlAttr(parser, "action")
        val data = xmlAttr(parser, "data")
        val targetPkg = xmlAttr(parser, "targetPackage") ?: pkg
        val targetClass = xmlAttr(parser, "targetClass")
        if (action.isNullOrBlank() && targetClass.isNullOrBlank() && data.isNullOrBlank()) return null
        return Intent(action).apply {
            if (!data.isNullOrBlank()) this.data = Uri.parse(data)
            if (!targetClass.isNullOrBlank()) setClassName(targetPkg, targetClass) else setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun xmlAttr(parser: XmlPullParser, name: String): String? =
        parser.getAttributeValue(ANDROID_NS, name) ?: parser.getAttributeValue(null, name)

    private fun xmlRes(parser: XmlResourceParser, name: String): Int {
        val id = parser.getAttributeResourceValue(ANDROID_NS, name, 0)
        return if (id != 0) id else parser.getAttributeResourceValue(null, name, 0)
    }

    private fun xmlText(parser: XmlResourceParser, resources: Resources, name: String): String {
        val id = xmlRes(parser, name)
        if (id != 0) return runCatching { resources.getString(id) }.getOrDefault("")
        return xmlAttr(parser, name).orEmpty()
    }

    fun resolve(key: AppKey): AppInfo? = _apps.value.firstOrNull { it.key == key }
}
