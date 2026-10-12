package com.lin0721.linmusic.desktop.ui.download

import com.lin0721.linmusic.desktop.data.download.DownloadTask
import com.lin0721.linmusic.desktop.data.download.DownloadTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRoundTrackerTest {

    private fun task(id: String, status: DownloadTaskStatus, progress: Int = 0) = DownloadTask(
        id = id,
        songId = id.hashCode().toLong(),
        songName = id,
        artistName = "",
        level = "standard",
        status = status,
        progress = progress,
        createdAt = 0
    )

    @Test
    fun idleWhenNoTasks() {
        assertEquals(DownloadRoundSummary(), DownloadRoundTracker().update(emptyList()))
    }

    @Test
    fun progressIsAverageOfActiveTasks() {
        val summary = DownloadRoundTracker().update(
            listOf(task("a", DownloadTaskStatus.DOWNLOADING, 50), task("b", DownloadTaskStatus.QUEUED, 0))
        )
        assertEquals(0.25f, summary.progress!!, 0.001f)
        assertFalse(summary.finished)
    }

    @Test
    fun finishedTaskKeepsCountingSoProgressDoesNotDrop() {
        val tracker = DownloadRoundTracker()
        tracker.update(listOf(task("a", DownloadTaskStatus.DOWNLOADING, 90), task("b", DownloadTaskStatus.DOWNLOADING, 10)))

        val summary = tracker.update(listOf(task("a", DownloadTaskStatus.SUCCEEDED, 100), task("b", DownloadTaskStatus.DOWNLOADING, 20)))

        assertEquals(0.6f, summary.progress!!, 0.001f)
    }

    @Test
    fun allSucceededEndsRoundWithCheck() {
        val tracker = DownloadRoundTracker()
        tracker.update(listOf(task("a", DownloadTaskStatus.DOWNLOADING, 50)))

        val summary = tracker.update(listOf(task("a", DownloadTaskStatus.SUCCEEDED, 100)))

        assertNull(summary.progress)
        assertTrue(summary.finished)
        assertTrue(summary.allSucceeded)
        assertEquals("下一次更新应回到空闲", DownloadRoundSummary(), tracker.update(listOf(task("a", DownloadTaskStatus.SUCCEEDED, 100))))
    }

    @Test
    fun failureEndsRoundWithoutCheck() {
        val tracker = DownloadRoundTracker()
        tracker.update(listOf(task("a", DownloadTaskStatus.DOWNLOADING, 50), task("b", DownloadTaskStatus.DOWNLOADING, 50)))

        val summary = tracker.update(listOf(task("a", DownloadTaskStatus.SUCCEEDED, 100), task("b", DownloadTaskStatus.FAILED, 50)))

        assertTrue(summary.finished)
        assertFalse(summary.allSucceeded)
    }

    @Test
    fun pausingEverythingEndsRoundWithoutCheck() {
        val tracker = DownloadRoundTracker()
        tracker.update(listOf(task("a", DownloadTaskStatus.DOWNLOADING, 50)))

        val summary = tracker.update(listOf(task("a", DownloadTaskStatus.PAUSED, 50)))

        assertTrue(summary.finished)
        assertFalse(summary.allSucceeded)
    }

    @Test
    fun cancelledTaskLeavesTheRound() {
        val tracker = DownloadRoundTracker()
        tracker.update(listOf(task("a", DownloadTaskStatus.DOWNLOADING, 80), task("b", DownloadTaskStatus.DOWNLOADING, 0)))

        val summary = tracker.update(listOf(task("a", DownloadTaskStatus.DOWNLOADING, 80)))

        assertEquals(0.8f, summary.progress!!, 0.001f)
    }

    @Test
    fun taskAddedMidRoundJoinsIt() {
        val tracker = DownloadRoundTracker()
        tracker.update(listOf(task("a", DownloadTaskStatus.SUCCEEDED, 100), task("b", DownloadTaskStatus.DOWNLOADING, 100)))

        val summary = tracker.update(
            listOf(
                task("a", DownloadTaskStatus.SUCCEEDED, 100),
                task("b", DownloadTaskStatus.DOWNLOADING, 100),
                task("c", DownloadTaskStatus.QUEUED, 0)
            )
        )

        // 上一轮已完成的 a 不在本轮，本轮只有 b 与新加入的 c
        assertEquals(0.5f, summary.progress!!, 0.001f)
    }
}
