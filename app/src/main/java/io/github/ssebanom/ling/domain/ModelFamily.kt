package io.github.ssebanom.ling.domain

import io.github.ssebanom.ling.engine.PromptSegment

/** 모델 카드 권장 샘플링 */
data class RecommendedSampling(
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float = 0f,
    val repeatPenalty: Float = 1f,
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
    };

    abstract fun segments(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean): List<PromptSegment>
    abstract fun render(messages: List<ChatMessage>, tools: List<Map<String, Any?>>, thinking: Boolean, addGenerationPrompt: Boolean = true): String
    abstract fun parseToolCalls(content: String): ToolCallParser.Result
    /** 스트리밍 중 보여줄 본문(툴 호출 마크업 제외) */
    abstract fun visibleContent(content: String): String
    abstract fun startInThink(thinking: Boolean): Boolean
    /** 공통 function 스키마({name, description, parameters}) → 이 계열 템플릿이 기대하는 형태 */
    abstract fun toolSpec(fn: Map<String, Any?>): Map<String, Any?>

    /**
     * 가속기 정확성 검사용 고정 프롬프트. 다음 토큰 분포가 정보량이 있도록 끝을 맞춘다
     * (LFM2.5 는 첫 토큰이 거의 확정적으로 `<think>` 이므로 추론 문장 중간까지 넣는다).
     */
    fun probePrompt(): String = render(
        listOf(ChatMessage(Role.USER, "대한민국의 수도는 어디인가요? Then compute 17*23 and explain briefly.")),
        emptyList(), thinking = false,
    ) + if (this == LFM2) "<think>\nThe user asks about the capital of South Korea and 17*23. The capital is" else ""

    companion object {
        /** GGUF 파일명/모델 설명으로 계열 추정 */
        fun detect(fileName: String, modelDesc: String = ""): ModelFamily {
            val s = (fileName + " " + modelDesc).lowercase()
            return if (s.contains("lfm")) LFM2 else LING
        }
    }
}
