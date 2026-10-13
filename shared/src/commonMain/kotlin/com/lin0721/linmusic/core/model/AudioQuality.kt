package com.lin0721.linmusic.core.model

// 音质代码转展示文案，被 settings/player 等多个域的音质相关 UI 复用。
fun getQualityDisplayName(quality: String): String {
    return when (quality) {
        "standard" -> "标准"
        "higher" -> "较高"
        "exhigh" -> "极高"
        "lossless" -> "无损 (FLAC)"
        "hires" -> "Hi-Res"
        "jyeffect" -> "高清环绕声"
        "sky" -> "沉浸环绕声"
        "jymaster" -> "超清母带"
        else -> quality
    }
}

// 音质代码转纯英文短标签，用于播放页等空间紧凑处；未知档位返回 null 以便隐藏
fun getQualityBadgeLabel(quality: String): String? {
    return when (quality) {
        "standard" -> "Standard"
        "higher" -> "Higher"
        "exhigh" -> "ExHigh"
        "lossless" -> "Lossless"
        "hires" -> "Hi-Res"
        "jyeffect" -> "Surround"
        "sky" -> "Immersive"
        "jymaster" -> "Master"
        else -> null
    }
}

// 可选下载音质档位，从低到高排列
val DOWNLOAD_QUALITY_LEVELS = listOf(
    "standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "jymaster"
)

// 音质档位高低排序，未知档位返回 -1
fun qualityRank(level: String?): Int = level?.let(DOWNLOAD_QUALITY_LEVELS::indexOf) ?: -1
