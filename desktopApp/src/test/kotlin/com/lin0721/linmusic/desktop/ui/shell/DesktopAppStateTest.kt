package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.desktop.platform.LibraryMode
import com.lin0721.linmusic.desktop.platform.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.CommentsHost
import com.lin0721.linmusic.desktop.ui.DockOverlay
import com.lin0721.linmusic.desktop.ui.FullscreenState
import com.lin0721.linmusic.desktop.ui.HOME_TAB_ALL
import com.lin0721.linmusic.desktop.ui.HOME_TAB_MUSIC
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DesktopAppStateTest {

    private class RecordingPersistence : PaneLayoutPersistence {
        val modes = mutableListOf<LibraryMode>()
        override fun saveLibraryMode(mode: LibraryMode) {
            modes += mode
        }
        override fun saveLibraryViewMode(mode: LibraryViewMode) = Unit
        override fun saveLibraryWidth(widthDp: Float) = Unit
        override fun saveDockWidth(widthDp: Float) = Unit
    }

    private val savedDockValues = mutableListOf<Boolean>()

    private fun create(mode: LibraryMode = LibraryMode.DEFAULT): DesktopAppState {
        val lyrics = LyricsViewState(FullscreenState(WindowState()))
        val pane = PaneLayoutState(mode, LibraryViewMode.LIST, 300.dp, 340.dp, RecordingPersistence())
        return DesktopAppState(lyrics, pane) { savedDockValues += it }
    }

    // 只用到对象身份，方法不会被调用
    private fun commentsHost(): CommentsHost =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommentsHost::class.java)) { _, _, _ -> null } as CommentsHost

    @Test
    fun backClosesLyricsFirstAndForwardReopensIt() {
        val state = create()
        state.navigate(DesktopRoute.Settings)
        state.lyricsView.open()

        state.navigateBack()
        assertFalse(state.lyricsView.isOpen)
        assertEquals(DesktopRoute.Settings, state.backStack.current)
        assertTrue(state.canGoForward)

        state.navigateForward()
        assertTrue(state.lyricsView.isOpen)
    }

    @Test
    fun newNavigationInvalidatesLyricsReopen() {
        val state = create()
        state.lyricsView.open()
        state.navigateBack()

        state.navigate(DesktopRoute.Search)

        assertFalse(state.canGoForward)
        state.navigateForward()
        assertFalse(state.lyricsView.isOpen)
    }

    @Test
    fun reopeningLyricsElsewhereInvalidatesForward() {
        val state = create()
        state.lyricsView.open()
        state.navigateBack()

        state.lyricsView.open()
        state.onLyricsOpenChanged(true)
        state.lyricsView.close()

        assertFalse(state.canGoForward)
    }

    @Test
    fun popBackLeavesLyricsAlone() {
        val state = create()
        state.navigate(DesktopRoute.Settings)
        state.lyricsView.open()

        state.popBack()

        assertEquals(DesktopRoute.Home, state.backStack.current)
        assertTrue(state.lyricsView.isOpen)
    }

    @Test
    fun goHomeAllResetsTabsAndClosesLyrics() {
        val state = create()
        state.selectHomeTab(HOME_TAB_MUSIC)
        state.showNewWorks = true
        state.navigate(DesktopRoute.Settings)
        state.lyricsView.open()
        assertFalse(state.isHomeAll)

        state.goHomeAll()

        assertEquals(DesktopRoute.Home, state.backStack.current)
        assertEquals(HOME_TAB_ALL, state.homeTab)
        assertFalse(state.showNewWorks)
        assertFalse(state.lyricsView.isOpen)
        assertTrue(state.isHomeAll)
    }

    @Test
    fun selectingMusicAgainReturnsToAll() {
        val state = create()
        state.selectHomeTab(HOME_TAB_MUSIC)
        state.selectHomeTab(HOME_TAB_MUSIC)
        assertEquals(HOME_TAB_ALL, state.homeTab)

        state.selectHomeTab(HOME_TAB_MUSIC)
        state.showNewWorks = true
        state.selectHomeTab(HOME_TAB_MUSIC)
        assertEquals(HOME_TAB_MUSIC, state.homeTab)
        assertFalse(state.showNewWorks)
    }

    @Test
    fun toggleOverlayOpensDockAndSecondToggleClosesPanel() {
        val state = create()

        state.toggleOverlay(DockOverlay.Queue)
        assertTrue(state.dockOpen)
        assertTrue(state.isOverlayOpen(DockOverlay.Queue))
        assertEquals(listOf(true), savedDockValues)

        state.toggleOverlay(DockOverlay.Queue)
        assertNull(state.dockOverlay)
        assertTrue(state.dockOpen)
    }

    @Test
    fun closingDockDropsOverlay() {
        val state = create()
        state.toggleOverlay(DockOverlay.Devices)

        state.setDockOpen(false)

        assertFalse(state.dockOpen)
        assertNull(state.dockOverlay)
    }

    @Test
    fun syncedClosedDockDropsOverlay() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)

        state.syncSavedDockOpen(false)

        assertNull(state.dockOverlay)
    }

    @Test
    fun toggleNowPlayingRevealsPanelBeforeCollapsing() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)

        state.toggleNowPlaying()
        assertTrue(state.dockOpen)
        assertNull(state.dockOverlay)

        state.toggleNowPlaying()
        assertFalse(state.dockOpen)
    }

    @Test
    fun playlistCommentsCloseWhenLeavingPage() {
        val state = create()
        val host = commentsHost()
        state.navigate(DesktopRoute.Playlist(1, "歌单"))
        state.toggleCommentsPanel(host)
        assertTrue(state.isCommentsPanelOpen(host))

        state.navigate(DesktopRoute.Settings)

        assertFalse(state.isCommentsPanelOpen(host))
        assertNull(state.dockOverlay)
    }

    @Test
    fun otherOverlaysSurvivePageChange() {
        val state = create()
        state.toggleOverlay(DockOverlay.Queue)

        state.navigate(DesktopRoute.Settings)

        assertEquals(DockOverlay.Queue, state.dockOverlay)
    }

    @Test
    fun expandedLibraryKeepsItsOwnDockStateWithoutPersisting() {
        val state = create()
        state.syncSavedDockOpen(true)

        state.setLibraryMode(LibraryMode.EXPANDED)
        assertTrue(state.dockOpen)
        state.setDockOpen(false)
        assertFalse(state.dockOpen)
        assertTrue(savedDockValues.isEmpty())

        state.setLibraryMode(LibraryMode.DEFAULT)
        assertTrue(state.dockOpen)
    }

    @Test
    fun openingLibraryItemCollapsesExpandedLibrary() {
        val state = create(LibraryMode.EXPANDED)

        state.openLibraryItem(LibraryItem("7", "专辑", "", "", LibraryItemType.ALBUM))

        assertEquals(LibraryMode.DEFAULT, state.pane.libraryMode)
        assertEquals(DesktopRoute.Playlist(7, "专辑", isAlbum = true), state.backStack.current)
    }

    @Test
    fun libraryItemWithInvalidIdDoesNotNavigate() {
        val state = create()

        state.openLibraryItem(LibraryItem("x", "坏数据", "", "", LibraryItemType.PLAYLIST))

        assertEquals(DesktopRoute.Home, state.backStack.current)
    }
}
