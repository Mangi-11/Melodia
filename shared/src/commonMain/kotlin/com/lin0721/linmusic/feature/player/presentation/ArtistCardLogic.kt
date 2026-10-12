package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.feature.player.ui.ArtistCardItem

private const val DESC_PREVIEW_LENGTH = 95

// 关于艺人只展示有封面或头像的歌手，缺图的歌手整张卡片没有可看的内容
fun validAboutArtists(artists: List<ArtistCardItem>): List<ArtistCardItem> =
    artists.filter { item ->
        val detail = item.artistDetail
        detail != null && (detail.cover.isNotBlank() || detail.avatar.isNotBlank())
    }

// 简介折叠态的显示：超过阈值时截断，展开态显示全文；canToggle 表示是否可展开/收起
data class DescPreview(val text: String, val canToggle: Boolean)

fun descPreview(desc: String, expanded: Boolean): DescPreview {
    val clean = desc.trim()
    val canToggle = clean.length > DESC_PREVIEW_LENGTH
    return DescPreview(if (canToggle && !expanded) clean.take(DESC_PREVIEW_LENGTH) + "..." else clean, canToggle)
}

// 轮播里当前选中的歌手在有效列表中的位置，找不到时落在第一位
fun initialAboutPage(valid: List<ArtistCardItem>, all: List<ArtistCardItem>, selectedIndex: Int): Int {
    val selectedId = all.getOrNull(selectedIndex)?.artistId
    return valid.indexOfFirst { it.artistId == selectedId }.coerceAtLeast(0)
}

// 更多专辑卡片标题，歌手名缺失时用通用标题
fun artistAlbumsTitle(artistName: String?): String =
    if (artistName.isNullOrBlank()) "更多专辑" else "${artistName}的更多专辑"
