package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Granite 4.0 chat_template.jinja 골든과 바이트 비교.
 * 골든 재생성: python3 tools/template/gen_goldens_granite.py > app/src/test/resources/granite_template_goldens.json
 */
class GranitePromptBuilderTest {

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
        return ChatMessage(role, m["content"] as String? ?: "", null, calls)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun matchesJinjaGoldens() {
        val text = javaClass.classLoader!!.getResource("granite_template_goldens.json")!!.readText()
        val cases = MiniJson(text).parse() as List<Map<String, Any?>>
        assertTrue(cases.size >= 9)
        for (c in cases) {
            val input = c["input"] as Map<String, Any?>
            val msgs = (input["messages"] as List<Map<String, Any?>>).map(::toMessage)
            val tools = (input["tools"] as List<Map<String, Any?>>?).orEmpty()
            val agp = input["add_generation_prompt"] as Boolean
            assertEquals("case ${c["name"]}", c["expected"], GranitePromptBuilder.render(msgs, tools, agp))
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
        val segs = GranitePromptBuilder.segments(msgs)
        val i = segs.indexOfFirst { it.tokens != null }
        assertEquals("<|start_of_role|>assistant<|end_of_role|>", segs[i - 1].text)
        assertEquals("<|end_of_text|>\n", segs[i + 1].text)
        assertEquals(3, segs.count { it.boundaryAfter }) // 머리, assistant 끝, 툴 묶음 끝
        assertEquals(GranitePromptBuilder.GENERATION_PROMPT, segs.last().text)
        // 토큰 대신 텍스트로 렌더한 것과 같은 바이트열
        val asText = GranitePromptBuilder.segments(msgs.map { it.copy(rawTokens = null) }).joinToString("") { it.text!! }
        assertEquals(GranitePromptBuilder.render(msgs), asText)
    }

    @Test
    fun parsesToolCalls() {
        val out = "확인할게요.\n<tool_call>\n{\"name\": \"web_search\", \"arguments\": {\"query\": \"서울 날씨\", \"max_results\": 3}}\n</tool_call>\n" +
            "<tool_call>\n{\"name\": \"calculator\", \"arguments\": {\"expression\": \"2*3\"}}" // EOS 로 닫는 태그 없이 끝남
        val r = GraniteToolCallParser.parse(out)
        assertEquals("확인할게요.", r.text)
        assertEquals(listOf("web_search", "calculator"), r.calls.map { it.name })
        assertEquals("서울 날씨", r.calls[0].arguments["query"])
        assertEquals(3L, r.calls[0].arguments["max_results"])
        assertEquals("확인할게요.", GraniteToolCallParser.visiblePrefix(out))
        // 렌더 → 파싱 왕복
        val rendered = GranitePromptBuilder.renderAssistant(ChatMessage(Role.ASSISTANT, "", toolCalls = r.calls))
        assertEquals(r.calls, GraniteToolCallParser.parse(rendered).calls)
        // 깨진 JSON: 이름만 살린다
        assertEquals("tap", GraniteToolCallParser.parse("<tool_call>{\"name\": \"tap\", \"arguments\": {\"index\": </tool_call>").calls.single().name)
        assertTrue(GraniteToolCallParser.parse("그냥 답").calls.isEmpty())
    }

    @Test
    fun toolGrammar() {
        val (g, trigger) = ModelFamily.GRANITE.toolGrammar(listOf("web_search", "calculator"))!!
        assertTrue(g.contains("name ::= \"web_search\" | \"calculator\""))
        assertTrue(g.contains("\"\\\\\" [\"\\\\/bfnrt]")) // \u 이스케이프 금지
        assertTrue(Regex(trigger).matches("확인할게요.\n<tool_call>\n{"))
        assertEquals(null, ModelFamily.LING.toolGrammar(listOf("x")))
        // 호스트 실모델 시험(ToolCallProbe -Dgrammar=)용으로 남김
        java.io.File("build/granite_tool_grammar.gbnf").writeText(GraniteToolGrammar.build(listOf("calculator", "current_time", "web_search", "fetch_url")))
    }

    @Test
    fun familyBasics() {
        assertEquals(ModelFamily.GRANITE, ModelFamily.detect("granite-4.0-h-tiny-Q4_0.gguf"))
        assertEquals(0.0f, ModelFamily.GRANITE.samplingFor(true).temperature)
        assertTrue(ModelFamily.GRANITE.probePrompt().endsWith("<|start_of_role|>assistant<|end_of_role|>"))
    }
}
