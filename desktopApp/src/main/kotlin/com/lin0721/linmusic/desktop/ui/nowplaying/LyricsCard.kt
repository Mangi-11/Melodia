package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.desktop.ui.components.DesktopTooltip
import com.lin0721.linmusic.desktop.ui.components.HoverReveal
import com.lin0721.linmusic.desktop.ui.icons.LyricCardIcons
import com.lin0721.linmusic.desktop.ui.palette.darken
import com.lin0721.linmusic.desktop.ui.palette.lighten
import com.lin0721.linmusic.desktop.ui.palette.saturateIfChromatic

private val MeshBlurRadius = 32.dp
private val FadeHeight = 28.dp
private val HeaderButtonSize = 28.dp
private val HeaderIconSize = 22.dp
private const val VIEWPORT_ANIM_MS = 300

// 歌词卡：背景随封面主色着色并缓慢游走（移植自移动端）。
// 折叠只看当前行与下一行，展开后显示六行；歌词随播放自动滚动，不响应滚轮，点击歌词行跳转到该处
@Composable
fun LyricsCard(
    lines: List<LyricLine>,
    currentIndex: Int,
    base: Color,
    onSeek: (Long) -> Unit,
    onOpenFullscreen: () -> Unit,
    onOpenLyricsView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "lyricMesh")
    val darkCenterX by transition.animateFloat(
        initialValue = 1.20f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshX"
    )
    val darkCenterY by transition.animateFloat(
        initialValue = 1.20f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshY"
    )
    val darkRadiusScale by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshRadius"
    )
    val vividBase = remember(base) { base.saturateIfChromatic(0.6f) }
    val fillColor = remember(vividBase) { vividBase.darken(0.35f) }
    val darkBlob = remember(vividBase) { vividBase.darken(0.15f) }
    // 与全屏歌词同配方：文字单独再提一档饱和度与明度，不然混完白会发灰
    val inactiveColor = remember(base) { lerp(base.saturateIfChromatic(0.8f).lighten(1.0f), Color.White, 0.5f) }

    var expanded by remember { mutableStateOf(false) }
    // 当前行实际折行数，由当前行文字回报，用来撑高视口
    var currentWrapLines by remember { mutableIntStateOf(1) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()

    val targetHeight = lyricViewportHeight(
        expanded = expanded,
        currentHasTranslation = lines.getOrNull(currentIndex)?.translation != null,
        extraWrapLines = currentWrapLines - 1
    )
    val viewportHeight by animateDpAsState(targetHeight, tween(VIEWPORT_ANIM_MS, easing = FastOutSlowInEasing), label = "lyricViewport")

    InfoCard(
        title = if (expanded) "歌词" else "歌词预览",
        modifier = modifier.hoverable(hoverSource),
        backdrop = {
            Box(
                Modifier.matchParentSize().blur(MeshBlurRadius).drawBehind {
                    drawSingleHueMesh(
                        fill = fillColor,
                        darkBlob = darkBlob,
                        darkCenter = Offset(size.width * darkCenterX, size.height * darkCenterY),
                        darkRadius = size.minDimension * darkRadiusScale
                    )
                }
            )
        },
        headerTrailing = if (expanded) {
            {
                HeaderIconButton(LyricCardIcons.Fullscreen, "全屏", hovered, onOpenFullscreen)
                HeaderIconButton(LyricCardIcons.ExpandLyrics, "全屏歌词", hovered, onOpenLyricsView)
            }
        } else {
            null
        }
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(viewportHeight)) {
            LyricList(
                lines = lines,
                currentIndex = currentIndex,
                inactiveColor = inactiveColor,
                expanded = expanded,
                viewportHeight = viewportHeight,
                onSeek = onSeek,
                onCurrentLineLayout = { currentWrapLines = it }
            )
            // 按钮默认隐藏，鼠标移到卡片上才出现
            HoverReveal(revealed = hovered, modifier = Modifier.align(Alignment.BottomEnd)) {
                Button(
                    onClick = { expanded = !expanded },
                    enabled = hovered,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (expanded) "收起" else "显示更多", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, description: String, revealed: Boolean, onClick: () -> Unit) {
    HoverReveal(revealed = revealed) {
        DesktopTooltip(description) {
            IconButton(onClick = onClick, enabled = revealed, modifier = Modifier.size(HeaderButtonSize)) {
                Icon(icon, description, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(HeaderIconSize))
            }
        }
    }
}

// 单一色相的光斑：深色底 + 一枚渐隐光斑；fill 必须是明显压暗过的变体，不能直接传未处理的 base
private fun DrawScope.drawSingleHueMesh(fill: Color, darkBlob: Color, darkCenter: Offset, darkRadius: Float) {
    drawRect(color = fill)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(darkBlob.copy(alpha = 0.6f), Color.Transparent),
            center = darkCenter,
            radius = darkRadius
        ),
        center = darkCenter,
        radius = darkRadius
    )
}

// 视口上下边缘渐隐：用 DstIn 把内容按渐变蒙版擦淡，顶部渐隐只在需要时启用
internal fun Modifier.verticalEdgeFade(top: Dp, bottom: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val height = size.height
        if (height <= 0f) return@drawWithContent
        val topFraction = (top.toPx() / height).coerceIn(0f, 0.5f)
        val bottomFraction = (bottom.toPx() / height).coerceIn(0f, 0.5f)
        val stops = buildList {
            add(0f to if (topFraction > 0f) Color.Transparent else Color.Black)
            if (topFraction > 0f) add(topFraction to Color.Black)
            add(1f - bottomFraction to Color.Black)
            add(1f to if (bottomFraction > 0f) Color.Transparent else Color.Black)
        }
        drawRect(Brush.verticalGradient(colorStops = stops.toTypedArray()), blendMode = BlendMode.DstIn)
    }

@Composable
private fun LyricList(
    lines: List<LyricLine>,
    currentIndex: Int,
    inactiveColor: Color,
    expanded: Boolean,
    viewportHeight: Dp,
    onSeek: (Long) -> Unit,
    onCurrentLineLayout: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 折叠时当前行贴顶，展开后落在约三分之一处，上方保留已唱过的行作语境
    val anchorOffsetPx = if (expanded) with(density) { (viewportHeight / 3).roundToPx() } else 0
    LaunchedEffect(currentIndex, lines, expanded) {
        if (currentIndex in lines.indices) listState.animateScrollToItem(currentIndex, -anchorOffsetPx)
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize().verticalEdgeFade(top = if (expanded) FadeHeight else 0.dp, bottom = FadeHeight),
        verticalArrangement = Arrangement.spacedBy(LyricItemSpacing),
        // 底部留白让末尾几行也能滚到定位处
        contentPadding = PaddingValues(bottom = viewportHeight)
    ) {
        itemsIndexed(lines, key = { index, line -> "${line.timeMs}_$index" }) { index, line ->
            // 纯音乐段的空白占位行不占用预览区域
            if (line.text.isBlank()) return@itemsIndexed
            LyricRow(
                line = line,
                isCurrent = index == currentIndex,
                look = lyricLineLook(index, currentIndex),
                inactiveColor = inactiveColor,
                onClick = { onSeek(line.timeMs) },
                onCurrentLineLayout = onCurrentLineLayout
            )
        }
    }
}

// 悬停时变白并加下划线，点击跳转；当前行与远近缩放、透明度沿用移动端
@Composable
private fun LyricRow(
    line: LyricLine,
    isCurrent: Boolean,
    look: LyricLineLook,
    inactiveColor: Color,
    onClick: () -> Unit,
    onCurrentLineLayout: (Int) -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(look.scale, spring(dampingRatio = 0.7f, stiffness = 300f), label = "lyricScale")
    val alpha by animateFloatAsState(look.alpha, tween(300, easing = FastOutSlowInEasing), label = "lyricAlpha")
    val translationAlpha by animateFloatAsState(
        if (isCurrent) 0.85f else 0.7f,
        tween(300, easing = FastOutSlowInEasing),
        label = "lyricTranslationAlpha"
    )
    val textColor = if (isCurrent || hovered) Color.White else inactiveColor
    val mainSize = if (isCurrent) 20.sp else 18.sp
    val translationSize = if (isCurrent) 15.sp else 14.sp

    Column(
        Modifier.fillMaxWidth(0.85f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Text(
            line.text,
            fontSize = mainSize,
            lineHeight = (mainSize.value * 1.35f).sp,
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            textDecoration = if (hovered) TextDecoration.Underline else TextDecoration.None,
            onTextLayout = { if (isCurrent) onCurrentLineLayout(it.lineCount) },
            modifier = Modifier.fillMaxWidth()
        )
        line.translation?.let { translation ->
            Text(
                translation,
                fontSize = translationSize,
                lineHeight = (translationSize.value * 1.35f).sp,
                color = textColor.copy(alpha = translationAlpha),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}
