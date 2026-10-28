package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.interaction.copyOnLongClick
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaColors
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.feature.podcast.ui.PodcastPlayerState

// 封面下方的歌名歌手行，右侧带收藏按钮
@Composable
fun SongInfo(
    title: String,
    artist: String,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null,
    // 播客节目：♡ 直接订阅所属电台
    podcast: PodcastPlayerState = PodcastPlayerState(),
    onToggleSubscribe: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MelodiaSpacing.md)) {
            Text(
                text = title,
                color = MelodiaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier
                    .basicMarquee()
                    .copyOnLongClick(title, "歌名", onClick = { onAlbumClick?.invoke() })
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                color = MelodiaColors.TextPrimary.copy(alpha = 0.75f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.copyOnLongClick(artist, "歌手", onClick = { onArtistClick?.invoke() })
            )
        }
        if (podcast.isPodcast) {
            if (podcast.canSubscribe) {
                MelodiaIconButton(onClick = onToggleSubscribe, modifier = Modifier.offset(x = 10.dp)) {
                    Icon(
                        if (podcast.subscribed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (podcast.subscribed) "取消订阅电台" else "订阅电台",
                        tint = MelodiaColors.TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else MelodiaIconButton(
            onClick = onToggleLike,
            modifier = Modifier.offset(x = 10.dp)
        ) {
            Icon(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = MelodiaColors.TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
