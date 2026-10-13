package com.lin0721.linmusic.core.ui.interaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import com.lin0721.linmusic.core.ui.components.ToastManager

// 长按复制文字并弹 Toast；单击原样转发给 onClick，避免吞掉所在行或文字原有的点击行为
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.copyOnLongClick(text: String, label: String, onClick: () -> Unit): Modifier {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = {
            if (text.isNotBlank()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setText(AnnotatedString(text))
                ToastManager.showToast("已复制$label")
            }
        }
    )
}
