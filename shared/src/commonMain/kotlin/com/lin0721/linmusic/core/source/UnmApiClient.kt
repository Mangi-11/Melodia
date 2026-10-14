package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.serialization.Serializable
import java.io.IOException

private const val TAG = "UnmApiClient"

// 网络超时/断连时的最大尝试次数（含首次），服务端业务失败不重试
private const val MAX_MATCH_ATTEMPTS = 2

// UNM 匹配结果
@Serializable
data class UnmMatchResult(
    val url: String,
    val source: String? = null
)

@Serializable
private data class UnmMatchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UnmMatchData? = null
)

@Serializable
private data class UnmMatchData(
    val url: String? = null,
    val source: String? = null
)

// 负责调用 UNM Utils REST API 服务的客户端
class UnmApiClient {

    // 匹配歌曲并返回播放直链
    suspend fun matchSong(
        serverUrl: String,
        songId: Long,
        source: String? = null
    ): UnmMatchResult? {
        val baseUrl = serverUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return null

        val targetUrl = buildString {
            append(baseUrl)
            append("/match?id=")
            append(songId)
            if (!source.isNullOrBlank()) {
                append("&source=")
                append(source)
            }
        }

        repeat(MAX_MATCH_ATTEMPTS) { attempt ->
            try {
                val responseText = SourceHttpClient.get(
                    url = targetUrl,
                    headers = mapOf(
                        "User-Agent" to "Melodia/1.0",
                        "Accept" to "application/json"
                    ),
                    httpClient = SourceHttpClient.unmClient
                )
                val parsed = SourceHttpClient.json.decodeFromString<UnmMatchResponse>(responseText)
                val playUrl = parsed.data?.url?.trim()
                if (parsed.code != 200 || playUrl.isNullOrBlank() ||
                    !(playUrl.startsWith("http://") || playUrl.startsWith("https://"))
                ) {
                    return null
                }
                if (isTrialPlayUrl(playUrl)) {
                    AppLogger.w(TAG, "UNM 返回的是试听链接，视为未命中 [$targetUrl]")
                    return null
                }
                return UnmMatchResult(url = playUrl, source = parsed.data?.source ?: source)
            } catch (e: IOException) {
                AppLogger.w(TAG, "UNM 匹配请求异常 [$targetUrl] 第 ${attempt + 1}/$MAX_MATCH_ATTEMPTS 次: ${e.message}")
            } catch (e: Exception) {
                AppLogger.w(TAG, "UNM 匹配请求异常 [$targetUrl]: ${e.message}")
                return null
            }
        }
        return null
    }

    // 测试服务是否可用
    suspend fun testConnection(serverUrl: String): Boolean {
        val baseUrl = serverUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return false
        val targetUrl = "$baseUrl/inner/version"
        return try {
            val resp = SourceHttpClient.get(targetUrl)
            resp.contains("version") || resp.contains("200")
        } catch (e: Exception) {
            false
        }
    }
}
