package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/**
 * K2-Horizon (IFM/K2-Horizon-3.7B) chat_template.jinja 의 Kotlin 이식.
 * 앱 고정 옵션: tool_presentation_format='json', tool_call_format='xml'.
 * 추론 강도: thinking=true → high(`<ifm|think>`), false → low(`<ifm|think_faster>`). 템플릿에 추론 끄기는 없다.
 *
 * - [render]: 템플릿과 바이트 단위 일치 (tools/template/gen_goldens_k2h.py 골든)
 * - [segments]: 엔진용 조각. 템플릿이 지난 턴 추론을 지우지 않아 생성 토큰을 그대로 재사용할 수 있다.
 */
object K2hPromptBuilder {
    const val BOS = "<|ifm|begin_of_text|>"
    const val IM_START = "<|ifm|im_start|>"
    const val IM_END = "<|ifm|im_end|>"

    private const val TOOLS_HEAD = "# Tools\nYou may call one or more tools to assist with the user query.\n\nAvailable tools are:\n\n"
    private const val CALL_INSTR_XML =
        "Wrap all tool calls in a single <ifm|tool_calls></ifm|tool_calls> block. For each call, write the function name at the start of " +
            "<ifm|tool_call>, followed by paired <ifm|arg_key> and <ifm|arg_value> tags for each argument:\n\n<ifm|tool_calls>\n" +
            "<ifm|tool_call>\$FUNCTION_NAME\n<ifm|arg_key>\$PARAMETER_NAME</ifm|arg_key>\n<ifm|arg_value>\$PARAMETER_VALUE</ifm|arg_value>\n" +
            "...\n</ifm|tool_call>\n</ifm|tool_calls>\n\nString and scalar parameters should be written as plain text. " +
            "Array and object parameters should be written as JSON literals."

    fun thinkTag(thinking: Boolean) = if (thinking) "ifm|think" else "ifm|think_faster"

    fun generationPrompt(thinking: Boolean): String = IM_START + "assistant\n<" + thinkTag(thinking) + ">\n"

    fun render(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        thinking: Boolean = true,
        addGenerationPrompt: Boolean = true,
    ): String = buildParts(messages, tools, thinking, addGenerationPrompt, useRawTokens = false).joinToString("") { it.text!! }

    fun segments(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        thinking: Boolean = true,
        addGenerationPrompt: Boolean = true,
    ): List<PromptSegment> = buildParts(messages, tools, thinking, addGenerationPrompt, useRawTokens = true)

    private fun buildParts(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>>,
        thinking: Boolean,
        addGenerationPrompt: Boolean,
        useRawTokens: Boolean,
    ): List<PromptSegment> {
        val parts = ArrayList<PromptSegment>()
        val first = messages.firstOrNull()
        val head = StringBuilder(BOS)
        if (tools.isNotEmpty()) {
            head.append(IM_START).append("system\n").append(TOOLS_HEAD).append("<ifm|tools>")
            for (t in tools) head.append('\n').append(PyJson.dumps(t))
            head.append("\n</ifm|tools>")
            head.append("\n\nWhen calling tools, you MUST follow the tool-call format below:\n\n").append(CALL_INSTR_XML)
            if (first?.role == Role.SYSTEM && first.content.isNotEmpty()) head.append("\n\n").append(first.content)
            head.append(IM_END)
        } else if (first?.role == Role.SYSTEM) {
            head.append(IM_START).append("system\n").append(first.content).append(IM_END)
        }
        parts += PromptSegment(text = head.toString(), boundaryAfter = true)

        messages.forEachIndexed { i, m ->
            when (m.role) {
                Role.USER -> parts += PromptSegment(text = IM_START + "user\n" + m.content + IM_END)
                Role.SYSTEM -> if (i != 0) parts += PromptSegment(text = IM_START + "system\n" + m.content + IM_END)
                Role.ASSISTANT -> {
                    val raw = m.rawTokens
                    if (useRawTokens && raw != null && m.thinkingAtGeneration != null) {
                        parts += PromptSegment(text = generationPrompt(m.thinkingAtGeneration))
                        if (raw.isNotEmpty()) parts += PromptSegment(tokens = raw)
                        parts += PromptSegment(text = IM_END, boundaryAfter = true)
                    } else {
                        parts += PromptSegment(text = renderAssistant(m), boundaryAfter = true)
                    }
                }
                Role.TOOL -> {
                    val lastOfGroup = i == messages.lastIndex || messages[i + 1].role != Role.TOOL
                    parts += PromptSegment(text = IM_START + "tool\n" + m.content + IM_END, boundaryAfter = lastOfGroup)
                }
            }
        }
        if (addGenerationPrompt) parts += PromptSegment(text = generationPrompt(thinking))
        return parts
    }

    fun renderAssistant(m: ChatMessage): String {
        val tag = thinkTag(m.thinkingAtGeneration != false)
        val sb = StringBuilder(IM_START).append("assistant\n")
        sb.append('<').append(tag).append(">\n").append(m.reasoning.orEmpty()).append("</").append(tag).append('>')
        sb.append(m.content)
        if (m.toolCalls.isNotEmpty()) {
            sb.append("<ifm|tool_calls>")
            for (tc in m.toolCalls) {
                sb.append("\n<ifm|tool_call>").append(tc.name).append('\n')
                for ((k, v) in tc.arguments) {
                    sb.append("<ifm|arg_key>").append(k).append("</ifm|arg_key>\n")
                    sb.append("<ifm|arg_value>").append(if (v is String) v else PyJson.dumps(v)).append("</ifm|arg_value>\n")
                }
                sb.append("</ifm|tool_call>")
            }
            sb.append("\n</ifm|tool_calls>")
        }
        sb.append(IM_END)
        return sb.toString()
    }
}

/** K2-Horizon XML 툴 호출 파서: `<ifm|tool_calls><ifm|tool_call>name\n<ifm|arg_key>k</ifm|arg_key>\n<ifm|arg_value>v</ifm|arg_value>…` */
object K2hToolCallParser {
    private val CALL = Regex("<ifm\\|tool_call>(.*?)</ifm\\|tool_call>", RegexOption.DOT_MATCHES_ALL)
    private val ARG = Regex("<ifm\\|arg_key>(.*?)</ifm\\|arg_key>\\s*(?:<ifm\\|arg_type>.*?</ifm\\|arg_type>\\s*)?<ifm\\|arg_value>(.*?)</ifm\\|arg_value>", RegexOption.DOT_MATCHES_ALL)
    private val BLOCK = Regex("<ifm\\|tool_calls>.*?(</ifm\\|tool_calls>|$)", RegexOption.DOT_MATCHES_ALL)

    fun parse(content: String): ToolCallParser.Result {
        val calls = ArrayList<ToolCall>()
        for (m in CALL.findAll(content)) {
            val body = m.groupValues[1]
            val firstArg = body.indexOf("<ifm|arg_key>")
            val name = (if (firstArg >= 0) body.substring(0, firstArg) else body).trim()
            if (name.isEmpty()) continue
            val args = LinkedHashMap<String, Any?>()
            for (a in ARG.findAll(body)) args[a.groupValues[1].trim()] = a.groupValues[2]
            calls += ToolCall(name, args)
        }
        val text = if (calls.isEmpty()) content else content.replace(BLOCK, "").replace(CALL, "").trim()
        return ToolCallParser.Result(text, calls)
    }

    fun visiblePrefix(content: String): String {
        val i = listOf(content.indexOf("<ifm|tool_calls>"), content.indexOf("<ifm|tool_call>")).filter { it >= 0 }.minOrNull()
        return if (i == null) content else content.substring(0, i).trimEnd()
    }
}
