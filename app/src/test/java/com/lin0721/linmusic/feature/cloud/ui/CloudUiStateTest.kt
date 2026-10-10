package com.lin0721.linmusic.feature.cloud.ui

import com.lin0721.linmusic.feature.cloud.domain.CloudQuota
import com.lin0721.linmusic.feature.cloud.domain.CloudSong
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudUiStateTest {

    private val sampleSongs = listOf(
        CloudSong(
            songId = 1L,
            name = "七里香",
            artist = "周杰伦",
            album = "七里香",
            bitrate = 320000,
            fileSizeBytes = 10000000L,
            fileSizeText = "9.5 MB",
            addedAtText = "2024-01-01",
            coverUrl = null,
            isUnmatched = false
        ),
        CloudSong(
            songId = 2L,
            name = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            bitrate = 320000,
            fileSizeBytes = 9000000L,
            fileSizeText = "8.6 MB",
            addedAtText = "2024-01-02",
            coverUrl = null,
            isUnmatched = false
        ),
        CloudSong(
            songId = 3L,
            name = "Hello",
            artist = "Adele",
            album = "25",
            bitrate = 320000,
            fileSizeBytes = 11000000L,
            fileSizeText = "10.5 MB",
            addedAtText = "2024-01-03",
            coverUrl = null,
            isUnmatched = false
        )
    )

    private val baseQuota = CloudQuota(usedBytes = 30000000L, maxBytes = 100000000L, totalCount = 3)

    @Test
    fun `未激活搜索时返回全部歌曲`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = false,
            searchQuery = "周杰伦"
        )
        assertEquals(3, state.filteredSongs.size)
    }

    @Test
    fun `激活搜索但关键字为空时返回全部歌曲`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = true,
            searchQuery = "   "
        )
        assertEquals(3, state.filteredSongs.size)
    }

    @Test
    fun `按歌名匹配过滤歌曲`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = true,
            searchQuery = "七里香"
        )
        assertEquals(1, state.filteredSongs.size)
        assertEquals(1L, state.filteredSongs[0].songId)
    }

    @Test
    fun `按歌手匹配过滤歌曲`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = true,
            searchQuery = "周杰伦"
        )
        assertEquals(2, state.filteredSongs.size)
    }

    @Test
    fun `按专辑名匹配过滤歌曲`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = true,
            searchQuery = "叶惠美"
        )
        assertEquals(1, state.filteredSongs.size)
        assertEquals(2L, state.filteredSongs[0].songId)
    }

    @Test
    fun `英文字母匹配忽略大小写`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = true,
            searchQuery = "hello"
        )
        assertEquals(1, state.filteredSongs.size)
        assertEquals("Hello", state.filteredSongs[0].name)
    }

    @Test
    fun `无匹配项时返回空列表`() {
        val state = CloudUiState.Success(
            songs = sampleSongs,
            quota = baseQuota,
            hasMore = false,
            isSearchActive = true,
            searchQuery = "林俊杰"
        )
        assertEquals(0, state.filteredSongs.size)
    }
}
