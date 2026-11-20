package com.nl.growwa.feature.home

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Blurs the wallpaper behind this window. The launcher window is already translucent and
 * shows the wallpaper, so this is the platform blur rather than a second wallpaper bitmap.
 * No-op below Android 12, where the caller should keep a dim scrim.
 */
@Composable
fun WallpaperBlur(enabled: Boolean, radius: Int = 80) {
    val view = LocalView.current
    DisposableEffect(enabled, radius) {
        val window = (view.context as? Activity)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(if (enabled) radius else 0)
            if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.setBackgroundBlurRadius(0)
                window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}
