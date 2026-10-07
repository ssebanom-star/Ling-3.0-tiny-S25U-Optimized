package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/**
 * Qwen3.6 (Qwen/Qwen3.6-35B-A3B) chat_template.jinja 의 Kotlin 이식(텍스트 전용 경로).
 *
 * - [render]: 템플릿과 바이트 단위 일치 (tools/template/gen_goldens_qwen.py 골든으로 검증)
 * - [segments]: 엔진용 조각. 앱은 preserve_thinking=true 로 쓴다 — 지난 턴의 추론을 지우지 않아
 *   모델이 생성한 토큰(`추론\n</think>\n\n답`)을 그대로 다시 넣어도 템플릿 렌더 결과와 같은 형태가 된다.
 */
object QwenPromptBuilder {
    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"

    private const val TOOLS_HEAD = "# Tools\n\nYou have access to the following functions:\n\n<tools>"
    private const val TOOLS_TAIL = "\n</tools>\n\nIf you choose to call a function ONLY reply in the following format with NO suffix:\n\n" +
        "<tool_call>\n<function=example_function_name>\n<parameter=example_parameter_1>\nvalue_1\n</parameter>\n" +
        "<parameter=example_parameter_2>\nThis is the value for the second parameter\nthat can span\nmultiple lines\n</parameter>\n" +
        "</function>\n</tool_call>\n\n<IMPORTANT>\nReminder:\n- Function calls MUST follow the specified format: an inner " +
        "<function=...></function> block must be nested within <tool_call></tool_call> XML tags\n- Required parameters MUST be specified\n" +
        "- You may provide optional reasoning for your function call in natural language BEFORE the function call, but NOT after\n" +
        "- If there is no function call available, answer the question like normal with your current knowledge and do not tell the user about function calls\n" +
        "</IMPORTANT>"

    fun generationPrompt(thinking: Boolean): String =
        IM_START + "assistant\n" + if (thinking) "<think>\n" else "<think>\n\n</think>\n\n"

    fun render(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        thinking: Boolean = true,
        preserveThinking: Boolean = false,
        addGenerationPrompt: Boolean = true,
    ): String = buildParts(messages, tools, thinking, preserveThinking, addGenerationPrompt, useRawTokens = false)
        .joinToString("") { it.text!! }

    fun segments(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        thinking: Boolean = true,
        addGenerationPrompt: Boolean = true,
    ): List<PromptSegment> = buildParts(messages, tools, thinking, preserveThinking = true, addGenerationPrompt, useRawTokens = true)

    private fun buildParts(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>>,
        thinking: Boolean,
        preserveThinking: Boolean,
        addGenerationPrompt: Boolean,
        useRawTokens: Boolean,
    ): List<PromptSegment> {
        val parts = ArrayList<PromptSegment>()
        val first = messages.firstOrNull()
        val head = StringBuilder()
        if (tools.isNotEmpty()) {
            head.append(IM_START).append("system\n").append(TOOLS_HEAD)
            for (t in tools) head.append('\n').append(PyJson.dumps(t))
            head.append(TOOLS_TAIL)
            if (first?.role == Role.SYSTEM) {
                val c = first.content.trim()
                if (c.isNotEmpty()) head.append("\n\n").append(c)
            }
            head.append(IM_END).append('\n')
        } else if (first?.role == Role.SYSTEM) {
            head.append(IM_START).append("system\n").append(first.content.trim()).append(IM_END).append('\n')
        }
        if (head.isNotEmpty()) parts += PromptSegment(text = head.toString(), boundaryAfter = true)

        // 마지막 "진짜" 사용자 질문(툴 응답으로만 된 user 제외)
        val lastQuery = messages.indices.reversed().firstOrNull { i ->
            val m = messages[i]
            m.role == Role.USER && m.content.trim().let { !(it.startsWith("<tool_response>") && it.endsWith("</tool_response>")) }
        } ?: messages.lastIndex

        messages.forEachIndexed { i, m ->
            when (m.role) {
                Role.SYSTEM -> Unit // 첫 메시지만 허용(템플릿은 중간 system 에서 예외) — 헤더에서 처리
                Role.USER -> parts += PromptSegment(text = IM_START + "user\n" + m.content.trim() + IM_END + "\n")
                Role.ASSISTANT -> {
                    val raw = m.rawTokens
                    if (useRawTokens && raw != null && m.thinkingAtGeneration != null) {
                        parts += PromptSegment(text = generationPrompt(m.thinkingAtGeneration))
                        if (raw.isNotEmpty()) parts += PromptSegment(tokens = raw)
                        parts += PromptSegment(text = "$IM_END\n", boundaryAfter = true)
                    } else {
                        parts += PromptSegment(text = renderAssistant(m, preserveThinking || i > lastQuery), boundaryAfter = true)
                    }
                }
                Role.TOOL -> {
                    val sb = StringBuilder()
                    if (i > 0 && messages[i - 1].role != Role.TOOL) sb.append(IM_START).append("user")
                    sb.append("\n<tool_response>\n").append(m.content.trim()).append("\n</tool_response>")
                    val lastOfGroup = i == messages.lastIndex || messages[i + 1].role != Role.TOOL
                    if (lastOfGroup) sb.append(IM_END).append('\n')
                    parts += PromptSegment(text = sb.toString(), boundaryAfter = lastOfGroup)
                }
            }
        }
        if (addGenerationPrompt) parts += PromptSegment(text = generationPrompt(thinking))
        return parts
    }

    fun renderAssistant(m: ChatMessage, keepThinking: Boolean): String {
        var content = m.content.trim()
        var reasoning: String
        if (m.reasoning != null) {
            reasoning = m.reasoning
        } else if (content.contains("</think>")) {
            reasoning = content.split("</think>")[0].trimEnd('\n').split("<think>").last().trimStart('\n')
            content = content.split("</think>").last().trimStart('\n')
        } else {
            reasoning = ""
        }
        reasoning = reasoning.trim()
        val sb = StringBuilder(IM_START).append("assistant\n")
        if (keepThinking) sb.append("<think>\n").append(reasoning).append("\n</think>\n\n")
        sb.append(content)
        m.toolCalls.forEachIndexed { idx, tc ->
            sb.append(if (idx == 0) (if (content.trim().isNotEmpty()) "\n\n" else "") else "\n")
            sb.append("<tool_call>\n<function=").append(tc.name).append(">\n")
            for ((k, v) in tc.arguments) {
                sb.append("<parameter=").append(k).append(">\n")
                sb.append(if (v is String) v else PyJson.dumps(v))
                sb.append("\n</parameter>\n")
            }
            sb.append("</function>\n</tool_call>")
        }
        sb.append(IM_END).append('\n')
        return sb.toString()
    }
}

/**
 * Qwen3.6 툴 호출 파서:
 *   <tool_call>\n<function=name>\n<parameter=k>\nvalue\n</parameter>\n</function>\n</tool_call>
 * 값은 문자열 그대로(앞뒤 개행 1개 제거). 숫자/불리언 변환은 툴 쪽 인자 헬퍼가 한다.
 */
object QwenToolCallParser {
    private val CALL = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)
    private val FUNC = Regex("<function=([^>\\n]+)>(.*?)(</function>|$)", RegexOption.DOT_MATCHES_ALL)
    private val PARAM = Regex("<parameter=([^>\\n]+)>(.*?)</parameter>", RegexOption.DOT_MATCHES_ALL)

    fun parse(content: String): ToolCallParser.Result {
        val calls = ArrayList<ToolCall>()
        for (m in CALL.findAll(content)) {
            val f = FUNC.find(m.groupValues[1]) ?: continue
            val args = LinkedHashMap<String, Any?>()
            for (p in PARAM.findAll(f.groupValues[2])) {
                args[p.groupValues[1].trim()] = p.groupValues[2].removePrefix("\n").removeSuffix("\n")
            }
            calls += ToolCall(f.groupValues[1].trim(), args)
        }
        val text = if (calls.isEmpty()) content else content.replace(CALL, "").trim()
        return ToolCallParser.Result(text, calls)
    }

    fun visiblePrefix(content: String): String {
        val open = content.indexOf("<tool_call>")
        return if (open < 0) content else content.substring(0, open).trimEnd()
    }
}
