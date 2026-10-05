package io.github.ssebanom.ling

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
        setContent { LingTheme { LingRoot(container) } }
    }
}
