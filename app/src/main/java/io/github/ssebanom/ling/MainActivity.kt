package io.github.ssebanom.ling

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.ssebanom.ling.service.LingService
import io.github.ssebanom.ling.ui.LingRoot
import io.github.ssebanom.ling.ui.theme.LingTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        LingService.start(this)
        val container = (application as LingApp).container
        // 첫 실행(또는 중단된 설치): 다운로드→검증→로드→튜닝을 자동으로 이어서 진행
        lifecycleScope.launch {
            if (!container.settings.current().setupDone) container.setup.start()
        }
        setContent {
            val s by container.settings.flow.collectAsState(initial = null)
            LingTheme(themeMode = s?.themeMode ?: 0, dynamicColor = s?.dynamicColor ?: false) { LingRoot(container) }
        }
    }

    // 툴 승인: 앱이 보이면 다이얼로그, 아니면 접근성 오버레이
    override fun onStart() {
        super.onStart()
        (application as LingApp).container.approvals.uiVisible = true
    }

    override fun onStop() {
        (application as LingApp).container.approvals.uiVisible = false
        super.onStop()
    }
}
