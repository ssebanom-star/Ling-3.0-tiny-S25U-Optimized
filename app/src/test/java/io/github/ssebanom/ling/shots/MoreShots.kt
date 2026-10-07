package io.github.ssebanom.ling.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.ssebanom.ling.data.MessageStats
import io.github.ssebanom.ling.data.StatPoint
import io.github.ssebanom.ling.data.ToolRun
import io.github.ssebanom.ling.data.ToolStatus
import io.github.ssebanom.ling.ui.ByToolChart
import io.github.ssebanom.ling.ui.DailyChart
import io.github.ssebanom.ling.ui.SpeedChart
import io.github.ssebanom.ling.ui.Summary
import io.github.ssebanom.ling.ui.chat.Composer
import io.github.ssebanom.ling.ui.chat.EmptyState
import io.github.ssebanom.ling.ui.chat.ThinkingCard
import io.github.ssebanom.ling.ui.theme.LingTheme
import io.github.ssebanom.ling.ui.tools.ToolRunCard
import org.junit.Rule
import org.junit.Test

class MoreShots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6_PRO.copy(screenHeight = 3400), maxPercentDifference = 100.0)

    @Composable
    private fun Page(content: @Composable () -> Unit) = LingTheme(themeMode = 2) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
        }
    }

    @Test fun homeAndTools() = paparazzi.snapshot {
        Page {
            Box(Modifier.height(560.dp)) { EmptyState("Ling-3.0-tiny", toolsOn = true) {} }
            Composer(false, true, true, 9, {}, {}, {}, {})
        }
    }

    @Test fun toolCards() = paparazzi.snapshot {
        Page {
            ThinkingCard("The user asks about the capital. Let me recall facts about Seoul and population and compute 17*23=391 carefully step by step.", live = true, elapsedSec = 7)
            ToolRunCard(
                "web_search", linkedMapOf("query" to "서울 인구 2026"),
                "Search results for \"서울 인구 2026\" (via DuckDuckGo):\n1. 서울특별시 인구 통계 - 서울 열린데이터광장\n   https://data.seoul.go.kr/dataList/10584\n   2026년 9월 기준 주민등록인구 932만명...\n2. Seoul - Wikipedia\n   https://en.wikipedia.org/wiki/Seoul\n   Seoul is the capital and largest city of South Korea.\nUse fetch_url on a result URL to read the page.",
                initiallyOpen = true,
            )
            ToolRunCard(
                "list_files", linkedMapOf("path" to "/Download"),
                "/Download (3 items)\n[dir]  papers/\n[file] report.pdf  2.1 MB  2026-10-01 12:00\n[file] notes.txt  4 KB  2026-10-05 09:12",
                initiallyOpen = true, durationMs = 42,
            )
            ToolRunCard("device_status", linkedMapOf(), "{\"battery_percent\": 81, \"charging\": false, \"thermal\": \"light\"}", initiallyOpen = true, durationMs = 8)
            ToolRunCard("tap", linkedMapOf("index" to "3"), "error: the user declined this action")
        }
    }

    private val now = System.currentTimeMillis()
    private val runs = buildList {
        val tools = listOf("web_search" to "WEB", "fetch_url" to "WEB", "calculator" to "BASIC", "list_files" to "FILES", "open_app" to "DEVICE", "read_screen" to "SCREEN")
        var id = 1L
        for (d in 0 until 14) for (k in 0 until (d * 7 % 5 + 1)) {
            val (t, g) = tools[(d + k) % tools.size]
            val st = if ((d + k) % 7 == 0) ToolStatus.ERROR else if ((d + k) % 11 == 0) ToolStatus.DENIED else ToolStatus.OK
            add(ToolRun(id++, 1, t, g, linkedMapOf("query" to "x"), "ok", st, now - d * 86_400_000L - k * 60_000L, (200L + (d * 97 + k * 31) % 2500)))
        }
    }

    @Test fun activityCharts() = paparazzi.snapshot {
        Page {
            Summary(runs)
            ByToolChart(runs)
            DailyChart(runs)
            SpeedChart((0 until 30).map { i ->
                StatPoint(now, "m", MessageStats(500, 0, 300.0, 200, 200 * 1000.0 / (28 + (i * 7 % 9)), 300.0 + (i * 53 % 400)))
            })
        }
    }
}
