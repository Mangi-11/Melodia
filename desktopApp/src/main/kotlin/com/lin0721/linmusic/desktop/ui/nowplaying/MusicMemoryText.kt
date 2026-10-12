package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory

// 日期只取「2026.08.06 22:39」的日期部分，时刻由 period 表达
fun buildMusicMemoryText(memory: SongMusicMemory, emphasis: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        val date = memory.firstListenDate.substringBefore(' ').trim()
        if (date.isNotEmpty()) {
            append(date)
            val moment = listOf(memory.season, memory.period)
                .filter { it.isNotEmpty() }
                .joinToString("的")
            if (moment.isNotEmpty()) {
                append("，一个")
                withStyle(emphasis) { append(moment) }
            }
            append("，你第一次听到这首歌。")
        }
        if (memory.playCount > 0) {
            append(if (date.isNotEmpty()) "至今播放 " else "这首歌你已播放 ")
            withStyle(emphasis) { append("${memory.playCount} 次") }
            val analogy = memory.playCountText.trimEnd('。', '，', '！', '.', ',', '!')
            if (analogy.isNotEmpty()) append("，$analogy")
            append("。")
        }
    }
