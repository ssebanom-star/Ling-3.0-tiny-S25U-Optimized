package io.github.ssebanom.ling.ui.markdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.ui.theme.MonoSmall
import ru.noties.jlatexmath.JLatexMathDrawable

/** 블록 수식: JLatexMath 로 동기 렌더링(스트리밍 중 깜빡임 없음), 넓으면 가로 스크롤 */
@Composable
fun MathBlock(latex: String, closed: Boolean, color: Color, fontSize: TextUnit) {
    val density = LocalDensity.current
    val px = with(density) { fontSize.toPx() } * 1.2f
    val argb = color.toArgb()
    val drawable = remember(latex, argb, px, closed) {
        if (!closed || latex.isBlank()) null
        else runCatching { JLatexMathDrawable.builder(latex).textSize(px).color(argb).align(JLatexMathDrawable.ALIGN_LEFT).build() }.getOrNull()
    }
    if (drawable == null) {
        // 미완성(스트리밍) 또는 문법 오류: 원문을 수식 상자에 그대로
        Text(
            latex, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(10.dp),
        )
        return
    }
    val w = drawable.intrinsicWidth
    val h = drawable.intrinsicHeight
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.widthIn(min = maxW).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                    drawIntoCanvas {
                        drawable.setBounds(0, 0, w, h)
                        drawable.draw(it.nativeCanvas)
                    }
                }
            }
        }
    }
}

/** GFM 표: 머리글 강조, 줄무늬, 열 너비 자동, 넓으면 가로 스크롤 */
@Composable
fun TableBlock(t: MdBlock.Table, color: Color) {
    val cs = MaterialTheme.colorScheme
    val code = cs.surfaceContainerHighest
    val link = cs.primary
    val rowsAnn = remember(t, code, link) { t.rows.map { r -> r.map { InlineMd.annotate(it, code, link) } } }
    val headAnn = remember(t, code, link) { t.header.map { InlineMd.annotate(it, code, link) } }
    // 열 너비: 내용 길이(한글 2칸) 기반, 남는 폭은 비례 배분
    val weights = remember(t) {
        t.header.indices.map { c ->
            (listOf(t.header[c]) + t.rows.map { it[c] }).maxOf { s -> s.sumOf { ch -> if (ch.code > 0x2E80) 2 else 1 }.toInt() }.coerceIn(3, 40)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = 7.5.dp
        val natural = weights.map { unit * it + 24.dp }
        val sum = natural.fold(0.dp) { a, b -> a + b }
        val widths: List<Dp> = if (sum < maxWidth) natural.map { it * (maxWidth / sum) } else natural
        val shape = RoundedCornerShape(12.dp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.clip(shape).border(1.dp, cs.outlineVariant, shape)) {
                Row(Modifier.background(cs.surfaceContainerHigh)) {
                    headAnn.forEachIndexed { c, a ->
                        Cell(a, widths[c], t.align[c], color, bold = true)
                    }
                }
                rowsAnn.forEachIndexed { r, row ->
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.width(widths.fold(0.dp) { a, b -> a + b }))
                    Row(Modifier.background(if (r % 2 == 1) cs.surfaceContainerLow else Color.Transparent)) {
                        row.forEachIndexed { c, a -> Cell(a, widths[c], t.align[c], color, bold = false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(a: AnnotatedString, w: Dp, align: Int, color: Color, bold: Boolean) {
    SelectionContainer {
        Text(
            a, Modifier.width(w).padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            color = color,
            textAlign = when (align) { 0 -> TextAlign.Center; 1 -> TextAlign.End; else -> TextAlign.Start },
        )
    }
}

/** 표 셀용 인라인 마크다운: **굵게**, *기울임*, `코드`, ~~취소선~~, [링크](url), $$수식$$(코드 모양) */
object InlineMd {
    private val TOKEN = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|~~(.+?)~~|`([^`]+)`|\\$\\$(.+?)\\$\\$|\\[([^\\]]+)]\\(([^)]+)\\)|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\w)|<br\\s*/?>")

    fun annotate(s: String, codeBg: Color, link: Color): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in TOKEN.findAll(s)) {
            append(s.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() || g[2].isNotEmpty() -> pushAndAppend(SpanStyle(fontWeight = FontWeight.Bold), g[1].ifEmpty { g[2] })
                g[3].isNotEmpty() -> pushAndAppend(SpanStyle(textDecoration = TextDecoration.LineThrough), g[3])
                g[4].isNotEmpty() -> pushAndAppend(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg), g[4])
                g[5].isNotEmpty() -> pushAndAppend(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif), g[5])
                g[6].isNotEmpty() -> pushAndAppend(SpanStyle(color = link), g[6])
                g[8].isNotEmpty() -> pushAndAppend(SpanStyle(fontStyle = FontStyle.Italic), g[8])
                else -> append('\n')
            }
            last = m.range.last + 1
        }
        append(s.substring(last))
    }

    private fun AnnotatedString.Builder.pushAndAppend(style: SpanStyle, text: String) {
        val start = length
        append(text)
        addStyle(style, start, length)
    }
}
