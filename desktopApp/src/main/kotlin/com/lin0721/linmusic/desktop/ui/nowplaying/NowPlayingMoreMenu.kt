package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.getQualityDisplayName
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackController.Companion.CONTEXT_INTELLIGENCE
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.core.player.formatSleepTimerRemaining
import com.lin0721.linmusic.desktop.ui.components.DesktopMenu
import com.lin0721.linmusic.desktop.ui.components.MenuDivider
import com.lin0721.linmusic.desktop.ui.components.MenuItem
import com.lin0721.linmusic.desktop.ui.components.SimpleMenuItem
import com.lin0721.linmusic.desktop.ui.components.SubMenuItem
import com.lin0721.linmusic.desktop.ui.components.copyTextToClipboard
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.CollectSubmenuContent
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.launch

private val SleepTimerOptions = listOf(15, 30, 60, 90)
private val QualityKeys = listOf("standard", "exhigh", "lossless", "hires", "jymaster")
private val MoreMenuWidth = 220.dp

// 与移动端播放页「更多」面板对齐；桌面没有系统分享，分享落成复制歌曲链接
@Composable
internal fun NowPlayingMoreMenu(
    track: NowPlaying,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    val scope = rememberCoroutineScope()
    val playContext by controller.playContext.collectAsState()
    val sleepRemaining by controller.sleepTimerRemaining.collectAsState()
    val podcast by playerViewModel.podcastState.collectAsState()
    val detailState by playerViewModel.songDetailState.collectAsState()
    val collectState by playerViewModel.collectState.collectAsState()

    // 红心与下一首播放用不到，只借用收藏二级菜单与新建歌单对话框
    val collectActions = rememberTrackActions(
        likedSongIds = emptySet(),
        collectState = collectState,
        onToggleLike = { _, _ -> },
        onPlayNext = {},
        onPrepareCollect = playerViewModel::prepareCollectDialog,
        onSaveCollect = playerViewModel::savePlaylistCollection,
        onCreateAndAdd = playerViewModel::createPlaylistAndAddSong
    )

    val songId = track.songId
    val detail = detailState.songDetail?.takeIf { it.id == songId }
    val isIntelligence = playContext == CONTEXT_INTELLIGENCE
    val isRoaming = playContext == SimilarRoamingController.CONTEXT_ROAMING
    val sleepActive = sleepRemaining > 0L
    // 本地歌曲未匹配云端时只有占位 id，依赖云端数据的项全部隐藏
    val cloudSongId = songId?.takeIf { it > 0L && !detailState.isLocalOnly }
    val cloud = !podcast.isPodcast && cloudSongId != null
    val modes = !podcast.isPodcast && (cloud || isIntelligence || isRoaming)
    val currentQuality = navigator.downloadLevel

    DesktopMenu(expanded = expanded, onDismiss = onDismiss, width = MoreMenuWidth) {
        if (cloud && cloudSongId != null) {
            val albumName = detail?.al?.name?.takeIf { it.isNotBlank() } ?: detailState.songWiki?.album ?: "未知专辑"
            MenuItem("专辑: $albumName", icon = Icons.Rounded.Album, onClick = {
                onDismiss()
                scope.launch { openCurrentSongAlbum(songId, playerViewModel, navigator) }
            })

            val artists = detail?.ar.orEmpty().filter { it.id > 0L }
            when {
                artists.size > 1 -> SubMenuItem("歌手", icon = Icons.Rounded.Person) {
                    artists.forEach { artist ->
                        SimpleMenuItem(artist.name, onClick = {
                            onDismiss()
                            navigator.openArtist(artist.id, artist.name)
                        })
                    }
                }
                artists.size == 1 -> MenuItem("歌手: ${artists[0].name}", icon = Icons.Rounded.Person, onClick = {
                    onDismiss()
                    navigator.openArtist(artists[0].id, artists[0].name)
                })
                else -> MenuItem("歌手: ${track.artist}", icon = Icons.Rounded.Person, enabled = false, onClick = {})
            }

            if (navigator.isLoggedIn) {
                val collectTrack = detail ?: Track(id = cloudSongId, name = track.title)
                SubMenuItem(
                    "收藏到歌单",
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    onOpen = { playerViewModel.prepareCollectDialog(cloudSongId) }
                ) {
                    CollectSubmenuContent(collectTrack, collectActions, onDismiss)
                }
            } else {
                MenuItem("收藏到歌单", icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = {
                    onDismiss()
                    navigator.showMessage("请先登录账号")
                })
            }
            MenuDivider()
        }

        if (modes) {
            if (isIntelligence || cloudSongId != null) {
                MenuItem(if (isIntelligence) "退出心动模式" else "心动模式", icon = Icons.Rounded.Favorite, onClick = {
                    onDismiss()
                    if (isIntelligence) {
                        controller.disableIntelligence()
                    } else if (cloudSongId != null) {
                        playerViewModel.startIntelligenceMode(cloudSongId, track.title, track.artist, track.artworkUri.orEmpty())
                    }
                })
            }
            if (isRoaming || cloudSongId != null) {
                MenuItem(if (isRoaming) "退出相似歌曲漫游" else "相似歌曲漫游", icon = Icons.Rounded.Radio, onClick = {
                    onDismiss()
                    if (isRoaming) {
                        controller.disableRoaming()
                    } else if (cloudSongId != null) {
                        playerViewModel.startSimilarSongsRoaming(cloudSongId, track.title, track.artist, track.artworkUri.orEmpty())
                    }
                })
            }
            if (cloud && cloudSongId != null) {
                MenuItem("插播相似歌曲", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {
                    onDismiss()
                    playerViewModel.insertSimilarSongs(cloudSongId)
                })
            }
            MenuDivider()
        }

        if (cloud && cloudSongId != null) {
            MenuItem("复制歌曲链接", icon = Icons.Rounded.Link, onClick = {
                onDismiss()
                val ok = copyTextToClipboard("https://music.163.com/song?id=$cloudSongId")
                navigator.showMessage(if (ok) "已复制链接" else "复制失败")
            })
            MenuItem("下载", icon = Icons.Rounded.Download, onClick = {
                onDismiss()
                scope.launch {
                    val target = playerViewModel.resolveSongDetail(cloudSongId) ?: Track(
                        id = cloudSongId,
                        name = track.title,
                        ar = listOf(Artist(name = track.artist)),
                        al = Album(picUrl = track.artworkUri.orEmpty())
                    )
                    navigator.downloadTrack(target)
                }
            })
            SubMenuItem("音质: ${getQualityDisplayName(currentQuality)}", icon = Icons.Rounded.Tune) {
                QualityKeys.forEach { key ->
                    val selected = key == currentQuality
                    SimpleMenuItem(
                        getQualityDisplayName(key),
                        onClick = {
                            onDismiss()
                            if (!selected) playerViewModel.updateQuality(key)
                        },
                        trailing = if (selected) {
                            { Icon(Icons.Rounded.Check, "当前音质", tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        SubMenuItem(
            if (sleepActive) "睡眠定时 (${formatSleepTimerRemaining(sleepRemaining)})" else "睡眠定时",
            icon = Icons.Rounded.Bedtime
        ) {
            SleepTimerOptions.forEach { minutes ->
                SimpleMenuItem("$minutes 分钟", icon = Icons.Rounded.Timer, onClick = {
                    onDismiss()
                    playerViewModel.setSleepTimer(minutes)
                })
            }
            if (sleepActive) {
                SimpleMenuItem("取消定时", icon = Icons.Rounded.TimerOff, onClick = {
                    onDismiss()
                    playerViewModel.setSleepTimer(0)
                })
            }
        }
    }
}
