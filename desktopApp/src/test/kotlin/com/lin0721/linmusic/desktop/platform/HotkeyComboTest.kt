package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.desktop.platform.windows.User32
import com.lin0721.linmusic.desktop.platform.windows.toWin32Modifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.KeyEvent as AwtKeyEvent

class HotkeyComboTest {

    @Test
    fun decodesPreviouslySavedWin32Values() {
        // 旧版直接保存 Win32 的 MOD_CONTROL|MOD_ALT 与 VK 码
        val combo = HotkeyCombo.decode("3:80")
        assertEquals(HotkeyCombo(HotkeyCombo.MOD_CTRL or HotkeyCombo.MOD_ALT, 'P'.code), combo)
        assertEquals("Ctrl+Alt+P", combo?.label)
    }

    @Test
    fun encodeRoundTrips() {
        HotkeyCombo.defaults.values.forEach { combo ->
            assertEquals(combo, HotkeyCombo.decode(combo.encode()))
        }
    }

    @Test
    fun rejectsMalformedOrInvalidValues() {
        assertNull(HotkeyCombo.decode(""))
        assertNull(HotkeyCombo.decode("3"))
        assertNull(HotkeyCombo.decode("a:b"))
        assertNull(HotkeyCombo.decode("3:80:1"))
        // 只有 Shift 不允许
        assertNull(HotkeyCombo.decode("4:80"))
        // 不支持的按键
        assertNull(HotkeyCombo.decode("3:${0xB3}"))
    }

    @Test
    fun requiresCtrlOrAlt() {
        assertFalse(HotkeyCombo.isValid(0, 'P'.code))
        assertFalse(HotkeyCombo.isValid(HotkeyCombo.MOD_SHIFT, 'P'.code))
        assertTrue(HotkeyCombo.isValid(HotkeyCombo.MOD_ALT, 'P'.code))
        assertTrue(HotkeyCombo.isValid(HotkeyCombo.MOD_CTRL or HotkeyCombo.MOD_SHIFT, AwtKeyEvent.VK_F5))
    }

    @Test
    fun mapsAwtKeyCodes() {
        assertEquals(0x2D, HotkeyCombo.keyCodeFromAwt(AwtKeyEvent.VK_INSERT))
        assertEquals(0x2E, HotkeyCombo.keyCodeFromAwt(AwtKeyEvent.VK_DELETE))
        assertEquals('A'.code, HotkeyCombo.keyCodeFromAwt(AwtKeyEvent.VK_A))
        assertEquals(AwtKeyEvent.VK_LEFT, HotkeyCombo.keyCodeFromAwt(AwtKeyEvent.VK_LEFT))
        assertEquals("Insert", HotkeyCombo.keyName(HotkeyCombo.keyCodeFromAwt(AwtKeyEvent.VK_INSERT)))
    }

    @Test
    fun buildsModifiersFromFlags() {
        assertEquals(0, HotkeyCombo.modifiersOf(ctrl = false, alt = false, shift = false))
        assertEquals(
            HotkeyCombo.MOD_CTRL or HotkeyCombo.MOD_SHIFT,
            HotkeyCombo.modifiersOf(ctrl = true, alt = false, shift = true)
        )
    }

    @Test
    fun convertsToWin32Modifiers() {
        assertEquals(User32.MOD_CONTROL, toWin32Modifiers(HotkeyCombo.MOD_CTRL))
        assertEquals(User32.MOD_ALT, toWin32Modifiers(HotkeyCombo.MOD_ALT))
        assertEquals(User32.MOD_SHIFT, toWin32Modifiers(HotkeyCombo.MOD_SHIFT))
        assertEquals(
            User32.MOD_CONTROL or User32.MOD_ALT or User32.MOD_SHIFT,
            toWin32Modifiers(HotkeyCombo.MOD_CTRL or HotkeyCombo.MOD_ALT or HotkeyCombo.MOD_SHIFT)
        )
    }
}
