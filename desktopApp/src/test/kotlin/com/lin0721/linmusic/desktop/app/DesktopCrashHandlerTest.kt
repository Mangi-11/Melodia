package com.lin0721.linmusic.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCrashHandlerTest {

    private val failure = RuntimeException("炸了")

    @Test
    fun logsEnvironmentAndThreadNameThenDelegates() {
        var message = ""
        var logged: Throwable? = null
        var flushed = false
        var delegated: Pair<Thread, Throwable>? = null
        val thread = Thread({}, "worker-7")
        val handler = DesktopCrashHandler(
            describeEnvironment = { "App Version : 9.9.9" },
            log = { m, t -> message = m; logged = t },
            flush = { flushed = true },
            previous = { t, e -> delegated = t to e }
        )

        handler.uncaughtException(thread, failure)

        assertTrue(message.contains("FATAL CRASH"))
        assertTrue(message.contains("App Version : 9.9.9"))
        assertTrue(message.contains("worker-7"))
        assertSame(failure, logged)
        assertTrue(flushed)
        assertEquals(thread to failure, delegated)
    }

    @Test
    fun delegatesEvenWhenLoggingThrows() {
        var delegated = false
        val handler = DesktopCrashHandler(
            describeEnvironment = { "" },
            log = { _, _ -> error("日志写入失败") },
            flush = {},
            previous = { _, _ -> delegated = true }
        )

        handler.uncaughtException(Thread.currentThread(), failure)

        assertTrue("记录失败不能妨碍原处理器", delegated)
    }

    @Test
    fun worksWithoutPreviousHandler() {
        var logged = false
        val handler = DesktopCrashHandler(
            describeEnvironment = { "" },
            log = { _, _ -> logged = true },
            flush = {},
            previous = null
        )

        handler.uncaughtException(Thread.currentThread(), failure)

        assertTrue(logged)
    }

    @Test
    fun environmentSummaryContainsVersionOsAndJava() {
        val summary = DesktopCrashHandler.environmentSummary()
        assertTrue(summary.contains("App Version"))
        assertTrue(summary.contains("OS"))
        assertTrue(summary.contains("Java"))
    }
}
