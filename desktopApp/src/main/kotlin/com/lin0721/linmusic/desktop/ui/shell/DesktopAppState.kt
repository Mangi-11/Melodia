package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.desktop.platform.LibraryMode
import com.lin0721.linmusic.desktop.ui.CommentsHost
import com.lin0721.linmusic.desktop.ui.DockOverlay
import com.lin0721.linmusic.desktop.ui.HOME_TAB_ALL
import com.lin0721.linmusic.desktop.ui.HOME_TAB_MUSIC
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// 应用壳的界面状态：导航历史、首页标签、歌词界面并入前进后退、右侧栏与覆盖面板、登录框与全局提示
@Stable
class DesktopAppState(
    val lyricsView: LyricsViewState,
    val pane: PaneLayoutState,
    private val persistDockOpen: (Boolean) -> Unit
) {
    val backStack = BackStack(DesktopRoute.Home)

    var homeTab by mutableStateOf(HOME_TAB_ALL)
        private set
    var showNewWorks by mutableStateOf(false)
    var showLogin by mutableStateOf(false)

    // 歌词界面被“后退”收起后，“前进”可把它重新打开；有新的导航或歌词被重新打开后失效
    private var lyricsReopenable by mutableStateOf(false)

    // 初值为关闭：读到已保存的开启状态后，侧栏随动画展开
    private var savedDockOpen by mutableStateOf(false)

    // 音乐库展开时右侧栏保持原状态，期间的开合只在本次展开内有效，不改保存的开关值
    private var expandedDockOpen by mutableStateOf(false)

    var dockOverlay by mutableStateOf<DockOverlay?>(null)
        private set
    var playlistComments by mutableStateOf<CommentsHost?>(null)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    // ─── 导航 ───

    val canGoBack: Boolean get() = lyricsView.isOpen || backStack.canGoBack
    val canGoForward: Boolean get() = lyricsReopenable || backStack.canGoForward

    val isHomeAll: Boolean
        get() = backStack.current == DesktopRoute.Home && homeTab == HOME_TAB_ALL && !showNewWorks && !lyricsView.isOpen

    fun navigate(route: DesktopRoute) = changeEntry { backStack.navigate(route) }

    // 页面内的返回（如删除歌单后）只回退历史，不涉及歌词界面
    fun popBack() = changeEntry { backStack.back() }

    // 标题栏与鼠标侧键的后退：先收起歌词界面，再回退历史
    fun navigateBack() {
        if (lyricsView.isOpen) {
            lyricsView.close()
            lyricsReopenable = true
        } else {
            changeEntry { backStack.back() }
        }
    }

    fun navigateForward() {
        if (lyricsReopenable && !lyricsView.isOpen) {
            lyricsView.open()
        } else {
            changeEntry { backStack.forward() }
        }
    }

    fun goHomeAll() {
        lyricsView.close()
        homeTab = HOME_TAB_ALL
        showNewWorks = false
        navigate(DesktopRoute.Home)
    }

    // 点任意主胶囊都回到该 tab 的默认内容，「最新」只能由二级胶囊单独选中；
    // 已在「音乐」默认内容时再点「音乐」则回到「全部」，收起二级胶囊
    fun selectHomeTab(tab: Int) {
        homeTab = if (tab == HOME_TAB_MUSIC && homeTab == HOME_TAB_MUSIC && !showNewWorks) HOME_TAB_ALL else tab
        showNewWorks = false
    }

    fun onLyricsOpenChanged(open: Boolean) {
        if (open) lyricsReopenable = false
    }

    fun openLibraryItem(item: LibraryItem) {
        if (pane.libraryMode == LibraryMode.EXPANDED) setLibraryMode(LibraryMode.DEFAULT)
        val id = item.id.toLongOrNull() ?: return
        navigate(
            when (item.type) {
                LibraryItemType.PLAYLIST -> DesktopRoute.Playlist(id, item.title)
                LibraryItemType.ALBUM -> DesktopRoute.Playlist(id, item.title, isAlbum = true)
                LibraryItemType.ARTIST -> DesktopRoute.Artist(id, item.title)
            }
        )
    }

    // 换页后歌单评论（属于原页面）收起，被后退收起的歌词界面不再能前进打开
    private inline fun changeEntry(block: () -> Unit) {
        val before = backStack.currentEntry.id
        block()
        if (backStack.currentEntry.id != before) {
            if (dockOverlay == DockOverlay.PlaylistComments) dockOverlay = null
            lyricsReopenable = false
        }
    }

    // ─── 右侧栏 ───

    val dockOpen: Boolean
        get() = if (pane.libraryMode == LibraryMode.EXPANDED) expandedDockOpen else savedDockOpen

    fun syncSavedDockOpen(open: Boolean) {
        savedDockOpen = open
        afterDockChange()
    }

    fun setDockOpen(open: Boolean) {
        if (pane.libraryMode == LibraryMode.EXPANDED) {
            expandedDockOpen = open
        } else {
            savedDockOpen = open
            persistDockOpen(open)
        }
        afterDockChange()
    }

    fun setLibraryMode(mode: LibraryMode) {
        if (mode == LibraryMode.EXPANDED && pane.libraryMode != LibraryMode.EXPANDED) expandedDockOpen = savedDockOpen
        pane.applyLibraryMode(mode)
        afterDockChange()
    }

    fun isOverlayOpen(target: DockOverlay): Boolean = dockOpen && dockOverlay == target

    // 覆盖面板已显示则关掉它；否则展开右侧栏并把面板盖上去
    fun toggleOverlay(target: DockOverlay) {
        if (isOverlayOpen(target)) {
            dockOverlay = null
        } else {
            dockOverlay = target
            setDockOpen(true)
        }
    }

    fun closeOverlay() {
        dockOverlay = null
    }

    // 盖着覆盖面板时点封面是撤掉面板露出正在播放页，而不是收起右侧栏
    fun toggleNowPlaying() {
        if (dockOpen && dockOverlay != null) dockOverlay = null else setDockOpen(!dockOpen)
    }

    fun isCommentsPanelOpen(host: CommentsHost): Boolean =
        isOverlayOpen(DockOverlay.PlaylistComments) && playlistComments === host

    fun toggleCommentsPanel(host: CommentsHost) {
        if (isCommentsPanelOpen(host)) {
            dockOverlay = null
        } else {
            playlistComments = host
            dockOverlay = DockOverlay.PlaylistComments
            setDockOpen(true)
        }
    }

    // 右侧栏收起后覆盖面板一并撤掉，下次展开回到正在播放页
    private fun afterDockChange() {
        if (!dockOpen) dockOverlay = null
    }
}
