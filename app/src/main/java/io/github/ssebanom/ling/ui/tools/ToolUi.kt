package io.github.ssebanom.ling.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.data.ToolStatus
import io.github.ssebanom.ling.tools.ToolGroup
import io.github.ssebanom.ling.ui.components.IconBadge
import io.github.ssebanom.ling.ui.components.Pill
import io.github.ssebanom.ling.ui.markdown.MarkdownText
import io.github.ssebanom.ling.ui.theme.LingColors
import io.github.ssebanom.ling.ui.theme.MonoSmall
import org.json.JSONObject

/** 툴 이름 → 표시 이름·아이콘·묶음 */
data class ToolMeta(val label: String, val icon: ImageVector, val group: ToolGroup?)

object ToolMetas {
    private val map = mapOf(
        "calculator" to ToolMeta("계산기", Icons.Outlined.Calculate, ToolGroup.BASIC),
        "current_time" to ToolMeta("현재 시각", Icons.Outlined.Schedule, ToolGroup.BASIC),
        "device_status" to ToolMeta("기기 상태", Icons.Outlined.PhoneAndroid, ToolGroup.BASIC),
        "web_search" to ToolMeta("웹 검색", Icons.Outlined.Search, ToolGroup.WEB),
        "fetch_url" to ToolMeta("웹 페이지 읽기", Icons.Outlined.Language, ToolGroup.WEB),
        "open_app" to ToolMeta("앱 열기", Icons.Outlined.Apps, ToolGroup.DEVICE),
        "open_url" to ToolMeta("링크 열기", Icons.AutoMirrored.Outlined.OpenInNew, ToolGroup.DEVICE),
        "set_alarm" to ToolMeta("알람 설정", Icons.Outlined.Alarm, ToolGroup.DEVICE),
        "set_timer" to ToolMeta("타이머 설정", Icons.Outlined.Timer, ToolGroup.DEVICE),
        "get_clipboard" to ToolMeta("클립보드 읽기", Icons.Outlined.ContentPaste, ToolGroup.DEVICE),
        "set_clipboard" to ToolMeta("클립보드 복사", Icons.Outlined.ContentPasteGo, ToolGroup.DEVICE),
        "list_files" to ToolMeta("파일 목록", Icons.Outlined.FolderOpen, ToolGroup.FILES),
        "search_files" to ToolMeta("파일 검색", Icons.Outlined.Search, ToolGroup.FILES),
        "read_text_file" to ToolMeta("파일 읽기", Icons.AutoMirrored.Outlined.Article, ToolGroup.FILES),
        "write_text_file" to ToolMeta("파일 쓰기", Icons.Outlined.EditNote, ToolGroup.FILES),
        "read_screen" to ToolMeta("화면 읽기", Icons.Outlined.Visibility, ToolGroup.SCREEN),
        "tap" to ToolMeta("탭", Icons.Outlined.TouchApp, ToolGroup.SCREEN),
        "type_text" to ToolMeta("텍스트 입력", Icons.Outlined.Keyboard, ToolGroup.SCREEN),
        "scroll" to ToolMeta("스크롤", Icons.Outlined.SwipeVertical, ToolGroup.SCREEN),
        "press_key" to ToolMeta("키 누르기", Icons.Outlined.KeyboardCommandKey, ToolGroup.SCREEN),
    )

    fun of(name: String): ToolMeta = map[name] ?: ToolMeta(name, Icons.Outlined.Build, null)

    /** 호출 인자 한 줄 요약(가장 의미 있는 값 우선) */
    fun summary(name: String, args: Map<String, Any?>): String {
        fun a(k: String) = args[k]?.toString()?.takeIf { it.isNotBlank() }
        return when (name) {
            "calculator" -> a("expression")
            "web_search", "search_files" -> a("query")?.let { "“$it”" }
            "fetch_url", "open_url" -> a("url")?.let(::prettyUrl)
            "open_app" -> a("name")
            "set_alarm" -> listOfNotNull(a("hour")?.let { h -> "$h:${(a("minute") ?: "0").padStart(2, '0')}" }, a("label")).joinToString(" · ")
            "set_timer" -> a("seconds")?.let { "${it}초" }
            "list_files", "read_text_file", "write_text_file" -> a("path")
            "type_text", "set_clipboard" -> a("text")?.let { "“${it.take(60)}”" }
            "tap" -> a("index")?.let { "#$it" }
            "scroll" -> a("direction")
            "press_key" -> a("key")
            else -> null
        } ?: args.entries.joinToString(", ") { "${it.key}=${it.value.toString().take(40)}" }
    }

    fun prettyUrl(u: String): String = u.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}

fun statusOf(result: String?): ToolStatus? = when {
    result == null -> null
    result.startsWith("error: the user declined") -> ToolStatus.DENIED
    result.startsWith("error:") -> ToolStatus.ERROR
    else -> ToolStatus.OK
}

@Composable
fun StatusPill(status: ToolStatus?, durationMs: Long? = null) {
    val dur = durationMs?.let { if (it < 1000) " · ${it}ms" else " · ${"%.1f".format(it / 1000.0)}s" }.orEmpty()
    when (status) {
        null -> Pill("실행 중", MaterialTheme.colorScheme.primary)
        ToolStatus.OK -> Pill("완료$dur", LingColors.success, icon = Icons.Outlined.CheckCircle)
        ToolStatus.ERROR -> Pill("오류$dur", MaterialTheme.colorScheme.error, icon = Icons.Outlined.ErrorOutline)
        ToolStatus.DENIED -> Pill("거부됨", LingColors.warning, icon = Icons.Outlined.Block)
    }
}

/**
 * 툴 호출 1건 카드: 아이콘·이름·인자 요약·상태 + 펼치면 결과(툴별 보기 좋은 형태).
 * result == null 이면 실행 중.
 */
@Composable
fun ToolRunCard(
    name: String,
    args: Map<String, Any?>,
    result: String?,
    modifier: Modifier = Modifier,
    durationMs: Long? = null,
    initiallyOpen: Boolean = false,
) {
    val meta = ToolMetas.of(name)
    val tint = LingColors.group(meta.group)
    val status = statusOf(result)
    var open by remember { mutableStateOf(initiallyOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = result != null) { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(meta.icon, tint, size = 30)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(meta.label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        ToolMetas.summary(name, args), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(status, durationMs)
                if (result != null) Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(rot).size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
            if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = tint, trackColor = Color.Transparent)
            AnimatedVisibility(open && result != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    ToolResultView(name, result.orEmpty())
                }
            }
        }
    }
}

/** 툴별 결과 표시 */
@Composable
fun ToolResultView(name: String, result: String) {
    if (result.startsWith("error:")) {
        Text(result.removePrefix("error:").trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        return
    }
    when (name) {
        "web_search" -> SearchResults(result)
        "fetch_url", "read_text_file" -> PagePreview(result)
        "list_files", "search_files" -> FileList(result)
        "device_status" -> JsonChips(result)
        "calculator", "current_time", "set_alarm", "set_timer", "open_app", "open_url", "set_clipboard" ->
            Text(result, style = MaterialTheme.typography.bodyMedium)
        else -> RawResult(result)
    }
}

private data class Hit(val title: String, val url: String, val snippet: String)

private fun parseSearch(r: String): Pair<String?, List<Hit>> {
    val lines = r.lines()
    val head = lines.firstOrNull()?.takeIf { it.startsWith("Search results") }
    val hits = ArrayList<Hit>()
    var i = 0
    val num = Regex("^(\\d+)\\. (.*)$")
    while (i < lines.size) {
        val m = num.find(lines[i])
        if (m != null) {
            val title = m.groupValues[2]
            val url = lines.getOrNull(i + 1)?.trim().orEmpty()
            val snip = lines.getOrNull(i + 2)?.takeIf { it.startsWith("   ") && num.find(it) == null }?.trim().orEmpty()
            hits += Hit(title, url, snip)
            i += if (snip.isEmpty()) 2 else 3
        } else i++
    }
    return head to hits
}

@Composable
private fun SearchResults(r: String) {
    val ctx = LocalContext.current
    val (head, hits) = remember(r) { parseSearch(r) }
    if (hits.isEmpty()) { RawResult(r); return }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        head?.let { Text(it.removeSuffix(":"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
        hits.forEach { h ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(h.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    .padding(10.dp),
            ) {
                Text(ToolMetas.prettyUrl(h.url).substringBefore('/'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(h.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (h.snippet.isNotEmpty()) Text(h.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PagePreview(r: String) {
    var all by remember { mutableStateOf(false) }
    val shown = if (all || r.length <= 1500) r else r.take(1500) + "\n\n…"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
    ) {
        MarkdownText(shown, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp))
        if (r.length > 1500) Text(
            if (all) "접기" else "전체 보기 (${r.length}자)",
            Modifier.padding(top = 6.dp).clickable { all = !all },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FileList(r: String) {
    val lines = remember(r) { r.lines().filter { it.isNotBlank() } }
    val rows = lines.filter { it.startsWith("[dir]") || it.startsWith("[file]") }
    if (rows.isEmpty()) { Text(r, style = MaterialTheme.typography.bodySmall); return }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        lines.firstOrNull()?.takeIf { !it.startsWith("[") }?.let {
            Text(it, Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        rows.take(60).forEach { l ->
            val dir = l.startsWith("[dir]")
            val body = l.substringAfter(']').trim()
            val parts = body.split("  ").filter { it.isNotBlank() }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (dir) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(16.dp),
                    tint = if (dir) LingColors.group(ToolGroup.FILES) else MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(8.dp))
                Text(parts.firstOrNull().orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (parts.size > 1) Text(parts.drop(1).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (rows.size > 60) Text("…외 ${rows.size - 60}개", Modifier.padding(10.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JsonChips(r: String) {
    val o = remember(r) { runCatching { JSONObject(r) }.getOrNull() }
    if (o == null) { RawResult(r); return }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in o.keys()) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(k.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(o.get(k).toString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun RawResult(r: String) {
    var all by remember { mutableStateOf(false) }
    val shown = if (all || r.length <= 1200) r else r.take(1200) + " …"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp)) {
        SelectionContainer {
            Text(shown, style = MonoSmall, modifier = Modifier.horizontalScroll(rememberScrollState()))
        }
        if (r.length > 1200) Text(if (all) "접기" else "전체 보기 (${r.length}자)", Modifier.padding(top = 6.dp).clickable { all = !all },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
