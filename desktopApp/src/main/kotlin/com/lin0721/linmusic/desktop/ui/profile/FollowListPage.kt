package com.lin0721.linmusic.desktop.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.components.Cover
import com.lin0721.linmusic.desktop.ui.components.HoverScrollbarBox
import com.lin0721.linmusic.desktop.ui.shell.LocalDesktopNavigator
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowUserItem
import com.lin0721.linmusic.feature.profile.ui.FollowListMode
import com.lin0721.linmusic.feature.profile.ui.FollowListUiState
import com.lin0721.linmusic.feature.profile.ui.FollowListViewModel

private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun FollowListPage(
    uid: Long,
    mode: FollowListMode,
    viewModel: FollowListViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(uid, mode) { viewModel.loadIfNeeded(uid, mode) }
    val uiState by viewModel.uiState.collectAsState()
    val title = if (mode == FollowListMode.FOLLOWS) "关注" else "粉丝"

    Column(modifier.fillMaxSize()) {
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )
        when (val state = uiState) {
            FollowListUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent)
            }
            is FollowListUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = DesktopColors.TextGray)
                    TextButton(onClick = viewModel::retry) { Text("重试", color = DesktopColors.TextPrimary) }
                }
            }
            is FollowListUiState.Success -> if (state.users.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (mode == FollowListMode.FOLLOWS) "还没有关注任何人" else "还没有粉丝",
                        color = DesktopColors.TextGray,
                        fontSize = 14.sp
                    )
                }
            } else {
                FollowList(state, viewModel)
            }
        }
    }
}

@Composable
private fun FollowList(state: FollowListUiState.Success, viewModel: FollowListViewModel) {
    val navigator = LocalDesktopNavigator.current
    val listState = rememberLazyListState()
    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            itemsIndexed(state.users, key = { _, user -> user.uid }) { index, user ->
                UserRow(
                    user = user,
                    onClick = { navigator.openProfile(user.uid) },
                    onToggleFollow = {
                        if (navigator.isLoggedIn) viewModel.toggleFollow(user.uid) else navigator.showMessage("请先登录账号")
                    }
                )
                if (state.hasMore && !state.isLoadingMore && index >= state.users.size - LOAD_MORE_THRESHOLD) {
                    LaunchedEffect(state.users.size) { viewModel.loadMore() }
                }
            }
            if (state.isLoadingMore) {
                item(key = "loading_more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: ProfileFollowUserItem, onClick: () -> Unit, onToggleFollow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(user.avatarUrl, 48.dp, shape = CircleShape)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                user.nickname,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (user.signature.isNotBlank()) {
                Text(user.signature, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FollowButton(followed = user.isFollowedByMe, onClick = onToggleFollow)
    }
}
