package io.github.ssebanom.ling.domain

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

/** 모델이 낸 툴 호출. 인자 값은 모델 출력 문자열 그대로 보관(템플릿 재렌더링 시 바이트 일치). */
data class ToolCall(val name: String, val arguments: LinkedHashMap<String, Any?>)

data class ChatMessage(
    val role: Role,
    val content: String,
    /** assistant 추론(<think> 내부) */
    val reasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    /**
     * 생성 프롬프트 뒤에 모델이 실제로 생성한 토큰열(EOG 제외).
     * 있으면 다음 턴 프롬프트에서 텍스트 대신 이 토큰을 그대로 넣어 KV/재귀 상태 캐시가 100% 재사용된다.
     */
    val rawTokens: IntArray? = null,
    /** 이 응답을 생성할 때 thinking 모드였는지(생성 프롬프트 접미사 재현용) */
    val thinkingAtGeneration: Boolean? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ChatMessage && role == other.role && content == other.content && reasoning == other.reasoning &&
            toolCalls == other.toolCalls && thinkingAtGeneration == other.thinkingAtGeneration &&
            (rawTokens?.contentEquals(other.rawTokens) ?: (other.rawTokens == null))

    override fun hashCode(): Int =
        listOf(role, content, reasoning, toolCalls, thinkingAtGeneration).hashCode() * 31 + (rawTokens?.contentHashCode() ?: 0)
}
