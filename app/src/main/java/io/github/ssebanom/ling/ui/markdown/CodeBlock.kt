package io.github.ssebanom.ling.ui.markdown

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ssebanom.ling.ui.theme.LingColors
import kotlinx.coroutines.delay

@Composable
fun CodeBlock(lang: String, code: String, running: Boolean = false, modifier: Modifier = Modifier) {
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val palette = LingColors.code()
    val highlighted = remember(code, lang, palette) { Highlighter.highlight(code, lang, palette) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.bg,
        contentColor = palette.fg,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lang.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelMedium, color = palette.comment,
                )
                if (running) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = palette.comment)
                }
                Spacer(Modifier.weight(1f))
                Text("${code.lines().size}줄", style = MaterialTheme.typography.labelSmall, color = palette.comment)
                IconButton(onClick = { clip.setText(AnnotatedString(code)); copied = true }) {
                    AnimatedContent(copied, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "copy") { done ->
                        Icon(
                            if (done) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "복사",
                            Modifier.size(16.dp), tint = if (done) palette.string else palette.comment,
                        )
                    }
                }
            }
            SelectionContainer {
                Text(
                    highlighted,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    softWrap = false,
                )
            }
        }
    }
}

data class CodePalette(
    val bg: Color, val fg: Color, val keyword: Color, val string: Color, val comment: Color,
    val number: Color, val type: Color, val fn: Color,
)

/** 언어 무관 정규식 하이라이터(주석·문자열·숫자·키워드·타입·함수 호출) */
object Highlighter {
    private val KEYWORDS = (
        "abstract and as assert async await break case catch class const continue def defer del do elif else enum except " +
            "export extends false final finally fn for from fun func function go if impl import in interface is lambda let " +
            "match mod module mut new nil none not null object or override package pass private protected pub public raise " +
            "return select self static struct super switch this throw throws trait true try type typeof use val var void " +
            "when where while with yield True False None echo then fi done esac local"
        ).split(' ').toSet()

    private val TOKEN = Regex(
        "(?<comment>//[^\\n]*|#(?![{\\[])[^\\n]*|/\\*[\\s\\S]*?\\*/|--[^\\n]*)" +
            "|(?<string>\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`[^`]*`)" +
            "|(?<number>\\b0[xX][0-9a-fA-F_]+\\b|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFLlu]?\\b)" +
            "|(?<word>[A-Za-z_][A-Za-z0-9_]*)",
    )

    fun highlight(code: String, lang: String, p: CodePalette): AnnotatedString = buildAnnotatedString {
        val l = lang.lowercase()
        val hashComments = l in setOf("python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb", "yaml", "yml", "toml", "r", "perl", "") ||
            l.isEmpty()
        val dashComments = l in setOf("sql", "lua", "haskell", "hs")
        append(code)
        if (code.length > 20_000) return@buildAnnotatedString
        for (m in TOKEN.findAll(code)) {
            val g = m.groups
            val style = when {
                g["comment"] != null -> {
                    val v = m.value
                    when {
                        v.startsWith("#") && !hashComments -> null
                        v.startsWith("--") && !dashComments -> null
                        else -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
                    }
                }
                g["string"] != null -> SpanStyle(color = p.string)
                g["number"] != null -> SpanStyle(color = p.number)
                g["word"] != null -> {
                    val w = m.value
                    val next = code.getOrNull(m.range.last + 1)
                    when {
                        w in KEYWORDS -> SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold)
                        next == '(' -> SpanStyle(color = p.fn)
                        w.first().isUpperCase() -> SpanStyle(color = p.type)
                        else -> null
                    }
                }
                else -> null
            } ?: continue
            addStyle(style, m.range.first, m.range.last + 1)
        }
    }
}
