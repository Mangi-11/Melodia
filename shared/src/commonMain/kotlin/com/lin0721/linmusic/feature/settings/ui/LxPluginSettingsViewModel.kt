package com.lin0721.linmusic.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.LxPluginItem
import com.lin0721.linmusic.core.source.LxPluginManager
import com.lin0721.linmusic.core.source.SourcePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    // 重名时覆盖更新，overwriteId 为空则作为新插件保留
    suspend fun confirmImport(info: LxPluginInfo, script: String, overwriteId: String?): LxPluginItem =
        manager.confirmImport(info, script, overwriteId)
}
