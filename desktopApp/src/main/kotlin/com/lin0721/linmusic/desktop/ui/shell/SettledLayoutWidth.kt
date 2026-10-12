package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 各栏宽度形态切换的动画时长
const val PANE_ANIMATION_MS = 260

// 右侧栏伸缩期间内容区可见宽度与稳定宽度之差；读取放在布局阶段，动画每帧只触发重新布局而非重组
val LocalPaneWidthExtra = compositionLocalOf<() -> Dp> { { 0.dp } }

// 让页面始终按右侧栏稳定后的宽度排版，伸缩动画中多出的部分被裁剪，避免内容逐帧被挤压
fun Modifier.settledLayoutWidth(extra: () -> Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
    val width = (constraints.maxWidth + extra().roundToPx()).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(0, 0) }
}

// 内容按固定宽度排版并贴左，父级变窄时右侧被裁掉而不是重新排版；offscreen 时移出父级范围，既不可见也不可点
internal fun Modifier.fixedWidthAtStart(width: Dp, offscreen: Boolean = false): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(width.roundToPx(), constraints.maxHeight))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(if (offscreen) -placeable.width else 0, 0)
    }
}
