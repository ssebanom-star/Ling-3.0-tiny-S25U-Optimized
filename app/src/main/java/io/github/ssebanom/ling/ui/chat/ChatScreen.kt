package io.github.ssebanom.ling.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.runtime.EngineStatus
import io.github.ssebanom.ling.service.LingService
import io.github.ssebanom.ling.ui.components.StatusDot
import io.github.ssebanom.ling.ui.theme.LingColors
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(c: AppContainer, navigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val st by c.chat.state.collectAsState()
    val engineStatus by c.inference.status.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = AppSettings())
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Int?>(null) }
    var confirmThinking by remember { mutableStateOf<Boolean?>(null) }
    var modelSheet by remember { mutableStateOf(false) }
    val family = c.inference.familyFor(File(settings.modelFile))

    LaunchedEffect(Unit) { if (st.conversation == null) c.chat.newConversation() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            AppDrawer(
                c = c, open = drawer.isOpen, currentId = st.conversation?.id, busy = st.busy,
                onNew = { if (!st.busy) c.chat.newConversation(); scope.launch { drawer.close() } },
                onOpen = { id -> if (!st.busy) c.chat.openConversation(id); scope.launch { drawer.close() } },
                onDeleted = { id -> if (id == st.conversation?.id) c.chat.newConversation() },
                onNavigate = { r -> scope.launch { drawer.close() }; navigate(r) },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "메뉴") } },
                        title = { ModelChip(family.label, engineStatus, st.busy) { modelSheet = true } },
                        actions = {
                            IconButton(onClick = { c.chat.newConversation() }, enabled = !st.busy) { Icon(Icons.Outlined.EditNote, "새 대화") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    EngineStrip(engineStatus, onModels = { navigate("models") })
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val convId = st.conversation?.id ?: 0L
                    val rows = remember(st.messages, st.busy, convId) { ChatTurns.rows(convId, st.messages, st.busy) }
                    if (rows.isEmpty()) {
                        EmptyState(family.label, toolsOn = settings.toolsEnabled) { text -> LingService.start(ctx); c.chat.send(text) }
                    } else {
                        MessageList(c, rows, st, onEdit = { editing = it }, onRegenerate = { LingService.start(ctx); c.chat.regenerate() })
                    }
                }
                AnimatedVisibility(st.error != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(st.error.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { c.chat.clearError() }) { Icon(Icons.Outlined.Close, "닫기") }
                        }
                    }
                }
                Composer(
                    busy = st.busy,
                    thinking = st.conversation?.thinking ?: true,
                    thinkingToggle = family.thinkingToggle,
                    toolCount = if (settings.toolsEnabled) c.tools.available(settings).size else 0,
                    onToggleThinking = {
                        val thinking = st.conversation?.thinking ?: true
                        val conv = st.conversation
                        if (conv == null || conv.id == 0L || st.messages.isEmpty()) c.chat.setThinking(!thinking) else confirmThinking = !thinking
                    },
                    onTools = { navigate("settings") },
                    onSend = { LingService.start(ctx); c.chat.send(it) },
                    onStop = { c.chat.stop() },
                )
            }
        }
    }

    if (modelSheet) ModelSheet(c, onDismiss = { modelSheet = false }, onManage = { modelSheet = false; navigate("models") })

    editing?.let { idx ->
        var text by remember(idx) { mutableStateOf(st.messages.getOrNull(idx)?.message?.content.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("메시지 수정") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) },
            confirmButton = { TextButton(onClick = { LingService.start(ctx); c.chat.editAndResend(idx, text); editing = null }) { Text("다시 보내기") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } },
        )
    }
    confirmThinking?.let { on ->
        AlertDialog(
            onDismissRequest = { confirmThinking = null },
            icon = { Icon(Icons.Outlined.Lightbulb, null) },
            title = { Text(if (on) "Thinking 으로 전환" else "Instant 로 전환") },
            text = { Text("추론 모드는 프롬프트 맨 앞에 들어가므로 이 대화 전체를 다시 읽어야(prefill) 합니다. 전환할까요?") },
            confirmButton = { TextButton(onClick = { c.chat.setThinking(on); confirmThinking = null }) { Text("전환") } },
            dismissButton = { TextButton(onClick = { confirmThinking = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun ModelChip(label: String, status: EngineStatus, busy: Boolean, onClick: () -> Unit) {
    val (color, sub) = when (status) {
        is EngineStatus.Ready -> LingColors.success to status.backend.name
        is EngineStatus.Loading -> MaterialTheme.colorScheme.primary to "로드 중"
        is EngineStatus.Error -> MaterialTheme.colorScheme.error to "오류"
        EngineStatus.NoModel -> MaterialTheme.colorScheme.error to "모델 없음"
        EngineStatus.Unloaded -> MaterialTheme.colorScheme.outline to "대기"
    }
    Row(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, pulsing = busy || status is EngineStatus.Loading)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EngineStrip(s: EngineStatus, onModels: () -> Unit) {
    AnimatedVisibility(s is EngineStatus.Loading || s is EngineStatus.NoModel || s is EngineStatus.Error) {
        when (s) {
            is EngineStatus.Loading -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                val p by animateFloatAsState(s.progress, label = "load")
                Text("모델 로드 중 · ${s.what}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(CircleShape))
            }
            else -> Surface(
                color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onModels),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s is EngineStatus.Error) s.message else "모델이 없습니다. 탭해서 모델을 받으세요.",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    c: AppContainer,
    rows: List<ChatRow>,
    st: io.github.ssebanom.ling.runtime.ChatState,
    onEdit: (Int) -> Unit,
    onRegenerate: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 사용자가 위로 올려 읽는 중이면 자동 스크롤을 멈춘다
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (!scrolling) follow = !listState.canScrollForward }
    }
    val s = st.streaming
    val tick = rows.size * 100_000 + s.content.length + s.reasoning.length / 8 + s.phase.ordinal * 7 + st.messages.size
    LaunchedEffect(tick) { if (follow && rows.isNotEmpty()) listState.scrollBy(100_000f) }
    LaunchedEffect(st.messages.size) {
        // 새 사용자 메시지가 추가되면 맨 아래로
        if (st.messages.lastOrNull()?.message?.role == io.github.ssebanom.ling.domain.Role.USER) { follow = true; listState.animateScrollToItem(rows.lastIndex) }
    }
    val showFab by remember { derivedStateOf { listState.canScrollForward && !follow } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                Box(Modifier.animateItem()) {
                    when (row) {
                        is ChatRow.User -> UserBubble(row.msg, st.busy) { onEdit(row.index) }
                        is ChatRow.Turn -> {
                            val model = row.entries.firstNotNullOfOrNull { it.message.rawModel }
                            val label = model?.let { c.inference.familyFor(File(it)).label }
                                ?: if (row.live) c.inference.familyFor(File(c.engine.config?.modelPath.orEmpty())).label else null
                            AssistantTurn(row, label, if (row.live) st.streaming else null, st.busy, onRegenerate)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            showFab, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
        ) {
            SmallFloatingActionButton(
                onClick = { follow = true; scope.launch { listState.animateScrollToItem(rows.lastIndex, 100_000) } },
                shape = CircleShape, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) { Icon(Icons.Outlined.ArrowDownward, "맨 아래로") }
        }
    }
}

private data class Suggestion(val icon: ImageVector, val title: String, val prompt: String)

@Composable
internal fun EmptyState(model: String, toolsOn: Boolean, onPick: (String) -> Unit) {
    val list = buildList {
        if (toolsOn) add(Suggestion(Icons.Outlined.Search, "최신 정보 찾기", "오늘 주요 IT 뉴스를 검색해서 3줄로 요약해줘"))
        add(Suggestion(Icons.Outlined.Functions, "수식 풀이", "∫₀¹ x² eˣ dx 를 단계별로 풀고 LaTeX 로 보여줘"))
        add(Suggestion(Icons.Outlined.Code, "코드 작성", "파이썬으로 퀵정렬을 구현하고 시간복잡도를 설명해줘"))
        add(Suggestion(Icons.Outlined.Lightbulb, "아이디어", "주말에 집에서 할 만한 생산적인 활동 5가지를 표로 정리해줘"))
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
            ),
            contentAlignment = Alignment.Center,
        ) { Text("L", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary) }
        Spacer(Modifier.height(16.dp))
        Text("무엇을 도와드릴까요?", style = MaterialTheme.typography.headlineSmall)
        Text("$model · 기기에서 실행", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        list.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { s ->
                    Surface(
                        onClick = { onPick(s.prompt) }, modifier = Modifier.weight(1f).height(96.dp),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Icon(s.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(s.title, style = MaterialTheme.typography.labelLarge)
                            Text(s.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun Composer(
    busy: Boolean,
    thinking: Boolean,
    thinkingToggle: Boolean,
    toolCount: Int,
    onToggleThinking: () -> Unit,
    onTools: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var lines by remember { mutableIntStateOf(1) }
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                if (text.isEmpty()) Text("메시지를 입력하세요", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 8,
                    onTextLayout = { lines = it.lineCount },
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thinkingToggle) ToggleChip(
                    icon = Icons.Outlined.Lightbulb, label = if (thinking) "Thinking" else "Instant",
                    active = thinking, enabled = !busy, onClick = onToggleThinking,
                )
                Spacer(Modifier.width(6.dp))
                ToggleChip(
                    icon = Icons.Outlined.Build, label = if (toolCount > 0) "툴 $toolCount" else "툴 꺼짐",
                    active = toolCount > 0, enabled = true, onClick = onTools,
                )
                Spacer(Modifier.weight(1f))
                val canSend = text.isNotBlank()
                AnimatedContent(
                    targetState = if (busy) 2 else if (canSend) 1 else 0,
                    transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                    label = "send",
                ) { mode ->
                    val bg = when (mode) { 0 -> MaterialTheme.colorScheme.surfaceContainerHighest; else -> MaterialTheme.colorScheme.primary }
                    val fg = when (mode) { 0 -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onPrimary }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(bg).clickable(enabled = mode != 0) {
                            if (mode == 2) onStop() else { onSend(text); text = "" }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (mode == 2) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward, if (mode == 2) "중지" else "보내기", tint = fg)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, textAlign = TextAlign.Center)
    }
}
