package com.lin0721.linmusic.feature.playlist.presentation

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTrackViewTest {

    private fun track(id: Long, name: String, artist: String = "", album: String = "", dt: Long = 0) =
        Track(id = id, name = name, ar = if (artist.isEmpty()) emptyList() else listOf(Artist(id = id, name = artist)), al = Album(name = album), dt = dt)

    private val tracks = listOf(
        track(1, "Blue", "曹格", "Z专辑", dt = 300_000),
        track(2, "阿刁", "Adele", "B专辑", dt = 100_000),
        track(3, "晴天", "周杰伦", "叶惠美", dt = 200_000)
    )

    private fun ids(list: List<Track>) = list.map { it.id }

    @Test
    fun blankQueryKeepsAll() {
        assertEquals(tracks, filterTracks(tracks, ""))
        assertEquals(tracks, filterTracks(tracks, "   "))
    }

    @Test
    fun queryMatchesNameArtistAndAlbumIgnoringCase() {
        assertEquals(listOf(1L), ids(filterTracks(tracks, "blue")))
        assertEquals(listOf(2L), ids(filterTracks(tracks, "ADELE")))
        assertEquals(listOf(3L), ids(filterTracks(tracks, "叶惠")))
        assertEquals(emptyList<Long>(), ids(filterTracks(tracks, "不存在")))
    }

    @Test
    fun customOrderIsUntouched() {
        assertEquals(tracks, sortTracks(tracks, PlaylistSortOrder(), emptyMap()))
    }

    @Test
    fun sortByTitleUsesPinyinForChinese() {
        val sorted = sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.TITLE), emptyMap())
        assertEquals(listOf(1L, 2L, 3L), ids(sorted))
    }

    @Test
    fun sortByArtistAlbumAndDuration() {
        assertEquals(listOf(2L, 1L, 3L), ids(sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.ARTIST), emptyMap())))
        assertEquals(listOf(2L, 1L, 3L), ids(sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.ALBUM), emptyMap())))
        assertEquals(listOf(2L, 3L, 1L), ids(sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.DURATION), emptyMap())))
    }

    @Test
    fun descendingReversesAscending() {
        val asc = sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.DURATION, true), emptyMap())
        val desc = sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.DURATION, false), emptyMap())
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun sortByAddedUsesTimestampsAndTreatsMissingAsZero() {
        val added = mapOf(1L to 300L, 3L to 100L)
        val sorted = sortTracks(tracks, PlaylistSortOrder(PlaylistSortKey.ADDED), added)
        assertEquals(listOf(2L, 3L, 1L), ids(sorted))
    }

    @Test
    fun toggleSwitchesDirectionOnSameColumnAndResetsOnNewColumn() {
        val title = PlaylistSortOrder().toggled(PlaylistSortKey.TITLE)
        assertEquals(PlaylistSortOrder(PlaylistSortKey.TITLE, true), title)
        assertEquals(PlaylistSortOrder(PlaylistSortKey.TITLE, false), title.toggled(PlaylistSortKey.TITLE))
        assertEquals(PlaylistSortOrder(PlaylistSortKey.ALBUM, true), title.toggled(PlaylistSortKey.TITLE).toggled(PlaylistSortKey.ALBUM))
        assertEquals(PlaylistSortOrder(), title.toggled(PlaylistSortKey.CUSTOM))
    }

    @Test
    fun totalDurationFormats() {
        assertEquals("9 小时 33 分钟", formatTotalDuration((9 * 3600 + 33 * 60 + 20) * 1000L))
        assertEquals("33 分钟", formatTotalDuration(33 * 60 * 1000L))
        assertEquals("45 秒", formatTotalDuration(45_000))
        assertEquals("0 秒", formatTotalDuration(-5))
    }

    @Test
    fun addedDateFormatsInGivenZoneAndHidesMissing() {
        val utc = ZoneId.of("UTC")
        assertEquals("2025年3月2日", formatAddedDate(1_740_873_600_000L, utc))
        assertEquals("", formatAddedDate(0, utc))
    }
}
