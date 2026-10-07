package io.github.ssebanom.ling.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.ToolRun
import io.github.ssebanom.ling.data.ToolStatus
import io.github.ssebanom.ling.tools.ToolGroup
import io.github.ssebanom.ling.ui.components.EmptyHint
import io.github.ssebanom.ling.ui.components.IconBadge
import io.github.ssebanom.ling.ui.components.ScreenScaffold
import io.github.ssebanom.ling.ui.components.SectionCard
import io.github.ssebanom.ling.ui.components.StatTile
import io.github.ssebanom.ling.ui.theme.LingColors
import io.github.ssebanom.ling.ui.theme.MonoSmall
import io.github.ssebanom.ling.ui.tools.StatusPill
import io.github.ssebanom.ling.ui.tools.ToolMetas
import io.github.ssebanom.ling.ui.tools.ToolResultView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class StatusFilter(val label: String) { ALL("전체"), OK("성공"), ERROR("오류"), DENIED("거부") }

/** 툴 활동: 요약 · 툴별/일별 차트 · 필터 가능한 기록 · 상세 · 내보내기 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(c: AppContainer, onBack: () -> Unit, openChat: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by c.chat.toolRunTick.collectAsState()
    var runs by remember { mutableStateOf<List<ToolRun>?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(tick, reload) { runs = c.conversations.toolRuns(limit = 2000) }
    var statusFilter by remember { mutableStateOf(StatusFilter.ALL) }
    var groupFilter by remember { mutableStateOf<ToolGroup?>(null) }
    var detail by remember { mutableStateOf<ToolRun?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "툴 활동", onBack = onBack,
        actions = {
            IconButton(onClick = {
                val arr = JSONArray()
                runs.orEmpty().forEach { r ->
                    arr.put(JSONObject().apply {
                        put("time", r.startedAt); put("tool", r.tool); put("group", r.group); put("status", r.status.name)
                        put("duration_ms", r.durationMs); put("conversation", r.convId)
                        put("args", JSONObject(r.args.mapValues { it.value?.toString() })); put("result", r.result)
                    })
                }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, arr.toString(2)), "툴 기록 내보내기"))
            }, enabled = !runs.isNullOrEmpty()) { Icon(Icons.Outlined.Share, "내보내기") }
            IconButton(onClick = { confirmClear = true }, enabled = !runs.isNullOrEmpty()) { Icon(Icons.Outlined.DeleteSweep, "기록 삭제") }
        },
    ) { pad ->
        val all = runs
        if (all == null) return@ScreenScaffold
        if (all.isEmpty()) {
            EmptyHint(Icons.Outlined.Insights, "아직 툴 기록이 없습니다", "설정에서 툴을 켜고 모델에게 검색·계산·파일 작업을 맡겨 보세요.", Modifier.padding(pad))
            return@ScreenScaffold
        }
        val filtered = all.filter { r ->
            (statusFilter == StatusFilter.ALL || r.status.name == statusFilter.name) &&
                (groupFilter == null || r.group == groupFilter!!.name)
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Summary(all) }
            item { ByToolChart(all) }
            item { DailyChart(all) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("기록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${filtered.size}건", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusFilter.entries.forEach { f ->
                            FilterChip(statusFilter == f, { statusFilter = f }, label = { Text(f.label) }, shape = CircleShape)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(groupFilter == null, { groupFilter = null }, label = { Text("모든 묶음") }, shape = CircleShape)
                        ToolGroup.entries.filter { g -> all.any { it.group == g.name } }.forEach { g ->
                            val col = LingColors.group(g)
                            FilterChip(
                                groupFilter == g, { groupFilter = if (groupFilter == g) null else g }, label = { Text(g.label) }, shape = CircleShape,
                                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(col)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = col.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            }
            val byDay = filtered.groupBy { day(it.startedAt) }
            for ((d, list) in byDay) {
                item(key = "d-$d") {
                    Text(dayLabel(d), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                items(list, key = { it.id }) { r -> RunRow(r, Modifier.animateItem()) { detail = r } }
            }
        }
    }

    detail?.let { r ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            val meta = ToolMetas.of(r.tool)
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(meta.icon, LingColors.group(meta.group), 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meta.label, style = MaterialTheme.typography.titleLarge)
                        Text("${r.tool} · ${TIME.format(Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(r.status, r.durationMs)
                }
                if (r.args.isNotEmpty()) {
                    Text("인자", style = MaterialTheme.typography.titleSmall)
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            r.args.forEach { (k, v) ->
                                Row {
                                    Text(k, style = MonoSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(96.dp))
                                    Text(v.toString(), style = MonoSmall)
                                }
                            }
                        }
                    }
                }
                Text("결과", style = MaterialTheme.typography.titleSmall)
                ToolResultView(r.tool, r.result)
                OutlinedButton(onClick = { detail = null; openChat(r.convId) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("대화 열기")
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("툴 기록을 모두 지울까요?") },
        text = { Text("대화 내용은 지워지지 않습니다.") },
        confirmButton = {
            TextButton(onClick = { scope.launch { c.conversations.clearToolRuns(); reload++ }; confirmClear = false }) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
    )
}

private val TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm:ss")
private val HM = DateTimeFormatter.ofPattern("HH:mm")

private fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(d: LocalDate): String {
    val t = LocalDate.now()
    return when (d) {
        t -> "오늘"
        t.minusDays(1) -> "어제"
        else -> "${d.monthValue}월 ${d.dayOfMonth}일"
    }
}

@Composable
internal fun Summary(all: List<ToolRun>) {
    val ok = all.count { it.status == ToolStatus.OK }
    val avg = all.map { it.durationMs }.average()
    val top = all.groupingBy { it.tool }.eachCount().maxByOrNull { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("총 실행", "${all.size}", Modifier.weight(1f), sub = "최근 ${all.size}건 기준")
            StatTile("성공률", "${ok * 100 / all.size}%", Modifier.weight(1f), accent = LingColors.success,
                sub = "오류 ${all.count { it.status == ToolStatus.ERROR }} · 거부 ${all.count { it.status == ToolStatus.DENIED }}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("평균 소요", if (avg < 1000) "${avg.toInt()}ms" else "${"%.1f".format(avg / 1000)}s", Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary)
            StatTile("가장 많이 사용", top?.let { ToolMetas.of(it.key).label } ?: "-", Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary, sub = top?.let { "${it.value}회" })
        }
    }
}

/** 툴별 가로 막대(성공/오류/거부 누적), 등장 시 늘어나는 애니메이션 */
@Composable
internal fun ByToolChart(all: List<ToolRun>) {
    val byTool = all.groupBy { it.tool }.entries.sortedByDescending { it.value.size }
    val max = byTool.maxOf { it.value.size }.toFloat()
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    val grow = remember { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(all.size) { if (!preview) { grow.snapTo(0f); grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) } }
    val err = MaterialTheme.colorScheme.error
    val warn = LingColors.warning
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    SectionCard("툴별 사용", Icons.Outlined.BarChart, subtitle = "막대: 성공 · 오류 · 거부") {
        byTool.forEach { (tool, list) ->
            val meta = ToolMetas.of(tool)
            val col = LingColors.group(meta.group)
            val okN = list.count { it.status == ToolStatus.OK }
            val errN = list.count { it.status == ToolStatus.ERROR }
            val denN = list.size - okN - errN
            val avg = list.map { it.durationMs }.average()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(meta.icon, null, Modifier.size(16.dp), tint = col)
                Spacer(Modifier.width(8.dp))
                Text(meta.label, Modifier.width(92.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    val full = size.width * (list.size / max) * grow.value
                    var x = 0f
                    for ((n, color) in listOf(okN to col, errN to err, denN to warn)) {
                        if (n == 0) continue
                        val w = full * n / list.size
                        drawRoundRect(color, Offset(x, 0f), Size(w, size.height), r)
                        x += w
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(56.dp)) {
                    Text("${list.size}회", style = MaterialTheme.typography.labelMedium)
                    Text(if (avg < 1000) "${avg.toInt()}ms" else "${"%.1f".format(avg / 1000)}s",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** 최근 14일 일별 막대(툴 묶음별 누적) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyChart(all: List<ToolRun>) {
    val today = LocalDate.now()
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = all.groupBy { day(it.startedAt) }
    val max = days.maxOf { byDay[it]?.size ?: 0 }.coerceAtLeast(1)
    val groups = ToolGroup.entries
    val colors = groups.map { LingColors.group(it) }
    val other = LingColors.group(null)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    val grow = remember { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(all.size) { if (!preview) { grow.snapTo(0f); grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) } }
    SectionCard("최근 14일", Icons.Outlined.CalendarMonth, subtitle = "하루 최대 ${max}회") {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.62f
            for (k in 1..3) {
                val y = size.height * (1 - k / 4f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            days.forEachIndexed { i, d ->
                val list = byDay[d].orEmpty()
                var y = size.height
                val x = i * slot + (slot - barW) / 2
                val counts = groups.map { g -> list.count { it.group == g.name } } + list.count { r -> groups.none { it.name == r.group } }
                counts.forEachIndexed { gi, n ->
                    if (n == 0) return@forEachIndexed
                    val h = size.height * n / max * grow.value
                    drawRoundRect(
                        if (gi < colors.size) colors[gi] else other, Offset(x, y - h), Size(barW, h),
                        CornerRadius(barW / 4),
                    )
                    y -= h
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    if (i % 3 == 1 || i == days.lastIndex) "${d.dayOfMonth}" else "",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.forEachIndexed { i, g ->
                if (all.none { it.group == g.name }) return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                    Spacer(Modifier.width(4.dp))
                    Text(g.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RunRow(r: ToolRun, modifier: Modifier, onClick: () -> Unit) {
    val meta = ToolMetas.of(r.tool)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(meta.icon, LingColors.group(meta.group), 34)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meta.label, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Text(HM.format(Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(ToolMetas.summary(r.tool, r.args), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(r.status, r.durationMs)
    }
}
