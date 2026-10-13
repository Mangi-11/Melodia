package com.lin0721.linmusic.desktop.app

import java.awt.GraphicsEnvironment
import java.awt.HeadlessException

const val MIN_WINDOW_WIDTH = 960
const val MIN_WINDOW_HEIGHT = 600

private const val TITLE_STRIP_HEIGHT = 40
private const val MIN_VISIBLE_WIDTH = 100

// AWT 逻辑坐标（与 Compose 的 dp 一一对应）
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {

    fun encode(): String = "$x,$y,$width,$height"

    // 标题栏条带落在某块屏幕内才可操作，显示器拔掉后窗口不会留在屏外
    fun isReachableOn(screens: List<WindowBounds>): Boolean = screens.any { screen ->
        val overlapWidth = minOf(x + width, screen.x + screen.width) - maxOf(x, screen.x)
        overlapWidth >= MIN_VISIBLE_WIDTH && y >= screen.y && y + TITLE_STRIP_HEIGHT <= screen.y + screen.height
    }

    companion object {
        fun decode(raw: String): WindowBounds? {
            val parts = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
            if (parts.size != 4) return null
            val (x, y, width, height) = parts
            if (width < MIN_WINDOW_WIDTH || height < MIN_WINDOW_HEIGHT) return null
            return WindowBounds(x, y, width, height)
        }
    }
}

// bounds 为最近一次浮动形态的位置与大小
data class SavedWindow(val bounds: WindowBounds?, val maximized: Boolean)

fun currentScreenBounds(): List<WindowBounds> = try {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        device.defaultConfiguration.bounds.let { WindowBounds(it.x, it.y, it.width, it.height) }
    }
} catch (_: HeadlessException) {
    emptyList()
}

// 界面缩放后内容区的有效 dp 变小，窗口最小尺寸随之放大；上限为所在屏幕，避免窗口无法放进屏幕
fun scaledMinWindowSize(scaleFactor: Float, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
    val width = (MIN_WINDOW_WIDTH * scaleFactor).toInt().coerceAtLeast(MIN_WINDOW_WIDTH)
    val height = (MIN_WINDOW_HEIGHT * scaleFactor).toInt().coerceAtLeast(MIN_WINDOW_HEIGHT)
    return width.coerceAtMost(maxOf(screenWidth, MIN_WINDOW_WIDTH)) to height.coerceAtMost(maxOf(screenHeight, MIN_WINDOW_HEIGHT))
}
