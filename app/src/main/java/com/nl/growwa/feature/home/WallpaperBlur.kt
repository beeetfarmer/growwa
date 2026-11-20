package com.nl.growwa.feature.home

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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

/** Ink for labels that sit on the wallpaper. Provided around the home screen. */
val LocalWallpaperInk = compositionLocalOf { Color.White }

/** 0 is black, 1 is white. Missing wallpaper info stays dark so labels stay white. */
@Composable
fun rememberWallpaperLuminance(): Float {
    val context = LocalContext.current
    var luminance by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val manager = WallpaperManager.getInstance(context)
        fun read(): Float {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return 0f
            val colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hints = colors.colorHints
                if (hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) return 1f
                if (hints and WallpaperColors.HINT_SUPPORTS_DARK_THEME != 0) return 0f
            }
            return Color(colors.primaryColor.toArgb()).luminance()
        }
        luminance = read()
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> luminance = read() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                manager.removeOnColorsChangedListener(listener)
            }
        }
    }
    return luminance
}

/** Dark ink on a light background, white ink on a dark one. */
fun inkOn(luminance: Float): Color =
    if (luminance > 0.4f) Color(0xFF1C1C1E) else Color.White
