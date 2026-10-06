package io.github.ssebanom.ling.tools

import io.github.ssebanom.ling.service.LingAccessibilityService
import kotlinx.coroutines.delay

private fun svc(): LingAccessibilityService = LingAccessibilityService.instance
    ?: throw ToolError("screen control is off: ask the user to enable 'Ling 화면 제어' in Settings > Accessibility")

/** 동작 후 화면이 바뀔 시간을 주고 새 화면을 돌려준다(모델이 다음 단계를 판단) */
private suspend fun after(result: String): String {
    if (result.startsWith("error")) return result
    delay(800)
    return "$result\n--- screen now ---\n" + svc().readScreen().capped(5000)
}

class ReadScreenTool : LocalTool {
    override val name = "read_screen"
    override val group = ToolGroup.SCREEN
    override val description =
        "Read what is currently shown on the phone screen. Returns numbered elements: [index] Type \"text\" (description) {click,edit,scroll}. Use the index with tap/type_text/scroll."
    override suspend fun execute(args: Map<String, Any?>): String = svc().readScreen().capped(6000)
}

class TapTool : LocalTool {
    override val name = "tap"
    override val group = ToolGroup.SCREEN
    override val description = "Tap an element on screen by its index from read_screen, or by visible text."
    override val properties = linkedMapOf<String, Any?>(
        "index" to prop("integer", "element index from read_screen"),
        "text" to prop("string", "visible text of the element (used if index is not given)"),
        "long_press" to prop("boolean", "long press instead of tap (default false)"),
    )
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) =
        (if (args.bool("long_press", false)) "길게 누르기: " else "탭: ") + (args["index"]?.let { "[$it]" } ?: "\"${args["text"]}\"")

    override suspend fun execute(args: Map<String, Any?>): String {
        val s = svc()
        val idx = if (args["index"] != null) args.int("index", -1)
        else s.findByText(args.str("text")) ?: throw ToolError("no element with text '${args["text"]}'")
        return after(s.tap(idx, args.bool("long_press", false)))
    }
}

class TypeTextTool : LocalTool {
    override val name = "type_text"
    override val group = ToolGroup.SCREEN
    override val description = "Replace the text of an editable field (by index from read_screen). Set submit=true to press enter/search."
    override val properties = linkedMapOf<String, Any?>(
        "index" to prop("integer", "index of an {edit} element"),
        "text" to prop("string", "text to enter"),
        "submit" to prop("boolean", "press enter after typing (default false)"),
    )
    override val required = listOf("index", "text")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "입력: [${args["index"]}] ← \"${args["text"].toString().take(60)}\"" +
        if (args.bool("submit", false)) " + 엔터" else ""

    override suspend fun execute(args: Map<String, Any?>): String {
        val s = svc()
        val idx = args.intReq("index")
        var r = s.setText(idx, args["text"]?.toString().orEmpty())
        if (!r.startsWith("error") && args.bool("submit", false)) r += if (s.submit(idx)) " + submitted" else " (submit failed)"
        return after(r)
    }
}

class ScrollTool : LocalTool {
    override val name = "scroll"
    override val group = ToolGroup.SCREEN
    override val description = "Scroll the screen or a scrollable element."
    override val properties = linkedMapOf<String, Any?>(
        "direction" to prop("string", "down or up (also right/left)"),
        "index" to prop("integer", "optional element index to scroll"),
    )
    override val required = listOf("direction")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "스크롤: ${args["direction"]}" + (args["index"]?.let { " [$it]" } ?: "")

    override suspend fun execute(args: Map<String, Any?>): String =
        after(svc().scroll(args.str("direction"), args["index"]?.let { args.int("index", 0) }))
}

class PressKeyTool : LocalTool {
    override val name = "press_key"
    override val group = ToolGroup.SCREEN
    override val description = "Press a system key: back, home, recents, notifications, quick_settings, lock_screen."
    override val properties = linkedMapOf<String, Any?>("key" to prop("string", "back | home | recents | notifications | quick_settings | lock_screen"))
    override val required = listOf("key")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "시스템 키: ${args["key"]}"

    override suspend fun execute(args: Map<String, Any?>): String = after(svc().global(args.str("key")))
}
