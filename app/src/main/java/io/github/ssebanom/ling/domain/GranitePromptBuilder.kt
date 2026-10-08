package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/**
 * Granite 4.0 (ibm-granite/granite-4.0-h-tiny) chat_template.jinja 의 Kotlin 이식(documents 경로 제외).
 *
 * - [render]: 템플릿과 바이트 단위 일치 (tools/template/gen_goldens_granite.py 골든)
 * - [segments]: 엔진용 조각. 추론 단계가 없어 생성 토큰(`본문<tool_call>…</tool_call>`)을 그대로 다시 넣어도
 *   템플릿 렌더 결과와 같은 형태가 된다.
 */
object GranitePromptBuilder {
    private const val SR = "<|start_of_role|>"
    private const val ER = "<|end_of_role|>"
    const val EOT = "<|end_of_text|>"

    private const val DEFAULT_SYSTEM = "You are a helpful assistant. Please ensure responses are professional, accurate, and safe."
    private const val TOOLS_PREFIX = "You are a helpful assistant with access to the following tools. You may call one or more tools to " +
        "assist with the user query.\n\nYou are provided with function signatures within <tools></tools> XML tags:\n<tools>"
    private const val TOOLS_SUFFIX = "\n</tools>\n\nFor each tool call, return a json object with function name and arguments within " +
        "<tool_call></tool_call> XML tags:\n<tool_call>\n{\"name\": <function-name>, \"arguments\": <args-json-object>}\n</tool_call>. " +
        "If a tool does not exist in the provided list of tools, notify the user that you do not have the ability to fulfill the request."

    const val GENERATION_PROMPT = SR + "assistant" + ER

    fun render(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        addGenerationPrompt: Boolean = true,
    ): String = buildParts(messages, tools, addGenerationPrompt, useRawTokens = false).joinToString("") { it.text!! }

    fun segments(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>> = emptyList(),
        addGenerationPrompt: Boolean = true,
    ): List<PromptSegment> = buildParts(messages, tools, addGenerationPrompt, useRawTokens = true)

    private fun buildParts(
        messages: List<ChatMessage>,
        tools: List<Map<String, Any?>>,
        addGenerationPrompt: Boolean,
        useRawTokens: Boolean,
    ): List<PromptSegment> {
        val parts = ArrayList<PromptSegment>()
        val toolsMsg = if (tools.isEmpty()) "" else buildString {
            append(TOOLS_PREFIX)
            for (t in tools) append('\n').append(PyJson.dumps(t))
            append(TOOLS_SUFFIX)
        }
        val first = messages.firstOrNull()
        val system = when {
            first?.role == Role.SYSTEM -> if (toolsMsg.isNotEmpty()) first.content + "\n\n" + toolsMsg else first.content
            else -> toolsMsg
        }
        parts += PromptSegment(text = SR + "system" + ER + system.ifEmpty { DEFAULT_SYSTEM } + EOT + "\n", boundaryAfter = true)

        messages.forEachIndexed { i, m ->
            when (m.role) {
                Role.USER -> parts += PromptSegment(text = SR + "user" + ER + m.content + EOT + "\n")
                Role.SYSTEM -> if (i != 0) parts += PromptSegment(text = SR + "system" + ER + m.content + EOT + "\n")
                Role.ASSISTANT -> {
                    val raw = m.rawTokens
                    if (useRawTokens && raw != null) {
                        parts += PromptSegment(text = GENERATION_PROMPT)
                        if (raw.isNotEmpty()) parts += PromptSegment(tokens = raw)
                        parts += PromptSegment(text = EOT + "\n", boundaryAfter = true)
                    } else {
                        parts += PromptSegment(text = renderAssistant(m), boundaryAfter = true)
                    }
                }
                Role.TOOL -> {
                    val sb = StringBuilder()
                    if (i == 0 || messages[i - 1].role != Role.TOOL) sb.append(SR).append("user").append(ER)
                    sb.append("\n<tool_response>\n").append(m.content).append("\n</tool_response>")
                    val lastOfGroup = i == messages.lastIndex || messages[i + 1].role != Role.TOOL
                    if (lastOfGroup) sb.append(EOT).append('\n')
                    parts += PromptSegment(text = sb.toString(), boundaryAfter = lastOfGroup)
                }
            }
        }
        if (addGenerationPrompt) parts += PromptSegment(text = GENERATION_PROMPT)
        return parts
    }

    fun renderAssistant(m: ChatMessage): String {
        val sb = StringBuilder(SR).append("assistant").append(ER).append(m.content)
        m.toolCalls.forEachIndexed { idx, tc ->
            if (idx > 0 || m.content.isNotEmpty()) sb.append('\n')
            sb.append("<tool_call>\n{\"name\": \"").append(tc.name).append("\", \"arguments\": ")
                .append(PyJson.dumps(tc.arguments)).append("}\n</tool_call>")
        }
        sb.append(EOT).append('\n')
        return sb.toString()
    }
}

/**
 * Granite 툴 호출 파서: `<tool_call>\n{"name": "...", "arguments": {...}}\n</tool_call>` (여러 개 가능).
 * 생성이 EOS 에서 끝나 닫는 태그가 빠진 마지막 호출도 받아들인다. 인자는 JSON 타입 그대로(Long/Double/Boolean/…).
 */
object GraniteToolCallParser {
    private val CALL = Regex("<tool_call>(.*?)(</tool_call>|$)", RegexOption.DOT_MATCHES_ALL)
    private val NAME = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")

    @Suppress("UNCHECKED_CAST")
    fun parse(content: String): ToolCallParser.Result {
        val calls = ArrayList<ToolCall>()
        for (m in CALL.findAll(content)) {
            val body = m.groupValues[1].trim()
            if (body.isEmpty()) continue
            val obj = runCatching { MiniJson(body).parse() as? Map<String, Any?> }.getOrNull()
            if (obj != null) {
                val name = obj["name"] as? String ?: continue
                val args = when (val a = obj["arguments"] ?: obj["parameters"]) {
                    is Map<*, *> -> LinkedHashMap(a as Map<String, Any?>)
                    is String -> runCatching { LinkedHashMap(MiniJson(a).parse() as Map<String, Any?>) }.getOrDefault(LinkedHashMap())
                    else -> LinkedHashMap()
                }
                calls += ToolCall(name, args)
            } else {
                // JSON 이 깨졌으면 이름만이라도(인자 없이 호출 → 툴이 누락 인자 오류를 돌려줌)
                NAME.find(body)?.let { calls += ToolCall(it.groupValues[1], LinkedHashMap()) }
            }
        }
        val text = if (calls.isEmpty()) content else content.replace(CALL, "").trim()
        return ToolCallParser.Result(text, calls)
    }

    fun visiblePrefix(content: String): String {
        val open = content.indexOf("<tool_call>")
        return if (open < 0) content else content.substring(0, open).trimEnd()
    }
}

/**
 * Granite 툴 호출 문법(GBNF, lazy). `<tool_call>` 이 나온 뒤에만 적용되어
 * - 함수 이름을 실제 툴 목록으로 제한하고
 * - arguments 를 JSON 객체로 강제하며
 * - 문자열 안의 `\u` 이스케이프를 금지한다(모델이 한글을 \uXXXX 로 쓰다 엉뚱한 글자로 망가뜨리는 문제 방지).
 */
object GraniteToolGrammar {
    /** 생성 시작부터 매칭, 첫 캡처 그룹부터 문법 적용 */
    const val TRIGGER = "[\\s\\S]*?(<tool_call>[\\s\\S]*)"

    fun build(toolNames: List<String>): String {
        val names = toolNames.distinct().joinToString(" | ") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
        return """
            root ::= call ("\n" call)*
            call ::= "<tool_call>\n{\"name\": \"" name "\", \"arguments\": " obj "}\n</tool_call>"
            name ::= $names
            obj ::= "{" ws ( kv ( ws "," ws kv )* )? ws "}"
            kv ::= str ws ":" ws val
            val ::= obj | arr | str | num | "true" | "false" | "null"
            arr ::= "[" ws ( val ( ws "," ws val )* )? ws "]"
            str ::= "\"" chr* "\""
            chr ::= [^"\\\x00-\x1F\x7F] | "\\" ["\\/bfnrt]
            num ::= "-"? [0-9]+ ("." [0-9]+)? ([eE] [-+]? [0-9]+)?
            ws ::= " "?
        """.trimIndent() + "\n"
    }
}
