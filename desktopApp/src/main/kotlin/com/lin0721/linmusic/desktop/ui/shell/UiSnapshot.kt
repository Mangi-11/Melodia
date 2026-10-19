package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Immutable
import com.lin0721.linmusic.desktop.data.LibraryMode
import com.lin0721.linmusic.desktop.data.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.nowplaying.DockOverlay
import com.lin0721.linmusic.feature.library.ui.LibraryFilter
import com.lin0721.linmusic.feature.library.ui.LibrarySortOrder
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

// 一步历史记下的界面状态，前进/后退时整体还原
@Immutable
data class UiSnapshot(
    val homeTab: Int,
    val showNewWorks: Boolean,
    val libraryMode: LibraryMode,
    val libraryViewMode: LibraryViewMode,
    val libraryFilter: LibraryFilter?,
    val librarySortOrder: LibrarySortOrder,
    val dockOpen: Boolean,
    val dockOverlay: DockOverlay?,
    val lyricsOpen: Boolean
)

// 音乐库筛选与排序的读写口，状态在共享 ViewModel 里
interface LibraryViewControl {
    val filter: LibraryFilter?
    val sortOrder: LibrarySortOrder
    fun toggleFilter(filter: LibraryFilter)
    fun selectFilter(filter: LibraryFilter?)
    fun updateSortOrder(order: LibrarySortOrder)
}

fun LibraryViewModel.asViewControl(): LibraryViewControl = object : LibraryViewControl {
    override val filter: LibraryFilter? get() = selectedFilter.value
    override val sortOrder: LibrarySortOrder get() = this@asViewControl.sortOrder.value
    override fun toggleFilter(filter: LibraryFilter) = this@asViewControl.toggleFilter(filter)
    override fun selectFilter(filter: LibraryFilter?) = this@asViewControl.selectFilter(filter)
    override fun updateSortOrder(order: LibrarySortOrder) = this@asViewControl.updateSortOrder(order)
}
