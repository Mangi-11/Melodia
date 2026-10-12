package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.ui.text.SpanStyle
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicMemoryTextTest {

    private fun memory(date: String = "", season: String = "", period: String = "", count: Int = 0, text: String = "") =
        SongMusicMemory(date, season, period, count, text)

    @Test
    fun `回忆坐标文案只取日期并拼接时段与次数`() {
        val text = buildMusicMemoryText(
            memory(date = "2026.08.06 22:39", season = "夏天", period = "深夜", count = 12, text = "相当于听了一个周末。"),
            SpanStyle()
        ).text
        assertEquals("2026.08.06，一个夏天的深夜，你第一次听到这首歌。至今播放 12 次，相当于听了一个周末。", text)
    }

    @Test
    fun `没有首听日期时只说播放次数`() {
        val text = buildMusicMemoryText(memory(count = 3), SpanStyle()).text
        assertEquals("这首歌你已播放 3 次。", text)
    }
}
