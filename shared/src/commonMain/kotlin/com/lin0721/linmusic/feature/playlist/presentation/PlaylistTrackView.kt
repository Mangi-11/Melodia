package com.lin0721.linmusic.feature.playlist.presentation

import com.lin0721.linmusic.core.model.Track
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class PlaylistSortKey(val label: String) {
    CUSTOM("自定义顺序"),
    TITLE("标题"),
    ARTIST("艺人"),
    ALBUM("专辑"),
    ADDED("添加日期"),
    DURATION("时长")
}

data class PlaylistSortOrder(val key: PlaylistSortKey = PlaylistSortKey.CUSTOM, val ascending: Boolean = true) {

    // 点列头：同一列切换方向，换列从升序开始；自定义顺序没有方向
    fun toggled(target: PlaylistSortKey): PlaylistSortOrder = when {
        target == PlaylistSortKey.CUSTOM -> PlaylistSortOrder()
        target == key -> copy(ascending = !ascending)
        else -> PlaylistSortOrder(target, true)
    }
}

private val zhCollator: Collator = Collator.getInstance(Locale.CHINA)

fun filterTracks(tracks: List<Track>, query: String): List<Track> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return tracks
    return tracks.filter { track ->
        track.name.contains(keyword, ignoreCase = true) ||
            track.al.name.contains(keyword, ignoreCase = true) ||
            track.ar.any { it.name.contains(keyword, ignoreCase = true) }
    }
}

// addedAt 为 歌曲 id 到加入时间戳的映射，缺失按 0 处理
fun sortTracks(tracks: List<Track>, order: PlaylistSortOrder, addedAt: Map<Long, Long>): List<Track> {
    val ascending = when (order.key) {
        PlaylistSortKey.CUSTOM -> return tracks
        PlaylistSortKey.TITLE -> tracks.sortedWith(compareBy(zhCollator) { it.name })
        PlaylistSortKey.ARTIST -> tracks.sortedWith(compareBy(zhCollator) { it.ar.firstOrNull()?.name.orEmpty() })
        PlaylistSortKey.ALBUM -> tracks.sortedWith(compareBy(zhCollator) { it.al.name })
        PlaylistSortKey.ADDED -> tracks.sortedBy { addedAt[it.id] ?: 0L }
        PlaylistSortKey.DURATION -> tracks.sortedBy { it.dt }
    }
    return if (order.ascending) ascending else ascending.reversed()
}

fun formatTotalDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    return when {
        hours > 0 -> "$hours 小时 $minutes 分钟"
        minutes > 0 -> "$minutes 分钟"
        else -> "$totalSeconds 秒"
    }
}

fun formatAddedDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epochMs <= 0) return ""
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}
