package com.lin0721.linmusic.feature.artist.ui

import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSongSearchTest {

    private fun track(id: Long, vararg artistIds: Long) =
        Track(id = id, name = "歌$id", ar = artistIds.map { Artist(id = it, name = "歌手$it") })

    @Test
    fun keywordJoinsArtistNameAndQuery() {
        assertEquals("周杰伦 晴天", ArtistSongSearch.keyword("周杰伦", "晴天"))
    }

    @Test
    fun keywordTrimsBothParts() {
        assertEquals("周杰伦 晴天", ArtistSongSearch.keyword("  周杰伦 ", " 晴天  "))
    }

    @Test
    fun keywordWithBlankQueryIsJustArtistName() {
        assertEquals("周杰伦", ArtistSongSearch.keyword("周杰伦", "  "))
    }

    @Test
    fun filterKeepsOnlyTracksByThisArtist() {
        val tracks = listOf(track(1, 7), track(2, 8), track(3, 7, 8), track(4))

        val result = ArtistSongSearch.filterByArtist(tracks, artistId = 7)

        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun filterReturnsEmptyWhenNothingMatches() {
        assertTrue(ArtistSongSearch.filterByArtist(listOf(track(1, 8)), artistId = 7).isEmpty())
    }

    @Test
    fun searchStateIsActiveOnlyForNonBlankQuery() {
        assertFalse(ArtistSongSearchState().active)
        assertFalse(ArtistSongSearchState(query = "   ").active)
        assertTrue(ArtistSongSearchState(query = "晴天").active)
    }
}
