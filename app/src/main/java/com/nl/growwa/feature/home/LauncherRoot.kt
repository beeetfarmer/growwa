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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import android.content.Intent
import android.provider.Settings
import com.nl.growwa.data.settings.GestureAction
import com.nl.growwa.feature.drawer.AppDrawer
import com.nl.growwa.feature.finder.FinderOverlay
import com.nl.growwa.feature.gestures.GrowwaAccessibilityService
import com.nl.growwa.feature.settings.SettingsScreen
import com.nl.growwa.feature.widgets.WidgetPicker

/** Overlay states stacked above the home surface. */
private enum class Overlay { NONE, DRAWER, FINDER, SETTINGS, WIDGETS }

@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    resetSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var editing by remember { mutableStateOf(false) }
    // Live state for an app being dragged out of the drawer onto home/dock.
    val drawerDrag = remember { DrawerDragState() }

    // Collapse overlays back to home whenever the launcher is re-entered.
    androidx.compose.runtime.LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            overlay = Overlay.NONE
            editing = false
        }
    }
    var showA11yPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val editingNow by rememberUpdatedState(editing)
    BackHandler(enabled = overlay != Overlay.NONE || editing) {
        if (overlay != Overlay.NONE) overlay = Overlay.NONE else editing = false
    }

    // A drag out of the drawer hides it; the home screen underneath must stay sharp.
    val placing = drawerDrag.app != null
    val blurWallpaper = !placing && (overlay == Overlay.FINDER || overlay == Overlay.DRAWER || editing)
    val blurHome = !placing && (overlay == Overlay.FINDER || overlay == Overlay.DRAWER)
    WallpaperBlur(enabled = blurWallpaper)

    Box(modifier.fillMaxSize()) {
        // Home surface with swipe-up (drawer) / swipe-down (finder) detection.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (blurHome) Modifier.blur(28.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    else Modifier,
                )
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (editingNow) return@detectVerticalDragGestures
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
                        onLongPress = { editing = true },
                        onDoubleTap = {
                            if (!editingNow) performGesture(currentSettings.gestureDoubleTap)
                        },
                    )
                },
        ) {
            HomeScreen(
                pagerState = homePager,
                settings = ui.settings,
                layout = ui.layout,
                apps = ui.mainApps,
                onLaunch = viewModel::launch,
                onAddToDock = viewModel::addToDock,
                onAppInfo = viewModel::openAppInfo,
                onRemoveFromHome = viewModel::removeFromHome,
                onRemoveFromDock = viewModel::removeFromDock,
                onDrop = viewModel::onDrop,
                onRenameFolder = viewModel::renameFolder,
                onTakeOutOfFolder = viewModel::takeOutOfFolder,
                onDeleteFolder = viewModel::deleteFolder,
                widgetHost = viewModel.widgetHost,
                onRemoveWidget = viewModel::removeWidget,
                onResizeWidget = viewModel::resizeWidget,
                drawerDrag = drawerDrag,
                onExternalDrop = { app, slot -> viewModel.dropFromDrawer(app, slot) },
                onExternalDragEnd = {
                    drawerDrag.app = null
                    drawerDrag.release = 0
                    overlay = Overlay.NONE
                },
                onSidebarAnchor = { v -> viewModel.updateSettings { it.copy(sidebarAnchor = v) } },
                editing = editing,
            )
        }

        if (editing && overlay == Overlay.NONE) {
            HomeEditChrome(
                pageCount = ui.layout.pageCount.coerceAtLeast(1),
                currentPage = homePager.currentPage,
                layout = ui.layout,
                columns = ui.settings.homeColumns,
                rows = ui.settings.homeRows,
                onSelectPage = { page -> scope.launch { homePager.animateScrollToPage(page) } },
                onReorderPages = { from, to ->
                    val max = ui.layout.pageCount - 1
                    if (from !in 0..max || to !in 0..max || from == to) return@HomeEditChrome
                    val order = (0..max).toMutableList()
                    val moved = order.removeAt(from)
                    order.add(to.coerceIn(0, order.size), moved)
                    val stay = order.indexOf(homePager.currentPage).coerceAtLeast(0)
                    viewModel.reorderHomePage(from, to)
                    scope.launch { homePager.scrollToPage(stay) }
                },
                onWallpapers = {
                    editing = false
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onSystemStyle = { editing = false; overlay = Overlay.SETTINGS },
                onWidgets = { editing = false; overlay = Overlay.WIDGETS },
                onSettings = { editing = false; overlay = Overlay.SETTINGS },
            )
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
                    // Visible only for a normal open. While an app is being dragged out (app != null)
                    // or the drawer is sliding away after a drop (overlay != DRAWER) it stays
                    // transparent — but composed, so the long-press drag keeps receiving events.
                    .alpha(if (overlay == Overlay.DRAWER && drawerDrag.app == null) 1f else 0f),
                // Pull-to-dismiss is handled inside AppDrawer (nested-scroll on the grid) so it
                // doesn't swallow the grid's own vertical scrolling.
            ) {
                AppDrawer(
                    apps = ui.mainApps,
                    suggestions = ui.topApps.ifEmpty {
                        ui.mainApps.take(ui.settings.suggestionCount)
                    },
                    privateApps = ui.privateApps,
                    privateSpace = ui.privateSpace,
                    settings = ui.settings,
                    onLaunch = { viewModel.launch(it); overlay = Overlay.NONE },
                    onUninstall = viewModel::uninstall,
                    onAddToDock = viewModel::addToDock,
                    onAppInfo = viewModel::openAppInfo,
                    onLockPrivate = viewModel::lockPrivateSpace,
                    onUnlockPrivate = viewModel::unlockPrivateSpace,
                    onDragStart = { app, p -> drawerDrag.app = app; drawerDrag.pointer = p },
                    onDragMove = { drawerDrag.pointer = it },
                    onDragEnd = { drawerDrag.release++ },
                    loadShortcuts = viewModel::appShortcuts,
                    onLaunchShortcut = { app, shortcut ->
                        viewModel.launchShortcut(app, shortcut)
                        overlay = Overlay.NONE
                    },
                    onDismiss = { overlay = Overlay.NONE },
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
                recentSearches = ui.settings.recentSearches,
                searchApps = viewModel::searchApps,
                searchSettings = viewModel::searchSettings,
                searchContacts = viewModel::searchContacts,
                searchFiles = viewModel::searchFiles,
                fileAccessGranted = viewModel::fileAccessGranted,
                onLaunchApp = viewModel::launch,
                onRememberSearch = viewModel::rememberFinderQuery,
                onClearSearches = viewModel::clearFinderQueries,
                onOpenSettings = { overlay = Overlay.SETTINGS },
                onDismiss = { overlay = Overlay.NONE },
                suggestionCount = ui.settings.suggestionCount,
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
                apps = ui.mainApps,
                onUpdate = viewModel::updateSettings,
                loadIconPacks = viewModel::loadIconPacks,
                privateSpaceAvailable = ui.privateSpace.available,
                onClose = { overlay = Overlay.NONE },
            )
        }

        // Widget picker (modal dialog).
        if (overlay == Overlay.WIDGETS) {
            WidgetPicker(
                widgetHost = viewModel.widgetHost,
                onPlace = { provider, id -> viewModel.addWidget(provider, id, homePager.currentPage) },
                onDismiss = { overlay = Overlay.NONE },
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
