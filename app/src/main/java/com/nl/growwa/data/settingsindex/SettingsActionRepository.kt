package com.nl.growwa.data.settingsindex

import android.provider.Settings
import com.nl.growwa.core.model.SettingAction

/**
 * Curated index of common system settings, searchable by name.
 * Android exposes no public global settings-search index, so we maintain this list.
 */
class SettingsActionRepository {

    private val all: List<SettingAction> = listOf(
        SettingAction("Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
        SettingAction("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingAction("Mobile network", Settings.ACTION_DATA_ROAMING_SETTINGS),
        SettingAction("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingAction("Display", Settings.ACTION_DISPLAY_SETTINGS),
        SettingAction("Sound", Settings.ACTION_SOUND_SETTINGS),
        SettingAction("Apps", Settings.ACTION_APPLICATION_SETTINGS),
        SettingAction("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        SettingAction("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingAction("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingAction("Security", Settings.ACTION_SECURITY_SETTINGS),
        SettingAction("Privacy", Settings.ACTION_PRIVACY_SETTINGS),
        SettingAction("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingAction("Date & time", Settings.ACTION_DATE_SETTINGS),
        SettingAction("Language & input", Settings.ACTION_LOCALE_SETTINGS),
        SettingAction("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        SettingAction("Home / default apps", Settings.ACTION_HOME_SETTINGS),
        SettingAction("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS),
        SettingAction("All settings", Settings.ACTION_SETTINGS),
        SettingAction("NFC", Settings.ACTION_NFC_SETTINGS),
        SettingAction("Data usage", Settings.ACTION_DATA_USAGE_SETTINGS),
        SettingAction("VPN", Settings.ACTION_VPN_SETTINGS),
        SettingAction("Print", Settings.ACTION_PRINT_SETTINGS),
    )

    fun search(query: String, limit: Int = 8): List<SettingAction> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        return all.filter { it.label.contains(q, ignoreCase = true) }.take(limit)
    }
}
