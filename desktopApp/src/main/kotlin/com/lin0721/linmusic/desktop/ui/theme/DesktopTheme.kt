package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// 背景分三档：窗口与边框 WindowBackground、大背景 Pane、卡片 CardSurface；
// PopupSurface 是菜单、对话框与悬停的底色，Surface 是药丸、搜索框、图标按钮、选项格子等控件的底色
object DesktopColors {
    val WindowBackground = Color(0xFF000000)
    val Pane = Color(0xFF121212)
    val Surface = Color(0xFF2A2A2A)
    val CardSurface = Color(0xFF1F1F1F)
    val PopupSurface = Color(0xFF282828)
    // 行与按钮的悬停底，与菜单同色
    val PaneHover = PopupSurface
    val SurfaceLight = Color(0xFF3E3E3E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextGray = Color(0xFFB3B3B3)
    val Accent = Color(0xFFC20C0C)
    val CoverPlaceholder = Color(0xFF2C2C2C)
}

object DesktopDimens {
    val TitleBarHeight = 64.dp
    val PlayerBarHeight = 80.dp
    val PaneGap = 8.dp
    val PaneRadius = 8.dp
    val SidebarWidth = 300.dp
    val LibraryMinWidth = 280.dp
    val LibraryMaxWidth = 420.dp
    val LibraryRailWidth = 72.dp
    val NowPlayingWidth = 340.dp
    val NowPlayingMinWidth = 280.dp
    val NowPlayingMaxWidth = 420.dp
    val NowPlayingHandleWidth = 28.dp
    val NowPlayingPeekWidth = 72.dp
    val CenterMinWidth = 400.dp
    val PaneSnapWidth = 220.dp
}

@Composable
fun MelodiaDesktopTheme(uiScale: Float = 1f, content: @Composable () -> Unit) {
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(baseDensity.density * uiScale, baseDensity.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = DesktopColors.Accent,
                background = DesktopColors.WindowBackground,
                surface = DesktopColors.Pane,
                surfaceVariant = DesktopColors.Surface,
                onPrimary = DesktopColors.TextPrimary,
                onBackground = DesktopColors.TextPrimary,
                onSurface = DesktopColors.TextPrimary,
                onSurfaceVariant = DesktopColors.TextGray
            ),
            content = content
        )
    }
}
