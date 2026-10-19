package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.ui.theme.uiEnter
import com.lin0721.linmusic.core.ui.theme.uiExit
import com.lin0721.linmusic.core.update.UpdateManager
import com.lin0721.linmusic.core.update.UpdateUiState
import com.lin0721.linmusic.core.ui.components.MiniStatusBanner
import org.koin.compose.koinInject

// 更新横幅，点击展开 UpdateDialog
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updateManager: UpdateManager = koinInject()
    val state by updateManager.uiState.collectAsStateWithLifecycle()
    val isBannerVisible by updateManager.isBannerVisible.collectAsStateWithLifecycle()
    // 保留最后一个非 Idle 状态供退出动画使用
    var lastActiveState by remember { mutableStateOf<UpdateUiState?>(null) }
    if (state !is UpdateUiState.Idle) lastActiveState = state

    AnimatedVisibility(
        visible = isBannerVisible && state !is UpdateUiState.Idle,
        enter = uiEnter(expandVertically() + fadeIn()),
        exit = uiExit(shrinkVertically() + fadeOut()),
        modifier = modifier
    ) {
        val onClick = { updateManager.showDialog() }
        val onClose = { updateManager.dismissBanner() }
        when (val s = lastActiveState) {
            is UpdateUiState.Available -> MiniStatusBanner(
                icon = Icons.Rounded.SystemUpdate,
                title = "发现新版本 ${s.info.versionName}${if (s.info.isPrerelease) "（测试版）" else ""}",
                subtitle = "点击查看更新内容",
                onClick = onClick,
                onClose = onClose
            )
            is UpdateUiState.Downloading -> MiniStatusBanner(
                icon = Icons.Rounded.SystemUpdate,
                title = "正在下载新版本 ${s.info.versionName}",
                progress = (s.progress / 100f).coerceIn(0f, 1f),
                trailingText = "${s.progress}%",
                onClick = onClick
            )
            is UpdateUiState.ReadyToInstall -> MiniStatusBanner(
                icon = Icons.Rounded.InstallMobile,
                title = "新版本 ${s.info.versionName} 已下载",
                subtitle = "点击安装",
                onClick = onClick,
                onClose = onClose
            )
            is UpdateUiState.DownloadFailed -> MiniStatusBanner(
                icon = Icons.Rounded.ErrorOutline,
                title = "新版本下载失败",
                subtitle = "点击查看并重试",
                onClick = onClick,
                onClose = onClose
            )
            else -> Unit
        }
    }
}
