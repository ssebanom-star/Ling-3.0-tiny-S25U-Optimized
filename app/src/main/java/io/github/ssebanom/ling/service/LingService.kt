package io.github.ssebanom.ling.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.ssebanom.ling.LingApp
import io.github.ssebanom.ling.MainActivity
import io.github.ssebanom.ling.R
import io.github.ssebanom.ling.data.ModelStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 포그라운드 서비스: 생성/다운로드 중 프로세스가 LMK 로 죽지 않게 하고(4.6GB 재로드 방지),
 * 화면이 꺼져도 생성이 이어지도록 partial wake lock 을 잡는다. 유휴가 길면 모델을 언로드한다.
 */
class LingService : LifecycleService() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var idleJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val c = (application as LingApp).container
        ServiceCompat.startForeground(
            this, NOTIF_ID, build("대기 중", ""),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        lifecycleScope.launch {
            combine(c.chat.busyFlow, c.chat.state, c.models.download) { busy, st, dl -> Triple(busy, st, dl) }
                .collect { (busy, st, dl) ->
                    val downloading = dl is ModelStore.DownloadState.Running || dl is ModelStore.DownloadState.Verifying
                    if (busy) acquire() else release()
                    val text = when {
                        busy -> "생성 중 · ${st.streaming.tokens} tok · ${"%.1f".format(st.streaming.tps)} tok/s"
                        dl is ModelStore.DownloadState.Running -> "다운로드 ${dl.done * 100 / dl.total}%"
                        dl is ModelStore.DownloadState.Verifying -> "검증 중 ${dl.done * 100 / dl.total}%"
                        else -> "모델 대기 중"
                    }
                    notify(build("Ling-3.0-tiny", text))
                    if (busy || downloading) idleJob?.cancel() else scheduleIdleUnload()
                }
        }
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = lifecycleScope.launch {
            val c = (application as LingApp).container
            val minutes = c.settings.current().autoUnloadMinutes
            if (minutes <= 0) return@launch
            delay(minutes * 60_000L)
            c.inference.unload()
            stopSelf()
        }
    }

    private fun acquire() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ling:generate").apply { acquire(30 * 60_000L) }
    }

    private fun release() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "추론 상태", NotificationManager.IMPORTANCE_LOW))
    }

    private fun build(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ling)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    private fun notify(n: Notification) = getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)

    companion object {
        private const val CHANNEL = "ling_inference"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LingService::class.java)) }
        }
    }
}
