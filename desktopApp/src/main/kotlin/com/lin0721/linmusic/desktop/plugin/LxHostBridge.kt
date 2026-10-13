package com.lin0721.linmusic.desktop.plugin

import com.lin0721.linmusic.core.log.AppLogger
import org.graalvm.polyglot.HostAccess
import java.security.MessageDigest

private const val TAG = "LxHostBridge"

// 暴露给沙盒脚本的宿主接口，HostAccess.EXPLICIT 下只有带 Export 注解的方法可被调用
class LxHostBridge internal constructor(
    private val pluginId: String,
    private val onHttp: (id: Int, url: String, method: String, headersJson: String, body: String) -> Unit,
    private val eventSink: (event: String, dataJson: String) -> Unit,
    private val onResolved: (callId: Int, url: String?, error: String?) -> Unit,
    private val onTimer: (id: Int, delayMs: Long) -> Unit
) {

    @HostAccess.Export
    fun httpRequest(id: Int, url: String, method: String, headersJson: String, body: String) {
        onHttp(id, url, method, headersJson, body)
    }

    @HostAccess.Export
    fun onEvent(event: String, dataJson: String) {
        eventSink(event, dataJson)
    }

    @HostAccess.Export
    fun onResolveResult(callId: Int, url: String?, error: String?) {
        onResolved(callId, url, error)
    }

    @HostAccess.Export
    fun md5(input: String): String = try {
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }

    // JS 数字可能是小数或负数，统一夹到合法范围
    @HostAccess.Export
    fun setTimer(id: Int, delayMs: Double) {
        onTimer(id, delayMs.coerceIn(0.0, MAX_TIMER_MS).toLong())
    }

    @HostAccess.Export
    fun log(level: String, message: String) {
        if (level == "w") {
            AppLogger.w(TAG, "[$pluginId] $message")
        } else {
            AppLogger.d(TAG, "[$pluginId] $message")
        }
    }

    private companion object {
        // 一天，防止脚本写入超大延时占住协程
        const val MAX_TIMER_MS = 86_400_000.0
    }
}
