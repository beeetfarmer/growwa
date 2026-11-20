package com.nl.growwa.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Ink = Color(0xFF1C1C1E)

/** Edit mode opened by a long-press on the home screen: page thumbnails and the bottom actions. */
@Composable
fun HomeEditChrome(
    pageCount: Int,
    currentPage: Int,
    layout: ResolvedLayout,
    columns: Int,
    rows: Int,
    onSelectPage: (Int) -> Unit,
    onReorderPages: (from: Int, to: Int) -> Unit,
    onWallpapers: () -> Unit,
    onSystemStyle: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Text(
            "Hold and drag a thumbnail to reorder",
            color = inkOn(rememberWallpaperLuminance()),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 24.dp, end = 24.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageThumbnails(
                pageCount = pageCount,
                currentPage = currentPage,
                layout = layout,
                columns = columns,
                rows = rows,
                onSelectPage = onSelectPage,
                onReorderPages = onReorderPages,
            )
            EditActionBar(onWallpapers, onSystemStyle, onWidgets, onSettings)
        }
    }
}

@Composable
private fun PageThumbnails(
    pageCount: Int,
    currentPage: Int,
    layout: ResolvedLayout,
    columns: Int,
    rows: Int,
    onSelectPage: (Int) -> Unit,
    onReorderPages: (from: Int, to: Int) -> Unit,
) {
    val stepPx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        for (page in 0 until pageCount) {
            val armed = dragFrom != null && dragTo == page
            Box(
                Modifier
                    .size(width = 46.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(
                        width = if (page == currentPage || armed) 2.dp else 1.dp,
                        color = Color.White.copy(alpha = if (page == currentPage || armed) 0.95f else 0.4f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .pointerInput(page) {
                        detectTapGestures(onTap = { onSelectPage(page) })
                    }
                    .pointerInput(page, pageCount) {
                        var totalX = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                totalX = 0f
                                dragFrom = page
                                dragTo = page
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                totalX += delta.x
                                val shift = (totalX / stepPx).roundToInt()
                                dragTo = (page + shift).coerceIn(0, pageCount - 1)
                            },
                            onDragEnd = {
                                val from = dragFrom
                                val to = dragTo
                                dragFrom = null
                                dragTo = null
                                if (from != null && to != null && from != to) onReorderPages(from, to)
                            },
                            onDragCancel = {
                                dragFrom = null
                                dragTo = null
                            },
                        )
                    },
            ) {
                ThumbnailDots(occupiedCells(layout, page, columns), columns, rows)
            }
        }
    }
}

private fun occupiedCells(layout: ResolvedLayout, page: Int, columns: Int): Set<Int> {
    val cells = layout.home[page].orEmpty().keys.toMutableSet()
    for (w in layout.widgets[page].orEmpty()) {
        val r0 = w.position / columns
        val c0 = w.position % columns
        for (rr in r0 until r0 + w.spanY) for (cc in c0 until c0 + w.spanX) {
            cells.add(rr * columns + cc)
        }
    }
    return cells
}

@Composable
private fun ThumbnailDots(occupied: Set<Int>, columns: Int, rows: Int) {
    Column(Modifier.fillMaxSize().padding(4.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until columns) {
                    val filled = (r * columns + c) in occupied
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(0.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = if (filled) 0.9f else 0.12f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditActionBar(
    onWallpapers: () -> Unit,
    onSystemStyle: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.86f))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditAction(Icons.Default.Wallpaper, "Wallpapers", onWallpapers)
        EditAction(Icons.Default.Palette, "System style", onSystemStyle)
        EditAction(Icons.Default.Widgets, "Widgets", onWidgets)
        EditAction(Icons.Default.Settings, "Settings", onSettings)
    }
}

@Composable
private fun EditAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(22.dp))
        Text(
            label,
            color = Ink,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
