package io.github.ssebanom.ling.ui.markdown

import android.content.Context
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** Markwon 인스턴스는 색·글자 크기 조합마다 하나 (LaTeX 렌더러 캐시 포함) */
private data class MdStyle(
    val text: Int, val muted: Int, val link: Int, val codeBg: Int, val quote: Int, val border: Int,
    val textPx: Float, val accent: Int, val onLink: Int,
)

private val cache = HashMap<MdStyle, Markwon>()

private fun markwonFor(ctx: Context, s: MdStyle): Markwon = synchronized(cache) {
    cache.getOrPut(s) {
        Markwon.builder(ctx)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(s.textPx * 1.05f, s.textPx * 1.15f) { b ->
                b.inlinesEnabled(true)
                b.theme().textColor(s.text)
                b.theme().blockFitCanvas(false)
            })
            .usePlugin(TablePlugin.create { t: TableTheme.Builder ->
                t.tableBorderColor(s.border).tableBorderWidth(2).tableCellPadding(18)
                    .tableHeaderRowBackgroundColor(s.codeBg).tableOddRowBackgroundColor(0).tableEvenRowBackgroundColor(0)
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(s.link, s.muted, s.onLink))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(s.link)
                        .codeTextColor(s.accent)
                        .codeBackgroundColor(s.codeBg)
                        .codeBlockBackgroundColor(s.codeBg)
                        .codeTypeface(Typeface.MONOSPACE)
                        .blockQuoteColor(s.quote)
                        .blockQuoteWidth(6)
                        .listItemColor(s.muted)
                        .bulletWidth((s.textPx * 0.32f).toInt())
                        .headingBreakHeight(0)
                        .headingTextSizeMultipliers(floatArrayOf(1.45f, 1.3f, 1.17f, 1.05f, 1f, 0.95f))
                        .thematicBreakColor(s.border)
                        .isLinkUnderlined(false)
                }
            })
            .build()
    }
}

/**
 * AI 응답용 리치 텍스트: 마크다운(표·체크리스트·취소선·링크) + LaTeX + 코드 블록(Compose).
 * 빈 줄 단위 블록마다 따로 그려 스트리밍 중엔 마지막 블록만 다시 렌더링된다.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 16.sp,
    streaming: Boolean = false,
) {
    val blocks = remember(text) { MdBlocks.split(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is MdBlock.Code -> CodeBlock(b.lang, b.code, running = streaming && i == blocks.lastIndex && !b.closed)
                is MdBlock.Text -> MarkdownBlock(b.md, color, fontSize)
                is MdBlock.Math -> MathBlock(b.latex, b.closed, color, fontSize)
                is MdBlock.Table -> TableBlock(b, color)
            }
        }
    }
}

@Composable
fun MarkdownBlock(md: String, color: Color = MaterialTheme.colorScheme.onSurface, fontSize: TextUnit = 16.sp) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val textPx = with(density) { fontSize.toPx() }
    val style = MdStyle(
        text = color.toArgb(), muted = cs.onSurfaceVariant.toArgb(), link = cs.primary.toArgb(),
        codeBg = cs.surfaceContainerHighest.toArgb(), quote = cs.primary.copy(alpha = 0.5f).toArgb(),
        border = cs.outlineVariant.toArgb(), textPx = textPx, accent = cs.tertiary.toArgb(), onLink = cs.onPrimary.toArgb(),
    )
    val markwon = remember(style) { markwonFor(ctx.applicationContext, style) }
    val normalized = md
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { c ->
            TextView(c).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.18f)
                includeFontPadding = false
            }
        },
        update = { tv ->
            tv.setTextColor(style.text)
            tv.setLinkTextColor(style.link)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
            if (tv.tag != Pair(normalized, style)) {
                tv.tag = Pair(normalized, style)
                markwon.setMarkdown(tv, normalized)
            }
        },
    )
}
