package com.lin0721.linmusic.desktop.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.components.LocalUiMotion
import com.lin0721.linmusic.desktop.ui.shell.fixedWidthAtStart
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

private const val MODE_SWITCH_MS = 150

// 音乐库容器：宽度随形态平滑过渡，各形态内容按自己的稳定宽度排版并被裁剪，形态切换时交叉淡化
@Composable
fun LibraryPane(
    mode: LibraryMode,
    width: Dp,
    defaultWidth: Dp,
    expandedWidth: Dp,
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onFilterToggle: (LibraryFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit
) {
    val motion = LocalUiMotion.current
    Box(
        Modifier.width(width).fillMaxHeight()
            .clip(RoundedCornerShape(DesktopDimens.PaneRadius))
            .background(DesktopColors.Pane)
    ) {
        AnimatedContent(
            targetState = mode,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                motion.transform(
                    fadeIn(tween(MODE_SWITCH_MS)) togetherWith fadeOut(tween(MODE_SWITCH_MS)) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                )
            },
            contentAlignment = Alignment.TopStart,
            label = "libraryMode"
        ) { current ->
            val contentWidth = when (current) {
                LibraryMode.RAIL -> DesktopDimens.LibraryRailWidth
                LibraryMode.DEFAULT -> defaultWidth
                LibraryMode.EXPANDED -> expandedWidth
            }
            Box(Modifier.fixedWidthAtStart(contentWidth)) {
                when (current) {
                    LibraryMode.RAIL -> LibraryRail(viewModel, isLoggedIn, onItemClick, onModeChange)
                    LibraryMode.DEFAULT -> LibrarySidebar(
                        viewModel, isLoggedIn, onLoginClick, onItemClick, onModeChange, viewMode, onViewModeChange,
                        onFilterToggle, onSortOrderChange
                    )
                    LibraryMode.EXPANDED -> LibraryExpanded(
                        viewModel, isLoggedIn, onLoginClick, onItemClick, onModeChange, viewMode, onViewModeChange,
                        onFilterToggle, onSortOrderChange
                    )
                }
            }
        }
    }
}
