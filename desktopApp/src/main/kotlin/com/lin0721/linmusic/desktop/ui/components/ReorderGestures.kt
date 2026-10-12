package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

// 长按后纵向拖动。鼠标在窗口外松开时收不到抬起事件，Compose 仍认为按键按着，
// 所以回到窗口后的第一次移动若已没有主键按下，就当作松手结束
suspend fun PointerInputScope.detectLongPressReorder(
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > slop
                ) {
                    return@withTimeoutOrNull
                }
            }
        } == null
        if (!longPressed) return@awaitEachGesture

        onDragStart()
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || !event.buttons.isPrimaryPressed) break
                val deltaY = change.positionChange().y
                if (deltaY != 0f) {
                    change.consume()
                    onDrag(deltaY)
                }
            }
        } finally {
            onDragEnd()
        }
    }
}
