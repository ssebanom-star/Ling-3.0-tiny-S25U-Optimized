package io.github.ssebanom.ling.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.Backend
import io.github.ssebanom.ling.data.Conversation
import io.github.ssebanom.ling.data.ModelCatalog
import io.github.ssebanom.ling.runtime.EngineStatus
import io.github.ssebanom.ling.ui.components.ChipRow
import io.github.ssebanom.ling.ui.components.Pill
import io.github.ssebanom.ling.ui.theme.LingColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 드로어: 새 대화 · 검색 · 날짜별 대화 목록 · 화면 이동 */
@Composable
fun AppDrawer(
    c: AppContainer,
    open: Boolean,
    currentId: Long?,
    busy: Boolean,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onDeleted: (Long) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var convs by remember { mutableStateOf(listOf<Conversation>()) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    LaunchedEffect(open, currentId, reload) { if (open) convs = c.conversations.listConversations() }

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)) {
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("Ling", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onNew, enabled = !busy) { Icon(Icons.Outlined.EditNote, "새 대화") }
            }
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("대화 검색", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val filtered = convs.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        val groups = filtered.groupBy { bucket(it.updatedAt) }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (filtered.isEmpty()) item {
                Text(if (convs.isEmpty()) "아직 대화가 없습니다" else "검색 결과 없음", Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
            for ((label, list) in groups) {
                item(key = "h-$label") {
                    Text(label, Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                items(list, key = { it.id }) { conv ->
                    ConvRow(
                        conv, selected = conv.id == currentId, enabled = !busy,
                        onClick = { onOpen(conv.id) },
                        onRename = { renaming = conv },
                        onDelete = {
                            scope.launch {
                                c.conversations.deleteConversation(conv.id)
                                convs = convs - conv
                                onDeleted(conv.id)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(12.dp).navigationBarsPadding()) {
            NavRow(Icons.Outlined.Download, "모델") { onNavigate("models") }
            NavRow(Icons.Outlined.Insights, "툴 활동") { onNavigate("activity") }
            NavRow(Icons.Outlined.Speed, "성능") { onNavigate("perf") }
            NavRow(Icons.Outlined.Settings, "설정") { onNavigate("settings") }
        }
    }

    renaming?.let { conv ->
        var t by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("이름 바꾸기") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true, shape = MaterialTheme.shapes.medium) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.conversations.updateConversation(conv.copy(title = t.trim().ifEmpty { conv.title })); reload++ }
                    renaming = null
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("취소") } },
        )
    }
}

private fun bucket(ts: Long): String {
    val d = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        d == today -> "오늘"
        d == today.minusDays(1) -> "어제"
        d.isAfter(today.minusDays(7)) -> "지난 7일"
        d.isAfter(today.minusDays(30)) -> "지난 30일"
        else -> "${d.year}년 ${d.monthValue}월"
    }
}

@Composable
private fun ConvRow(
    conv: Conversation, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(conv.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "더보기", Modifier.size(18.dp)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("이름 바꾸기") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(
                    text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 상단 모델 칩 → 내려받은 모델 · 백엔드 빠른 전환 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(c: AppContainer, onDismiss: () -> Unit, onManage: () -> Unit) {
    val settings by c.settings.flow.collectAsState(initial = AppSettings())
    val status by c.inference.status.collectAsState()
    val chat by c.chat.state.collectAsState()
    val files = remember { c.models.listModels() }
    var working by remember { mutableStateOf(false) }

    fun apply(change: (AppSettings) -> AppSettings) {
        if (working || chat.busy) return
        working = true
        c.appScope.launch {
            c.settings.update(change)
            c.inference.lock.withLock { c.inference.ensureLoaded() }
            working = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("모델", style = MaterialTheme.typography.titleLarge)
            if (files.isEmpty()) Text("내려받은 모델이 없습니다.", color = MaterialTheme.colorScheme.outline)
            files.forEach { f ->
                val v = ModelCatalog.byFile(f.name)
                val fam = c.inference.familyFor(f)
                val active = settings.modelFile == f.name
                Surface(
                    onClick = { if (!active) apply { it.copy(modelFile = f.name) } },
                    shape = MaterialTheme.shapes.medium,
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (active) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fam.label, style = MaterialTheme.typography.titleSmall)
                            Text("${v?.id ?: f.name} · ${"%.1f".format(f.length() / 1e9)} GB", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (v?.experimental == true) Pill("실험", LingColors.warning)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("실행 장치", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val cur = (status as? EngineStatus.Ready)?.backend
                Text(
                    when {
                        working -> "전환 중…"
                        status is EngineStatus.Loading -> "로드 중"
                        cur != null -> "현재 ${cur.name}"
                        else -> "미로드"
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChipRow(null, listOf(Backend.GPU, Backend.CPU, Backend.NPU), settings.backend, display = { it.name }, enabled = !working && !chat.busy) { b ->
                apply { it.copy(backend = b) }
            }
            c.inference.lastFallback?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = LingColors.warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onManage) { Text("모델 관리") }
                OutlinedButton(onClick = { c.appScope.launch { c.inference.lock.withLock { c.inference.unload() } } }, enabled = !chat.busy && !working) {
                    Text("메모리에서 내리기")
                }
            }
        }
    }
}
