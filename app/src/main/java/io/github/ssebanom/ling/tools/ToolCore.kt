package io.github.ssebanom.ling.tools

/** 툴 묶음(설정에서 묶음 단위로 켜고 끔) */
enum class ToolGroup(val label: String, val detail: String) {
    BASIC("기본", "계산기 · 현재 시각 · 배터리/발열 상태"),
    WEB("웹", "인터넷 검색 · 웹 페이지 읽기"),
    DEVICE("기기 동작", "앱/URL 열기 · 알람 · 타이머 · 클립보드"),
    FILES("파일", "내장 저장소 파일 목록 · 검색 · 읽기 · 쓰기 (모든 파일 접근 권한 필요)"),
    SCREEN("화면 제어", "현재 화면 읽기 · 탭 · 입력 · 스크롤 · 뒤로/홈 (접근성 서비스 필요)"),
}

/**
 * 모델이 호출하는 온디바이스 툴. 스키마는 {name, description, parameters} 공통 형태로 내고
 * 모델 계열별 래핑(ModelFamily.toolSpec)은 레지스트리가 한다. 키 순서 고정(LinkedHashMap) → 프롬프트 바이트 안정.
 */
interface LocalTool {
    val name: String
    val group: ToolGroup
    val description: String
    val properties: Map<String, Any?> get() = linkedMapOf()
    val required: List<String> get() = emptyList()
    /** 부수효과가 있어 실행 전 사용자 승인이 필요한지 */
    val sideEffect: Boolean get() = false

    suspend fun execute(args: Map<String, Any?>): String

    /** 승인 요청에 보여줄 한 줄 요약 */
    fun describeCall(args: Map<String, Any?>): String =
        "$name(" + args.entries.joinToString(", ") { (k, v) -> "$k=${v.toString().take(80)}" } + ")"
}

fun LocalTool.function(): Map<String, Any?> = linkedMapOf(
    "name" to name,
    "description" to description,
    "parameters" to linkedMapOf<String, Any?>(
        "type" to "object",
        "properties" to properties,
        "required" to required,
    ),
)

internal fun prop(type: String, description: String, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
    linkedMapOf<String, Any?>("type" to type, "description" to description).apply { putAll(extra) }

/** 툴 실패: 메시지가 그대로 모델에게 전달된다 */
class ToolError(message: String) : Exception(message)

// ---- 인자 변환: Ling 은 모든 값을 문자열로, LFM 은 Python 리터럴 타입으로 준다 ----

internal fun Map<String, Any?>.str(key: String): String =
    this[key]?.toString()?.takeIf { it.isNotBlank() } ?: throw ToolError("missing argument '$key'")

internal fun Map<String, Any?>.strOr(key: String, def: String): String = this[key]?.toString()?.takeIf { it.isNotBlank() } ?: def

internal fun Map<String, Any?>.int(key: String, def: Int): Int = when (val v = this[key]) {
    null -> def
    is Number -> v.toInt()
    else -> v.toString().trim().toDoubleOrNull()?.toInt() ?: throw ToolError("'$key' must be a number, got '$v'")
}

internal fun Map<String, Any?>.intReq(key: String): Int = if (this[key] == null) throw ToolError("missing argument '$key'") else int(key, 0)

internal fun Map<String, Any?>.bool(key: String, def: Boolean): Boolean = when (val v = this[key]) {
    null -> def
    is Boolean -> v
    else -> when (v.toString().trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> def
    }
}

/** 툴 결과를 컨텍스트 보호용으로 자른다 */
internal fun String.capped(max: Int = 6000): String =
    if (length <= max) this else take(max) + "\n…(truncated, ${length - max} more chars)"
