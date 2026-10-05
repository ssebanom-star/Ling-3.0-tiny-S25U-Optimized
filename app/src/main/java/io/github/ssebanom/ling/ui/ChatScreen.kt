package io.github.ssebanom.ling.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.Conversation
import io.github.ssebanom.ling.data.MessageStats
import io.github.ssebanom.ling.data.StoredMessage
import io.github.ssebanom.ling.domain.Role
import io.github.ssebanom.ling.runtime.EngineStatus
import io.github.ssebanom.ling.runtime.Phase
import io.github.ssebanom.ling.runtime.Streaming
import io.github.ssebanom.ling.service.LingService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(c: AppContainer, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val st by c.chat.state.collectAsState()
    val engineStatus by c.inference.status.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val convs = remember { mutableStateListOf<Conversation>() }
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmThinking by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(drawer.isOpen, st.conversation?.id) {
        convs.clear(); convs.addAll(c.conversations.listConversations())
    }
    LaunchedEffect(Unit) { if (st.conversation == null) c.chat.newConversation() }

    ModalNavigationDrawer(
        drawerState = drawer,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                Text("대화 목록", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                NavigationDrawerItem(
                    label = { Text("새 대화") }, selected = false,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { if (!st.busy) c.chat.newConversation(); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider()
                LazyColumn {
                    items(convs, key = { it.id }) { conv ->
                        NavigationDrawerItem(
                            label = { Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = conv.id == st.conversation?.id,
                            onClick = { if (!st.busy) c.chat.openConversation(conv.id); scope.launch { drawer.close() } },
                            badge = {
                                IconButton(onClick = {
                                    scope.launch {
                                        c.conversations.deleteConversation(conv.id)
                                        convs.remove(conv)
                                        if (conv.id == st.conversation?.id) c.chat.newConversation()
                                    }
                                }) { Icon(Icons.Outlined.Delete, "삭제") }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = {
                    Text(st.conversation?.title ?: "Ling-3.0-tiny", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "목록") }
                },
                actions = {
                    val thinking = st.conversation?.thinking ?: true
                    FilterChip(
                        selected = thinking,
                        onClick = {
                            val conv = st.conversation
                            if (conv == null || conv.id == 0L || st.messages.isEmpty()) c.chat.setThinking(!thinking)
                            else confirmThinking = !thinking
                        },
                        label = { Text(if (thinking) "Thinking" else "Instant") },
                        enabled = !st.busy,
                    )
                    IconButton(onClick = { c.chat.newConversation() }, enabled = !st.busy) { Icon(Icons.Outlined.Add, "새 대화") }
                },
            )
            EngineBanner(engineStatus)

            val listState = rememberLazyListState()
            val count = st.messages.size + if (st.busy) 1 else 0
            LaunchedEffect(count, st.streaming.content.length / 200, st.streaming.reasoning.length / 200) {
                if (count > 0) listState.animateScrollToItem(count - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(st.messages.size) { i ->
                    MessageItem(
                        st.messages[i],
                        isLastAssistant = i == st.messages.lastIndex && st.messages[i].message.role == Role.ASSISTANT,
                        busy = st.busy,
                        onEdit = { editing = i },
                        onRegenerate = { LingService.start(ctx); c.chat.regenerate() },
                    )
                }
                if (st.busy) item { StreamingItem(st.streaming) }
            }

            st.error?.let { err ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { c.chat.clearError() }) { Text("닫기") }
                    }
                }
            }
            InputBar(busy = st.busy, onSend = { LingService.start(ctx); c.chat.send(it) }, onStop = { c.chat.stop() })
        }
    }

    editing?.let { idx ->
        var text by remember(idx) { mutableStateOf(st.messages.getOrNull(idx)?.message?.content.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("메시지 수정") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { c.chat.editAndResend(idx, text); editing = null }) { Text("다시 보내기") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } },
        )
    }
    confirmThinking?.let { on ->
        AlertDialog(
            onDismissRequest = { confirmThinking = null },
            title = { Text("모드 전환") },
            text = {
                Text("Thinking 모드 플래그는 시스템 프롬프트 맨 앞에 들어가므로, 이 대화 전체를 다시 계산(prefill)해야 합니다. 전환할까요?")
            },
            confirmButton = { TextButton(onClick = { c.chat.setThinking(on); confirmThinking = null }) { Text("전환") } },
            dismissButton = { TextButton(onClick = { confirmThinking = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun EngineBanner(s: EngineStatus) {
    when (s) {
        is EngineStatus.Loading -> Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text("모델 로드 중: ${s.what}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        }
        is EngineStatus.NoModel -> Text(
            "모델이 없습니다. '모델' 탭에서 다운로드하거나 가져오세요.",
            Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error,
        )
        is EngineStatus.Error -> Text(s.message, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error)
        else -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(m: StoredMessage, isLastAssistant: Boolean, busy: Boolean, onEdit: () -> Unit, onRegenerate: () -> Unit) {
    val msg = m.message
    when (msg.role) {
        Role.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = { if (!busy) onEdit() }),
            ) {
                Text(msg.content, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Role.ASSISTANT -> Column(Modifier.fillMaxWidth()) {
            msg.reasoning?.takeIf { it.isNotBlank() }?.let { ReasoningBlock(it, initiallyOpen = false) }
            if (msg.content.isNotBlank()) SelectionContainer { Text(msg.content, style = MaterialTheme.typography.bodyLarge) }
            msg.toolCalls.forEach { tc ->
                AssistChip(onClick = {}, label = { Text("🔧 ${tc.name}(${tc.arguments.entries.joinToString { "${it.key}=${it.value}" }})") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                m.stats?.let { StatsLine(it) }
                Spacer(Modifier.weight(1f))
                if (isLastAssistant && !busy) {
                    IconButton(onClick = onRegenerate) { Icon(Icons.Outlined.Refresh, "재생성", Modifier.size(18.dp)) }
                }
            }
        }
        Role.TOOL -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(),
        ) {
            Text("툴 결과: ${msg.content}", Modifier.padding(8.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Role.SYSTEM -> Unit
    }
}

@Composable
private fun ReasoningBlock(text: String, initiallyOpen: Boolean) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { open = !open },
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(if (open) "▾ 추론 과정" else "▸ 추론 과정 (${text.length}자)", style = MaterialTheme.typography.labelMedium)
            AnimatedVisibility(open) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatsLine(s: MessageStats) {
    val reuse = if (s.reusedTokens > 0) " · 캐시 ${s.reusedTokens}" else ""
    val ckpt = if (s.restored) " · 체크포인트" else ""
    Text(
        "${"%.1f".format(s.decodeTps)} tok/s · ${s.decodeTokens} tok · TTFT ${"%.0f".format(s.ttftMs)}ms · prefill ${s.prefillTokens}$reuse$ckpt",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun StreamingItem(s: Streaming) {
    Column(Modifier.fillMaxWidth()) {
        when (s.phase) {
            Phase.LOADING -> Text("모델 준비 중…", style = MaterialTheme.typography.labelMedium)
            Phase.PREFILL -> {
                Text("프롬프트 처리 ${s.prefillDone}/${s.prefillTotal}", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    progress = { if (s.prefillTotal > 0) s.prefillDone.toFloat() / s.prefillTotal else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Phase.TOOL -> Text("툴 실행 중…", style = MaterialTheme.typography.labelMedium)
            else -> Unit
        }
        if (s.reasoning.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    s.reasoning.takeLast(1200), Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (s.content.isNotEmpty()) Text(s.content, style = MaterialTheme.typography.bodyLarge)
        if (s.tokens > 0) {
            val hot = if (s.thermal.ordinal >= 2) " · 🌡 ${s.thermal}" else ""
            Text("${s.tokens} tok · ${"%.1f".format(s.tps)} tok/s$hot", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun InputBar(busy: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.weight(1f), placeholder = { Text("메시지") }, maxLines = 6,
        )
        Box(Modifier.padding(start = 4.dp)) {
            if (busy) IconButton(onClick = onStop) { Icon(Icons.Outlined.Stop, "중지") }
            else IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, "보내기")
            }
        }
    }
}
