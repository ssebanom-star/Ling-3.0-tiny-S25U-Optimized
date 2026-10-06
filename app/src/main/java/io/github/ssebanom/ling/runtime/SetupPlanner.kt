package io.github.ssebanom.ling.runtime

import io.github.ssebanom.ling.data.ModelCatalog
import io.github.ssebanom.ling.data.ModelVariant

/**
 * 첫 실행 자동 설치 계획(순수 함수 — 단위 테스트 대상).
 * 우선순위: 이미 받은 모델 > 받다 만 모델 이어받기 > RAM 기준 기본 선택. 저장공간이 모자라면 더 작은 양자화로.
 */
object SetupPlanner {
    private const val GiB = 1024L * 1024 * 1024
    /** 다운로드 후에도 남겨둘 여유 공간 */
    const val STORAGE_MARGIN = 300L * 1024 * 1024

    /** 이미 받은 파일이 여러 개면 이 순서로 사용 */
    private val usePreference = listOf("Q4_0", "Q4_K_M", "IQ4_XS", "Q5_K_M", "Q3_K_XL")

    sealed interface Plan {
        data class UseExisting(val variant: ModelVariant) : Plan
        data class Download(val variant: ModelVariant, val resumeFrom: Long, val reason: String) : Plan
        data class NotEnoughStorage(val neededBytes: Long, val freeBytes: Long) : Plan
    }

    fun plan(
        totalRamBytes: Long,
        freeStorageBytes: Long,
        completeIds: Set<String>,
        partialBytes: Map<String, Long>,
    ): Plan {
        usePreference.firstOrNull { it in completeIds }?.let { return Plan.UseExisting(ModelCatalog.byId(it)!!) }

        // 받다 만 파일이 있으면 그걸 이어받는다(가장 많이 받은 것)
        partialBytes.filterValues { it > 0 }.maxByOrNull { it.value }?.let { (id, have) ->
            val v = ModelCatalog.byId(id)
            if (v != null && v.sizeBytes - have + STORAGE_MARGIN <= freeStorageBytes) {
                return Plan.Download(v, have, "이전 다운로드 이어받기")
            }
        }

        // 12GB 기기(S25U, OS 보고값 ≈ 11.x GiB)는 Q4_0, 그 미만은 IQ4_XS
        val preferred = if (totalRamBytes >= 10 * GiB) listOf("Q4_0", "IQ4_XS", "Q3_K_XL") else listOf("IQ4_XS", "Q3_K_XL")
        for (id in preferred) {
            val v = ModelCatalog.byId(id)!!
            if (v.sizeBytes + STORAGE_MARGIN <= freeStorageBytes) {
                val why = when {
                    id == preferred.first() && id == "Q4_0" -> "권장 기본값 (CPU·GPU·NPU 고속 경로 호환)"
                    id == preferred.first() -> "RAM ${totalRamBytes / GiB}GiB 기기용 저메모리 양자화"
                    else -> "저장공간 부족으로 더 작은 양자화 선택"
                }
                return Plan.Download(v, 0, why)
            }
        }
        val smallest = ModelCatalog.byId(preferred.last())!!
        return Plan.NotEnoughStorage(smallest.sizeBytes + STORAGE_MARGIN, freeStorageBytes)
    }

    /** RAM 기준 기본 컨텍스트 */
    fun defaultContext(totalRamBytes: Long): Int = if (totalRamBytes >= 10 * GiB) 16384 else 8192
}
