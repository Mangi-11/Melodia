package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.content.res.Configuration
import kotlin.math.roundToInt
import com.lin0721.linmusic.core.ui.interaction.MelodiaPressIndication

private val DarkColorScheme = darkColorScheme(
    primary = NeteaseRed,
    onPrimary = Color.White,
    background = BackgroundDark,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextGray,
    error = NeteaseRed,
    onError = Color.White
)

// Melodia 应用的全局 Material3 主题配置
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelodiaTheme(
    uiScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val baseConfiguration = LocalConfiguration.current
    val scaledDensity = remember(baseDensity, uiScale) {
        Density(baseDensity.density * uiScale, baseDensity.fontScale)
    }
    // 断点判断读的是 Configuration 的 dp 尺寸，需同步换算为缩放后的有效尺寸
    val scaledConfiguration = remember(baseConfiguration, uiScale) {
        if (uiScale == 1f) {
            baseConfiguration
        } else {
            Configuration(baseConfiguration).apply {
                screenWidthDp = (baseConfiguration.screenWidthDp / uiScale).roundToInt()
                screenHeightDp = (baseConfiguration.screenHeightDp / uiScale).roundToInt()
                smallestScreenWidthDp = (baseConfiguration.smallestScreenWidthDp / uiScale).roundToInt()
                densityDpi = (baseConfiguration.densityDpi * uiScale).roundToInt()
            }
        }
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalConfiguration provides scaledConfiguration
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            shapes = MelodiaShapes
        ) {
            CompositionLocalProvider(
                LocalIndication provides MelodiaPressIndication.Default,
                LocalRippleConfiguration provides null,
                content = content
            )
        }
    }
}