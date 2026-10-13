package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "SongMatcher"

// 跨平台歌曲模糊匹配算法，移植自 SPlayer-Next
object SongMatcher {

    private const val NAME_CONTAIN_MIN_RATIO = 0.34
    private const val DURATION_FAR_THRESHOLD_MS = 20_000L
    private const val DURATION_CLOSE_THRESHOLD_MS = 5_000L

    fun pickBestCandidate(
        candidates: List<ExternalTrack>,
        targetName: String,
        targetArtists: List<String>,
        targetAlbum: String?,
        targetDurationMs: Long
    ): ExternalTrack? {
        if (candidates.isEmpty()) return null

        val normTarget = normalize(targetName)
        val normTargetArtists = targetArtists.map { normalize(it) }.filter { it.isNotEmpty() }
        val normTargetAlbum = targetAlbum?.let { normalize(it) }?.takeIf { it.isNotEmpty() }

        data class Scored(val track: ExternalTrack, val score: Int)

        val scored = candidates.mapNotNull { candidate ->
            val normCandName = normalize(candidate.name)
            val normCandArtists = splitArtists(candidate.artists)

            // 硬性门槛 1：歌名匹配
            val nameExact = normCandName == normTarget
            val nameContains = !nameExact && (
                normCandName.contains(normTarget) || normTarget.contains(normCandName)
            )
            if (!nameExact && !nameContains) return@mapNotNull null

            // 歌名子串匹配时检查长度比率
            if (nameContains) {
                val shorter = minOf(normCandName.length, normTarget.length)
                val longer = maxOf(normCandName.length, normTarget.length)
                if (longer == 0 || shorter.toDouble() / longer < NAME_CONTAIN_MIN_RATIO) {
                    return@mapNotNull null
                }
            }

            // 硬性门槛 2：时长差
            val durationClose = if (targetDurationMs > 0 && candidate.durationMs > 0) {
                val diff = kotlin.math.abs(targetDurationMs - candidate.durationMs)
                if (diff > DURATION_FAR_THRESHOLD_MS) return@mapNotNull null
                diff <= DURATION_CLOSE_THRESHOLD_MS
            } else {
                false
            }

            // 硬性门槛 3：歌手交集
            val artistExact = hasArtistExactMatch(normTargetArtists, normCandArtists)
            val artistContains = !artistExact && hasArtistContainsMatch(normTargetArtists, normCandArtists)
            val artistMatch = artistExact || artistContains

            if (normTargetArtists.isNotEmpty() && normCandArtists.isNotEmpty() && !artistMatch) {
                return@mapNotNull null
            }

            // 置信度地板：非全等歌名必须有歌手全等或时长极度接近
            if (!nameExact && !artistExact && !durationClose) {
                return@mapNotNull null
            }

            // 打分
            var score = 0
            score += if (nameExact) 10 else 4
            if (artistExact) score += 5
            else if (artistContains) score += 2
            if (normTargetAlbum != null) {
                val normCandAlbum = normalize(candidate.albumName)
                if (normCandAlbum == normTargetAlbum) score += 2
            }
            if (durationClose) score += 3

            Scored(candidate, score)
        }

        val best = scored.maxByOrNull { it.score }
        if (best != null) {
            AppLogger.d(TAG, "匹配命中: ${best.track.name} - ${best.track.artists} [${best.track.platform.key}] 得分=${best.score}")
        }
        return best?.track
    }

    // 去除标点、空格、特殊字符后转全小写
    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[、&;，,/|()（）·・\\s\\-_'\"`~!?？！.。:：]+"), "")
    }

    // 拆分多歌手文本
    private fun splitArtists(text: String): List<String> {
        return text.split(Regex("[、&;，,/|·・]+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
    }

    // 检查是否有歌手全等匹配
    private fun hasArtistExactMatch(target: List<String>, candidate: List<String>): Boolean {
        if (target.isEmpty() || candidate.isEmpty()) return false
        return target.any { t -> candidate.any { c -> c == t } }
    }

    // 检查是否有歌手包含匹配（仅当单方长度 > 2 时）
    private fun hasArtistContainsMatch(target: List<String>, candidate: List<String>): Boolean {
        if (target.isEmpty() || candidate.isEmpty()) return false
        return target.any { t ->
            t.length > 2 && candidate.any { c ->
                c.length > 2 && (c.contains(t) || t.contains(c))
            }
        }
    }
}
