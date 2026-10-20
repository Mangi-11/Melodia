package com.lin0721.linmusic.desktop.ui.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.format.formatDuration
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.desktop.data.local.LocalLibraryRepository
import com.lin0721.linmusic.desktop.data.local.LocalMatchState
import com.lin0721.linmusic.desktop.data.local.LocalTrack
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.DesktopDialog
import com.lin0721.linmusic.desktop.ui.components.DialogButton
import com.lin0721.linmusic.desktop.ui.components.DialogTextField
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface CandidateState {
    data object Idle : CandidateState
    data object Loading : CandidateState
    data class Loaded(val tracks: List<Track>) : CandidateState
    data object Failed : CandidateState
}

internal fun matchStateLabel(track: LocalTrack): String = when (track.matchState) {
    LocalMatchState.PENDING -> "等待自动匹配"
    LocalMatchState.AUTO -> "已自动匹配"
    LocalMatchState.MANUAL -> "已手动匹配"
    LocalMatchState.NOT_FOUND -> "未找到匹配的歌曲"
    LocalMatchState.DETACHED -> "已解除匹配"
}

// 手动为本地歌曲选择对应的网易云歌曲
@Composable
internal fun LocalMatchDialog(
    track: LocalTrack,
    repository: LocalLibraryRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var keyword by remember(track.path) { mutableStateOf("${track.title} ${track.artist}".trim()) }
    var state by remember(track.path) { mutableStateOf<CandidateState>(CandidateState.Idle) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    val search: () -> Unit = {
        searchJob?.cancel()
        if (keyword.isNotBlank()) {
            state = CandidateState.Loading
            searchJob = scope.launch {
                state = repository.searchCandidates(keyword).fold(
                    onSuccess = { CandidateState.Loaded(it) },
                    onFailure = { CandidateState.Failed }
                )
            }
        }
    }
    LaunchedEffect(track.path) {
        runCatching { focusRequester.requestFocus() }
        search()
    }

    DesktopDialog(
        title = "匹配网易云歌曲",
        onDismiss = onDismiss,
        width = 560.dp,
        actions = {
            if (track.songId != null) {
                DialogButton("解除匹配", {
                    repository.detachMatch(track.path)
                    onDismiss()
                }, danger = true)
            }
            DialogButton("关闭", onDismiss)
        }
    ) {
        Text(
            "${track.title} · ${track.artist}　${matchStateLabel(track)}",
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogTextField(
                value = keyword,
                onValueChange = { keyword = it.take(100) },
                placeholder = "输入歌名或歌手",
                focusRequester = focusRequester,
                onEnter = search,
                modifier = Modifier.weight(1f)
            )
            DialogButton("搜索", search, primary = true, enabled = keyword.isNotBlank())
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).padding(top = 10.dp)) {
            when (val current = state) {
                CandidateState.Idle -> Unit
                CandidateState.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesktopColors.Accent)
                }
                CandidateState.Failed -> CenteredHint("搜索失败，请检查网络后重试")
                is CandidateState.Loaded -> if (current.tracks.isEmpty()) {
                    CenteredHint("没有找到相关歌曲，换个关键词试试")
                } else {
                    LazyColumn {
                        items(current.tracks, key = { it.id }) { candidate ->
                            CandidateRow(candidate, selected = candidate.id == track.songId) {
                                repository.setManualMatch(track.path, candidate.id)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DesktopColors.TextGray, fontSize = 13.sp)
    }
}

@Composable
private fun CandidateRow(track: Track, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.al.picUrl, 40.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                track.name,
                color = if (selected) DesktopColors.Accent else DesktopColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(track.ar.joinToString(" / ") { it.name }, track.al.name).filter { it.isNotBlank() }.joinToString(" · "),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatDuration(track.dt),
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}
