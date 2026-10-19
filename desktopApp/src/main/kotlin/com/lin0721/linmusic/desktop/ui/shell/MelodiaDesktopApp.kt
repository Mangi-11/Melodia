package com.lin0721.linmusic.desktop.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.desktop.player.AudioOutputControl
import com.lin0721.linmusic.desktop.ui.login.LoginDialog
import com.lin0721.linmusic.desktop.ui.lyricsview.CHROME_ANIM_MS
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.DesktopFrameHost
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.search.SearchDropdown
import com.lin0721.linmusic.desktop.ui.search.SearchDropdownState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.update.UpdateDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private val ChromeEnter = fadeIn(tween(CHROME_ANIM_MS)) + expandVertically(tween(CHROME_ANIM_MS))
private val ChromeExit = fadeOut(tween(CHROME_ANIM_MS)) + shrinkVertically(tween(CHROME_ANIM_MS))

// AWT 把鼠标侧键报告为第 4、5 键
private const val MOUSE_BUTTON_BACK = 4
private const val MOUSE_BUTTON_FORWARD = 5

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.MelodiaDesktopApp(
    deps: DesktopAppDependencies,
    windowState: WindowState,
    fullscreen: FullscreenState,
    lyricsView: LyricsViewState,
    searchFocusRequests: Flow<Unit>,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val controller = deps.playbackController
    // 占位播放器没有音量与输出设备能力，界面隐藏对应入口
    val audioOutput: AudioOutputControl? = controller.takeIf { it.unavailableReason == null }
    val pane = rememberPaneLayoutState(deps.desktopPreferences, scope)
    val state = remember(lyricsView, pane) {
        DesktopAppState(lyricsView, pane, deps.libraryViewModel.asViewControl()) { open ->
            scope.launch { deps.desktopPreferences.saveNowPlayingPanelOpen(open) }
        }
    }

    val userProfile by deps.homeViewModel.userProfile.collectAsState()
    val downloadLevel by deps.settingsPreferences.wifiQuality.collectAsState(initial = "standard")
    val defaultPlaylistPrivate by deps.settingsPreferences.defaultPlaylistPrivate.collectAsState(initial = false)
    val nowPlaying by controller.nowPlaying.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val searchDropdown = remember { SearchDropdownState() }

    // 没有曲目时歌词界面无内容可看，自动收起
    LaunchedEffect(nowPlaying == null) { if (nowPlaying == null) lyricsView.close() }
    // 不经记录入口的界面变化（Esc 收起歌词、拖拽改排序等）并入当前步骤，后退时不会被还原回去
    LaunchedEffect(state) {
        combine(
            snapshotFlow { state.liveUi() },
            deps.libraryViewModel.selectedFilter,
            deps.libraryViewModel.sortOrder
        ) { _, _, _ -> }.collect { state.absorbUiChange() }
    }
    LaunchedEffect(state) { deps.desktopPreferences.nowPlayingPanelOpen.collect(state::syncSavedDockOpen) }
    // 登录失效时本地登录态已被清掉，这里提示并直接打开登录框
    LaunchedEffect(state) {
        deps.sessionMonitor.watch {
            state.showMessage("登录已过期，请重新登录")
            state.showLogin = true
        }
    }
    // 播放组件加载失败时启动就提示一次，之后每次尝试播放再提示
    LaunchedEffect(Unit) { controller.unavailableReason?.let { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(state) { appMessages(deps, state).collect { snackbarHostState.showSnackbar(it) } }

    val navigator = desktopNavigator(state, deps, userProfile != null, downloadLevel, defaultPlaylistPrivate)
    val saveableStateHolder = rememberSaveableStateHolder()
    val frameHost = remember(saveableStateHolder) {
        DesktopFrameHost(
            saveableStateHolder = saveableStateHolder,
            liveEntryIds = { state.backStack.liveEntryIds },
            scope = scope,
            onToast = state::showMessage
        )
    }
    val liveEntryIds = state.backStack.liveEntryIds
    LaunchedEffect(liveEntryIds) { frameHost.reconcile() }

    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val isFullscreen = fullscreen.isFullscreen

    CompositionLocalProvider(LocalDesktopNavigator provides navigator) {
        Box(
            Modifier.fillMaxSize().background(DesktopColors.WindowBackground)
                .onPointerEvent(PointerEventType.Enter) { lyricsView.pointerInWindow = true }
                .onPointerEvent(PointerEventType.Exit) { lyricsView.pointerInWindow = false }
                // 点到搜索下拉之外就收起它；鼠标侧键与浏览器一致：后退键返回，前进键前进；不消费事件，子组件照常响应
                .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                    event.changes.firstOrNull()?.position?.let(searchDropdown::onWindowPress)
                    when (event.awtEventOrNull?.button) {
                        MOUSE_BUTTON_BACK -> state.navigateBack()
                        MOUSE_BUTTON_FORWARD -> state.navigateForward()
                    }
                }
                // Esc 先让输入框放弃焦点，窗口级快捷键随即恢复；不消费，输入框自己的 Esc 逻辑照常
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        searchDropdown.close()
                        focusManager.clearFocus()
                    }
                    false
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                // 全屏或歌词沉浸态时收起自绘标题栏，Esc 或底栏按钮退出
                AnimatedVisibility(
                    visible = !isFullscreen && !lyricsView.isImmersive,
                    enter = ChromeEnter,
                    exit = ChromeExit
                ) {
                    AppTitleBar(state, deps, windowState, userProfile, searchFocusRequests, searchDropdown, onClose)
                }
                Workspace(state, deps, frameHost, audioOutput, hasNowPlaying = nowPlaying != null, isFullscreen, Modifier.weight(1f))
                AnimatedVisibility(
                    visible = !lyricsView.isImmersive,
                    enter = ChromeEnter,
                    exit = ChromeExit
                ) {
                    AppPlayerBar(state, deps, audioOutput, fullscreen)
                }
            }
            SearchDropdown(searchDropdown, deps.searchViewModel) { keyword ->
                state.navigate(DesktopRoute.Search)
                deps.searchViewModel.searchWithKeyword(keyword)
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { data ->
                Snackbar(data, containerColor = DesktopColors.PopupSurface, contentColor = DesktopColors.TextPrimary)
            }
            WindowResizeHandles(enabled = !isMaximized && !isFullscreen)
        }
    }

    val availableUpdate by deps.updateChecker.availableUpdate.collectAsState()
    val isUpdateDialogVisible by deps.updateChecker.isDialogVisible.collectAsState()
    availableUpdate?.takeIf { isUpdateDialogVisible }?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = deps.updateChecker::dismiss,
            onHide = deps.updateChecker::hideDialog,
            onIgnore = deps.updateChecker::ignoreCurrentVersion,
            onMessage = state::showMessage
        )
    }

    if (state.showLogin) {
        LoginDialog(
            viewModel = deps.loginViewModel,
            browserLocator = deps.browserLocator,
            onLoginSuccess = { cookies ->
                deps.homeViewModel.handleLoginSuccess(cookies)
                state.showLogin = false
            },
            onDismiss = { state.showLogin = false }
        )
    }
}
