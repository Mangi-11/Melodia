package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.log.AppLogger
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

private const val TAG = "SingleInstance"
private const val LOCK_NAME = "instance.lock"
private const val PORT_NAME = "instance.port"
private const val ACTIVATE = "ACTIVATE"
private const val ACKNOWLEDGED = "OK"
private const val BACKLOG = 5
private const val CONNECT_TIMEOUT_MS = 500
private const val READ_TIMEOUT_MS = 1_000

// 首个实例持有文件锁并监听本机临时端口，后来的实例连上去要求激活后退出
// 端口号单独存文件：Windows 上被锁区域其他进程读不了
class SingleInstance(
    private val dir: File,
    private val retries: Int = 20,
    private val retryDelayMs: Long = 100
) : AutoCloseable {

    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    // true 为首个实例，onActivate 在其他实例要求激活时回调（非主线程）
    // false 表示已通知已有实例，调用方应直接退出
    fun acquire(onActivate: () -> Unit): Boolean {
        if (tryBecomePrimary(onActivate)) return true
        notifyPrimary()
        return false
    }

    private fun tryBecomePrimary(onActivate: () -> Unit): Boolean {
        return try {
            dir.mkdirs()
            val candidate = FileChannel.open(File(dir, LOCK_NAME).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val acquired = try {
                candidate.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (acquired == null) {
                candidate.close()
                return false
            }
            val listener = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
            channel = candidate
            lock = acquired
            server = listener
            File(dir, PORT_NAME).writeText(listener.localPort.toString())
            thread(isDaemon = true, name = "single-instance") { serve(listener, onActivate) }
            true
        } catch (e: IOException) {
            // 守卫失败时放行启动
            AppLogger.w(TAG, "单实例守卫初始化失败，按首个实例继续", e)
            true
        }
    }

    // 首个实例可能还没写好端口，重试
    private fun notifyPrimary() {
        repeat(retries) {
            val port = runCatching { File(dir, PORT_NAME).readText().trim().toInt() }.getOrNull()
            if (port != null && sendActivate(port)) return
            Thread.sleep(retryDelayMs)
        }
        AppLogger.w(TAG, "已有实例持有锁但未响应激活请求")
    }

    private fun sendActivate(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.getOutputStream().apply {
                write("$ACTIVATE\n".toByteArray())
                flush()
            }
            socket.getInputStream().bufferedReader().readLine() == ACKNOWLEDGED
        }
    } catch (_: IOException) {
        false
    }

    private fun serve(listener: ServerSocket, onActivate: () -> Unit) {
        while (!listener.isClosed) {
            try {
                listener.accept().use { socket ->
                    socket.soTimeout = READ_TIMEOUT_MS
                    if (socket.getInputStream().bufferedReader().readLine() == ACTIVATE) {
                        onActivate()
                        socket.getOutputStream().apply {
                            write("$ACKNOWLEDGED\n".toByteArray())
                            flush()
                        }
                    }
                }
            } catch (e: IOException) {
                if (!listener.isClosed) AppLogger.w(TAG, "处理激活请求失败", e)
            }
        }
    }

    override fun close() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        server = null
        lock = null
        channel = null
    }
}
