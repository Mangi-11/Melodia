package com.lin0721.linmusic.desktop.ui.comments

import com.lin0721.linmusic.core.comment.domain.CommentComposerState
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentDraftTest {

    private val pending = CommentDraft(text = "草稿", submissionPending = true)

    @Test
    fun `本框发起的提交成功后清空草稿`() {
        assertEquals(CommentDraft(), resolveDraft(pending, CommentComposerState.Idle))
    }

    @Test
    fun `提交失败时保留草稿并结束等待`() {
        assertEquals(CommentDraft("草稿", false), resolveDraft(pending, CommentComposerState.Failed))
    }

    @Test
    fun `提交中保持不变`() {
        assertEquals(pending, resolveDraft(pending, CommentComposerState.Submitting))
    }

    @Test
    fun `非本框发起的状态变化不动草稿`() {
        val idle = CommentDraft(text = "另一个框里的草稿")
        assertEquals(idle, resolveDraft(idle, CommentComposerState.Idle))
        assertEquals(idle, resolveDraft(idle, CommentComposerState.Failed))
    }
}
