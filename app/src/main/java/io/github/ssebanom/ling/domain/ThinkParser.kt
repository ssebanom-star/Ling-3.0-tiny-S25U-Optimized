package io.github.ssebanom.ling.domain

/**
 * 스트리밍 출력에서 추론(<think>…</think>)과 본문을 분리한다.
 * thinking 모드 생성 프롬프트가 "<think>" 로 끝나므로, on 모드는 추론 상태에서 시작한다.
 * 태그가 조각 경계에 걸려 와도 처리하도록 태그 접두부는 보류한다.
 */
class ThinkParser(
    startInThink: Boolean,
    /** 모델별 추론 태그(K2-Horizon 은 `<ifm|think>` / `</ifm|think_faster>` 등) */
    private val openTag: String = OPEN,
    private val closeTag: String = CLOSE,
) {
    private var inThink = startInThink
    private var pending = StringBuilder()
    private val reasoningBuf = StringBuilder()
    private val contentBuf = StringBuilder()

    val reasoning: String get() = reasoningBuf.toString()
    val content: String get() = contentBuf.toString()
    val isThinking: Boolean get() = inThink

    data class Delta(val reasoning: String, val content: String)

    fun feed(piece: String): Delta {
        pending.append(piece)
        val r = StringBuilder()
        val c = StringBuilder()
        while (true) {
            val tag = if (inThink) closeTag else openTag
            val s = pending.toString()
            val idx = s.indexOf(tag)
            if (idx >= 0) {
                emit(s.substring(0, idx), r, c)
                pending = StringBuilder(s.substring(idx + tag.length))
                inThink = !inThink
                continue
            }
            // 태그 접두부일 수 있는 꼬리는 보류
            val hold = partialSuffix(s, tag)
            emit(s.substring(0, s.length - hold), r, c)
            pending = StringBuilder(s.substring(s.length - hold))
            break
        }
        return Delta(r.toString(), c.toString())
    }

    /** 스트림 종료: 보류분을 현재 상태로 내보낸다 */
    fun finish(): Delta {
        val r = StringBuilder()
        val c = StringBuilder()
        emit(pending.toString(), r, c)
        pending = StringBuilder()
        return Delta(r.toString(), c.toString())
    }

    private fun emit(text: String, r: StringBuilder, c: StringBuilder) {
        if (text.isEmpty()) return
        if (inThink) {
            r.append(text); reasoningBuf.append(text)
        } else {
            c.append(text); contentBuf.append(text)
        }
    }

    private fun partialSuffix(s: String, tag: String): Int {
        for (len in minOf(tag.length - 1, s.length) downTo 1) {
            if (s.endsWith(tag.substring(0, len))) return len
        }
        return 0
    }

    companion object {
        const val OPEN = "<think>"
        const val CLOSE = "</think>"
    }
}
