package com.lin0721.linmusic.desktop.app

import java.util.Properties

private const val RESOURCE_NAME = "app-info.properties"
private const val UNKNOWN_VERSION = "unknown"

// 构建时生成的 app-info.properties
object AppInfo {
    val version: String by lazy {
        runCatching {
            Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE_NAME)?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VERSION
    }
}
