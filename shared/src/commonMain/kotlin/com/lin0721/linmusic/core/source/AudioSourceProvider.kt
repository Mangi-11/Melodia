package com.lin0721.linmusic.core.source

// 第三方音源 Provider 统一接口
interface AudioSourceProvider {

    val platform: MusicPlatform

    // 根据歌曲元数据搜索匹配并返回可播放 URL
    suspend fun resolveUrl(
        songId: String = "",
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String
    ): SourceResult?

    // 搜索歌曲
    suspend fun search(
        keyword: String,
        offset: Int = 0,
        limit: Int = 30
    ): List<ExternalTrack>
}
