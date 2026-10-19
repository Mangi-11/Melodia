package com.lin0721.linmusic.feature.player.ui

import com.lin0721.linmusic.core.ui.components.PlayPauseIcon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.ui.interaction.pressScale
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 主控件行：随机/漫游、上一曲、播放暂停、下一曲、循环模式
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    playMode: PlayMode,
    isRoaming: Boolean = false,
    onDisableRoaming: () -> Unit = {},
    isIntelligence: Boolean = false,
    onDisableIntelligence: () -> Unit = {},
    // 播客没有随机与循环，隐藏后用等宽占位保持播放键居中
    showModeButtons: Boolean = true
) {
    val motion = LocalUiMotion.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = MelodiaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showModeButtons) MelodiaIconButton(
            onClick = {
                if (isRoaming) {
                    onDisableRoaming()
                } else if (isIntelligence) {
                    onDisableIntelligence()
                } else {
                    onToggleShuffle()
                }
            },
            modifier = Modifier.offset(x = (-10).dp)
        ) {
            Icon(
                imageVector = when {
                    isRoaming -> Icons.Rounded.AllInclusive
                    isIntelligence -> Icons.Rounded.AutoAwesome
                    else -> Icons.Default.Shuffle
                },
                contentDescription = null,
                tint = if (isRoaming || isIntelligence || playMode == PlayMode.SHUFFLE) Color.White else TextGray,
                modifier = Modifier.size(28.dp)
            )
        } else Spacer(Modifier.size(ModeButtonSlot))

        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MelodiaIconButton(onClick = onPlayPrevious, style = MelodiaPress.Transport) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            val playInteraction = remember { MutableInteractionSource() }
            val isPressed by playInteraction.collectIsPressedAsState()
            val buttonAlpha = remember { Animatable(1f) }
            var clickTrigger by remember { mutableIntStateOf(0) }

            LaunchedEffect(isPressed) {
                if (isPressed) {
                    buttonAlpha.animateTo(0.70f, motion.tween(durationMillis = 80))
                } else if (clickTrigger == 0) {
                    buttonAlpha.animateTo(1f, motion.tween(durationMillis = 200, easing = FastOutSlowInEasing))
                }
            }

            LaunchedEffect(clickTrigger) {
                if (clickTrigger > 0) {
                    buttonAlpha.snapTo(0.70f)
                    buttonAlpha.animateTo(1f, motion.tween(durationMillis = 220, easing = FastOutSlowInEasing))
                }
            }

            FloatingActionButton(
                onClick = {
                    clickTrigger++
                    onTogglePlay()
                },
                containerColor = Color.White,
                shape = CircleShape,
                interactionSource = playInteraction,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        alpha = buttonAlpha.value
                    }
            ) {
                PlayPauseIcon(isPlaying, Color.Black, size = 42.dp)
            }
            MelodiaIconButton(onClick = onPlayNext, style = MelodiaPress.Transport) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
        }

        if (showModeButtons) MelodiaIconButton(
            onClick = onToggleRepeat,
            modifier = Modifier.offset(x = 10.dp)
        ) {
            Icon(
                if (playMode == PlayMode.SINGLE_LOOP) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = null,
                tint = if (playMode == PlayMode.SINGLE_LOOP) Color.White else TextGray,
                modifier = Modifier.size(28.dp)
            )
        } else Spacer(Modifier.size(ModeButtonSlot))
    }
}

// 与图标按钮同宽的占位
private val ModeButtonSlot = 40.dp
