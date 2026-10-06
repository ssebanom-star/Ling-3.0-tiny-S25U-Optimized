package io.github.ssebanom.ling.runtime

/**
 * Adreno OpenCL 정확성 우회 구성. 네이티브 측 스위치는 patches/llama-opencl-runtime-switches.patch 참고.
 *
 * - [opFilter]: 정규식(ggml_op_desc 기준)에 맞는 op 를 GPU 가 맡지 않고 CPU 로 보낸다
 * - [noFusion]: op 융합 끔
 * - [noAdrenoGemm]: Adreno 전용 dense 가중치 재배열/GEMM 대신 일반 커널
 * - [noAdrenoMoe]: MoE 가중치 재배열(*_trans4_ns) 끔
 *
 * 모두 GPU 모델 로드 전에 환경변수로 적용한다(가중치 배치가 로드 시점에 결정되므로 바꾸면 재로드 필요).
 */
data class GpuQuirks(
    val opFilter: String = "",
    val noFusion: Boolean = false,
    val noAdrenoGemm: Boolean = false,
    val noAdrenoMoe: Boolean = false,
) {
    val isDefault: Boolean get() = this == GpuQuirks()

    fun encode(): String = listOf(opFilter, flag(noFusion), flag(noAdrenoGemm), flag(noAdrenoMoe)).joinToString(SEP)

    /** 설정할 환경변수(null = 제거) */
    fun env(): Map<String, String?> = mapOf(
        "GGML_OPENCL_OPFILTER" to opFilter.ifEmpty { null },
        "GGML_OPENCL_DISABLE_FUSION" to if (noFusion) "1" else null,
        "LING_OPENCL_NO_ADRENO_GEMM" to if (noAdrenoGemm) "1" else null,
        "LING_OPENCL_NO_ADRENO_MOE" to if (noAdrenoMoe) "1" else null,
    )

    val label: String
        get() = if (isDefault) "기본" else buildList {
            if (opFilter.isNotEmpty()) add("CPU로: $opFilter")
            if (noFusion) add("융합 끔")
            if (noAdrenoGemm) add("Adreno GEMM 끔")
            if (noAdrenoMoe) add("MoE 재배열 끔")
        }.joinToString(", ")

    operator fun plus(o: GpuQuirks) = GpuQuirks(
        opFilter = listOf(opFilter, o.opFilter).filter { it.isNotEmpty() }.joinToString("|"),
        noFusion = noFusion || o.noFusion,
        noAdrenoGemm = noAdrenoGemm || o.noAdrenoGemm,
        noAdrenoMoe = noAdrenoMoe || o.noAdrenoMoe,
    )

    companion object {
        private const val SEP = ";"
        private fun flag(b: Boolean) = if (b) "1" else "0"

        fun decode(s: String): GpuQuirks {
            if (s.isBlank()) return GpuQuirks()
            val p = s.split(SEP)
            return GpuQuirks(
                opFilter = p.getOrElse(0) { "" },
                noFusion = p.getOrNull(1) == "1",
                noAdrenoGemm = p.getOrNull(2) == "1",
                noAdrenoMoe = p.getOrNull(3) == "1",
            )
        }

        // bailingmoe3 에서 시퀀스 혼합을 담당하는 op 묶음 (증상: 문장은 유창한데 입력 문맥을 무시)
        private val RECURRENT = GpuQuirks(opFilter = "GATED_DELTA_NET|SSM_CONV|L2_NORM")
        private val ATTENTION = GpuQuirks(opFilter = "FLASH_ATTN_EXT|SOFT_MAX|ROPE")
        private val KERNELS = GpuQuirks(noFusion = true, noAdrenoGemm = true, noAdrenoMoe = true)

        /**
         * 정확성 검사 실패 시 시도 순서: 성능 손실이 작은 것 → 큰 것.
         * 첫 통과 구성을 저장한다. 마지막까지 실패하면 GPU 사용 불가.
         */
        val CANDIDATES: List<GpuQuirks> = listOf(
            GpuQuirks(),
            GpuQuirks(noFusion = true),
            GpuQuirks(opFilter = "GATED_DELTA_NET"),
            GpuQuirks(noAdrenoMoe = true),
            RECURRENT,
            ATTENTION,
            GpuQuirks(noAdrenoGemm = true),
            RECURRENT + ATTENTION,
            KERNELS,
            RECURRENT + ATTENTION + KERNELS,
        )
    }
}
