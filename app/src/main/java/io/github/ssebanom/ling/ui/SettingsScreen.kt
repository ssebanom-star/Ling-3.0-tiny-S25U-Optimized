package io.github.ssebanom.ling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.data.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(c: AppContainer, modifier: Modifier = Modifier) {
    val s by c.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    fun set(f: (AppSettings) -> AppSettings) = scope.launch { c.settings.update(f) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("대화") {
            Toggle("새 대화 기본 Thinking 모드", s.defaultThinking) { v -> set { it.copy(defaultThinking = v) } }
            Toggle("온디바이스 툴 (계산기·시간·기기 상태)", s.toolsEnabled) { v -> set { it.copy(toolsEnabled = v) } }
            var sys by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
            OutlinedTextField(sys, { sys = it }, Modifier.fillMaxWidth(), label = { Text("시스템 프롬프트 (새 대화부터 적용)") }, minLines = 2)
            TextButton(onClick = { set { it.copy(systemPrompt = sys) } }, enabled = sys != s.systemPrompt) { Text("저장") }
        }
        Section("샘플링 (기본 = 모델 카드 권장값)") {
            SliderRow("temperature", s.temperature, 0f..2f) { v -> set { it.copy(temperature = v) } }
            SliderRow("top_p", s.topP, 0.5f..1f) { v -> set { it.copy(topP = v) } }
            SliderRow("top_k", s.topK.toFloat(), 1f..100f, decimals = 0) { v -> set { it.copy(topK = v.toInt()) } }
            SliderRow("min_p", s.minP, 0f..0.3f) { v -> set { it.copy(minP = v) } }
            SliderRow("repeat penalty", s.repeatPenalty, 1f..1.5f) { v -> set { it.copy(repeatPenalty = v) } }
            Choice("최대 생성 토큰", listOf(1024, 2048, 4096, 8192, 16384), s.maxTokens) { v -> set { it.copy(maxTokens = v) } }
            TextButton(onClick = {
                set { it.copy(temperature = 1.0f, topP = 0.95f, topK = 20, minP = 0f, repeatPenalty = 1f) }
            }) { Text("권장값으로") }
        }
        Section("메모리 · 컨텍스트 (변경 시 모델 재로드)") {
            Text("MLA 캐시 6.9KB/토큰 + KDA 상태 19MiB 고정 → 32K = 약 216MiB", style = MaterialTheme.typography.bodySmall)
            Choice("컨텍스트", listOf(4096, 8192, 16384, 32768, 65536), s.nCtx) { v -> set { it.copy(nCtx = v) } }
            Toggle("KV 캐시 q8_0 (메모리 절반, 품질 영향 미미)", s.kvQ8) { v -> set { it.copy(kvQ8 = v) } }
            Choice("턴 체크포인트 최대 개수 (개당 ~19MiB)", listOf(2, 4, 8, 16), s.maxCheckpoints) { v -> set { it.copy(maxCheckpoints = v) } }
            Choice("유휴 시 자동 언로드(분, 0=안 함)", listOf(0, 5, 10, 30), s.autoUnloadMinutes) { v -> set { it.copy(autoUnloadMinutes = v) } }
        }
        Section("CPU · 열 관리") {
            Choice("디코드 스레드 (0=자동/튜닝값 ${s.tunedThreads})", listOf(0, 2, 3, 4, 5, 6, 8), s.threads) { v -> set { it.copy(threads = v) } }
            Choice("prefill 스레드 (0=자동/튜닝값 ${s.tunedThreadsBatch})", listOf(0, 4, 6, 8), s.threadsBatch) { v -> set { it.copy(threadsBatch = v) } }
            Toggle("열 거버너 (온도 상승 시 스레드/속도 제한)", s.thermalGovernor) { v -> set { it.copy(thermalGovernor = v) } }
            Toggle("ADPF 성능 힌트", s.perfHints) { v -> set { it.copy(perfHints = v) } }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, decimals: Int = 2, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Column {
        Text("$label: ${"%.${decimals}f".format(v)}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = v, onValueChange = { v = it }, valueRange = range, onValueChangeFinished = { onDone(v) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(label: String, options: List<Int>, value: Int, onChange: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == value, onClick = { onChange(o) }, label = { Text(o.toString()) }) }
    }
}
