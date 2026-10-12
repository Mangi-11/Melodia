package com.lin0721.linmusic.core.format

import java.util.Locale

// 歌曲时长与播放进度，m:ss；超过一小时仍按分钟累计，负数按 0 处理
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
