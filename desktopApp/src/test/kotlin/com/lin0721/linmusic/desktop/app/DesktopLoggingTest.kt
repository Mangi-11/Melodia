package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.log.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopLoggingTest {

    private lateinit var dir: File
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var previousProperty: String? = null

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-logging-test").toFile()
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        previousProperty = System.getProperty(DesktopLogging.DEBUG_PROPERTY)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        if (previousProperty == null) System.clearProperty(DesktopLogging.DEBUG_PROPERTY)
        else System.setProperty(DesktopLogging.DEBUG_PROPERTY, previousProperty)
        AppEnvironment.isDebug = false
        AppLogger.setLevel(AppLogger.LogLevel.WARN)
        dir.deleteRecursively()
    }

    private fun logText(): String =
        AppLogger.getLogFiles().joinToString("\n") { it.readText() }

    private fun awaitLog(timeoutMs: Long = 5_000, predicate: (String) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(logText())) return true
            Thread.sleep(20)
        }
        return predicate(logText())
    }

    @Test
    fun releaseByDefaultAndDebugWhenPropertySet() {
        System.clearProperty(DesktopLogging.DEBUG_PROPERTY)
        DesktopLogging.install(File(dir, "a")) { "WARN" }
        assertFalse(AppEnvironment.isDebug)

        System.setProperty(DesktopLogging.DEBUG_PROPERTY, "true")
        DesktopLogging.install(File(dir, "b")) { "WARN" }
        assertTrue(AppEnvironment.isDebug)
    }

    @Test
    fun createsLogDirAndWritesAtSavedLevel() {
        val logDir = File(dir, "logs")
        DesktopLogging.install(logDir) { "WARN" }
        assertTrue(logDir.isDirectory)

        AppLogger.i("T", "标准级别消息")
        AppLogger.w("T", "警告级别消息")
        assertTrue("警告应写入文件", awaitLog { "警告级别消息" in it })
        assertFalse("低于已保存级别的消息不应写入", "标准级别消息" in logText())
    }

    @Test
    fun invalidSavedLevelFallsBackToDefault() {
        DesktopLogging.install(File(dir, "logs")) { "不是级别" }
        AppLogger.e("T", "错误消息")
        assertTrue(awaitLog { "错误消息" in it })
    }

    @Test
    fun installsCrashHandlerThatChainsToPrevious() {
        var delegated: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> delegated = throwable }
        DesktopLogging.install(File(dir, "logs")) { "WARN" }

        val installed = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue(installed is DesktopCrashHandler)
        val failure = IllegalStateException("模拟崩溃")
        installed.uncaughtException(Thread.currentThread(), failure)

        assertSame(failure, delegated)
        assertTrue("崩溃信息应写入日志", awaitLog { "FATAL CRASH" in it && "模拟崩溃" in it })
        assertTrue(logText().contains("App Version"))
        assertNotEquals("", AppInfo.version)
    }

    @Test
    fun appVersionComesFromGeneratedResource() {
        assertTrue("版本号应来自构建生成的资源，实际 ${AppInfo.version}", AppInfo.version != "unknown" && AppInfo.version.isNotBlank())
        assertEquals(AppInfo.version, AppInfo.version)
    }
}
