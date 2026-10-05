package io.github.ssebanom.ling.runtime

import android.content.Context
import android.os.SystemClock
import io.github.ssebanom.ling.data.Conversation
import io.github.ssebanom.ling.data.ConversationStore
import io.github.ssebanom.ling.data.MessageStats
import io.github.ssebanom.ling.data.SettingsRepository
import io.github.ssebanom.ling.data.StoredMessage
import io.github.ssebanom.ling.domain.ChatMessage
import io.github.ssebanom.ling.domain.LingPromptBuilder
import io.github.ssebanom.ling.domain.Role
import io.github.ssebanom.ling.domain.ThinkParser
import io.github.ssebanom.ling.domain.ToolCallParser
import io.github.ssebanom.ling.engine.EngineException
import io.github.ssebanom.ling.engine.SamplerConfig
import io.github.ssebanom.ling.engine.StopReason
import io.github.ssebanom.ling.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

enum class Phase { IDLE, LOADING, PREFILL, THINKING, ANSWERING, TOOL }

data class Streaming(
    val phase: Phase = Phase.IDLE,
    val prefillDone: Int = 0,
    val prefillTotal: Int = 0,
    val reasoning: String = "",
    val content: String = "",
    val tokens: Int = 0,
    val tps: Double = 0.0,
    val thermal: ThermalGovernor.Level = ThermalGovernor.Level.NORMAL,
)

data class ChatState(
    val conversation: Conversation? = null,
    val messages: List<StoredMessage> = emptyList(),
    val streaming: Streaming = Streaming(),
    val error: String? = null,
    val lastStats: MessageStats? = null,
) {
    val busy: Boolean get() = streaming.phase != Phase.IDLE
}

/**
 * 대화 1턴 실행기.
 * 메시지 → LingPromptBuilder.segments(rawTokens 재사용) → engine.sync(증분 prefill/체크포인트)
 * → engine.generate(스트리밍, ThinkParser, 열 관리, ADPF) → 저장 → (툴 호출 시) 실행 후 반복.
 */
class ChatController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val inference: InferenceManager,
    private val store: ConversationStore,
    private val settings: SettingsRepository,
    private val tools: ToolRegistry,
    private val thermal: ThermalGovernor,
    private val hints: PerfHints,
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state
    private var job: Job? = null
    @Volatile private var stopRequested = false

    /** 서비스가 구독: 생성 중 여부 */
    val busyFlow = MutableStateFlow(false)

    fun openConversation(id: Long) = scope.launch {
        if (_state.value.busy) return@launch // 생성 중 전환 금지(진행 중 턴이 상태를 덮어씀)
        val c = store.getConversation(id) ?: return@launch
        _state.value = ChatState(conversation = c, messages = store.messages(id))
    }

    fun newConversation(thinking: Boolean? = null) = scope.launch {
        if (_state.value.busy) return@launch
        val s = settings.current()
        _state.value = ChatState(
            conversation = Conversation(0, "새 대화", thinking ?: s.defaultThinking, s.systemPrompt, 0, 0),
        )
    }

    /** 새 대화에서만 의미 있음(기존 대화는 전환 시 전체 재-prefill 필요 → 확인 후 호출) */
    fun setThinking(on: Boolean) = scope.launch {
        val c = _state.value.conversation ?: return@launch
        val updated = c.copy(thinking = on)
        if (c.id != 0L) store.updateConversation(updated)
        _state.update { it.copy(conversation = updated) }
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.busy) return
        val msgs = _state.value.messages.map { it.message to it.stats } + (ChatMessage(Role.USER, text.trim()) to null)
        runTurn(msgs)
    }

    /** 마지막 assistant 응답 재생성 */
    fun regenerate() {
        if (_state.value.busy) return
        val list = _state.value.messages
        val lastUser = list.indexOfLast { it.message.role == Role.USER }
        if (lastUser < 0) return
        runTurn(list.take(lastUser + 1).map { it.message to it.stats })
    }

    /** index 번째 사용자 메시지를 수정하고 그 뒤를 다시 생성 */
    fun editAndResend(index: Int, newText: String) {
        if (_state.value.busy) return
        val list = _state.value.messages
        if (index !in list.indices || list[index].message.role != Role.USER) return
        val head = list.take(index).map { it.message to it.stats }
        runTurn(head + (ChatMessage(Role.USER, newText.trim()) to null))
    }

    fun stop() {
        stopRequested = true
        inference.engine.cancel()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    @Synchronized
    private fun runTurn(initial: List<Pair<ChatMessage, MessageStats?>>) {
        if (busyFlow.value) return // 연타/중복 호출 방지(상태 반영 전 구간 포함)
        busyFlow.value = true
        stopRequested = false
        _state.update { it.copy(error = null, streaming = it.streaming.copy(phase = Phase.LOADING)) }
        job = scope.launch {
            try {
                inference.lock.withLock { turnLoop(initial) }
            } catch (e: EngineException) {
                // 사용자가 prefill 중 정지한 경우는 오류로 표시하지 않는다
                if (!stopRequested) _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toString()) }
            } finally {
                hints.stop()
                _state.update { it.copy(streaming = Streaming()) }
                busyFlow.value = false
            }
        }
    }

    private suspend fun turnLoop(initial: List<Pair<ChatMessage, MessageStats?>>) {
        var conv = ensureConversation(initial)
        var msgs = initial.toMutableList()
        persist(conv, msgs)

        setPhase(Phase.LOADING)
        if (!inference.ensureLoaded()) {
            _state.update { it.copy(error = (inference.status.value as? EngineStatus.Error)?.message ?: "모델이 없습니다. 모델 탭에서 받으세요.") }
            return
        }
        val engine = inference.engine
        val s = settings.current()
        val toolSpecs = if (s.toolsEnabled) tools.specs else emptyList()

        // 열 상태에 따라 이번 턴 디코드 스레드 조정
        val cfg = engine.config!!
        if (s.thermalGovernor) {
            val lvl = thermal.snapshot().level
            val want = thermal.recommendedThreads(inference.configFor(s, java.io.File(cfg.modelPath)).nThreads, lvl)
            if (want != cfg.nThreads) engine.setThreads(want, cfg.nThreadsBatch, cfg.cpuMask)
        }

        repeat(MAX_TOOL_ROUNDS) {
            val withSystem = buildList {
                if (conv.systemPrompt.isNotBlank()) add(ChatMessage(Role.SYSTEM, conv.systemPrompt))
                addAll(msgs.map { it.first })
            }
            val segments = LingPromptBuilder.segments(withSystem, toolSpecs, conv.thinking)

            setPhase(Phase.PREFILL)
            val sync = engine.sync(segments) { done, total ->
                _state.update { st -> st.copy(streaming = st.streaming.copy(phase = Phase.PREFILL, prefillDone = done, prefillTotal = total)) }
                if (stopRequested) engine.cancel()
            }
            if (stopRequested) return

            // ADPF: 추론 스레드 + ggml 워커, 목표 25ms/토큰(40 tok/s)
            if (s.perfHints) hints.start(DeviceProfiler.inferenceThreadIds(), 1_000_000_000L / 40)

            val parser = ThinkParser(startInThink = conv.thinking)
            var nTok = 0
            val t0 = SystemClock.elapsedRealtimeNanos()
            var lastTokNs = t0
            var lastUi = 0L
            var lastThermalCheck = 0L
            var level = thermal.snapshot().level
            setPhase(if (conv.thinking) Phase.THINKING else Phase.ANSWERING)

            val sampler = SamplerConfig(
                temperature = s.temperature, topP = s.topP, topK = s.topK, minP = s.minP,
                repeatPenalty = s.repeatPenalty, maxTokens = s.maxTokens,
            )
            val gen = engine.generate(sampler) { piece ->
                parser.feed(piece)
                nTok++
                val now = SystemClock.elapsedRealtimeNanos()
                if (s.perfHints) hints.report(now - lastTokNs)
                lastTokNs = now
                // 열 관리(추론 스레드에서 지연 삽입)
                if (s.thermalGovernor && now - lastThermalCheck > 2_000_000_000L) {
                    level = thermal.snapshot().level
                    lastThermalCheck = now
                }
                val delay = if (s.thermalGovernor) thermal.interTokenDelayMs(level) else 0
                if (delay > 0) Thread.sleep(delay)
                // UI 갱신은 ~16fps 로 제한
                if (now - lastUi > 60_000_000L) {
                    lastUi = now
                    val tps = nTok * 1e9 / (now - t0)
                    _state.update { st ->
                        st.copy(streaming = st.streaming.copy(
                            phase = if (parser.isThinking) Phase.THINKING else Phase.ANSWERING,
                            reasoning = parser.reasoning,
                            content = ToolCallParser.visiblePrefix(parser.content),
                            tokens = nTok, tps = tps, thermal = level,
                        ))
                    }
                }
                !stopRequested
            }
            parser.finish()
            hints.stop()

            val parsed = ToolCallParser.parse(parser.content.trim())
            val reasoning = parser.reasoning.trim('\n').ifEmpty { null }
            val assistant = ChatMessage(
                role = Role.ASSISTANT,
                content = parsed.text,
                reasoning = reasoning,
                toolCalls = parsed.calls,
                rawTokens = gen.tokens,
                thinkingAtGeneration = conv.thinking,
            )
            val stats = MessageStats(
                prefillTokens = sync.nPrefilled, reusedTokens = sync.nReused, prefillMs = sync.prefillMs,
                decodeTokens = gen.tokens.size, decodeMs = gen.decodeMs, ttftMs = sync.prefillMs + gen.ttftMs,
                restored = sync.restoredCheckpoint,
            )
            msgs += assistant to stats
            _state.update { it.copy(lastStats = stats) }
            persist(conv, msgs)

            if (gen.reason == StopReason.CONTEXT_FULL) {
                _state.update { it.copy(error = "컨텍스트가 가득 찼습니다. 새 대화를 시작하거나 설정에서 컨텍스트를 늘리세요.") }
                return
            }
            if (stopRequested || parsed.calls.isEmpty() || !s.toolsEnabled) return

            // 툴 실행 → OBSERVATION 추가 후 다음 라운드
            setPhase(Phase.TOOL)
            for (call in parsed.calls) {
                msgs += ChatMessage(Role.TOOL, tools.execute(call.name, call.arguments)) to null
            }
            persist(conv, msgs)
        }
    }

    private suspend fun ensureConversation(initial: List<Pair<ChatMessage, MessageStats?>>): Conversation {
        val c = _state.value.conversation ?: Conversation(0, "새 대화", settings.current().defaultThinking, settings.current().systemPrompt, 0, 0)
        if (c.id != 0L) return c
        val firstUser = initial.firstOrNull { it.first.role == Role.USER }?.first?.content.orEmpty()
        val created = store.createConversation(firstUser.take(40).ifEmpty { "새 대화" }, c.thinking, c.systemPrompt)
        _state.update { it.copy(conversation = created) }
        return created
    }

    private suspend fun persist(conv: Conversation, msgs: List<Pair<ChatMessage, MessageStats?>>) {
        store.replaceMessages(conv.id, msgs)
        val stored = store.messages(conv.id)
        _state.update { it.copy(messages = stored) }
    }

    private fun setPhase(p: Phase) = _state.update { it.copy(streaming = it.streaming.copy(phase = p)) }

    companion object {
        const val MAX_TOOL_ROUNDS = 4
    }
}
