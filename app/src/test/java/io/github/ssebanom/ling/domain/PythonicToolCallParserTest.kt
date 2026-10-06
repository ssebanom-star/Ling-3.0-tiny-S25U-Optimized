package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonicToolCallParserTest {
    @Test fun parsesPythonicCalls() {
        val r = PythonicToolCallParser.parse(
            "<|tool_call_start|>[web_search(query='서울 날씨', max_results=3), tap(index=2, long=False, x=1.5, o={\"a\": [1, 'b']}, n=None)]<|tool_call_end|>확인 중입니다.",
        )
        assertEquals("확인 중입니다.", r.text)
        assertEquals(2, r.calls.size)
        assertEquals("web_search", r.calls[0].name)
        assertEquals("서울 날씨", r.calls[0].arguments["query"])
        assertEquals(3L, r.calls[0].arguments["max_results"])
        val a = r.calls[1].arguments
        assertEquals(false, a["long"]); assertEquals(1.5, a["x"]); assertEquals(null, a["n"])
        assertEquals(mapOf("a" to listOf(1L, "b")), a["o"])
    }

    @Test fun parsesDoubleQuotesEscapesAndNoArgs() {
        val r = PythonicToolCallParser.parse("<|tool_call_start|>[f(s=\"it's \\\"q\\\"\\n\"), g()]<|tool_call_end|>")
        assertEquals("it's \"q\"\n", r.calls[0].arguments["s"])
        assertEquals("g", r.calls[1].name)
        assertTrue(r.calls[1].arguments.isEmpty())
    }

    @Test fun parsesJsonStyle() {
        val r = PythonicToolCallParser.parse("<|tool_call_start|>[{\"name\": \"f\", \"arguments\": {\"a\": 1}}]<|tool_call_end|>")
        assertEquals("f", r.calls.single().name)
        assertEquals(1L, r.calls.single().arguments["a"])
    }

    @Test fun roundTripThroughTemplateFormat() {
        val calls = listOf(ToolCall("calc", linkedMapOf("expr" to "3*4", "flag" to true, "n" to 2L)))
        val back = PythonicToolCallParser.parse(LfmPromptBuilder.renderToolCalls(calls)).calls
        assertEquals(calls, back)
    }

    @Test fun visibleHidesCallMarkup() {
        assertEquals("앞 ", PythonicToolCallParser.visiblePrefix("앞 <|tool_call_start|>[f(a="))
        assertEquals("앞  뒤", PythonicToolCallParser.visiblePrefix("앞 <|tool_call_start|>[f()]<|tool_call_end|> 뒤"))
    }

    @Test fun malformedYieldsNoCalls() {
        val r = PythonicToolCallParser.parse("<|tool_call_start|>[f(a=]<|tool_call_end|>x")
        assertTrue(r.calls.isEmpty())
        assertEquals("x", r.text)
    }
}
