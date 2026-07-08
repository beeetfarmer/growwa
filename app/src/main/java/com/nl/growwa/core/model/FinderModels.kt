package com.nl.growwa.core.model

/** A contact match in Finder results. */
data class ContactInfo(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
)

/** A system-settings shortcut in Finder results (e.g. Wi-Fi, Bluetooth). */
data class SettingAction(
    val label: String,
    val action: String,
)

/** A file or folder match in Finder results. */
data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,          // bytes; 0 for folders
    val lastModified: Long,
)

/** Bundled, categorized Finder results. */
data class FinderResults(
    val apps: List<AppInfo> = emptyList(),
    val contacts: List<ContactInfo> = emptyList(),
    val settings: List<SettingAction> = emptyList(),
) {
    val isEmpty: Boolean get() = apps.isEmpty() && contacts.isEmpty() && settings.isEmpty()
}
