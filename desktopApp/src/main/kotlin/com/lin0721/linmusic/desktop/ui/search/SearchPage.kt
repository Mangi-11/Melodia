package com.lin0721.linmusic.desktop.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.podcast.PodcastProgramRow
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.track.TrackActions
import com.lin0721.linmusic.desktop.ui.track.TrackRow
import com.lin0721.linmusic.desktop.ui.track.rememberTrackActions
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import com.lin0721.linmusic.feature.search.ui.ExternalSearchUiState
import com.lin0721.linmusic.feature.search.ui.SearchMode
import com.lin0721.linmusic.feature.search.ui.SearchResultsUiState
import com.lin0721.linmusic.feature.search.ui.SearchViewModel

private const val RESULT_LOAD_MORE_THRESHOLD = 5

// 最近搜索与联想在标题栏的下拉浮层里，这里只承载搜索结果
@Composable
fun SearchPage(
    viewModel: SearchViewModel,
    controller: PlaybackController,
    onOpenPlaylist: (id: Long, title: String, isAlbum: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by viewModel.mode.collectAsState()
    Box(modifier.fillMaxSize()) {
        if (mode == SearchMode.Results) {
            ResultsContent(viewModel, controller, onOpenPlaylist)
        } else {
            Centered { Text("在上方搜索框输入关键词后按回车搜索", color = DesktopColors.TextGray) }
        }
    }
}

@Composable
private fun ResultsContent(
    viewModel: SearchViewModel,
    controller: PlaybackController,
    onOpenPlaylist: (Long, String, Boolean) -> Unit
) {
    val selectedPlatform by viewModel.selectedPlatform.collectAsState()
    val searchPlatforms by viewModel.searchPlatforms.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val results by viewModel.resultsByType.getValue(selectedType).collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val collectState by viewModel.collectState.collectAsState()
    val actions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = viewModel::toggleLikeSong,
        onPlayNext = viewModel::addTrackToPlayNext,
        onPrepareCollect = viewModel::prepareCollectDialog,
        onSaveCollect = viewModel::savePlaylistCollection,
        onCreateAndAdd = viewModel::createPlaylistAndAddSong
    )

    Column(Modifier.fillMaxSize()) {
        if (searchPlatforms.size > 1) {
            PlatformTabs(
                platforms = searchPlatforms,
                selected = selectedPlatform,
                externalResults = viewModel.externalResults,
                onSelect = viewModel::selectPlatform,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (selectedPlatform == MusicPlatform.NETEASE) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { viewModel.selectType(type) },
                        label = { Text(type.label) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DesktopColors.Surface,
                            labelColor = DesktopColors.TextPrimary,
                            selectedContainerColor = DesktopColors.TextPrimary,
                            selectedLabelColor = DesktopColors.Pane
                        ),
                        border = null
                    )
                }
            }
            when (val state = results) {
                SearchResultsUiState.Idle, SearchResultsUiState.Loading ->
                    Centered { CircularProgressIndicator(color = DesktopColors.Accent) }
                SearchResultsUiState.Empty -> Centered { Text("没有找到相关结果", color = DesktopColors.TextGray) }
                is SearchResultsUiState.Error -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = DesktopColors.TextGray)
                        TextButton(onClick = viewModel::retrySearch) { Text("重试", color = DesktopColors.TextPrimary) }
                    }
                }
                is SearchResultsUiState.Success -> {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember(state) {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            state.hasMore && !state.isLoadingMore &&
                                lastVisible >= listState.layoutInfo.totalItemsCount - RESULT_LOAD_MORE_THRESHOLD
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) viewModel.loadMore()
                    }
                    HoverScrollbarBox(listState) {
                        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            itemsIndexed(state.items) { index, item ->
                                ResultEntry(index, item, nowPlaying?.songId, viewModel, actions, onOpenPlaylist)
                            }
                            if (state.isLoadingMore) {
                                item { Centered { CircularProgressIndicator(color = DesktopColors.Accent) } }
                            }
                        }
                    }
                }
            }
        } else {
            ExternalResultsContent(viewModel, selectedPlatform, nowPlaying?.songId)
        }
    }
}

@Composable
private fun ExternalResultsContent(
    viewModel: SearchViewModel,
    platform: MusicPlatform,
    currentSongId: Long?
) {
    val resultsState = viewModel.externalResults[platform]?.collectAsState()?.value ?: ExternalSearchUiState.Idle
    when (resultsState) {
        ExternalSearchUiState.Idle, ExternalSearchUiState.Loading ->
            Centered { CircularProgressIndicator(color = DesktopColors.Accent) }
        ExternalSearchUiState.Empty -> Centered { Text("在 ${platform.displayName} 没有找到相关结果", color = DesktopColors.TextGray) }
        is ExternalSearchUiState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(resultsState.message, color = DesktopColors.TextGray)
                TextButton(onClick = { viewModel.searchExternal(viewModel.committedQuery.value, platform, false) }) {
                    Text("重试", color = DesktopColors.TextPrimary)
                }
            }
        }
        is ExternalSearchUiState.Success -> {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember(resultsState) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    resultsState.hasMore && !resultsState.isLoadingMore &&
                        lastVisible >= listState.layoutInfo.totalItemsCount - RESULT_LOAD_MORE_THRESHOLD
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMoreExternal(platform)
            }
            HoverScrollbarBox(listState) {
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    itemsIndexed(resultsState.tracks) { index, track ->
                        val isCurrent = currentSongId == track.queueSongId
                        ExternalResultEntry(index, track, isCurrent) {
                            viewModel.playExternalTrack(track)
                        }
                    }
                    if (resultsState.isLoadingMore) {
                        item { Centered { CircularProgressIndicator(color = DesktopColors.Accent) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalResultEntry(
    index: Int,
    track: ExternalTrack,
    isCurrent: Boolean,
    onPlay: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            color = DesktopColors.TextGray,
            fontSize = 13.sp,
            modifier = Modifier.width(32.dp)
        )
        Cover(track.coverUrl.orEmpty(), 48.dp, shape = RoundedCornerShape(4.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                track.name,
                color = if (isCurrent) DesktopColors.Accent else DesktopColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (track.albumName.isNotBlank()) "${track.artists} · ${track.albumName}" else track.artists,
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (track.durationMs > 0) {
            val totalSeconds = (track.durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            Text(
                "$minutes:${seconds.toString().padStart(2, '0')}",
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ResultEntry(
    index: Int,
    item: SearchResultItem,
    currentSongId: Long?,
    viewModel: SearchViewModel,
    actions: TrackActions,
    onOpenPlaylist: (Long, String, Boolean) -> Unit
) {
    val navigator = LocalDesktopNavigator.current
    when (item) {
        is SearchResultItem.SongItem -> TrackRow(
            index = index,
            track = item.track,
            isCurrent = currentSongId == item.track.id,
            onPlay = { viewModel.playSong(item.track) },
            actions = actions
        )
        is SearchResultItem.PlaylistItem -> CollectionEntry(
            coverUrl = item.playlist.coverImgUrl,
            title = item.playlist.name,
            subtitle = "${item.playlist.trackCount} 首 · ${item.playlist.creator?.nickname.orEmpty()}",
            onClick = { onOpenPlaylist(item.playlist.id, item.playlist.name, false) }
        )
        is SearchResultItem.AlbumItem -> CollectionEntry(
            coverUrl = item.album.picUrl,
            title = item.album.name,
            subtitle = "专辑",
            onClick = { onOpenPlaylist(item.album.id, item.album.name, true) }
        )
        is SearchResultItem.ArtistItem -> CollectionEntry(
            coverUrl = item.artist.picUrl,
            title = item.artist.name,
            subtitle = "歌手",
            circle = true,
            onClick = { navigator.openArtist(item.artist.id, item.artist.name) }
        )
        is SearchResultItem.RadioItem -> CollectionEntry(
            coverUrl = item.radio.picUrl,
            title = item.radio.name,
            subtitle = listOfNotNull(
                item.radio.djName.takeIf { it.isNotBlank() },
                item.radio.programCount.takeIf { it > 0 }?.let { "$it 期" }
            ).joinToString(" · ").ifBlank { "电台" },
            onClick = { navigator.openRadio(item.radio.id) }
        )
        is SearchResultItem.ProgramItem -> PodcastProgramRow(
            program = item.program,
            progress = null,
            onPlay = { viewModel.playProgram(item.program) },
            index = null
        )
    }
}

@Composable
private fun CollectionEntry(
    coverUrl: String,
    title: String,
    subtitle: String,
    circle: Boolean = false,
    onClick: (() -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverUrl, 56.dp, shape = if (circle) CircleShape else RoundedCornerShape(4.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { content() }
}
