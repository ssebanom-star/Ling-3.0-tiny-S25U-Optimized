package io.github.ssebanom.ling.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun Context.launch(i: Intent) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(i)
}

/** 런처에 보이는 앱 (라벨, 패키지) */
private fun Context.launchableApps(): List<Pair<String, String>> {
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(q, 0)
        .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
        .distinctBy { it.second }
        .sortedBy { it.first }
}

class OpenAppTool(private val context: Context) : LocalTool {
    override val name = "open_app"
    override val group = ToolGroup.DEVICE
    override val description = "Open an installed app by its name (e.g. '카카오톡', 'YouTube', '설정') or package name."
    override val properties = linkedMapOf<String, Any?>("name" to prop("string", "app name or package"))
    override val required = listOf("name")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "앱 열기: ${args["name"]}"

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val q = args.str("name").trim()
        val apps = context.launchableApps()
        val norm = { s: String -> s.lowercase().replace(" ", "") }
        val hit = apps.firstOrNull { norm(it.first) == norm(q) || it.second == q }
            ?: apps.firstOrNull { norm(it.first).contains(norm(q)) }
            ?: apps.firstOrNull { it.second.contains(norm(q)) }
            ?: return@withContext "error: no app named '$q'. Installed apps include: " +
                apps.take(60).joinToString(", ") { it.first }
        val i = context.packageManager.getLaunchIntentForPackage(hit.second) ?: throw ToolError("cannot launch ${hit.first}")
        withContext(Dispatchers.Main) { context.launch(i) }
        "opened ${hit.first} (${hit.second})"
    }
}

class OpenUrlTool(private val context: Context) : LocalTool {
    override val name = "open_url"
    override val group = ToolGroup.DEVICE
    override val description = "Open a URL in the browser or the app that handles it (also tel:, mailto:, geo: links)."
    override val properties = linkedMapOf<String, Any?>("url" to prop("string", "URL to open"))
    override val required = listOf("url")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "열기: ${args["url"]}"

    override suspend fun execute(args: Map<String, Any?>): String {
        val raw = args.str("url").trim()
        val uri = Uri.parse(if (raw.contains(':')) raw else "https://$raw")
        if (uri.scheme !in setOf("http", "https", "tel", "mailto", "geo", "sms", "market")) throw ToolError("unsupported scheme: ${uri.scheme}")
        withContext(Dispatchers.Main) { context.launch(Intent(Intent.ACTION_VIEW, uri)) }
        return "opened $uri"
    }
}

class SetAlarmTool(private val context: Context) : LocalTool {
    override val name = "set_alarm"
    override val group = ToolGroup.DEVICE
    override val description = "Set an alarm in the clock app."
    override val properties = linkedMapOf<String, Any?>(
        "hour" to prop("integer", "0-23"),
        "minute" to prop("integer", "0-59"),
        "label" to prop("string", "optional alarm label"),
    )
    override val required = listOf("hour", "minute")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) =
        "알람: %02d:%02d %s".format(args.int("hour", 0), args.int("minute", 0), args["label"] ?: "")

    override suspend fun execute(args: Map<String, Any?>): String {
        val h = args.intReq("hour").also { if (it !in 0..23) throw ToolError("hour must be 0-23") }
        val m = args.intReq("minute").also { if (it !in 0..59) throw ToolError("minute must be 0-59") }
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args["label"]?.toString()?.takeIf { it.isNotBlank() }?.let { i.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        withContext(Dispatchers.Main) { context.launch(i) }
        return "alarm set for %02d:%02d".format(h, m)
    }
}

class SetTimerTool(private val context: Context) : LocalTool {
    override val name = "set_timer"
    override val group = ToolGroup.DEVICE
    override val description = "Start a countdown timer in the clock app."
    override val properties = linkedMapOf<String, Any?>(
        "seconds" to prop("integer", "duration in seconds (1-86400)"),
        "label" to prop("string", "optional label"),
    )
    override val required = listOf("seconds")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "타이머: ${args["seconds"]}초 ${args["label"] ?: ""}"

    override suspend fun execute(args: Map<String, Any?>): String {
        val s = args.intReq("seconds").also { if (it !in 1..86_400) throw ToolError("seconds must be 1-86400") }
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        args["label"]?.toString()?.takeIf { it.isNotBlank() }?.let { i.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        withContext(Dispatchers.Main) { context.launch(i) }
        return "timer started for $s seconds"
    }
}

class GetClipboardTool(private val context: Context) : LocalTool {
    override val name = "get_clipboard"
    override val group = ToolGroup.DEVICE
    override val description = "Read the current clipboard text (works while this app is in the foreground)."
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.Main) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip ?: return@withContext "(clipboard empty or not accessible in background)"
        (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(context).toString() }.capped(4000)
    }
}

class SetClipboardTool(private val context: Context) : LocalTool {
    override val name = "set_clipboard"
    override val group = ToolGroup.DEVICE
    override val description = "Copy text to the clipboard."
    override val properties = linkedMapOf<String, Any?>("text" to prop("string", "text to copy"))
    override val required = listOf("text")
    override val sideEffect = true
    override fun describeCall(args: Map<String, Any?>) = "클립보드 복사: \"${args["text"].toString().take(60)}\""

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.Main) {
        val t = args["text"]?.toString().orEmpty()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Ling", t))
        "copied ${t.length} chars"
    }
}
