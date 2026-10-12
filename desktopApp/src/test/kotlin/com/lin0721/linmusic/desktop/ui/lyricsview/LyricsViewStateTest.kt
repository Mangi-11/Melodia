package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.desktop.ui.shell.FullscreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsViewStateTest {

    private fun create(placement: WindowPlacement = WindowPlacement.Floating): Triple<WindowState, FullscreenState, LyricsViewState> {
        val window = WindowState(placement = placement)
        val fullscreen = FullscreenState(window)
        return Triple(window, fullscreen, LyricsViewState(fullscreen))
    }

    @Test
    fun `仅打开歌词界面时收起不动窗口`() {
        val (window, _, view) = create()
        view.open()
        assertTrue(view.isOpen)
        view.close()
        assertFalse(view.isOpen)
        assertEquals(WindowPlacement.Floating, window.placement)
    }

    @Test
    fun `通过全屏入口进入时收起一并退出窗口全屏并恢复原状态`() {
        val (window, _, view) = create(WindowPlacement.Maximized)
        view.openWithFullscreen()
        assertEquals(WindowPlacement.Fullscreen, window.placement)
        view.close()
        assertEquals(WindowPlacement.Maximized, window.placement)
    }

    @Test
    fun `窗口本来就是全屏时收起保持全屏`() {
        val (window, fullscreen, view) = create()
        fullscreen.toggle()
        view.openWithFullscreen()
        view.close()
        assertEquals(WindowPlacement.Fullscreen, window.placement)
    }

    @Test
    fun `手动切换窗口全屏后窗口不再归属歌词界面`() {
        val (window, _, view) = create()
        view.openWithFullscreen()
        view.toggleFullscreen()
        view.toggleFullscreen()
        assertEquals(WindowPlacement.Fullscreen, window.placement)
        view.close()
        assertEquals(WindowPlacement.Fullscreen, window.placement)
    }

    @Test
    fun `未打开时收起无效果`() {
        val (window, fullscreen, view) = create()
        fullscreen.toggle()
        view.close()
        assertEquals(WindowPlacement.Fullscreen, window.placement)
    }

    @Test
    fun `沉浸态仅在界面打开且控件收起时成立`() {
        val (_, _, view) = create()
        view.controlsHidden = true
        assertFalse(view.isImmersive)
        view.open()
        assertFalse(view.isImmersive)
        view.controlsHidden = true
        assertTrue(view.isImmersive)
    }

    @Test
    fun `收起歌词界面后控件隐藏状态复位`() {
        val (_, _, view) = create()
        view.open()
        view.controlsHidden = true
        view.close()
        view.open()
        assertFalse(view.controlsHidden)
    }

    @Test
    fun `鼠标进出窗口状态默认在窗口内`() {
        val (_, _, view) = create()
        assertTrue(view.pointerInWindow)
        view.pointerInWindow = false
        assertFalse(view.pointerInWindow)
    }
}
