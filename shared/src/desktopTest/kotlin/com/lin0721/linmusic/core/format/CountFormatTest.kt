package com.lin0721.linmusic.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFormatTest {

    @Test
    fun smallNumbersStayPlain() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("9999", formatCompactCount(9_999))
    }

    @Test
    fun negativeIsClampedToZero() {
        assertEquals("0", formatCompactCount(-3))
    }

    @Test
    fun tenThousandsUseWanWithOneDecimal() {
        assertEquals("1万", formatCompactCount(10_000))
        assertEquals("1.2万", formatCompactCount(12_345))
        assertEquals("1.2万", formatCompactCount(12_999))
        assertEquals("99.9万", formatCompactCount(999_999))
    }

    @Test
    fun hundredMillionsUseYi() {
        assertEquals("1亿", formatCompactCount(100_000_000))
        assertEquals("2.5亿", formatCompactCount(254_000_000))
    }
}
