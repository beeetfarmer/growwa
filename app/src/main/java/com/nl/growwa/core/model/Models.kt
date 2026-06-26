package com.nl.growwa.core.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Uniquely identifies a launchable activity for a given user profile.
 * userSerial distinguishes main / work / private-space copies of the same app.
 */
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
)

/** A launchable app as surfaced by LauncherApps, with its rendered icon. */
data class AppInfo(
    val key: AppKey,
    val label: String,
    val componentName: ComponentName,
    val userHandle: UserHandle,
    val icon: ImageBitmap,
) {
    val packageName: String get() = key.packageName
}

/** Where an app is placed on the home surface. */
enum class Container { HOME, DOCK }

/** A resolved placement (app + slot) ready for the UI. */
data class Placement(
    val app: AppInfo,
    val container: Container,
    val page: Int,
    val position: Int,
)
