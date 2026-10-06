package io.github.ssebanom.ling.runtime

import android.util.Log
import io.github.ssebanom.ling.data.Backend
import io.github.ssebanom.ling.data.ModelCatalog
import io.github.ssebanom.ling.data.ModelStore
import io.github.ssebanom.ling.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 첫 실행 자동 설치: 기기 확인 → 양자화 선택 → 다운로드(이어받기·네트워크 대기) → SHA-256 검증
 * → 설정 저장 → 모델 로드 → 스레드 자동 튜닝 → 완료.
 * 앱이 죽어도 `.part` 와 설정이 남아 다음 실행 때 같은 지점부터 다시 진행한다(멱등).
 */
class SetupManager(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val models: ModelStore,
    private val inference: InferenceManager,
) {
    enum class Step(val label: String) {
        CHECK("기기 확인"),
        DOWNLOAD("모델 다운로드"),
        VERIFY("무결성 검증 (SHA-256)"),
        LOAD("모델 로드"),
        TUNE("성능 자동 튜닝"),
        DONE("완료"),
    }

    data class State(
        val step: Step = Step.CHECK,
        val running: Boolean = false,
        val message: String = "",
        val variant: String = "",
        val variantReason: String = "",
        val progress: Float? = null,
        val bytesDone: Long = 0,
        val bytesTotal: Long = 0,
        val bytesPerSec: Double = 0.0,
        val waitingNetwork: Boolean = false,
        val meteredBlocked: Boolean = false,
        val error: String? = null,
    ) {
        val etaSec: Long get() = if (bytesPerSec > 1) ((bytesTotal - bytesDone) / bytesPerSec).toLong() else -1
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var job: Job? = null

    /** 멱등: 이미 진행 중이면 무시 */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runCatching { run() }.onFailure { e ->
            Log.e(TAG, "setup failed", e)
            _state.value = _state.value.copy(running = false, error = e.message ?: e.toString())
        } }
    }

    /** 모바일 데이터로 계속 받기 허용 후 재개 */
    fun allowMeteredAndContinue() {
        scope.launch {
            settings.update { it.copy(allowMeteredDownload = true) }
            models.allowMetered = true
            start()
        }
    }

    fun pause() {
        models.cancelDownload()
    }

    private fun update(f: (State) -> State) { _state.value = f(_state.value) }

    private suspend fun run() {
        update { State(step = Step.CHECK, running = true, message = "기기 확인 중") }
        val s0 = settings.current()
        models.allowMetered = s0.allowMeteredDownload

        // 이미 완료된 설치면 로드만 확인
        val configured = s0.modelFile.takeIf { it.isNotEmpty() }?.let { File(models.dir, it) }?.takeIf { it.exists() }
        if (s0.setupDone && configured != null) {
            update { it.copy(step = Step.DONE, running = false, message = "준비 완료") }
            return
        }

        val profile = inference.profile
        val complete = ModelCatalog.variants.filter { models.isComplete(it) }.map { it.id }.toSet()
        val partial = ModelCatalog.variants.associate { v ->
            v.id to File(models.fileFor(v).path + ".part").let { if (it.exists()) it.length() else 0L }
        }
        val plan = SetupPlanner.plan(profile.totalRamBytes, models.dir.usableSpace, complete, partial)
        val variant = when (plan) {
            is SetupPlanner.Plan.UseExisting -> plan.variant
            is SetupPlanner.Plan.Download -> plan.variant
            is SetupPlanner.Plan.NotEnoughStorage -> {
                update {
                    it.copy(running = false, error = "저장 공간 부족: ${plan.neededBytes / 1_000_000_000.0}GB 필요, " +
                        "여유 ${"%.1f".format(plan.freeBytes / 1e9)}GB. 공간 확보 후 다시 시도하세요.")
                }
                return
            }
        }
        val reason = when (plan) {
            is SetupPlanner.Plan.Download -> plan.reason
            else -> "이미 받은 파일 사용"
        }
        update { it.copy(variant = variant.id, variantReason = reason, bytesTotal = variant.sizeBytes) }

        // ---- 다운로드 + 검증 ----
        if (!models.isComplete(variant)) {
            update { it.copy(step = Step.DOWNLOAD, message = "${variant.fileName} 받는 중") }
            val watcher = scope.launch {
                models.download.collect { d ->
                    when (d) {
                        is ModelStore.DownloadState.Running -> update {
                            it.copy(step = Step.DOWNLOAD, bytesDone = d.done, bytesTotal = d.total, bytesPerSec = d.bytesPerSec,
                                progress = d.done.toFloat() / d.total, waitingNetwork = false, meteredBlocked = false,
                                message = "${variant.fileName} 받는 중")
                        }
                        is ModelStore.DownloadState.WaitingNetwork -> update {
                            it.copy(waitingNetwork = true, meteredBlocked = models.networkState() == ModelStore.NetState.METERED,
                                bytesDone = d.done, progress = d.done.toFloat() / d.total, message = d.reason)
                        }
                        is ModelStore.DownloadState.Retrying -> update { it.copy(message = "연결 끊김 — 재시도 ${d.attempt} (${d.reason})") }
                        is ModelStore.DownloadState.Verifying -> update {
                            it.copy(step = Step.VERIFY, progress = d.done.toFloat() / d.total, message = "SHA-256 검증 중")
                        }
                        else -> Unit
                    }
                }
            }
            val ok = models.downloadModel(variant)
            watcher.cancel()
            if (!ok) {
                val err = (models.download.value as? ModelStore.DownloadState.Failed)?.error ?: "다운로드 실패"
                update { it.copy(running = false, waitingNetwork = false, error = err) }
                return
            }
        }
        settings.update {
            it.copy(
                modelFile = variant.fileName,
                nCtx = if (it.setupDone) it.nCtx else SetupPlanner.defaultContext(profile.totalRamBytes),
                // 첫 설치 기본 백엔드 GPU (실패 시 ensureLoaded 가 CPU 로 복귀)
                backend = if (it.setupDone) it.backend else Backend.GPU,
            )
        }

        // ---- 로드 ----
        update { it.copy(step = Step.LOAD, progress = null, waitingNetwork = false, message = "모델을 메모리에 올리는 중") }
        val loader = scope.launch {
            inference.status.collect { st ->
                if (st is EngineStatus.Loading) update { it.copy(progress = st.progress) }
            }
        }
        val loaded = inference.lock.let { l -> l.lock(); try { inference.ensureLoaded() } finally { l.unlock() } }
        loader.cancel()
        if (!loaded) {
            update { it.copy(running = false, error = (inference.status.value as? EngineStatus.Error)?.message ?: "모델 로드 실패") }
            return
        }

        // ---- 자동 튜닝 (실패해도 기본값으로 진행) ----
        update { it.copy(step = Step.TUNE, progress = null, message = "성능 측정 중") }
        runCatching { inference.autoTune { label -> update { it.copy(message = "측정: $label") } } }
            .onFailure { Log.w(TAG, "autotune failed: ${it.message}") }

        settings.update { it.copy(setupDone = true) }
        update { it.copy(step = Step.DONE, running = false, progress = 1f, message = "준비 완료") }
    }

    companion object { private const val TAG = "SetupManager" }
}
