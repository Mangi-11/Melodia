package com.lin0721.linmusic.desktop.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.core.update.UpdateChecker
import com.lin0721.linmusic.core.update.data.UpdateRepository
import com.lin0721.linmusic.desktop.app.AppInfo
import com.lin0721.linmusic.desktop.app.RenderBackend
import com.lin0721.linmusic.desktop.data.CacheLimits
import com.lin0721.linmusic.desktop.data.CloseAction
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.chooseDirectory
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.components.uiTween
import com.lin0721.linmusic.desktop.ui.nowplaying.SupportedInfoCards
import com.lin0721.linmusic.desktop.ui.player.PlayerSlider
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.update.openInBrowser
import com.lin0721.linmusic.feature.cloud.domain.formatFileSize
import com.lin0721.linmusic.feature.settings.ui.CommonSettingsViewModel
import com.lin0721.linmusic.feature.settings.ui.LxPluginSettingsViewModel
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import kotlin.math.roundToInt

internal enum class SettingsCategory(val label: String) {
    Playback("播放"),
    Source("音源与换源"),
    Storage("存储与下载"),
    Interface("界面与歌词"),
    System("系统与窗口"),
    Hotkeys("快捷键"),
    About("关于")
}

// 一个设置项：分类、搜索用的文本与实际渲染内容；group 非空时在分区内按连续的同名组分段
internal class SettingsEntry(
    val key: String,
    val category: SettingsCategory,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
    val group: String? = null,
    val content: @Composable () -> Unit
) {
    val searchText: List<String> get() = listOfNotNull(title, subtitle, group) + keywords
}

private fun rowEntry(
    key: String,
    category: SettingsCategory,
    title: String,
    subtitle: String? = null,
    keywords: List<String> = emptyList(),
    group: String? = null,
    trailing: @Composable () -> Unit
) = SettingsEntry(key, category, title, subtitle, keywords, group) {
    SettingRow(title, subtitle, trailing = trailing)
}

private val CrossfadeDurationOptionsMs = listOf(1_000, 2_000, 3_000, 4_000, 6_000, 8_000, 10_000, 12_000)

private val QualityOptions = listOf(
    "standard" to "标准音质",
    "exhigh" to "极高音质",
    "lossless" to "无损音质 (FLAC)",
    "hires" to "Hi-Res 无损",
    "jymaster" to "超清母带"
)

private val ActionSpacing = Arrangement.spacedBy(8.dp)
private const val CARD_LAYOUT_EXPAND_MS = 200

@Composable
internal fun settingsEntries(
    common: CommonSettingsViewModel,
    desktop: DesktopSettingsViewModel,
    lxPlugins: LxPluginSettingsViewModel,
    updateChecker: UpdateChecker
): List<SettingsEntry> {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()

    val quality by common.wifiQuality.collectAsState()
    val searchAggregationEnabled by common.searchAggregationEnabled.collectAsState()
    val fallbackEnabled by common.fallbackEnabled.collectAsState()
    val communityPriority by common.communityPriority.collectAsState()
    val unmServerUrl by common.unmServerUrl.collectAsState()
    val unmRemoteFallbackEnabled by common.unmRemoteFallbackEnabled.collectAsState()
    val unmAutoMatch by common.unmAutoMatch.collectAsState()
    val showDesktopLyric by common.showDesktopLrc.collectAsState()
    val logLevelName by common.logLevel.collectAsState()
    val logBytes by desktop.logBytes.collectAsState()
    val cacheMaxSize by common.audioCacheMaxSize.collectAsState()
    val cacheUsage by desktop.cacheUsage.collectAsState()
    val cacheDir by desktop.cacheDir.collectAsState()
    val relocation by desktop.relocation.collectAsState()
    var confirmClearCache by remember { mutableStateOf(false) }
    val crossfadeEnabled by common.crossfadeEnabled.collectAsState()
    val crossfadeDurationMs by common.crossfadeDurationMs.collectAsState()
    val uiScalePercent by common.uiScalePercent.collectAsState()
    val uiAnimationsEnabled by common.uiAnimationsEnabled.collectAsState()
    val downloadFolder by common.downloadFolderUri.collectAsState()
    val downloadLyrics by common.downloadLyricsEnabled.collectAsState()
    val defaultPlaylistPrivate by common.defaultPlaylistPrivate.collectAsState()
    val cardLayout by common.fullPlayerCardLayout.collectAsState()
    val closeAction by desktop.closeAction.collectAsState()
    val renderBackend by desktop.renderBackend.collectAsState()
    val mediaKeysEnabled by desktop.mediaKeysEnabled.collectAsState()
    val hotkeyMap by desktop.hotkeyMap.collectAsState()
    val failedHotkeys by desktop.failedHotkeys.collectAsState()
    val mediaSessionAvailable by desktop.mediaSessionAvailable.collectAsState()
    val autoCheckUpdate by common.autoCheckUpdateEnabled.collectAsState()
    val allowPrerelease by common.allowPrereleaseChannel.collectAsState()
    val availableUpdate by updateChecker.availableUpdate.collectAsState()

    val customDownloadFolder = downloadFolder?.takeIf { it.isNotBlank() }
    val cacheDirSubtitle = relocation?.let { "正在迁移缓存：${formatFileSize(it.copiedBytes)} / ${formatFileSize(it.totalBytes)}" }
        ?: (cacheDir.root.absolutePath + if (cacheDir.pendingRestart) "（重启后生效）" else "")
    val cacheUsageSubtitle = "${formatFileSize(cacheUsage.total)}（歌曲 ${formatFileSize(cacheUsage.audio)} · 图片 ${formatFileSize(cacheUsage.image)} · " +
        "歌词 ${formatFileSize(cacheUsage.lyrics)} · 离线数据 ${formatFileSize(cacheUsage.metadata)}）"
    val unmServerSubtitle = unmServerUrl.ifBlank { "未配置" }
    val cardLayoutSubtitle = "拖动调整信息卡片的顺序，关闭开关可隐藏对应卡片"

    return buildList {
        add(
            rowEntry(
                "quality", SettingsCategory.Playback, "在线播放音质",
                keywords = listOf("音质", "码率", "无损", "flac", "hi-res", "母带")
            ) {
                SettingsDropdown(
                    options = QualityOptions.map { it.first },
                    current = quality,
                    label = { key -> QualityOptions.firstOrNull { it.first == key }?.second ?: key },
                    onSelect = common::updateWifiQuality
                )
            }
        )
        add(
            rowEntry(
                "crossfade", SettingsCategory.Playback, "歌曲淡入淡出",
                subtitle = "自动切歌时前后两首交叉淡化，手动切歌不受影响",
                keywords = listOf("交叉淡化", "切歌", "过渡")
            ) {
                SettingSwitch(crossfadeEnabled, onChange = common::updateCrossfadeEnabled)
            }
        )
        if (crossfadeEnabled) {
            add(
                rowEntry("crossfadeDuration", SettingsCategory.Playback, "淡化时长", keywords = listOf("淡入淡出", "秒")) {
                    SettingsDropdown(
                        options = CrossfadeDurationOptionsMs,
                        current = CrossfadePolicy.normalizeDurationMs(crossfadeDurationMs),
                        label = ::formatSeconds,
                        onSelect = common::updateCrossfadeDurationMs
                    )
                }
            )
        }

        add(
            rowEntry(
                "searchAggregation", SettingsCategory.Source, "多平台聚合搜索",
                subtitle = "在搜索结果页提供酷狗、酷我、QQ 音乐的平台标签，切换后才会发起搜索；播放通过换源插件解析",
                keywords = listOf("搜索", "酷狗", "酷我", "qq", "平台")
            ) {
                SettingSwitch(searchAggregationEnabled, onChange = common::updateSearchAggregationEnabled)
            }
        )
        add(
            rowEntry(
                "fallback", SettingsCategory.Source, "无版权/VIP 自动换源",
                subtitle = "官方网易云音源不可用或仅为试听时，自动从可用音源解析完整播放直链",
                keywords = listOf("换源", "版权", "vip", "试听", "音源")
            ) {
                SettingSwitch(fallbackEnabled, onChange = common::updateFallbackEnabled)
            }
        )
        if (fallbackEnabled) {
            add(
                rowEntry(
                    "communityPriority", SettingsCategory.Source, "社区源优先",
                    subtitle = "开启后优先尝试通过 LX 插件脚本解析，失败后 fallback 到远程 UNM 服务；关闭则先尝试远程 UNM",
                    keywords = listOf("lx", "unm", "插件", "换源")
                ) {
                    SettingSwitch(communityPriority, onChange = common::updateCommunityPriority)
                }
            )
        }
        add(
            SettingsEntry(
                key = "lxPlugins",
                category = SettingsCategory.Source,
                title = "社区源（LX）插件",
                subtitle = "启用开关、插件列表与脚本导入",
                keywords = listOf("lx", "洛雪", "插件", "脚本", "导入", "社区源", "js")
            ) {
                Column(Modifier.fillMaxWidth()) {
                    LxPluginSection(lxPlugins)
                }
            }
        )
        add(
            rowEntry(
                "unmRemote", SettingsCategory.Source, "启用远程 UNM 服务",
                subtitle = "向远程自建或第三方的 UNM（UnblockNeteaseMusic）服务器请求音频直链",
                keywords = listOf("unm", "远程", "换源", "服务器")
            ) {
                SettingSwitch(unmRemoteFallbackEnabled, onChange = common::updateUnmRemoteFallbackEnabled)
            }
        )
        if (unmRemoteFallbackEnabled) {
            add(
                rowEntry(
                    "unmServer", SettingsCategory.Source, "远程服务接口 (Base URL)",
                    subtitle = unmServerSubtitle,
                    keywords = listOf("unm", "地址", "url", "接口")
                ) {
                    DesktopServerUrlInput(
                        currentUrl = unmServerUrl,
                        onSave = common::updateUnmServerUrl,
                        onReset = common::resetUnmServerUrl
                    )
                }
            )
            add(
                rowEntry(
                    "unmAutoMatch", SettingsCategory.Source, "服务端自动选择模式",
                    subtitle = "由服务端自动轮询最优源匹配",
                    keywords = listOf("unm", "自动", "匹配")
                ) {
                    SettingSwitch(unmAutoMatch, onChange = common::updateUnmAutoMatch)
                }
            )
        }

        add(
            rowEntry(
                "cacheLimit", SettingsCategory.Storage, "缓存容量上限",
                subtitle = "歌曲、图片、歌词与离线数据合计，超出后淘汰最久未使用的缓存",
                keywords = listOf("缓存", "空间", "容量", "淘汰"),
                group = "缓存"
            ) {
                SettingsDropdown(
                    options = CacheLimits.options,
                    current = CacheLimits.normalize(cacheMaxSize),
                    label = ::cacheLimitLabel,
                    onSelect = { size ->
                        common.updateAudioCacheMaxSize(size)
                        desktop.enforceCacheLimit(size)
                    }
                )
            }
        )
        add(
            SettingsEntry(
                key = "cacheDir",
                category = SettingsCategory.Storage,
                title = "缓存目录",
                subtitle = cacheDirSubtitle,
                keywords = listOf("缓存", "位置", "路径", "迁移", "文件夹"),
                group = "缓存"
            ) {
                Column {
                    SettingRow("缓存目录", cacheDirSubtitle) {
                        Row(horizontalArrangement = ActionSpacing, verticalAlignment = Alignment.CenterVertically) {
                            if (relocation != null) {
                                SettingsActionButton("取消迁移", onClick = desktop::cancelRelocation)
                            } else {
                                if (cacheDir.isCustom) {
                                    SettingsActionButton("恢复默认", onClick = { desktop.changeCacheDir(null) })
                                }
                                SettingsActionButton("更改", onClick = {
                                    scope.launch {
                                        val initial = cacheDir.root.parentFile ?: cacheDir.root
                                        chooseDirectory(initial, "选择缓存目录")?.let(desktop::changeCacheDir)
                                    }
                                })
                            }
                        }
                    }
                    relocation?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = DesktopColors.Accent,
                            trackColor = DesktopColors.SurfaceLight
                        )
                    }
                }
            }
        )
        add(
            SettingsEntry(
                key = "cacheUsage",
                category = SettingsCategory.Storage,
                title = "已用空间",
                subtitle = cacheUsageSubtitle,
                keywords = listOf("缓存", "清除", "清理", "歌曲", "图片", "歌词", "离线数据"),
                group = "缓存"
            ) {
                Column {
                    SettingRow("已用空间", cacheUsageSubtitle) {
                        SettingsActionButton("清除缓存", onClick = { confirmClearCache = true }, danger = true)
                    }
                    if (confirmClearCache) {
                        ClearCacheDialog(
                            onConfirm = {
                                confirmClearCache = false
                                desktop.clearCaches()
                            },
                            onDismiss = { confirmClearCache = false }
                        )
                    }
                }
            }
        )
        add(
            rowEntry(
                "downloadDir", SettingsCategory.Storage, "下载目录",
                subtitle = customDownloadFolder ?: desktop.defaultDownloadDir.absolutePath,
                keywords = listOf("下载", "位置", "路径", "文件夹"),
                group = "下载"
            ) {
                Row(horizontalArrangement = ActionSpacing, verticalAlignment = Alignment.CenterVertically) {
                    if (customDownloadFolder != null) {
                        SettingsActionButton("恢复默认", onClick = { common.updateDownloadFolderUri(null) })
                    }
                    SettingsActionButton("更改", onClick = {
                        scope.launch {
                            val initial = File(customDownloadFolder ?: desktop.defaultDownloadDir.absolutePath)
                            chooseDirectory(initial, "选择下载目录")?.let { common.updateDownloadFolderUri(it.absolutePath) }
                        }
                    })
                }
            }
        )
        add(
            rowEntry(
                "downloadLyrics", SettingsCategory.Storage, "内嵌歌词",
                subtitle = "下载时把歌词写入音频文件的标签",
                keywords = listOf("下载", "歌词", "标签", "tag"),
                group = "下载"
            ) {
                SettingSwitch(downloadLyrics, onChange = common::updateDownloadLyricsEnabled)
            }
        )
        add(
            rowEntry(
                "playlistPrivate", SettingsCategory.Storage, "新建歌单默认设为隐私",
                subtitle = "创建歌单对话框中的隐私开关以此为初始值",
                keywords = listOf("歌单", "隐私", "新建"),
                group = "歌单"
            ) {
                SettingSwitch(defaultPlaylistPrivate, onChange = common::updateDefaultPlaylistPrivate)
            }
        )

        add(
            rowEntry(
                "desktopLyric", SettingsCategory.Interface, "显示桌面歌词",
                keywords = listOf("歌词", "桌面", "悬浮")
            ) {
                SettingSwitch(showDesktopLyric, onChange = common::updateShowDesktopLrc)
            }
        )
        add(
            SettingsEntry(
                key = "cardLayout",
                category = SettingsCategory.Interface,
                title = "正在播放面板信息卡片",
                subtitle = cardLayoutSubtitle,
                keywords = SupportedInfoCards.map { it.title } + listOf("面板", "排序", "显示", "隐藏", "卡片")
            ) {
                CardLayoutSetting(cardLayout, cardLayoutSubtitle, common::updateFullPlayerCardLayout)
            }
        )
        add(
            SettingsEntry(
                key = "uiScale",
                category = SettingsCategory.Interface,
                title = "界面缩放",
                subtitle = "整体放大界面，松手后生效",
                keywords = listOf("缩放", "放大", "大小", "dpi", "字体")
            ) {
                UiScaleSetting(uiScalePercent, common::updateUiScalePercent)
            }
        )
        add(
            rowEntry(
                "uiAnimations", SettingsCategory.Interface, "界面过渡动画",
                subtitle = "关闭后切页、面板展开收起等直接切换，不影响歌词滚动",
                keywords = listOf("动画", "过渡", "减少动态", "流畅")
            ) {
                SettingSwitch(uiAnimationsEnabled, onChange = common::updateUiAnimationsEnabled)
            }
        )
        add(
            rowEntry(
                "renderBackend", SettingsCategory.Interface, "渲染方式",
                subtitle = "出现黑屏、闪烁或花屏时可尝试切换，重启后生效",
                keywords = listOf("渲染", "gpu", "显卡", "硬件加速", "黑屏", "闪烁", "directx", "opengl")
            ) {
                SettingsDropdown(
                    options = RenderBackend.entries,
                    current = renderBackend,
                    label = ::renderBackendLabel,
                    onSelect = desktop::updateRenderBackend
                )
            }
        )

        add(
            rowEntry(
                "closeAction", SettingsCategory.System, "关闭主窗口时",
                keywords = listOf("关闭", "托盘", "退出", "最小化")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloseOption("最小化到托盘", closeAction == CloseAction.TRAY) {
                        desktop.updateCloseAction(CloseAction.TRAY)
                    }
                    CloseOption("直接退出", closeAction == CloseAction.EXIT) {
                        desktop.updateCloseAction(CloseAction.EXIT)
                    }
                }
            }
        )
        add(
            SettingsEntry(
                key = "autoStart",
                category = SettingsCategory.System,
                title = "开机自动启动",
                subtitle = desktop.autoStartUnavailableReason,
                keywords = listOf("开机", "自启", "启动", "登录")
            ) {
                AutoStartRow(desktop)
            }
        )
        add(
            rowEntry(
                "logLevel", SettingsCategory.System, "日志级别",
                subtitle = "越详细越利于排查问题，立即生效",
                keywords = listOf("日志", "调试", "debug")
            ) {
                SettingsDropdown(
                    options = AppLogger.LogLevel.entries,
                    current = runCatching { AppLogger.LogLevel.valueOf(logLevelName) }.getOrDefault(AppLogger.LogLevel.WARN),
                    label = ::logLevelLabel,
                    onSelect = common::updateLogLevel
                )
            }
        )
        add(
            rowEntry(
                "logFile", SettingsCategory.System, "日志文件",
                subtitle = "${formatFileSize(logBytes)} · ${desktop.logDir.absolutePath}",
                keywords = listOf("日志", "目录", "清除", "清理")
            ) {
                Row(horizontalArrangement = ActionSpacing, verticalAlignment = Alignment.CenterVertically) {
                    SettingsActionButton("打开目录", onClick = {
                        if (!openDirectory(desktop.logDir)) navigator.showMessage("无法打开日志目录")
                    })
                    SettingsActionButton("清除", onClick = desktop::clearLogs, danger = true)
                }
            }
        )

        if (desktop.hotkeysSupported) {
            add(
                rowEntry(
                    "mediaKeys", SettingsCategory.Hotkeys, "系统媒体控制（媒体键与系统播放卡片）",
                    subtitle = if (mediaSessionAvailable) null else "系统卡片不可用，已改用全局媒体键",
                    keywords = listOf("媒体键", "系统播放", "smtc", "快捷键")
                ) {
                    SettingSwitch(mediaKeysEnabled, onChange = desktop::updateMediaKeysEnabled)
                }
            )
            add(
                SettingsEntry(
                    key = "hotkeys",
                    category = SettingsCategory.Hotkeys,
                    title = "全局热键",
                    subtitle = "点击组合键后按下新的快捷键即可修改",
                    keywords = HotkeyActionLabels + hotkeyMap.values.mapNotNull { it?.label } + listOf("快捷键", "热键", "录制")
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        HotkeyEditor(
                            hotkeyMap = hotkeyMap,
                            failed = failedHotkeys,
                            onPause = desktop::pauseHotkeys,
                            onResume = desktop::resumeHotkeys,
                            onSave = desktop::updateHotkeys
                        )
                    }
                }
            )
        }

        add(
            SettingsEntry(
                key = "appVersion",
                category = SettingsCategory.About,
                title = "当前版本",
                keywords = listOf("版本", "更新", "升级", "version")
            ) {
                val update = availableUpdate
                SettingRow(
                    title = "当前版本",
                    subtitle = update?.let { "发现新版本 ${it.versionName}${if (it.isPrerelease) "（测试版）" else ""}" }
                        ?: appVersionLabel(),
                    subtitleColor = if (update != null) DesktopColors.Accent else DesktopColors.TextGray
                ) {
                    Row(horizontalArrangement = ActionSpacing, verticalAlignment = Alignment.CenterVertically) {
                        if (update != null) SettingsActionButton("查看新版本", onClick = updateChecker::showDialog)
                        SettingsActionButton("检查更新", onClick = { updateChecker.checkForUpdate(manual = true) })
                    }
                }
            }
        )
        add(
            rowEntry(
                "autoCheckUpdate", SettingsCategory.About, "自动检查更新",
                subtitle = "启动应用时后台检查一次更新",
                keywords = listOf("更新", "升级", "自动")
            ) {
                SettingSwitch(autoCheckUpdate, onChange = common::updateAutoCheckUpdateEnabled)
            }
        )
        add(
            rowEntry(
                "allowPrerelease", SettingsCategory.About, "接收测试版更新",
                subtitle = "包含 beta/rc 预览版本，可能不稳定",
                keywords = listOf("更新", "测试版", "预发布", "beta", "rc")
            ) {
                SettingSwitch(allowPrerelease, onChange = common::updateAllowPrereleaseChannel)
            }
        )
        add(
            rowEntry(
                "projectHome", SettingsCategory.About, "项目主页",
                subtitle = UpdateRepository.REPOSITORY_URL,
                keywords = listOf("github", "主页", "反馈", "源码")
            ) {
                SettingsActionButton("打开", onClick = {
                    if (!openInBrowser(UpdateRepository.REPOSITORY_URL)) navigator.showMessage("无法打开浏览器")
                })
            }
        )
    }
}

private fun appVersionLabel(): String = when {
    !AppInfo.isRelease -> "开发版本 ${AppInfo.version}"
    AppInfo.isPrerelease -> "Version ${AppInfo.version}（测试版）"
    else -> "Version ${AppInfo.version}"
}

private val HotkeyActionLabels = HotkeyAction.entries.map { it.label }

// 松手后才应用，避免拖动中界面缩放导致滑块在指针下移位
@Composable
private fun UiScaleSetting(percent: Int, onCommit: (Int) -> Unit) {
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(percent) { pending = null }
    val shown = pending ?: percent
    val span = UiScale.MAX_PERCENT - UiScale.MIN_PERCENT
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("界面缩放", color = DesktopColors.TextPrimary, fontSize = 14.sp)
                Text("整体放大界面，松手后生效", color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            Text("$shown%", color = DesktopColors.TextGray, fontSize = 12.sp)
        }
        PlayerSlider(
            value = (shown - UiScale.MIN_PERCENT).toFloat() / span,
            onValueChange = { fraction ->
                pending = UiScale.normalizePercent((UiScale.MIN_PERCENT + fraction * span).roundToInt())
            },
            onValueChangeFinished = { pending?.let(onCommit) },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// 卡片列表较长，默认收起只留标题行
@Composable
private fun CardLayoutSetting(
    layout: List<FullPlayerCardSetting>,
    expandedSubtitle: String,
    onChange: (List<FullPlayerCardSetting>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val supported = layout.filter { it.card in SupportedInfoCards }
    val subtitle = if (expanded) {
        expandedSubtitle
    } else {
        "已显示 ${supported.count { it.visible }}/${supported.size} 张，展开可调整顺序与显隐"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("正在播放面板信息卡片", color = DesktopColors.TextPrimary, fontSize = 14.sp)
                Text(subtitle, color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = DesktopColors.TextGray,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(uiTween(CARD_LAYOUT_EXPAND_MS)) + expandVertically(uiTween(CARD_LAYOUT_EXPAND_MS)),
            exit = fadeOut(uiTween(CARD_LAYOUT_EXPAND_MS)) + shrinkVertically(uiTween(CARD_LAYOUT_EXPAND_MS))
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                CardLayoutEditor(layout, onChange)
            }
        }
    }
}

private fun renderBackendLabel(backend: RenderBackend): String = when (backend) {
    RenderBackend.AUTO -> "自动"
    RenderBackend.DIRECT3D -> "DirectX"
    RenderBackend.OPENGL -> "OpenGL"
    RenderBackend.SOFTWARE -> "软件渲染"
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
private fun ClearCacheDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DesktopDialog(
        title = "清除缓存",
        onDismiss = onDismiss,
        width = 340.dp,
        actions = {
            DialogButton("取消", onDismiss)
            DialogButton("清除", onConfirm, danger = true)
        }
    ) {
        Text(
            "将清除已缓存的歌曲、图片和歌词，离线数据会保留。已缓存的歌曲在离线时将无法播放。",
            color = DesktopColors.TextGray,
            fontSize = 13.sp
        )
    }
}

private fun cacheLimitLabel(bytes: Long): String =
    if (bytes == CacheLimits.UNLIMITED) "无限制" else formatFileSize(bytes)

private fun formatSeconds(ms: Int): String = if (ms % 1000 == 0) "${ms / 1000} 秒" else "${ms / 1000.0} 秒"

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

@Composable
private fun DesktopServerUrlInput(
    currentUrl: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var text by remember(currentUrl, showDialog) { mutableStateOf(currentUrl) }

    SettingsActionButton("配置接口", onClick = { showDialog = true })

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
