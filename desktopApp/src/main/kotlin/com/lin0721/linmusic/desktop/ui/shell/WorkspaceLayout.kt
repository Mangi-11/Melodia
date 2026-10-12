package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.ui.nowplaying.NowPlayingDockState
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens

// 三栏（音乐库 / 中间内容 / 正在播放）的宽度分配：音乐库展开时吃掉中间区域，中间区域宽度收到 0
@Stable
class WorkspaceLayout internal constructor(
    private val mode: LibraryMode,
    private val defaultWidth: Dp,
    val expandedWidth: Dp,
    private val libraryWidthState: State<Dp>,
    private val centerGapState: State<Dp>,
    private val dock: NowPlayingDockState
) {
    val libraryWidth: Dp get() = libraryWidthState.value

    // 音乐库与中间区域之间的间距，展开时收到 0
    val centerGap: Dp get() = centerGapState.value

    // 中间内容始终按“音乐库为默认宽度”的稳定宽度排版：可见宽度加上该差值即排版宽度，
    // 展开态下可见宽度为 0，内容保持原样被裁剪，返回后不必重建
    val centerWidthExtra: Dp
        get() {
            val frozenLibrary = if (mode == LibraryMode.RAIL) DesktopDimens.LibraryRailWidth else defaultWidth
            return (libraryWidth - frozenLibrary) + (centerGap - DesktopDimens.PaneGap) + dock.widthExtra
        }
}

@Composable
fun rememberWorkspaceLayout(
    mode: LibraryMode,
    availableWidth: Dp,
    dock: NowPlayingDockState,
    defaultWidth: Dp,
    resizing: Boolean
): WorkspaceLayout {
    val expandedWidth = (availableWidth - dock.settledOccupied).coerceAtLeast(0.dp)
    val target = when (mode) {
        LibraryMode.RAIL -> DesktopDimens.LibraryRailWidth
        LibraryMode.DEFAULT -> defaultWidth
        LibraryMode.EXPANDED -> expandedWidth
    }
    // 拖动调宽时跟手，不走过渡动画
    val spec = if (resizing) snap<Dp>() else tween<Dp>(PANE_ANIMATION_MS, easing = FastOutSlowInEasing)
    val libraryWidth = animateDpAsState(target, spec, label = "libraryWidth")
    val centerGap = animateDpAsState(
        if (mode == LibraryMode.EXPANDED) 0.dp else DesktopDimens.PaneGap,
        spec,
        label = "centerGap"
    )
    return WorkspaceLayout(mode, defaultWidth, expandedWidth, libraryWidth, centerGap, dock)
}
