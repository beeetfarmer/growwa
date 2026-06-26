package com.nl.growwa.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import android.content.Intent
import android.provider.Settings
import com.nl.growwa.data.settings.GestureAction
import com.nl.growwa.feature.drawer.AppDrawer
import com.nl.growwa.feature.finder.FinderOverlay
import com.nl.growwa.feature.gestures.GrowwaAccessibilityService
import com.nl.growwa.feature.settings.SettingsScreen

/** Overlay states stacked above the home surface. */
private enum class Overlay { NONE, DRAWER, FINDER, SETTINGS }

@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    resetSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var homeMenu by remember { mutableStateOf(false) }

    // Collapse overlays back to home whenever the launcher is re-entered.
    androidx.compose.runtime.LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            overlay = Overlay.NONE
            homeMenu = false
        }
    }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }
    var showA11yPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current

    val homePager = rememberPagerState(
        pageCount = { ui.layout.pageCount.coerceAtLeast(1) },
    )

    // Read latest via rememberUpdatedState so the long-lived pointerInput closures, which
    // capture state once, always see current settings and dispatch logic.
    val currentSettings by rememberUpdatedState(ui.settings)
    val performGesture by rememberUpdatedState<(GestureAction) -> Unit> { action ->
        when (action) {
            GestureAction.NONE -> {}
            GestureAction.APP_DRAWER -> overlay = Overlay.DRAWER
            GestureAction.FINDER -> overlay = Overlay.FINDER
            GestureAction.LAUNCHER_SETTINGS -> overlay = Overlay.SETTINGS
            GestureAction.NOTIFICATIONS ->
                if (!GrowwaAccessibilityService.expandNotifications()) showA11yPrompt = true
            GestureAction.LOCK_SCREEN ->
                if (!GrowwaAccessibilityService.lockScreen()) showA11yPrompt = true
        }
    }

    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }

    Box(modifier.fillMaxSize()) {
        // Home surface with swipe-up (drawer) / swipe-down (finder) detection.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total < -SWIPE_THRESHOLD -> performGesture(currentSettings.gestureSwipeUp)
                                total > SWIPE_THRESHOLD -> performGesture(currentSettings.gestureSwipeDown)
                            }
                        },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            menuOffset = offset
                            homeMenu = true
                        },
                        onDoubleTap = { performGesture(currentSettings.gestureDoubleTap) },
                    )
                },
        ) {
            HomeScreen(
                pagerState = homePager,
                settings = ui.settings,
                layout = ui.layout,
                onLaunch = viewModel::launch,
                onAddToDock = viewModel::addToDock,
                onAppInfo = viewModel::openAppInfo,
                onRemoveFromHome = viewModel::removeFromHome,
                onRemoveFromDock = viewModel::removeFromDock,
            )

            // Long-press-on-home popup.
            DropdownMenu(
                expanded = homeMenu,
                onDismissRequest = { homeMenu = false },
                offset = with(density) { DpOffset(menuOffset.x.toDp(), menuOffset.y.toDp()) },
            ) {
                DropdownMenuItem(
                    text = { Text("Launcher settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { homeMenu = false; overlay = Overlay.SETTINGS },
                )
                DropdownMenuItem(
                    text = { Text("Wallpaper") },
                    leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                    onClick = {
                        homeMenu = false
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SET_WALLPAPER)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }
        }

        // App drawer (slides up from bottom).
        AnimatedVisibility(
            visible = overlay == Overlay.DRAWER,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > SWIPE_THRESHOLD) overlay = Overlay.NONE },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    },
            ) {
                AppDrawer(
                    apps = ui.allApps,
                    settings = ui.settings,
                    onLaunch = { viewModel.launch(it); overlay = Overlay.NONE },
                    onAddToHome = { viewModel.addToHome(it, homePager.currentPage) },
                    onAddToDock = viewModel::addToDock,
                    onAppInfo = viewModel::openAppInfo,
                )
            }
        }

        // Finder search (fades in from top swipe).
        AnimatedVisibility(
            visible = overlay == Overlay.FINDER,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 4 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 4 }),
        ) {
            FinderOverlay(
                topApps = ui.topApps,
                searchApps = viewModel::searchApps,
                searchSettings = viewModel::searchSettings,
                searchContacts = viewModel::searchContacts,
                onLaunchApp = viewModel::launch,
                onDismiss = { overlay = Overlay.NONE },
            )
        }

        // Launcher settings.
        AnimatedVisibility(
            visible = overlay == Overlay.SETTINGS,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 }),
        ) {
            SettingsScreen(
                settings = ui.settings,
                onUpdate = viewModel::updateSettings,
                loadIconPacks = viewModel::loadIconPacks,
                onClose = { overlay = Overlay.NONE },
            )
        }

        // Prompt to enable the accessibility service when a gesture needs it but it's off.
        if (showA11yPrompt) {
            AlertDialog(
                onDismissRequest = { showA11yPrompt = false },
                title = { Text("Enable accessibility service") },
                text = {
                    Text(
                        "This gesture needs Growwa's accessibility service to open the " +
                            "notification shade or lock the screen. Enable it in system settings.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showA11yPrompt = false
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text("Open settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showA11yPrompt = false }) { Text("Not now") }
                },
            )
        }
    }
}

private const val SWIPE_THRESHOLD = 90f
