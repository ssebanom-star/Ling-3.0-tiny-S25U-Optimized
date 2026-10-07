package io.github.ssebanom.ling.engine

import android.content.Context
import android.os.Process
import android.system.Os
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ling-3.0-tiny 추론 엔진 (프로세스당 1개).
 *
 * llama.cpp 컨텍스트는 스레드 안전하지 않으므로 모든 네이티브 호출은 전용 단일 스레드
 * ("ling-infer")에서 직렬 실행한다. [cancel] 만 예외적으로 어느 스레드에서나 호출 가능.
 */
class LingEngine private constructor(nativeLibDir: String) {

    private val inferTid = AtomicInteger(0)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread({
            inferTid.set(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            r.run()
        }, "ling-infer")
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val handle: Long

    @Volatile var config: EngineConfig? = null
        private set
    @Volatile var modelInfo: ModelInfo? = null
        private set

    init {
        // Hexagon: 모델(4.6GB) > 세션당 VA 3.5GB → 같은 NPU 위 가상 세션 2개로 레이어 분할 (docs/01 §3.1)
        if (Os.getenv("GGML_HEXAGON_DEVICES") == null) {
            runCatching { Os.setenv("GGML_HEXAGON_DEVICES", hexagonDevices, true) }
        }
        // Hexagon DSP skel(libggml-htp-v*.so) 탐색 경로. FastRPC 는 ';' 구분
        runCatching {
            Os.setenv(
                "ADSP_LIBRARY_PATH",
                "$nativeLibDir;/vendor/dsp/cdsp;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp",
                true,
            )
        }
        LingNative.nativeInit(nativeLibDir)
        handle = LingNative.nativeCreate()
    }

    /** 추론 스레드 TID (ADPF 성능 힌트 세션 등록용). 첫 작업 전에는 0 */
    val inferenceThreadId: Int get() = inferTid.get()

    val isLoaded: Boolean get() = config != null

    private suspend fun <T> onInfer(block: () -> T): T = withContext(dispatcher) { block() }

    suspend fun load(cfg: EngineConfig, onProgress: (Float) -> Unit = {}) = onInfer {
        config = null
        modelInfo = null
        val err = LingNative.nativeLoad(
            handle, cfg.modelPath,
            intArrayOf(cfg.nCtx, cfg.nBatch, cfg.nUbatch, cfg.nThreads, cfg.nThreadsBatch, cfg.poll, cfg.nGpuLayers, cfg.maxCheckpoints),
            booleanArrayOf(cfg.strictCpu, cfg.useMmap, cfg.useMlock, cfg.flashAttn, cfg.kvQ8, cfg.weightRepack),
            cfg.cpuMask, cfg.devices,
            NativeCallbacks.Load { p -> onProgress(p); true },
        )
        if (err != null) throw EngineException(err)
        config = cfg
        val mi = LingNative.nativeModelInfo(handle)
        modelInfo = ModelInfo(mi[0], mi[1].toLong(), mi[2].toLong(), mi[3].toInt())
    }

    suspend fun unload() = onInfer {
        LingNative.nativeUnload(handle)
        config = null
        modelInfo = null
    }

    suspend fun setThreads(nThreads: Int, nThreadsBatch: Int, cpuMask: String = config?.cpuMask ?: "") = onInfer {
        LingNative.nativeSetThreads(handle, nThreads, nThreadsBatch, cpuMask)
        config = config?.copy(nThreads = nThreads, nThreadsBatch = nThreadsBatch, cpuMask = cpuMask)
    }

    suspend fun tokenize(text: String): IntArray = onInfer { LingNative.nativeTokenize(handle, text) }

    suspend fun detokenize(tokens: IntArray): String = onInfer { LingNative.nativeDetokenize(handle, tokens) }

    /** 컨텍스트를 segments 로 구성된 프롬프트에 맞춘다(캐시 재사용 + 체크포인트 복원 + 차이분 prefill). */
    suspend fun sync(segments: List<PromptSegment>, onProgress: (Int, Int) -> Unit = { _, _ -> }): SyncStats = onInfer {
        val r = LingNative.nativeSync(
            handle,
            Array(segments.size) { segments[it].text },
            Array(segments.size) { segments[it].tokens },
            BooleanArray(segments.size) { segments[it].boundaryAfter },
            NativeCallbacks.Prefill { d, t -> onProgress(d, t) },
        )
        if (r[0] == 0.0) throw EngineException(LingNative.nativeLastError(handle))
        SyncStats(r[1].toInt(), r[2].toInt(), r[3].toInt(), r[4] != 0.0, r[5] != 0.0, r[6])
    }

    /** sync 직후 호출. onPiece 가 false 를 반환하면 중단. */
    suspend fun generate(sampler: SamplerConfig, onPiece: (String) -> Boolean): GenerateStats = onInfer {
        val toks = LingNative.nativeGenerate(
            handle,
            floatArrayOf(sampler.temperature, sampler.topP, sampler.minP, sampler.repeatPenalty, sampler.presencePenalty),
            intArrayOf(sampler.maxTokens, sampler.topK, sampler.repeatLastN, sampler.seed),
            NativeCallbacks.Token { piece, _ -> onPiece(piece) },
        )
        val s = LingNative.nativeLastGenerate(handle)
        val reason = StopReason.entries[s[0].toInt().coerceIn(0, StopReason.entries.size - 1)]
        if (reason == StopReason.ERROR) throw EngineException(LingNative.nativeLastError(handle))
        GenerateStats(reason, toks, LingNative.nativeDetokenize(handle, toks), s[1], s[2])
    }

    /** 진행 중인 prefill/생성을 중단 (어느 스레드에서나 호출 가능) */
    fun cancel() = LingNative.nativeCancel(handle)

    suspend fun checkpointNow(): Boolean = onInfer { LingNative.nativeCheckpointNow(handle) }

    suspend fun clearCheckpoints() = onInfer { LingNative.nativeClearCheckpoints(handle) }

    suspend fun reset() = onInfer { LingNative.nativeReset(handle) }

    suspend fun state(): EngineState = onInfer {
        val s = LingNative.nativeState(handle)
        EngineState(s[0].toInt(), s[1], s[2].toInt(), s[3].toInt())
    }

    suspend fun bench(nPrompt: Int, nGen: Int, reps: Int = 1): BenchStats = onInfer {
        val b = LingNative.nativeBench(handle, nPrompt, nGen, reps)
        BenchStats(nPrompt, nGen, b[0], b[1])
    }

    /**
     * 고정 토큰열의 마지막 위치 상위 k 토큰 (id → log-prob). 컨텍스트를 비운다.
     * [nSingle] > 0 이면 마지막 nSingle 토큰을 1개씩 디코드해 생성(배치 1) 경로를 검사한다.
     */
    suspend fun evalTopK(tokens: IntArray, k: Int, nSingle: Int = 0): List<Pair<Int, Float>> = onInfer {
        val a = LingNative.nativeEvalTopK(handle, tokens, k, nSingle)
        (0 until a.size / 2).map { a[2 * it].toInt() to a[2 * it + 1] }
    }

    fun devices(): List<DeviceInfo> = LingNative.nativeDevices().map {
        val p = it.split('|')
        DeviceInfo(p[0], p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, p.getOrElse(3) { "0" }.toLongOrNull() ?: 0)
    }

    fun systemInfo(): String = LingNative.nativeSystemInfo()

    /**
     * GPU("opencl") / NPU("hexagon") 백엔드를 필요할 때만 로드한다.
     * Hexagon 은 등록 시 FastRPC 세션을 열므로 CPU 모드에서는 로드하지 않는다. 한 번 로드되면 프로세스 종료까지 유지.
     */
    fun loadBackend(name: String): Boolean = LingNative.nativeLoadBackend(name)

    companion object {
        @Volatile private var instance: LingEngine? = null

        /** 첫 [get] 전에 바꿀 수 있는 Hexagon 세션 구성 (GGML_HEXAGON_DEVICES 형식) */
        @Volatile var hexagonDevices: String = "HTP0:0,HTP0:1"

        fun get(context: Context): LingEngine = instance ?: synchronized(this) {
            instance ?: LingEngine(context.applicationInfo.nativeLibraryDir).also { instance = it }
        }
    }
}
