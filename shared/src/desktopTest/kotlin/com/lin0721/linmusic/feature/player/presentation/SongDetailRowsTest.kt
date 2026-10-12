package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import com.lin0721.linmusic.feature.player.domain.SongWikiCreatorRole
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDetailRowsTest {

    private fun memory(date: String = "", season: String = "", period: String = "", count: Int = 0, text: String = "") =
        SongMusicMemory(date, season, period, count, text)

    @Test
    fun `百科为空时没有任何行`() {
        assertEquals(emptyList<DetailRow>(), songDetailRows(SongWikiData(), null))
    }

    @Test
    fun `行按固定顺序出现且缺失项被跳过`() {
        val wiki = SongWikiData(style = "流行", language = "国语", bpm = "120")
        assertEquals(listOf("曲风", "语种", "BPM"), songDetailRows(wiki, null).map { it.label })
    }

    @Test
    fun `专辑缺失时回退到歌曲自身并可跳转`() {
        val track = Track(al = Album(id = 7, name = "专辑名"))
        val row = songDetailRows(SongWikiData(), track).single { it.label == "专辑" }
        assertEquals("专辑名", row.value)
        assertEquals(DetailAction.OpenAlbum(7, "专辑名"), row.action)
    }

    @Test
    fun `没有专辑id时专辑行不可点击`() {
        val row = songDetailRows(SongWikiData(album = "某专辑"), null).single { it.label == "专辑" }
        assertEquals(null, row.action)
    }

    @Test
    fun `发行时间优先用百科，缺失时用时间戳格式化`() {
        assertEquals("2020-01-02", songDetailRows(SongWikiData(publishTime = "2020-01-02"), Track(publishTime = 1L)).single().value)
        assertEquals("", formatPublishDate(0L))
        val formatted = formatPublishDate(1_600_000_000_000L)
        assertTrue(formatted.length == 10 && formatted[4] == '-' && formatted[7] == '-')
    }

    @Test
    fun `获奖行带总数提示，制作行有角色时可展开`() {
        val wiki = SongWikiData(
            awards = listOf("甲", "乙"),
            awardTotal = 5,
            creators = "丙",
            creatorRoles = listOf(SongWikiCreatorRole("作词", listOf("丙")))
        )
        val rows = songDetailRows(wiki, null)
        assertEquals("共 5 项", rows.single { it.label == "获奖成就" }.supportingText)
        assertEquals(DetailAction.ShowCreators, rows.single { it.label == "制作" }.action)
    }

    @Test
    fun `回忆坐标有首听日期或播放次数才算有内容`() {
        assertFalse(hasMusicMemoryContent(memory()))
        assertTrue(hasMusicMemoryContent(memory(date = "2026.08.06 22:39")))
        assertTrue(hasMusicMemoryContent(memory(count = 3)))
    }
}
