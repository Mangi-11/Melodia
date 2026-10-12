package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.desktop.platform.CloseAction
import com.lin0721.linmusic.feature.settings.ui.CommonSettingsViewModel
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent as AwtKeyEvent
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser

// 与 Android 音质设置保持同一组选项
private val CacheSizeOptions = listOf(500L, 1024L, 2048L, 5120L, 10240L).map { it * 1024 * 1024 }

private val CrossfadeDurationOptionsMs = listOf(1_000, 2_000, 3_000, 4_000, 6_000, 8_000, 10_000, 12_000)

private val QualityOptions = listOf(
    "standard" to "标准音质",
    "exhigh" to "极高音质",
    "lossless" to "无损音质 (FLAC)",
    "hires" to "Hi-Res 无损",
    "jymaster" to "超清母带"
)

@Composable
fun SettingsPage(
    common: CommonSettingsViewModel,
    desktop: DesktopSettingsViewModel,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(desktop) { desktop.refresh() }
    LaunchedEffect(desktop) { desktop.messages.collect { navigator.showMessage(it) } }

    val quality by common.wifiQuality.collectAsState()
    val fallbackEnabled by common.fallbackEnabled.collectAsState()
    val communityPriority by common.communityPriority.collectAsState()
    val unmServerUrl by common.unmServerUrl.collectAsState()
    val unmRemoteFallbackEnabled by common.unmRemoteFallbackEnabled.collectAsState()
    val unmAutoMatch by common.unmAutoMatch.collectAsState()
    val showDesktopLyric by common.showDesktopLrc.collectAsState()
    val logLevelName by common.logLevel.collectAsState()
    val logBytes by desktop.logBytes.collectAsState()
    val streamCacheEnabled by common.streamCacheEnabled.collectAsState()
    val cacheMaxSize by common.audioCacheMaxSize.collectAsState()
    val cacheUsedBytes by desktop.audioCacheBytes.collectAsState()
    val imageCacheBytes by desktop.imageCacheBytes.collectAsState()
    val crossfadeEnabled by common.crossfadeEnabled.collectAsState()
    val crossfadeDurationMs by common.crossfadeDurationMs.collectAsState()
    val downloadFolder by common.downloadFolderUri.collectAsState()
    val downloadLyrics by common.downloadLyricsEnabled.collectAsState()
    val defaultPlaylistPrivate by common.defaultPlaylistPrivate.collectAsState()
    val cardLayout by common.fullPlayerCardLayout.collectAsState()
    val closeAction by desktop.closeAction.collectAsState()
    val mediaKeysEnabled by desktop.mediaKeysEnabled.collectAsState()
    val hotkeyMap by desktop.hotkeyMap.collectAsState()
    val failedHotkeys by desktop.failedHotkeys.collectAsState()
    val mediaSessionAvailable by desktop.mediaSessionAvailable.collectAsState()

    val scrollState = rememberScrollState()
    HoverScrollbarBox(scrollState) {
        Column(
            modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("设置", color = DesktopColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)

            SettingsCard("播放") {
                SettingRow("在线播放音质") {
                    QualitySelector(quality, common::updateWifiQuality)
                }
                SettingRow("歌曲淡入淡出", subtitle = "自动切歌时前后两首交叉淡化，手动切歌不受影响") {
                    SettingSwitch(crossfadeEnabled, onChange = common::updateCrossfadeEnabled)
                }
                if (crossfadeEnabled) {
                    SettingRow("淡化时长") {
                        CrossfadeDurationSelector(crossfadeDurationMs, common::updateCrossfadeDurationMs)
                    }
                }
            }

            SettingsCard("音源与换源") {
                SettingRow(
                    title = "无版权/VIP 自动换源",
                    subtitle = "官方网易云音源不可用或仅为试听时，自动从可用音源解析完整播放直链"
                ) {
                    SettingSwitch(fallbackEnabled, onChange = common::updateFallbackEnabled)
                }

                if (fallbackEnabled) {
                    SettingRow(
                        title = "社区源优先",
                        subtitle = "开启后优先尝试通过 LX 插件脚本解析，失败后 fallback 到远程 UNM 服务；关闭则先尝试远程 UNM"
                    ) {
                        SettingSwitch(communityPriority, onChange = common::updateCommunityPriority)
                    }
                }

                SettingRow(
                    title = "启用远程 UNM 服务",
                    subtitle = "向远程自建或第三方的 UNM（UnblockNeteaseMusic）服务器请求音频直链"
                ) {
                    SettingSwitch(unmRemoteFallbackEnabled, onChange = common::updateUnmRemoteFallbackEnabled)
                }

                if (unmRemoteFallbackEnabled) {
                    SettingRow(
                        title = "远程服务接口 (Base URL)",
                        subtitle = unmServerUrl.ifBlank { "未配置" }
                    ) {
                        DesktopServerUrlInput(
                            currentUrl = unmServerUrl,
                            onSave = common::updateUnmServerUrl,
                            onReset = common::resetUnmServerUrl
                        )
                    }

                    SettingRow(
                        title = "服务端自动选择模式",
                        subtitle = "由服务端自动轮询最优源匹配"
                    ) {
                        SettingSwitch(unmAutoMatch, onChange = common::updateUnmAutoMatch)
                    }
                }
            }

            SettingsCard("缓存") {
                SettingRow("边听边存", subtitle = "在线播放完整听完的歌曲保存到本地，下次直接播放，离线时也可播放") {
                    SettingSwitch(streamCacheEnabled, onChange = common::updateStreamCacheEnabled)
                }
                SettingRow("缓存容量上限") {
                    CacheSizeSelector(cacheMaxSize) { size ->
                        common.updateAudioCacheMaxSize(size)
                        desktop.trimAudioCache(size)
                    }
                }
                SettingRow("图片缓存", subtitle = "${formatBytes(imageCacheBytes)} / ${formatBytes(desktop.imageCacheMaxBytes)}") {
                    TextButton(onClick = desktop::clearImageCache) {
                        Text("清除图片缓存", color = DesktopColors.Accent)
                    }
                }
                SettingRow("已用空间", subtitle = formatBytes(cacheUsedBytes)) {
                    TextButton(onClick = desktop::clearAudioCache) {
                        Text("清除缓存", color = DesktopColors.Accent)
                    }
                }
            }

            SettingsCard("下载") {
                val customFolder = downloadFolder?.takeIf { it.isNotBlank() }
                SettingRow("下载目录", subtitle = customFolder ?: desktop.defaultDownloadDir.absolutePath) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (customFolder != null) {
                            TextButton(onClick = { common.updateDownloadFolderUri(null) }) {
                                Text("恢复默认", color = DesktopColors.TextGray)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val initial = File(customFolder ?: desktop.defaultDownloadDir.absolutePath)
                                chooseDirectory(initial)?.let { common.updateDownloadFolderUri(it.absolutePath) }
                            }
                        }) {
                            Text("更改", color = DesktopColors.Accent)
                        }
                    }
                }
                SettingRow("内嵌歌词", subtitle = "下载时把歌词写入音频文件的标签") {
                    SettingSwitch(downloadLyrics, onChange = common::updateDownloadLyricsEnabled)
                }
            }

            SettingsCard("歌单") {
                SettingRow("新建歌单默认设为隐私", subtitle = "创建歌单对话框中的隐私开关以此为初始值") {
                    SettingSwitch(defaultPlaylistPrivate, onChange = common::updateDefaultPlaylistPrivate)
                }
            }

            SettingsCard("桌面歌词") {
                SettingRow("显示桌面歌词") {
                    SettingSwitch(showDesktopLyric, onChange = common::updateShowDesktopLrc)
                }
            }

            SettingsCard("正在播放面板") {
                Text("拖动调整信息卡片的顺序，关闭开关可隐藏对应卡片", color = DesktopColors.TextGray, fontSize = 12.sp)
                CardLayoutEditor(cardLayout, common::updateFullPlayerCardLayout)
            }

            SettingsCard("窗口") {
                SettingRow("关闭主窗口时") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CloseOption("最小化到托盘", closeAction == CloseAction.TRAY) {
                            desktop.updateCloseAction(CloseAction.TRAY)
                        }
                        CloseOption("直接退出", closeAction == CloseAction.EXIT) {
                            desktop.updateCloseAction(CloseAction.EXIT)
                        }
                    }
                }
                AutoStartRow(desktop)
            }

            SettingsCard("日志") {
                SettingRow("日志级别", subtitle = "越详细越利于排查问题，立即生效") {
                    LogLevelSelector(logLevelName, common::updateLogLevel)
                }
                SettingRow("日志文件", subtitle = "${formatBytes(logBytes)} · ${desktop.logDir.absolutePath}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (!openDirectory(desktop.logDir)) navigator.showMessage("无法打开日志目录")
                        }) {
                            Text("打开目录", color = DesktopColors.Accent)
                        }
                        TextButton(onClick = desktop::clearLogs) {
                            Text("清除", color = DesktopColors.TextGray)
                        }
                    }
                }
            }

            if (desktop.hotkeysSupported) {
                SettingsCard("快捷键") {
                    SettingRow(
                        title = "系统媒体控制（媒体键与系统播放卡片）",
                        subtitle = if (mediaSessionAvailable) null else "系统卡片不可用，已改用全局媒体键"
                    ) {
                        SettingSwitch(mediaKeysEnabled, onChange = desktop::updateMediaKeysEnabled)
                    }
                    HotkeyEditor(
                        hotkeyMap = hotkeyMap,
                        failed = failedHotkeys,
                        onPause = desktop::pauseHotkeys,
                        onResume = desktop::resumeHotkeys,
                        onSave = desktop::updateHotkeys
                    )
                }
            }
        }
    }
}

private fun chooseDirectory(initial: File): File? {
    val chooser = JFileChooser(initial.takeIf { it.isDirectory }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "选择下载目录"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.CardSurface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = DesktopColors.TextGray,
    trailing: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = subtitleColor, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
internal fun SettingSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DesktopColors.TextPrimary,
            checkedTrackColor = DesktopColors.Accent,
            uncheckedThumbColor = DesktopColors.TextGray,
            uncheckedTrackColor = DesktopColors.SurfaceLight
        )
    )
}

@Composable
private fun LogLevelSelector(currentName: String, onSelect: (AppLogger.LogLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = runCatching { AppLogger.LogLevel.valueOf(currentName) }.getOrDefault(AppLogger.LogLevel.WARN)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(logLevelLabel(current), color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            AppLogger.LogLevel.entries.forEach { level ->
                SimpleMenuItem(
                    text = logLevelLabel(level),
                    trailing = if (level == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(level)
                    }
                )
            }
        }
    }
}

private fun logLevelLabel(level: AppLogger.LogLevel): String = when (level) {
    AppLogger.LogLevel.DEBUG -> "详细"
    AppLogger.LogLevel.INFO -> "标准"
    AppLogger.LogLevel.WARN -> "精简"
    AppLogger.LogLevel.ERROR -> "仅错误"
}

private fun openDirectory(dir: File): Boolean = try {
    dir.mkdirs()
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(dir)
        true
    } else {
        false
    }
} catch (_: Exception) {
    false
}

@Composable
private fun CacheSizeSelector(currentBytes: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(formatBytes(currentBytes), color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            CacheSizeOptions.forEach { value ->
                SimpleMenuItem(
                    text = formatBytes(value),
                    trailing = if (value == currentBytes) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> "%.1f GB".format(java.util.Locale.ROOT, mb / 1024)
        mb >= 1 -> "%.0f MB".format(java.util.Locale.ROOT, mb)
        else -> "%.0f KB".format(java.util.Locale.ROOT, bytes / 1024.0)
    }
}

@Composable
private fun CrossfadeDurationSelector(currentMs: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = CrossfadePolicy.normalizeDurationMs(currentMs)
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(formatSeconds(current), color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            CrossfadeDurationOptionsMs.forEach { value ->
                SimpleMenuItem(
                    text = formatSeconds(value),
                    trailing = if (value == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

private fun formatSeconds(ms: Int): String = if (ms % 1000 == 0) "${ms / 1000} 秒" else "${ms / 1000.0} 秒"

@Composable
private fun QualitySelector(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(QualityOptions.firstOrNull { it.first == current }?.second ?: current, color = DesktopColors.TextPrimary)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = DesktopColors.TextGray)
        }
        DesktopMenu(expanded = expanded, onDismiss = { expanded = false }, width = 168.dp) {
            QualityOptions.forEach { (value, label) ->
                SimpleMenuItem(
                    text = label,
                    trailing = if (value == current) {
                        { Icon(Icons.Rounded.Check, null, tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun CloseOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = DesktopColors.Accent, unselectedColor = DesktopColors.TextGray)
        )
        Text(label, color = DesktopColors.TextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun AutoStartRow(desktop: DesktopSettingsViewModel) {
    val enabled by desktop.autoStartEnabled.collectAsState()
    val unavailableReason = desktop.autoStartUnavailableReason
    SettingRow(
        title = "开机自动启动",
        subtitle = unavailableReason
    ) {
        SettingSwitch(
            checked = enabled == true,
            enabled = unavailableReason == null && enabled != null,
            onChange = desktop::updateAutoStart
        )
    }
}

// 录制期间暂停全局热键，避免按下的组合被已注册的热键拦截
@Composable
private fun HotkeyEditor(
    hotkeyMap: Map<HotkeyAction, HotkeyCombo?>,
    failed: Set<HotkeyAction>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: (Map<HotkeyAction, HotkeyCombo?>) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    var recording by remember { mutableStateOf<HotkeyAction?>(null) }

    // 离开页面时若仍在录制，恢复热键
    DisposableEffect(Unit) {
        onDispose { if (recording != null) onResume() }
    }

    val stopRecording = {
        if (recording != null) {
            recording = null
            onResume()
        }
    }

    HotkeyAction.entries.forEach { action ->
        val combo = hotkeyMap[action]
        val isFailed = combo != null && action in failed
        SettingRow(
            title = action.label,
            subtitle = if (isFailed) "已被其他程序占用，未生效" else null,
            subtitleColor = DesktopColors.Accent
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HotkeyRecorder(
                    combo = combo,
                    isRecording = recording == action,
                    onStart = {
                        if (recording == null) onPause()
                        recording = action
                    },
                    onCancel = stopRecording,
                    onRecorded = { newCombo ->
                        val updated = hotkeyMap.toMutableMap()
                        // 与其他动作重复时，从原动作上移除
                        val conflict = updated.entries.firstOrNull { it.key != action && it.value == newCombo }?.key
                        if (conflict != null) {
                            updated[conflict] = null
                            navigator.showMessage("${newCombo.label} 已从「${conflict.label}」移除")
                        }
                        updated[action] = newCombo
                        onSave(updated)
                        stopRecording()
                    },
                    onInvalid = navigator.showMessage
                )
                IconButton(
                    onClick = { onSave(hotkeyMap + (action to null)) },
                    enabled = combo != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "清除",
                        tint = if (combo != null) DesktopColors.TextGray else DesktopColors.SurfaceLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onSave(HotkeyCombo.defaults) }) {
            Text("恢复默认", color = DesktopColors.TextPrimary)
        }
    }
}

@Composable
private fun HotkeyRecorder(
    combo: HotkeyCombo?,
    isRecording: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRecorded: (HotkeyCombo) -> Unit,
    onInvalid: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    HoldTextInputFocus(isRecording)
    LaunchedEffect(isRecording) {
        if (isRecording) focusRequester.requestFocus()
    }
    Box(
        Modifier.widthIn(min = 160.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DesktopColors.Pane)
            .border(1.dp, if (isRecording) DesktopColors.Accent else DesktopColors.SurfaceLight, RoundedCornerShape(4.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { if (!it.isFocused && isRecording) onCancel() }
            .onPreviewKeyEvent { event ->
                if (!isRecording || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val awtCode = (event.nativeKeyEvent as? AwtKeyEvent)?.keyCode ?: return@onPreviewKeyEvent true
                when (awtCode) {
                    AwtKeyEvent.VK_ESCAPE -> onCancel()
                    AwtKeyEvent.VK_CONTROL, AwtKeyEvent.VK_ALT, AwtKeyEvent.VK_SHIFT, AwtKeyEvent.VK_WINDOWS,
                    AwtKeyEvent.VK_META, AwtKeyEvent.VK_ALT_GRAPH -> Unit
                    else -> {
                        val vk = HotkeyCombo.keyCodeFromAwt(awtCode)
                        val modifiers = HotkeyCombo.modifiersOf(event.isCtrlPressed, event.isAltPressed, event.isShiftPressed)
                        when {
                            !HotkeyCombo.isSupportedKey(vk) -> onInvalid("不支持该按键")
                            !HotkeyCombo.isValid(modifiers, vk) -> onInvalid("快捷键需要包含 Ctrl 或 Alt")
                            else -> onRecorded(HotkeyCombo(modifiers, vk))
                        }
                    }
                }
                true
            }
            .focusable()
            .clickable(onClick = onStart)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            when {
                isRecording -> "请按下组合键…"
                combo != null -> combo.label
                else -> "未设置"
            },
            color = when {
                isRecording -> DesktopColors.Accent
                combo != null -> DesktopColors.TextPrimary
                else -> DesktopColors.TextGray
            },
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DesktopServerUrlInput(
    currentUrl: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var text by remember(currentUrl, showDialog) { mutableStateOf(currentUrl) }

    OutlinedButton(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
    ) {
        Text("配置接口", fontSize = 12.sp)
    }

    if (showDialog) {
        DesktopDialog(
            title = "配置换源服务接口",
            onDismiss = { showDialog = false },
            width = 440.dp,
            actions = {
                DialogButton("清空", {
                    text = ""
                    onReset()
                    showDialog = false
                }, danger = true)
                DialogButton("取消", { showDialog = false })
                DialogButton("保存", {
                    onSave(text.trim())
                    showDialog = false
                }, primary = true)
            }
        ) {
            Text(
                text = "填写 UNM Utils 服务的基础访问 URL（末尾无需斜杠）：",
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            DialogTextField(value = text, onValueChange = { text = it }, placeholder = "例如 https://your-unm-server.com")
        }
    }
}
