package io.github.ssebanom.ling.ui.markdown

/**
 * 모델 출력 → 렌더링 블록. 스트리밍 중에도 완성된 블록은 다시 그리지 않도록 빈 줄 단위로 자른다.
 * 코드 펜스와 `$$` 수식 블록은 원자 단위로 유지한다.
 */
sealed interface MdBlock {
    data class Text(val md: String) : MdBlock
    data class Code(val lang: String, val code: String, val closed: Boolean) : MdBlock
    /** 블록 수식(`$$` 단독 줄로 감싼 것). closed=false 면 스트리밍 중 미완성 */
    data class Math(val latex: String, val closed: Boolean) : MdBlock
    /** GFM 표. align: -1 왼쪽, 0 가운데, 1 오른쪽 */
    data class Table(val header: List<String>, val align: List<Int>, val rows: List<List<String>>) : MdBlock
}

object MdBlocks {
    private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^`\\s]*)")

    fun split(src: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        for (b in splitRaw(src)) if (b is MdBlock.Text) out += refine(b.md) else out += b
        return out
    }

    private val DELIM = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    /** 텍스트 블록에서 LaTeX 구분자 정규화 후 블록 수식·표를 떼어낸다 */
    private fun refine(text: String): List<MdBlock> {
        val lines = LatexNormalizer.normalize(text).split('\n')
        val out = ArrayList<MdBlock>()
        val buf = StringBuilder()
        fun flush() {
            val t = buf.toString().trim('\n')
            buf.setLength(0)
            if (t.isNotBlank()) out += MdBlock.Text(t)
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim() == "$$") {
                flush()
                val sb = StringBuilder()
                var closed = false
                i++
                while (i < lines.size) {
                    if (lines[i].trim() == "$$") { closed = true; i++; break }
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i])
                    i++
                }
                out += MdBlock.Math(sb.toString().trim(), closed)
                continue
            }
            if ('|' in line && i + 1 < lines.size && DELIM.matches(lines[i + 1]) && '|' in lines[i + 1]) {
                flush()
                val header = cells(line)
                val align = cells(lines[i + 1]).map { c ->
                    val l = c.startsWith(":"); val r = c.endsWith(":")
                    if (l && r) 0 else if (r) 1 else -1
                }
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && '|' in lines[i] && lines[i].isNotBlank()) { rows += cells(lines[i]); i++ }
                val n = header.size
                out += MdBlock.Table(header, List(n) { align.getOrElse(it) { -1 } }, rows.map { r -> List(n) { r.getOrElse(it) { "" } } })
                continue
            }
            buf.append(line).append('\n')
            i++
        }
        flush()
        return out
    }

    private fun cells(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
        // \| 는 셀 안의 리터럴 파이프
        return t.split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
    }

    private fun splitRaw(src: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val buf = StringBuilder()
        fun flushText() {
            val t = buf.toString().trim('\n')
            buf.setLength(0)
            if (t.isBlank()) return
            // 들여쓴 이어지는 문단(목록 항목의 연속)은 앞 블록에 붙인다 — 따로 두면 들여쓰기 코드로 해석된다
            val prev = out.lastOrNull()
            if (prev is MdBlock.Text && t.first().isWhitespace()) out[out.lastIndex] = MdBlock.Text(prev.md + "\n\n" + t)
            else out += MdBlock.Text(t)
        }

        val lines = src.split('\n')
        var i = 0
        var inMath = false
        while (i < lines.size) {
            val line = lines[i]
            val fence = if (!inMath) FENCE.find(line) else null
            if (fence != null) {
                flushText()
                val marker = fence.groupValues[1]
                val lang = fence.groupValues[2]
                val code = StringBuilder()
                var closed = false
                i++
                while (i < lines.size) {
                    val l = lines[i]
                    if (l.trimStart().startsWith(marker) && l.trim().all { it == marker[0] }) { closed = true; i++; break }
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(l)
                    i++
                }
                out += MdBlock.Code(lang, code.toString(), closed)
                continue
            }
            val trimmed = line.trim()
            if (trimmed == "$$") inMath = !inMath
            if (!inMath && trimmed.isEmpty()) flushText() else buf.append(line).append('\n')
            i++
        }
        flushText()
        return out
    }
}

/**
 * LaTeX 구분자 정규화. Markwon(JLatexMath)은 `$$…$$` 만 인식하므로
 * `\[…\]`·`\(…\)`·`$…$` 를 바꾼다. 인라인 코드(`…`) 안은 건드리지 않는다.
 */
object LatexNormalizer {
    private val DISPLAY_BRACKET = Regex("\\\\\\[(.+?)\\\\]", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_PAREN = Regex("\\\\\\((.+?)\\\\\\)", RegexOption.DOT_MATCHES_ALL)
    // 단일 $: 통화 표기($5, $10)와 구분하려고 여닫는 $ 안쪽에 공백을 허용하지 않는다
    private val SINGLE_DOLLAR = Regex("(?<![\\\\$\\w])\\$(?![\\s$])([^$\\n]+?)(?<![\\s\\\\])\\$(?![\\w$])")
    // 한 줄 안의 $$…$$ 가 단독 줄이면 블록으로
    private val LINE_DISPLAY = Regex("^\\s*\\$\\$(.+?)\\$\\$\\s*$", RegexOption.MULTILINE)
    private val CODE_SPAN = Regex("(`+)(.+?)\\1", RegexOption.DOT_MATCHES_ALL)

    fun normalize(md: String): String {
        if ('$' !in md && '\\' !in md) return md
        val sb = StringBuilder()
        var last = 0
        for (m in CODE_SPAN.findAll(md)) {
            sb.append(convert(md.substring(last, m.range.first)))
            sb.append(m.value)
            last = m.range.last + 1
        }
        sb.append(convert(md.substring(last)))
        return sb.toString()
    }

    private fun convert(s: String): String {
        var t = s
        t = DISPLAY_BRACKET.replace(t) { "\n$$\n" + it.groupValues[1].trim() + "\n$$\n" }
        t = INLINE_PAREN.replace(t) { "$$" + it.groupValues[1].trim() + "$$" }
        t = protectDouble(t) { SINGLE_DOLLAR.replace(it) { m -> "$$" + m.groupValues[1] + "$$" } }
        t = LINE_DISPLAY.replace(t) { "$$\n" + it.groupValues[1].trim() + "\n$$" }
        return t
    }

    /** 이미 `$$`로 감싼 부분은 단일 $ 치환에서 제외 */
    private inline fun protectDouble(s: String, f: (String) -> String): String {
        val parts = s.split("$$")
        if (parts.size == 1) return f(s)
        return parts.mapIndexed { i, p -> if (i % 2 == 0) f(p) else p }.joinToString("$$")
    }
}
