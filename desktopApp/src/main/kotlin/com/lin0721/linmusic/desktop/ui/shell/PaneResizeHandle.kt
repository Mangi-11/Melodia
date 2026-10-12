package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.Cursor

private val LineWidth = 2.dp

// 栏间缝隙里的拖动条：悬停或拖动时显示细线。拖动会让条自身随栏移动，
// 位移按窗口根坐标计算，不受自身位置变化影响；onDrag 收到的是相对按下点的水平位移
@Composable
fun PaneResizeHandle(
    width: Dp,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (delta: Dp) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier.width(width).fillMaxHeight()
            .onGloballyPositioned { coordinates = it }
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(hoverSource)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val origin = coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                                val startX = origin.localToRoot(down.position).x
                                dragging = true
                                currentOnDragStart()
                                drag(down.id) { change ->
                                    if (origin.isAttached) {
                                        val deltaPx = origin.localToRoot(change.position).x - startX
                                        currentOnDrag(with(density) { deltaPx.toDp() })
                                    }
                                    change.consume()
                                }
                                dragging = false
                                currentOnDragEnd()
                            }
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (enabled && (hovered || dragging)) {
            Box(Modifier.width(LineWidth).fillMaxHeight().background(DesktopColors.TextGray.copy(alpha = 0.5f)))
        }
    }
}
