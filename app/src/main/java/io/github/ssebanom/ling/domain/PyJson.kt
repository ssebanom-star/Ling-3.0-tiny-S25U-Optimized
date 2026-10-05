package io.github.ssebanom.ling.domain

/**
 * Python json.dumps(ensure_ascii=False) 와 같은 형식으로 직렬화한다.
 * (HF 채팅 템플릿의 tojson 필터 출력과 바이트 단위로 일치해야 프롬프트가 학습 분포와 같아진다)
 * 구분자: ", " / ": ", 키 순서 유지.
 */
object PyJson {
    fun dumps(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> sb.append(pyFloat(v))
            is Float -> sb.append(pyFloat(v.toDouble()))
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(", ")
                    first = false
                    writeString(sb, k.toString())
                    sb.append(": ")
                    write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (e in v) {
                    if (!first) sb.append(", ")
                    first = false
                    write(sb, e)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.asList())
            else -> writeString(sb, v.toString())
        }
    }

    private fun pyFloat(d: Double): String = when {
        d.isNaN() -> "NaN"
        d.isInfinite() -> if (d > 0) "Infinity" else "-Infinity"
        d == Math.rint(d) && kotlin.math.abs(d) < 1e16 -> "${d.toLong()}.0"
        else -> d.toString()
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
