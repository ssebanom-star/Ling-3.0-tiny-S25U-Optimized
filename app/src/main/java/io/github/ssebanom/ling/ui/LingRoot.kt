package io.github.ssebanom.ling.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.ssebanom.ling.AppContainer

private enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("대화", Icons.AutoMirrored.Outlined.Chat),
    MODELS("모델", Icons.Outlined.Download),
    PERF("성능", Icons.Outlined.Speed),
    SETTINGS("설정", Icons.Outlined.Settings),
}

@Composable
fun LingRoot(c: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT) }
    val settings by c.settings.flow.collectAsState(initial = null)
    var skipSetup by rememberSaveable { mutableStateOf(false) }
    val s = settings ?: return  // DataStore 첫 로드 전
    if (!s.setupDone && !skipSetup) {
        SetupScreen(c, onSkip = { skipSetup = true })
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            Tab.CHAT -> ChatScreen(c, m)
            Tab.MODELS -> ModelsScreen(c, m)
            Tab.PERF -> PerfScreen(c, m)
            Tab.SETTINGS -> SettingsScreen(c, m)
        }
    }
}
