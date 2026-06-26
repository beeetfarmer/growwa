package com.nl.growwa.data.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.res.ResourcesCompat
import com.nl.growwa.core.model.IconPackInfo
import com.nl.growwa.core.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** A parsed, loaded icon pack: component -> drawable-name map + the pack's resources. */
class LoadedIconPack(
    val packageName: String,
    private val resources: Resources,
    private val componentToDrawable: Map<String, String>,
) {
    /** Themed [Drawable] for [component], or null if the pack doesn't theme it. */
    fun drawableFor(component: ComponentName): Drawable? {
        val key = "ComponentInfo{${component.packageName}/${component.className}}"
        val drawableName = componentToDrawable[key] ?: return null
        val id = resources.getIdentifier(drawableName, "drawable", packageName)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(resources, id, null) }.getOrNull()
    }
}

/**
 * Detects installed icon packs (Nova/ADW format) and parses their appfilter.xml.
 * Loaded packs are cached so repeated icon lookups are cheap.
 */
class IconPackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val previewSizePx = 144

    @Volatile
    private var cache: LoadedIconPack? = null

    private val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "app.lawnchair.icons.THEMED_ICON",
    )

    /** All installed icon packs, de-duplicated by package, sorted by label. */
    suspend fun availablePacks(): List<IconPackInfo> = withContext(Dispatchers.Default) {
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (action in themeActions) {
            val infos = runCatching {
                pm.queryIntentActivities(Intent(action), 0)
            }.getOrDefault(emptyList())
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (seen.containsKey(pkg)) continue
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                val icon = runCatching { ri.loadIcon(pm).toImageBitmap(previewSizePx) }.getOrNull()
                seen[pkg] = IconPackInfo(pkg, label, icon)
            }
        }
        seen.values.sortedBy { it.label.lowercase() }
    }

    /** Load (and cache) a pack's appfilter map. Returns null if it can't be read. */
    suspend fun load(packageName: String): LoadedIconPack? = withContext(Dispatchers.IO) {
        cache?.let { if (it.packageName == packageName) return@withContext it }
        val res = runCatching { pm.getResourcesForApplication(packageName) }.getOrNull()
            ?: return@withContext null
        val map = parseAppFilter(res, packageName)
        LoadedIconPack(packageName, res, map).also { cache = it }
    }

    private fun parseAppFilter(res: Resources, pkg: String): Map<String, String> {
        val parser: XmlPullParser = openAppFilter(res, pkg) ?: return emptyMap()
        val map = HashMap<String, String>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                        map[component] = drawable
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Tolerate malformed packs; keep whatever parsed so far.
        }
        return map
    }

    private fun openAppFilter(res: Resources, pkg: String): XmlPullParser? {
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        if (xmlId != 0) return runCatching { res.getXml(xmlId) }.getOrNull()
        // Fallback: some packs ship appfilter.xml in assets.
        return runCatching {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(res.assets.open("appfilter.xml"), "UTF-8")
            parser
        }.getOrNull()
    }
}
