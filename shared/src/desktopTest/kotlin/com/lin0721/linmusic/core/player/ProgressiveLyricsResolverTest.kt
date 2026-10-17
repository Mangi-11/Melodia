package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.WordInfo
import java.lang.reflect.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressiveLyricsResolverTest {
    private val plain = listOf(LyricLine(1000, 1000, "网易歌词"))
    private val word = listOf(LyricLine(1000, 1000, "网易逐字", words = listOf(WordInfo("网易逐字", 0, 1000))))
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s"><span begin="1s" end="2s">AMLL</span></p></div></body></tt>"""

    private fun repo(result: Result<List<LyricLine>>, waitMs: Long = 0): PlaybackRepository = Proxy.newProxyInstance(
        PlaybackRepository::class.java.classLoader, arrayOf(PlaybackRepository::class.java)
    ) { _, method, _ ->
        check(method.name == "getLyrics")
        flow { delay(waitMs); emit(result) }
    } as PlaybackRepository

    @Test fun neteaseAppearsBeforeSlowAmllThenUpgrades() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain), 10), { null }, { null },
            readAmllLyrics = { delay(3000); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, ResolvedLyrics>>()
        val job = launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow() } }
        advanceTimeBy(10); runCurrent()
        assertEquals(listOf(10L), received.map { it.first })
        assertEquals(LyricsSource.NETEASE, received.single().second.source)
        advanceUntilIdle(); job.join()
        assertEquals(listOf(10L, 3000L), received.map { it.first })
        assertEquals(LyricsSource.AMLL, received.last().second.source)
    }

    @Test fun slowNeteaseDoesNotBlockFastAmllOrDowngradeIt() = runTest {
        val resolver = LyricsResolver(repo(Result.success(word), 3000), { null }, { null },
            readAmllLyrics = { delay(10); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler))
        val received = mutableListOf<Pair<Long, ResolvedLyrics>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow() } }
        advanceTimeBy(10); runCurrent()
        assertEquals(10L, received.single().first)
        assertEquals(LyricsSource.AMLL, received.single().second.source)
        advanceUntilIdle()
        assertEquals(1, received.size)
    }

    @Test fun amllFailureDoesNotClearOrRepeatNetease() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            readAmllLyrics = { delay(1000); error("offline") }, processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList()
        assertEquals(1, results.size)
        assertEquals(LyricsSource.NETEASE, results.single().getOrThrow().source)
    }

    @Test fun malformedAmllTimingDoesNotReplaceNeteaseWordTiming() = runTest {
        val bad = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">普通行</p></div></body></tt>"""
        val resolver = LyricsResolver(repo(Result.success(word)), { null }, { null },
            readAmllLyrics = { delay(100); bad }, processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList()
        assertEquals(1, results.size)
        assertEquals(LyricsSource.NETEASE, results.single().getOrThrow().source)
    }

    @Test fun earlyNeteaseErrorDoesNotHideLaterAmll() = runTest {
        val resolver = LyricsResolver(repo(Result.failure(IllegalStateException("offline"))), { null }, { null },
            readAmllLyrics = { delay(100); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList()
        assertEquals(1, results.size)
        assertEquals(LyricsSource.AMLL, results.single().getOrThrow().source)
    }

    @Test fun emptyResultsAreOnlyEmittedAfterAllSourcesFinish() = runTest {
        val resolver = LyricsResolver(repo(Result.success(emptyList())), { null }, { null },
            readAmllLyrics = { delay(1000); null }, processingDispatcher = StandardTestDispatcher(testScheduler))
        var received = false
        launch { resolver.lyricsFor(1).collect { received = true; assertTrue(it.getOrThrow().isEmpty()) } }
        runCurrent()
        assertFalse(received)
        advanceUntilIdle()
        assertTrue(received)
    }

    @Test fun timeoutPreservesOriginalNeteaseError() = runTest {
        val failure = IllegalStateException("offline")
        val resolver = LyricsResolver(repo(Result.failure(failure)), { null }, { null },
            readAmllLyrics = { delay(20000); null }, processingDispatcher = StandardTestDispatcher(testScheduler))
        assertSame(failure, resolver.lyricsFor(1).toList().single().exceptionOrNull())
        assertEquals(12000L, testScheduler.currentTime)
    }

    @Test fun matchedLocalSongKeepsCollectingOnlineUpgrades() = runTest {
        var localReads = 0
        val resolver = LyricsResolver(repo(Result.success(plain)), { localReads++; "[00:01.00]本地" }, { "local" },
            readAmllLyrics = { delay(100); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler))
        val results = resolver.lyricsWithSourceFor(1).toList().map { it.getOrThrow().source }
        assertEquals(listOf(LyricsSource.NETEASE, LyricsSource.AMLL), results)
        assertEquals(0, localReads)
    }

    @Test fun cancellingAfterFirstResultCancelsOtherSource() = runTest {
        var cancelled = false
        val resolver = LyricsResolver(repo(Result.success(plain), 10), { null }, { null },
            readAmllLyrics = { try { delay(3000); ttml } finally { cancelled = true } },
            processingDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(LyricsSource.NETEASE, resolver.lyricsWithSourceFor(1).first().getOrThrow().source)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(10L, testScheduler.currentTime)
    }

    @Test fun neteaseThenQqThenAmllUpgradeWithoutWaitingForOtherSources() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain), 10), { null }, { null },
            readAmllLyrics = { delay(4000); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { delay(1000); word })
        val received = mutableListOf<Pair<Long, LyricsSource>>()
        launch { resolver.lyricsWithSourceFor(1).collect { received += testScheduler.currentTime to it.getOrThrow().source } }
        advanceUntilIdle()
        assertEquals(listOf(10L to LyricsSource.NETEASE, 1010L to LyricsSource.QQ, 4000L to LyricsSource.AMLL), received)
    }

    @Test fun lateQqDoesNotDowngradeAmll() = runTest {
        val resolver = LyricsResolver(repo(Result.success(plain)), { null }, { null },
            readAmllLyrics = { delay(1000); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { delay(2000); word })
        assertEquals(listOf(LyricsSource.NETEASE, LyricsSource.AMLL),
            resolver.lyricsWithSourceFor(1).toList().map { it.getOrThrow().source })
    }

    @Test fun amllMayUpgradeAfterTheFormerFourSecondDeadline() = runTest {
        var qqReads = 0
        val resolver = LyricsResolver(repo(Result.success(word)), { null }, { null },
            readAmllLyrics = { delay(6000); ttml }, processingDispatcher = StandardTestDispatcher(testScheduler),
            readQqLyrics = { qqReads++; emptyList() })
        assertEquals(listOf(LyricsSource.NETEASE, LyricsSource.AMLL),
            resolver.lyricsWithSourceFor(1).toList().map { it.getOrThrow().source })
        assertEquals(6000L, testScheduler.currentTime)
        assertEquals(0, qqReads)
    }
}
