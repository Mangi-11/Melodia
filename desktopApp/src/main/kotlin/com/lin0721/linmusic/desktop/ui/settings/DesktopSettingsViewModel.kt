package com.lin0721.linmusic.desktop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.app.DesktopPaths
import com.lin0721.linmusic.desktop.data.CloseAction
import com.lin0721.linmusic.desktop.data.DesktopImageLoader
import com.lin0721.linmusic.desktop.data.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.desktop.player.cache.AudioCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 图片缓存与日志由全局对象管理，抽成参数便于测试替换
class LocalStorageOps(
    val imageCacheSize: () -> Long = DesktopImageLoader::diskCacheSize,
    val clearImageCache: () -> Unit = DesktopImageLoader::clear,
    val logsSize: () -> Long = AppLogger::getLogsSize,
    val clearLogs: () -> Unit = { AppLogger.clearLogs() }
)

// 桌面专属设置：窗口、开机自启、快捷键与本地存储
class DesktopSettingsViewModel(
    private val desktopPreferences: DesktopPreferences,
    private val hotkeys: GlobalHotkeyService,
    mediaSession: MediaSessionService,
    private val autoStart: AutoStartService,
    private val audioCache: AudioCache,
    private val storage: LocalStorageOps = LocalStorageOps(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> = stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initial
    )

    val closeAction = desktopPreferences.closeAction.asState(CloseAction.TRAY)
    val mediaKeysEnabled = desktopPreferences.mediaKeysEnabled.asState(true)
    val hotkeyMap = desktopPreferences.hotkeys.asState(HotkeyCombo.defaults)

    val hotkeysSupported: Boolean = hotkeys.isSupported
    val failedHotkeys: StateFlow<Set<HotkeyAction>> = hotkeys.failed
    val mediaSessionAvailable: StateFlow<Boolean> = mediaSession.available

    val autoStartUnavailableReason: String? = autoStart.unavailableReason

    // null 表示尚未读到系统中的自启状态
    private val _autoStartEnabled = MutableStateFlow<Boolean?>(null)
    val autoStartEnabled: StateFlow<Boolean?> = _autoStartEnabled.asStateFlow()

    private val _audioCacheBytes = MutableStateFlow(0L)
    val audioCacheBytes: StateFlow<Long> = _audioCacheBytes.asStateFlow()

    private val _imageCacheBytes = MutableStateFlow(0L)
    val imageCacheBytes: StateFlow<Long> = _imageCacheBytes.asStateFlow()

    private val _logBytes = MutableStateFlow(0L)
    val logBytes: StateFlow<Long> = _logBytes.asStateFlow()

    val imageCacheMaxBytes: Long = DesktopImageLoader.DISK_CACHE_MAX_BYTES
    val logDir: File = DesktopPaths.logDir
    val defaultDownloadDir: File = DesktopPaths.defaultDownloadDir

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // 进入设置页时刷新一次；这些值都要读磁盘或系统
    fun refresh() {
        viewModelScope.launch {
            _autoStartEnabled.value = withContext(ioDispatcher) { autoStart.isEnabled() }
        }
        viewModelScope.launch {
            withContext(ioDispatcher) {
                _audioCacheBytes.value = audioCache.totalSize()
                _imageCacheBytes.value = storage.imageCacheSize()
                _logBytes.value = storage.logsSize()
            }
        }
    }

    fun updateCloseAction(action: CloseAction) {
        viewModelScope.launch { desktopPreferences.saveCloseAction(action) }
    }

    fun updateMediaKeysEnabled(enabled: Boolean) {
        viewModelScope.launch { desktopPreferences.saveMediaKeysEnabled(enabled) }
    }

    fun updateHotkeys(map: Map<HotkeyAction, HotkeyCombo?>) {
        viewModelScope.launch { desktopPreferences.saveHotkeys(map) }
    }

    // 录制新组合期间暂停，避免按键被已注册的热键拦截
    fun pauseHotkeys() = hotkeys.pause()

    fun resumeHotkeys() = hotkeys.resume()

    fun updateAutoStart(enabled: Boolean) {
        if (autoStartUnavailableReason != null) return
        viewModelScope.launch {
            val ok = withContext(ioDispatcher) { autoStart.setEnabled(enabled) }
            if (ok) _autoStartEnabled.value = enabled else _messages.tryEmit("修改开机启动失败")
        }
    }

    // 容量上限调低后立即按新上限淘汰
    fun trimAudioCache(maxBytes: Long) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                audioCache.evict(maxBytes)
                _audioCacheBytes.value = audioCache.totalSize()
            }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                audioCache.clear()
                _audioCacheBytes.value = audioCache.totalSize()
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                storage.clearImageCache()
                _imageCacheBytes.value = storage.imageCacheSize()
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                storage.clearLogs()
                _logBytes.value = storage.logsSize()
            }
        }
    }
}
