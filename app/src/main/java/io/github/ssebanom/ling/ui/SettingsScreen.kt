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
import io.github.ssebanom.ling.domain.ModelFamily
import io.github.ssebanom.ling.tools.ToolGroup
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(c: AppContainer, modifier: Modifier = Modifier) {
    val s by c.settings.flow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    fun set(f: (AppSettings) -> AppSettings) = scope.launch { c.settings.update(f) }

    val ctx = LocalContext.current
    // 권한 화면에서 돌아오면 상태 갱신
    var resumed by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val family = c.inference.familyFor(File(s.modelFile))

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("대화") {
            Toggle("새 대화 기본 Thinking 모드 (Ling 전용, LFM2.5 는 항상 추론)", s.defaultThinking) { v -> set { it.copy(defaultThinking = v) } }
            var sys by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
            OutlinedTextField(sys, { sys = it }, Modifier.fillMaxWidth(), label = { Text("시스템 프롬프트 (새 대화부터 적용)") }, minLines = 2)
            TextButton(onClick = { set { it.copy(systemPrompt = sys) } }, enabled = sys != s.systemPrompt) { Text("저장") }
        }
        Section("툴") {
            Toggle("툴 사용", s.toolsEnabled) { v -> set { it.copy(toolsEnabled = v) } }
            if (s.toolsEnabled) {
                key(resumed) {
                    for (g in ToolGroup.entries) {
                        val ready = c.tools.groupReady(g)
                        Toggle("${g.label} — ${g.detail}", g.name in s.toolGroups) { v ->
                            set { it.copy(toolGroups = if (v) it.toolGroups + g.name else it.toolGroups - g.name) }
                        }
                        when {
                            g == ToolGroup.FILES && !ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("권한 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                                }) { Text("모든 파일 접근 허용") }
                            }
                            g == ToolGroup.SCREEN && !ready -> Column {
                                Text(
                                    "접근성 서비스 꺼짐. 설정 → 접근성 → 설치된 앱 → 'Ling 화면 제어'를 켜세요. " +
                                        "'제한된 설정' 안내가 뜨면 앱 정보 → ⋮ → '제한된 설정 허용' 후 다시 켭니다.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                )
                                Row {
                                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("접근성 설정") }
                                    TextButton(onClick = {
                                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                                    }) { Text("앱 정보") }
                                }
                            }
                        }
                    }
                }
                Toggle("확인 없이 실행 (탭·입력·파일 쓰기·앱 열기 등)", s.autoApproveTools) { v -> set { it.copy(autoApproveTools = v) } }
                if (s.autoApproveTools) Text(
                    "주의: 모델이 잘못 판단하면 의도하지 않은 탭·입력·메시지 전송이 일어날 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                Choice("요청당 최대 툴 라운드", listOf(4, 8, 12, 20), s.maxToolRounds) { v -> set { it.copy(maxToolRounds = v) } }
                Text("웹 검색: 기본은 DuckDuckGo → Bing 순서(키 불필요, 차단될 수 있음). 아래 값이 있으면 우선 사용.", style = MaterialTheme.typography.bodySmall)
                var sx by remember(s.searxngUrl) { mutableStateOf(s.searxngUrl) }
                OutlinedTextField(sx, { sx = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("SearXNG 주소 (JSON 허용 인스턴스)") })
                var bk by remember(s.braveApiKey) { mutableStateOf(s.braveApiKey) }
                OutlinedTextField(bk, { bk = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Brave Search API 키") })
                TextButton(onClick = { set { it.copy(searxngUrl = sx.trim(), braveApiKey = bk.trim()) } },
                    enabled = sx != s.searxngUrl || bk != s.braveApiKey) { Text("저장") }
            }
        }
        Section("샘플링") {
            val r = family.sampling
            Toggle(
                "모델 권장값 사용 (${family.label}: temp ${r.temperature}, top_p ${r.topP}, top_k ${r.topK}, rep ${r.repeatPenalty})",
                s.useRecommendedSampling,
            ) { v -> set { it.copy(useRecommendedSampling = v) } }
            if (!s.useRecommendedSampling) {
                SliderRow("temperature", s.temperature, 0f..2f) { v -> set { it.copy(temperature = v) } }
                SliderRow("top_p", s.topP, 0.5f..1f) { v -> set { it.copy(topP = v) } }
                SliderRow("top_k", s.topK.toFloat(), 1f..100f, decimals = 0) { v -> set { it.copy(topK = v.toInt()) } }
                SliderRow("min_p", s.minP, 0f..0.3f) { v -> set { it.copy(minP = v) } }
                SliderRow("repeat penalty", s.repeatPenalty, 1f..1.5f) { v -> set { it.copy(repeatPenalty = v) } }
                TextButton(onClick = {
                    set { it.copy(temperature = r.temperature, topP = r.topP, topK = r.topK, minP = r.minP, repeatPenalty = r.repeatPenalty) }
                }) { Text("현재 모델 권장값으로") }
            }
            Choice("최대 생성 토큰", listOf(1024, 2048, 4096, 8192, 16384), s.maxTokens) { v -> set { it.copy(maxTokens = v) } }
        }
        Section("메모리 · 컨텍스트 (변경 시 모델 재로드)") {
            Text(
                when (family) {
                    ModelFamily.LING -> "MLA 캐시 6.9KB/토큰 + KDA 상태 19MiB 고정 → 32K = 약 216MiB"
                    ModelFamily.LFM2 -> "LFM2.5: GQA 6층 KV f16 12KB/토큰(8 KV헤드×64차원) + conv 상태 소량 → 32K = 약 384MiB"
                    ModelFamily.K2H -> "K2-Horizon: 전층 어텐션 KV f16 144KB/토큰(q8 72KB) → 이 모델은 컨텍스트 최대 8K·KV q8 로 자동 제한(약 0.56GiB)"
                    ModelFamily.QWEN36 -> "Qwen3.6(실험): 어텐션 10층 KV f16 20KB/토큰 + DeltaNet 상태 ~63MiB(체크포인트 최대 2개) → 32K = 약 640MiB. " +
                        "가중치는 저장장치에서 읽으므로 컨텍스트를 줄일수록 가중치 캐시에 RAM 이 더 남음"
                },
                style = MaterialTheme.typography.bodySmall,
            )
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
