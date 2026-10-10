package com.lin0721.linmusic.feature.source.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.source.SourceResult
import kotlinx.coroutines.flow.first

private const val TAG = "LxAudioSourceProvider"

// LX 自定义插件音源 Provider 适配器（支持多插件按优先级轮询）
class LxAudioSourceProvider(
    private val engine: LxPluginEngine,
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
        engine.syncActivePlugins(plugins)

        val priorityPlatformOrder = listOf("wy", "kw", "kg", "tx", "mg")

        // 按照用户调整的插件优先级顺序逐个尝试
        for (plugin in plugins) {
            val supported = engine.getSupportedSources(plugin.id).ifEmpty { plugin.sources }
            if (supported.isEmpty()) continue

            val trySources = priorityPlatformOrder.filter { it in supported }.ifEmpty { supported }
            for (source in trySources) {
                try {
                    val url = engine.resolveMusicUrl(
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
