package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.network.AppString
import com.lin0721.linmusic.core.network.ResourceProvider

// 文案与 Android 端 strings.xml 保持一致
class DesktopResourceProvider : ResourceProvider() {
    override fun getString(key: AppString): String = when (key) {
        AppString.ErrorNetwork -> "网络连接失败，请检查网络后重试"
        AppString.ErrorRiskControl -> "网络繁忙，请稍后重试"
        AppString.ErrorUnauthorized -> "请先登录"
        AppString.ErrorParse -> "数据解析失败"
        AppString.ErrorBizDefault -> "操作失败，请重试"
    }
}
