package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import java.util.concurrent.atomic.AtomicInteger

// 主窗口里是否有输入框（或快捷键录制框）正占用键盘；窗口级快捷键在预览阶段拦截按键，据此避让
object TextInputFocus {

    private val holders = AtomicInteger(0)

    val isActive: Boolean get() = holders.get() > 0

    fun acquire() {
        holders.incrementAndGet()
    }

    fun release() {
        holders.updateAndGet { (it - 1).coerceAtLeast(0) }
    }
}

// 挂在文本输入框的 Modifier 链上，持有焦点期间标记为占用
@Composable
fun Modifier.trackTextInputFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (focused) TextInputFocus.release() }
    }
    return onFocusChanged { state ->
        if (state.isFocused != focused) {
            focused = state.isFocused
            if (focused) TextInputFocus.acquire() else TextInputFocus.release()
        }
    }
}

// 没有焦点概念但在独占按键的控件（快捷键录制）：active 期间标记为占用
@Composable
fun HoldTextInputFocus(active: Boolean) {
    DisposableEffect(active) {
        if (active) TextInputFocus.acquire()
        onDispose { if (active) TextInputFocus.release() }
    }
}
