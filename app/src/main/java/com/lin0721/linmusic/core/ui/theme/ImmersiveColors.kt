package com.lin0721.linmusic.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// 由 ImmersiveTheme 提供：外层是浅色主题，沉浸区铺底取中调而非压暗
val LocalImmersiveMidTone = staticCompositionLocalOf { false }

// 封面取色铺底的沉浸页（全屏播放器、歌词预览卡）配色：fill 铺底，blob 是流动光斑，inactive 是未唱到的歌词
@Immutable
data class ImmersiveColors(val fill: Color, val blob: Color, val inactive: Color)

// 两种主题都是有色底配白字：深色主色提饱和后压暗，浅色按相对亮度取中调，比深色亮但仍托得住白字
fun immersiveColors(base: Color, midTone: Boolean): ImmersiveColors {
    val vivid = base.saturateIfChromatic(0.6f)
    return ImmersiveColors(
        fill = if (midTone) immersiveMidFill(vivid) else vivid.darken(0.35f),
        blob = if (midTone) immersiveMidBlob(vivid) else vivid.darken(0.15f),
        inactive = lerp(base.saturateIfChromatic(0.8f).lighten(1.0f), Color.White, 0.5f)
    )
}

private val immersiveMidTone: Boolean
    @Composable @ReadOnlyComposable get() = LocalImmersiveMidTone.current || !MelodiaColors.IsDark

@Composable
fun rememberImmersiveColors(base: Color): ImmersiveColors {
    val midTone = immersiveMidTone
    return remember(base, midTone) { immersiveColors(base, midTone) }
}

// 播放页整页铺底：深色直接用封面主色（本身已偏深且明度有上限），浅色取中调
@Composable
@ReadOnlyComposable
fun immersiveCoverFill(base: Color): Color = if (immersiveMidTone) immersiveMidFill(base) else base

// 封面主色作页面头部底色：深色原样，浅色取同色相浅调，正文沿用主题文字色
@Composable
@ReadOnlyComposable
fun themedCoverBase(base: Color): Color = if (MelodiaColors.IsDark) base else coverHeaderTint(base)

// 同上，深色下按 darkenFraction 压暗
@Composable
@ReadOnlyComposable
fun themedCoverBase(base: Color, darkenFraction: Float): Color =
    if (MelodiaColors.IsDark) base.darken(darkenFraction) else coverHeaderTint(base)
