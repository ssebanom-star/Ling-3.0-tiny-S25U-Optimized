package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test
    fun thinkParserSplitsAcrossPieceBoundaries() {
        val p = ThinkParser(startInThink = true)
        val pieces = listOf("step ", "one</th", "ink", ">The answer", " is 42", "<thi", "nk>x</think>!")
        val r = StringBuilder(); val c = StringBuilder()
        for (x in pieces) { val d = p.feed(x); r.append(d.reasoning); c.append(d.content) }
        val f = p.finish(); r.append(f.reasoning); c.append(f.content)
        assertEquals("step onex", r.toString())
        assertEquals("The answer is 42!", c.toString())
        assertEquals("The answer is 42!", p.content)
        assertFalse(p.isThinking)
    }

    @Test
    fun thinkParserOffMode() {
        val p = ThinkParser(startInThink = false)
        val d = p.feed("hello <")
        assertEquals("hello ", d.content)
        assertEquals("<", p.finish().content)
    }

    @Test
    fun toolCallParser() {
        val text = "Let me check.\n<tool_call>get_weather\n<arg_key>city</arg_key>\n<arg_value>서울</arg_value>" +
            "<arg_key>days</arg_key>\n<arg_value>2</arg_value>\n</tool_call>"
        val r = ToolCallParser.parse(text)
        assertEquals("Let me check.", r.text)
        assertEquals(1, r.calls.size)
        assertEquals("get_weather", r.calls[0].name)
        assertEquals(linkedMapOf<String, Any?>("city" to "서울", "days" to "2"), r.calls[0].arguments)
        // 재렌더링 왕복
        val msg = ChatMessage(Role.ASSISTANT, r.text, toolCalls = r.calls)
        assertTrue(LingPromptBuilder.renderAssistant(msg).contains(text.substringAfter("\n")))
    }

    @Test
    fun visiblePrefixHidesOpenToolCall() {
        assertEquals("abc ", ToolCallParser.visiblePrefix("abc <tool_call>calc\n<arg_k"))
    }

    @Test
    fun pyJsonMatchesPythonFormat() {
        val m = linkedMapOf<String, Any?>("a" to 1, "b" to listOf("x", true, null), "c" to "한\"\n", "d" to 2.0)
        assertEquals("{\"a\": 1, \"b\": [\"x\", true, null], \"c\": \"한\\\"\\n\", \"d\": 2.0}", PyJson.dumps(m))
    }
}
