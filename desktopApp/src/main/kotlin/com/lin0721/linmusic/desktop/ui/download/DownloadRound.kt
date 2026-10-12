package com.lin0721.linmusic.desktop.ui.download

import com.lin0721.linmusic.desktop.data.download.DownloadTask
import com.lin0721.linmusic.desktop.data.download.DownloadTaskStatus

// 一轮下载的汇总结果：进行中时 progress 有值；一轮刚结束时 finished 为真
data class DownloadRoundSummary(
    val progress: Float? = null,
    val finished: Boolean = false,
    val allSucceeded: Boolean = false
)

// 同一时段内开始的任务算一轮，用整轮平均进度驱动进度环
// 已完成或失败的按 100% 计入，避免整体进度回退
class DownloadRoundTracker {
    private val roundIds = LinkedHashSet<String>()

    fun update(tasks: List<DownloadTask>): DownloadRoundSummary {
        val byId = tasks.associateBy { it.id }
        // 取消或移除的任务退出本轮
        roundIds.retainAll(byId.keys)
        tasks.filter { it.isActive }.forEach { roundIds += it.id }
        if (roundIds.isEmpty()) return DownloadRoundSummary()

        if (tasks.any { it.isActive }) {
            val progress = roundIds.map { id ->
                val task = byId.getValue(id)
                when (task.status) {
                    DownloadTaskStatus.SUCCEEDED, DownloadTaskStatus.FAILED -> 1f
                    else -> task.progress / 100f
                }
            }.average().toFloat()
            return DownloadRoundSummary(progress = progress)
        }

        // 没有进行中的任务：本轮结束，暂停的任务不算成功
        val allSucceeded = roundIds.all { byId.getValue(it).status == DownloadTaskStatus.SUCCEEDED }
        roundIds.clear()
        return DownloadRoundSummary(finished = true, allSucceeded = allSucceeded)
    }
}
