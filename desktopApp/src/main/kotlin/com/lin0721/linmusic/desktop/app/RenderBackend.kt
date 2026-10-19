package com.lin0721.linmusic.desktop.app

import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "RenderBackend"
private const val RENDER_API_PROPERTY = "skiko.renderApi"
private const val RENDER_API_ENV = "SKIKO_RENDER_API"

// 渲染后端；AUTO 交给 Skiko 按平台选择，其余取值即 skiko.renderApi
enum class RenderBackend(val skikoApi: String?) {
    AUTO(null),
    DIRECT3D("DIRECT3D"),
    OPENGL("OPENGL"),
    SOFTWARE("SOFTWARE");

    companion object {
        fun fromName(name: String?): RenderBackend = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

// 须在创建任何窗口前调用；环境变量优先级高于系统属性，留作显卡异常打不开设置页时的兜底
fun applyRenderBackend(backend: RenderBackend) {
    val override = System.getenv(RENDER_API_ENV)
    if (!override.isNullOrBlank()) {
        AppLogger.i(TAG, "环境变量 $RENDER_API_ENV=$override 生效，忽略设置项 ${backend.name}")
        return
    }
    backend.skikoApi?.let { System.setProperty(RENDER_API_PROPERTY, it) }
    AppLogger.i(TAG, "渲染方式设置=${backend.name}")
}
