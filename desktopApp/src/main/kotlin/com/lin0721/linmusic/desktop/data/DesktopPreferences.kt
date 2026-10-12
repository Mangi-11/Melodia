package com.lin0721.linmusic.desktop.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.desktop.app.SavedWindow
import com.lin0721.linmusic.desktop.app.WindowBounds
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.player.AUTO_AUDIO_DEVICE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class CloseAction { TRAY, EXIT }

// 音乐库形态：收起为窄条 / 默认侧栏 / 展开铺满中间区域
enum class LibraryMode { RAIL, DEFAULT, EXPANDED }

// 音乐库条目的显示方式
enum class LibraryViewMode { COMPACT_LIST, LIST, SMALL_GRID, LARGE_GRID }

// 桌面端独有设置；快捷键以 “动作=修饰键:键码” 分号拼接存储，空值表示用户已清除该键
class DesktopPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val STORE_NAME = "desktop_prefs"
        const val DEFAULT_LYRICS_VIEW_FONT_SIZE = 40

        private val KEY_HOTKEYS = stringPreferencesKey("hotkeys")
        private val KEY_MEDIA_KEYS_ENABLED = booleanPreferencesKey("media_keys_enabled")
        private val KEY_CLOSE_ACTION = stringPreferencesKey("close_action")
        private val KEY_NOW_PLAYING_OPEN = booleanPreferencesKey("now_playing_panel_open")
        private val KEY_LIBRARY_MODE = stringPreferencesKey("library_mode")
        private val KEY_LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        private val KEY_LIBRARY_WIDTH = floatPreferencesKey("library_width")
        private val KEY_NOW_PLAYING_WIDTH = floatPreferencesKey("now_playing_width")
        private val KEY_AUDIO_DEVICE = stringPreferencesKey("audio_device")
        private val KEY_LYRICS_VIEW_FONT_SIZE = intPreferencesKey("lyrics_view_font_size")
        private val KEY_WINDOW_BOUNDS = stringPreferencesKey("window_bounds")
        private val KEY_WINDOW_MAXIMIZED = booleanPreferencesKey("window_maximized")
    }

    val hotkeys: Flow<Map<HotkeyAction, HotkeyCombo?>> = dataStore.data.map { prefs ->
        val saved = parseHotkeys(prefs[KEY_HOTKEYS].orEmpty())
        HotkeyAction.entries.associateWith { action ->
            if (saved.containsKey(action)) saved[action] else HotkeyCombo.defaults[action]
        }
    }.distinctUntilChanged()

    val mediaKeysEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MEDIA_KEYS_ENABLED] ?: true }.distinctUntilChanged()

    val closeAction: Flow<CloseAction> = dataStore.data.map { prefs ->
        prefs[KEY_CLOSE_ACTION]?.let { name -> CloseAction.entries.firstOrNull { it.name == name } } ?: CloseAction.TRAY
    }.distinctUntilChanged()

    val nowPlayingPanelOpen: Flow<Boolean> = dataStore.data.map { it[KEY_NOW_PLAYING_OPEN] ?: true }.distinctUntilChanged()

    val libraryMode: Flow<LibraryMode> = dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_MODE]?.let { name -> LibraryMode.entries.firstOrNull { it.name == name } } ?: LibraryMode.DEFAULT
    }.distinctUntilChanged()

    val libraryViewMode: Flow<LibraryViewMode> = dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_VIEW_MODE]?.let { name -> LibraryViewMode.entries.firstOrNull { it.name == name } } ?: LibraryViewMode.LIST
    }.distinctUntilChanged()

    // 拖动调整过的栏宽（dp），未调整过为 null
    val libraryWidth: Flow<Float?> = dataStore.data.map { it[KEY_LIBRARY_WIDTH] }.distinctUntilChanged()

    val nowPlayingWidth: Flow<Float?> = dataStore.data.map { it[KEY_NOW_PLAYING_WIDTH] }.distinctUntilChanged()

    // 音频输出设备的 mpv 设备名，缺省跟随系统默认
    // 全屏歌词字号（sp）：与移动端共用的同名偏好默认值只适合手机，桌面端单独存
    val lyricsViewFontSize: Flow<Int> = dataStore.data.map { it[KEY_LYRICS_VIEW_FONT_SIZE] ?: DEFAULT_LYRICS_VIEW_FONT_SIZE }.distinctUntilChanged()

    val audioDevice: Flow<String> = dataStore.data.map { it[KEY_AUDIO_DEVICE] ?: AUTO_AUDIO_DEVICE }.distinctUntilChanged()

    suspend fun saveHotkeys(hotkeys: Map<HotkeyAction, HotkeyCombo?>) {
        dataStore.edit { prefs ->
            prefs[KEY_HOTKEYS] = hotkeys.entries.joinToString(";") { (action, combo) -> "${action.name}=${combo?.encode().orEmpty()}" }
        }
    }

    suspend fun saveMediaKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MEDIA_KEYS_ENABLED] = enabled }
    }

    suspend fun saveCloseAction(action: CloseAction) {
        dataStore.edit { it[KEY_CLOSE_ACTION] = action.name }
    }

    suspend fun saveNowPlayingPanelOpen(open: Boolean) {
        dataStore.edit { it[KEY_NOW_PLAYING_OPEN] = open }
    }

    suspend fun saveLibraryMode(mode: LibraryMode) {
        dataStore.edit { it[KEY_LIBRARY_MODE] = mode.name }
    }

    suspend fun saveLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[KEY_LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun saveLibraryWidth(widthDp: Float) {
        dataStore.edit { it[KEY_LIBRARY_WIDTH] = widthDp }
    }

    suspend fun saveAudioDevice(name: String) {
        dataStore.edit { it[KEY_AUDIO_DEVICE] = name }
    }

    suspend fun saveLyricsViewFontSize(size: Int) {
        dataStore.edit { it[KEY_LYRICS_VIEW_FONT_SIZE] = size }
    }

    suspend fun saveNowPlayingWidth(widthDp: Float) {
        dataStore.edit { it[KEY_NOW_PLAYING_WIDTH] = widthDp }
    }

    suspend fun loadWindow(): SavedWindow = dataStore.data.first().let { prefs ->
        SavedWindow(
            bounds = prefs[KEY_WINDOW_BOUNDS]?.let(WindowBounds::decode),
            maximized = prefs[KEY_WINDOW_MAXIMIZED] ?: false
        )
    }

    // bounds 为 null 时保留已存的浮动位置与大小
    suspend fun saveWindow(bounds: WindowBounds?, maximized: Boolean) {
        dataStore.edit { prefs ->
            if (bounds != null) prefs[KEY_WINDOW_BOUNDS] = bounds.encode()
            prefs[KEY_WINDOW_MAXIMIZED] = maximized
        }
    }

    // 解析失败的条目忽略，回落到默认值
    private fun parseHotkeys(raw: String): Map<HotkeyAction, HotkeyCombo?> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<HotkeyAction, HotkeyCombo?>()
        raw.split(';').forEach { entry ->
            val (name, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val action = HotkeyAction.entries.firstOrNull { it.name == name } ?: return@forEach
            if (value.isEmpty()) {
                result[action] = null
            } else {
                HotkeyCombo.decode(value)?.let { result[action] = it }
            }
        }
        return result
    }
}
