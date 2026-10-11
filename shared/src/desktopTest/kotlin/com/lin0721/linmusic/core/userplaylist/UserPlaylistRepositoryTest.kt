package com.lin0721.linmusic.core.userplaylist

import com.lin0721.linmusic.core.cache.testOfflineFallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPlaylistRepositoryTest {

    private class FakeUserPlaylistApi : UserPlaylistApi {
        var lastOrderRequest: PlaylistOrderUpdateRequest? = null
        var shouldSucceed = true

        var lastUserPlaylistRequest: UserPlaylistRequest? = null

        override suspend fun getUserPlaylists(body: UserPlaylistRequest): UserPlaylistResponse {
            lastUserPlaylistRequest = body
            return UserPlaylistResponse(code = 200, playlist = emptyList())
        }

        override suspend fun updatePlaylistOrder(body: PlaylistOrderUpdateRequest): PlaylistOrderUpdateResponse {
            lastOrderRequest = body
            return if (shouldSucceed) {
                PlaylistOrderUpdateResponse(code = 200)
            } else {
                PlaylistOrderUpdateResponse(code = 400, message = "更新失败")
            }
        }

        var lastTopId: Long? = null
        var lastCancelTopId: Long? = null

        override suspend fun topPlaylist(body: PlaylistTopRequest): PlaylistTopResponse {
            lastTopId = body.id
            return if (shouldSucceed) {
                PlaylistTopResponse(code = 200)
            } else {
                PlaylistTopResponse(code = 400, message = "置顶失败")
            }
        }

        override suspend fun cancelTopPlaylist(body: PlaylistTopRequest): PlaylistTopResponse {
            lastCancelTopId = body.id
            return if (shouldSucceed) {
                PlaylistTopResponse(code = 200)
            } else {
                PlaylistTopResponse(code = 400, message = "取消置顶失败")
            }
        }
    }

    @Test
    fun `updatePlaylistOrder 正常序列化歌单列表并成功返回`() = runTest {
        val fakeApi = FakeUserPlaylistApi()
        val repository = UserPlaylistRepositoryImpl(fakeApi, testOfflineFallback())

        val result = repository.updatePlaylistOrder(listOf(101L, 102L, 103L)).first()

        assertTrue(result.isSuccess)
        assertEquals("[101,102,103]", fakeApi.lastOrderRequest?.ids)
    }

    @Test
    fun `updatePlaylistOrder 服务端返回错误时输出 failure`() = runTest {
        val fakeApi = FakeUserPlaylistApi().apply { shouldSucceed = false }
        val repository = UserPlaylistRepositoryImpl(fakeApi, testOfflineFallback())

        val result = repository.updatePlaylistOrder(listOf(101L)).first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `setPlaylistTop 置顶与取消置顶均正确调用对应接口`() = runTest {
        val fakeApi = FakeUserPlaylistApi()
        val repository = UserPlaylistRepositoryImpl(fakeApi, testOfflineFallback())

        val topResult = repository.setPlaylistTop(101L, true).first()
        assertTrue(topResult.isSuccess)
        assertEquals(101L, fakeApi.lastTopId)

        val cancelResult = repository.setPlaylistTop(101L, false).first()
        assertTrue(cancelResult.isSuccess)
        assertEquals(101L, fakeApi.lastCancelTopId)
    }

    @Test
    fun `getUserPlaylists 默认携带 includeTop 标志`() = runTest {
        val fakeApi = FakeUserPlaylistApi()
        val repository = UserPlaylistRepositoryImpl(fakeApi, testOfflineFallback())

        val result = repository.getUserPlaylists(1001L, limit = 50).first()

        assertTrue(result.isSuccess)
        assertEquals(true, fakeApi.lastUserPlaylistRequest?.includeTop)
        assertEquals(1001L, fakeApi.lastUserPlaylistRequest?.uid)
        assertEquals(50, fakeApi.lastUserPlaylistRequest?.limit)
    }
}
