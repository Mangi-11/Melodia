package com.lin0721.linmusic.desktop.app

import java.io.File

// 用户数据目录：Windows 取 %APPDATA%\Melodia，其他平台回退到用户主目录
object DesktopPaths {
    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
        File(base, "Melodia").apply { mkdirs() }
    }

    val logDir: File by lazy { File(dataDir, "logs") }

    // 缓存放 %LOCALAPPDATA%，取不到时回退数据目录
    val localCacheDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        if (base != null) File(File(base, "Melodia"), "cache") else File(dataDir, "cache")
    }

    val imageCacheDir: File by lazy { File(localCacheDir, "image_cache") }
    val audioCacheDir: File by lazy { File(localCacheDir, "audio_cache") }
    val metadataCacheDir: File by lazy { File(localCacheDir, "meta_cache") }

    val lyricsCacheDir: File by lazy { File(localCacheDir, "lyrics") }

    val downloadTempDir: File by lazy { File(localCacheDir, "download_tmp") }

    val defaultDownloadDir: File by lazy { File(File(System.getProperty("user.home"), "Music"), "Melodia") }

    fun preferencesFile(name: String): File = File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")
}
