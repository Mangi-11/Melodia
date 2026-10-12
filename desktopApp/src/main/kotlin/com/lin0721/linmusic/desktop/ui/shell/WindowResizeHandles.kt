package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.Cursor
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window

private val EdgeThickness = 6.dp

private enum class Edge(val cursor: Int, val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    Left(Cursor.W_RESIZE_CURSOR, true, false, false, false),
    Right(Cursor.E_RESIZE_CURSOR, false, false, true, false),
    Top(Cursor.N_RESIZE_CURSOR, false, true, false, false),
    Bottom(Cursor.S_RESIZE_CURSOR, false, false, false, true),
    TopLeft(Cursor.NW_RESIZE_CURSOR, true, true, false, false),
    TopRight(Cursor.NE_RESIZE_CURSOR, false, true, true, false),
    BottomLeft(Cursor.SW_RESIZE_CURSOR, true, false, false, true),
    BottomRight(Cursor.SE_RESIZE_CURSOR, false, false, true, true)
}

// 无边框窗口没有系统缩放边框，四边四角各放一条透明热区自行调整窗口尺寸
@Composable
fun WindowScope.WindowResizeHandles(enabled: Boolean) {
    if (!enabled) return
    val awtWindow = window
    Box(Modifier.fillMaxSize()) {
        EdgeHandle(awtWindow, Edge.Left, Modifier.align(Alignment.CenterStart).width(EdgeThickness).fillMaxHeight())
        EdgeHandle(awtWindow, Edge.Right, Modifier.align(Alignment.CenterEnd).width(EdgeThickness).fillMaxHeight())
        EdgeHandle(awtWindow, Edge.Top, Modifier.align(Alignment.TopCenter).height(EdgeThickness).fillMaxWidth())
        EdgeHandle(awtWindow, Edge.Bottom, Modifier.align(Alignment.BottomCenter).height(EdgeThickness).fillMaxWidth())
        EdgeHandle(awtWindow, Edge.TopLeft, Modifier.align(Alignment.TopStart).size(EdgeThickness * 2))
        EdgeHandle(awtWindow, Edge.TopRight, Modifier.align(Alignment.TopEnd).size(EdgeThickness * 2))
        EdgeHandle(awtWindow, Edge.BottomLeft, Modifier.align(Alignment.BottomStart).size(EdgeThickness * 2))
        EdgeHandle(awtWindow, Edge.BottomRight, Modifier.align(Alignment.BottomEnd).size(EdgeThickness * 2))
    }
}

@Composable
private fun BoxScope.EdgeHandle(window: Window, edge: Edge, modifier: Modifier) {
    // 左/上边拖动会移动窗口本身，相对位移会失真，改以屏幕绝对坐标计算
    var startMouse by remember { mutableStateOf<Point?>(null) }
    var startBounds by remember { mutableStateOf<Rectangle?>(null) }
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge) {
                detectDragGestures(
                    onDragStart = {
                        startMouse = MouseInfo.getPointerInfo()?.location
                        startBounds = window.bounds
                    },
                    onDragEnd = {
                        startMouse = null
                        startBounds = null
                    },
                    onDragCancel = {
                        startMouse = null
                        startBounds = null
                    }
                ) { change, _ ->
                    change.consume()
                    val origin = startMouse ?: return@detectDragGestures
                    val bounds = startBounds ?: return@detectDragGestures
                    val now = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                    window.bounds = resizedBounds(bounds, window.minimumSize, edge, now.x - origin.x, now.y - origin.y)
                }
            }
    )
}

private fun resizedBounds(start: Rectangle, min: Dimension, edge: Edge, dx: Int, dy: Int): Rectangle {
    var x = start.x
    var y = start.y
    var w = start.width
    var h = start.height
    if (edge.right) w = (start.width + dx).coerceAtLeast(min.width)
    if (edge.bottom) h = (start.height + dy).coerceAtLeast(min.height)
    if (edge.left) {
        w = (start.width - dx).coerceAtLeast(min.width)
        x = start.x + start.width - w
    }
    if (edge.top) {
        h = (start.height - dy).coerceAtLeast(min.height)
        y = start.y + start.height - h
    }
    return Rectangle(x, y, w, h)
}
