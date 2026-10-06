package io.github.ssebanom.ling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.runtime.SetupManager.Step
import io.github.ssebanom.ling.service.LingService

/** 첫 실행 자동 설치 화면. 사용자는 기다리기만 하면 되고, 필요할 때만(데이터 요금·오류) 버튼이 나타난다. */
@Composable
fun SetupScreen(c: AppContainer, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    val st by c.setup.state.collectAsState()
    LaunchedEffect(Unit) {
        LingService.start(ctx)
        c.setup.start() // 멱등
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ling-3.0-tiny 준비", style = MaterialTheme.typography.headlineMedium)
            Text(
                "필요한 모든 것을 자동으로 설정합니다. 앱을 나가도 다운로드는 계속되고, 중단되면 다음 실행 때 이어서 받습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (st.variant.isNotEmpty()) {
                Text("모델: Ling-3.0-tiny ${st.variant} · ${st.variantReason}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))

            Step.entries.filter { it != Step.DONE }.forEach { step ->
                val done = st.step.ordinal > step.ordinal
                val current = st.step == step && st.step != Step.DONE
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        done -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        current && st.running && st.error == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Outlined.Circle, null, tint = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step.label, style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                }
                if (current) {
                    Column(Modifier.padding(start = 34.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        st.progress?.let { p ->
                            LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        }
                        if (step == Step.DOWNLOAD && st.bytesTotal > 0) {
                            val eta = st.etaSec
                            Text(
                                "%.2f / %.2f GB".format(st.bytesDone / 1e9, st.bytesTotal / 1e9) +
                                    (if (st.bytesPerSec > 0) " · %.1f MB/s".format(st.bytesPerSec / 1e6) else "") +
                                    (if (eta > 0) " · 약 ${eta / 60}분 ${eta % 60}초 남음" else ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (st.message.isNotEmpty()) Text(st.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (st.meteredBlocked) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("모바일 데이터에 연결되어 있습니다. 약 4.6GB 를 받아야 해서 Wi-Fi 연결을 기다리는 중입니다.")
                        OutlinedButton(onClick = { c.setup.allowMeteredAndContinue() }) { Text("모바일 데이터로 받기") }
                    }
                }
            }

            st.error?.let { err ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(onClick = { c.setup.start() }) { Text("다시 시도 (이어받기)") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (st.step != Step.DONE) {
                TextButton(onClick = onSkip) { Text("백그라운드로 계속 진행하고 앱 둘러보기") }
            }
        }
    }
}
