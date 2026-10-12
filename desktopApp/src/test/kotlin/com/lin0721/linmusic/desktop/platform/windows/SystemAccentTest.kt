package com.lin0721.linmusic.desktop.platform.windows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemAccentTest {

    private fun regOutput(prevalence: String, accent: String?) = buildString {
        appendLine()
        appendLine("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\DWM")
        appendLine("    Composition    REG_DWORD    0x1")
        appendLine("    ColorPrevalence    REG_DWORD    $prevalence")
        if (accent != null) appendLine("    AccentColor    REG_DWORD    $accent")
        appendLine("    ColorizationColor    REG_DWORD    0xc40078d4")
    }

    // AccentColor 按 0xAABBGGRR 存储，转换成 0xRRGGBB
    @Test
    fun convertsAbgrToRgbWhenBorderAccentEnabled() {
        assertEquals(0x0078D4, parseWindowBorderAccent(regOutput("0x1", "0xffd47800")))
    }

    @Test
    fun returnsNullWhenBorderAccentDisabled() {
        assertNull(parseWindowBorderAccent(regOutput("0x0", "0xffd47800")))
    }

    @Test
    fun returnsNullWhenAccentMissingOrOutputUnexpected() {
        assertNull(parseWindowBorderAccent(regOutput("0x1", null)))
        assertNull(parseWindowBorderAccent(""))
        assertNull(parseWindowBorderAccent("错误: 系统找不到指定的注册表项或值。"))
    }
}
