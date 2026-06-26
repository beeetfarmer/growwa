package com.nl.growwa.feature.home

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.feature.components.AppContextMenu
import com.nl.growwa.feature.components.AppIconCell
import com.nl.growwa.feature.components.AppMenuActions

@Composable
fun HomeScreen(
    pagerState: PagerState,
    settings: LauncherSettings,
    layout: ResolvedLayout,
    onLaunch: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemoveFromHome: (page: Int, position: Int) -> Unit,
    onRemoveFromDock: (position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            HomePage(
                pageApps = layout.home[page].orEmpty(),
                page = page,
                settings = settings,
                onLaunch = onLaunch,
                onAddToDock = onAddToDock,
                onAppInfo = onAppInfo,
                onRemove = onRemoveFromHome,
            )
        }

        PageIndicator(
            count = pagerState.pageCount,
            current = pagerState.currentPage,
        )
        Spacer(Modifier.height(8.dp))

        Dock(
            dock = layout.dock,
            settings = settings,
            onLaunch = onLaunch,
            onAppInfo = onAppInfo,
            onRemove = onRemoveFromDock,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HomePage(
    pageApps: Map<Int, AppInfo>,
    page: Int,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: (page: Int, position: Int) -> Unit,
) {
    val cols = settings.homeColumns
    val rows = settings.homeRows
    Column(Modifier.fillMaxSize()) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (c in 0 until cols) {
                    val pos = r * cols + c
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        val app = pageApps[pos]
                        if (app != null) {
                            HomeCell(
                                app = app,
                                settings = settings,
                                onLaunch = onLaunch,
                                onAddToDock = onAddToDock,
                                onAppInfo = onAppInfo,
                                onRemove = { onRemove(page, pos) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCell(
    app: AppInfo,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        AppIconCell(
            icon = app.icon,
            label = app.label,
            iconSize = settings.iconSizeDp.dp,
            labelSize = settings.labelSizeSp.sp,
            showLabel = settings.showHomeLabels,
            onClick = { onLaunch(app) },
            onLongClick = { menuOpen = true },
        )
        AppContextMenu(
            expanded = menuOpen,
            actions = AppMenuActions(
                onOpen = { onLaunch(app) },
                onAddToDock = { onAddToDock(app) },
                onRemove = onRemove,
                onAppInfo = { onAppInfo(app) },
            ),
            onDismiss = { menuOpen = false },
        )
    }
}

@Composable
private fun Dock(
    dock: Map<Int, AppInfo>,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: (position: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (pos in 0 until settings.dockCount) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val app = dock[pos]
                if (app != null) {
                    DockCell(app, settings, onLaunch, onAppInfo) { onRemove(pos) }
                }
            }
        }
    }
}

@Composable
private fun DockCell(
    app: AppInfo,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        AppIconCell(
            icon = app.icon,
            label = app.label,
            iconSize = settings.iconSizeDp.dp,
            labelSize = settings.labelSizeSp.sp,
            showLabel = false,
            onClick = { onLaunch(app) },
            onLongClick = { menuOpen = true },
        )
        AppContextMenu(
            expanded = menuOpen,
            actions = AppMenuActions(
                onOpen = { onLaunch(app) },
                onRemove = onRemove,
                onAppInfo = { onAppInfo(app) },
            ),
            onDismiss = { menuOpen = false },
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val active = i == current
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (active) 0.95f else 0.4f)),
            )
        }
    }
}
