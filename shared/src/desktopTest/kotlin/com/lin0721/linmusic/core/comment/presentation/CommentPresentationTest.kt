package com.lin0721.linmusic.core.comment.presentation

import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.BeRepliedComment
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.core.model.CommentUser
import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting
import com.lin0721.linmusic.feature.player.presentation.visibleInfoCards
import com.lin0721.linmusic.feature.player.ui.PlayerSongDetailState
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentFormatTest {

    private fun comment(id: Long) = CommentItem(commentId = id)

    private fun success(hot: List<CommentItem>, normal: List<CommentItem>, total: Int = 10) =
        CommentsState.Success(hotComments = hot, comments = normal, total = total)

    @Test
    fun `点赞数按量级缩写`() {
        assertEquals("999", formatLikedCount(999))
        assertEquals("1k+", formatLikedCount(1_000))
        assertEquals("9k+", formatLikedCount(9_999))
        assertEquals("1.0w", formatLikedCount(10_000))
        assertEquals("1.5w", formatLikedCount(15_000))
        assertEquals("10w+", formatLikedCount(100_000))
    }

    @Test
    fun `评论流热评在前并按id去重`() {
        val state = success(hot = listOf(comment(1), comment(2)), normal = listOf(comment(2), comment(3)))
        assertEquals(listOf(1L, 2L, 3L), allComments(state).map { it.commentId })
    }

    @Test
    fun `预览只取前两条`() {
        val state = success(hot = listOf(comment(1)), normal = listOf(comment(2), comment(3)))
        assertEquals(listOf(1L, 2L), previewComments(state).map { it.commentId })
    }

    @Test
    fun `被回复引用文案带昵称与内容，缺失项当空串`() {
        val quoted = BeRepliedComment(user = CommentUser(nickname = "小明"), content = "原话")
        assertEquals("回复 小明：原话", quotedReplyText(quoted))
        assertEquals("回复 ：", quotedReplyText(BeRepliedComment()))
    }

    @Test
    fun `评论卡在加载中时挡住后面的卡片，失败或完成后出现`() {
        val order = listOf(
            FullPlayerCardSetting(FullPlayerCard.COMMENTS_PREVIEW, visible = true),
            FullPlayerCardSetting(FullPlayerCard.LYRICS, visible = true)
        )
        val state = PlayerSongDetailState(lyrics = listOf(com.lin0721.linmusic.core.player.domain.LyricLine(timeMs = 0, text = "词")))
        assertEquals(emptyList<FullPlayerCard>(), visibleInfoCards(state, order, commentsState = CommentsState.Loading()))
        val expected = listOf(FullPlayerCard.COMMENTS_PREVIEW, FullPlayerCard.LYRICS)
        assertEquals(expected, visibleInfoCards(state, order, commentsState = success(emptyList(), emptyList(), 0)))
        assertEquals(expected, visibleInfoCards(state, order, commentsState = CommentsState.Error("失败")))
    }
}
