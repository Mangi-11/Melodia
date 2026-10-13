package com.lin0721.linmusic.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.source.ArchiveScript
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginManager
import com.lin0721.linmusic.core.source.SourcePreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 社区源（LX）插件的列表与导入状态，桌面设置页使用
class LxPluginSettingsViewModel(
    private val manager: LxPluginManager,
    private val sourcePreferences: SourcePreferences
) : ViewModel() {

    val pluginEnabled: StateFlow<Boolean> = sourcePreferences.lxPluginEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val plugins: StateFlow<List<LxPluginItem>> = sourcePreferences.lxPlugins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { manager.setEnabled(enabled) }
    }

    fun toggle(id: String) {
        viewModelScope.launch { manager.toggle(id) }
    }

    fun moveUp(id: String) {
        viewModelScope.launch { sourcePreferences.moveLxPluginUp(id) }
    }

    fun moveDown(id: String) {
        viewModelScope.launch { sourcePreferences.moveLxPluginDown(id) }
    }

    fun remove(id: String) {
        viewModelScope.launch { manager.remove(id) }
    }

    // 输入可以是脚本文本或远程链接，失败时统一折叠为 Failure
    suspend fun importFrom(input: String): LxPluginManager.ImportCheckResult {
        val content = manager.resolveScriptContent(input)
        if (content.isFailure) {
            return LxPluginManager.ImportCheckResult.Failure(
                "下载远程脚本失败：${content.exceptionOrNull()?.message.orEmpty()}"
            )
        }
        return manager.precheckAndImport(content.getOrThrow())
    }

    // 批量导入跑在 ViewModel 作用域，离开设置页不会中断
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    // 待用户确认的重名插件，按到达顺序逐个处理
    private val _pendingDuplicates = MutableStateFlow<List<LxPluginManager.ImportCheckResult.Duplicate>>(emptyList())
    val pendingDuplicates: StateFlow<List<LxPluginManager.ImportCheckResult.Duplicate>> = _pendingDuplicates.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun addDuplicate(duplicate: LxPluginManager.ImportCheckResult.Duplicate) {
        _pendingDuplicates.update { it + duplicate }
    }

    // 导入压缩包里勾选的脚本，结果经 messages 与 pendingDuplicates 回报
    fun importScriptsInBackground(scripts: List<ArchiveScript>) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val batch = manager.importScripts(scripts)
                _pendingDuplicates.update { it + batch.duplicates }
                _messages.emit(batch.summary())
                batch.failures.firstOrNull()?.let { _messages.emit("插件解析失败：$it") }
            } catch (e: Exception) {
                _messages.emit("导入异常：${e.message}")
            } finally {
                _importing.value = false
            }
        }
    }

    // 处理队首的重名项：overwrite 为真覆盖更新，否则保留为副本
    fun resolveFirstDuplicate(overwrite: Boolean) {
        val dup = _pendingDuplicates.value.firstOrNull() ?: return
        _pendingDuplicates.update { it.drop(1) }
        viewModelScope.launch {
            manager.confirmImport(dup.newInfo, dup.rawScript, if (overwrite) dup.existing.id else null)
            _messages.emit(if (overwrite) "已覆盖更新插件：${dup.newInfo.name}" else "已作为新插件添加：${dup.newInfo.name}")
        }
    }

    fun skipFirstDuplicate() {
        _pendingDuplicates.update { it.drop(1) }
    }

    // 重名时覆盖更新，overwriteId 为空则作为新插件保留
    suspend fun confirmImport(info: LxPluginInfo, script: String, overwriteId: String?): LxPluginItem =
        manager.confirmImport(info, script, overwriteId)
}
