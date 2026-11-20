package com.nl.growwa.feature.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.data.privatespace.PrivateSpaceState
import com.nl.growwa.data.settings.DrawerSearch
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.data.settings.PrivateSpacePosition
import com.nl.growwa.feature.components.AppContextMenu
import com.nl.growwa.feature.components.AppIconCell
import com.nl.growwa.feature.components.AppMenuActions
import com.nl.growwa.feature.components.AppSuggestionsCard
import com.nl.growwa.feature.components.SearchField

private const val TAB_APPS = 0
private const val TAB_PRIVATE = 1
// Horizontal drag (px) needed to switch drawer tabs.
private const val SWIPE_TAB = 120f
// Downward overscroll (px) past the top needed to dismiss the drawer to home.
private const val PULL_DISMISS = 180f

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    suggestions: List<AppInfo>,
    privateApps: List<AppInfo>,
    privateSpace: PrivateSpaceState,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onLockPrivate: () -> Unit,
    onUnlockPrivate: () -> Unit,
    onDragStart: (AppInfo, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(TAB_APPS) }
    val privateVisible = privateSpace.available && !settings.hidePrivateSpace
    // Clamp to Apps if Private Space is unavailable or hidden by the user.
    val tab = if (selectedTab == TAB_PRIVATE && !privateVisible) TAB_APPS else selectedTab

    val q = query.trim()
    val source = if (tab == TAB_PRIVATE) privateApps else apps
    val shown = remember(query, source) {
        if (q.isBlank()) source else source.filter { it.label.contains(q, ignoreCase = true) }
    }

    val searchBar: @Composable () -> Unit = {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            hint = if (tab == TAB_PRIVATE) "Search private apps" else "Search apps",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }

    val tabs: @Composable () -> Unit = {
        TabRow(
            selectedTabIndex = tab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            LeadingIconTab(
                selected = tab == TAB_APPS,
                onClick = { selectedTab = TAB_APPS; query = "" },
                text = { Text("Apps") },
                icon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
            LeadingIconTab(
                selected = tab == TAB_PRIVATE,
                onClick = { selectedTab = TAB_PRIVATE; query = "" },
                text = { Text("Private space") },
                icon = {
                    Icon(
                        if (privateSpace.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }

    val gridState = rememberLazyGridState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = ((1f - settings.drawerTransparency) * 0.42f).coerceIn(0f, 0.55f),
                ),
            )
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        val showTabs = privateVisible
        if (showTabs && settings.privateSpacePosition == PrivateSpacePosition.TOP) tabs()
        if (settings.drawerSearch == DrawerSearch.TOP) searchBar()

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // A non-consuming observer: reads raw finger movement (ignoring consumption) so the
                // grid still scrolls normally underneath. A downward pull while the grid is at the
                // top returns to home; a horizontal swipe switches between Apps and Private space.
                .pointerInput(privateVisible) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startedAtTop = gridState.firstVisibleItemIndex == 0 &&
                            gridState.firstVisibleItemScrollOffset == 0
                        var dx = 0f
                        var dy = 0f
                        var dismissed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) {
                                val d = change.positionChangeIgnoreConsumed()
                                dx += d.x
                                dy += d.y
                                if (!dismissed && startedAtTop && dy > PULL_DISMISS &&
                                    dy > kotlin.math.abs(dx)
                                ) {
                                    dismissed = true
                                    onDismiss()
                                }
                            }
                            if (event.changes.all { !it.pressed }) break
                        }
                        if (!dismissed && kotlin.math.abs(dx) > kotlin.math.abs(dy) &&
                            kotlin.math.abs(dx) > SWIPE_TAB
                        ) {
                            if (dx < 0 && privateVisible) { selectedTab = TAB_PRIVATE; query = "" }
                            else if (dx > 0) { selectedTab = TAB_APPS; query = "" }
                        }
                    }
                },
        ) {
            if (tab == TAB_PRIVATE && privateSpace.locked) {
                PrivateLocked(onUnlock = onUnlockPrivate)
            } else {
                AppGrid(
                    apps = shown,
                    columns = settings.drawerColumns,
                    settings = settings,
                    gridState = gridState,
                    header = when {
                        tab == TAB_PRIVATE -> {
                            { PrivateUnlockedHeader(onLock = onLockPrivate) }
                        }
                        q.isBlank() && suggestions.isNotEmpty() -> {
                            {
                                AppSuggestionsCard(
                                    apps = suggestions,
                                    onLaunch = onLaunch,
                                    columns = settings.drawerColumns,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                )
                            }
                        }
                        else -> null
                    },
                    onLaunch = onLaunch,
                    onUninstall = onUninstall,
                    onAddToDock = onAddToDock,
                    onAppInfo = onAppInfo,
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                )
            }
        }

        if (settings.drawerSearch == DrawerSearch.BOTTOM) searchBar()
        if (showTabs && settings.privateSpacePosition == PrivateSpacePosition.BOTTOM) tabs()
    }
}

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    columns: Int,
    settings: LauncherSettings,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    header: (@Composable () -> Unit)?,
    onLaunch: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onDragStart: (AppInfo, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        if (header != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        }
        items(apps, key = { it.key.toString() }) { app ->
            DrawerCell(
                app, settings, onLaunch, onUninstall, onAddToDock, onAppInfo,
                onDragStart, onDragMove, onDragEnd,
            )
        }
    }
}

@Composable
private fun PrivateLocked(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Private space is locked",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Unlock to see and open your private apps.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onUnlock) {
            Text("Unlock")
        }
    }
}

@Composable
private fun PrivateUnlockedHeader(onLock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onLock) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Lock")
        }
    }
}

@Composable
private fun DrawerCell(
    app: AppInfo,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onDragStart: (AppInfo, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords = it }
            .pointerInput(app.key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var longPressed = false
                    // Quick tap → launch; hold past the long-press timeout → context menu.
                    val up = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        longPressed = true
                        null
                    }
                    when {
                        longPressed -> {
                            // Held ~0.5s: reveal the context menu.
                            menuOpen = true
                            // If the finger then drags, pick the app up to place on home/dock.
                            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            if (slop != null) {
                                menuOpen = false
                                coords?.let { onDragStart(app, it.localToWindow(slop.position)) }
                                drag(down.id) { change ->
                                    coords?.let { onDragMove(it.localToWindow(change.position)) }
                                    change.consume()
                                }
                                onDragEnd()
                            }
                        }
                        up != null -> onLaunch(app)
                    }
                }
            },
    ) {
        // Pure visual — gestures are handled by the wrapping Box above.
        AppIconCell(
            icon = app.icon,
            label = app.label,
            iconSize = settings.iconSizeDp.dp,
            labelSize = settings.labelSizeSp.sp,
            showLabel = settings.showDrawerLabels,
            onClick = null,
            onLongClick = null,
            modifier = Modifier.fillMaxWidth(),
            labelColor = MaterialTheme.colorScheme.onSurface,
        )
        AppContextMenu(
            expanded = menuOpen,
            actions = AppMenuActions(
                onOpen = { onLaunch(app) },
                onAddToDock = { onAddToDock(app) },
                onAppInfo = { onAppInfo(app) },
                onUninstall = { onUninstall(app) },
            ),
            onDismiss = { menuOpen = false },
        )
    }
}
