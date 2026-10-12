package com.lin0721.linmusic.desktop.ui.lyricsview

import com.lin0721.linmusic.feature.player.presentation.isEndAligned
import com.lin0721.linmusic.core.player.domain.LyricAlignment
import com.lin0721.linmusic.core.player.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLogicTest {

    private fun box(line: Int, left: Float, right: Float) = KaraokeCharacterBox(line, left, right)

    // 单行布局：行宽 0..200，两个词各占一半，各 1000ms
    private fun singleLineInfo() = LyricLayoutInfo(
        wordLayouts = listOf(
            WordLayout(startMs = 0, endMs = 1000, left = 0f, right = 100f, top = 0f, bottom = 40f, lineIndex = 0),
            WordLayout(startMs = 1000, endMs = 2000, left = 100f, right = 200f, top = 0f, bottom = 40f, lineIndex = 0)
        ),
        lineLayouts = listOf(LineLayout(left = 0f, right = 200f, top = 0f, bottom = 40f))
    )

    @Test
    fun `词不跨行时整词一段，时间不变`() {
        val segments = splitTimedWordAcrossLines(500, 1000, listOf(box(0, 0f, 10f), box(0, 10f, 20f)))
        assertEquals(1, segments.size)
        assertEquals(KaraokeWordSegment(0, 0f, 20f, 500, 1500), segments.single())
    }

    @Test
    fun `词跨行时按各行字形宽度分配时间，首尾衔接`() {
        // 第一行宽 30，第二行宽 10：1000ms 按 3:1 分
        val segments = splitTimedWordAcrossLines(
            0, 1000,
            listOf(box(0, 0f, 15f), box(0, 15f, 30f), box(1, 0f, 10f))
        )
        assertEquals(2, segments.size)
        assertEquals(0L, segments[0].startMs)
        assertEquals(750L, segments[0].endMs)
        assertEquals(750L, segments[1].startMs)
        assertEquals(1000L, segments[1].endMs)
        assertEquals(1, segments[1].lineIndex)
    }

    @Test
    fun `没有字符时没有分段`() {
        assertEquals(emptyList<KaraokeWordSegment>(), splitTimedWordAcrossLines(0, 1000, emptyList()))
    }

    @Test
    fun `开唱前没有高亮，词内按比例插值并带羽化中心`() {
        val info = singleLineInfo()
        val before = computePlayedSpans(info, relativeProgress = -100, featherHalfPx = 8f).single()
        assertEquals(0f, before.clipRight, 0.001f)
        assertNull(before.featherCenterX)

        val mid = computePlayedSpans(info, relativeProgress = 500, featherHalfPx = 8f).single()
        assertEquals(50f, mid.featherCenterX!!, 0.001f)
        assertEquals(58f, mid.clipRight, 0.001f)
    }

    @Test
    fun `已唱完的词保持高亮，唱到下一个词时从其起点继续`() {
        val info = singleLineInfo()
        val second = computePlayedSpans(info, relativeProgress = 1500, featherHalfPx = 8f).single()
        assertEquals(150f, second.featherCenterX!!, 0.001f)
    }

    @Test
    fun `整行唱完后拉满且不再羽化`() {
        val done = computePlayedSpans(singleLineInfo(), relativeProgress = 2500, featherHalfPx = 8f).single()
        assertEquals(200f, done.clipRight, 0.001f)
        assertNull(done.featherCenterX)
    }

    @Test
    fun `羽化外扩不超过行右缘`() {
        val nearEnd = computePlayedSpans(singleLineInfo(), relativeProgress = 1990, featherHalfPx = 30f).single()
        assertEquals(200f, nearEnd.clipRight, 0.001f)
    }

    @Test
    fun `折行的词各行独立计算已播范围`() {
        val info = LyricLayoutInfo(
            wordLayouts = listOf(
                WordLayout(0, 1000, 0f, 100f, 0f, 40f, lineIndex = 0),
                WordLayout(1000, 2000, 0f, 60f, 40f, 80f, lineIndex = 1)
            ),
            lineLayouts = listOf(LineLayout(0f, 100f, 0f, 40f), LineLayout(0f, 60f, 40f, 80f))
        )
        val spans = computePlayedSpans(info, relativeProgress = 1500, featherHalfPx = 0f)
        assertEquals(100f, spans[0].clipRight, 0.001f)
        assertNull(spans[0].featherCenterX)
        assertEquals(30f, spans[1].clipRight, 0.001f)
    }

    @Test
    fun `外推位置：播放中随时间前进并有上限，暂停时停在锚点`() {
        assertEquals(1100L, extrapolatedPosition(1000, 0, 100_000_000L, playing = true))
        assertEquals(1250L, extrapolatedPosition(1000, 0, 5_000_000_000L, playing = true))
        assertEquals(1000L, extrapolatedPosition(1000, 0, 100_000_000L, playing = false))
        assertEquals(1000L, extrapolatedPosition(1000, 500, 100, playing = true))
    }

    @Test
    fun `对唱行靠右，其余靠左`() {
        assertTrue(isEndAligned(LyricLine(timeMs = 0, text = "词", alignment = LyricAlignment.END)))
        assertFalse(isEndAligned(LyricLine(timeMs = 0, text = "词", alignment = LyricAlignment.START)))
    }
}
