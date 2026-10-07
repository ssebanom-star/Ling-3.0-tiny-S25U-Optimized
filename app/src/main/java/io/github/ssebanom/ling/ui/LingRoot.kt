package io.github.ssebanom.ling.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.ssebanom.ling.AppContainer
import io.github.ssebanom.ling.tools.ToolApprovals
import io.github.ssebanom.ling.ui.chat.ChatScreen
import io.github.ssebanom.ling.ui.theme.MonoSmall
import io.github.ssebanom.ling.ui.tools.ToolMetas

/** 앱 골격: 대화가 홈, 나머지 화면은 드로어에서 이동(슬라이드 전환) */
@Composable
fun LingRoot(c: AppContainer) {
    val settings by c.settings.flow.collectAsState(initial = null)
    var skipSetup by rememberSaveable { mutableStateOf(false) }
    val s = settings ?: return // DataStore 첫 로드 전
    if (!s.setupDone && !skipSetup) {
        SetupScreen(c, onSkip = { skipSetup = true })
        return
    }
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    ToolApprovalDialog(c)
    NavHost(
        navController = nav,
        startDestination = "chat",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320)) + fadeIn(tween(320)) },
        exitTransition = { fadeOut(tween(200)) + scaleOut(targetScale = 0.96f, animationSpec = tween(320)) },
        popEnterTransition = { fadeIn(tween(320)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320)) + fadeOut(tween(320)) },
    ) {
        composable("chat") { ChatScreen(c) { route -> nav.navigate(route) { launchSingleTop = true } } }
        composable("models") { ModelsScreen(c, back) }
        composable("activity") { ActivityScreen(c, back, openChat = { id -> c.chat.openConversation(id); nav.popBackStack("chat", false) }) }
        composable("perf") { PerfScreen(c, back) }
        composable("settings") { SettingsScreen(c, back) }
    }
}

@Composable
private fun ToolApprovalDialog(c: AppContainer) {
    val req by c.approvals.pending.collectAsState()
    val r = req ?: return
    AlertDialog(
        onDismissRequest = { c.approvals.respond(r, ToolApprovals.Decision.DENY) },
        icon = { Icon(Icons.Outlined.Shield, null) },
        title = { Text("${ToolMetas.of(r.tool).label} 실행할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("모델이 기기에서 다음 동작을 요청했습니다.", style = MaterialTheme.typography.bodyMedium)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Text(r.summary, Modifier.padding(10.dp), style = MonoSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { c.approvals.respond(r, ToolApprovals.Decision.ALLOW) }) { Text("허용") }
        },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = { c.approvals.respond(r, ToolApprovals.Decision.ALWAYS) }) { Text("이번 실행 동안 항상 허용") }
                TextButton(onClick = { c.approvals.respond(r, ToolApprovals.Decision.DENY) }) { Text("거부", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
