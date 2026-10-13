package com.lin0721.linmusic.feature.search.ui

import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSearchTabTest {

    private fun track(id: String) = ExternalTrack(
        id = id,
        name = "歌曲$id",
        artists = "歌手",
        platform = MusicPlatform.KUGOU
    )

    @Test
    fun `未加载的平台不显示角标`() {
        assertNull(ExternalSearchUiState.Idle.tabBadge())
    }

    @Test
    fun `加载中与失败显示状态文案`() {
        assertEquals("加载中", ExternalSearchUiState.Loading.tabBadge())
        assertEquals("失败", ExternalSearchUiState.Error("x").tabBadge())
    }

    @Test
    fun `空结果显示 0`() {
        assertEquals("0", ExternalSearchUiState.Empty.tabBadge())
    }

    @Test
    fun `仍有下一页时条数带加号`() {
        val tracks = listOf(track("1"), track("2"))
        assertEquals("2+", ExternalSearchUiState.Success(tracks, hasMore = true).tabBadge())
        assertEquals("2", ExternalSearchUiState.Success(tracks, hasMore = false).tabBadge())
    }

    @Test
    fun `聚合搜索开关默认关闭且可保存`() = runBlocking {
        val prefs = SourcePreferences(InMemoryPreferencesStore())
        assertFalse(prefs.searchAggregationEnabled.first())
        prefs.saveSearchAggregationEnabled(true)
        assertTrue(prefs.searchAggregationEnabled.first())
    }

    @Test
    fun `外部平台都有简称与品牌色`() {
        listOf(MusicPlatform.KUGOU, MusicPlatform.KUWO, MusicPlatform.QQ).forEach {
            assertTrue(it.shortName.isNotBlank())
            assertTrue(it.brandColorArgb != 0L)
        }
    }
}
