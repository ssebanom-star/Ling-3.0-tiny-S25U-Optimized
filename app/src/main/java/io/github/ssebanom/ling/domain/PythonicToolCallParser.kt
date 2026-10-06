package io.github.ssebanom.ling.domain

/**
 * LFM2.5 툴 호출 파서: `<|tool_call_start|>[f(a='x', b=1), g()]<|tool_call_end|>`
 * 값은 Python 리터럴(문자열 ''/"", 정수/실수, True/False/None, 리스트, 딕셔너리; JSON true/false/null 도 허용).
 * 시스템 프롬프트로 JSON 호출을 요구한 경우의 `[{"name": .., "arguments": {..}}]` 형식도 받는다.
 */
object PythonicToolCallParser {
    private val BLOCK = Regex(
        Regex.escape(LfmPromptBuilder.TOOL_CALL_START) + "(.*?)" + Regex.escape(LfmPromptBuilder.TOOL_CALL_END),
        RegexOption.DOT_MATCHES_ALL,
    )

    fun parse(content: String): ToolCallParser.Result {
        val calls = ArrayList<ToolCall>()
        for (m in BLOCK.findAll(content)) calls += runCatching { parseCalls(m.groupValues[1]) }.getOrDefault(emptyList())
        val text = if (BLOCK.containsMatchIn(content)) content.replace(BLOCK, "").trim() else content
        return ToolCallParser.Result(text, calls)
    }

    /** 스트리밍 표시용: 완성된 호출 블록은 지우고, 닫히지 않은 블록 이후는 숨긴다 */
    fun visiblePrefix(content: String): String {
        val s = content.replace(BLOCK, "")
        val open = s.indexOf(LfmPromptBuilder.TOOL_CALL_START)
        return if (open < 0) s else s.substring(0, open)
    }

    fun parseCalls(src: String): List<ToolCall> {
        val p = Lit(src.trim())
        p.ws()
        if (p.peek() == '[' && p.lookaheadIsJsonObject()) {
            @Suppress("UNCHECKED_CAST")
            val arr = p.value() as List<Any?>
            return arr.mapNotNull { o ->
                val m = o as? Map<String, Any?> ?: return@mapNotNull null
                val name = m["name"] as? String ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                ToolCall(name, LinkedHashMap((m["arguments"] as? Map<String, Any?>).orEmpty()))
            }
        }
        val out = ArrayList<ToolCall>()
        val bracket = p.eat('[')
        while (true) {
            p.ws()
            if (p.done() || p.peek() == ']') break
            val name = p.ident()
            require(p.eat('(')) { "expected (" }
            val args = LinkedHashMap<String, Any?>()
            while (true) {
                p.ws()
                if (p.eat(')')) break
                val key = p.ident()
                p.ws()
                require(p.eat('=')) { "expected =" }
                args[key] = p.value()
                p.ws()
                if (p.eat(',')) continue
                require(p.eat(')')) { "expected )" }
                break
            }
            out += ToolCall(name, args)
            p.ws()
            if (!p.eat(',')) break
        }
        if (bracket) p.eat(']')
        return out
    }

    private class Lit(val s: String) {
        var i = 0
        fun done() = i >= s.length
        fun peek(): Char? = s.getOrNull(i)
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun eat(c: Char): Boolean { ws(); return if (peek() == c) { i++; true } else false }

        fun lookaheadIsJsonObject(): Boolean {
            var j = i + 1
            while (j < s.length && s[j].isWhitespace()) j++
            return s.getOrNull(j) == '{'
        }

        fun ident(): String {
            ws()
            val st = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.' || s[i] == '-')) i++
            require(i > st) { "expected identifier at $i" }
            return s.substring(st, i)
        }

        fun value(): Any? {
            ws()
            val c = peek() ?: error("unexpected end")
            return when {
                c == '\'' || c == '"' -> string(c)
                c == '[' || c == '(' -> {
                    val close = if (c == '[') ']' else ')'
                    i++
                    val list = ArrayList<Any?>()
                    while (true) {
                        if (eat(close)) break
                        list += value()
                        if (eat(',')) continue
                        require(eat(close)) { "expected $close" }
                        break
                    }
                    list
                }
                c == '{' -> {
                    i++
                    val map = LinkedHashMap<String, Any?>()
                    while (true) {
                        if (eat('}')) break
                        val k = value().toString()
                        require(eat(':')) { "expected :" }
                        map[k] = value()
                        if (eat(',')) continue
                        require(eat('}')) { "expected }" }
                        break
                    }
                    map
                }
                c == '-' || c == '+' || c.isDigit() || c == '.' -> number()
                else -> when (val w = ident()) {
                    "True", "true" -> true
                    "False", "false" -> false
                    "None", "null" -> null
                    else -> w // 따옴표 없는 단어는 문자열로 관대하게 처리
                }
            }
        }

        fun number(): Any {
            val st = i
            if (s[i] == '-' || s[i] == '+') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE_" || ((s[i] == '-' || s[i] == '+') && s[i - 1] in "eE"))) i++
            val t = s.substring(st, i).replace("_", "")
            return t.toLongOrNull() ?: t.toDouble()
        }

        fun string(q: Char): String {
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                if (c == q) return sb.toString()
                if (c == '\\' && i < s.length) {
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else sb.append(c)
            }
            error("unterminated string")
        }
    }
}
