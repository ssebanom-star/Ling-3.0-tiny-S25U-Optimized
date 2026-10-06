package io.github.ssebanom.ling.tools

import io.github.ssebanom.ling.service.LingAccessibilityService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 부수효과 툴 실행 전 사용자 승인.
 * - 앱 화면이 보이면 Compose 다이얼로그([pending] 구독)
 * - 다른 앱 위에서 동작 중이면 접근성 오버레이
 * - 둘 다 불가하거나 2분 무응답이면 거부
 * "이 대화 동안 항상 허용"은 툴 이름 단위로 프로세스 수명 동안 기억한다.
 */
class ToolApprovals {
    enum class Decision { ALLOW, DENY, ALWAYS }

    class Request(val tool: String, val summary: String) {
        internal val result = CompletableDeferred<Decision>()
    }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending

    /** MainActivity 가 onStart/onStop 에서 갱신 */
    @Volatile var uiVisible: Boolean = false

    private val always = java.util.Collections.synchronizedSet(HashSet<String>())

    fun resetAlways() = always.clear()

    suspend fun ask(tool: String, summary: String): Boolean {
        if (tool in always) return true
        if (uiVisible) {
            val r = Request(tool, summary)
            _pending.value = r
            val d = try {
                withTimeoutOrNull(TIMEOUT_MS) { r.result.await() } ?: Decision.DENY
            } finally {
                _pending.compareAndSet(r, null)
            }
            if (d == Decision.ALWAYS) always += tool
            return d != Decision.DENY
        }
        val svc = LingAccessibilityService.instance ?: return false
        return withTimeoutOrNull(TIMEOUT_MS) { svc.confirmOverlay("Ling: 이 동작을 실행할까요?", summary) } ?: false
    }

    fun respond(r: Request, d: Decision) {
        r.result.complete(d)
    }

    companion object {
        const val TIMEOUT_MS = 120_000L
    }
}
