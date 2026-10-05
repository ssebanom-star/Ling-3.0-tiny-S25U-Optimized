package io.github.ssebanom.ling.engine

/** JNI 진입점. 직접 쓰지 말고 [LingEngine] 을 사용한다(스레드 규약 보장). */
internal object LingNative {
    init {
        System.loadLibrary("ling_jni")
    }

    @JvmStatic external fun nativeInit(backendDir: String)
    @JvmStatic external fun nativeSystemInfo(): String
    @JvmStatic external fun nativeDevices(): Array<String>
    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(h: Long)

    /** ip: nCtx,nBatch,nUbatch,nThreads,nThreadsBatch,poll,nGpuLayers,maxCheckpoints
     *  bp: strictCpu,useMmap,useMlock,flashAttn,kvQ8,weightRepack. 반환: 오류 메시지(null=성공) */
    @JvmStatic external fun nativeLoad(
        h: Long, path: String, ip: IntArray, bp: BooleanArray,
        cpumask: String, devices: String, progress: NativeCallbacks.Load?,
    ): String?

    @JvmStatic external fun nativeUnload(h: Long)
    @JvmStatic external fun nativeSetThreads(h: Long, n: Int, nBatch: Int, cpuMask: String)
    @JvmStatic external fun nativeTokenize(h: Long, text: String): IntArray
    @JvmStatic external fun nativeDetokenize(h: Long, tokens: IntArray): String

    /** 반환: ok,nPrompt,nReused,nPrefilled,restored,reset,prefillMs */
    @JvmStatic external fun nativeSync(
        h: Long, texts: Array<String?>, tokens: Array<IntArray?>, boundaries: BooleanArray,
        listener: NativeCallbacks.Prefill?,
    ): DoubleArray

    /** fp: temp,topP,minP,repeatPenalty / ip: maxTokens,topK,repeatLastN,seed */
    @JvmStatic external fun nativeGenerate(h: Long, fp: FloatArray, ip: IntArray, listener: NativeCallbacks.Token?): IntArray

    /** 반환: reason,decodeMs,ttftMs,nTokens */
    @JvmStatic external fun nativeLastGenerate(h: Long): DoubleArray
    @JvmStatic external fun nativeLastError(h: Long): String
    @JvmStatic external fun nativeCancel(h: Long)
    @JvmStatic external fun nativeCheckpointNow(h: Long): Boolean
    @JvmStatic external fun nativeClearCheckpoints(h: Long)

    /** 반환: checkpointCount,checkpointBytes,contextTokens,nCtx */
    @JvmStatic external fun nativeState(h: Long): LongArray
    @JvmStatic external fun nativeReset(h: Long)

    /** 반환: ppTps,tgTps,ppMs,tgMs */
    @JvmStatic external fun nativeBench(h: Long, nPrompt: Int, nGen: Int, reps: Int): DoubleArray

    /** 반환: [id0, logprob0, id1, logprob1, ...] */
    @JvmStatic external fun nativeEvalTopK(h: Long, tokens: IntArray, k: Int): FloatArray

    /** 반환: desc,sizeBytes,nParams,nVocab */
    @JvmStatic external fun nativeModelInfo(h: Long): Array<String>
}

/** 네이티브에서 호출하는 콜백(추론 스레드에서 동기 호출됨). */
interface NativeCallbacks {
    fun interface Load { fun onLoadProgress(progress: Float): Boolean }
    fun interface Prefill { fun onPrefillProgress(done: Int, total: Int) }
    fun interface Token { fun onPiece(piece: String, token: Int): Boolean }
}
