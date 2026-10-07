package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K2-Horizon chat_template.jinja(tool_presentation_format=json) 골든과 바이트 비교.
 * 골든 재생성: python3 tools/template/gen_goldens_k2h.py > app/src/test/resources/k2h_template_goldens.json
 */
class K2hPromptBuilderTest {

    @Suppress("UNCHECKED_CAST")
    private fun toMessage(m: Map<String, Any?>): ChatMessage {
        val role = when (m["role"]) {
            "system" -> Role.SYSTEM
            "user" -> Role.USER
            "assistant" -> Role.ASSISTANT
            "tool" -> Role.TOOL
            else -> error("role")
        }
        val calls = (m["tool_calls"] as List<Map<String, Any?>>?).orEmpty().map { tc ->
            val f = (tc["function"] ?: tc) as Map<String, Any?>
            ToolCall(f["name"] as String, LinkedHashMap(f["arguments"] as Map<String, Any?>))
        }
        return ChatMessage(role, m["content"] as String? ?: "", m["reasoning_content"] as String?, calls)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun matchesJinjaGoldens() {
        val text = javaClass.classLoader!!.getResource("k2h_template_goldens.json")!!.readText()
        val cases = MiniJson(text).parse() as List<Map<String, Any?>>
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val input = c["input"] as Map<String, Any?>
            val msgs = (input["messages"] as List<Map<String, Any?>>).map(::toMessage)
            val tools = (input["tools"] as List<Map<String, Any?>>?).orEmpty()
            val thinking = (input["reasoning_effort"] as String? ?: "high") == "high"
            val agp = input["add_generation_prompt"] as Boolean
            assertEquals("case ${c["name"]}", c["expected"], K2hPromptBuilder.render(msgs, tools, thinking, agp))
        }
    }

    @Test
    fun rawTokensAndBoundaries() {
        val msgs = listOf(
            ChatMessage(Role.SYSTEM, "S"),
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.ASSISTANT, "yo", rawTokens = intArrayOf(5), thinkingAtGeneration = false),
            ChatMessage(Role.TOOL, "1"),
            ChatMessage(Role.TOOL, "2"),
            ChatMessage(Role.USER, "again"),
        )
        val segs = K2hPromptBuilder.segments(msgs, thinking = true)
        val i = segs.indexOfFirst { it.tokens != null }
        assertEquals("<|ifm|im_start|>assistant\n<ifm|think_faster>\n", segs[i - 1].text)
        assertEquals("<|ifm|im_end|>", segs[i + 1].text)
        assertEquals(3, segs.count { it.boundaryAfter }) // 머리, assistant 끝, 툴 묶음 끝
        assertEquals("<|ifm|im_start|>assistant\n<ifm|think>\n", segs.last().text)
    }

    @Test
    fun parsesToolCallsAndThinkTags() {
        val out = "</ifm|think_faster>확인합니다.<ifm|tool_calls>\n<ifm|tool_call>web_search\n<ifm|arg_key>query</ifm|arg_key>\n" +
            "<ifm|arg_value>서울 날씨</ifm|arg_value>\n<ifm|arg_key>max_results</ifm|arg_key>\n<ifm|arg_value>3</ifm|arg_value>\n" +
            "</ifm|tool_call>\n</ifm|tool_calls>"
        val (open, close) = ModelFamily.K2H.thinkTags(false)
        val p = ThinkParser(startInThink = true, openTag = open, closeTag = close)
        out.chunked(7).forEach { p.feed(it) }
        p.finish()
        assertEquals("", p.reasoning)
        val r = K2hToolCallParser.parse(p.content)
        assertEquals("확인합니다.", r.text)
        assertEquals("web_search", r.calls.single().name)
        assertEquals("서울 날씨", r.calls.single().arguments["query"])
        assertEquals("3", r.calls.single().arguments["max_results"])
        assertEquals("확인합니다.", K2hToolCallParser.visiblePrefix(p.content))
        // 렌더 → 파싱 왕복
        val rendered = K2hPromptBuilder.renderAssistant(ChatMessage(Role.ASSISTANT, "", toolCalls = r.calls))
        assertEquals(r.calls, K2hToolCallParser.parse(rendered).calls)
    }

    @Test
    fun familyBasics() {
        assertEquals(ModelFamily.K2H, ModelFamily.detect("K2-Horizon-4B-Q4_K_M.gguf"))
        assertEquals(8192, ModelFamily.K2H.ctxCap)
        assertTrue(ModelFamily.K2H.probePrompt().endsWith("</ifm|think_faster>The capital of South Korea is"))
    }
}
