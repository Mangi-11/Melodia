package com.lin0721.linmusic.feature.playlist.presentation

import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSelectionTest {

    private fun track(id: Long) = Track(id = id, name = "歌$id")

    @Test
    fun startsInactive() {
        val selection = TrackSelection()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun enterPreselectsFirstTrack() {
        val selection = TrackSelection().enter(5L)

        assertTrue(selection.active)
        assertEquals(setOf(5L), selection.selectedIds)
    }

    @Test
    fun enterWithoutTrackStartsEmpty() {
        val selection = TrackSelection().enter()

        assertTrue(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun toggleAddsAndRemoves() {
        val selection = TrackSelection().enter(1L).toggle(2L).toggle(1L)

        assertEquals(setOf(2L), selection.selectedIds)
        assertTrue(selection.active)
    }

    @Test
    fun toggleIsIgnoredWhenInactive() {
        assertEquals(TrackSelection(), TrackSelection().toggle(1L))
    }

    @Test
    fun deselectingEverythingKeepsSelectionModeActive() {
        val selection = TrackSelection().enter(1L).toggle(1L)

        assertTrue(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun toggleAllSelectsMissingThenClearsWhenAllSelected() {
        val ids = listOf(1L, 2L, 3L)
        val partial = TrackSelection().enter(1L)

        val all = partial.toggleAll(ids)
        assertEquals(setOf(1L, 2L, 3L), all.selectedIds)

        val none = all.toggleAll(ids)
        assertEquals(0, none.count)
        assertTrue(none.active)
    }

    @Test
    fun toggleAllOnlyAffectsGivenIds() {
        val selection = TrackSelection().enter(9L).toggleAll(listOf(1L, 2L))

        assertEquals(setOf(9L, 1L, 2L), selection.selectedIds)
        assertEquals(setOf(9L), selection.toggleAll(listOf(1L, 2L)).selectedIds)
    }

    @Test
    fun toggleAllWithEmptyListIsNoOp() {
        val selection = TrackSelection().enter(1L)

        assertEquals(selection, selection.toggleAll(emptyList()))
    }

    @Test
    fun exitClearsEverything() {
        assertEquals(TrackSelection(), TrackSelection().enter(1L).toggle(2L).exit())
    }

    @Test
    fun pickKeepsListOrderAndIgnoresUnloadedIds() {
        val selection = TrackSelection().enter(3L).toggle(1L).toggle(99L)

        val picked = selection.pick(listOf(track(1), track(2), track(3)))

        assertEquals(listOf(1L, 3L), picked.map { it.id })
    }
}
