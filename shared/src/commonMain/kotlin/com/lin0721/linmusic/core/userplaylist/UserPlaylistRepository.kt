package com.lin0721.linmusic.core.userplaylist

import kotlinx.coroutines.flow.Flow

// 当前用户歌单列表仓储（core 共享能力，被 library/artist/playlist 的"收藏到歌单"等入口复用）
interface UserPlaylistRepository {

    fun getUserPlaylists(uid: Long, limit: Int = 1000): Flow<Result<List<UserPlaylist>>>

    // 更新用户歌单顺序并同步至云端
    fun updatePlaylistOrder(playlistIds: List<Long>): Flow<Result<Unit>>

    // 置顶或取消置顶自建歌单并同步至云端
    fun setPlaylistTop(playlistId: Long, top: Boolean): Flow<Result<Unit>>
}
