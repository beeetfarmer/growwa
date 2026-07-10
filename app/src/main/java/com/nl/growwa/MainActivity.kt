package com.nl.growwa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.data.settings.ThemeMode
import com.nl.growwa.feature.home.LauncherRoot
import com.nl.growwa.feature.home.LauncherViewModel
import com.nl.growwa.ui.theme.GrowwaTheme

class MainActivity : ComponentActivity() {

    // Bumped whenever the launcher is (re)entered, so the UI can reset to the home screen.
    private var homeReset by mutableIntStateOf(0)

    private val container by lazy { (application as GrowwaApp).container }

    override fun onStart() {
        super.onStart()
        container.widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        container.widgetHost.stopListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsState(initial = LauncherSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Match status/nav bar icon contrast to the app's own theme, so a light drawer in
            // light mode gets dark (visible) icons even when the system is set to dark.
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            GrowwaTheme(
                darkTheme = dark,
                dynamicColor = false,
                accent = Color(settings.accent.argb),
            ) {
                val vm: LauncherViewModel = viewModel(
                    factory = LauncherViewModel.Factory(container),
                )
                LauncherRoot(
                    viewModel = vm,
                    resetSignal = homeReset,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Pressing Home while already in the launcher delivers a new HOME intent — collapse
    // overlays back to the home screen. We deliberately do NOT reset in onResume(), so that
    // returning from a system activity (e.g. Private Space unlock, App Info) keeps the drawer
    // open where the user left it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeReset++
    }
}
