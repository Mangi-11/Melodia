package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.player.domain.SongMusicMemory
import com.lin0721.linmusic.feature.player.domain.SongWikiData
import java.time.Instant
import java.time.ZoneId

private const val AWARDS_MAX_LINES = 15
private const val CREATORS_MAX_LINES = 15
private const val DEFAULT_MAX_LINES = 3

// 点击行时触发的动作
sealed interface DetailAction {
    data class OpenAlbum(val id: Long, val name: String) : DetailAction
    data object ShowCreators : DetailAction
}

data class DetailRow(
    val label: String,
    val value: String,
    val maxLines: Int = DEFAULT_MAX_LINES,
    val supportingText: String? = null,
    val action: DetailAction? = null
)

// 歌曲详情卡的行：缺失项不出现；专辑与发行时间在百科缺失时回退到歌曲本身的信息
fun songDetailRows(wiki: SongWikiData, track: Track?): List<DetailRow> = buildList {
    if (wiki.style.isNotEmpty()) add(DetailRow("曲风", wiki.style))

    val albumName = wiki.album.ifEmpty { track?.al?.name.orEmpty() }
    if (albumName.isNotEmpty()) {
        val albumId = track?.al?.id ?: 0L
        add(DetailRow("专辑", albumName, action = if (albumId > 0L) DetailAction.OpenAlbum(albumId, albumName) else null))
    }

    if (wiki.language.isNotEmpty()) add(DetailRow("语种", wiki.language))

    val publishDate = wiki.publishTime.ifEmpty { formatPublishDate(track?.publishTime ?: 0L) }
    if (publishDate.isNotEmpty()) add(DetailRow("发行时间", publishDate))

    if (wiki.bpm.isNotEmpty()) add(DetailRow("BPM", wiki.bpm))
    if (wiki.entertainment.isNotEmpty()) add(DetailRow("影综", wiki.entertainment))

    if (wiki.awards.isNotEmpty()) {
        add(
            DetailRow(
                "获奖成就",
                wiki.awards.joinToString(" / "),
                maxLines = AWARDS_MAX_LINES,
                supportingText = if (wiki.awardTotal > wiki.awards.size) "共 ${wiki.awardTotal} 项" else null
            )
        )
    }

    if (wiki.creators.isNotEmpty()) {
        add(
            DetailRow(
                "制作",
                wiki.creators,
                maxLines = CREATORS_MAX_LINES,
                action = if (wiki.creatorRoles.isNotEmpty()) DetailAction.ShowCreators else null
            )
        )
    }
}

// 毫秒时间戳转 yyyy-MM-dd，非正数视为缺失
fun formatPublishDate(millis: Long): String =
    if (millis > 0L) Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString() else ""

fun hasMusicMemoryContent(memory: SongMusicMemory): Boolean =
    memory.firstListenDate.substringBefore(' ').trim().isNotEmpty() || memory.playCount > 0
