package io.github.ssebanom.ling.data

import io.github.ssebanom.ling.domain.ModelFamily

/**
 * 배포 GGUF 목록.
 * - Ling-3.0-tiny: bartowski/Ling-3.0-tiny-GGUF (llama.cpp b10472 imatrix 양자화), MIT
 * - LFM2.5-8B-A1B: LiquidAI/LFM2.5-8B-A1B-GGUF (공식), LFM Open License v1.0
 * 크기/SHA-256 은 HF API(LFS oid) 기준. 토큰당 읽기량은 tools/gguf_budget.py 계산값.
 */
data class ModelVariant(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val decodeBytesPerToken: Long,
    val note: String,
    val recommended: Boolean = false,
    val family: ModelFamily = ModelFamily.LING,
    val repo: String = ModelCatalog.REPO,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"
}

object ModelCatalog {
    const val REPO = "bartowski/Ling-3.0-tiny-GGUF"
    const val LFM_REPO = "LiquidAI/LFM2.5-8B-A1B-GGUF"

    val variants = listOf(
        ModelVariant(
            "Q4_0", "Ling-3.0-tiny-Q4_0.gguf", 4_623_917_248L,
            "c1a548fd60cfb5a46d6e5fd7635224dec473664c9320a656132106bf7ce23cfb", 807_000_000L,
            "기본. CPU repack·Adreno·Hexagon 고속 경로 모두 호환", recommended = true,
        ),
        ModelVariant(
            "Q4_K_M", "Ling-3.0-tiny-Q4_K_M.gguf", 4_917_354_688L,
            "5eb12d8150f502e5ef15c05f0a03aec72ef8b8319fbaa8b2e03b43754ce9f2c5", 845_000_000L,
            "품질 우선(CPU). GPU MoE 는 Adreno 전용 커널 조건부",
        ),
        ModelVariant(
            "IQ4_XS", "Ling-3.0-tiny-IQ4_XS.gguf", 4_385_795_776L,
            "bce9e797ece66e7e8a082fe04aefd05c20b35445b785e13f7cc3355b98a5b9d0", 792_000_000L,
            "저메모리. CPU 전용 권장",
        ),
        ModelVariant(
            "Q3_K_XL", "Ling-3.0-tiny-Q3_K_XL.gguf", 4_125_490_240L,
            "bef70268644e6e1b757eb032779e98c4d45159462d93ce90d0e66d1baab2eb32", 760_000_000L,
            "메모리 부족 시 최후 수단(품질 저하)",
        ),
        ModelVariant(
            "Q5_K_M", "Ling-3.0-tiny-Q5_K_M.gguf", 5_716_431_040L,
            "7625ce18c72b29e616fffa9fdc49e1bc73f2f2ad1784704171cdba7c5c9a149c", 930_000_000L,
            "고품질. 16GB 모델 권장(12GB 는 LMK 위험)",
        ),
        // LFM2.5-8B-A1B: 8.3B 총 / 1.5B 활성, conv+GQA 하이브리드. 출력층이 임베딩과 묶여 토큰당 읽기량이 Ling 보다 큼
        ModelVariant(
            "LFM-Q4_0", "LFM2.5-8B-A1B-Q4_0.gguf", 4_844_678_368L,
            "48ed1465d761311b2fd57b7fb46cf969a20b3a8281945b04e10f52bd1609e715", 1_021_000_000L,
            "LFM2.5 기본. 툴 호출·지시 수행 특화, 항상 추론", recommended = true,
            family = ModelFamily.LFM2, repo = LFM_REPO,
        ),
        ModelVariant(
            "LFM-Q4_K_M", "LFM2.5-8B-A1B-Q4_K_M.gguf", 5_155_564_768L,
            "4923ec14f06b968b74d663e5949867d2d9c3bf13a20b8be1a9f9af39989b2bb0", 1_080_000_000L,
            "LFM2.5 품질 우선(CPU)", family = ModelFamily.LFM2, repo = LFM_REPO,
        ),
        ModelVariant(
            "LFM-Q5_K_M", "LFM2.5-8B-A1B-Q5_K_M.gguf", 6_030_339_296L,
            "eb8bd10148ea21e195502d6d6a205983ba40815302068ce72eb160684df92b3e", 1_250_000_000L,
            "LFM2.5 고품질. 12GB 는 LMK 위험", family = ModelFamily.LFM2, repo = LFM_REPO,
        ),
    )

    fun byId(id: String): ModelVariant? = variants.firstOrNull { it.id == id }
    fun byFile(name: String): ModelVariant? = variants.firstOrNull { it.fileName == name }
}
