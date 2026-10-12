package com.lin0721.linmusic.desktop.ui.nowplaying

import com.lin0721.linmusic.core.preferences.FullPlayerCard

// 已在桌面端实现的卡片；设置页只编辑这些卡片，正在播放面板也只展示这些卡片
val SupportedInfoCards: Set<FullPlayerCard> = setOf(
    FullPlayerCard.LYRICS,
    FullPlayerCard.COMMENTS_PREVIEW,
    FullPlayerCard.SONG_DETAIL,
    FullPlayerCard.MUSIC_MEMORY,
    FullPlayerCard.ABOUT_ARTIST,
    FullPlayerCard.ARTIST_ALBUMS,
    FullPlayerCard.SIMILAR_ARTISTS
)
