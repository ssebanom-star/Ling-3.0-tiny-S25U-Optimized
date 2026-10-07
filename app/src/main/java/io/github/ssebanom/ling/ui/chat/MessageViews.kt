package io.github.ssebanom.ling.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ssebanom.ling.data.MessageStats
import io.github.ssebanom.ling.data.StoredMessage
import io.github.ssebanom.ling.runtime.Phase
import io.github.ssebanom.ling.runtime.Streaming
import io.github.ssebanom.ling.ui.components.ShimmerText
import io.github.ssebanom.ling.ui.markdown.MarkdownText
import io.github.ssebanom.ling.ui.tools.ToolRunCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserBubble(m: StoredMessage, busy: Boolean, onEdit: () -> Unit) {
    val clip = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        Box {
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 560.dp).combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Text(
                    m.message.content, Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("수정 후 다시 보내기") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    enabled = !busy, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("복사") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menu = false; clip.setText(AnnotatedString(m.message.content)) })
            }
        }
    }
}

/** 응답 1건: 머리글 · (추론 · 본문 · 툴 카드) × 라운드 · 실시간 부분 · 동작 줄 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantTurn(
    row: ChatRow.Turn,
    modelLabel: String?,
    streaming: Streaming?,
    busy: Boolean,
    onRegenerate: () -> Unit,
) {
    val steps = remember(row.entries) { ChatTurns.steps(row.entries) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AssistantHeader(modelLabel, active = row.live)
        steps.forEach { st ->
            st.reasoning?.let { ThinkingCard(it, live = false, elapsedSec = null) }
            if (st.content.isNotBlank()) MarkdownText(st.content)
            st.tools.forEach { tp -> ToolRunCard(tp.call.name, tp.call.arguments, tp.result) }
        }
        if (row.live && streaming != null) LiveSection(streaming)
        if (!row.live && steps.isNotEmpty()) ActionRow(steps, canRegenerate = row.isLast && !busy, onRegenerate = onRegenerate)
    }
}

@Composable
private fun AssistantHeader(label: String?, active: Boolean) {
    val t = rememberInfiniteTransition(label = "spark")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400)), label = "r")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp).rotate(if (active) rot else 0f), tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(label ?: "Ling", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 생성 중 부분: 준비/프롬프트 처리 표시, 실시간 추론, 실시간 본문 */
@Composable
private fun LiveSection(s: Streaming) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.phase) {
        while (s.phase == Phase.THINKING) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    AnimatedContent(
        targetState = when (s.phase) {
            Phase.LOADING -> "모델 준비 중"
            Phase.PREFILL -> "프롬프트 읽는 중"
            else -> ""
        },
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "phase",
    ) { label ->
        if (label.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypingDots()
                Spacer(Modifier.width(10.dp))
                val pct = if (s.phase == Phase.PREFILL && s.prefillTotal > 0) " · ${s.prefillDone * 100 / s.prefillTotal}%" else ""
                ShimmerText("$label$pct", MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.phase == Phase.PREFILL && s.prefillTotal > 0) {
                val p by animateFloatAsState(s.prefillDone.toFloat() / s.prefillTotal, label = "pf")
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(0.6f).clip(CircleShape))
            }
        }
    }
    val generating = s.phase == Phase.THINKING || s.phase == Phase.ANSWERING
    if (generating && s.reasoning.isNotEmpty()) {
        val end = if (s.thinkEndedAt > 0) s.thinkEndedAt else now
        ThinkingCard(s.reasoning, live = s.phase == Phase.THINKING, elapsedSec = ((end - s.startedAt) / 1000).coerceAtLeast(0))
    } else if (s.phase == Phase.THINKING) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypingDots(); Spacer(Modifier.width(10.dp))
            ShimmerText("생각 중", MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (generating && s.content.isNotEmpty()) MarkdownText(s.content, streaming = true)
    else if (s.phase == Phase.ANSWERING) TypingDots()
    if (generating && s.tokens > 0) {
        val hot = if (s.thermal.ordinal >= 2) " · 발열 ${s.thermal}" else ""
        Text("${s.tokens} tok · ${"%.1f".format(s.tps)} tok/s$hot", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** 추론 과정 카드: 진행 중이면 빛 효과와 최근 내용 미리보기, 끝나면 접힘 */
@Composable
fun ThinkingCard(text: String, live: Boolean, elapsedSec: Long?) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lightbulb, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                val label = when {
                    live -> "생각 중" + (elapsedSec?.let { " · ${it}초" } ?: "")
                    elapsedSec != null -> "${elapsedSec}초 동안 생각함"
                    else -> "생각 과정"
                }
                if (live) ShimmerText(label, MaterialTheme.typography.labelLarge, muted, Modifier.weight(1f))
                else Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = muted)
                Text("${text.length}자", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(rot).size(20.dp), tint = MaterialTheme.colorScheme.outline)
            }
            if (live && !open) {
                // 최근 추론 미리보기(위쪽 페이드)
                Text(
                    text.takeLast(420).trimStart(),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp).padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black), blendMode = BlendMode.DstIn)
                        },
                    style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 5, overflow = TextOverflow.Clip,
                )
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    Box(Modifier.width(2.dp).heightIn(min = 16.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.width(10.dp))
                    MarkdownText(text, color = muted, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(steps: List<Step>, canRegenerate: Boolean, onRegenerate: () -> Unit) {
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val stats = steps.mapNotNull { it.msg.stats }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-10).dp)) {
            IconButton(onClick = {
                clip.setText(AnnotatedString(steps.map { it.content }.filter { it.isNotBlank() }.joinToString("\n\n")))
                copied = true
            }) {
                AnimatedContent(copied, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "c") { d ->
                    Icon(if (d) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "복사", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
            if (canRegenerate) IconButton(onClick = onRegenerate) {
                Icon(Icons.Outlined.Refresh, "다시 생성", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
            stats.lastOrNull()?.let { s ->
                Row(
                    Modifier.clip(CircleShape).clickable { showStats = !showStats }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Speed, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Text("${"%.1f".format(s.decodeTps)} tok/s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        AnimatedVisibility(showStats && stats.isNotEmpty()) { StatsDetail(stats) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsDetail(stats: List<MessageStats>) {
    val dec = stats.sumOf { it.decodeTokens }
    val decMs = stats.sumOf { it.decodeMs }
    val pre = stats.sumOf { it.prefillTokens }
    val reused = stats.sumOf { it.reusedTokens }
    val items = listOf(
        "생성" to "$dec tok",
        "속도" to "${"%.1f".format(if (decMs > 0) dec * 1000 / decMs else 0.0)} tok/s",
        "첫 토큰" to "${"%.0f".format(stats.first().ttftMs)} ms",
        "프롬프트" to "$pre tok",
        "캐시 재사용" to "$reused tok",
        "라운드" to "${stats.size}",
    ) + if (stats.any { it.restored }) listOf("체크포인트" to "복원") else emptyList()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (k, v) ->
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(v, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 점 3개가 차례로 튀는 입력 표시 */
@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i",
            )
            Box(Modifier.offset(y = (-4 * y).dp).size(7.dp).clip(CircleShape).background(color.copy(alpha = 0.4f + 0.6f * y)))
        }
    }
}
