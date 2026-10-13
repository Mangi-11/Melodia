package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.first

private const val TAG = "LxAudioSourceProvider"

// LX 自定义插件音源 Provider 适配器（支持多插件按优先级轮询）
class LxAudioSourceProvider(
    private val runtime: LxPluginRuntime,
    private val sourcePreferences: SourcePreferences
) : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.LX

    override suspend fun resolveUrl(
        songId: String,
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String
    ): SourceResult? {
        val enabled = sourcePreferences.lxPluginEnabled.first()
        if (!enabled) return null

        val plugins = sourcePreferences.lxPlugins.first().filter { it.isEnabled }
        if (plugins.isEmpty()) return null

        // 确保沙盒池与当前已启用的插件保持同步
        runtime.syncActivePlugins(plugins)

        val priorityPlatformOrder = listOf("wy", "kw", "kg", "tx", "mg")

        // 按照用户调整的插件优先级顺序逐个尝试
        for (plugin in plugins) {
            val supported = runtime.getSupportedSources(plugin.id).ifEmpty { plugin.sources }
            if (supported.isEmpty()) continue

            val trySources = priorityPlatformOrder.filter { it in supported }.ifEmpty { supported }
            for (source in trySources) {
                try {
                    val url = runtime.resolveMusicUrl(
                        pluginId = plugin.id,
                        source = source,
                        songId = songId,
                        songName = songName,
                        singer = artists,
                        albumName = albumName ?: "",
                        durationMs = durationMs,
                        quality = quality
                    )
                    if (!url.isNullOrBlank()) {
                        AppLogger.i(TAG, "插件 [${plugin.name}] 成功通过 [$source] 解析直链: $songName")
                        return SourceResult(
                            url = url,
                            platform = platform,
                            quality = quality
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "插件 [${plugin.name}] 解析平台 [$source] 失败: ${e.message}")
                }
            }
        }
        return null
    }

    override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> {
        return emptyList()
    }
}
