package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.platform.LibraryMode
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibraryUiState
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel

private val RailCoverSize = 48.dp

// 收起态：只保留图标、创建按钮与竖排封面
@Composable
fun LibraryRail(
    viewModel: LibraryViewModel,
    isLoggedIn: Boolean,
    onItemClick: (LibraryItem) -> Unit,
    onModeChange: (LibraryMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var deletingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var isSavingEdit by remember { mutableStateOf(false) }

    editingItem?.let { item ->
        val id = item.id.toLongOrNull()
        if (id != null) {
            EditPlaylistDialog(
                initialName = item.title,
                initialDescription = "",
                coverUrl = item.coverUrl,
                isSaving = isSavingEdit,
                onDismiss = { editingItem = null },
                onConfirm = { name, desc, coverBytes ->
                    isSavingEdit = true
                    viewModel.updatePlaylistInfo(id, item.title, name, null, desc, coverBytes) { success ->
                        isSavingEdit = false
                        if (success) editingItem = null
                    }
                }
            )
        }
    }

    deletingItem?.let { item ->
        val id = item.id.toLongOrNull()
        if (id != null) {
            AlertDialog(
                onDismissRequest = { deletingItem = null },
                title = { Text("删除歌单", color = DesktopColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("确定要删除歌单「${item.title}」吗？", color = DesktopColors.TextGray, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePlaylist(id)
                        deletingItem = null
                    }) {
                        Text("删除", color = DangerColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingItem = null }) {
                        Text("取消", color = DesktopColors.TextGray)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                containerColor = DesktopColors.PopupSurface
            )
        }
    }

    val handleDelete: (LibraryItem) -> Unit = { item ->
        if (item.isOwnedByMe && item.type == LibraryItemType.PLAYLIST) {
            deletingItem = item
        } else {
            item.id.toLongOrNull()?.let { id ->
                when (item.type) {
                    LibraryItemType.PLAYLIST -> viewModel.unsubscribePlaylist(id)
                    LibraryItemType.ALBUM -> viewModel.unsubscribeAlbum(id)
                    LibraryItemType.ARTIST -> viewModel.unsubscribeArtist(id)
                }
            }
        }
    }

    Column(modifier.fillMaxSize().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LibraryTitleToggle(
            baseIcon = Icons.Rounded.LibraryMusic,
            hoverIcon = Icons.Rounded.KeyboardDoubleArrowRight,
            hint = "展开音乐库",
            showTitle = false
        ) {
            viewModel.loadLibraryData()
            onModeChange(LibraryMode.DEFAULT)
        }
        if (!isLoggedIn) return@Column
        Spacer(Modifier.height(12.dp))
        LibraryCreateButton(viewModel, pill = false)
        val state = uiState
        if (state is LibraryUiState.Success) {
            val listState = rememberLazyListState()
            val scrolled by remember { derivedStateOf { listState.isScrolled } }
            Box(Modifier.fillMaxSize()) {
                HoverScrollbarBox(listState) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(state.filteredItems, key = { it.id }) { item ->
                            LibraryContextMenuBox(
                                item = item,
                                viewModel = viewModel,
                                onEdit = { editingItem = it },
                                onDelete = handleDelete
                            ) {
                                DesktopTooltip(tooltipContent = { LibraryItemTooltipCard(item) }, side = TooltipSide.Right) {
                                    val shape = coverShape(item)
                                    Box(Modifier.clip(shape).pointerHoverIcon(PointerIcon.Hand).clickable { onItemClick(item) }) {
                                        Cover(item.coverUrl, RailCoverSize, shape = shape)
                                    }
                                }
                            }
                        }
                    }
                }
                ListTopShadow(scrolled)
            }
        }
    }
}
