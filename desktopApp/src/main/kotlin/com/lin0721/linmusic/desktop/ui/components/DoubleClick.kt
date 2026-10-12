package com.lin0721.linmusic.desktop.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

// 双击取 AWT 自带的 clickCount，不消费事件；与 TrackRow 的双击播放保持一致
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onDoubleClick(action: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.awtEventOrNull?.clickCount == 2) action()
    }
