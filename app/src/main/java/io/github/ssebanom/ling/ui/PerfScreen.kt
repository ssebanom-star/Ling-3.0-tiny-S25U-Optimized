package io.github.ssebanom.ling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.Backend
import io.github.ssebanom.ling.engine.EngineState
import io.github.ssebanom.ling.runtime.EngineStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerfScreen(c: AppContainer, modifier: Modifier = Modifier) {
    val status by c.inference.status.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = AppSettings())
    val chat by c.chat.state.collectAsState()
    var log by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var engineState by remember { mutableStateOf<EngineState?>(null) }
    var thermal by remember { mutableStateOf(c.thermal.snapshot()) }

    LaunchedEffect(Unit) {
        while (true) {
            thermal = c.thermal.snapshot()
            if (c.engine.isLoaded && !chat.busy && !running) engineState = runCatching { c.engine.state() }.getOrNull()
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("기기", style = MaterialTheme.typography.titleMedium)
                Mono(c.inference.profile.summary())
                Mono("열: ${thermal.level} (headroom ${"%.2f".format(thermal.headroom)}, status ${thermal.status})")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("엔진", style = MaterialTheme.typography.titleMedium)
                when (val s = status) {
                    is EngineStatus.Ready -> {
                        Mono("${s.info.desc} · ${s.backend.label}")
                        Mono("ctx ${s.config.nCtx} · 스레드 ${s.config.nThreads}/${s.config.nThreadsBatch} · cpu[${s.config.cpuMask.ifEmpty { "OS" }}]")
                        engineState?.let {
                            Mono("컨텍스트 ${it.contextTokens}/${it.nCtx} tok · 체크포인트 ${it.checkpoints}개 (${it.checkpointBytes shr 20} MiB)")
                        }
                    }
                    is EngineStatus.Loading -> Mono("로드 중 ${(s.progress * 100).toInt()}%")
                    is EngineStatus.Error -> Mono("오류: ${s.message}")
                    EngineStatus.NoModel -> Mono("모델 없음")
                    EngineStatus.Unloaded -> Mono("언로드됨")
                }
                chat.lastStats?.let {
                    Mono("최근 응답: ${"%.1f".format(it.decodeTps)} tok/s, TTFT ${"%.0f".format(it.ttftMs)}ms, prefill ${it.prefillTokens} (재사용 ${it.reusedTokens})")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { task("로드") { if (c.inference.ensureLoaded()) "로드 완료" else "로드 실패" } }, enabled = !running && !chat.busy) { Text("로드") }
                    OutlinedButton(onClick = { task("언로드") { c.inference.unload(); "언로드됨" } }, enabled = !running && !chat.busy) { Text("언로드") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("튜닝 · 벤치마크", style = MaterialTheme.typography.titleMedium)
                Text("자동 튜닝: 디코드 스레드 수 × 코어 배치(OS/빠른 코어 우선), prefill 스레드를 실측해 저장합니다.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        task("자동 튜닝") {
                            val r = c.inference.autoTune { log = "튜닝: $it" } ?: return@task "모델 로드 실패"
                            buildString {
                                append("선택: decode ${r.decodeThreads} / batch ${r.batchThreads} / cpu[${r.cpuMask.ifEmpty { "OS" }}]\n")
                                r.table.forEach { (l, pp, tg) -> append("%-28s %s\n".format(l, if (pp > 0) "pp %.1f".format(pp) else "tg %.1f".format(tg))) }
                            }.also { saveReport(c, "tune", it) }
                        }
                    }, enabled = !running && !chat.busy) { Text("자동 튜닝") }
                    OutlinedButton(onClick = {
                        task("벤치") {
                            val sb = StringBuilder()
                            for ((pp, tg) in listOf(128 to 0, 512 to 0, 0 to 128)) {
                                log = "벤치 pp$pp tg$tg"
                                val b = c.inference.bench(pp, tg, 2) ?: return@task "모델 로드 실패"
                                sb.append(if (pp > 0) "pp$pp: %.1f tok/s\n".format(b.ppTps) else "tg$tg: %.1f tok/s\n".format(b.tgTps))
                            }
                            sb.toString().also { saveReport(c, "bench", it) }
                        }
                    }, enabled = !running && !chat.busy) { Text("벤치 (pp128/pp512/tg128)") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("가속기 (실험)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "NPU/GPU 로 전체 오프로드 후 CPU 기준 logits 와 비교합니다. 기준 미달이면 자동으로 CPU 로 되돌립니다. " +
                        "현재: ${settings.backend.label}${if (settings.acceleratorValidated.isNotEmpty()) " · 검증됨 ${settings.acceleratorValidated}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Mono("백엔드: " + c.engine.devices().joinToString { "${it.name}(${it.type})" }.ifEmpty { "CPU만" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in listOf(Backend.NPU, Backend.GPU)) {
                        OutlinedButton(onClick = {
                            task("${b.label} 검사") {
                                val r = c.inference.validateAccelerator(b) { log = it }
                                (if (r.passed) "통과 → ${b.label} 사용\n" else "실패 → CPU 유지\n") + r.detail
                            }
                        }, enabled = !running && !chat.busy) { Text("${b.name} 검사") }
                    }
                    OutlinedButton(onClick = {
                        task("CPU 복귀") { c.settings.update { it.copy(backend = Backend.CPU) }; c.inference.ensureLoaded(); "CPU 로 전환" }
                    }, enabled = !running && !chat.busy && settings.backend != Backend.CPU) { Text("CPU 로") }
                }
            }
        }
        if (log.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Mono(log, Modifier.padding(12.dp)) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("llama.cpp 시스템 정보", style = MaterialTheme.typography.titleMedium)
                Mono(remember { c.engine.systemInfo() })
            }
        }
    }
}

@Composable
private fun Mono(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

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
