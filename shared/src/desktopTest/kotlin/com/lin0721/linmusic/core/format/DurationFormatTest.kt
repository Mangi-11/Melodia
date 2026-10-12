package com.lin0721.linmusic.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `按分钟和两位秒数显示`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5_999))
        assertEquals("3:07", formatDuration(187_000))
    }

    @Test
    fun `超过一小时仍按分钟累计`() {
        assertEquals("61:01", formatDuration(3_661_000))
    }

    @Test
    fun `负数按零处理`() {
        assertEquals("0:00", formatDuration(-1_000))
    }
}
