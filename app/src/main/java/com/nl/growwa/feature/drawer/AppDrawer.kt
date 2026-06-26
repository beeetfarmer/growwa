package com.nl.growwa.feature.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.data.settings.DrawerSearch
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.feature.components.AppContextMenu
import com.nl.growwa.feature.components.AppIconCell
import com.nl.growwa.feature.components.AppMenuActions
import com.nl.growwa.feature.components.SearchField

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    val searchBar: @Composable () -> Unit = {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            hint = "Search apps",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f - settings.drawerTransparency))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        if (settings.drawerSearch == DrawerSearch.TOP) searchBar()

        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.drawerColumns),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(shown, key = { it.key.toString() }) { app ->
                DrawerCell(app, settings, onLaunch, onAddToHome, onAddToDock, onAppInfo)
            }
        }

        if (settings.drawerSearch == DrawerSearch.BOTTOM) searchBar()
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
