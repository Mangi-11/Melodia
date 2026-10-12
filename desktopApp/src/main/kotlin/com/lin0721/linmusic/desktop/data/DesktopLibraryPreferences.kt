package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

private const val TAG = "DesktopLibraryPrefs"

// java.util.prefs 在 Windows 落到注册表 HKCU\Software\JavaSoft\Prefs
class DesktopLibraryPreferences : LibraryPreferences {

    private val prefs: Preferences = Preferences.userRoot().node("melodia/library")

    override fun pinnedIds(): Set<String> = splitList(prefs.get(KEY_PINNED_IDS, "")).toSet()

    override fun setPinnedIds(ids: Set<String>) = put(KEY_PINNED_IDS, ids.joinToString(SEPARATOR))

    override fun customPlaylistOrder(): List<String> = splitList(prefs.get(KEY_CUSTOM_ORDER, ""))

    override fun setCustomPlaylistOrder(order: List<String>) = put(KEY_CUSTOM_ORDER, order.joinToString(SEPARATOR))

    override fun sortOrderName(): String? = prefs.get(KEY_SORT_ORDER, null)

    override fun setSortOrderName(name: String) = put(KEY_SORT_ORDER, name)

    override fun isGridView(): Boolean = prefs.getBoolean(KEY_GRID_VIEW, false)

    override fun setGridView(isGrid: Boolean) {
        prefs.putBoolean(KEY_GRID_VIEW, isGrid)
        flush()
    }

    private fun put(key: String, value: String) {
        prefs.put(key, value)
        flush()
    }

    private fun flush() {
        try {
            prefs.flush()
        } catch (e: BackingStoreException) {
            AppLogger.w(TAG, "音乐库偏好落盘失败", e)
        }
    }

    private fun splitList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }

    private companion object {
        const val SEPARATOR = ","
        const val KEY_PINNED_IDS = "pinned_ids"
        const val KEY_CUSTOM_ORDER = "custom_playlist_order"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_GRID_VIEW = "is_grid_view"
    }
}
