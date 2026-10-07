package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qwen3.6 chat_template.jinja 골든과 바이트 비교.
 * 골든 재생성: python3 tools/template/gen_goldens_qwen.py > app/src/test/resources/qwen_template_goldens.json
 */
class QwenPromptBuilderTest {

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
        val text = javaClass.classLoader!!.getResource("qwen_template_goldens.json")!!.readText()
        val cases = MiniJson(text).parse() as List<Map<String, Any?>>
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val input = c["input"] as Map<String, Any?>
            val msgs = (input["messages"] as List<Map<String, Any?>>).map(::toMessage)
            val tools = (input["tools"] as List<Map<String, Any?>>?).orEmpty()
            val thinking = input["enable_thinking"] as Boolean? ?: true
            val preserve = input["preserve_thinking"] as Boolean? ?: false
            val agp = input["add_generation_prompt"] as Boolean
            assertEquals("case ${c["name"]}", c["expected"], QwenPromptBuilder.render(msgs, tools, thinking, preserve, agp))
        }
    }

    @Test
    fun segmentsWithoutRawTokensEqualPreserveRender() {
        val msgs = listOf(
            ChatMessage(Role.SYSTEM, "S"),
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.ASSISTANT, "yo", reasoning = "r", toolCalls = listOf(ToolCall("f", linkedMapOf("a" to "x")))),
            ChatMessage(Role.TOOL, "1"),
            ChatMessage(Role.TOOL, "2"),
            ChatMessage(Role.USER, "again"),
        )
        val segs = QwenPromptBuilder.segments(msgs, thinking = false)
        assertEquals(QwenPromptBuilder.render(msgs, thinking = false, preserveThinking = true), segs.joinToString("") { it.text!! })
        assertEquals(3, segs.count { it.boundaryAfter })
    }

    @Test
    fun rawTokensUseGenerationPromptOfThatTurn() {
        val msgs = listOf(
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.ASSISTANT, "yo", rawTokens = intArrayOf(7), thinkingAtGeneration = false),
            ChatMessage(Role.USER, "again"),
        )
        val segs = QwenPromptBuilder.segments(msgs, thinking = true)
        val i = segs.indexOfFirst { it.tokens != null }
        assertEquals("<|im_start|>assistant\n<think>\n\n</think>\n\n", segs[i - 1].text)
        assertEquals("<|im_end|>\n", segs[i + 1].text)
        assertEquals("<|im_start|>assistant\n<think>\n", segs.last().text)
    }

    @Test
    fun parsesToolCalls() {
        val out = "확인할게요.\n\n<tool_call>\n<function=web_search>\n<parameter=query>\n서울 날씨\n</parameter>\n" +
            "<parameter=max_results>\n3\n</parameter>\n</function>\n</tool_call>\n<tool_call>\n<function=current_time>\n</function>\n</tool_call>"
        val r = QwenToolCallParser.parse(out)
        assertEquals("확인할게요.", r.text)
        assertEquals(listOf("web_search", "current_time"), r.calls.map { it.name })
        assertEquals("서울 날씨", r.calls[0].arguments["query"])
        assertEquals("3", r.calls[0].arguments["max_results"])
        assertEquals("확인할게요.", QwenToolCallParser.visiblePrefix(out))
        // 렌더 → 파싱 왕복(문자열 인자)
        val calls = listOf(ToolCall("f", linkedMapOf("a" to "줄1\n줄2", "b" to "x")))
        val rendered = QwenPromptBuilder.renderAssistant(ChatMessage(Role.ASSISTANT, "", toolCalls = calls), keepThinking = false)
        assertEquals(calls, QwenToolCallParser.parse(rendered).calls)
    }

    @Test
    fun familyAndSampling() {
        assertEquals(ModelFamily.QWEN36, ModelFamily.detect("Qwen_Qwen3.6-35B-A3B-IQ1_M.gguf"))
        assertEquals(1.5f, ModelFamily.QWEN36.samplingFor(true).presencePenalty)
        assertEquals(0.7f, ModelFamily.QWEN36.samplingFor(false).temperature)
        assertTrue(ModelFamily.QWEN36.probePrompt().endsWith("<think>\n\n</think>\n\n"))
    }
}
