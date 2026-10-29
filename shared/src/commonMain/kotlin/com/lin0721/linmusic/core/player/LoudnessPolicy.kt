package com.lin0721.linmusic.core.player

import kotlin.math.pow

// 整曲响度归一化：只衰减不提升，避免削波
object LoudnessPolicy {

    // 写入 MediaItem extras，服务侧据此设定曲目初始增益
    const val EXTRA_TRACK_GAIN_DB = "trackGainDb"

    const val TARGET_DBFS = -16.0
    const val MIN_GAIN_DB = -12.0

    // 增益变化的平滑时长
    const val RAMP_MS = 2_000L

    // 自测在曲长这些比例处各取一段
    val SAMPLE_FRACTIONS = listOf(0.25, 0.5, 0.75)
    const val SAMPLE_SPAN_MS = 10_000L

    private val REPLAY_GAIN_NUMBER = Regex("""[-+]?\d+(?:\.\d+)?""")

    fun clampGainDb(db: Double): Double = db.coerceIn(MIN_GAIN_DB, 0.0)

    // 自测响度为 RMS dBFS，全程无声时无结果
    fun measuredGainDb(segments: List<List<LoudnessWindow>>): Double? =
        TrackEdges.audibleLoudnessDb(segments.flatten())?.let { clampGainDb(TARGET_DBFS - it) }

    // 解析 ReplayGain 曲目增益标签，如 "-7.32 dB"
    fun replayGainDb(tagValue: String?): Double? {
        val number = tagValue?.let { REPLAY_GAIN_NUMBER.find(it)?.value?.toDoubleOrNull() } ?: return null
        if (!number.isFinite()) return null
        return clampGainDb(number)
    }

    // 线性幅度增益
    fun amplitude(db: Double): Float = 10.0.pow(db / 20).toFloat()

    // mpv 软件音量按三次方映射为幅度，系数需开三次方
    fun mpvVolumeFactor(db: Double): Double = 10.0.pow(db / 60)
}
