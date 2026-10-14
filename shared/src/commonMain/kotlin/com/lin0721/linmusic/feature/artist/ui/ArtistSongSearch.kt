package com.lin0721.linmusic.feature.artist.ui

import com.lin0721.linmusic.core.model.Track

// 歌手歌曲搜索状态：results 已按当前歌手过滤，query 为输入框内容
data class ArtistSongSearchState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
) {
    val active: Boolean get() = query.isNotBlank()
}

// 歌手接口不支持关键词，借用云搜索：用"歌手名 关键词"搜单曲，再按歌手 id 过滤
object ArtistSongSearch {

    const val PAGE_SIZE = 30
    const val DEBOUNCE_MS = 300L

    // 过滤后结果过少时自动继续翻页，直到凑够下限或翻满上限
    const val MIN_RESULTS_PER_FETCH = 10
    const val MAX_PAGES_PER_FETCH = 3

    fun keyword(artistName: String, query: String): String = "${artistName.trim()} ${query.trim()}".trim()

    fun filterByArtist(tracks: List<Track>, artistId: Long): List<Track> =
        tracks.filter { track -> track.ar.any { it.id == artistId } }
}
