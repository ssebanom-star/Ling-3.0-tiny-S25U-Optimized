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
import io.github.ssebanom.ling.ui.LingTheme

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
        setContent { LingTheme { LingRoot(container) } }
    }
}
