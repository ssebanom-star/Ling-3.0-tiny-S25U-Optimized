package io.github.ssebanom.ling.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 대용량(수 GB) 파일용 이어받기 다운로더. Android 의존성 없음(JVM 단위 테스트 대상).
 *
 * - `.part` 파일에 이어 쓰고 Range 요청으로 재개. 서버가 Range 를 무시(200)하면 처음부터 다시 받는다.
 * - HF resolve URL 은 만료되는 서명 CDN URL 로 302 리다이렉트되므로, 재시도마다 원 URL 부터 다시 따라간다.
 * - 네트워크 오류 시 지수 백오프 재시도(무제한 대기 대신 [maxRetries]). [waitForNetwork] 로 연결 복구까지 대기 가능.
 * - 완료 후 SHA-256 검증, 불일치면 `.part` 삭제 후 실패.
 */
class ResumableDownloader(
    private val maxRetries: Int = 8,
    private val baseBackoffMs: Long = 2_000,
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 60_000,
    private val userAgent: String = "LingS25U/0.2",
    /** 재시도 전 호출: 네트워크가 돌아올 때까지 블록(예: ConnectivityManager). false 면 중단 */
    private val waitForNetwork: () -> Boolean = { true },
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    sealed interface Event {
        data class Progress(val done: Long, val total: Long, val bytesPerSec: Double) : Event
        data class Retrying(val attempt: Int, val delayMs: Long, val reason: String) : Event
        data class Verifying(val done: Long, val total: Long) : Event
    }

    class CancelledException : IOException("취소됨")

    @Volatile private var cancelled = false

    /** 인스턴스당 1회성. 취소 후 다시 받으려면 새 인스턴스를 만든다 */
    fun cancel() { cancelled = true }

    val isCancelled: Boolean get() = cancelled

    /**
     * @return 완성 파일. 실패 시 예외(IOException/IllegalStateException).
     */
    fun download(url: String, target: File, expectedSize: Long, sha256: String?, onEvent: (Event) -> Unit = {}): File {
        val part = File(target.path + ".part")
        target.parentFile?.mkdirs()

        var attempt = 0
        while (true) {
            if (cancelled) throw CancelledException()
            if (part.exists() && expectedSize > 0 && part.length() > expectedSize) part.delete() // 손상
            if (part.exists() && part.length() == expectedSize) break
            try {
                fetchOnce(url, part, expectedSize, onEvent)
                if (expectedSize <= 0 || part.length() == expectedSize) break
                throw IOException("스트림 조기 종료: ${part.length()}/$expectedSize")
            } catch (e: CancelledException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt > maxRetries) throw IOException("다운로드 실패(${attempt - 1}회 재시도): ${e.message}", e)
                val delay = (baseBackoffMs shl (attempt - 1).coerceAtMost(5)).coerceAtMost(60_000)
                onEvent(Event.Retrying(attempt, delay, e.message ?: e.javaClass.simpleName))
                if (!waitForNetwork()) throw CancelledException()
                sleeper(delay)
            }
        }

        if (sha256 != null) {
            val digest = sha256(part) { d -> onEvent(Event.Verifying(d, part.length())) }
            if (!digest.equals(sha256, ignoreCase = true)) {
                part.delete()
                throw IllegalStateException("SHA-256 불일치 → 파일 삭제 (다시 받기 필요)")
            }
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IOException("이름 변경 실패: ${part.name}")
        return target
    }

    private fun fetchOnce(url: String, part: File, expectedSize: Long, onEvent: (Event) -> Unit) {
        val have = if (part.exists()) part.length() else 0L
        var u = URL(url)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Encoding", "identity")
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("리다이렉트 Location 없음")
                conn.disconnect()
                if (++redirects > 10) throw IOException("리다이렉트 과다")
                u = URL(u, loc)
                continue
            }
            break
        }
        try {
            val code = conn.responseCode
            when {
                code == 416 -> {
                    // 요청 범위가 파일 끝을 넘음 = 이미 다 받았거나 .part 가 잘못됨
                    if (expectedSize > 0 && have == expectedSize) return
                    part.delete()
                    throw IOException("HTTP 416 (부분 파일 폐기 후 재시작)")
                }
                code == HttpURLConnection.HTTP_PARTIAL -> {
                    val cr = conn.getHeaderField("Content-Range").orEmpty() // bytes start-end/total
                    val start = Regex("bytes (\\d+)-").find(cr)?.groupValues?.get(1)?.toLongOrNull()
                    if (start != null && start != have) {
                        part.delete()
                        throw IOException("Content-Range 불일치($cr) → 처음부터")
                    }
                    copy(conn, part, append = true, startAt = have, total = expectedSize, onEvent)
                }
                code == HttpURLConnection.HTTP_OK -> copy(conn, part, append = false, startAt = 0, total = expectedSize, onEvent)
                code in 500..599 || code == 429 || code == 408 -> throw IOException("HTTP $code")
                else -> throw IllegalStateException("HTTP $code (복구 불가)")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun copy(conn: HttpURLConnection, part: File, append: Boolean, startAt: Long, total: Long, onEvent: (Event) -> Unit) {
        val buf = ByteArray(1 shl 20)
        var done = startAt
        var lastT = System.nanoTime()
        var lastDone = done
        conn.inputStream.use { input ->
            FileOutputStream(part, append).use { out ->
                while (true) {
                    if (cancelled) throw CancelledException()
                    val n = try {
                        input.read(buf)
                    } catch (e: InterruptedIOException) {
                        throw IOException("읽기 시간 초과", e)
                    }
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - lastT > 500_000_000L) {
                        onEvent(Event.Progress(done, total, (done - lastDone) * 1e9 / (now - lastT)))
                        lastT = now; lastDone = done
                    }
                }
                out.fd.sync()
            }
        }
        onEvent(Event.Progress(done, total, 0.0))
    }

    companion object {
        fun sha256(file: File, onProgress: (Long) -> Unit = {}): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(4 shl 20)
            var done = 0L
            var lastReport = 0L
            FileInputStream(file).use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    done += n
                    if (done - lastReport >= (128L shl 20)) { onProgress(done); lastReport = done }
                }
            }
            onProgress(done)
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
