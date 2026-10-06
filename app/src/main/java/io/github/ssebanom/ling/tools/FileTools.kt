package io.github.ssebanom.ling.tools

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 내장 저장소(/storage/emulated/0) 파일 툴. "모든 파일 접근"(MANAGE_EXTERNAL_STORAGE) 권한이 있어야 동작.
 * 경로는 저장소 루트 기준("/Download/a.txt")이며, 정규화 후 루트 밖으로 나가는 경로는 거부한다.
 */
class StorageSandbox(
    val root: File = Environment.getExternalStorageDirectory(),
    private val hasAccess: () -> Boolean = { Environment.isExternalStorageManager() },
) {
    fun resolve(path: String): File {
        if (!hasAccess()) throw ToolError("no storage permission: ask the user to allow '모든 파일 접근' for this app in Settings")
        val rootCanon = root.canonicalFile
        val p = path.trim().ifEmpty { "/" }
        val f = if (p.startsWith(rootCanon.path) || p.startsWith(root.path)) File(p) else File(rootCanon, p.trimStart('/'))
        val c = f.canonicalFile
        if (c != rootCanon && !c.path.startsWith(rootCanon.path + File.separator)) throw ToolError("path outside storage: $path")
        return c
    }

    fun display(f: File): String = "/" + f.canonicalFile.relativeTo(root.canonicalFile).path
}

private fun human(n: Long): String = when {
    n >= 1L shl 30 -> "%.1fGB".format(n / (1L shl 30).toDouble())
    n >= 1L shl 20 -> "%.1fMB".format(n / (1L shl 20).toDouble())
    n >= 1L shl 10 -> "%.1fKB".format(n / 1024.0)
    else -> "${n}B"
}

private val DATE = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

class ListFilesTool(private val box: StorageSandbox) : LocalTool {
    override val name = "list_files"
    override val group = ToolGroup.FILES
    override val description = "List files and folders in the phone's internal storage. Paths are relative to storage root, e.g. '/Download'."
    override val properties = linkedMapOf<String, Any?>("path" to prop("string", "folder path (default '/')"))

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val dir = box.resolve(args.strOr("path", "/"))
        if (!dir.isDirectory) throw ToolError("not a folder: ${box.display(dir)}")
        val items = dir.listFiles().orEmpty().filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        buildString {
            append(box.display(dir)).append(" (").append(items.size).append(" items)\n")
            for (f in items.take(200)) {
                if (f.isDirectory) append("[dir]  ").append(f.name).append("/\n")
                else append("[file] ").append(f.name).append("  ").append(human(f.length())).append("  ").append(DATE.format(Date(f.lastModified()))).append('\n')
            }
            if (items.size > 200) append("…(${items.size - 200} more)")
        }
    }
}

class SearchFilesTool(private val box: StorageSandbox) : LocalTool {
    override val name = "search_files"
    override val group = ToolGroup.FILES
    override val description = "Find files whose name contains the query (case-insensitive), searching sub-folders."
    override val properties = linkedMapOf<String, Any?>(
        "query" to prop("string", "part of the file name, e.g. 'invoice' or '.pdf'"),
        "path" to prop("string", "folder to search (default '/')"),
        "max_results" to prop("integer", "default 30"),
    )
    override val required = listOf("query")

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val q = args.str("query").lowercase()
        val start = box.resolve(args.strOr("path", "/"))
        val max = args.int("max_results", 30).coerceIn(1, 100)
        val hits = ArrayList<File>()
        var scanned = 0
        val stack = ArrayDeque(listOf(start to 0))
        while (stack.isNotEmpty() && hits.size < max && scanned < 50_000) {
            val (d, depth) = stack.removeLast()
            for (f in d.listFiles().orEmpty()) {
                scanned++
                if (f.name.startsWith(".")) continue
                if (f.name.lowercase().contains(q)) hits += f
                if (f.isDirectory && depth < 10 && box.display(f) != "/Android/data" && box.display(f) != "/Android/obb") stack.addLast(f to depth + 1)
                if (hits.size >= max) break
            }
        }
        if (hits.isEmpty()) "no files matching '$q' under ${box.display(start)}"
        else hits.joinToString("\n") { f ->
            (if (f.isDirectory) "[dir]  " else "[file] ") + box.display(f) + if (f.isFile) "  " + human(f.length()) else ""
        }
    }
}

class ReadTextFileTool(private val box: StorageSandbox) : LocalTool {
    override val name = "read_text_file"
    override val group = ToolGroup.FILES
    override val description = "Read a text file (txt, md, csv, json, code, etc.) from internal storage."
    override val properties = linkedMapOf<String, Any?>(
        "path" to prop("string", "file path, e.g. '/Documents/notes.txt'"),
        "start" to prop("integer", "character offset (default 0)"),
        "max_chars" to prop("integer", "default 6000, max 12000"),
    )
    override val required = listOf("path")

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val f = box.resolve(args.str("path"))
        if (!f.isFile) throw ToolError("file not found: ${box.display(f)}")
        val start = args.int("start", 0).coerceAtLeast(0)
        val max = args.int("max_chars", 6000).coerceIn(200, 12000)
        val head = f.inputStream().use { it.readNBytes(4096) }
        if (head.any { it == 0.toByte() }) throw ToolError("binary file (not text): ${box.display(f)}")
        if (f.length() > 20L shl 20) throw ToolError("file too large (${human(f.length())})")
        val text = f.readText()
        if (start >= text.length) return@withContext "(end of file; ${text.length} chars)"
        val end = minOf(text.length, start + max)
        text.substring(start, end) + if (end < text.length) "\n…(more: start=$end; total ${text.length} chars)" else ""
    }
}

class WriteTextFileTool(private val box: StorageSandbox) : LocalTool {
    override val name = "write_text_file"
    override val group = ToolGroup.FILES
    override val description = "Create or overwrite (or append to) a text file in internal storage. Parent folders are created."
    override val properties = linkedMapOf<String, Any?>(
        "path" to prop("string", "file path, e.g. '/Documents/summary.md'"),
        "content" to prop("string", "text to write"),
        "append" to prop("boolean", "append instead of overwrite (default false)"),
    )
    override val required = listOf("path", "content")
    override val sideEffect = true

    override fun describeCall(args: Map<String, Any?>): String =
        "파일 ${if (args.bool("append", false)) "추가" else "쓰기"}: ${args["path"]} (${args["content"]?.toString()?.length ?: 0}자)"

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val f = box.resolve(args.str("path"))
        if (f.isDirectory) throw ToolError("is a folder: ${box.display(f)}")
        val content = args["content"]?.toString() ?: throw ToolError("missing argument 'content'")
        f.parentFile?.mkdirs()
        if (args.bool("append", false)) f.appendText(content) else f.writeText(content)
        "wrote ${content.length} chars to ${box.display(f)} (${human(f.length())})"
    }
}
