package io.github.ssebanom.ling.engine

/** 추론 실행 설정. 기본값은 docs/02-design.md §4.2 */
data class EngineConfig(
    val modelPath: String,
    val nCtx: Int = 16384,
    val nBatch: Int = 512,
    val nUbatch: Int = 512,
    val nThreads: Int = 4,
    val nThreadsBatch: Int = 6,
    /** 쉼표 구분 CPU 번호. 빈 문자열 = OS 기본 */
    val cpuMask: String = "",
    val strictCpu: Boolean = false,
    val poll: Int = 50,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val flashAttn: Boolean = true,
    val kvQ8: Boolean = false,
    val weightRepack: Boolean = true,
    /** 오프로드 디바이스(예: "HTP0,HTP1", "GPUOpenCL"). 빈 문자열 = CPU 전용 */
    val devices: String = "",
    val nGpuLayers: Int = 0,
    val maxCheckpoints: Int = 8,
)

/** 샘플링 설정. 기본값 = 모델 카드 권장값(temp 1.0 / top_p 0.95 / top_k 20) */
data class SamplerConfig(
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 20,
    val minP: Float = 0.0f,
    val repeatPenalty: Float = 1.0f,
    /** 0 = 끔. 등장한 토큰에 고정 감점(Qwen3.6 권장 1.5) */
    val presencePenalty: Float = 0.0f,
    val repeatLastN: Int = 64,
    val seed: Int = -1,
    val maxTokens: Int = 4096,
)

/** 프롬프트 조각: 텍스트 또는 이미 알고 있는 토큰열. [boundaryAfter] = 턴 경계(체크포인트 후보) */
data class PromptSegment(
    val text: String? = null,
    val tokens: IntArray? = null,
    val boundaryAfter: Boolean = false,
) {
    init {
        require((text == null) != (tokens == null)) { "text 또는 tokens 중 하나만 지정" }
    }
    override fun equals(other: Any?): Boolean =
        other is PromptSegment && text == other.text && boundaryAfter == other.boundaryAfter &&
            (tokens?.contentEquals(other.tokens) ?: (other.tokens == null))
    override fun hashCode(): Int = (text?.hashCode() ?: tokens!!.contentHashCode()) * 31 + boundaryAfter.hashCode()
}

data class SyncStats(
    val nPrompt: Int,
    val nReused: Int,
    val nPrefilled: Int,
    val restoredCheckpoint: Boolean,
    val fullReset: Boolean,
    val prefillMs: Double,
) {
    val prefillTps: Double get() = if (prefillMs > 0) nPrefilled * 1000.0 / prefillMs else 0.0
}

enum class StopReason { EOG, MAX_TOKENS, CANCELLED, CONTEXT_FULL, ERROR }

data class GenerateStats(
    val reason: StopReason,
    val tokens: IntArray,
    val text: String,
    val decodeMs: Double,
    val ttftMs: Double,
) {
    val decodeTps: Double get() = if (decodeMs > 0) tokens.size * 1000.0 / decodeMs else 0.0
}

data class BenchStats(val nPrompt: Int, val nGen: Int, val ppTps: Double, val tgTps: Double)

data class EngineState(val checkpoints: Int, val checkpointBytes: Long, val contextTokens: Int, val nCtx: Int)

data class ModelInfo(val desc: String, val sizeBytes: Long, val nParams: Long, val nVocab: Int)

data class DeviceInfo(val name: String, val type: String, val description: String, val memoryMiB: Long)

class EngineException(message: String) : RuntimeException(message)
