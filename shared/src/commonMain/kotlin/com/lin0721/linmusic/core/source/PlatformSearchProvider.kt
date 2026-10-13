package com.lin0721.linmusic.core.source

// 第三方平台歌曲搜索，仅用于聚合搜索展示，不参与播放链接解析
interface PlatformSearchProvider {

    val platform: MusicPlatform

    suspend fun search(
        keyword: String,
        offset: Int = 0,
        limit: Int = 30
    ): List<ExternalTrack>
}
