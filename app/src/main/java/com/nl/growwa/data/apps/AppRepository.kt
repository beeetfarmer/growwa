package com.nl.growwa.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.AppKey
import com.nl.growwa.core.toImageBitmap
import com.nl.growwa.data.iconpack.IconPackRepository
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

/**
 * Single source of truth for installed launchable apps across all user profiles.
 * Backed by the LauncherApps system service; refreshes live on package changes.
 */
class AppRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val iconPackRepository: IconPackRepository,
    private val settingsRepository: SettingsRepository,
) {
    @Volatile
    private var iconPackPkg: String = ""
    private val appContext = context.applicationContext
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
    }

    private fun userSerial(user: UserHandle): Long =
        userManager.getSerialNumberForUser(user)

    suspend fun refresh() = withContext(Dispatchers.Default) {
        val pack = iconPackPkg.takeIf { it.isNotBlank() }?.let { iconPackRepository.load(it) }
        val profiles = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
        val result = mutableListOf<AppInfo>()
        for (user in profiles) {
            val activities = runCatching { launcherApps.getActivityList(null, user) }
                .getOrDefault(emptyList())
            for (activity in activities) {
                val themed = pack?.iconFor(activity.componentName, iconSizePx)
                val icon = themed ?: runCatching {
                    activity.getBadgedIcon(0).toImageBitmap(iconSizePx)
                }.getOrNull() ?: continue
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

    /** Open the system App Info screen for an app. */
    fun openAppInfo(app: AppInfo) {
        runCatching {
            launcherApps.startAppDetailsActivity(app.componentName, app.userHandle, null, null)
        }
    }

    fun resolve(key: AppKey): AppInfo? = _apps.value.firstOrNull { it.key == key }
}
