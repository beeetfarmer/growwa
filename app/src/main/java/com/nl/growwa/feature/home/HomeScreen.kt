package com.nl.growwa.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.core.model.AppItem
import com.nl.growwa.core.model.FolderItem
import com.nl.growwa.core.model.HomeItem
import com.nl.growwa.core.model.WidgetPlacement
import com.nl.growwa.data.settings.LauncherSettings
import com.nl.growwa.data.widgets.GrowwaWidgetHost
import kotlin.math.roundToInt

/** A live drag in progress. */
private data class DragSession(
    val item: HomeItem,
    val from: DropSlot,
    val start: Offset,
    val pointer: Offset,
)

/**
 * A drag originating from the app drawer, tracked in window coordinates. The drawer writes to it;
 * HomeScreen observes it to drive its drag overlay, hover highlights and grid-snapped drop.
 */
class DrawerDragState {
    var app by mutableStateOf<AppInfo?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    // Bumped on release so HomeScreen can commit the drop exactly once.
    var release by mutableStateOf(0)
}

/** Sentinel [DropSlot] marking a drag that came from the drawer (no existing placement). */
private val EXTERNAL_SLOT = DropSlot("external", -1, -1)

/** How a drag that started in the drawer ended. */
sealed class ExternalDragResult {
    data class Place(val app: AppInfo, val slot: DropSlot) : ExternalDragResult()
}

/** Live geometry of the widget currently being resized (in grid cells). */
private data class ResizeDraft(
    val id: Int,
    val col: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int,
)

@Composable
fun HomeScreen(
    pagerState: PagerState,
    settings: LauncherSettings,
    layout: ResolvedLayout,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemoveFromHome: (page: Int, position: Int) -> Unit,
    onRemoveFromDock: (position: Int) -> Unit,
    onDrop: (from: DropSlot, to: DropSlot, makeFolder: Boolean) -> Unit,
    onRenameFolder: (id: Long, name: String) -> Unit,
    onTakeOutOfFolder: (folderId: Long, app: AppInfo) -> Unit,
    onDeleteFolder: (id: Long) -> Unit,
    widgetHost: GrowwaWidgetHost,
    onRemoveWidget: (appWidgetId: Int) -> Unit,
    onResizeWidget: (appWidgetId: Int, page: Int, position: Int, spanX: Int, spanY: Int) -> Unit,
    drawerDrag: DrawerDragState,
    onExternalDrop: (AppInfo, DropSlot) -> Unit,
    onExternalDragEnd: () -> Unit,
    onSidebarAnchor: (Float) -> Unit,
    editing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val iconPx = with(density) { settings.iconSizeDp.dp.toPx() }
    val moveSlopPx = with(density) { 24.dp.toPx() }

    var drag by remember { mutableStateOf<DragSession?>(null) }
    var menuSlot by remember { mutableStateOf<DropSlot?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    var renameFolderId by remember { mutableStateOf<Long?>(null) }
    val gridBounds = remember { mutableStateOf(Rect.Zero) }
    val dockBounds = remember { mutableStateOf(Rect.Zero) }
    val removeBounds = remember { mutableStateOf(Rect.Zero) }
    val cancelBounds = remember { mutableStateOf(Rect.Zero) }
    var overRemove by remember { mutableStateOf(false) }
    var overCancel by remember { mutableStateOf(false) }
    val rootCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hoverSlot by remember { mutableStateOf<DropSlot?>(null) }
    // When the pointer dwells over another app, we switch from "swap" to "make folder".
    var folderTarget by remember { mutableStateOf<DropSlot?>(null) }
    // Slot whose item we keep hidden after a drop, until the layout update lands — avoids the
    // dropped icon flashing back at its old position before the move/remove propagates.
    var pendingFrom by remember { mutableStateOf<DropSlot?>(null) }

    // Converts a pointer (local to a cell) into the drag layer's coordinate space.
    val toRoot: (LayoutCoordinates, Offset) -> Offset = { coords, local ->
        rootCoords.value?.localPositionOf(coords, local) ?: local
    }

    // Live snapshots so the remembered drag controller never uses stale values.
    val latestSettings by androidx.compose.runtime.rememberUpdatedState(settings)
    val latestPage by androidx.compose.runtime.rememberUpdatedState(pagerState.currentPage)
    val latestOnDrop by androidx.compose.runtime.rememberUpdatedState(onDrop)
    val latestDockCount by androidx.compose.runtime.rememberUpdatedState(layout.dock.size)
    val latestFolderTarget by androidx.compose.runtime.rememberUpdatedState(folderTarget)
    val latestOnRemove by androidx.compose.runtime.rememberUpdatedState<(DropSlot) -> Unit> { slot ->
        when (slot.container) {
            "home" -> onRemoveFromHome(slot.page, slot.position)
            "dock" -> onRemoveFromDock(slot.position)
        }
    }

    val controller = remember {
        object {
            fun start(s: DragSession) { drag = s; hoverSlot = null; folderTarget = null; overRemove = false }
            fun move(p: Offset) {
                drag = drag?.copy(pointer = p)
                // Over the Remove bar: arm removal and suppress grid highlighting.
                if (removeBounds.value.width > 0 && removeBounds.value.contains(p)) {
                    overRemove = true
                    hoverSlot = null
                    folderTarget = null
                    return
                }
                overRemove = false
                val t = computeTarget(p, latestSettings, gridBounds.value, dockBounds.value, latestPage, latestDockCount)
                if (t != hoverSlot) folderTarget = null // reset dwell whenever the target changes
                hoverSlot = t
            }
            fun end() {
                val d = drag
                val hv = hoverSlot
                val overRm = overRemove
                val makeFolder = hv != null && hv == latestFolderTarget
                drag = null
                hoverSlot = null
                folderTarget = null
                overRemove = false
                if (d == null) return
                val moved = (d.pointer - d.start).getDistance()
                if (moved < moveSlopPx) {
                    menuSlot = d.from
                    return
                }
                // Released over the Remove bar → delete the dragged item.
                if (overRm || (removeBounds.value.width > 0 && removeBounds.value.contains(d.pointer))) {
                    pendingFrom = d.from
                    latestOnRemove(d.from)
                    return
                }
                val target = hv
                    ?: computeTarget(d.pointer, latestSettings, gridBounds.value, dockBounds.value, latestPage, latestDockCount)
                if (target != null) {
                    pendingFrom = d.from
                    latestOnDrop(d.from, target, makeFolder)
                }
            }
            fun cancel() { drag = null; hoverSlot = null; folderTarget = null; overRemove = false }

            // --- Drag originating from the drawer (places a brand-new app) ---
            fun rootPoint(p: Offset): Offset = rootCoords.value?.windowToLocal(p) ?: p
            fun zone(bounds: Rect, p: Offset) = bounds.width > 0f && bounds.inflate(120f).contains(p)
            fun startExternal(app: AppInfo, p: Offset) {
                val local = rootPoint(p)
                drag = DragSession(AppItem(app), EXTERNAL_SLOT, local, local)
                hoverSlot = null
                folderTarget = null
                overCancel = false
            }
            fun moveExternal(p: Offset) {
                if (drag == null) return
                val local = rootPoint(p)
                drag = drag?.copy(pointer = local)
                if (zone(cancelBounds.value, local)) {
                    overCancel = true
                    hoverSlot = null
                    folderTarget = null
                    return
                }
                overCancel = false
                val t = computeTarget(local, latestSettings, gridBounds.value, dockBounds.value, latestPage, latestDockCount)
                if (t != hoverSlot) folderTarget = null
                hoverSlot = t
            }
            /** Ends a drawer drag: place on a slot, or cancel when released on the X. */
            fun endExternal(): ExternalDragResult? {
                val d = drag ?: return null
                val hv = hoverSlot
                val p = d.pointer
                val cancel = zone(cancelBounds.value, p)
                drag = null
                hoverSlot = null
                folderTarget = null
                overCancel = false
                val app = (d.item as? AppItem)?.app ?: return null
                if (cancel) return null
                val target = hv
                    ?: computeTarget(p, latestSettings, gridBounds.value, dockBounds.value, latestPage, latestDockCount)
                return target?.let { ExternalDragResult.Place(app, it) }
            }
        }
    }

    // Bridge the drawer's drag (window coords) into the drag controller above.
    LaunchedEffect(drawerDrag.app) {
        drawerDrag.app?.let { controller.startExternal(it, drawerDrag.pointer) }
    }
    LaunchedEffect(drawerDrag.pointer) {
        if (drawerDrag.app != null) controller.moveExternal(drawerDrag.pointer)
    }
    LaunchedEffect(drawerDrag.release) {
        if (drawerDrag.release > 0) {
            when (val end = controller.endExternal()) {
                is ExternalDragResult.Place -> onExternalDrop(end.app, end.slot)
                null -> {}
            }
            onExternalDragEnd()
        }
    }

    // Reveal a just-dropped item once the new layout arrives (or after a short safety timeout,
    // in case the drop was a no-op that produced no layout change).
    LaunchedEffect(layout) { pendingFrom = null }
    LaunchedEffect(pendingFrom) {
        if (pendingFrom != null) {
            delay(500)
            pendingFrom = null
        }
    }
    LaunchedEffect(editing) {
        if (editing) dockBounds.value = Rect.Zero
    }

    // Dwell timer: hovering over another app for a moment arms folder creation.
    LaunchedEffect(hoverSlot, drag) {
        folderTarget = null
        val hv = hoverSlot
        val d = drag
        if (d != null && hv != null && hv != d.from && itemAt(layout, hv) is AppItem && d.item is AppItem) {
            delay(450)
            folderTarget = hv
        }
    }

    val ink = inkOn(rememberWallpaperLuminance())
    CompositionLocalProvider(LocalWallpaperInk provides ink) {
    Box(
        modifier
            .fillMaxSize()
            .background(if (editing) Color.Black.copy(alpha = 0.22f) else Color.Transparent)
            .onGloballyPositioned { rootCoords.value = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 8.dp)
                .padding(top = if (editing) 28.dp else 0.dp, bottom = if (editing) 156.dp else 0.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned {
                        gridBounds.value = rootCoords.value?.localBoundingBoxOf(it) ?: it.boundsInRoot()
                    },
            ) { page ->
                HomePage(
                    pageApps = layout.home[page].orEmpty(),
                    widgets = layout.widgets[page].orEmpty(),
                    widgetHost = widgetHost,
                    onRemoveWidget = onRemoveWidget,
                    onResizeWidget = onResizeWidget,
                    page = page,
                    settings = settings,
                    dragging = drag != null,
                    hoverSlot = hoverSlot,
                    drag = drag,
                    pendingSlot = pendingFrom,
                    menuSlot = menuSlot,
                    onTap = { item ->
                        when (item) {
                            is AppItem -> onLaunch(item.app)
                            is FolderItem -> openFolderId = item.id
                        }
                    },
                    onDragStart = controller::start,
                    onDragMove = controller::move,
                    onDragEnd = controller::end,
                    onDragCancel = controller::cancel,
                    onMenuDismiss = { menuSlot = null },
                    onAddToDock = onAddToDock,
                    onAppInfo = onAppInfo,
                    onRemove = { pos -> menuSlot = null; onRemoveFromHome(page, pos) },
                    onRenameFolder = { id -> menuSlot = null; renameFolderId = id },
                    onDeleteFolder = { id -> menuSlot = null; onDeleteFolder(id) },
                    folderTarget = folderTarget,
                    toRoot = toRoot,
                    editing = editing,
                )
            }

            if (!editing) {
            PageIndicator(count = pagerState.pageCount, current = pagerState.currentPage)
            Spacer(Modifier.height(8.dp))

            Dock(
                dock = layout.dock,
                settings = settings,
                dragging = drag != null,
                hoverSlot = hoverSlot,
                drag = drag,
                pendingSlot = pendingFrom,
                menuSlot = menuSlot,
                boundsCapture = { coords ->
                    dockBounds.value = rootCoords.value?.localBoundingBoxOf(coords) ?: coords.boundsInRoot()
                },
                toRoot = toRoot,
                onTap = { item ->
                    when (item) {
                        is AppItem -> onLaunch(item.app)
                        is FolderItem -> openFolderId = item.id
                    }
                },
                onDragStart = controller::start,
                onDragMove = controller::move,
                onDragEnd = controller::end,
                onDragCancel = controller::cancel,
                onMenuDismiss = { menuSlot = null },
                onAppInfo = onAppInfo,
                onRemove = { pos -> menuSlot = null; onRemoveFromDock(pos) },
            )
            Spacer(Modifier.height(12.dp))
            }
        }

        if (!editing && drag == null) {
            Sidebar(
                apps = apps,
                tokens = settings.sidebarApps,
                anchor = settings.sidebarAnchor,
                onAnchorChange = onSidebarAnchor,
                onLaunch = onLaunch,
            )
        }

        // Dropping on the X cancels a drag that started in the drawer.
        if (drag?.from == EXTERNAL_SLOT) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 28.dp, top = 4.dp),
            ) {
                DragPill(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Cancel",
                    highlighted = overCancel,
                    onBounds = { coords ->
                        cancelBounds.value = rootCoords.value?.localBoundingBoxOf(coords) ?: coords.boundsInRoot()
                    },
                )
            }
        }

        // Remove target: shown at the top while dragging an existing home/dock item.
        drag?.let { d ->
            if (d.from != EXTERNAL_SLOT) {
                RemoveTarget(
                    highlighted = overRemove,
                    onBounds = { coords ->
                        removeBounds.value = rootCoords.value?.localBoundingBoxOf(coords) ?: coords.boundsInRoot()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        // Drag overlay floating above everything, lifted slightly larger.
        drag?.let { d ->
            val sizeDp = with(density) { iconPx.toDp() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (d.pointer.x - iconPx / 2f).roundToInt(),
                            (d.pointer.y - iconPx / 2f).roundToInt(),
                        )
                    }
                    .size(sizeDp)
                    .scale(1.15f)
                    .alpha(0.95f),
            ) {
                ItemVisual(d.item, settings, showLabel = false, iconSizeDp = settings.iconSizeDp)
            }
        }
    }
    }

    // Folder contents dialog (re-derived from current layout by id).
    openFolderId?.let { id ->
        val folder = findFolder(layout, id)
        if (folder == null) {
            openFolderId = null
        } else {
            FolderDialog(
                folder = folder,
                settings = settings,
                onLaunch = { onLaunch(it); openFolderId = null },
                onTakeOut = { onTakeOutOfFolder(folder.id, it) },
                onRename = { renameFolderId = folder.id },
                onDismiss = { openFolderId = null },
            )
        }
    }

    renameFolderId?.let { id ->
        val folder = findFolder(layout, id)
        RenameDialog(
            initial = folder?.name ?: "Folder",
            onConfirm = { onRenameFolder(id, it); renameFolderId = null },
            onDismiss = { renameFolderId = null },
        )
    }
}

@Composable
private fun HomePage(
    pageApps: Map<Int, HomeItem>,
    widgets: List<WidgetPlacement>,
    widgetHost: GrowwaWidgetHost,
    onRemoveWidget: (appWidgetId: Int) -> Unit,
    onResizeWidget: (appWidgetId: Int, page: Int, position: Int, spanX: Int, spanY: Int) -> Unit,
    page: Int,
    settings: LauncherSettings,
    dragging: Boolean,
    hoverSlot: DropSlot?,
    drag: DragSession?,
    pendingSlot: DropSlot?,
    menuSlot: DropSlot?,
    onTap: (HomeItem) -> Unit,
    onDragStart: (DragSession) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onMenuDismiss: () -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: (position: Int) -> Unit,
    onRenameFolder: (id: Long) -> Unit,
    onDeleteFolder: (id: Long) -> Unit,
    folderTarget: DropSlot?,
    toRoot: (LayoutCoordinates, Offset) -> Offset,
    editing: Boolean,
) {
    val cols = settings.homeColumns
    val rows = settings.homeRows
    // One picked-up widget: drag the body to move it, drag the handles to resize.
    var edit by remember { mutableStateOf<ResizeDraft?>(null) }
    var dragOrigin by remember { mutableStateOf<ResizeDraft?>(null) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    // Cells covered by a widget so we don't draw drag placeholders beneath them.
    val widgetCells = remember(widgets, cols) {
        buildSet {
            for (w in widgets) {
                val r0 = w.position / cols
                val c0 = w.position % cols
                for (rr in r0 until r0 + w.spanY) for (cc in c0 until c0 + w.spanX) add(rr * cols + cc)
            }
        }
    }
    // Cells occupied by apps and by widgets other than the one being edited (resized/moved).
    val editingId = edit?.id
    val blocked = remember(pageApps, widgets, cols, editingId) {
        buildSet {
            addAll(pageApps.keys)
            for (w in widgets) {
                if (w.appWidgetId == editingId) continue
                val r0 = w.position / cols
                val c0 = w.position % cols
                for (rr in r0 until r0 + w.spanY) for (cc in c0 until c0 + w.spanX) add(rr * cols + cc)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Fill the whole surface: cells stretch edge-to-edge and top-to-dock (iOS-style).
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val padX = 0.dp
        val padY = 0.dp
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { cellH.toPx() }

        Column(Modifier.fillMaxSize()) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until cols) {
                        val pos = r * cols + c
                        val slot = DropSlot("home", page, pos)
                        val isHover = hoverSlot == slot
                        val isFolderTarget = folderTarget == slot
                        Box(
                            Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val placing = drag?.from == EXTERNAL_SLOT
                            val item = pageApps[pos]
                            if (placing && isHover) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(Color.White.copy(alpha = 0.42f)),
                                )
                            } else if (placing && item == null && pos !in widgetCells) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(8.dp)
                                        .border(
                                            1.25.dp,
                                            Color.White.copy(alpha = 0.7f),
                                            RoundedCornerShape(22.dp),
                                        ),
                                )
                            } else if (dragging && pos !in widgetCells) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Color.White.copy(
                                                alpha = when {
                                                    isFolderTarget -> 0.30f
                                                    isHover -> 0.22f
                                                    else -> 0.05f
                                                },
                                            ),
                                        ),
                                )
                            }
                            if (editing && !dragging && item == null && pos !in widgetCells) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(8.dp)
                                        .border(
                                            1.25.dp,
                                            Color.White.copy(alpha = 0.7f),
                                            RoundedCornerShape(18.dp),
                                        ),
                                )
                            }
                            if (item != null) {
                                ItemCell(
                                    item = item,
                                    slot = slot,
                                    settings = settings,
                                    showLabel = settings.showHomeLabels,
                                    isDragSource = drag?.from == slot || pendingSlot == slot,
                                    menuExpanded = menuSlot == slot,
                                    toRoot = toRoot,
                                    onTap = onTap,
                                    onDragStart = onDragStart,
                                    onDragMove = onDragMove,
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragCancel,
                                    onMenuDismiss = onMenuDismiss,
                                    menuItems = {
                                        HomeItemMenu(
                                            item = item,
                                            inDock = false,
                                            onAddToDock = onAddToDock,
                                            onAppInfo = onAppInfo,
                                            onRemove = { onRemove(pos) },
                                            onRename = onRenameFolder,
                                            onDeleteFolder = onDeleteFolder,
                                            onDismiss = onMenuDismiss,
                                        )
                                    },
                                )
                                if (editing) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 2.dp, end = 2.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .pointerInput(pos) {
                                                detectTapGestures { onRemove(pos) }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tap empty space to drop the widget being moved or resized.
        val dismissEdit by rememberUpdatedState {
            edit?.let { onResizeWidget(it.id, page, it.row * cols + it.col, it.spanX, it.spanY) }
            edit = null
            dragOrigin = null
        }
        if (edit != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { dismissEdit() } },
            )
        }

        // Widgets float above the cell grid, spanning their cells.
        for (w in widgets) {
            val live = edit?.takeIf { it.id == w.appWidgetId }
            val col = live?.col ?: (w.position % cols)
            val row = live?.row ?: (w.position / cols)
            val sx = live?.spanX ?: w.spanX
            val sy = live?.spanY ?: w.spanY
            val info = remember(w.appWidgetId) { widgetHost.providerInfo(w.appWidgetId) }
            WidgetCell(
                placement = w,
                info = info,
                widgetHost = widgetHost,
                col = col, row = row, spanX = sx, spanY = sy,
                cellW = cellW, cellH = cellH, padX = padX, padY = padY,
                onEdit = {
                    if (edit?.id != w.appWidgetId) dismissEdit()
                    val start = ResizeDraft(w.appWidgetId, col, row, sx, sy)
                    edit = start
                    dragOrigin = start
                    dragTotal = Offset.Zero
                },
                onDrag = { delta ->
                    dragTotal += delta
                    val start = dragOrigin
                    if (start != null) {
                        val nc = (start.col + (dragTotal.x / cellWpx).roundToInt())
                            .coerceIn(0, cols - start.spanX)
                        val nr = (start.row + (dragTotal.y / cellHpx).roundToInt())
                            .coerceIn(0, rows - start.spanY)
                        if (cellsClear(nc, nr, start.spanX, start.spanY, blocked, cols)) {
                            edit = start.copy(col = nc, row = nr)
                        }
                    }
                },
            )
        }

        // Resize handles, plus Cities / Remove, while a widget is picked up.
        edit?.let { d ->
            val info = remember(d.id) { widgetHost.providerInfo(d.id) }
            val mode = info?.resizeMode ?: AppWidgetProviderInfo.RESIZE_BOTH
            val context = LocalContext.current
            WidgetResizeFrame(
                draft = d,
                cols = cols, rows = rows,
                cellW = cellW, cellH = cellH, padX = padX, padY = padY,
                minCellsX = info?.let { widgetCellsFor(it.minResizeWidth, density.density) } ?: 1,
                minCellsY = info?.let { widgetCellsFor(it.minResizeHeight, density.density) } ?: 1,
                canHorizontal = mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
                canVertical = mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
                blocked = blocked,
                onChange = { edit = it },
            )
            Row(
                Modifier
                    .offset(x = padX + cellW * d.col, y = padY + cellH * d.row - 36.dp)
                    .width(cellW * d.spanX),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val configure = info?.configure
                if (configure != null) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                    .setComponent(configure)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, d.id),
                            )
                        },
                    ) {
                        Text(
                            if (configure.className.contains("WorldClock")) "Cities" else "Edit",
                            color = LocalWallpaperInk.current,
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .pointerInput(d.id) {
                            detectTapGestures {
                                onRemoveWidget(d.id)
                                edit = null
                                dragOrigin = null
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove widget", tint = Color(0xFF1C1C1E), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetCell(
    placement: WidgetPlacement,
    info: AppWidgetProviderInfo?,
    widgetHost: GrowwaWidgetHost,
    col: Int,
    row: Int,
    spanX: Int,
    spanY: Int,
    cellW: Dp,
    cellH: Dp,
    padX: Dp,
    padY: Dp,
    onEdit: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    val widthDp = (cellW.value * spanX).toInt()
    val heightDp = (cellH.value * spanY).toInt()
    val editNow by rememberUpdatedState(onEdit)
    val dragNow by rememberUpdatedState(onDrag)
    Box(
        Modifier
            .offset(x = padX + cellW * col, y = padY + cellH * row)
            .size(width = cellW * spanX, height = cellH * spanY)
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (info != null) {
            AndroidView(
                factory = { ctx ->
                    widgetHost.createView(ctx, placement.appWidgetId, info).apply {
                        setAppWidget(placement.appWidgetId, info)
                    }
                },
                update = { view ->
                    val opts = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                    }
                    runCatching { view.updateAppWidgetSize(opts, widthDp, heightDp, widthDp, heightDp) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Long-press, then drag, to move. Handles for resizing appear above this.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(placement.appWidgetId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { editNow() },
                        onDrag = { change, delta ->
                            change.consume()
                            dragNow(delta)
                        },
                    )
                },
        )
    }
}

@Composable
private fun WidgetResizeFrame(
    draft: ResizeDraft,
    cols: Int,
    rows: Int,
    cellW: Dp,
    cellH: Dp,
    padX: Dp,
    padY: Dp,
    minCellsX: Int,
    minCellsY: Int,
    canHorizontal: Boolean,
    canVertical: Boolean,
    blocked: Set<Int>,
    onChange: (ResizeDraft) -> Unit,
) {
    val density = LocalDensity.current
    val cellWpx = with(density) { cellW.toPx() }
    val cellHpx = with(density) { cellH.toPx() }
    Box(
        Modifier
            .offset(x = padX + cellW * draft.col, y = padY + cellH * draft.row)
            .size(width = cellW * draft.spanX, height = cellH * draft.spanY)
            .padding(4.dp)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(16.dp)),
    ) {
        // Right edge: grow/shrink spanX.
        EdgeHandle(Alignment.CenterEnd, canHorizontal, { draft }, onChange) { s, total ->
            val want = (s.spanX + (total.x / cellWpx).roundToInt()).coerceIn(minCellsX, cols - s.col)
            s.copy(spanX = fitSpan(s.col, s.row, want, s.spanY, minCellsX, blocked, cols, horizontal = true))
        }
        // Bottom edge: grow/shrink spanY.
        EdgeHandle(Alignment.BottomCenter, canVertical, { draft }, onChange) { s, total ->
            val want = (s.spanY + (total.y / cellHpx).roundToInt()).coerceIn(minCellsY, rows - s.row)
            s.copy(spanY = fitSpan(s.col, s.row, s.spanX, want, minCellsY, blocked, cols, horizontal = false))
        }
        // Left edge: move left boundary, keep right edge fixed.
        EdgeHandle(Alignment.CenterStart, canHorizontal, { draft }, onChange) { s, total ->
            val rightEdge = s.col + s.spanX
            val newCol = (s.col + (total.x / cellWpx).roundToInt()).coerceIn(0, rightEdge - minCellsX)
            val (col, span) = fitEdge(newCol, rightEdge, s.row, s.spanY, minCellsX, blocked, cols, horizontal = true)
            s.copy(col = col, spanX = span)
        }
        // Top edge: move top boundary, keep bottom edge fixed.
        EdgeHandle(Alignment.TopCenter, canVertical, { draft }, onChange) { s, total ->
            val bottomEdge = s.row + s.spanY
            val newRow = (s.row + (total.y / cellHpx).roundToInt()).coerceIn(0, bottomEdge - minCellsY)
            val (row, span) = fitEdge(newRow, bottomEdge, s.col, s.spanX, minCellsY, blocked, cols, horizontal = false)
            s.copy(row = row, spanY = span)
        }
    }
}

@Composable
private fun BoxScope.EdgeHandle(
    alignment: Alignment,
    enabled: Boolean,
    current: () -> ResizeDraft,
    onChange: (ResizeDraft) -> Unit,
    transform: (start: ResizeDraft, total: Offset) -> ResizeDraft,
) {
    if (!enabled) return
    val latest by androidx.compose.runtime.rememberUpdatedState(current)
    Box(
        Modifier
            .align(alignment)
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(BorderStroke(2.dp, Color.White), CircleShape)
            .pointerInput(Unit) {
                var start: ResizeDraft? = null
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { start = latest(); total = Offset.Zero },
                    onDragEnd = { start = null },
                    onDragCancel = { start = null },
                    onDrag = { change, delta ->
                        change.consume()
                        total += delta
                        start?.let { onChange(transform(it, total)) }
                    },
                )
            },
    )
}

/** Convert a widget's min dp dimension to a whole number of grid cells. */
/** Convert a widget's minimum pixel dimension into a whole number of ~70dp grid cells. */
private fun widgetCellsFor(minPx: Int, density: Float): Int {
    val dp = minPx / density
    return ((dp + 30f) / 70f).roundToInt().coerceAtLeast(1)
}

private fun cellsClear(
    col: Int, row: Int, spanX: Int, spanY: Int, blocked: Set<Int>, cols: Int,
): Boolean {
    for (rr in row until row + spanY) for (cc in col until col + spanX) {
        if ((rr * cols + cc) in blocked) return false
    }
    return true
}

/** Largest span (down to [min]) along one axis from a fixed top-left that hits no blocked cell. */
private fun fitSpan(
    col: Int, row: Int, spanX: Int, spanY: Int, min: Int, blocked: Set<Int>, cols: Int,
    horizontal: Boolean,
): Int {
    var want = if (horizontal) spanX else spanY
    while (want > min) {
        val ok = if (horizontal) cellsClear(col, row, want, spanY, blocked, cols)
        else cellsClear(col, row, spanX, want, blocked, cols)
        if (ok) return want
        want--
    }
    return min
}

/**
 * Move a leading edge inward from [start] toward the fixed [end], stopping at the first
 * blocked cell. Returns the new leading coordinate and the resulting span.
 */
private fun fitEdge(
    start: Int, end: Int, otherStart: Int, otherSpan: Int, min: Int, blocked: Set<Int>, cols: Int,
    horizontal: Boolean,
): Pair<Int, Int> {
    var lead = start
    while (lead <= end - min) {
        val span = end - lead
        val ok = if (horizontal) cellsClear(lead, otherStart, span, otherSpan, blocked, cols)
        else cellsClear(otherStart, lead, otherSpan, span, blocked, cols)
        if (ok) return lead to span
        lead++
    }
    return (end - min) to min
}

@Composable
private fun Dock(
    dock: Map<Int, HomeItem>,
    settings: LauncherSettings,
    dragging: Boolean,
    hoverSlot: DropSlot?,
    drag: DragSession?,
    pendingSlot: DropSlot?,
    menuSlot: DropSlot?,
    boundsCapture: (LayoutCoordinates) -> Unit,
    toRoot: (LayoutCoordinates, Offset) -> Offset,
    onTap: (HomeItem) -> Unit,
    onDragStart: (DragSession) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onMenuDismiss: () -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: (position: Int) -> Unit,
) {
    val items = dock.entries.sortedBy { it.key }
    val count = items.size

    // Dynamic: ≤4 apps stay big; 5–6 shrink. Off: one height, scaled from that 90dp tray.
    val heightScale = settings.dockHeightDp / 90f
    val iconSize by animateDpAsState(
        if (settings.dockDynamicSize) when {
            count <= 4 -> 62.dp
            count == 5 -> 54.dp
            else -> 48.dp
        } else (62f * heightScale).dp,
        label = "dockIcon",
    )
    val gap by animateDpAsState(
        if (settings.dockDynamicSize) when {
            count <= 4 -> 20.dp
            count == 5 -> 14.dp
            else -> 10.dp
        } else (20f * heightScale).dp,
        label = "dockGap",
    )
    val vPad by animateDpAsState(
        if (settings.dockDynamicSize) if (count <= 4) 14.dp else 10.dp else (14f * heightScale).dp,
        label = "dockPad",
    )
    val corner by animateDpAsState(settings.dockCornerDp.dp, label = "dockCorner")
    val shape = RoundedCornerShape(corner)
    // 0.6 is the original tray: 22% white fill, 38% border. 0 is more solid, 1 is clear.
    val shade = ((1f - settings.dockTransparency) / 0.4f).coerceIn(0f, 2.5f)
    val glass = (0.22f * shade).coerceIn(0f, 0.55f)
    val rim = (0.38f * shade).coerceIn(0f, 0.7f)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (count == 0) {
            // Empty placeholder so apps can still be dragged into the dock.
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(width = 196.dp, height = if (settings.dockDynamicSize) 76.dp else settings.dockHeightDp.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = glass))
                    .border(1.dp, Color.White.copy(alpha = rim), shape)
                    .onGloballyPositioned { boundsCapture(it) },
            )
            return@Box
        }
        // Frosted tray. No elevation shadow: it shows through the translucent fill as a bar behind the icons.
        Row(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = glass))
                .border(1.dp, Color.White.copy(alpha = rim), shape)
                .padding(horizontal = 18.dp, vertical = vPad)
                .onGloballyPositioned { boundsCapture(it) },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (pos, item) ->
                val slot = DropSlot("dock", 0, pos)
                // Key by app/folder identity so a reorder moves the same instance — the
                // placement animation then slides it to its new spot instead of snapping.
                key(keyOf(item)) {
                    // Scale + fade in when this app first lands in the dock.
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }
                    val enter by animateFloatAsState(
                        if (shown) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dockEnter",
                    )
                    Box(
                        Modifier
                            .animatePlacement()
                            .graphicsLayer {
                                scaleX = 0.5f + 0.5f * enter
                                scaleY = 0.5f + 0.5f * enter
                                alpha = enter
                            }
                            .size(iconSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        ItemCell(
                            item = item,
                            slot = slot,
                            settings = settings,
                            showLabel = false,
                            iconSizeDp = iconSize.value,
                            isDragSource = drag?.from == slot || pendingSlot == slot,
                            menuExpanded = menuSlot == slot,
                            toRoot = toRoot,
                            onTap = onTap,
                            onDragStart = onDragStart,
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                            onMenuDismiss = onMenuDismiss,
                            menuItems = {
                                HomeItemMenu(
                                    item = item,
                                    inDock = true,
                                    onAddToDock = {},
                                    onAppInfo = onAppInfo,
                                    onRemove = { onRemove(pos) },
                                    onRename = {},
                                    onDeleteFolder = {},
                                    onDismiss = onMenuDismiss,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Stable identity for a home/dock item, used as a Compose key so reorders animate. */
private fun keyOf(item: HomeItem): String = when (item) {
    is AppItem -> "app_${item.app.key}"
    is FolderItem -> "folder_${item.id}"
}

/**
 * Animates a child whenever its placement within the parent changes (e.g. a reorder), sliding it
 * from its old position to the new one. Works in any layout, unlike LazyList item animations.
 */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { coords -> target = coords.positionInParent().round() }
        .offset {
            val anim = animatable
                ?: Animatable(target, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != target) {
                scope.launch { anim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
            anim.value - target
        }
}

@Composable
private fun ItemCell(
    item: HomeItem,
    slot: DropSlot,
    settings: LauncherSettings,
    showLabel: Boolean,
    isDragSource: Boolean,
    menuExpanded: Boolean,
    toRoot: (LayoutCoordinates, Offset) -> Offset,
    onTap: (HomeItem) -> Unit,
    onDragStart: (DragSession) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onMenuDismiss: () -> Unit,
    menuItems: @Composable () -> Unit,
    iconSizeDp: Float = settings.iconSizeDp,
) {
    var cellCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        Modifier
            .onGloballyPositioned { cellCoords = it }
            .pointerInput(slot) {
                detectTapGestures(onTap = { onTap(item) })
            }
            .pointerInput(slot, item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        val p = cellCoords?.let { toRoot(it, local) } ?: local
                        onDragStart(DragSession(item, slot, p, p))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        cellCoords?.let { onDragMove(toRoot(it, change.position)) }
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!isDragSource) {
            ItemVisual(item, settings, showLabel, iconSizeDp)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            menuItems()
        }
    }
}

@Composable
private fun ItemVisual(
    item: HomeItem,
    settings: LauncherSettings,
    showLabel: Boolean,
    iconSizeDp: Float,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (item) {
            is AppItem -> Image(
                bitmap = item.app.icon,
                contentDescription = item.app.label,
                modifier = Modifier.size(iconSizeDp.dp),
            )
            is FolderItem -> FolderIcon(item, iconSizeDp.dp)
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (item) {
                    is AppItem -> item.app.label
                    is FolderItem -> item.name
                },
                color = LocalWallpaperInk.current,
                fontSize = settings.labelSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderIcon(folder: FolderItem, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(Color.White.copy(alpha = 0.22f))
            .padding(size / 12),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(size / 16)) {
            val preview = folder.apps.take(4)
            for (rowApps in preview.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(size / 16)) {
                    for (app in rowApps) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier.size(size / 3),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeItemMenu(
    item: HomeItem,
    inDock: Boolean,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onRemove: () -> Unit,
    onRename: (id: Long) -> Unit,
    onDeleteFolder: (id: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    when (item) {
        is AppItem -> {
            if (!inDock) {
                DropdownMenuItem(text = { Text("Add to dock") }, onClick = { onDismiss(); onAddToDock(item.app) })
            }
            DropdownMenuItem(text = { Text("App info") }, onClick = { onDismiss(); onAppInfo(item.app) })
            DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove() })
        }
        is FolderItem -> {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                onClick = { onRename(item.id) },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { onDeleteFolder(item.id) },
            )
        }
    }
}

@Composable
private fun FolderDialog(
    folder: FolderItem,
    settings: LauncherSettings,
    onLaunch: (AppInfo) -> Unit,
    onTakeOut: (AppInfo) -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onRename) { Text("Rename") } },
        title = { Text(folder.name, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                for (rowApps in folder.apps.chunked(4)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (app in rowApps) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .pointerInput(app.key) {
                                        detectTapGestures(
                                            onTap = { onLaunch(app) },
                                            onLongPress = { onTakeOut(app) },
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    app.label,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // Pad the last row so cells keep their width.
                        repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    "Tip: long-press an app to move it out to the home screen.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Rename folder") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
    )
}

@Composable
private fun DragPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean,
    onBounds: (LayoutCoordinates) -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (highlighted) 0.96f else 0.78f))
            .onGloballyPositioned(onBounds)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color(0xFF1C1C1E), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RemoveTarget(
    highlighted: Boolean,
    onBounds: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (highlighted) Color(0xFFE53935).copy(alpha = 0.92f)
                else Color.Black.copy(alpha = 0.55f),
            )
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .onGloballyPositioned { onBounds(it) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
        Text("Remove", color = Color.White, fontWeight = FontWeight.SemiBold)
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

private fun findFolder(layout: ResolvedLayout, id: Long): FolderItem? {
    layout.dock.values.forEach { if (it is FolderItem && it.id == id) return it }
    layout.home.values.forEach { page ->
        page.values.forEach { if (it is FolderItem && it.id == id) return it }
    }
    return null
}

private fun itemAt(layout: ResolvedLayout, slot: DropSlot): HomeItem? = when (slot.container) {
    "home" -> layout.home[slot.page]?.get(slot.position)
    "dock" -> layout.dock[slot.position]
    else -> null
}

/** Which slot a pointer (in root coords) is hovering over, or null if outside. */
private fun computeTarget(
    p: Offset,
    s: LauncherSettings,
    gb: Rect,
    db: Rect,
    page: Int,
    dockItems: Int,
): DropSlot? = when {
    db.width > 0 && db.contains(p) -> {
        // Insertion index 0..n based on which gap between dock items the pointer is nearest.
        val n = dockItems.coerceAtLeast(1)
        val rel = (p.x - db.left) / (db.width / n)
        DropSlot("dock", 0, (rel + 0.5f).toInt().coerceIn(0, n))
    }
    gb.width > 0 && gb.contains(p) -> {
        val cw = gb.width / s.homeColumns
        val ch = gb.height / s.homeRows
        val col = ((p.x - gb.left) / cw).toInt().coerceIn(0, s.homeColumns - 1)
        val row = ((p.y - gb.top) / ch).toInt().coerceIn(0, s.homeRows - 1)
        DropSlot("home", page, row * s.homeColumns + col)
    }
    else -> null
}
