package io.github.ssebanom.ling.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 모델 파일 저장/다운로드(이어받기 + SHA-256 검증)/가져오기. 위치: 앱 전용 외부 저장소(권한 불필요) */
class ModelStore(private val context: Context) {

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val variant: String, val done: Long, val total: Long, val bytesPerSec: Double) : DownloadState
        data class Verifying(val variant: String, val done: Long, val total: Long) : DownloadState
        data class Done(val variant: String) : DownloadState
        data class Failed(val variant: String, val error: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    @Volatile private var cancelRequested = false

    val dir: File get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

    fun fileFor(v: ModelVariant) = File(dir, v.fileName)

    fun listModels(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }?.sortedBy { it.name }.orEmpty()

    fun isComplete(v: ModelVariant): Boolean = fileFor(v).let { it.exists() && it.length() == v.sizeBytes }

    fun delete(file: File) {
        file.delete()
        File(file.path + ".part").delete()
    }

    fun cancelDownload() {
        cancelRequested = true
    }

    /** 이어받기 지원 다운로드. 완료 후 SHA-256 검증, 불일치 시 파일 삭제. */
    suspend fun downloadModel(v: ModelVariant): Boolean = withContext(Dispatchers.IO) {
        cancelRequested = false
        val target = fileFor(v)
        val part = File(target.path + ".part")
        try {
            val free = dir.usableSpace
            val need = v.sizeBytes - (if (part.exists()) part.length() else 0L)
            if (free < need + 200L * 1024 * 1024) {
                throw IllegalStateException("저장 공간 부족: 필요 ${need / 1_000_000}MB, 여유 ${free / 1_000_000}MB")
            }
            var url = URL(v.url)
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "LingS25U/0.1")
                    if (part.exists() && part.length() > 0) setRequestProperty("Range", "bytes=${part.length()}-")
                }
                val code = conn.responseCode
                if (code in 300..399 && redirects++ < 8) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                    continue
                }
                break
            }
            val code = conn.responseCode
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !append) throw IllegalStateException("HTTP $code")
            if (!append) part.delete()

            var done = if (append) part.length() else 0L
            val buf = ByteArray(1 shl 20)
            var lastT = System.nanoTime()
            var lastDone = done
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    while (true) {
                        if (cancelRequested) throw InterruptedException("취소됨")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastT > 500_000_000L) {
                            val bps = (done - lastDone) * 1e9 / (now - lastT)
                            _download.value = DownloadState.Running(v.id, done, v.sizeBytes, bps)
                            lastT = now; lastDone = done
                        }
                    }
                }
            }
            conn.disconnect()
            if (part.length() != v.sizeBytes) throw IllegalStateException("크기 불일치: ${part.length()} != ${v.sizeBytes}")

            val digest = sha256(part) { d -> _download.value = DownloadState.Verifying(v.id, d, v.sizeBytes) }
            if (!digest.equals(v.sha256, ignoreCase = true)) {
                part.delete()
                throw IllegalStateException("SHA-256 불일치 (파일 삭제됨)")
            }
            if (!part.renameTo(target)) throw IllegalStateException("이름 변경 실패")
            _download.value = DownloadState.Done(v.id)
            true
        } catch (e: Exception) {
            _download.value = DownloadState.Failed(v.id, e.message ?: e.toString())
            false
        }
    }

    /** SAF 로 고른 GGUF 를 모델 폴더로 복사 */
    suspend fun importFrom(uri: Uri, displayName: String, onProgress: (Long) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val name = if (displayName.endsWith(".gguf")) displayName else "$displayName.gguf"
        val out = File(dir, name)
        context.contentResolver.openInputStream(uri)!!.use { input -> copy(input, out, onProgress) }
        out
    }

    private fun copy(input: InputStream, out: File, onProgress: (Long) -> Unit) {
        FileOutputStream(out).use { o ->
            val buf = ByteArray(1 shl 20)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                o.write(buf, 0, n)
                total += n
                onProgress(total)
            }
        }
    }

    companion object {
        fun sha256(file: File, onProgress: (Long) -> Unit = {}): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(4 shl 20)
            var done = 0L
            FileInputStream(file).use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    done += n
                    if (done % (256L shl 20) < n) onProgress(done)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** GGUF 매직 확인 */
        fun isGguf(file: File): Boolean = runCatching {
            FileInputStream(file).use { val b = ByteArray(4); it.read(b) == 4 && String(b) == "GGUF" }
        }.getOrDefault(false)
    }
}
