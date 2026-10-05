package io.github.ssebanom.ling.runtime

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock

/**
 * 열 관리: PowerManager 열 여유(headroom)·상태에 따라 생성 속도를 조절한다.
 *  - headroom < 0.75 : 제한 없음
 *  - 0.75–0.9       : 다음 턴부터 디코드 스레드 1단계 감소(recommendedThreads)
 *  - ≥ 0.9 / SEVERE : 토큰 사이 지연 삽입으로 목표 tok/s 상한 적용
 * headroom 조회는 시스템이 1초 이상 간격을 요구하므로 캐시한다.
 */
class ThermalGovernor(context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)
    private var lastQueryMs = 0L
    private var lastHeadroom = Float.NaN

    enum class Level { NORMAL, WARM, HOT, CRITICAL }

    data class Snapshot(val headroom: Float, val status: Int, val level: Level)

    fun snapshot(): Snapshot {
        val now = SystemClock.elapsedRealtime()
        if (now - lastQueryMs >= 2_000 || lastHeadroom.isNaN()) {
            lastHeadroom = runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
            lastQueryMs = now
        }
        val status = pm.currentThermalStatus
        val level = when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> Level.CRITICAL
            status >= PowerManager.THERMAL_STATUS_SEVERE || (!lastHeadroom.isNaN() && lastHeadroom >= 0.9f) -> Level.HOT
            status >= PowerManager.THERMAL_STATUS_MODERATE || (!lastHeadroom.isNaN() && lastHeadroom >= 0.75f) -> Level.WARM
            else -> Level.NORMAL
        }
        return Snapshot(lastHeadroom, status, level)
    }

    /** 토큰 사이에 넣을 지연(ms). 생성 루프(추론 스레드)에서 호출 */
    fun interTokenDelayMs(level: Level): Long = when (level) {
        Level.NORMAL, Level.WARM -> 0
        Level.HOT -> 25      // ≈ 최대 30 tok/s 안팎으로 제한
        Level.CRITICAL -> 120
    }

    /** 다음 턴 디코드 스레드 수 */
    fun recommendedThreads(base: Int, level: Level): Int = when (level) {
        Level.NORMAL -> base
        Level.WARM -> (base - 1).coerceAtLeast(2)
        Level.HOT, Level.CRITICAL -> (base - 2).coerceAtLeast(2)
    }

    val isPowerSave: Boolean get() = pm.isPowerSaveMode
}
