package com.lin0721.linmusic.core.preferences

import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCard.ABOUT_ARTIST
import com.lin0721.linmusic.core.preferences.FullPlayerCard.COMMENTS_PREVIEW
import com.lin0721.linmusic.core.preferences.FullPlayerCard.LYRICS
import com.lin0721.linmusic.core.preferences.FullPlayerCard.SONG_DETAIL
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class CardLayoutOpsTest {

    private val supported = setOf(LYRICS, SONG_DETAIL, ABOUT_ARTIST)

    private fun layout(vararg cards: FullPlayerCard) = cards.map { FullPlayerCardSetting(it, visible = true) }

    @Test
    fun `调序结果只填回已实现卡片的位置，未实现的卡片原地不动`() {
        val full = layout(LYRICS, COMMENTS_PREVIEW, SONG_DETAIL, ABOUT_ARTIST)
        val reordered = layout(ABOUT_ARTIST, LYRICS, SONG_DETAIL)
        val merged = mergeSupportedOrder(full, reordered, supported)
        assertEquals(listOf(ABOUT_ARTIST, COMMENTS_PREVIEW, LYRICS, SONG_DETAIL), merged.map { it.card })
    }

    @Test
    fun `调序结果数量不符时保持原配置`() {
        val full = layout(LYRICS, COMMENTS_PREVIEW, SONG_DETAIL, ABOUT_ARTIST)
        assertEquals(full, mergeSupportedOrder(full, layout(LYRICS), supported))
    }

    @Test
    fun `调序保留各卡片自己的显隐`() {
        val full = listOf(
            FullPlayerCardSetting(LYRICS, visible = true),
            FullPlayerCardSetting(SONG_DETAIL, visible = false)
        )
        val merged = mergeSupportedOrder(full, full.reversed(), setOf(LYRICS, SONG_DETAIL))
        assertEquals(listOf(SONG_DETAIL to false, LYRICS to true), merged.map { it.card to it.visible })
    }

    @Test
    fun `切换显隐只影响目标卡片`() {
        val full = layout(LYRICS, SONG_DETAIL)
        val toggled = setCardVisible(full, SONG_DETAIL, visible = false)
        assertEquals(listOf(LYRICS to true, SONG_DETAIL to false), toggled.map { it.card to it.visible })
    }
}
