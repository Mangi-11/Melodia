package com.lin0721.linmusic.core.userplaylist

import com.lin0721.linmusic.core.cache.OfflineFallback
import com.lin0721.linmusic.core.network.apiFlow
import kotlinx.coroutines.flow.Flow

class UserPlaylistRepositoryImpl(
    private val apiService: UserPlaylistApi,
    private val offline: OfflineFallback
) : UserPlaylistRepository {

    override fun getUserPlaylists(uid: Long, limit: Int): Flow<Result<List<UserPlaylist>>> =
        offline.cached("user_playlists_$uid") {
            apiFlow(
                request = { apiService.getUserPlaylists(UserPlaylistRequest(uid = uid, limit = limit)) },
                isSuccess = { it.isSuccess },
                code = { it.code },
                transform = { it.playlist }
            )
        }

    override fun updatePlaylistOrder(playlistIds: List<Long>): Flow<Result<Unit>> = apiFlow(
        request = {
            apiService.updatePlaylistOrder(
                PlaylistOrderUpdateRequest(
                    ids = playlistIds.joinToString(separator = ",", prefix = "[", postfix = "]") { "$it" }
                )
            )
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun setPlaylistTop(playlistId: Long, top: Boolean): Flow<Result<Unit>> = apiFlow(
        request = {
            if (top) {
                apiService.topPlaylist(PlaylistTopRequest(id = playlistId))
            } else {
                apiService.cancelTopPlaylist(PlaylistTopRequest(id = playlistId))
            }
        },
        isSuccess = { it.isSuccess },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )
}
