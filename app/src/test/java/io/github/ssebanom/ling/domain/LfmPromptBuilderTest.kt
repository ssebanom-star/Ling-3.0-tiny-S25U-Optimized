package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LFM2.5 chat_template.jinja 골든과 바이트 비교.
 * 골든 재생성: python3 tools/template/gen_goldens_lfm.py > app/src/test/resources/lfm_template_goldens.json
 */
class LfmPromptBuilderTest {

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
        val text = javaClass.classLoader!!.getResource("lfm_template_goldens.json")!!.readText()
        val cases = MiniJson(text).parse() as List<Map<String, Any?>>
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val input = c["input"] as Map<String, Any?>
            val msgs = (input["messages"] as List<Map<String, Any?>>).map(::toMessage)
            val tools = (input["tools"] as List<Map<String, Any?>>?).orEmpty()
            val preserve = input["preserve_thinking"] as Boolean? ?: false
            val agp = input["add_generation_prompt"] as Boolean
            assertEquals("case ${c["name"]}", c["expected"], LfmPromptBuilder.render(msgs, tools, preserve, agp))
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
        val segs = LfmPromptBuilder.segments(msgs)
        assertEquals(LfmPromptBuilder.render(msgs, preserveThinking = true), segs.joinToString("") { it.text!! })
        // 경계: 머리(system) 끝, assistant 끝, 툴 묶음 끝
        assertEquals(3, segs.count { it.boundaryAfter })
    }

    @Test
    fun rawTokensReplaceAssistantText() {
        val msgs = listOf(
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.ASSISTANT, "yo", rawTokens = intArrayOf(1, 2, 3), thinkingAtGeneration = true),
            ChatMessage(Role.USER, "again"),
        )
        val segs = LfmPromptBuilder.segments(msgs)
        val i = segs.indexOfFirst { it.tokens != null }
        assertEquals(LfmPromptBuilder.GENERATION_PROMPT, segs[i - 1].text)
        assertEquals("<|im_end|>\n", segs[i + 1].text)
        assertTrue(segs[i + 1].boundaryAfter)
    }

    @Test
    fun familyDetection() {
        assertEquals(ModelFamily.LFM2, ModelFamily.detect("LFM2.5-8B-A1B-Q4_0.gguf"))
        assertEquals(ModelFamily.LING, ModelFamily.detect("Ling-3.0-tiny-Q4_0.gguf"))
        assertTrue(ModelFamily.LFM2.probePrompt().startsWith("<|startoftext|><|im_start|>user\n"))
        assertTrue(ModelFamily.LING.probePrompt().endsWith("<role>ASSISTANT</role>\n<think></think>"))
    }
}
