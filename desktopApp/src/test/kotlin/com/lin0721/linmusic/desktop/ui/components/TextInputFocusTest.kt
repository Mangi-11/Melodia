package com.lin0721.linmusic.desktop.ui.components

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputFocusTest {

    // 全局计数，用例结束后归零，避免影响其他用例
    @After
    fun tearDown() {
        repeat(10) { TextInputFocus.release() }
    }

    @Test
    fun activeWhileAnyHolderRemains() {
        TextInputFocus.acquire()
        TextInputFocus.acquire()

        TextInputFocus.release()
        assertTrue(TextInputFocus.isActive)

        TextInputFocus.release()
        assertFalse(TextInputFocus.isActive)
    }

    // 多余的释放不会让计数变成负数，之后一次占用仍然生效
    @Test
    fun extraReleaseDoesNotGoNegative() {
        TextInputFocus.release()
        TextInputFocus.release()

        TextInputFocus.acquire()

        assertTrue(TextInputFocus.isActive)
    }
}
