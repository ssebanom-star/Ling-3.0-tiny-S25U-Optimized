package io.github.ssebanom.ling

import android.content.Context
import io.github.ssebanom.ling.data.ConversationStore
import io.github.ssebanom.ling.data.ModelStore
import io.github.ssebanom.ling.data.SettingsRepository
import io.github.ssebanom.ling.engine.LingEngine
import io.github.ssebanom.ling.runtime.ChatController
import io.github.ssebanom.ling.runtime.InferenceManager
import io.github.ssebanom.ling.runtime.PerfHints
import io.github.ssebanom.ling.runtime.SetupManager
import io.github.ssebanom.ling.runtime.ThermalGovernor
import io.github.ssebanom.ling.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 수동 DI 컨테이너 (프로세스 싱글톤) */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val models = ModelStore(context)
    val conversations = ConversationStore(context)
    val engine: LingEngine by lazy { LingEngine.get(context) }
    val inference: InferenceManager by lazy { InferenceManager(context, engine, settings, models) }
    val thermal = ThermalGovernor(context)
    val hints = PerfHints(context)
    val tools = ToolRegistry(context)
    val setup: SetupManager by lazy { SetupManager(appScope, settings, models, inference) }
    val chat: ChatController by lazy {
        ChatController(context, appScope, inference, conversations, settings, tools, thermal, hints)
    }
}
