package io.github.ssebanom.ling.runtime

import android.content.Context
import android.os.PerformanceHintManager
import android.util.Log

/**
 * ADPF(Android Dynamic Performance Framework) 힌트 세션.
 * 추론 스레드 + ggml 워커 스레드를 등록하고, 토큰당 목표 시간 대비 실제 시간을 보고해
 * 스케줄러가 DVFS/코어 배치를 맞추도록 돕는다. 효과는 기기 실측 필요(문서 §4.4).
 */
class PerfHints(context: Context) {
    private val mgr = context.getSystemService(PerformanceHintManager::class.java)
    private var session: PerformanceHintManager.Session? = null

    fun start(tids: Collection<Int>, targetNanos: Long) {
        stop()
        val ids = tids.filter { it > 0 }.distinct().toIntArray()
        if (mgr == null || ids.isEmpty()) return
        session = runCatching { mgr.createHintSession(ids, targetNanos) }
            .onFailure { Log.w(TAG, "createHintSession failed: ${it.message}") }
            .getOrNull()
    }

    fun report(actualNanos: Long) {
        session?.let { runCatching { it.reportActualWorkDuration(actualNanos) } }
    }

    fun updateTarget(targetNanos: Long) {
        session?.let { runCatching { it.updateTargetWorkDuration(targetNanos) } }
    }

    fun stop() {
        session?.close()
        session = null
    }

    val active: Boolean get() = session != null

    companion object { private const val TAG = "PerfHints" }
}
