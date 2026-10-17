package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import com.lin0721.linmusic.core.player.domain.LyricTimeline
import com.lin0721.linmusic.core.player.domain.TtmlLyricParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.withTimeoutOrNull

// 启用的三源同时查询，每 250ms 按最新快照择优，保留最后一批结果。
enum class LyricsSource { AMLL, NETEASE, LOCAL, QQ }

data class ResolvedLyrics(val lines: List<LyricLine>, val source: LyricsSource)

class LyricsResolver(
    private val playbackRepository: PlaybackRepository,
    // 按来源 Uri 读取本地歌词原文，平台无本地音乐时返回 null
    private val readLocalLyrics: suspend (sourceUri: String) -> String?,
    private val localUriOf: (songId: Long) -> String?,
    // 按 songId 读取 AMLL TTML 原文；平台未接入 AMLL 时保持默认实现返回 null
    private val readAmllLyrics: suspend (songId: Long) -> String? = { null },
    private val isAmllEnabled: suspend () -> Boolean = { true },
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val readQqLyrics: suspend (songId: Long) -> List<LyricLine> = { emptyList() },
    private val isQqEnabled: suspend () -> Boolean = { true }
) {

    private companion object {
        const val AMLL_TIMEOUT_MS = 12000L
        const val QQ_TIMEOUT_MS = 8000L
        const val UPDATE_INTERVAL_MS = 250L
    }

    fun lyricsFor(songId: Long): Flow<Result<List<LyricLine>>> = lyricsWithSourceFor(songId)
        .map { result -> result.map { it.lines } }

    fun lyricsWithSourceFor(songId: Long): Flow<Result<ResolvedLyrics>> = flow {
        val localUri = localUriOf(songId)
        if (localUri == null) {
            if (songId > 0) emitAll(onlineLyricsFor(songId)) else emit(Result.success(ResolvedLyrics(emptyList(), LyricsSource.LOCAL)))
            return@flow
        }
        if (songId > 0) {
            // 已关联网易的本地歌曲也持续收集，首份歌词显示后仍能升级为 AMLL。
            onlineLyricsFor(songId).collect { online ->
                if (online.getOrNull()?.lines.orEmpty().isNotEmpty()) emit(online)
                else emit(Result.success(localLyrics(localUri)))
            }
            return@flow
        }
        emit(Result.success(localLyrics(localUri)))
    }.flowOn(processingDispatcher)

    private suspend fun localLyrics(uri: String): ResolvedLyrics {
        val lines = readLocalLyrics(uri)?.let(LyricParser::parseLocal).orEmpty()
        return ResolvedLyrics(LyricTimeline.prepareLines(lines), LyricsSource.LOCAL)
    }

    private sealed interface SourceUpdate {
        data class Netease(val result: Result<List<LyricLine>>) : SourceUpdate
        data class Amll(val lines: List<LyricLine>) : SourceUpdate
        data class Qq(val lines: List<LyricLine>) : SourceUpdate
        data class Cancelled(val exception: CancellationException) : SourceUpdate
        data object Tick : SourceUpdate
    }

    private fun onlineLyricsFor(songId: Long): Flow<Result<ResolvedLyrics>> = flow {
        coroutineScope {
            val updates = Channel<SourceUpdate>(Channel.UNLIMITED)
            fun query(block: suspend () -> SourceUpdate) = launch {
                try {
                    updates.send(block())
                } catch (e: CancellationException) {
                    // 子查询主动取消也要通知协调器，避免等待一条永远不会到达的结果。
                    updates.trySend(SourceUpdate.Cancelled(e))
                }
            }
            val amllEnabled = isAmllEnabled()
            val qqEnabled = isQqEnabled()
            var pending = 1 + (if (amllEnabled) 1 else 0) + (if (qqEnabled) 1 else 0)
            val jobs = mutableListOf(query { SourceUpdate.Netease(fetchNeteaseResult(songId)) })
            if (amllEnabled) jobs += query { SourceUpdate.Amll(fetchAmllLines(songId)) }
            if (qqEnabled) jobs += query { SourceUpdate.Qq(fetchQqLines(songId)) }
            val ticker = launch {
                while (isActive) {
                    delay(UPDATE_INTERVAL_MS)
                    updates.send(SourceUpdate.Tick)
                }
            }
            var netease: Result<List<LyricLine>>? = null
            var amll = emptyList<LyricLine>()
            var qq = emptyList<LyricLine>()
            var displayed: ResolvedLyrics? = null
            suspend fun display(resolved: ResolvedLyrics) {
                if (resolved == displayed) return
                emit(Result.success(resolved))
                displayed = resolved
                AppLogger.d("LyricsResolver", "歌词更新 songId=$songId source=${resolved.source} lines=${resolved.lines.size}")
            }
            // 与 sample 类似按固定周期取最新值；查询结束后仍消费最后一次 Tick，避免丢失尾值。
            var dirty = false
            try {
                while (pending > 0 || dirty) {
                    when (val update = updates.receive()) {
                        is SourceUpdate.Netease -> { netease = update.result; pending--; dirty = true }
                        is SourceUpdate.Amll -> { amll = update.lines; pending--; dirty = true }
                        is SourceUpdate.Qq -> { qq = update.lines; pending--; dirty = true }
                        is SourceUpdate.Cancelled -> throw update.exception
                        SourceUpdate.Tick -> {
                            if (!dirty) continue
                            val candidate = chooseWithSource(amll, netease ?: Result.success(emptyList()), qq)
                            val resolved = candidate.getOrNull()?.takeIf { it.lines.isNotEmpty() }
                            if (resolved != null) display(resolved)
                            else if (pending == 0 && displayed == null) emit(candidate)
                            dirty = false
                        }
                    }
                }
            } finally {
                ticker.cancel()
                jobs.forEach { it.cancel() }
                updates.cancel()
            }
        }
    }

    private suspend fun fetchQqLines(songId: Long): List<LyricLine> = try {
        withTimeoutOrNull(QQ_TIMEOUT_MS) { readQqLyrics(songId) }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** AMLL 任何失败都降级为空列表；网易的失败要原样带回，上层需要区分"没有歌词"与"请求失败"。 */
    private suspend fun fetchAmllLines(songId: Long): List<LyricLine> = try {
        withTimeoutOrNull(AMLL_TIMEOUT_MS) { readAmllLyrics(songId) }
            ?.let(TtmlLyricParser::parse)
            .orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun fetchNeteaseResult(songId: Long): Result<List<LyricLine>> = try {
        playbackRepository.getLyrics(songId).first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    internal fun choose(amll: List<LyricLine>, netease: Result<List<LyricLine>>): Result<List<LyricLine>> {
        return chooseWithSource(amll, netease).map { it.lines }
    }

    internal fun chooseWithSource(amll: List<LyricLine>, netease: Result<List<LyricLine>>, qq: List<LyricLine> = emptyList()): Result<ResolvedLyrics> {
        val a = cleanLines(amll)
        val n = cleanLines(netease.getOrNull().orEmpty())
        val q = cleanLines(qq)
        val (lines, source) = when {
            a.any { it.words.isNotEmpty() } -> a to LyricsSource.AMLL
            n.any { it.words.isNotEmpty() } -> n to LyricsSource.NETEASE
            q.any { it.words.isNotEmpty() } -> q to LyricsSource.QQ
            a.isNotEmpty() -> a to LyricsSource.AMLL
            n.isNotEmpty() -> n to LyricsSource.NETEASE
            q.isNotEmpty() -> q to LyricsSource.QQ
            // 两边都没内容：保留网易原结果，是失败就继续向上抛
            else -> return netease.map { ResolvedLyrics(it, LyricsSource.NETEASE) }
        }
        // 载入时统一做一次时间轴整理：补全行时长、把背景和声并入同组、裁掉标注误差级重叠，
        // 但保留有意为之的重叠行（对唱），这样上层才能识别出同时需要高亮的多行。
        return Result.success(ResolvedLyrics(LyricTimeline.prepareLines(lines), source))
    }
}

private fun cleanLines(lines: List<LyricLine>): List<LyricLine> = lines
    .filter { it.timeMs >= 0 && it.text.isNotBlank() }
    .map(::cleanLine)
    .sortedBy { it.timeMs }

// 逐字时序不合法时整行退回普通行——宁可整行高亮，也不能渲染出错位的逐字；
// 背景和声行（TTML x-bg）走同一套校验。
private fun cleanLine(line: LyricLine): LyricLine {
    val wordsValid = line.words.isNotEmpty() &&
        line.words.any { it.durationMs > 0 } &&
        line.words.all {
            it.startOffsetMs >= 0 && it.durationMs >= 0 &&
                it.startOffsetMs <= line.durationMs && it.durationMs <= line.durationMs - it.startOffsetMs
        } &&
        line.words.zipWithNext().all { (a, b) -> a.startOffsetMs <= b.startOffsetMs } &&
        line.words.joinToString("") { it.text } == line.text
    return line.copy(
        words = if (wordsValid) line.words else emptyList(),
        backgroundLine = line.backgroundLine
            ?.takeIf { it.timeMs >= 0 && it.text.isNotBlank() }
            ?.let(::cleanLine)
    )
}
