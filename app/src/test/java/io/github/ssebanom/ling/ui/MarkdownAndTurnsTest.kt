package io.github.ssebanom.ling.ui

import io.github.ssebanom.ling.data.StoredMessage
import io.github.ssebanom.ling.domain.ChatMessage
import io.github.ssebanom.ling.domain.Role
import io.github.ssebanom.ling.domain.ToolCall
import io.github.ssebanom.ling.ui.chat.ChatRow
import io.github.ssebanom.ling.ui.chat.ChatTurns
import io.github.ssebanom.ling.ui.markdown.LatexNormalizer
import io.github.ssebanom.ling.ui.markdown.MdBlock
import io.github.ssebanom.ling.ui.markdown.MdBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAndTurnsTest {
    @Test fun splitsCodeAndParagraphs() {
        val b = MdBlocks.split("Intro\n\n```kotlin\nval x = 1\n\nval y = 2\n```\nAfter\n\n- a\n- b")
        assertEquals(4, b.size)
        assertEquals(MdBlock.Code("kotlin", "val x = 1\n\nval y = 2", closed = true), b[1])
        assertEquals(MdBlock.Text("After"), b[2])
    }

    @Test fun unclosedFenceWhileStreaming() {
        val b = MdBlocks.split("text\n```py\nprint(1)")
        assertEquals(MdBlock.Code("py", "print(1)", closed = false), b.last())
    }

    @Test fun mathBlockKeptWhole() {
        val b = MdBlocks.split("$$\na\n\nb\n$$\n\nnext")
        assertEquals(2, b.size)
    }

    @Test fun indentedContinuationMergesIntoList() {
        val b = MdBlocks.split("1. item\n\n    more of item\n\nPara")
        assertEquals(2, b.size)
        assertTrue((b[0] as MdBlock.Text).md.contains("more of item"))
    }

    @Test fun latexDelimiters() {
        assertEquals("값은 $\$x^2$$ 이다", LatexNormalizer.normalize("값은 \\(x^2\\) 이다"))
        assertEquals("값은 $\$x^2$$ 이다", LatexNormalizer.normalize("값은 \$x^2\$ 이다"))
        assertEquals("a\n$$\n\\int_0^1 x\\,dx\n$$\nb", LatexNormalizer.normalize("a\\[\\int_0^1 x\\,dx\\]b"))
        // 통화와 코드는 그대로
        assertEquals("costs \$5 and \$10", LatexNormalizer.normalize("costs \$5 and \$10"))
        assertEquals("`\$HOME\$`", LatexNormalizer.normalize("`\$HOME\$`"))
        // 단독 줄 $$..$$ 는 블록으로
        assertEquals("$$\nE=mc^2\n$$", LatexNormalizer.normalize("$\$E=mc^2$$"))
    }

    private fun sm(role: Role, content: String, calls: List<ToolCall> = emptyList()) =
        StoredMessage(0, ChatMessage(role, content, toolCalls = calls), null)

    @Test fun turnsPairToolResults() {
        val msgs = listOf(
            sm(Role.USER, "q"),
            sm(Role.ASSISTANT, "", listOf(ToolCall("web_search", linkedMapOf<String, Any?>("query" to "x")), ToolCall("calculator", linkedMapOf<String, Any?>("expression" to "1+1")))),
            sm(Role.TOOL, "r1"), sm(Role.TOOL, "2"),
            sm(Role.ASSISTANT, "answer"),
            sm(Role.USER, "q2"),
        )
        val rows = ChatTurns.rows(7, msgs, busy = true)
        assertEquals(listOf("7-u-0", "7-a-0", "7-u-5", "7-a-5"), rows.map { it.key })
        val turn = rows[1] as ChatRow.Turn
        val steps = ChatTurns.steps(turn.entries)
        assertEquals(2, steps.size)
        assertEquals(listOf("r1", "2"), steps[0].tools.map { it.result })
        assertTrue((rows[3] as ChatRow.Turn).live)
        assertTrue((rows[3] as ChatRow.Turn).isLast)
    }

    @Test fun runningToolHasNoResult() {
        val msgs = listOf(sm(Role.USER, "q"), sm(Role.ASSISTANT, "", listOf(ToolCall("fetch_url", linkedMapOf<String, Any?>("url" to "u")))))
        val steps = ChatTurns.steps((ChatTurns.rows(1, msgs, true)[1] as ChatRow.Turn).entries)
        assertEquals(null, steps[0].tools[0].result)
    }
}
