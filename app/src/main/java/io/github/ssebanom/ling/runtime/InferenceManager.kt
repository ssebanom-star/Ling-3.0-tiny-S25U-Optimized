package io.github.ssebanom.ling.runtime

import android.content.Context
import android.util.Log
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.data.Backend
import io.github.ssebanom.ling.data.ModelCatalog
import io.github.ssebanom.ling.data.ModelStore
import io.github.ssebanom.ling.domain.ModelFamily
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

    /** 모델 파일의 계열(템플릿·툴 포맷·권장 샘플링) */
    fun familyFor(path: File): ModelFamily = ModelCatalog.byFile(path.name)?.family ?: ModelFamily.detect(path.name)

    /** 실험 모델(RAM 초과)은 설정과 무관하게 CPU+mmap 으로만 돌린다 */
    fun isExperimental(path: File): Boolean = ModelCatalog.byFile(path.name)?.experimental == true

    fun effectiveBackend(s: AppSettings, path: File): Backend = if (isExperimental(path)) Backend.CPU else s.backend

    /** 현재 로드된(없으면 설정상) 모델의 계열 */
    val family: ModelFamily
        get() = engine.config?.modelPath?.let { familyFor(File(it)) } ?: ModelFamily.LING

        /** 설정 + 기기 프로파일 → 엔진 구성 */
    fun configFor(
        s: AppSettings,
        path: File,
        backend: Backend = s.backend,
        quirks: GpuQuirks = GpuQuirks.decode(s.gpuQuirks),
    ): EngineConfig {
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
                applyGpuQuirks(quirks)
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
            // 실험 모델(Qwen3.6)은 재귀 상태가 체크포인트당 ~65MB → RAM 압박을 줄이려 2개로 제한
            maxCheckpoints = if (isExperimental(path)) minOf(2, s.maxCheckpoints) else s.maxCheckpoints,
            devices = devices,
            // NPU/GPU 는 디바이스 버퍼로 올리므로 CPU 재배열 불필요(메모리 이중 상주 방지).
            // 실험 모델은 재배열하면 전체가 RAM 에 복사되어 mmap 으로 읽는 의미가 없어진다
            weightRepack = backend == Backend.CPU && !isExperimental(path),
            useMmap = backend == Backend.CPU,
        )
    }

    /** 설정과 다르면 (재)로드. 반환: 준비 완료 여부 */
    suspend fun ensureLoaded(): Boolean {
        val s = settings.current()
        val path = modelPath(s) ?: run { _status.value = EngineStatus.NoModel; return false }
        val backend = effectiveBackend(s, path)
        val want = configFor(s, path, backend)
        if (backend != Backend.CPU && want.devices.isEmpty()) return fallbackToCpu(s, path, "${backend.label}: 디바이스 없음")
        val cur = engine.config
        val quirksOk = backend != Backend.GPU || loadedQuirks == GpuQuirks.decode(s.gpuQuirks)
        if (cur != null && sameLoad(cur, want) && quirksOk) {
            if (cur.nThreads != want.nThreads || cur.nThreadsBatch != want.nThreadsBatch || cur.cpuMask != want.cpuMask) {
                engine.setThreads(want.nThreads, want.nThreadsBatch, want.cpuMask)
            }
            _status.value = EngineStatus.Ready(engine.modelInfo!!, engine.config!!, backend)
            return true
        }
        if (backend == Backend.CPU) return load(want, backend)
        // 가속기는 이 모델로 정확성 검사를 통과한 적이 없으면 먼저 검사(실패 시 CPU 복귀)
        if (s.acceleratorValidated != "${backend.name}:${path.name}") {
            val r = validateAcceleratorLocked(backend) { Log.i(TAG, "auto-validate: $it") }
            if (!r.passed) lastFallback = "${backend.label} 정확성 검사 실패 → CPU: ${r.detail}"
            return status.value is EngineStatus.Ready
        }
        if (load(want, backend)) {
            if (backend == Backend.GPU) loadedQuirks = GpuQuirks.decode(s.gpuQuirks)
            return true
        }
        return fallbackToCpu(s, path, "${backend.label} 로드 실패: ${(status.value as? EngineStatus.Error)?.message}")
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
        _status.value = EngineStatus.Loading(0f, note ?: File(cfg.modelPath).name)
        return try {
            engine.load(cfg) { p -> _status.value = EngineStatus.Loading(p, note ?: File(cfg.modelPath).name) }
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
        // 실험 모델(저장장치 스트리밍)은 느려서 후보와 측정 길이를 줄인다
        val slow = isExperimental(File(engine.config!!.modelPath))
        val decodeCands = (if (slow) listOf(4, 6, 8) else listOf(2, 3, 4, 5, 6, 8)).filter { it <= n }.distinct()
        val batchCands = listOf(4, 6, 8).filter { it <= n }.distinct()
        val tgN = if (slow) 8 else 32
        val table = ArrayList<Triple<String, Double, Double>>()
        val cfg0 = engine.config!!
        var bestTg = -1.0; var bestDecode = cfg0.nThreads; var bestMask = ""
        for (t in decodeCands) {
            for (mask in listOf("", DeviceProfiler.maskFor(p, t)).distinct()) {
                val label = "tg t=$t ${if (mask.isEmpty()) "OS" else "cpu[$mask]"}"
                onProgress(label)
                engine.setThreads(t, cfg0.nThreadsBatch, mask)
                engine.bench(0, if (slow) 2 else 8) // 워밍업
                val b = engine.bench(0, tgN, if (slow) 1 else 2)
                table += Triple(label, 0.0, b.tgTps)
                if (b.tgTps > bestTg) { bestTg = b.tgTps; bestDecode = t; bestMask = mask }
            }
        }
        var bestPp = -1.0; var bestBatch = cfg0.nThreadsBatch
        for (t in batchCands) {
            val label = "pp t=$t"
            onProgress(label)
            engine.setThreads(bestDecode, t, bestMask)
            val b = engine.bench(if (slow) 32 else 128, 0, 1)
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
     * 두 경로를 본다 — (B) 전체 배치 prefill, (S) 마지막 [PROBE_SINGLE] 토큰을 1개씩 디코드(생성 경로).
     * 기준값은 CPU 로 1회 계산해 파일로 보관(두 모델을 동시에 올리지 않기 위해).
     * 통과 기준(각 경로): top-1 일치, top-10 중 7개 이상 겹침, 공통 top-5 평균 |Δlogp| < 0.5
     *
     * GPU 는 실패 시 [GpuQuirks.CANDIDATES] 를 차례로 적용·재로드해 통과하는 첫 구성을 저장한다.
     */
    suspend fun validateAccelerator(backend: Backend, onProgress: (String) -> Unit): AccelCheck =
        lock.withLock { validateAcceleratorLocked(backend, onProgress) }

    private data class Probe(val batch: List<Pair<Int, Float>>, val single: List<Pair<Int, Float>>)

    private data class Verdict(val passed: Boolean, val top1: Boolean, val overlap: Int, val mad: Double, val detail: String)

    private suspend fun runProbe(): Probe {
        val toks = engine.tokenize(family.probePrompt())
        return Probe(engine.evalTopK(toks, 20), engine.evalTopK(toks, 20, PROBE_SINGLE))
    }

    /**
     * 비교는 기준 분포에서 의미 있는 토큰(log-prob > [TAIL_LP])만 본다 — 극단적 꼬리 확률은 정상 구현끼리도
     * 수치 오차가 커서 오판을 만든다. 의미 있는 토큰이 top-1 하나뿐이면 top-1 과 그 log-prob 만 비교.
     */
    private fun compare(got: List<Pair<Int, Float>>, ref: List<Pair<Int, Float>>): Verdict {
        val top1 = got.firstOrNull()?.first == ref.firstOrNull()?.first
        val refSig = ref.take(10).filter { it.second > TAIL_LP }.ifEmpty { ref.take(1) }
        val gotIds = got.take(10).map { it.first }.toSet()
        val overlap = refSig.count { it.first in gotIds }
        val gotMap = got.toMap()
        val diffs = refSig.take(5).map { (id, lp) -> gotMap[id]?.let { kotlin.math.abs(it - lp).toDouble() } ?: 99.0 }
        val mad = diffs.average()
        val passed = top1 && overlap >= kotlin.math.ceil(refSig.size * 0.7) && mad < 0.5
        return Verdict(passed, top1, overlap, mad,
            "top1=${if (top1) "O" else "X"} ov=$overlap/${refSig.size} |Δ|=${"%.2f".format(mad)}")
    }

    private fun compare(got: Probe, ref: Probe): Verdict {
        val b = compare(got.batch, ref.batch)
        val g = compare(got.single, ref.single)
        return Verdict(b.passed && g.passed, b.top1 && g.top1, minOf(b.overlap, g.overlap), maxOf(b.mad, g.mad),
            "prefill[${b.detail}] decode[${g.detail}]")
    }

    private suspend fun cpuReference(s: AppSettings, path: File, onProgress: (String) -> Unit): Probe? {
        val refFile = File(context.filesDir, "accel_ref2_${path.name}.txt")
        if (!refFile.exists()) {
            onProgress("CPU 기준값 계산")
            if (!load(configFor(s, path, Backend.CPU), Backend.CPU)) return null
            val p = runProbe()
            refFile.writeText((p.batch.map { "B ${it.first} ${it.second}" } + p.single.map { "S ${it.first} ${it.second}" }).joinToString("\n"))
        }
        val rows = refFile.readLines().filter { it.isNotBlank() }.map { it.split(' ') }
        fun pick(tag: String) = rows.filter { it[0] == tag }.map { it[1].toInt() to it[2].toFloat() }
        return Probe(pick("B"), pick("S"))
    }

    private suspend fun validateAcceleratorLocked(backend: Backend, onProgress: (String) -> Unit): AccelCheck =
        try {
            validateInner(backend) { note = it; onProgress(it) }
        } finally {
            note = null
        }

    /** 로드 상태 표시에 덧붙일 진행 메모(정확성 검사 중) */
    @Volatile private var note: String? = null

    private suspend fun validateInner(backend: Backend, onProgress: (String) -> Unit): AccelCheck {
        val s = settings.current()
        val path = modelPath(s) ?: return AccelCheck(backend, false, false, 0, 0.0, "모델 없음")
        val ref = cpuReference(s, path, onProgress) ?: return AccelCheck(backend, false, false, 0, 0.0, "CPU 기준 로드 실패")

        val candidates = if (backend == Backend.GPU) {
            (listOf(GpuQuirks.decode(s.gpuQuirks)) + GpuQuirks.CANDIDATES).distinct()
        } else listOf(GpuQuirks())
        val log = StringBuilder()
        var last: Verdict? = null
        for ((i, q) in candidates.withIndex()) {
            val tag = if (backend == Backend.GPU) " [${i + 1}/${candidates.size}: ${q.label}]" else ""
            onProgress("${backend.label} 로드$tag")
            val cfg = configFor(s, path, backend, q)
            if (cfg.devices.isEmpty()) return fail(backend, "디바이스 없음 (빌드에 백엔드 미포함 또는 권한 거부)", s, path)
            if (!load(cfg, backend)) {
                log.append("${q.label}: 로드 실패\n")
                continue
            }
            if (backend == Backend.GPU) loadedQuirks = q
            onProgress("${backend.label} 평가$tag")
            val got = runCatching { runProbe() }.getOrElse {
                log.append("${q.label}: 평가 실패 ${it.message}\n")
                continue
            }
            val v = compare(got, ref)
            last = v
            log.append("${q.label}: ${if (v.passed) "통과" else "실패"} ${v.detail}\n")
            Log.i(TAG, "validate ${backend.name} ${q.label}: ${v.detail}")
            if (!v.passed) continue

            var detail = log.toString()
            onProgress("${backend.label} 속도 측정 (tg$ACCEL_TG)")
            runCatching { engine.bench(0, ACCEL_TG, 1) }.onSuccess { detail += "tg$ACCEL_TG=${"%.1f".format(it.tgTps)} tok/s" }
            settings.update {
                it.copy(
                    backend = backend,
                    acceleratorValidated = "${backend.name}:${path.name}",
                    gpuQuirks = if (backend == Backend.GPU) q.encode() else it.gpuQuirks,
                )
            }
            return AccelCheck(backend, true, v.top1, v.overlap, v.mad, detail.trim())
        }
        val v = last
        return fail(backend, "정확성 기준 미달(모든 구성)\n$log".trim(), s, path)
            .copy(top1Match = v?.top1 ?: false, overlap10 = v?.overlap ?: 0, meanAbsDiff = v?.mad ?: 0.0)
    }

    /** 현재 GPU 에 올라간 우회 구성 */
    @Volatile private var loadedQuirks: GpuQuirks? = null

    private fun applyGpuQuirks(q: GpuQuirks) {
        for ((k, v) in q.env()) {
            runCatching { if (v == null) android.system.Os.unsetenv(k) else android.system.Os.setenv(k, v, true) }
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
        /** 정확성 검사에서 1개씩 디코드할 마지막 토큰 수 */
        const val PROBE_SINGLE = 8
        /** 정확성 비교에서 무시할 꼬리 확률 경계(log-prob) */
        const val TAIL_LP = -8f
    }
}
