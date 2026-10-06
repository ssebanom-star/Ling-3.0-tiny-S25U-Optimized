package io.github.ssebanom.ling.tools

import android.content.Context
import android.os.Environment
import io.github.ssebanom.ling.data.AppSettings
import io.github.ssebanom.ling.domain.ModelFamily
import io.github.ssebanom.ling.service.LingAccessibilityService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 툴 목록·노출·실행. 설정에서 켠 묶음 중 권한이 갖춰진 것만 모델에 노출한다
 * (노출 목록이 바뀌면 시스템 프롬프트가 바뀌어 캐시가 끊기므로 대화 중에는 가급적 고정).
 */
class ToolRegistry(context: Context, private val approvals: ToolApprovals) {
    private val app = context.applicationContext
    @Volatile private var searchSettings: AppSettings = AppSettings()

    private val box = StorageSandbox()

    val all: List<LocalTool> = listOf(
        CalculatorTool(), TimeTool(), DeviceStatusTool(app),
        WebSearchTool { providers(searchSettings) }, FetchUrlTool(),
        OpenAppTool(app), OpenUrlTool(app), SetAlarmTool(app), SetTimerTool(app), GetClipboardTool(app), SetClipboardTool(app),
        ListFilesTool(box), SearchFilesTool(box), ReadTextFileTool(box), WriteTextFileTool(box),
        ReadScreenTool(), TapTool(), TypeTextTool(), ScrollTool(), PressKeyTool(),
    )

    fun groupReady(g: ToolGroup): Boolean = when (g) {
        ToolGroup.FILES -> Environment.isExternalStorageManager()
        ToolGroup.SCREEN -> LingAccessibilityService.instance != null
        else -> true
    }

    fun available(s: AppSettings): List<LocalTool> =
        if (!s.toolsEnabled) emptyList() else all.filter { it.group.name in s.toolGroups && groupReady(it.group) }

    fun specs(s: AppSettings, family: ModelFamily): List<Map<String, Any?>> = available(s).map { family.toolSpec(it.function()) }

    /** 툴 사용 지침(켜진 묶음만). 날짜는 일 단위라 하루 동안 프롬프트 접두부(캐시)가 유지된다 */
    fun guide(s: AppSettings): String {
        val groups = available(s).map { it.group }.toSet()
        if (groups.isEmpty()) return ""
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)", Locale.ENGLISH))
        return buildString {
            append("You are an assistant running on the user's Android phone (Samsung Galaxy S25 Ultra). Today is ").append(today).append('.')
            append(" Use the provided tools when they help, one step at a time, and answer in the user's language.")
            if (ToolGroup.WEB in groups) append(" For recent or uncertain facts, call web_search, then fetch_url on the most relevant results, and cite the URLs you used.")
            if (ToolGroup.SCREEN in groups) append(" To operate other apps: open_app, then read_screen, then tap/type_text/scroll using element indices; check the returned screen after every action.")
            if (ToolGroup.FILES in groups) append(" File paths are relative to internal storage, e.g. /Download or /Documents.")
            append(" If a tool returns an error, explain it or try another way instead of repeating the same call.")
        }
    }

    suspend fun execute(name: String, args: Map<String, Any?>, s: AppSettings): String {
        val tool = available(s).firstOrNull { it.name == name }
            ?: return if (all.any { it.name == name }) "error: tool '$name' is disabled or lacks permission" else "error: unknown tool '$name'"
        if (tool.sideEffect && !s.autoApproveTools && !approvals.ask(tool.name, tool.describeCall(args))) {
            return "error: the user declined this action"
        }
        searchSettings = s
        return try {
            tool.execute(args).capped(8000)
        } catch (e: ToolError) {
            "error: ${e.message}"
        } catch (e: Exception) {
            "error: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    companion object {
        fun providers(s: AppSettings): List<SearchProvider> = buildList {
            if (s.searxngUrl.isNotBlank()) add(SearxngProvider(s.searxngUrl.trim()))
            if (s.braveApiKey.isNotBlank()) add(BraveProvider(s.braveApiKey.trim()))
            add(DdgLiteProvider())
            add(DdgHtmlProvider())
            add(BingProvider())
        }
    }
}
