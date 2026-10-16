package com.lin0721.linmusic.feature.source.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginRuntime
import com.lin0721.linmusic.core.source.LxScriptSupport
import com.lin0721.linmusic.core.source.LxShim
import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LxPluginEngine"

// 沙盒代理请求的响应体上限，超限视为请求失败
private const val MAX_PROXY_BODY_BYTES = 4L * 1024 * 1024

// LX Music JS 插件沙盒引擎（多沙盒隔离与生命周期管理）
class LxPluginEngine(private val context: Context) : LxPluginRuntime {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 活跃插件沙盒池 (pluginId -> PluginSandbox)
    private val activeSandboxes = ConcurrentHashMap<String, PluginSandbox>()
    private val nextCallId = AtomicInteger(0)

    // 内部沙盒实体
    private class PluginSandbox(
        val pluginId: String,
        val webView: WebView,
        var supportedSources: List<String> = emptyList(),
        val pendingResolves: ConcurrentHashMap<Int, CompletableDeferred<String?>> = ConcurrentHashMap()
    )

    // 从远程 URL 下载脚本内容
    override suspend fun downloadScript(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val script = SourceHttpClient.get(url)
            if (script.isNotBlank()) {
                Result.success(script)
            } else {
                Result.failure(Exception("脚本内容为空"))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载远程脚本失败: $url", e)
            Result.failure(e)
        }
    }

    // 校验并提取脚本信息（使用临时无头沙盒测试运行并监听 inited）
    override suspend fun loadAndVerifyScript(scriptText: String): Result<LxPluginInfo> = withContext(Dispatchers.Main) {
        val tempId = "temp_${System.currentTimeMillis()}"
        var tempSandbox: PluginSandbox? = null
        try {
            val headerInfo = LxScriptSupport.parseScriptHeader(scriptText)
            val initDeferred = CompletableDeferred<List<String>>()

            tempSandbox = createSandboxInstance(
                pluginId = tempId,
                onInited = { sources -> initDeferred.complete(sources) }
            )

            // 注入插件全局元数据并执行脚本
            val initTempInfoJs = "window.lx.currentScriptInfo = { name: " +
                org.json.JSONObject.quote(headerInfo.name) + ", description: " +
                org.json.JSONObject.quote(headerInfo.description) + ", version: " +
                org.json.JSONObject.quote(headerInfo.version) + ", author: " +
                org.json.JSONObject.quote(headerInfo.author) + " };"
            tempSandbox.webView.evaluateJavascript(initTempInfoJs, null)
            tempSandbox.webView.evaluateJavascript(scriptText, null)

            // 等待 inited 事件触发（最长 8 秒）
            val sources = withTimeoutOrNull(LxScriptSupport.INIT_TIMEOUT_MS) {
                initDeferred.await()
            } ?: emptyList()

            val info = headerInfo.copy(
                sources = sources,
                rawScript = scriptText
            )
            AppLogger.i(TAG, "脚本校验成功: ${info.name} v${info.version}, 支持平台: $sources")
            Result.success(info)
        } catch (e: Exception) {
            AppLogger.e(TAG, "脚本校验失败", e)
            Result.failure(e)
        } finally {
            tempSandbox?.let { destroySandbox(it) }
        }
    }

    // 激活并注册插件
    override suspend fun activatePlugin(item: LxPluginItem): Boolean = withContext(Dispatchers.Main) {
        try {
            // 若已存在相同 ID 沙盒，先进行销毁
            deactivatePlugin(item.id)

            val initDeferred = CompletableDeferred<List<String>>()
            val sandbox = createSandboxInstance(
                pluginId = item.id,
                onInited = { sources -> initDeferred.complete(sources) }
            )

            sandbox.supportedSources = item.sources
            val initInfoJs = "window.lx.currentScriptInfo = { name: " +
                org.json.JSONObject.quote(item.name) + ", description: " +
                org.json.JSONObject.quote(item.description) + ", version: " +
                org.json.JSONObject.quote(item.version) + ", author: " +
                org.json.JSONObject.quote(item.author) + " };"
            sandbox.webView.evaluateJavascript(initInfoJs, null)
            sandbox.webView.evaluateJavascript(item.rawScript, null)

            // 等待初始化
            val sources = withTimeoutOrNull(LxScriptSupport.INIT_TIMEOUT_MS) {
                initDeferred.await()
            }
            if (sources != null && sources.isNotEmpty()) {
                sandbox.supportedSources = sources
            }

            activeSandboxes[item.id] = sandbox
            AppLogger.i(TAG, "插件 [${item.name}] 沙盒激活成功, ID=${item.id}, sources=${sandbox.supportedSources}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "激活插件失败: ${item.name}", e)
            false
        }
    }

    // 注销并销毁指定插件沙盒
    override fun deactivatePlugin(pluginId: String) {
        activeSandboxes.remove(pluginId)?.let { sandbox ->
            scope.launch(Dispatchers.Main) {
                destroySandbox(sandbox)
            }
        }
    }

    // 批量同步活跃插件沙盒状态
    override suspend fun syncActivePlugins(items: List<LxPluginItem>) = withContext(Dispatchers.Main) {
        val enabledItems = items.filter { it.isEnabled }
        val enabledIds = enabledItems.map { it.id }.toSet()

        // 卸载未启用或已被删除的沙盒
        val toRemove = activeSandboxes.keys.filter { it !in enabledIds }
        toRemove.forEach { deactivatePlugin(it) }

        // 激活新增或尚未运行的沙盒
        for (item in enabledItems) {
            if (!activeSandboxes.containsKey(item.id)) {
                activatePlugin(item)
            }
        }
    }

    // 获取特定插件当前支持的平台列表
    override fun getSupportedSources(pluginId: String): List<String> {
        return activeSandboxes[pluginId]?.supportedSources ?: emptyList()
    }

    // 针对特定插件调用解析音乐直链
    override suspend fun resolveMusicUrl(
        pluginId: String,
        source: String,
        songId: String,
        songName: String,
        singer: String,
        albumName: String,
        durationMs: Long,
        quality: String
    ): String? = withContext(Dispatchers.IO) {
        val sandbox = activeSandboxes[pluginId] ?: return@withContext null
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        sandbox.pendingResolves[callId] = deferred

        val lxQuality = LxScriptSupport.mapQualityToLx(quality)
        val musicInfoJson = LxScriptSupport.buildMusicInfoJson(source, songId, songName, singer, albumName, durationMs)

        withContext(Dispatchers.Main) {
            val quotedMusicInfo = org.json.JSONObject.quote(musicInfoJson)
            val quotedSource = org.json.JSONObject.quote(source)
            val quotedQuality = org.json.JSONObject.quote(lxQuality)
            val jsCode = "window.__melodia_resolve_music_url($callId, $quotedSource, $quotedMusicInfo, $quotedQuality)"
            sandbox.webView.evaluateJavascript(jsCode, null)
        }

        try {
            withTimeoutOrNull(LxScriptSupport.RESOLVE_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "插件[$pluginId] 解析直链超时或失败: ${e.message}")
            null
        } finally {
            sandbox.pendingResolves.remove(callId)
        }
    }

    // 兼容旧接口：载入并初始化脚本
    suspend fun loadScript(scriptText: String): Result<LxPluginInfo> {
        return loadAndVerifyScript(scriptText)
    }

    // 重置并销毁全部沙盒
    override fun reset() {
        val sandboxes = activeSandboxes.values.toList()
        activeSandboxes.clear()
        scope.launch(Dispatchers.Main) {
            sandboxes.forEach { destroySandbox(it) }
        }
    }

    // 创建隔离的无头 WebView 沙盒
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun createSandboxInstance(
        pluginId: String,
        onInited: (List<String>) -> Unit
    ): PluginSandbox = withContext(Dispatchers.Main) {
        val pendingResolves = ConcurrentHashMap<Int, CompletableDeferred<String?>>()
        val readyDeferred = CompletableDeferred<Unit>()

        lateinit var sandbox: PluginSandbox
        val bridge = SandboxBridge(
            onHttp = { id, url, method, headersJson, body ->
                handleHttpRequest(sandbox.webView, id, url, method, headersJson, body)
            },
            onInited = onInited,
            onResolved = { callId, url, error ->
                if (!error.isNullOrBlank()) {
                    AppLogger.w(TAG, "插件沙盒[$pluginId] 解析直链失败: $error")
                }
                val deferred = sandbox.pendingResolves[callId]
                deferred?.complete(if (url.isNullOrBlank()) null else url)
            }
        )

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    readyDeferred.complete(Unit)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        AppLogger.d(TAG, "[$pluginId] JS Console: ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }
            }
            addJavascriptInterface(bridge, "MelodiaHostBridge")
        }

        val htmlContent = buildShimHtml()
        wv.loadDataWithBaseURL("https://melodia.local", htmlContent, "text/html", "UTF-8", null)

        withTimeoutOrNull(4000L) { readyDeferred.await() }

        sandbox = PluginSandbox(
            pluginId = pluginId,
            webView = wv,
            pendingResolves = pendingResolves
        )
        sandbox
    }

    // 销毁并释放单个 WebView
    private fun destroySandbox(sandbox: PluginSandbox) {
        try {
            sandbox.webView.stopLoading()
            sandbox.webView.loadUrl("about:blank")
            sandbox.webView.clearHistory()
            sandbox.webView.removeAllViews()
            sandbox.webView.destroy()
        } catch (e: Exception) {
            AppLogger.w(TAG, "销毁沙盒异常", e)
        }
    }

    // 处理沙盒内发起的网络代理请求
    private fun handleHttpRequest(
        webView: WebView,
        id: Int,
        url: String,
        method: String,
        headersJson: String,
        body: String
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val headersMap = mutableMapOf<String, String>()
                if (headersJson.isNotBlank()) {
                    runCatching {
                        val jsonObj = Json.parseToJsonElement(headersJson).jsonObject
                        jsonObj.forEach { (k, v) ->
                            headersMap[k] = v.jsonPrimitive.content
                        }
                    }
                }

                val respBody = if (method.equals("POST", ignoreCase = true)) {
                    SourceHttpClient.post(url, body, headersMap, maxBodyBytes = MAX_PROXY_BODY_BYTES)
                } else {
                    SourceHttpClient.get(url, headersMap, maxBodyBytes = MAX_PROXY_BODY_BYTES)
                }

                val respJson = org.json.JSONObject().apply {
                    put("statusCode", 200)
                }.toString()

                val quotedResp = org.json.JSONObject.quote(respJson)
                val quotedBody = org.json.JSONObject.quote(respBody)

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "window.__melodia_handle_http_response($id, null, $quotedResp, $quotedBody)",
                        null
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val quotedErr = org.json.JSONObject.quote(e.message ?: "Network error")
                    webView.evaluateJavascript(
                        "window.__melodia_handle_http_response($id, $quotedErr, null, null)",
                        null
                    )
                }
            }
        }
    }

    // 桥接供 JS 沙盒调用的宿主接口
    private class SandboxBridge(
        private val onHttp: (Int, String, String, String, String) -> Unit,
        private val onInited: (List<String>) -> Unit,
        private val onResolved: (Int, String?, String?) -> Unit
    ) {
        @JavascriptInterface
        fun httpRequest(id: Int, url: String, method: String, headersJson: String, body: String) {
            onHttp(id, url, method, headersJson, body)
        }

        @JavascriptInterface
        fun onEvent(event: String, dataJson: String) {
            if (event == "inited") {
                val sourcesList = mutableListOf<String>()
                runCatching {
                    val root = Json.parseToJsonElement(dataJson).jsonObject
                    val sourcesObj = root["sources"]?.jsonObject
                    sourcesObj?.keys?.forEach { sourcesList.add(it) }
                }
                onInited(sourcesList)
            }
        }

        @JavascriptInterface
        fun onResolveResult(callId: Int, url: String?, error: String?) {
            onResolved(callId, url, error)
        }

        @JavascriptInterface
        fun md5(input: String): String {
            return try {
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(input.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun buildShimHtml(): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body><script>\n" +
            LxShim.script("mobile") +
            "\n</script></body></html>"
}
