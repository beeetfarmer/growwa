package com.nl.growwa.feature.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.core.model.AppInfo
import com.nl.growwa.data.settings.sidebarToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PanelWidth = 88.dp
private val EdgeGap = 20.dp
private val HandleWidth = 48.dp
private val HandleHeight = 120.dp
private val Ink = Color(0xFF1C1C1E)
private val Glass = Color.White.copy(alpha = 0.78f)

/**
 * Edge panel. A short handle sits on the right edge until it is dragged open.
 * Drag the handle up or down to move it. Apps are chosen in Settings.
 */
@Composable
fun Sidebar(
    apps: List<AppInfo>,
    tokens: List<String>,
    anchor: Float,
    onAnchorChange: (Float) -> Unit,
    onLaunch: (AppInfo) -> Unit,
) {
    val pinned = remember(apps, tokens) {
        val byToken = apps.associateBy { sidebarToken(it.key) }
        tokens.mapNotNull { byToken[it] }
    }
    val density = LocalDensity.current
    val hiddenPx = with(density) { (PanelWidth + 8.dp).toPx() }
    val openPx = with(density) { -EdgeGap.toPx() }
    // Plain state, updated inside the gesture. An Animatable snap posted to a scope
    // was still at the closed position when the finger lifted, so the panel never opened.
    var offsetPx by remember { mutableFloatStateOf(hiddenPx) }
    var anchorPx by remember { mutableFloatStateOf(anchor.coerceIn(0f, 1f)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(anchor) { anchorPx = anchor.coerceIn(0f, 1f) }
    val scope = rememberCoroutineScope()
    val settleJob = remember { arrayOf<Job?>(null) }
    fun settle(target: Float) {
        settleJob[0]?.cancel()
        settleJob[0] = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { v, _ -> offsetPx = v }
        }
    }
    val shape = RoundedCornerShape(40.dp)
    val open = offsetPx < hiddenPx * 0.5f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val top = with(density) { 48.dp.toPx() }
        val travel = (heightPx - top - with(density) { (150.dp + HandleHeight).toPx() }).coerceAtLeast(1f)
        val handleY = top + anchorPx * travel
        var panelHeight by remember { mutableIntStateOf(0) }
        val handleCenter = handleY + with(density) { HandleHeight.toPx() } / 2f
        val maxPanelTop = (heightPx - with(density) { 130.dp.toPx() } - panelHeight).coerceAtLeast(top)
        val panelY = (handleCenter - panelHeight / 2f).coerceIn(top, maxPanelTop)

        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { settle(hiddenPx) },
                    ),
            )
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(offsetPx.roundToInt(), panelY.roundToInt()) }
                .width(PanelWidth)
                .heightIn(max = maxHeight - 180.dp)
                .onGloballyPositioned { panelHeight = it.size.height }
                .clip(shape)
                .background(Glass)
                .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
                .pointerInput(hiddenPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            offsetPx = (offsetPx + dx).coerceIn(openPx, hiddenPx)
                        },
                        onDragEnd = { settle(if (offsetPx < hiddenPx * 0.75f) openPx else hiddenPx) },
                        onDragCancel = { settle(if (offsetPx < hiddenPx * 0.75f) openPx else hiddenPx) },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pinned.isEmpty()) {
                    Text(
                        "Add apps in Settings",
                        color = Ink,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                pinned.forEach { app ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLaunch(app)
                                settle(hiddenPx)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            app.label,
                            color = Ink,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Close sidebar",
                tint = Ink,
                modifier = Modifier
                    .clickable { settle(hiddenPx) }
                    .padding(bottom = 8.dp)
                    .size(22.dp),
            )
        }
        // Stay composed for the whole drag. Removing the pill mid-swipe cancelled the
        // gesture and the panel sprang shut.
        if (dragging || offsetPx > hiddenPx * 0.8f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, handleY.roundToInt()) }
                    .width(HandleWidth)
                    .height(HandleHeight)
                    .systemGestureExclusion()
                    .pointerInput(hiddenPx, travel) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            settleJob[0]?.cancel()
                            dragging = true
                            var mode = 0 // 1 open/close, 2 move along the edge
                            var accX = 0f
                            var accY = 0f
                            val slop = viewConfiguration.touchSlop
                            try {
                                drag(down.id) { change ->
                                    val dx = change.positionChange().x
                                    val dy = change.positionChange().y
                                    change.consume()
                                    if (mode == 0) {
                                        accX += dx
                                        accY += dy
                                        if (abs(accX) > slop || abs(accY) > slop) {
                                            mode = if (abs(accX) >= abs(accY)) 1 else 2
                                            if (mode == 1) offsetPx = (offsetPx + accX).coerceIn(openPx, hiddenPx)
                                            else anchorPx = (anchorPx + accY / travel).coerceIn(0f, 1f)
                                        }
                                    } else when (mode) {
                                        1 -> offsetPx = (offsetPx + dx).coerceIn(openPx, hiddenPx)
                                        2 -> anchorPx = (anchorPx + dy / travel).coerceIn(0f, 1f)
                                    }
                                }
                                when (mode) {
                                    1 -> settle(if (offsetPx < hiddenPx * 0.75f) openPx else hiddenPx)
                                    2 -> onAnchorChange(anchorPx)
                                }
                            } finally {
                                dragging = false
                            }
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!dragging || offsetPx > hiddenPx * 0.8f) {
                    Box(
                        Modifier
                            .padding(end = 2.dp)
                            .width(8.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.72f)),
                    )
                }
            }
        }
    }
}
