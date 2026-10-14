package com.lin0721.linmusic.feature.playlist.presentation

import com.lin0721.linmusic.core.model.Track

// 歌曲列表多选状态：按歌曲 id 记录，排序、过滤、分页追加都不影响已选
data class TrackSelection(
    val active: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
) {
    val count: Int get() = selectedIds.size

    fun isSelected(id: Long): Boolean = id in selectedIds

    // firstId 非空时作为首个已选项
    fun enter(firstId: Long? = null): TrackSelection =
        TrackSelection(active = true, selectedIds = if (firstId != null) linkedSetOf(firstId) else emptySet())

    fun toggle(id: Long): TrackSelection {
        if (!active) return this
        val next = LinkedHashSet(selectedIds)
        if (!next.remove(id)) next.add(id)
        return copy(selectedIds = next)
    }

    // ids 已全部选中时取消这批，否则补选缺的部分
    fun toggleAll(ids: Collection<Long>): TrackSelection {
        if (!active || ids.isEmpty()) return this
        val next = LinkedHashSet(selectedIds)
        if (next.containsAll(ids)) next.removeAll(ids.toSet()) else next.addAll(ids)
        return copy(selectedIds = next)
    }

    fun exit(): TrackSelection = TrackSelection()

    fun pick(tracks: List<Track>): List<Track> = tracks.filter { it.id in selectedIds }
}
