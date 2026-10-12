package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.LibraryMode
import com.lin0721.linmusic.desktop.platform.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

interface PaneLayoutPersistence {
    fun saveLibraryMode(mode: LibraryMode)
    fun saveLibraryViewMode(mode: LibraryViewMode)
    fun saveLibraryWidth(widthDp: Float)
    fun saveDockWidth(widthDp: Float)
}

private class PreferencesPaneLayoutPersistence(
    private val preferences: DesktopPreferences,
    private val scope: CoroutineScope
) : PaneLayoutPersistence {
    override fun saveLibraryMode(mode: LibraryMode) {
        scope.launch { preferences.saveLibraryMode(mode) }
    }

    override fun saveLibraryViewMode(mode: LibraryViewMode) {
        scope.launch { preferences.saveLibraryViewMode(mode) }
    }

    override fun saveLibraryWidth(widthDp: Float) {
        scope.launch { preferences.saveLibraryWidth(widthDp) }
    }

    override fun saveDockWidth(widthDp: Float) {
        scope.launch { preferences.saveNowPlayingWidth(widthDp) }
    }
}

// 一次排版下两侧栏的可用宽度
@Immutable
data class PaneMeasure(
    val libraryMax: Dp,
    val libraryDefaultWidth: Dp,
    val dockMax: Dp,
    val dockOpenWidth: Dp
)

// 音乐库与正在播放栏的形态、记忆宽度与拖动；拖动期间只改内存值，松手才落盘
@Stable
class PaneLayoutState(
    initialMode: LibraryMode,
    initialViewMode: LibraryViewMode,
    initialLibraryWidth: Dp,
    initialDockWidth: Dp,
    private val persistence: PaneLayoutPersistence
) {
    var libraryMode by mutableStateOf(initialMode)
        private set
    var libraryViewMode by mutableStateOf(initialViewMode)
        private set
    var libraryWidthPref by mutableStateOf(initialLibraryWidth)
        private set
    var dockWidthPref by mutableStateOf(initialDockWidth)
        private set
    var resizing by mutableStateOf(false)
        private set

    private var libraryDragStart = 0.dp
    private var dockDragStart = 0.dp

    // 形态切换涉及右侧栏的联动，统一由 DesktopAppState.setLibraryMode 调用
    internal fun applyLibraryMode(mode: LibraryMode) {
        if (mode == libraryMode) return
        libraryMode = mode
        persistence.saveLibraryMode(mode)
    }

    fun updateViewMode(mode: LibraryViewMode) {
        if (mode == libraryViewMode) return
        libraryViewMode = mode
        persistence.saveLibraryViewMode(mode)
    }

    // 侧栏最宽不超过固定上限，且尽量给中间内容区留出 CenterMinWidth；
    // 两侧互相让位时，音乐库按正在播放栏的记忆宽度算，正在播放栏按音乐库的实际占用算
    fun measure(available: Dp, hasTrack: Boolean, dockOpen: Boolean): PaneMeasure {
        val dockStaticOccupied = when {
            !hasTrack -> 0.dp
            dockOpen -> dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, DesktopDimens.NowPlayingMaxWidth) +
                DesktopDimens.PaneGap
            else -> DesktopDimens.NowPlayingHandleWidth + DesktopDimens.PaneGap
        }
        val libraryMax = maxOf(
            DesktopDimens.LibraryMinWidth,
            minOf(DesktopDimens.LibraryMaxWidth, available - dockStaticOccupied - DesktopDimens.PaneGap - DesktopDimens.CenterMinWidth)
        )
        val libraryDefaultWidth = libraryWidthPref.coerceIn(DesktopDimens.LibraryMinWidth, libraryMax)
        val libraryOccupied = if (libraryMode == LibraryMode.RAIL) DesktopDimens.LibraryRailWidth else libraryDefaultWidth
        val dockMax = maxOf(
            DesktopDimens.NowPlayingMinWidth,
            minOf(
                DesktopDimens.NowPlayingMaxWidth,
                available - libraryOccupied - DesktopDimens.PaneGap * 2 - DesktopDimens.CenterMinWidth
            )
        )
        val dockOpenWidth = dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, dockMax)
        return PaneMeasure(libraryMax, libraryDefaultWidth, dockMax, dockOpenWidth)
    }

    fun startLibraryDrag(currentWidth: Dp) {
        resizing = true
        libraryDragStart = currentWidth
    }

    // 拖窄过阈值吸附成窄条，拖宽只停在上限，不会变成展开态
    fun dragLibrary(delta: Dp, libraryMax: Dp, setMode: (LibraryMode) -> Unit) {
        val proposed = libraryDragStart + delta
        if (proposed < DesktopDimens.PaneSnapWidth) {
            resizing = false
            if (libraryMode != LibraryMode.RAIL) setMode(LibraryMode.RAIL)
        } else {
            resizing = true
            if (libraryMode != LibraryMode.DEFAULT) setMode(LibraryMode.DEFAULT)
            libraryWidthPref = proposed.coerceIn(DesktopDimens.LibraryMinWidth, libraryMax)
        }
    }

    fun endLibraryDrag() {
        resizing = false
        persistence.saveLibraryWidth(libraryWidthPref.value)
    }

    fun startDockDrag(currentWidth: Dp) {
        resizing = true
        dockDragStart = currentWidth
    }

    // 右侧栏向右拖窄过阈值直接收起
    fun dragDock(delta: Dp, dockMax: Dp, dockOpen: Boolean, closeDock: () -> Unit) {
        val proposed = dockDragStart - delta
        if (proposed < DesktopDimens.PaneSnapWidth) {
            resizing = false
            if (dockOpen) closeDock()
        } else {
            resizing = true
            dockWidthPref = proposed.coerceIn(DesktopDimens.NowPlayingMinWidth, dockMax)
        }
    }

    fun endDockDrag() {
        resizing = false
        persistence.saveDockWidth(dockWidthPref.value)
    }
}

// 启动时同步读到上次的形态与宽度，避免先按默认值排版再动画过去
@Composable
fun rememberPaneLayoutState(preferences: DesktopPreferences, scope: CoroutineScope): PaneLayoutState =
    remember(preferences) {
        runBlocking {
            PaneLayoutState(
                initialMode = preferences.libraryMode.first(),
                initialViewMode = preferences.libraryViewMode.first(),
                initialLibraryWidth = (preferences.libraryWidth.first() ?: DesktopDimens.SidebarWidth.value).dp,
                initialDockWidth = (preferences.nowPlayingWidth.first() ?: DesktopDimens.NowPlayingWidth.value).dp,
                persistence = PreferencesPaneLayoutPersistence(preferences, scope)
            )
        }
    }
