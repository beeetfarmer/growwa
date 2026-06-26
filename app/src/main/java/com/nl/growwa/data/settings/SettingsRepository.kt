package com.nl.growwa.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("growwa_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Placement of the search bar inside the app drawer. */
enum class DrawerSearch { OFF, TOP, BOTTOM }

/** Action a home-screen gesture can trigger. */
enum class GestureAction(val label: String) {
    NONE("Nothing"),
    APP_DRAWER("App drawer"),
    FINDER("Finder search"),
    LAUNCHER_SETTINGS("Launcher settings"),
    NOTIFICATIONS("Notification shade"),
    LOCK_SCREEN("Lock screen"),
}

/** Preset accent colors (ARGB) applied to the Material color scheme. */
enum class AccentColor(val argb: Long) {
    BLUE(0xFF3B82F6),
    PURPLE(0xFF8B5CF6),
    GREEN(0xFF22C55E),
    TEAL(0xFF14B8A6),
    ORANGE(0xFFF97316),
    RED(0xFFEF4444),
    PINK(0xFFEC4899),
}

/** User-tunable launcher settings (defaults match One UI feel). */
data class LauncherSettings(
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val dockCount: Int = 5,
    val drawerColumns: Int = 4,
    val drawerRows: Int = 5,
    val iconSizeDp: Float = 52f,
    val labelSizeSp: Float = 12f,
    val showHomeLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val drawerSearch: DrawerSearch = DrawerSearch.TOP,
    val drawerTransparency: Float = 0f, // 0f = fully opaque, 1f = fully transparent
    val accent: AccentColor = AccentColor.BLUE,
    val iconPack: String = "", // package name of selected icon pack; "" = system default
    val gestureSwipeUp: GestureAction = GestureAction.APP_DRAWER,
    val gestureSwipeDown: GestureAction = GestureAction.FINDER,
    val gestureDoubleTap: GestureAction = GestureAction.LOCK_SCREEN,
) {
    companion object {
        const val MAX_DOCK = 7
        const val MAX_COLUMNS = 7
        const val MIN_COLUMNS = 3
        const val MAX_ROWS = 8
        const val MIN_ROWS = 3
        const val MIN_ICON_DP = 36f
        const val MAX_ICON_DP = 72f
        const val MIN_LABEL_SP = 9f
        const val MAX_LABEL_SP = 16f
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val homeColumns = intPreferencesKey("home_columns")
        val homeRows = intPreferencesKey("home_rows")
        val dockCount = intPreferencesKey("dock_count")
        val drawerColumns = intPreferencesKey("drawer_columns")
        val drawerRows = intPreferencesKey("drawer_rows")
        val iconSizeDp = floatPreferencesKey("icon_size_dp")
        val labelSizeSp = floatPreferencesKey("label_size_sp")
        val showHomeLabels = booleanPreferencesKey("show_home_labels")
        val showDrawerLabels = booleanPreferencesKey("show_drawer_labels")
        val themeMode = intPreferencesKey("theme_mode")
        val drawerSearch = intPreferencesKey("drawer_search")
        val drawerTransparency = floatPreferencesKey("drawer_transparency")
        val accent = intPreferencesKey("accent")
        val iconPack = stringPreferencesKey("icon_pack")
        val gestureSwipeUp = intPreferencesKey("gesture_swipe_up")
        val gestureSwipeDown = intPreferencesKey("gesture_swipe_down")
        val gestureDoubleTap = intPreferencesKey("gesture_double_tap")
    }

    private fun read(p: Preferences): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            homeColumns = (p[Keys.homeColumns] ?: d.homeColumns)
                .coerceIn(LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS),
            homeRows = (p[Keys.homeRows] ?: d.homeRows)
                .coerceIn(LauncherSettings.MIN_ROWS, LauncherSettings.MAX_ROWS),
            dockCount = (p[Keys.dockCount] ?: d.dockCount).coerceIn(1, LauncherSettings.MAX_DOCK),
            drawerColumns = (p[Keys.drawerColumns] ?: d.drawerColumns)
                .coerceIn(LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS),
            drawerRows = (p[Keys.drawerRows] ?: d.drawerRows)
                .coerceIn(LauncherSettings.MIN_ROWS, LauncherSettings.MAX_ROWS),
            iconSizeDp = (p[Keys.iconSizeDp] ?: d.iconSizeDp)
                .coerceIn(LauncherSettings.MIN_ICON_DP, LauncherSettings.MAX_ICON_DP),
            labelSizeSp = (p[Keys.labelSizeSp] ?: d.labelSizeSp)
                .coerceIn(LauncherSettings.MIN_LABEL_SP, LauncherSettings.MAX_LABEL_SP),
            showHomeLabels = p[Keys.showHomeLabels] ?: d.showHomeLabels,
            showDrawerLabels = p[Keys.showDrawerLabels] ?: d.showDrawerLabels,
            themeMode = ThemeMode.entries.getOrElse(p[Keys.themeMode] ?: 0) { ThemeMode.SYSTEM },
            drawerSearch = DrawerSearch.entries.getOrElse(p[Keys.drawerSearch] ?: 1) { DrawerSearch.TOP },
            drawerTransparency = (p[Keys.drawerTransparency] ?: d.drawerTransparency).coerceIn(0f, 1f),
            accent = AccentColor.entries.getOrElse(p[Keys.accent] ?: 0) { AccentColor.BLUE },
            iconPack = p[Keys.iconPack] ?: d.iconPack,
            gestureSwipeUp = GestureAction.entries
                .getOrElse(p[Keys.gestureSwipeUp] ?: d.gestureSwipeUp.ordinal) { d.gestureSwipeUp },
            gestureSwipeDown = GestureAction.entries
                .getOrElse(p[Keys.gestureSwipeDown] ?: d.gestureSwipeDown.ordinal) { d.gestureSwipeDown },
            gestureDoubleTap = GestureAction.entries
                .getOrElse(p[Keys.gestureDoubleTap] ?: d.gestureDoubleTap.ordinal) { d.gestureDoubleTap },
        )
    }

    val settings: Flow<LauncherSettings> = context.settingsDataStore.data.map(::read)

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { p ->
            val next = transform(read(p))
            p[Keys.homeColumns] = next.homeColumns
            p[Keys.homeRows] = next.homeRows
            p[Keys.dockCount] = next.dockCount
            p[Keys.drawerColumns] = next.drawerColumns
            p[Keys.drawerRows] = next.drawerRows
            p[Keys.iconSizeDp] = next.iconSizeDp
            p[Keys.labelSizeSp] = next.labelSizeSp
            p[Keys.showHomeLabels] = next.showHomeLabels
            p[Keys.showDrawerLabels] = next.showDrawerLabels
            p[Keys.themeMode] = next.themeMode.ordinal
            p[Keys.drawerSearch] = next.drawerSearch.ordinal
            p[Keys.drawerTransparency] = next.drawerTransparency
            p[Keys.accent] = next.accent.ordinal
            p[Keys.iconPack] = next.iconPack
            p[Keys.gestureSwipeUp] = next.gestureSwipeUp.ordinal
            p[Keys.gestureSwipeDown] = next.gestureSwipeDown.ordinal
            p[Keys.gestureDoubleTap] = next.gestureDoubleTap.ordinal
        }
    }
}
