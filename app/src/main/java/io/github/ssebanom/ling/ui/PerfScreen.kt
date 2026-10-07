package io.github.ssebanom.ling.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.Backend
import io.github.ssebanom.ling.data.StatPoint
import io.github.ssebanom.ling.engine.EngineState
import io.github.ssebanom.ling.runtime.EngineStatus
import io.github.ssebanom.ling.runtime.GpuQuirks
import io.github.ssebanom.ling.runtime.InferenceManager
import io.github.ssebanom.ling.ui.components.ChipRow
import io.github.ssebanom.ling.ui.components.Pill
import io.github.ssebanom.ling.ui.components.ScreenScaffold
import io.github.ssebanom.ling.ui.components.SectionCard
import io.github.ssebanom.ling.ui.components.StatTile
import io.github.ssebanom.ling.ui.components.StatusDot
import io.github.ssebanom.ling.ui.components.SwitchRow
import io.github.ssebanom.ling.ui.theme.LingColors
import io.github.ssebanom.ling.ui.theme.MonoSmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerfScreen(c: AppContainer, onBack: () -> Unit) {
    val status by c.inference.status.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = AppSettings())
    val chat by c.chat.state.collectAsState()
    var log by remember { mutableStateOf("") }
    var loadTarget by remember { mutableStateOf<Backend?>(null) }
    var tuneTarget by remember { mutableStateOf<Backend?>(null) }
    var running by remember { mutableStateOf(false) }
    var engineState by remember { mutableStateOf<EngineState?>(null) }
    var thermal by remember { mutableStateOf(c.thermal.snapshot()) }
    var devicesText by remember { mutableStateOf("…") }
    var sysInfo by remember { mutableStateOf("…") }
    var points by remember { mutableStateOf(listOf<StatPoint>()) }
    var showSys by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            devicesText = c.engine.devices().joinToString { "${it.name}(${it.type})" }.ifEmpty { "CPU만" }
            sysInfo = c.engine.systemInfo()
        }
    }
    LaunchedEffect(chat.lastStats) { points = c.conversations.recentStats(60) }
    LaunchedEffect(Unit) {
        while (true) {
            thermal = c.thermal.snapshot()
            if (c.inference.status.value is EngineStatus.Ready && !chat.busy && !running) engineState = runCatching { c.engine.state() }.getOrNull()
            delay(2000)
        }
    }

    fun task(name: String, block: suspend () -> String) {
        if (running) return
        running = true
        log = "$name 실행 중…"
        c.appScope.launch {
            log = runCatching { block() }.getOrElse { "오류: ${it.message}" }
            running = false
        }
    }
    val idle = !running && !chat.busy

    ScreenScaffold("성능", onBack) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 요약 타일
            val last = points.lastOrNull()?.stats
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("최근 생성 속도", last?.let { "%.1f".format(it.decodeTps) } ?: "-", Modifier.weight(1f), sub = "tok/s")
                StatTile("첫 토큰", last?.let { "%.0f".format(it.ttftMs) } ?: "-", Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary, sub = "ms")
                val tcol = when (thermal.level.ordinal) { 0 -> LingColors.success; 1 -> LingColors.warning; else -> MaterialTheme.colorScheme.error }
                StatTile("발열", thermal.level.name.lowercase(), Modifier.weight(1f), accent = tcol, sub = "여유 ${"%.2f".format(thermal.headroom)}")
            }

            SectionCard("응답 속도 추이", Icons.Outlined.ShowChart, subtitle = "최근 ${points.size}개 응답 · 생성 tok/s(선) · 첫 토큰 지연(막대)") {
                if (points.size < 2) Text("대화를 몇 번 하면 그래프가 나타납니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                else SpeedChart(points)
            }

            SectionCard(
                "엔진", Icons.Outlined.Memory,
                trailing = {
                    val (col, txt) = when (val s = status) {
                        is EngineStatus.Ready -> LingColors.success to s.backend.name
                        is EngineStatus.Loading -> MaterialTheme.colorScheme.primary to "로드 ${(s.progress * 100).toInt()}%"
                        is EngineStatus.Error -> MaterialTheme.colorScheme.error to "오류"
                        EngineStatus.NoModel -> MaterialTheme.colorScheme.error to "모델 없음"
                        EngineStatus.Unloaded -> MaterialTheme.colorScheme.outline to "언로드"
                    }
                    StatusDot(col, pulsing = status is EngineStatus.Loading || chat.busy)
                    Spacer(Modifier.width(6.dp))
                    Text(txt, style = MaterialTheme.typography.labelLarge, color = col)
                },
            ) {
                when (val s = status) {
                    is EngineStatus.Ready -> {
                        Text("${s.info.desc} · ${s.backend.label}", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("ctx ${s.config.nCtx}", MaterialTheme.colorScheme.primary)
                            Pill("스레드 ${s.config.nThreads}/${s.config.nThreadsBatch}", MaterialTheme.colorScheme.secondary)
                            Pill("cpu ${s.config.cpuMask.ifEmpty { "OS" }}", MaterialTheme.colorScheme.tertiary)
                            if (s.config.kvQ8) Pill("KV q8", MaterialTheme.colorScheme.secondary)
                        }
                        engineState?.let { es ->
                            val frac = if (es.nCtx > 0) es.contextTokens.toFloat() / es.nCtx else 0f
                            Text("컨텍스트 ${es.contextTokens}/${es.nCtx} tok · 체크포인트 ${es.checkpoints}개 (${es.checkpointBytes shr 20} MiB)",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.material3.LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                        }
                    }
                    is EngineStatus.Loading -> Text("로드 중 · ${s.what}", style = MaterialTheme.typography.bodyMedium)
                    is EngineStatus.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    EngineStatus.NoModel -> Text("모델 없음")
                    EngineStatus.Unloaded -> Text("메모리에서 내려가 있습니다. 다음 메시지에서 자동 로드됩니다.", style = MaterialTheme.typography.bodySmall)
                }
                c.inference.lastFallback?.let { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = LingColors.warning) }
                val lt = loadTarget ?: settings.backend
                ChipRow("실행 장치", listOf(Backend.GPU, Backend.CPU, Backend.NPU), lt, display = { it.name }, enabled = idle) { loadTarget = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        task("로드") {
                            c.inference.lock.withLock {
                                if (c.settings.current().backend != lt) c.settings.update { it.copy(backend = lt) }
                                loadTarget = null
                                val ok = c.inference.ensureLoaded()
                                val now = (c.inference.status.value as? EngineStatus.Ready)?.backend
                                when {
                                    !ok -> "로드 실패"
                                    now != lt -> "${lt.name} 사용 불가 → ${now?.name} 로 로드\n${c.inference.lastFallback.orEmpty()}"
                                    else -> "${lt.name} 로드 완료"
                                }
                            }
                        }
                    }, enabled = idle) { Text("${lt.name}로 로드") }
                    OutlinedButton(onClick = { task("언로드") { c.inference.unload(); "언로드됨" } }, enabled = idle) { Text("언로드") }
                }
                SwitchRow(
                    "GPU/NPU 정확성 검사 건너뛰기", settings.skipAccelCheck,
                    desc = "켜면 CPU 기준 비교 없이 바로 로드합니다(로드 실패 시에만 CPU 복귀). 출력이 이상하면 끄고 다시 로드하세요.",
                    enabled = idle,
                ) { v -> c.appScope.launch { c.settings.update { it.copy(skipAccelCheck = v) } } }
            }

            AnimatedVisibility(log.isNotEmpty()) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        if (running) { StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, Modifier.padding(top = 4.dp)); Spacer(Modifier.width(8.dp)) }
                        Text(log, style = MonoSmall, modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()))
                    }
                }
            }

            SectionCard("튜닝 · 벤치마크", Icons.Outlined.Tune, subtitle = "CPU: 스레드×코어 배치 실측 / GPU·NPU: 토큰 ${InferenceManager.ACCEL_TG}개 생성 속도") {
                val target = tuneTarget ?: settings.backend
                ChipRow(null, listOf(Backend.GPU, Backend.CPU, Backend.NPU), target, display = { it.name }, enabled = idle) { tuneTarget = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        task("자동 튜닝") {
                            val r = c.inference.autoTune(target) { log = "튜닝: $it" } ?: return@task "모델 로드 실패"
                            tuneTarget = null
                            buildString {
                                c.inference.lastFallback?.takeIf { c.settings.current().backend != target }?.let { append("$target 사용 불가 → CPU: $it\n") }
                                append("선택: decode ${r.decodeThreads} / batch ${r.batchThreads} / cpu[${r.cpuMask.ifEmpty { "OS" }}]\n")
                                r.table.forEach { (l, pp, tg) -> append("%-28s %s\n".format(l, if (pp > 0) "pp %.1f".format(pp) else "tg %.1f".format(tg))) }
                            }.also { saveReport(c, "tune", it) }
                        }
                    }, enabled = idle) { Text("${target.name} 튜닝") }
                    OutlinedButton(onClick = {
                        task("벤치") {
                            val sb = StringBuilder()
                            val runs = if (settings.backend == Backend.CPU) listOf(128 to 0, 512 to 0, 0 to 128) else listOf(0 to InferenceManager.ACCEL_TG)
                            for ((pp, tg) in runs) {
                                log = "벤치 pp$pp tg$tg"
                                val b = c.inference.bench(pp, tg, 2) ?: return@task "모델 로드 실패"
                                sb.append(if (pp > 0) "pp$pp: %.1f tok/s\n".format(b.ppTps) else "tg$tg: %.1f tok/s\n".format(b.tgTps))
                            }
                            sb.toString().also { saveReport(c, "bench", it) }
                        }
                    }, enabled = idle) { Text(if (settings.backend == Backend.CPU) "벤치 pp128/512·tg128" else "벤치 tg${InferenceManager.ACCEL_TG}") }
                }
            }

            SectionCard(
                "가속기 검증", Icons.Outlined.VerifiedUser,
                subtitle = "GPU/NPU 전체 오프로드 후 CPU 기준 logits 와 비교, 미달이면 CPU 로 복귀",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("현재 ${settings.backend.name}", MaterialTheme.colorScheme.primary)
                    if (settings.acceleratorValidated.isNotEmpty()) Pill("검증됨 ${settings.acceleratorValidated.substringBefore(':')}", LingColors.success)
                    Pill("GPU 우회: ${GpuQuirks.decode(settings.gpuQuirks).label}", MaterialTheme.colorScheme.tertiary)
                }
                Text("백엔드: $devicesText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in listOf(Backend.NPU, Backend.GPU)) {
                        OutlinedButton(onClick = {
                            task("${b.label} 검사") {
                                val r = c.inference.validateAccelerator(b) { log = it }
                                (if (r.passed) "통과 → ${b.label} 사용\n" else "실패 → CPU 유지\n") + r.detail
                            }
                        }, enabled = idle) { Text("${b.name} 검사") }
                    }
                    OutlinedButton(onClick = {
                        task("CPU 복귀") { c.settings.update { it.copy(backend = Backend.CPU) }; c.inference.ensureLoaded(); "CPU 로 전환" }
                    }, enabled = idle && settings.backend != Backend.CPU) { Text("CPU로") }
                }
            }

            SectionCard("기기", Icons.Outlined.DeveloperBoard) {
                Text(c.inference.profile.summary(), style = MonoSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Outlined.Thermostat, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("열 ${thermal.level} · headroom ${"%.2f".format(thermal.headroom)} · status ${thermal.status}", style = MonoSmall)
                }
            }

            SectionCard("llama.cpp 시스템 정보", Icons.Outlined.Terminal, trailing = {
                TextButton(onClick = { showSys = !showSys }) { Text(if (showSys) "접기" else "펼치기") }
            }) {
                AnimatedVisibility(showSys) { Text(sysInfo, style = MonoSmall) }
            }
        }
    }
}

/** 선: 생성 tok/s, 막대: TTFT(오른쪽 축, 정규화) */
@Composable
internal fun SpeedChart(points: List<StatPoint>) {
    val line = MaterialTheme.colorScheme.primary
    val bar = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val tps = points.map { it.stats.decodeTps.toFloat() }
    val ttft = points.map { it.stats.ttftMs.toFloat() }
    val maxT = (tps.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.15f
    val maxL = (ttft.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    val grow = remember { Animatable(if (preview) 1f else 0f) }
    LaunchedEffect(points.size) { if (!preview) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) } }
    Column {
        Row {
            Text("최고 ${"%.1f".format(tps.max())} · 평균 ${"%.1f".format(tps.average())} tok/s", style = MaterialTheme.typography.labelMedium,
                color = line, modifier = Modifier.weight(1f))
            Text("TTFT 중앙값 ${"%.0f".format(ttft.sorted()[ttft.size / 2])}ms", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = points.size
            val step = size.width / (n - 1).coerceAtLeast(1)
            for (k in 1..3) {
                val y = size.height * (1 - k / 4f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val bw = (size.width / n) * 0.5f
            ttft.forEachIndexed { i, v ->
                val h = size.height * 0.45f * (v / maxL) * grow.value
                drawRect(bar, Offset(i * step - bw / 2, size.height - h), androidx.compose.ui.geometry.Size(bw, h))
            }
            val path = Path()
            val fill = Path()
            tps.forEachIndexed { i, v ->
                val x = i * step
                val y = size.height - size.height * (v / maxT) * grow.value
                if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo((n - 1) * step, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            val lx = (n - 1) * step
            val ly = size.height - size.height * (tps.last() / maxT) * grow.value
            drawCircle(line, 5.dp.toPx(), Offset(lx, ly))
        }
    }
}

/** 결과를 앱 외부 저장소 bench/ 에 JSON 으로 남긴다(사용자가 PC 로 가져가 docs 에 반영) */
private fun saveReport(c: AppContainer, kind: String, text: String) {
    val dir = File(c.models.dir.parentFile, "bench").apply { mkdirs() }
    val o = JSONObject().apply {
        put("kind", kind)
        put("time", System.currentTimeMillis())
        put("device", c.inference.profile.summary())
        put("engine", c.engine.config?.toString() ?: "")
        put("result", JSONArray(text.lines().filter { it.isNotBlank() }))
    }
    File(dir, "${kind}_${System.currentTimeMillis()}.json").writeText(o.toString(2))
}
