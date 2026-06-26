package com.nl.growwa.feature.drawer

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
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
import com.nl.growwa.feature.components.SearchField

private const val TAB_APPS = 0
private const val TAB_PRIVATE = 1

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    privateApps: List<AppInfo>,
    privateSpace: PrivateSpaceState,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onLockPrivate: () -> Unit,
    onUnlockPrivate: () -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f - settings.drawerTransparency))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        val showTabs = privateVisible
        if (showTabs && settings.privateSpacePosition == PrivateSpacePosition.TOP) tabs()
        if (settings.drawerSearch == DrawerSearch.TOP) searchBar()

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (tab == TAB_PRIVATE && privateSpace.locked) {
                PrivateLocked(onUnlock = onUnlockPrivate)
            } else {
                AppGrid(
                    apps = shown,
                    columns = settings.drawerColumns,
                    settings = settings,
                    header = if (tab == TAB_PRIVATE) {
                        { PrivateUnlockedHeader(onLock = onLockPrivate) }
                    } else null,
                    onLaunch = onLaunch,
                    onAddToHome = onAddToHome,
                    onAddToDock = onAddToDock,
                    onAppInfo = onAppInfo,
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
    header: (@Composable () -> Unit)?,
    onLaunch: (AppInfo) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
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
            DrawerCell(app, settings, onLaunch, onAddToHome, onAddToDock, onAppInfo)
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
    onAddToHome: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        AppIconCell(
            icon = app.icon,
            label = app.label,
            iconSize = settings.iconSizeDp.dp,
            labelSize = settings.labelSizeSp.sp,
            showLabel = settings.showDrawerLabels,
            onClick = { onLaunch(app) },
            onLongClick = { menuOpen = true },
            modifier = Modifier.fillMaxWidth(),
            labelColor = MaterialTheme.colorScheme.onSurface,
        )
        AppContextMenu(
            expanded = menuOpen,
            actions = AppMenuActions(
                onOpen = { onLaunch(app) },
                onAddToHome = { onAddToHome(app) },
                onAddToDock = { onAddToDock(app) },
                onAppInfo = { onAppInfo(app) },
            ),
            onDismiss = { menuOpen = false },
        )
    }
}
