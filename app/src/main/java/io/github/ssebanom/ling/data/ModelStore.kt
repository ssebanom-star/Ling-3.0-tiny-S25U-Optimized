package io.github.ssebanom.ling.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** 모델 파일 저장/다운로드(이어받기 + SHA-256 검증)/가져오기. 위치: 앱 전용 외부 저장소(권한 불필요) */
class ModelStore(private val context: Context) {

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val variant: String, val done: Long, val total: Long, val bytesPerSec: Double) : DownloadState
        data class Verifying(val variant: String, val done: Long, val total: Long) : DownloadState
        /** 네트워크 없음 또는 데이터(종량제) 네트워크라 Wi-Fi 대기 */
        data class WaitingNetwork(val variant: String, val done: Long, val total: Long, val reason: String) : DownloadState
        data class Retrying(val variant: String, val attempt: Int, val reason: String) : DownloadState
        data class Done(val variant: String) : DownloadState
        data class Failed(val variant: String, val error: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    @Volatile private var active: ResumableDownloader? = null

    /** 종량제(모바일 데이터) 네트워크에서도 받을지. 기본 false → Wi-Fi 등 비종량제 대기 */
    @Volatile var allowMetered: Boolean = false

    enum class NetState { NONE, METERED, UNMETERED }

    fun networkState(): NetState {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        // 권한/시스템 오류로 상태를 못 읽으면 막지 않고 진행(종량제 여부 불명 → UNMETERED 취급)
        val caps = try {
            cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetState.NONE
        } catch (e: SecurityException) {
            return NetState.UNMETERED
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetState.NONE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) NetState.UNMETERED else NetState.METERED
    }

    private fun networkOk(): Boolean = when (networkState()) {
        NetState.UNMETERED -> true
        NetState.METERED -> allowMetered
        NetState.NONE -> false
    }

    val dir: File get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

    fun fileFor(v: ModelVariant) = File(dir, v.fileName)

    fun listModels(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }?.sortedBy { it.name }.orEmpty()

    fun isComplete(v: ModelVariant): Boolean = fileFor(v).let { it.exists() && it.length() == v.sizeBytes }

    fun delete(file: File) {
        file.delete()
        File(file.path + ".part").delete()
    }

    fun cancelDownload() {
        active?.cancel()
    }

    /**
     * 이어받기 다운로드 + SHA-256 검증. 네트워크가 없거나(또는 허용 안 된 종량제) 끊기면 복구될 때까지 대기 후 재개.
     * 앱/프로세스가 죽어도 `.part` 가 남아 다음 호출에서 이어 받는다.
     */
    val isDownloading: Boolean get() = active != null

    suspend fun downloadModel(v: ModelVariant): Boolean = withContext(Dispatchers.IO) {
        if (active != null) return@withContext false // 동시 다운로드 금지(설치 흐름과 수동 다운로드 충돌 방지)
        val target = fileFor(v)
        val part = File(target.path + ".part")
        try {
            if (isComplete(v)) {
                _download.value = DownloadState.Done(v.id)
                return@withContext true
            }
            val free = dir.usableSpace
            val need = v.sizeBytes - (if (part.exists()) part.length() else 0L)
            if (free < need + 300L * 1024 * 1024) {
                throw IllegalStateException("저장 공간 부족: ${need / 1_000_000}MB 필요, 여유 ${free / 1_000_000}MB")
            }
            val dl = ResumableDownloader(
                maxRetries = 20,
                waitForNetwork = { waitForNetwork(v, part) },
            )
            active = dl
            if (!waitForNetwork(v, part)) throw ResumableDownloader.CancelledException()
            dl.download(v.url, target, v.sizeBytes, v.sha256) { ev ->
                _download.value = when (ev) {
                    is ResumableDownloader.Event.Progress -> DownloadState.Running(v.id, ev.done, ev.total, ev.bytesPerSec)
                    is ResumableDownloader.Event.Retrying -> DownloadState.Retrying(v.id, ev.attempt, ev.reason)
                    is ResumableDownloader.Event.Verifying -> DownloadState.Verifying(v.id, ev.done, ev.total)
                }
            }
            _download.value = DownloadState.Done(v.id)
            true
        } catch (e: ResumableDownloader.CancelledException) {
            _download.value = DownloadState.Failed(v.id, "일시정지됨")
            false
        } catch (e: Exception) {
            _download.value = DownloadState.Failed(v.id, e.message ?: e.toString())
            false
        } finally {
            active = null
        }
    }

    /** 네트워크 조건이 맞을 때까지 블록. 취소되면 false */
    private fun waitForNetwork(v: ModelVariant, part: File): Boolean {
        while (!networkOk()) {
            if (active?.isCancelled != false) return false
            val reason = if (networkState() == NetState.METERED) "Wi-Fi 연결 대기 중 (모바일 데이터 사용 안 함)" else "네트워크 연결 대기 중"
            _download.value = DownloadState.WaitingNetwork(v.id, if (part.exists()) part.length() else 0, v.sizeBytes, reason)
            Thread.sleep(3_000)
        }
        return true
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
        fun sha256(file: File, onProgress: (Long) -> Unit = {}): String = ResumableDownloader.sha256(file, onProgress)

        /** GGUF 매직 확인 */
        fun isGguf(file: File): Boolean = runCatching {
            FileInputStream(file).use { val b = ByteArray(4); it.read(b) == 4 && String(b) == "GGUF" }
        }.getOrDefault(false)
    }
}
