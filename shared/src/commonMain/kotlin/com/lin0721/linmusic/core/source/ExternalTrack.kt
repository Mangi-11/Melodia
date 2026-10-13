package com.lin0721.linmusic.core.source

import kotlin.math.absoluteValue

// 外部平台歌曲通用模型，用于跨平台匹配与搜索结果
data class ExternalTrack(
    val id: String,
    val name: String,
    val artists: String,
    val artistList: List<String> = emptyList(),
    val albumName: String = "",
    val albumId: String = "",
    val durationMs: Long = 0,
    val coverUrl: String = "",
    val platform: MusicPlatform,
    // 酷狗专用 32 位 hex hash
    val hash: String? = null
) {
    // 播放队列里的歌曲 id；取负数，与网易云的正数 id 区分，避免被当成网易云歌曲去查词、打卡
    val queueSongId: Long
        get() = -1L - "${platform.key}:$id".hashCode().toLong().absoluteValue
}
