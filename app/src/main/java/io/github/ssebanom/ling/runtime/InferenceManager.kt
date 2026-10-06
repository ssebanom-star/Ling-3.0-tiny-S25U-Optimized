package io.github.ssebanom.ling.runtime

import android.content.Context
import android.util.Log
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.Backend
import io.github.ssebanom.ling.data.ModelStore
import io.github.ssebanom.ling.data.SettingsRepository
import io.github.ssebanom.ling.engine.BenchStats
import io.github.ssebanom.ling.engine.EngineConfig
import io.github.ssebanom.ling.engine.EngineException
import io.github.ssebanom.ling.engine.LingEngine
import io.github.ssebanom.ling.engine.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

sealed interface EngineStatus {
    data object NoModel : EngineStatus
    data object Unloaded : EngineStatus
    data class Loading(val progress: Float, val what: String) : EngineStatus
    data class Ready(val info: ModelInfo, val config: EngineConfig, val backend: Backend) : EngineStatus
    data class Error(val message: String) : EngineStatus
}

data class TuneResult(
    val decodeThreads: Int,
    val batchThreads: Int,
    val cpuMask: String,
    val table: List<Triple<String, Double, Double>>, // label, pp tok/s, tg tok/s
)

data class AccelCheck(val backend: Backend, val passed: Boolean, val top1Match: Boolean, val overlap10: Int, val meanAbsDiff: Double, val detail: String)

/**
 * 엔진 수명주기/구성 관리: 설정 → EngineConfig 변환, 로드/언로드, 자동 튜닝, 가속기 정확성 검사, 벤치.
 * 무거운 작업은 [lock] 으로 직렬화한다(대화 생성 포함).
 */
class InferenceManager(
    private val context: Context,
    val engine: LingEngine,
    private val settings: SettingsRepository,
    private val models: ModelStore,
) {
    val lock = Mutex()
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Unloaded)
    val status: StateFlow<EngineStatus> = _status

    val profile: DeviceProfile by lazy { DeviceProfiler.profile(context) }

    fun modelPath(s: AppSettings): File? {
        if (s.modelFile.isNotEmpty()) File(models.dir, s.modelFile).takeIf { it.exists() }?.let { return it }
        return models.listModels().firstOrNull { !it.name.endsWith(".part") }
    }

    /** 설정 + 기기 프로파일 → 엔진 구성 */
    fun configFor(s: AppSettings, path: File, backend: Backend = s.backend): EngineConfig {
        val p = profile
        val decode = when {
            s.threads > 0 -> s.threads
            s.tunedThreads > 0 -> s.tunedThreads
            else -> minOf(4, p.nCpus) // 튜닝 전 기본: 메모리 바운드 디코드는 4 스레드 근방이 보통 최적
        }
        val batch = when {
            s.threadsBatch > 0 -> s.threadsBatch
            s.tunedThreadsBatch > 0 -> s.tunedThreadsBatch
            else -> minOf(6, p.nCpus)
        }
        val mask = if (s.threads == 0 && s.tunedCpuMask.isNotEmpty()) s.tunedCpuMask else ""
        val devices = when (backend) {
            Backend.CPU -> ""
            Backend.NPU -> {
                engine.loadBackend("hexagon")
                engine.devices().filter { it.name.startsWith("HTP") }.joinToString(",") { it.name }
            }
            Backend.GPU -> {
                engine.loadBackend("opencl")
                engine.devices().firstOrNull { it.type == "GPU" || it.name.contains("OpenCL", true) }?.name ?: ""
            }
        }
        return EngineConfig(
            modelPath = path.absolutePath,
            nCtx = s.nCtx,
            nThreads = decode,
            nThreadsBatch = batch,
            cpuMask = mask,
            kvQ8 = s.kvQ8,
            maxCheckpoints = s.maxCheckpoints,
            devices = devices,
            // NPU/GPU 는 디바이스 버퍼로 올리므로 CPU 재배열 불필요(메모리 이중 상주 방지)
            weightRepack = backend == Backend.CPU,
            useMmap = backend == Backend.CPU,
        )
    }

    /** 설정과 다르면 (재)로드. 반환: 준비 완료 여부 */
    suspend fun ensureLoaded(): Boolean {
        val s = settings.current()
        val path = modelPath(s) ?: run { _status.value = EngineStatus.NoModel; return false }
        val backend = s.backend
        val want = configFor(s, path, backend)
        if (backend != Backend.CPU && want.devices.isEmpty()) return fallbackToCpu(s, path, "${backend.label}: 디바이스 없음")
        val cur = engine.config
        if (cur != null && sameLoad(cur, want)) {
            if (cur.nThreads != want.nThreads || cur.nThreadsBatch != want.nThreadsBatch || cur.cpuMask != want.cpuMask) {
                engine.setThreads(want.nThreads, want.nThreadsBatch, want.cpuMask)
            }
            _status.value = EngineStatus.Ready(engine.modelInfo!!, engine.config!!, backend)
            return true
        }
        if (load(want, backend)) return true
        return if (backend != Backend.CPU) fallbackToCpu(s, path, "${backend.label} 로드 실패: ${(status.value as? EngineStatus.Error)?.message}") else false
    }

    /** 가속기 사용 불가 시 설정을 CPU 로 바꾸고 CPU 로 로드 */
    private suspend fun fallbackToCpu(s: AppSettings, path: File, why: String): Boolean {
        Log.w(TAG, "$why → CPU 복귀")
        lastFallback = why
        settings.update { it.copy(backend = Backend.CPU) }
        return load(configFor(s, path, Backend.CPU), Backend.CPU)
    }

    /** 마지막 CPU 자동 복귀 사유(UI 표시용) */
    @Volatile var lastFallback: String? = null
        private set

    private fun sameLoad(a: EngineConfig, b: EngineConfig) =
        a.modelPath == b.modelPath && a.nCtx == b.nCtx && a.kvQ8 == b.kvQ8 && a.devices == b.devices &&
            a.maxCheckpoints == b.maxCheckpoints && a.weightRepack == b.weightRepack

    private suspend fun load(cfg: EngineConfig, backend: Backend): Boolean {
        val need = File(cfg.modelPath).length()
        val avail = DeviceProfiler.profile(context).availRamBytes
        if (avail < need + 600L * 1024 * 1024) {
            Log.w(TAG, "가용 메모리 부족 가능: avail=${avail shr 20}MiB need=${need shr 20}MiB")
        }
        _status.value = EngineStatus.Loading(0f, File(cfg.modelPath).name)
        return try {
            engine.load(cfg) { p -> _status.value = EngineStatus.Loading(p, File(cfg.modelPath).name) }
            _status.value = EngineStatus.Ready(engine.modelInfo!!, cfg, backend)
            true
        } catch (e: EngineException) {
            _status.value = EngineStatus.Error(e.message ?: "load failed")
            false
        }
    }

    suspend fun unload() {
        engine.unload()
        _status.value = EngineStatus.Unloaded
    }

    /**
     * 스레드/코어 배치 자동 튜닝 (CPU 백엔드). 디코드 tg32, prefill pp128 실측으로 최고값 선택.
     * NPU/GPU 백엔드에서는 스레드 스윕 없이 [ACCEL_TG] 토큰 생성 속도만 측정한다.
     * [target] 을 주면 그 백엔드로 전환(설정 저장) 후 측정한다. 전환 실패 시 CPU 로 복귀해 CPU 를 측정.
     */
    suspend fun autoTune(target: Backend? = null, onProgress: (String) -> Unit): TuneResult? = lock.withLock {
        if (target != null && target != settings.current().backend) {
            onProgress("${target.label} 로 전환")
            settings.update { it.copy(backend = target) }
        }
        if (!ensureLoaded()) return null
        val backend = (status.value as? EngineStatus.Ready)?.backend ?: settings.current().backend
        if (backend != Backend.CPU) {
            val c = engine.config!!
            val label = "${backend.label} tg$ACCEL_TG"
            onProgress(label)
            val b = engine.bench(0, ACCEL_TG, 1)
            return TuneResult(c.nThreads, c.nThreadsBatch, c.cpuMask, listOf(Triple(label, 0.0, b.tgTps)))
        }
        val p = profile
        val n = p.nCpus
        val decodeCands = listOf(2, 3, 4, 5, 6, 8).filter { it <= n }.distinct()
        val batchCands = listOf(4, 6, 8).filter { it <= n }.distinct()
        val table = ArrayList<Triple<String, Double, Double>>()
        val cfg0 = engine.config!!
        var bestTg = -1.0; var bestDecode = cfg0.nThreads; var bestMask = ""
        for (t in decodeCands) {
            for (mask in listOf("", DeviceProfiler.maskFor(p, t)).distinct()) {
                val label = "tg t=$t ${if (mask.isEmpty()) "OS" else "cpu[$mask]"}"
                onProgress(label)
                engine.setThreads(t, cfg0.nThreadsBatch, mask)
                engine.bench(0, 8) // 워밍업
                val b = engine.bench(0, 32, 2)
                table += Triple(label, 0.0, b.tgTps)
                if (b.tgTps > bestTg) { bestTg = b.tgTps; bestDecode = t; bestMask = mask }
            }
        }
        var bestPp = -1.0; var bestBatch = cfg0.nThreadsBatch
        for (t in batchCands) {
            val label = "pp t=$t"
            onProgress(label)
            engine.setThreads(bestDecode, t, bestMask)
            val b = engine.bench(128, 0, 1)
            table += Triple(label, b.ppTps, 0.0)
            if (b.ppTps > bestPp) { bestPp = b.ppTps; bestBatch = t }
        }
        engine.setThreads(bestDecode, bestBatch, bestMask)
        settings.update { it.copy(tunedThreads = bestDecode, tunedThreadsBatch = bestBatch, tunedCpuMask = bestMask) }
        _status.value = EngineStatus.Ready(engine.modelInfo!!, engine.config!!, settings.current().backend)
        TuneResult(bestDecode, bestBatch, bestMask, table)
    }

    suspend fun bench(nPrompt: Int, nGen: Int, reps: Int): BenchStats? = lock.withLock {
        if (!ensureLoaded()) return null
        engine.bench(nPrompt, nGen, reps)
    }

    /**
     * 가속기 정확성 검사: 고정 프롬프트의 마지막 위치 상위 20 log-prob 을 CPU 기준값과 비교.
     * 기준값은 CPU 로 1회 계산해 파일로 보관(두 모델을 동시에 올리지 않기 위해).
     * 통과 기준: top-1 일치, top-10 중 7개 이상 겹침, 공통 top-5 평균 |Δlogp| < 0.5
     */
    suspend fun validateAccelerator(backend: Backend, onProgress: (String) -> Unit): AccelCheck = lock.withLock {
        val s = settings.current()
        val path = modelPath(s) ?: return AccelCheck(backend, false, false, 0, 0.0, "모델 없음")
        val refFile = File(context.filesDir, "accel_ref_${path.name}.txt")
        if (!refFile.exists()) {
            onProgress("CPU 기준값 계산")
            load(configFor(s, path, Backend.CPU), Backend.CPU)
            val ref = engine.evalTopK(engine.tokenize(PROBE), 20)
            refFile.writeText(ref.joinToString("\n") { "${it.first} ${it.second}" })
        }
        val ref = refFile.readLines().filter { it.isNotBlank() }.map { l -> l.split(' ').let { it[0].toInt() to it[1].toFloat() } }

        onProgress("${backend.label} 로드")
        val cfg = configFor(s, path, backend)
        if (cfg.devices.isEmpty()) return fail(backend, "디바이스 없음 (빌드에 백엔드 미포함 또는 권한 거부)", s, path)
        if (!load(cfg, backend)) return fail(backend, (status.value as? EngineStatus.Error)?.message ?: "로드 실패", s, path)
        onProgress("${backend.label} 평가")
        val got = runCatching { engine.evalTopK(engine.tokenize(PROBE), 20) }.getOrElse {
            return fail(backend, "평가 실패: ${it.message}", s, path)
        }
        val top1 = got.firstOrNull()?.first == ref.firstOrNull()?.first
        val overlap = got.take(10).map { it.first }.intersect(ref.take(10).map { it.first }.toSet()).size
        val refMap = ref.toMap()
        val diffs = got.take(5).mapNotNull { (id, lp) -> refMap[id]?.let { kotlin.math.abs(it - lp).toDouble() } }
        val mad = if (diffs.isEmpty()) 99.0 else diffs.average()
        val passed = top1 && overlap >= 7 && mad < 0.5
        var detail = "top1=${top1}, overlap10=$overlap, mean|Δlogp|=${"%.3f".format(mad)}"
        if (passed) {
            onProgress("${backend.label} 속도 측정 (tg$ACCEL_TG)")
            runCatching { engine.bench(0, ACCEL_TG, 1) }.onSuccess { detail += ", tg$ACCEL_TG=${"%.1f".format(it.tgTps)} tok/s" }
        }
        if (passed) {
            settings.update { it.copy(backend = backend, acceleratorValidated = "${backend.name}:${path.name}") }
            AccelCheck(backend, true, top1, overlap, mad, detail)
        } else {
            fail(backend, "정확성 기준 미달: $detail", s, path).copy(top1Match = top1, overlap10 = overlap, meanAbsDiff = mad)
        }
    }

    private suspend fun fail(backend: Backend, why: String, s: AppSettings, path: File): AccelCheck {
        settings.update { it.copy(backend = Backend.CPU) }
        load(configFor(s, path, Backend.CPU), Backend.CPU)
        return AccelCheck(backend, false, false, 0, 0.0, why)
    }

    companion object {
        private const val TAG = "InferenceManager"
        /** NPU/GPU 속도 측정 시 생성 토큰 수 (가속기는 느릴 수 있어 짧게) */
        const val ACCEL_TG = 5
        /** 정확성 검사용 고정 프롬프트(한국어/영어/수식 혼합) */
        const val PROBE = "<role>SYSTEM</role>detailed thinking off<|role_end|><role>HUMAN</role>" +
            "대한민국의 수도는 어디인가요? Then compute 17*23 and explain briefly.<|role_end|>" +
            "<role>ASSISTANT</role>\n<think></think>"
    }
}
