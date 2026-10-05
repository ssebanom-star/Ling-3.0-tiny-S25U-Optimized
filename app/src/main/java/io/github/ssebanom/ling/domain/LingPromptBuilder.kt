package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/**
 * Ling-3.0 (Bailing V3) chat_template.jinja 의 Kotlin 이식.
 *
 * - [render] : 템플릿과 바이트 단위로 같은 문자열 (tools/template/gen_goldens.py 골든으로 검증)
 * - [segments] : 같은 내용을 엔진용 조각으로 분할. 메시지마다 조각을 고정 분할하므로 턴이 바뀌어도
 *   토큰화 결과가 같고, assistant 의 rawTokens 가 있으면 텍스트 대신 생성 토큰을 그대로 넣어
 *   엔진의 접두부 캐시가 끊기지 않는다. 턴 경계(system 끝, assistant/observation 끝)에 체크포인트 표시.
 */
object LingPromptBuilder {
    const val ROLE_END = "<|role_end|>"
    private const val TOOLS_HEADER =
        "# Tools\n\nYou may call one or more functions to assist with the user query.\n\n" +
            "You are provided with function signatures within <tools></tools> XML tags:\n<tools>"
    private const val TOOLS_FOOTER =
        "\n</tools>\n\nIf none of the functions can be used, point it out. If the given question lacks the parameters " +
            "required by the function, also point it out.\nIf you need to use a function, for each function call, output " +
            "the function name and arguments within the following XML format:\n<tool_call>{function-name}\n" +
            "<arg_key>{arg-key-1}</arg_key>\n<arg_value>{arg-value-1}</arg_value>\n<arg_key>{arg-key-2}</arg_key>\n" +
            "<arg_value>{arg-value-2}</arg_value>\n...\n</tool_call>\n"

    fun generationPrompt(thinking: Boolean): String =
        "<role>ASSISTANT</role>" + if (thinking) "\n<think>" else "\n<think></think>"

    fun render(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        thinking: Boolean = true,
        addGenerationPrompt: Boolean = true,
    ): String = buildParts(messages, tools, thinking, addGenerationPrompt, useRawTokens = false)
        .joinToString("") { it.text!! }

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
        val opt = if (thinking) "on" else "off"
        val first = messages.firstOrNull()
        val firstIsSystem = first?.role == Role.SYSTEM
        val hasFlag = firstIsSystem &&
            (first!!.content.contains("detailed thinking on") || first.content.contains("detailed thinking off"))

        // ---- system header ----
        val sys = StringBuilder("<role>SYSTEM</role>")
        if (tools.isNotEmpty()) {
            if (firstIsSystem) sys.append(first!!.content).append('\n')
            sys.append(TOOLS_HEADER)
            for (t in tools) sys.append('\n').append(PyJson.dumps(t))
            sys.append(TOOLS_FOOTER)
            sys.append(if (hasFlag) ROLE_END else "detailed thinking $opt$ROLE_END")
        } else if (firstIsSystem) {
            if (hasFlag) sys.append(first!!.content).append(ROLE_END)
            else sys.append(first!!.content).append('\n').append("detailed thinking $opt$ROLE_END")
        } else {
            sys.append("detailed thinking $opt$ROLE_END")
        }
        parts += PromptSegment(text = sys.toString(), boundaryAfter = true)

        // ---- messages ----
        messages.forEachIndexed { i, m ->
            when (m.role) {
                Role.USER -> parts += PromptSegment(text = "<role>HUMAN</role>${m.content}$ROLE_END")
                Role.SYSTEM -> if (i != 0) parts += PromptSegment(text = "<role>SYSTEM</role>${m.content}$ROLE_END")
                Role.ASSISTANT -> {
                    val raw = m.rawTokens
                    if (useRawTokens && raw != null && m.thinkingAtGeneration != null) {
                        parts += PromptSegment(text = generationPrompt(m.thinkingAtGeneration))
                        if (raw.isNotEmpty()) parts += PromptSegment(tokens = raw)
                        parts += PromptSegment(text = ROLE_END, boundaryAfter = true)
                    } else {
                        parts += PromptSegment(text = renderAssistant(m), boundaryAfter = true)
                    }
                }
                Role.TOOL -> {
                    val sb = StringBuilder()
                    if (i == 0 || messages[i - 1].role != Role.TOOL) sb.append("<role>OBSERVATION</role>")
                    sb.append("\n<tool_response>\n").append(m.content).append("\n</tool_response>")
                    val lastOfGroup = i == messages.lastIndex || messages[i + 1].role != Role.TOOL
                    if (lastOfGroup) sb.append(ROLE_END)
                    parts += PromptSegment(text = sb.toString(), boundaryAfter = lastOfGroup)
                }
            }
        }
        if (addGenerationPrompt) parts += PromptSegment(text = generationPrompt(thinking))
        return parts
    }

    /** 템플릿 규칙대로 assistant 메시지를 텍스트로 렌더링 (rawTokens 미사용 경로) */
    fun renderAssistant(m: ChatMessage): String {
        var content = m.content
        var reasoning = ""
        if (!m.reasoning.isNullOrEmpty()) {
            reasoning = m.reasoning
        } else if (content.contains("</think>")) {
            reasoning = content.split("</think>")[0].trimEnd('\n').split("<think>").last().trimStart('\n')
            content = content.split("</think>").last().trimStart('\n')
        }
        val sb = StringBuilder()
        if (reasoning.isNotEmpty()) {
            sb.append("<role>ASSISTANT</role>\n<think>").append(reasoning.trim('\n')).append("</think>")
                .append(content.trimStart('\n'))
        } else {
            sb.append("<role>ASSISTANT</role>\n<think></think>").append(content)
        }
        m.toolCalls.forEachIndexed { idx, tc ->
            if ((idx == 0 && content.isNotEmpty()) || idx > 0) sb.append('\n')
            sb.append("<tool_call>").append(tc.name).append('\n')
            for ((k, v) in tc.arguments) {
                sb.append("<arg_key>").append(k).append("</arg_key>")
                sb.append("\n<arg_value>").append(if (v is String) v else PyJson.dumps(v)).append("</arg_value>")
            }
            sb.append("\n</tool_call>")
        }
        sb.append(ROLE_END)
        return sb.toString()
    }
}
