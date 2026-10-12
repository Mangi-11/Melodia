package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.player.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// 桌面播放器在通用播放接口之外的能力
interface DesktopPlaybackController : PlaybackController, AudioOutputControl {

    // 0 到 100
    val volume: StateFlow<Int>

    fun setVolume(percent: Int)

    // 需要提示给用户的播放事件，如播放失败、输出设备断开
    val messages: Flow<String>

    // 播放组件不可用的原因；可用时为 null
    val unavailableReason: String?

    // 退出前同步保存播放状态并释放播放内核
    fun release()
}
