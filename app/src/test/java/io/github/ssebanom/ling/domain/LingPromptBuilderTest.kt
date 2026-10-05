package io.github.ssebanom.ling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * chat_template.jinja(HF 환경 재현)로 만든 골든과 Kotlin 이식 결과를 바이트 단위로 비교.
 * 골든 재생성: python3 tools/template/gen_goldens.py > app/src/test/resources/template_goldens.json
 */
class LingPromptBuilderTest {

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
        val text = javaClass.classLoader!!.getResource("template_goldens.json")!!.readText()
        val cases = MiniJson(text).parse() as List<Map<String, Any?>>
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val input = c["input"] as Map<String, Any?>
            val msgs = (input["messages"] as List<Map<String, Any?>>).map(::toMessage)
            val tools = (input["tools"] as List<Map<String, Any?>>?).orEmpty()
            val thinking = input["enable_thinking"] as Boolean? ?: true
            val agp = input["add_generation_prompt"] as Boolean
            val actual = LingPromptBuilder.render(msgs, tools, thinking, agp)
            assertEquals("case ${c["name"]}", c["expected"], actual)
        }
    }

    @Test
    fun segmentsConcatenateToRender() {
        val msgs = listOf(
            ChatMessage(Role.SYSTEM, "S"),
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.ASSISTANT, "yo", reasoning = "r"),
            ChatMessage(Role.USER, "again"),
        )
        val segs = LingPromptBuilder.segments(msgs, thinking = true)
        assertEquals(LingPromptBuilder.render(msgs), segs.joinToString("") { it.text!! })
        // 경계: system 끝, assistant 끝
        assertEquals(2, segs.count { it.boundaryAfter })
    }

    @Test
    fun rawTokensReplaceAssistantText() {
        val raw = intArrayOf(1, 2, 3)
        val msgs = listOf(
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.ASSISTANT, "yo", rawTokens = raw, thinkingAtGeneration = false),
            ChatMessage(Role.USER, "again"),
        )
        val segs = LingPromptBuilder.segments(msgs, thinking = false)
        // system, user, genPrompt, tokens, role_end, user, genPrompt
        assertEquals(7, segs.size)
        assertEquals("<role>ASSISTANT</role>\n<think></think>", segs[2].text)
        assertTrue(segs[3].tokens!!.contentEquals(raw))
        assertEquals("<|role_end|>", segs[4].text)
        assertTrue(segs[4].boundaryAfter)
        // 생성 당시 프롬프트 = 이전 턴의 [system, user, genPrompt] 조각과 동일 분할
        val prev = LingPromptBuilder.segments(msgs.take(1), thinking = false)
        assertEquals(prev, segs.take(3))
    }
}
