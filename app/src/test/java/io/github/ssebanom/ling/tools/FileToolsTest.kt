package io.github.ssebanom.ling.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileToolsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val box by lazy { StorageSandbox(tmp.root, hasAccess = { true }) }

    @Test fun sandboxRejectsEscape() {
        for (p in listOf("../etc/passwd", "/../../x", "/Download/../../x")) {
            try { box.resolve(p); fail(p) } catch (e: ToolError) { assertTrue(e.message!!.contains("outside")) }
        }
        assertEquals(tmp.root.canonicalFile, box.resolve("/"))
        assertEquals("/Download/a.txt", box.display(box.resolve("Download/a.txt")))
    }

    @Test fun noPermissionMessage() {
        val denied = StorageSandbox(tmp.root, hasAccess = { false })
        try { denied.resolve("/"); fail() } catch (e: ToolError) { assertTrue(e.message!!.contains("permission")) }
    }

    @Test fun writeListSearchRead() = runBlocking {
        val args = mapOf("path" to "/Documents/note.md", "content" to "안녕\n두 번째 줄")
        assertTrue(WriteTextFileTool(box).execute(args).startsWith("wrote"))
        WriteTextFileTool(box).execute(mapOf("path" to "/Documents/note.md", "content" to "!", "append" to true))
        assertTrue(ListFilesTool(box).execute(mapOf("path" to "/Documents")).contains("note.md"))
        assertTrue(SearchFilesTool(box).execute(mapOf("query" to "NOTE")).contains("/Documents/note.md"))
        assertEquals("안녕\n두 번째 줄!", ReadTextFileTool(box).execute(mapOf("path" to "/Documents/note.md")))
        // 페이지 넘김
        val part = ReadTextFileTool(box).execute(mapOf("path" to "/Documents/note.md", "start" to "3", "max_chars" to 200))
        assertEquals("두 번째 줄!", part)
    }

    @Test fun binaryRejected() = runBlocking {
        tmp.newFile("b.bin").writeBytes(byteArrayOf(1, 0, 2))
        try { ReadTextFileTool(box).execute(mapOf("path" to "/b.bin")); fail() } catch (e: ToolError) { assertTrue(e.message!!.contains("binary")) }
    }

    @Test fun argCoercion() {
        val a = mapOf<String, Any?>("n" to "3", "m" to 4L, "b" to "true", "x" to 2.9)
        assertEquals(3, a.int("n", 0)); assertEquals(4, a.int("m", 0)); assertEquals(2, a.int("x", 0))
        assertTrue(a.bool("b", false)); assertEquals(7, a.int("missing", 7))
    }
}
