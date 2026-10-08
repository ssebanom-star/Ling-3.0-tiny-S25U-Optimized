package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/** 모델 카드 권장 샘플링 */
data class RecommendedSampling(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float = 0f,
    val repeatPenalty: Float = 1f,
    val presencePenalty: Float = 0f,
)

/**
 * 모델 계열별 채팅 포맷(템플릿·툴 호출·추론 태그·권장 샘플링).
 * ChatController 와 정확성 검사가 이 인터페이스만 쓴다.
 */
enum class ModelFamily(
    val label: String,
    /** thinking on/off 를 대화 단위로 바꿀 수 있는지(LFM2.5 는 항상 추론) */
    val thinkingToggle: Boolean,
    val sampling: RecommendedSampling,
) {
    LING("Ling-3.0-tiny", thinkingToggle = true, RecommendedSampling(1.0f, 0.95f, 20)) {
        override fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean) =
            LingPromptBuilder.segments(messages, tools, thinking)

        override fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean) =
            LingPromptBuilder.render(messages, tools, thinking, addGenerationPrompt)

        override fun parseToolCalls(content: String) = ToolCallParser.parse(content)
        override fun visibleContent(content: String) = ToolCallParser.visiblePrefix(content)
        override fun startInThink(thinking: Boolean) = thinking
        override fun toolSpec(fn: Map<String, Any?>) = linkedMapOf<String, Any?>("type" to "function", "function" to fn)
    },

    LFM2("LFM2.5-8B-A1B", thinkingToggle = false, RecommendedSampling(0.2f, 1.0f, 80, repeatPenalty = 1.05f)) {
        override fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean) =
            LfmPromptBuilder.segments(messages, tools)

        override fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean) =
            LfmPromptBuilder.render(messages, tools, preserveThinking = true, addGenerationPrompt = addGenerationPrompt)

        override fun parseToolCalls(content: String) = PythonicToolCallParser.parse(content)
        override fun visibleContent(content: String) = PythonicToolCallParser.visiblePrefix(content)
        // 생성 프롬프트가 "<|im_start|>assistant\n" 로 끝나고 모델이 스스로 <think> 를 연다
        override fun startInThink(thinking: Boolean) = false
        override fun toolSpec(fn: Map<String, Any?>) = fn
    },

    /** Qwen3.6-35B-A3B (실험: 1bit 양자화, CPU·mmap 전용) */
    QWEN36("Qwen3.6-35B-A3B (실험)", thinkingToggle = true, RecommendedSampling(1.0f, 0.95f, 20, presencePenalty = 1.5f)) {
        override fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean) =
            QwenPromptBuilder.segments(messages, tools, thinking)

        override fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean) =
            QwenPromptBuilder.render(messages, tools, thinking, preserveThinking = true, addGenerationPrompt = addGenerationPrompt)

        override fun parseToolCalls(content: String) = QwenToolCallParser.parse(content)
        override fun visibleContent(content: String) = QwenToolCallParser.visiblePrefix(content)
        override fun startInThink(thinking: Boolean) = thinking
        override fun toolSpec(fn: Map<String, Any?>) = linkedMapOf<String, Any?>("type" to "function", "function" to fn)

        // 모델 카드: thinking 일반 1.0/0.95/20, non-thinking 0.7/0.8/20, 둘 다 presence_penalty 1.5
        override fun samplingFor(thinking: Boolean) =
            if (thinking) sampling else RecommendedSampling(0.7f, 0.8f, 20, presencePenalty = 1.5f)
    },

    /** K2-Horizon 3.7B (IFM). 추론 끄기 없음: Thinking=high, Instant=low(think_faster). 권장 temp 1.0, top_p 0.95 */
    K2H("K2-Horizon 3.7B", thinkingToggle = true, RecommendedSampling(1.0f, 0.95f, 0)) {
        override fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean) =
            K2hPromptBuilder.segments(messages, tools, thinking)

        override fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean) =
            K2hPromptBuilder.render(messages, tools, thinking, addGenerationPrompt)

        override fun parseToolCalls(content: String) = K2hToolCallParser.parse(content)
        override fun visibleContent(content: String) = K2hToolCallParser.visiblePrefix(content)
        override fun startInThink(thinking: Boolean) = true // 생성 프롬프트가 추론 태그를 연 상태로 끝남
        override fun toolSpec(fn: Map<String, Any?>) = linkedMapOf<String, Any?>("type" to "function", "function" to fn)
        override fun thinkTags(thinking: Boolean) = K2hPromptBuilder.thinkTag(thinking).let { "<$it>" to "</$it>" }

        // 전층 어텐션(36층×KV 8헤드×128) → f16 KV 144KB/토큰. 32K 면 4.5GB 라 12GB 폰에선 8K·q8 로 제한
        override val ctxCap = 8192
        override val kvQ8Default = true
    },

    /** IBM Granite 4.0-H-Tiny (7B MoE·1B 활성, Mamba2+어텐션 혼합). 추론 단계 없음. IBM 권장: temperature 0(그리디), top_p 1, top_k 0 */
    GRANITE("Granite 4.0-H-Tiny", thinkingToggle = false, RecommendedSampling(0.0f, 1.0f, 0)) {
        override fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean) =
            GranitePromptBuilder.segments(messages, tools)

        override fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean) =
            GranitePromptBuilder.render(messages, tools, addGenerationPrompt)

        override fun parseToolCalls(content: String) = GraniteToolCallParser.parse(content)
        override fun visibleContent(content: String) = GraniteToolCallParser.visiblePrefix(content)
        override fun startInThink(thinking: Boolean) = false
        override fun toolSpec(fn: Map<String, Any?>) = linkedMapOf<String, Any?>("type" to "function", "function" to fn)
        override fun toolGrammar(toolNames: List<String>) = GraniteToolGrammar.build(toolNames) to GraniteToolGrammar.TRIGGER
    };

    abstract fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean): List<PromptSegment>
    abstract fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean = true): String
    abstract fun parseToolCalls(content: String): ToolCallParser.Result
    /** 스트리밍 중 보여줄 본문(툴 호출 마크업 제외) */
    abstract fun visibleContent(content: String): String
    abstract fun startInThink(thinking: Boolean): Boolean
    /** 공통 function 스키마({name, description, parameters}) → 이 계열 템플릿이 기대하는 형태 */
    abstract fun toolSpec(fn: Map<String, Any?>): Map<String, Any?>

    /** 이 계열에서 허용할 최대 컨텍스트(KV 메모리 보호) */
    open val ctxCap: Int = Int.MAX_VALUE

    /** KV 캐시를 기본으로 q8_0 으로 둘지 */
    open val kvQ8Default: Boolean = false

    /** 추론 구간 여닫는 태그 */
    open fun thinkTags(thinking: Boolean): Pair<String, String> = ThinkParser.OPEN to ThinkParser.CLOSE

    /** 툴 호출 구간 제약 문법(GBNF, lazy 트리거 정규식). null = 제약 없음 */
    open fun toolGrammar(toolNames: List<String>): Pair<String, String>? = null

    /** 모드별 권장 샘플링(기본은 [sampling]) */
    open fun samplingFor(thinking: Boolean): RecommendedSampling = sampling

    /**
     * 가속기 정확성 검사용 고정 프롬프트. 다음 토큰 분포가 정보량이 있도록 끝을 맞춘다
     * (LFM2.5 는 첫 토큰이 거의 확정적으로 `<think>` 이므로 추론 문장 중간까지 넣는다).
     */
    fun probePrompt(): String = render(
        listOf(ChatMessage(Role.USER, "대한민국의 수도는 어디인가요? Then compute 17*23 and explain briefly.")),
        emptyList(), thinking = false,
    ) + when (this) {
        LFM2 -> "<think>\nThe user asks about the capital of South Korea and 17*23. The capital is"
        // 생성 프롬프트가 추론 태그를 연 채 끝나 첫 토큰이 닫는 태그로 거의 확정 → 답 문장 중간까지
        K2H -> "</ifm|think_faster>The capital of South Korea is"
        else -> ""
    }

    companion object {
        /** GGUF 파일명/모델 설명으로 계열 추정 */
        fun detect(fileName: String, modelDesc: String = ""): ModelFamily {
            val s = (fileName + " " + modelDesc).lowercase()
            return when {
                s.contains("lfm") -> LFM2
                s.contains("qwen") -> QWEN36
                s.contains("k2-horizon") || s.contains("k2_horizon") || s.contains("k2horizon") -> K2H
                s.contains("granite") -> GRANITE
                else -> LING
            }
        }
    }
}
