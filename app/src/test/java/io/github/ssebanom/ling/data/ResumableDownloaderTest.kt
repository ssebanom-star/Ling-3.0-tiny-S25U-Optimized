package io.github.ssebanom.ling.data

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** 로컬 HTTP 서버로 리다이렉트·Range·중간 끊김·Range 무시·해시 불일치를 재현 */
class ResumableDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val data = Random(42).nextBytes(3 * 1024 * 1024 + 123)
    private val sha = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private lateinit var server: ServerSocket
    private val ranges = Collections.synchronizedList(mutableListOf<String?>())
    private val requests = AtomicInteger()
    /** 1MiB 만 보내고 끊을 요청 번호들(/cdn 요청 기준 1부터) */
    @Volatile private var dropOnRequest = setOf<Int>()
    @Volatile private var ignoreRange = false
    private val base get() = "http://127.0.0.1:${server.localPort}"

    @Before fun setUp() {
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(s) }; runCatching { s.close() } }
            }
        }
    }

    /** 최소 HTTP/1.1 서버: 요청마다 Connection: close */
    private fun handle(sock: Socket) {
        val inp = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val path = inp.readLine()?.split(' ')?.getOrNull(1) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = inp.readLine() ?: break
            if (l.isEmpty()) break
            headers[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim()
        }
        val out = sock.getOutputStream()
        fun head(status: String, extra: Map<String, String>) {
            val sb = StringBuilder("HTTP/1.1 $status\r\nConnection: close\r\n")
            extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        }
        if (path.startsWith("/resolve")) {
            // HF resolve → 서명 CDN URL 302 흉내
            head("302 Found", mapOf("Location" to "/cdn/file?sig=${requests.get()}", "Content-Length" to "0"))
            out.flush(); return
        }
        val n = requests.incrementAndGet()
        val range = headers["range"]
        ranges += range
        val start = if (range != null && !ignoreRange) range.removePrefix("bytes=").substringBefore('-').toInt() else 0
        if (start > 0) {
            head("206 Partial Content", mapOf("Content-Range" to "bytes $start-${data.size - 1}/${data.size}",
                "Content-Length" to "${data.size - start}", "Accept-Ranges" to "bytes"))
        } else {
            head("200 OK", mapOf("Content-Length" to "${data.size}", "Accept-Ranges" to "bytes"))
        }
        if (n in dropOnRequest) {
            out.write(data, start, 1024 * 1024) // 1MiB 만 보내고 끊음
            out.flush(); return
        }
        out.write(data, start, data.size - start)
        out.flush()
    }

    @After fun tearDown() = server.close()

    private fun downloader() = ResumableDownloader(maxRetries = 3, baseBackoffMs = 1, sleeper = {})

    @Test fun fullDownloadFollowsRedirectAndVerifies() {
        val target = tmp.root.resolve("m.gguf")
        val events = mutableListOf<ResumableDownloader.Event>()
        downloader().download("$base/resolve", target, data.size.toLong(), sha) { events += it }
        assertArrayEquals(data, target.readBytes())
        assertFalse(tmp.root.resolve("m.gguf.part").exists())
        assertTrue(events.any { it is ResumableDownloader.Event.Verifying })
    }

    @Test fun resumesWithRangeAfterDisconnect() {
        dropOnRequest = setOf(1)
        val target = tmp.root.resolve("m.gguf")
        val retries = mutableListOf<ResumableDownloader.Event.Retrying>()
        downloader().download("$base/resolve", target, data.size.toLong(), sha) {
            if (it is ResumableDownloader.Event.Retrying) retries += it
        }
        assertArrayEquals(data, target.readBytes())
        assertEquals(1, retries.size)
        assertEquals(listOf(null, "bytes=1048576-"), ranges.toList())
    }

    @Test fun resumesExistingPartFileAcrossProcessRestart() {
        val target = tmp.root.resolve("m.gguf")
        tmp.root.resolve("m.gguf.part").writeBytes(data.copyOfRange(0, 2_000_000))
        downloader().download("$base/resolve", target, data.size.toLong(), sha)
        assertArrayEquals(data, target.readBytes())
        assertEquals(listOf("bytes=2000000-"), ranges.toList())
    }

    @Test fun restartsWhenServerIgnoresRange() {
        ignoreRange = true
        val target = tmp.root.resolve("m.gguf")
        tmp.root.resolve("m.gguf.part").writeBytes(data.copyOfRange(0, 500_000))
        downloader().download("$base/resolve", target, data.size.toLong(), sha)
        assertArrayEquals(data, target.readBytes())
    }

    @Test fun shaMismatchDeletesPart() {
        val target = tmp.root.resolve("m.gguf")
        try {
            downloader().download("$base/resolve", target, data.size.toLong(), "00".repeat(32))
            fail("expected failure")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SHA-256"))
        }
        assertFalse(target.exists())
        assertFalse(tmp.root.resolve("m.gguf.part").exists())
    }

    @Test fun givesUpAfterMaxRetries() {
        dropOnRequest = (1..10).toSet()
        try {
            downloader().download("$base/resolve", tmp.root.resolve("m.gguf"), data.size.toLong(), sha)
            fail("expected failure")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("재시도"))
        }
    }

    @Test fun alreadyCompletePartOnlyVerifies() {
        val target = tmp.root.resolve("m.gguf")
        tmp.root.resolve("m.gguf.part").writeBytes(data)
        downloader().download("$base/resolve", target, data.size.toLong(), sha)
        assertArrayEquals(data, target.readBytes())
        assertEquals(0, requests.get())
    }
}
