package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "DesktopWebViewLoginManager"
private const val LOG_EVERY_POLLS = 5
private const val NETEASE_DOMAIN = "163.com"
private const val LOGIN_URL = "https://music.163.com/m/login"
private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

/**
 * 桌面端原生 WebView 登录管理器
 * 以独立 App 模式调起系统 Chromium 内核浏览器（Windows 上为 Edge）的小尺寸窗口，
 * 通过本地全局 CDP 协议监听 Storage.getCookies，检测到 MUSIC_U 后自动关闭并同步凭据。
 */
class DesktopWebViewLoginManager(
    private val browserLocator: BrowserLocator,
    private val onLoginSuccess: (String) -> Unit,
    private val onClosed: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var process: Process? = null
    private var tempUserDataDir: File? = null
    private var webSocket: WebSocket? = null
    private val isFinished = AtomicBoolean(false)
    private val messageIdCounter = AtomicInteger(1)
    private val pollCounter = AtomicInteger(0)

    fun start(): Boolean {
        val edgeExe = browserLocator.findChromiumBrowser()
        if (edgeExe == null) {
            val err = "未找到可用于网页登录的浏览器，请改用扫码登录"
            AppLogger.e(TAG, err)
            onError(err)
            return false
        }

        val port = try {
            allocateAvailablePort()
        } catch (e: Exception) {
            val err = "分配调试通信端口失败"
            AppLogger.e(TAG, err, e)
            onError(err)
            return false
        }

        val tempDir = try {
            Files.createTempDirectory("melodia_login_").toFile()
        } catch (e: Exception) {
            val err = "创建临时用户数据目录失败"
            AppLogger.e(TAG, err, e)
            onError(err)
            return false
        }
        tempUserDataDir = tempDir

        // 启动参数：禁用扩展插件防止弹窗干扰、设置移动端UA与独立隔离目录
        val command = listOf(
            edgeExe.absolutePath,
            "--app=$LOGIN_URL",
            "--user-data-dir=${tempDir.absolutePath}",
            "--remote-debugging-port=$port",
            "--window-size=520,800",
            "--user-agent=$MOBILE_USER_AGENT",
            "--disable-extensions",
            "--no-first-run",
            "--no-default-browser-check"
        )

        return try {
            // 浏览器的标准输出与错误输出不读取会写满管道，进程随之卡死，直接丢弃
            val p = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            process = p
            AppLogger.i(TAG, "已启动原生独立登录窗口，端口: $port")

            scope.launch {
                pollAndConnectCdp(port)
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动 Edge 独立窗口失败", e)
            cleanUp()
            onError("启动官方登录窗口失败: ${e.message}")
            false
        }
    }

    fun cancel() {
        if (isFinished.compareAndSet(false, true)) {
            AppLogger.i(TAG, "用户取消或关闭登录弹窗，销毁登录窗口与临时数据")
            cleanUp()
            onClosed()
        }
    }

    private suspend fun pollAndConnectCdp(port: Int) {
        var wsUrl: String? = null
        for (i in 0 until 60) {
            if (!scope.isActive || isFinished.get()) return
            delay(500)
            wsUrl = fetchBrowserWebSocketUrl(port)
            if (wsUrl != null) break
        }

        if (wsUrl == null) {
            AppLogger.e(TAG, "未能连接至登录窗口通信通道")
            if (!isFinished.get()) {
                onError("与登录窗口建立通信超时")
            }
            return
        }

        connectWebSocket(wsUrl, port)
    }

    // 从 /json/version 获取浏览器全局调试 WebSocket 地址
    private fun fetchBrowserWebSocketUrl(port: Int): String? {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/json/version")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(bodyStr).jsonObject
                obj["webSocketDebuggerUrl"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun connectWebSocket(wsUrl: String, port: Int) {
        val request = Request.Builder().url(wsUrl).build()
        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.i(TAG, "CDP 全局通道已连接，开始轮询登录凭据")
                scope.launch {
                    while (isActive && !isFinished.get()) {
                        val reqId = messageIdCounter.getAndIncrement()
                        val cdpCmd = """{"id":$reqId,"method":"Storage.getCookies"}"""
                        webSocket.send(cdpCmd)
                        delay(1200)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleCdpMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                handleWindowClosed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleWindowClosed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.w(TAG, "CDP 通信连接中断: ${t::class.simpleName} ${t.message}")
                handleWindowClosed()
            }
        })
        webSocket = ws

        // 后台持续探活窗口状态
        scope.launch {
            delay(3000)
            var failCount = 0
            while (isActive && !isFinished.get()) {
                delay(2000)
                val alive = isPortAlive(port)
                if (!alive) {
                    failCount++
                    if (failCount >= 2) {
                        AppLogger.i(TAG, "登录窗口端口已不可达，判定窗口已关闭")
                        handleWindowClosed()
                        break
                    }
                } else {
                    failCount = 0
                }
            }
        }
    }

    private fun isPortAlive(port: Int): Boolean {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/json/version")
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun handleWindowClosed() {
        if (isFinished.compareAndSet(false, true)) {
            AppLogger.i(TAG, "登录窗口已关闭")
            cleanUp()
            onClosed()
        }
    }

    private fun handleCdpMessage(webSocket: WebSocket, text: String) {
        if (isFinished.get()) return
        try {
            val root = json.parseToJsonElement(text).jsonObject
            root["error"]?.let {
                AppLogger.w(TAG, "CDP 命令返回错误: $it")
                return
            }
            val result = root["result"]?.jsonObject ?: return
            val cookiesArray = result["cookies"]?.jsonArray ?: return
            val cookieMap = neteaseCookiesOf(cookiesArray)
            if (pollCounter.incrementAndGet() % LOG_EVERY_POLLS == 1) {
                AppLogger.i(TAG, "已读取 ${cookiesArray.size} 个 Cookie，网易域名 ${cookieMap.size} 个：${cookieMap.keys.joinToString()}")
            }

            if (cookieMap.containsKey("MUSIC_U")) {
                if (isFinished.compareAndSet(false, true)) {
                    val cookieStr = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    AppLogger.i(TAG, "成功拦截到 MUSIC_U 登录凭据")
                    
                    // 发送指令关闭 Edge 浏览器窗口
                    try {
                        webSocket.send("""{"id":9999,"method":"Browser.close"}""")
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "发送 Browser.close 指令异常", e)
                    }

                    onLoginSuccess(cookieStr)
                    cleanUp()
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "解析 CDP 消息失败", e)
        }
    }

    private fun cleanUp() {
        try {
            webSocket?.close(1000, "Done")
        } catch (e: Exception) {
            AppLogger.w(TAG, "关闭 WebSocket 异常", e)
        }
        webSocket = null

        process?.let { p ->
            try {
                if (p.isAlive) {
                    p.destroyForcibly()
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "终止登录进程异常", e)
            }
        }
        process = null

        tempUserDataDir?.let { dir ->
            scope.launch {
                delay(1200)
                deleteDirectoryRecursively(dir)
            }
        }
        tempUserDataDir = null
    }

    private fun deleteDirectoryRecursively(dir: File) {
        try {
            if (dir.exists()) {
                dir.walkBottomUp().forEach { it.delete() }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "清理临时用户目录失败: ${dir.absolutePath}", e)
        }
    }

    private fun allocateAvailablePort(): Int =
        ServerSocket(0).use { it.localPort }
}

// 只取网易域名的 Cookie：浏览器里还可能有 Edge 自身或第三方域名的条目，混进请求头没有意义；
// 按域名后缀匹配时要求以点分隔，避免 xx163.com 这类域名混入
internal fun neteaseCookiesOf(cookies: JsonArray): LinkedHashMap<String, String> {
    val result = LinkedHashMap<String, String>()
    for (element in cookies) {
        val obj = element as? JsonObject ?: continue
        val domain = (obj["domain"] as? JsonPrimitive)?.contentOrNull?.trimStart('.').orEmpty()
        if (domain != NETEASE_DOMAIN && !domain.endsWith(".$NETEASE_DOMAIN")) continue
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
        val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: continue
        result[name] = value
    }
    return result
}
