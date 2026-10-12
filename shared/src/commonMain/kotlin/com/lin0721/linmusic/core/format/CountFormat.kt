package com.lin0721.linmusic.core.format

import java.util.Locale

private const val TEN_THOUSAND = 10_000L
private const val HUNDRED_MILLION = 100_000_000L

// 大于一万按“万”、大于一亿按“亿”缩写，保留一位小数并去掉多余的 .0
fun formatCompactCount(count: Long): String {
    val value = count.coerceAtLeast(0)
    return when {
        value >= HUNDRED_MILLION -> compactUnit(value, HUNDRED_MILLION, "亿")
        value >= TEN_THOUSAND -> compactUnit(value, TEN_THOUSAND, "万")
        else -> value.toString()
    }
}

private fun compactUnit(value: Long, unit: Long, suffix: String): String {
    val tenths = value * 10 / unit
    val text = if (tenths % 10 == 0L) (tenths / 10).toString() else String.format(Locale.ROOT, "%.1f", tenths / 10.0)
    return text + suffix
}
