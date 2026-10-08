package io.github.ssebanom.ling.data

import io.github.ssebanom.ling.domain.ModelFamily

/**
 * 배포 GGUF 목록.
 * - Ling-3.0-tiny: bartowski/Ling-3.0-tiny-GGUF (llama.cpp b10472 imatrix 양자화), MIT
 * - LFM2.5-8B-A1B: LiquidAI/LFM2.5-8B-A1B-GGUF (공식), LFM Open License v1.0
 * - Qwen3.6-35B-A3B: bartowski/Qwen_Qwen3.6-35B-A3B-GGUF (imatrix IQ1_M), Apache-2.0 — 실험
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
    /**
     * 실험(RAM 보다 큰 모델): mmap 으로 저장장치에서 필요한 가중치만 읽는다. GPU/NPU 사용 시 routed expert 만
     * CPU(mmap)에 두고 나머지를 가속기에 올린다. CPU 가중치 재배열(repack)은 전체를 RAM 에 복사하므로 끈다.
     */
    val experimental: Boolean = false,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"
}

/** tools/gguf_budget.py 로 계산한 IQ1_M 토큰당 가중치 읽기량 */
private const val QWEN_IQ1M_DECODE_BYTES = 1_497_000_000L

/** tools/gguf_budget.py: Granite 4.0-H-Tiny 토큰당 가중치 읽기량(라우티드 expert 6/64) */
private const val GRANITE_Q40_DECODE_BYTES = 880_000_000L
private const val GRANITE_Q4KM_DECODE_BYTES = 913_000_000L
private const val GRANITE_Q5KM_DECODE_BYTES = 1_061_000_000L

object ModelCatalog {
    const val REPO = "bartowski/Ling-3.0-tiny-GGUF"
    const val LFM_REPO = "LiquidAI/LFM2.5-8B-A1B-GGUF"
    const val QWEN_REPO = "bartowski/Qwen_Qwen3.6-35B-A3B-GGUF"
    const val K2H_REPO = "IFM/K2-Horizon-3.7B-GGUF"
    const val GRANITE_REPO = "ibm-granite/granite-4.0-h-tiny-GGUF"

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
        // Qwen3.6-35B-A3B: 36B 총 / 3B 활성, Gated DeltaNet+어텐션 하이브리드. 1bit(IQ1_M)로도 12GB 폰 RAM 보다 큼
        ModelVariant(
            "QWEN-IQ1_M", "Qwen_Qwen3.6-35B-A3B-IQ1_M.gguf", 9_421_649_536L,
            "d291e12a0f693b5f14c11bb1278ba43b432b2b6f6c33c7fdaf750c214ff83f28", QWEN_IQ1M_DECODE_BYTES,
            "원본 가중치 IQ1_M(1.75bpw). 시험: 답·툴 호출 모두 정상 — Qwen 빌드 중 가장 양호. RAM 초과라 저장장치에서 읽으며 실행",
            family = ModelFamily.QWEN36, repo = QWEN_REPO, experimental = true,
        ),
        // K2-Horizon 3.7B (IFM, 공식 GGUF): dense 3.7B, 전층 어텐션. AA 지수 16. 한국어 약함(모델 카드 언어: en)
        ModelVariant(
            "K2H-Q4_K_M", "K2-Horizon-4B-Q4_K_M.gguf", 3_156_598_144L,
            "07773aed93890d4f5d08b421430000d5c8d73cefc74fbdc32e67d820f23207b2", 3_000_000_000L,
            "K2-Horizon 3.7B. 시험: 툴 호출 정상, 영어 답 정상, 한국어 설명은 사실 오류(영어 사용 권장). 컨텍스트 8K·KV q8 고정",
            recommended = true, family = ModelFamily.K2H, repo = K2H_REPO,
        ),
        ModelVariant(
            "K2H-Q5_K_M", "K2-Horizon-4B-Q5_K_M.gguf", 3_643_776_384L,
            "944ec9515178a0e138a8951cd53e23967b9d24ff0604e07ca590ac2003f14066", 3_500_000_000L,
            "K2-Horizon 3.7B 고품질(느림)", family = ModelFamily.K2H, repo = K2H_REPO,
        ),
        // Granite 4.0-H-Tiny (IBM 공식 GGUF): 6.9B 총 / ~1B 활성 MoE, Mamba2 36층 + 어텐션 4층 → KV 8KB/토큰. 한국어 공식 지원 12개 언어 중 하나
        ModelVariant(
            "GR-Q4_0", "granite-4.0-h-tiny-Q4_0.gguf", 3_962_938_208L,
            "85c1f5484c7974a06d33642779ed633b144d7cfa0b498a64e35d08a7116882ef", GRANITE_Q40_DECODE_BYTES,
            "Granite 기본. 긴 컨텍스트에도 메모리 거의 안 늘어남, 툴 호출 지원", recommended = true,
            family = ModelFamily.GRANITE, repo = GRANITE_REPO,
        ),
        ModelVariant(
            "GR-Q4_K_M", "granite-4.0-h-tiny-Q4_K_M.gguf", 4_230_976_352L,
            "5a38b08c441ae1adbafb1d2b8a7167e0d48734d83af68b268cefea1eec553dcd", GRANITE_Q4KM_DECODE_BYTES,
            "Granite 품질 우선(CPU)", family = ModelFamily.GRANITE, repo = GRANITE_REPO,
        ),
        ModelVariant(
            "GR-Q5_K_M", "granite-4.0-h-tiny-Q5_K_M.gguf", 4_948_534_112L,
            "28f5214cfc50b4b05340c50ad5c5b116a955b70c1c980c8283362f441b038bc1", GRANITE_Q5KM_DECODE_BYTES,
            "Granite 고품질", family = ModelFamily.GRANITE, repo = GRANITE_REPO,
        ),
        // ---- 같은 모델의 더 작은 빌드(호스트 greedy 시험 결과를 note 에 기록) ----
        ModelVariant(
            "QWEN-IQ1_S", "Qwen3.6-35B-A3B.i1-IQ1_S.gguf", 7_484_142_304L,
            "866e42dd53c834bdf2c0b05a92cd912c56c2990fbc56258fa9e12c9831778fbc", 1_108_000_000L,
            "원본 가중치 IQ1_S(1.56bpw). 시험: 일반 답 정상, 툴 호출 깨짐(형식 붕괴·반복)",
            family = ModelFamily.QWEN36, repo = "mradermacher/Qwen3.6-35B-A3B-i1-GGUF", experimental = true,
        ),
        ModelVariant(
            "QWEN-REAP25", "qwen3.6-35b-reap25_iq1m.gguf", 7_932_246_080L,
            "0f591699a08251582b34ba6ecc2aab1a23d2dc03b089206b81683e088e7c2d1c", 1_500_000_000L,
            "사용자 빌드: expert 25% 가지치기(256→192)+IQ1_M. 시험: 상식 오답, 툴 호출 태그 일부 누락(앱이 보정)",
            family = ModelFamily.QWEN36, repo = "dxx117/Qwen3.6-35B-REAP-IQ1M", experimental = true,
        ),
        ModelVariant(
            "QWEN-REAP40", "qwen3.6-35b-reap40-iq1m.gguf", 6_678_657_120L,
            "a5174823ab6bc0c03373762bb7fa524ef4c426581be4e6df45c74422c2a2478a", 1_500_000_000L,
            "사용자 빌드: expert 40% 가지치기(→154)+IQ1_M. 시험: 상식 오답(서울→Tokyo), 툴 호출 형식 정상",
            family = ModelFamily.QWEN36, repo = "dxx117/Qwen3.6-35B-REAP-IQ1M", experimental = true,
        ),
        ModelVariant(
            "QWEN-REAP50", "qwen3.6-35b-reap50-iq1m.gguf", 5_820_938_368L,
            "4c38e3678d8e30c073696059d914f2b44d0ca8bebffef13c2b05c14cf096152f", 1_500_000_000L,
            "사용자 빌드: expert 50% 가지치기(→128)+IQ1_M. 시험: 상식 오답, 툴 호출 형식 정상(인자에 중국어 섞임)",
            family = ModelFamily.QWEN36, repo = "dxx117/Qwen3.6-35B-REAP-IQ1M", experimental = true,
        ),
        ModelVariant(
            "QWEN28-IQ1_S", "Qwen3.6-28B-REAP.i1-IQ1_S.gguf", 6_192_747_488L,
            "b01d1d0188bf39a79842bb6ef9e6ed629cc3a1f4e5b8b99c71d6f395ef9ee43f", 1_091_000_000L,
            "사용자 빌드: 0xSero 28B REAP(→205 expert)+IQ1_S. 시험: 일반 답 정상, 툴 호출 깨짐(반복)",
            family = ModelFamily.QWEN36, repo = "mradermacher/Qwen3.6-28B-REAP-i1-GGUF", experimental = true,
        ),
    )

    fun byId(id: String): ModelVariant? = variants.firstOrNull { it.id == id }
    fun byFile(name: String): ModelVariant? = variants.firstOrNull { it.fileName == name }
}
