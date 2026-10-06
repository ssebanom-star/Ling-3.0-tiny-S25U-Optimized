package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/**
 * LFM2.5 (LiquidAI/LFM2.5-8B-A1B) chat_template.jinja 의 Kotlin 이식.
 *
 * - [render]: 템플릿과 바이트 단위 일치 (tools/template/gen_goldens_lfm.py 골든으로 검증)
 * - [segments]: 엔진용 조각. 앱은 preserve_thinking=true 로 쓴다 — 모델이 생성한 토큰
 *   (`<think>…</think>답`)을 다음 턴에 그대로 넣어도 템플릿 렌더 결과와 같은 형태가 되어 캐시가 이어진다.
 */
object LfmPromptBuilder {
    const val BOS = "<|startoftext|>"
    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"
    const val TOOL_CALL_START = "<|tool_call_start|>"
    const val TOOL_CALL_END = "<|tool_call_end|>"
    const val GENERATION_PROMPT = "$IM_START" + "assistant\n"

    fun render(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        preserveThinking: Boolean = false,
        addGenerationPrompt: Boolean = true,
    ): String = buildParts(messages, tools, preserveThinking, addGenerationPrompt, useRawTokens = false)
        .joinToString("") { it.text!! }

    fun segments(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        addGenerationPrompt: Boolean = true,
    ): List<PromptSegment> = buildParts(messages, tools, preserveThinking = true, addGenerationPrompt, useRawTokens = true)

    private fun roleName(r: Role) = when (r) {
        Role.SYSTEM -> "system"
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
        Role.TOOL -> "tool"
    }

    private fun buildParts(
        all: List<ChatMessage>,
        tools: List<Map<String, Any?>>,
        preserveThinking: Boolean,
        addGenerationPrompt: Boolean,
        useRawTokens: Boolean,
    ): List<PromptSegment> {
        val parts = ArrayList<PromptSegment>()
        var systemPrompt = ""
        var messages = all
        if (all.firstOrNull()?.role == Role.SYSTEM) {
            systemPrompt = all[0].content
            messages = all.drop(1)
        }
        if (tools.isNotEmpty()) {
            val sb = StringBuilder(systemPrompt)
            if (systemPrompt.isNotEmpty()) sb.append('\n')
            sb.append("List of tools: [")
            tools.forEachIndexed { i, t ->
                if (i > 0) sb.append(", ")
                sb.append(PyJson.dumps(t))
            }
            sb.append(']')
            systemPrompt = sb.toString()
        }
        val head = StringBuilder(BOS)
        if (systemPrompt.isNotEmpty()) head.append(IM_START).append("system\n").append(systemPrompt).append(IM_END).append('\n')
        parts += PromptSegment(text = head.toString(), boundaryAfter = true)

        val lastUser = messages.indexOfLast { it.role == Role.USER }
        messages.forEachIndexed { i, m ->
            when (m.role) {
                Role.ASSISTANT -> {
                    val raw = m.rawTokens
                    if (useRawTokens && raw != null) {
                        parts += PromptSegment(text = GENERATION_PROMPT)
                        if (raw.isNotEmpty()) parts += PromptSegment(tokens = raw)
                        parts += PromptSegment(text = "$IM_END\n", boundaryAfter = true)
                    } else {
                        parts += PromptSegment(text = renderAssistant(m, preserveThinking || i > lastUser), boundaryAfter = true)
                    }
                }
                else -> {
                    val lastOfToolGroup = m.role == Role.TOOL && (i == messages.lastIndex || messages[i + 1].role != Role.TOOL)
                    parts += PromptSegment(
                        text = IM_START + roleName(m.role) + "\n" + m.content + IM_END + "\n",
                        boundaryAfter = lastOfToolGroup,
                    )
                }
            }
        }
        if (addGenerationPrompt) parts += PromptSegment(text = GENERATION_PROMPT)
        return parts
    }

    fun renderAssistant(m: ChatMessage, keepThinking: Boolean): String {
        val sb = StringBuilder(IM_START).append("assistant\n")
        val thinking = m.reasoning.orEmpty()
        if (thinking.isNotEmpty() && keepThinking) sb.append("<think>").append(thinking).append("</think>")
        var content = m.content
        if (!keepThinking && content.contains("</think>")) content = content.split("</think>").last().trim()
        sb.append(content)
        if (m.toolCalls.isNotEmpty()) sb.append(renderToolCalls(m.toolCalls))
        sb.append(IM_END).append('\n')
        return sb.toString()
    }

    fun renderToolCalls(calls: List<ToolCall>): String =
        TOOL_CALL_START + "[" + calls.joinToString(", ") { c ->
            c.name + "(" + c.arguments.entries.joinToString(", ") { (k, v) -> "$k=${argValue(v)}" } + ")"
        } + "]" + TOOL_CALL_END

    /** 템플릿 format_arg_value: 문자열은 작은따옴표(이스케이프 없음), mapping 은 tojson, 나머지는 Python str() */
    private fun argValue(v: Any?): String = when (v) {
        is String -> "'$v'"
        is Map<*, *> -> PyJson.dumps(v)
        else -> pyStr(v)
    }

    private fun pyStr(v: Any?): String = when (v) {
        null -> "None"
        is Boolean -> if (v) "True" else "False"
        is Number -> PyJson.dumps(v)
        is String -> pyRepr(v)
        is Map<*, *> -> "{" + v.entries.joinToString(", ") { (k, x) -> pyRepr(k.toString()) + ": " + pyStr(x) } + "}"
        is Iterable<*> -> "[" + v.joinToString(", ") { pyStr(it) } + "]"
        else -> v.toString()
    }

    /** 컨테이너 안 문자열의 Python repr (따옴표 선택 규칙만 재현) */
    private fun pyRepr(s: String): String {
        val q = if (s.contains('\'') && !s.contains('"')) '"' else '\''
        val body = s.replace("\\", "\\\\").replace("\n", "\\n").let { if (q == '\'') it.replace("'", "\\'") else it }
        return "$q$body$q"
    }
}
