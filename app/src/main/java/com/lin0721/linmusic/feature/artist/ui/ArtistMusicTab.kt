package com.lin0721.linmusic.feature.artist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.SongRow
import com.lin0721.linmusic.core.ui.components.SongRowData
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

// 音乐 Tab: 呈现热门50首歌曲
fun LazyListScope.artistMusicTab(
    hotSongs: List<Track>,
    likedSongIds: Set<Long>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isLoggedIn: Boolean,
    onPlaySong: (Track, List<Track>) -> Unit,
    onLikeClick: (Long) -> Unit,
    onOpenCollectSheet: (Long) -> Unit,
    onOpenMoreOptions: (Track) -> Unit,
    onRequireLogin: () -> Unit
) {
    if (hotSongs.isEmpty()) {
        item(key = "empty_songs") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    } else {
        itemsIndexed(hotSongs, key = { _, track -> "hot_${track.id}" }) { index, track ->
            val isActive = track.id.toString() == currentTrackId
            SongRow(
                data = SongRowData(
                    id = track.id,
                    title = track.name,
                    artist = track.ar.joinToString(" • ") { it.name },
                    coverUrl = track.al.picUrl,
                    isVip = track.fee == 1
                ),
                isActive = isActive,
                isPlaying = isPlaying,
                index = index + 1,
                onClick = { onPlaySong(track, hotSongs) },
                trailingSlot = {
                    val isLiked = track.id in likedSongIds
                    MelodiaIconButton(
                        onClick = {
                            if (!isLoggedIn) {
                                onRequireLogin()
                            } else {
                                onOpenCollectSheet(track.id)
                                onLikeClick(track.id)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "收藏到歌单",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    MelodiaIconButton(
                        onClick = { onOpenMoreOptions(track) },
                        modifier = Modifier.size(32.dp).padding(end = MelodiaSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    }
}
