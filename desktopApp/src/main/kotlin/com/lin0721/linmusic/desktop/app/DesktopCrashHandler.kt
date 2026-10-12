package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "CrashHandler"

// 日志异步写盘，崩溃后等它落盘
private const val FLUSH_WAIT_MS = 800L

// 写入日志后交还原处理器
class DesktopCrashHandler(
    private val describeEnvironment: () -> String,
    private val log: (message: String, throwable: Throwable) -> Unit,
    private val flush: () -> Unit,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            log("================ FATAL CRASH ================\n${describeEnvironment()}\nThread      : ${thread.name}\n=============================================\n异常崩溃堆栈信息:", throwable)
            flush()
        } catch (_: Throwable) {
            // 日志失败也要走原处理器
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install() {
            Thread.setDefaultUncaughtExceptionHandler(
                DesktopCrashHandler(
                    describeEnvironment = ::environmentSummary,
                    log = { message, throwable -> AppLogger.e(TAG, message, throwable) },
                    flush = { Thread.sleep(FLUSH_WAIT_MS) },
                    previous = Thread.getDefaultUncaughtExceptionHandler()
                )
            )
        }

        internal fun environmentSummary(): String = buildString {
            appendLine("App Version : ${AppInfo.version}")
            appendLine("OS          : ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            append("Java        : ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        }
    }
}
