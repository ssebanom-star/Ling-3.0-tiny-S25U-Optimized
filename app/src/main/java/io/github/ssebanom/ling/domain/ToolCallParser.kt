package io.github.ssebanom.ling.domain

/**
 * Ling-3.0 툴 호출 포맷 파서:
 *   <tool_call>name\n<arg_key>k</arg_key>\n<arg_value>v</arg_value>...\n</tool_call>
 * 인자 값은 문자열 그대로 둔다(재렌더링 시 원문 보존).
 */
object ToolCallParser {
    private val CALL = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)
    private val ARG = Regex("<arg_key>(.*?)</arg_key>\\s*<arg_value>(.*?)</arg_value>", RegexOption.DOT_MATCHES_ALL)

    data class Result(val text: String, val calls: List<ToolCall>)

    fun parse(content: String): Result {
        val calls = ArrayList<ToolCall>()
        for (m in CALL.findAll(content)) {
            val body = m.groupValues[1]
            val firstArg = body.indexOf("<arg_key>")
            val name = (if (firstArg >= 0) body.substring(0, firstArg) else body).trim()
            if (name.isEmpty()) continue
            val args = LinkedHashMap<String, Any?>()
            for (a in ARG.findAll(body)) args[a.groupValues[1].trim()] = a.groupValues[2]
            calls += ToolCall(name, args)
        }
        val text = if (calls.isEmpty()) content else content.replace(CALL, "").trim()
        return Result(text, calls)
    }

    /** 스트리밍 중 UI 표시용: 닫히지 않은 <tool_call> 이후는 숨긴다 */
    fun visiblePrefix(content: String): String {
        val open = content.lastIndexOf("<tool_call>")
        if (open < 0) return content
        val close = content.indexOf("</tool_call>", open)
        return if (close < 0) content.substring(0, open) else content
    }
}
