package io.github.ssebanom.ling.ui.chat

import io.github.ssebanom.ling.data.StoredMessage
import io.github.ssebanom.ling.domain.Role
import io.github.ssebanom.ling.domain.ToolCall

/** 화면 단위: 사용자 말풍선 1개 또는 (assistant/툴 결과 묶음) 응답 1개 */
sealed interface ChatRow {
    val key: String

    data class User(override val key: String, val index: Int, val msg: StoredMessage) : ChatRow

    data class Turn(override val key: String, val entries: List<StoredMessage>, val live: Boolean, val isLast: Boolean) : ChatRow
}

/** 툴 호출과 그 결과 짝 */
data class ToolPair(val call: ToolCall, val result: String?)

/** assistant 메시지 1개를 렌더링 단위로: 추론 · 본문 · 툴 호출(결과 짝지음) */
data class Step(val reasoning: String?, val content: String, val tools: List<ToolPair>, val msg: StoredMessage)

object ChatTurns {
    fun rows(convId: Long, messages: List<StoredMessage>, busy: Boolean): List<ChatRow> {
        val out = ArrayList<ChatRow>()
        var turn = ArrayList<StoredMessage>()
        var turnAfter = -1
        fun flush(live: Boolean) {
            if (turn.isNotEmpty() || live) out += ChatRow.Turn("$convId-a-$turnAfter", turn, live, isLast = false)
            turn = ArrayList()
        }
        messages.forEachIndexed { i, m ->
            when (m.message.role) {
                Role.USER -> {
                    flush(false)
                    out += ChatRow.User("$convId-u-$i", i, m)
                    turnAfter = i
                }
                Role.SYSTEM -> Unit
                else -> turn += m
            }
        }
        flush(busy)
        val lastTurn = out.indexOfLast { it is ChatRow.Turn }
        if (lastTurn >= 0 && lastTurn == out.lastIndex) out[lastTurn] = (out[lastTurn] as ChatRow.Turn).copy(isLast = true)
        return out
    }

    /** 응답 묶음 → 단계. 툴 결과는 직전 assistant 의 호출 순서대로 짝짓는다 */
    fun steps(entries: List<StoredMessage>): List<Step> {
        val out = ArrayList<Step>()
        var i = 0
        while (i < entries.size) {
            val m = entries[i]
            if (m.message.role != Role.ASSISTANT) { i++; continue }
            val calls = m.message.toolCalls
            val results = ArrayList<String>()
            var j = i + 1
            while (j < entries.size && entries[j].message.role == Role.TOOL && results.size < calls.size) {
                results += entries[j].message.content
                j++
            }
            out += Step(
                reasoning = m.message.reasoning?.takeIf { it.isNotBlank() },
                content = m.message.content,
                tools = calls.mapIndexed { k, c -> ToolPair(c, results.getOrNull(k)) },
                msg = m,
            )
            i = j
        }
        return out
    }
}
