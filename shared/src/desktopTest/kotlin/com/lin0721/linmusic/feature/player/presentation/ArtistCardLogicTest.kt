package com.lin0721.linmusic.feature.player.presentation

import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.feature.player.ui.ArtistCardItem
import com.lin0721.linmusic.feature.player.ui.PlayerSongDetailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistCardLogicTest {

    private fun artist(id: Long, cover: String = "", avatar: String = "", withDetail: Boolean = true) =
        ArtistCardItem(
            artistId = id,
            artistName = "歌手$id",
            artistDetail = if (withDetail) ArtistDetailInfo(id = id, cover = cover, avatar = avatar) else null
        )

    @Test
    fun `没有详情或没有图片的歌手被过滤`() {
        val artists = listOf(
            artist(1, cover = "c"),
            artist(2, avatar = "a"),
            artist(3),
            artist(4, cover = "c", withDetail = false)
        )
        assertEquals(listOf(1L, 2L), validAboutArtists(artists).map { it.artistId })
    }

    @Test
    fun `短简介不可折叠，长简介折叠态截断展开态全文`() {
        val short = descPreview("  简介  ", expanded = false)
        assertEquals(DescPreview("简介", canToggle = false), short)

        val long = "字".repeat(120)
        val folded = descPreview(long, expanded = false)
        assertTrue(folded.canToggle)
        assertEquals("字".repeat(95) + "...", folded.text)
        assertEquals(long, descPreview(long, expanded = true).text)
    }

    @Test
    fun `轮播初始页对应当前选中的歌手，找不到时落在第一页`() {
        val all = listOf(artist(1, withDetail = false), artist(2, cover = "c"), artist(3, cover = "c"))
        val valid = validAboutArtists(all)
        assertEquals(1, initialAboutPage(valid, all, selectedIndex = 2))
        assertEquals(0, initialAboutPage(valid, all, selectedIndex = 0))
        assertEquals(0, initialAboutPage(valid, all, selectedIndex = 9))
    }

    @Test
    fun `关于艺人只在有可展示的歌手时出现`() {
        val order = listOf(FullPlayerCardSetting(FullPlayerCard.ABOUT_ARTIST, visible = true))
        val noImage = PlayerSongDetailState(artists = listOf(artist(1)))
        assertFalse(FullPlayerCard.ABOUT_ARTIST in visibleInfoCards(noImage, order))

        val ready = PlayerSongDetailState(artists = listOf(artist(1, cover = "c")))
        assertTrue(FullPlayerCard.ABOUT_ARTIST in visibleInfoCards(ready, order))
    }

    @Test
    fun `更多专辑标题带歌手名，缺失时用通用标题`() {
        assertEquals("某歌手的更多专辑", artistAlbumsTitle("某歌手"))
        assertEquals("更多专辑", artistAlbumsTitle(null))
        assertEquals("更多专辑", artistAlbumsTitle(" "))
    }

    @Test
    fun `专辑与相似艺人在加载完成且有数据时按配置顺序出现`() {
        val order = listOf(
            FullPlayerCardSetting(FullPlayerCard.ARTIST_ALBUMS, visible = true),
            FullPlayerCardSetting(FullPlayerCard.SIMILAR_ARTISTS, visible = true)
        )
        val albums = listOf(com.lin0721.linmusic.core.model.ArtistAlbum(id = 1, name = "专辑"))
        val similar = listOf(com.lin0721.linmusic.core.model.ArtistInfo(id = 2, name = "艺人", avatarUrl = ""))

        val loading = PlayerSongDetailState(isArtistAlbumsLoading = true, similarArtists = similar)
        assertEquals(emptyList<FullPlayerCard>(), visibleInfoCards(loading, order))

        val ready = PlayerSongDetailState(artistAlbums = albums, similarArtists = similar)
        assertEquals(listOf(FullPlayerCard.ARTIST_ALBUMS, FullPlayerCard.SIMILAR_ARTISTS), visibleInfoCards(ready, order))
    }
}
