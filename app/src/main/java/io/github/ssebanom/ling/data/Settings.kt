package io.github.ssebanom.ling.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 실행 백엔드. 기본 GPU, 디바이스 없음/로드 실패 시 CPU 로 자동 복귀 */
enum class Backend(val label: String) {
    CPU("CPU"),
    NPU("Hexagon NPU (실험)"),
    GPU("Adreno GPU OpenCL"),
}

data class AppSettings(
    val modelFile: String = "",
    val nCtx: Int = 16384,
    val backend: Backend = Backend.GPU,
    /** 0 = 자동(튜닝 결과 사용) */
    val threads: Int = 0,
    val threadsBatch: Int = 0,
    val tunedThreads: Int = 0,
    val tunedThreadsBatch: Int = 0,
    val tunedCpuMask: String = "",
    val kvQ8: Boolean = false,
    val maxCheckpoints: Int = 8,
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 20,
    val minP: Float = 0.0f,
    val repeatPenalty: Float = 1.0f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "",
    val defaultThinking: Boolean = true,
    val toolsEnabled: Boolean = false,
    val thermalGovernor: Boolean = true,
    val perfHints: Boolean = true,
    val autoUnloadMinutes: Int = 10,
    /** 가속기 정확성 검사 통과 기록 "BACKEND:modelFile" */
    val acceleratorValidated: String = "",
    /** GPU 정확성 우회 구성(GpuQuirks.encode). 자동 탐색 결과 */
    val gpuQuirks: String = "",
    /** 첫 실행 자동 설치(다운로드→검증→로드→튜닝) 완료 여부 */
    val setupDone: Boolean = false,
    /** 모바일 데이터(종량제)로 모델 다운로드 허용 */
    val allowMeteredDownload: Boolean = false,
    /** 모델 계열 권장 샘플링 사용(끄면 아래 사용자 값) */
    val useRecommendedSampling: Boolean = true,
    /** 켜진 툴 묶음(ToolGroup.name). 파일·화면 제어는 권한 허용 후 사용자가 켬 */
    val toolGroups: Set<String> = setOf("BASIC", "WEB", "DEVICE"),
    /** 부수효과 있는 툴(탭·입력·파일 쓰기·앱 열기 등)을 확인 없이 실행 */
    val autoApproveTools: Boolean = false,
    /** 웹 검색: 비우면 DuckDuckGo, 값이 있으면 SearXNG 인스턴스 주소(JSON API) */
    val searxngUrl: String = "",
    /** Brave Search API 키(있으면 우선 사용) */
    val braveApiKey: String = "",
    /** 한 번의 사용자 요청에서 툴 호출 최대 라운드 */
    val maxToolRounds: Int = 8,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val modelFile = stringPreferencesKey("model_file")
        val nCtx = intPreferencesKey("n_ctx")
        val backend = stringPreferencesKey("backend")
        val threads = intPreferencesKey("threads")
        val threadsBatch = intPreferencesKey("threads_batch")
        val tunedThreads = intPreferencesKey("tuned_threads")
        val tunedThreadsBatch = intPreferencesKey("tuned_threads_batch")
        val tunedCpuMask = stringPreferencesKey("tuned_cpumask")
        val kvQ8 = booleanPreferencesKey("kv_q8")
        val maxCheckpoints = intPreferencesKey("max_checkpoints")
        val temperature = floatPreferencesKey("temperature")
        val topP = floatPreferencesKey("top_p")
        val topK = intPreferencesKey("top_k")
        val minP = floatPreferencesKey("min_p")
        val repeatPenalty = floatPreferencesKey("repeat_penalty")
        val maxTokens = intPreferencesKey("max_tokens")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val defaultThinking = booleanPreferencesKey("default_thinking")
        val toolsEnabled = booleanPreferencesKey("tools_enabled")
        val thermalGovernor = booleanPreferencesKey("thermal_governor")
        val perfHints = booleanPreferencesKey("perf_hints")
        val autoUnloadMinutes = intPreferencesKey("auto_unload_minutes")
        val acceleratorValidated = stringPreferencesKey("accel_validated")
        val gpuQuirks = stringPreferencesKey("gpu_quirks")
        val setupDone = booleanPreferencesKey("setup_done")
        val allowMeteredDownload = booleanPreferencesKey("allow_metered_download")
        val useRecommendedSampling = booleanPreferencesKey("use_recommended_sampling")
        val toolGroups = stringSetPreferencesKey("tool_groups")
        val autoApproveTools = booleanPreferencesKey("auto_approve_tools")
        val searxngUrl = stringPreferencesKey("searxng_url")
        val braveApiKey = stringPreferencesKey("brave_api_key")
        val maxToolRounds = intPreferencesKey("max_tool_rounds")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { flowValue(it, AppSettings()) }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(flowValue(p, AppSettings()))
            p[K.modelFile] = n.modelFile
            p[K.nCtx] = n.nCtx
            p[K.backend] = n.backend.name
            p[K.threads] = n.threads
            p[K.threadsBatch] = n.threadsBatch
            p[K.tunedThreads] = n.tunedThreads
            p[K.tunedThreadsBatch] = n.tunedThreadsBatch
            p[K.tunedCpuMask] = n.tunedCpuMask
            p[K.kvQ8] = n.kvQ8
            p[K.maxCheckpoints] = n.maxCheckpoints
            p[K.temperature] = n.temperature
            p[K.topP] = n.topP
            p[K.topK] = n.topK
            p[K.minP] = n.minP
            p[K.repeatPenalty] = n.repeatPenalty
            p[K.maxTokens] = n.maxTokens
            p[K.systemPrompt] = n.systemPrompt
            p[K.defaultThinking] = n.defaultThinking
            p[K.toolsEnabled] = n.toolsEnabled
            p[K.thermalGovernor] = n.thermalGovernor
            p[K.perfHints] = n.perfHints
            p[K.autoUnloadMinutes] = n.autoUnloadMinutes
            p[K.acceleratorValidated] = n.acceleratorValidated
            p[K.gpuQuirks] = n.gpuQuirks
            p[K.setupDone] = n.setupDone
            p[K.allowMeteredDownload] = n.allowMeteredDownload
            p[K.useRecommendedSampling] = n.useRecommendedSampling
            p[K.toolGroups] = n.toolGroups
            p[K.autoApproveTools] = n.autoApproveTools
            p[K.searxngUrl] = n.searxngUrl
            p[K.braveApiKey] = n.braveApiKey
            p[K.maxToolRounds] = n.maxToolRounds
        }
    }

    private fun flowValue(p: Preferences, d: AppSettings) = AppSettings(
        modelFile = p[K.modelFile] ?: d.modelFile,
        nCtx = p[K.nCtx] ?: d.nCtx,
        backend = runCatching { Backend.valueOf(p[K.backend] ?: d.backend.name) }.getOrDefault(Backend.GPU),
        threads = p[K.threads] ?: d.threads,
        threadsBatch = p[K.threadsBatch] ?: d.threadsBatch,
        tunedThreads = p[K.tunedThreads] ?: d.tunedThreads,
        tunedThreadsBatch = p[K.tunedThreadsBatch] ?: d.tunedThreadsBatch,
        tunedCpuMask = p[K.tunedCpuMask] ?: d.tunedCpuMask,
        kvQ8 = p[K.kvQ8] ?: d.kvQ8,
        maxCheckpoints = p[K.maxCheckpoints] ?: d.maxCheckpoints,
        temperature = p[K.temperature] ?: d.temperature,
        topP = p[K.topP] ?: d.topP,
        topK = p[K.topK] ?: d.topK,
        minP = p[K.minP] ?: d.minP,
        repeatPenalty = p[K.repeatPenalty] ?: d.repeatPenalty,
        maxTokens = p[K.maxTokens] ?: d.maxTokens,
        systemPrompt = p[K.systemPrompt] ?: d.systemPrompt,
        defaultThinking = p[K.defaultThinking] ?: d.defaultThinking,
        toolsEnabled = p[K.toolsEnabled] ?: d.toolsEnabled,
        thermalGovernor = p[K.thermalGovernor] ?: d.thermalGovernor,
        perfHints = p[K.perfHints] ?: d.perfHints,
        autoUnloadMinutes = p[K.autoUnloadMinutes] ?: d.autoUnloadMinutes,
        acceleratorValidated = p[K.acceleratorValidated] ?: d.acceleratorValidated,
        gpuQuirks = p[K.gpuQuirks] ?: d.gpuQuirks,
        setupDone = p[K.setupDone] ?: d.setupDone,
        allowMeteredDownload = p[K.allowMeteredDownload] ?: d.allowMeteredDownload,
        useRecommendedSampling = p[K.useRecommendedSampling] ?: d.useRecommendedSampling,
        toolGroups = p[K.toolGroups] ?: d.toolGroups,
        autoApproveTools = p[K.autoApproveTools] ?: d.autoApproveTools,
        searxngUrl = p[K.searxngUrl] ?: d.searxngUrl,
        braveApiKey = p[K.braveApiKey] ?: d.braveApiKey,
        maxToolRounds = p[K.maxToolRounds] ?: d.maxToolRounds,
    )
}
