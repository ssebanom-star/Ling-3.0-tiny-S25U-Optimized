package io.github.ssebanom.ling.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.ssebanom.ling.data.MessageStats
import io.github.ssebanom.ling.data.StoredMessage
import io.github.ssebanom.ling.domain.ChatMessage
import io.github.ssebanom.ling.domain.Role
import io.github.ssebanom.ling.domain.ToolCall
import io.github.ssebanom.ling.runtime.Phase
import io.github.ssebanom.ling.runtime.Streaming
import io.github.ssebanom.ling.ui.chat.AssistantTurn
import io.github.ssebanom.ling.ui.chat.ChatRow
import io.github.ssebanom.ling.ui.chat.UserBubble
import io.github.ssebanom.ling.ui.theme.LingTheme
import org.junit.Before
import org.junit.Rule
import ru.noties.jlatexmath.JLatexMathAndroid
import org.junit.Test

class ChatShots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6_PRO.copy(screenHeight = 3600), maxPercentDifference = 100.0)

    private fun sm(role: Role, content: String, reasoning: String? = null, calls: List<ToolCall> = emptyList(), stats: MessageStats? = null) =
        StoredMessage(0, ChatMessage(role, content, reasoning = reasoning, toolCalls = calls), stats)

    private val answer = """
## 적분 결과

부분적분을 두 번 쓰면 \(\int x^2 e^x dx = e^x(x^2 - 2x + 2) + C\) 이므로

$$\int_0^1 x^2 e^x\,dx = e - 2 \approx 0.718$$

| 단계 | 내용 | 결과 |
|---|---|---|
| 1 | u = x², dv = eˣdx | x²eˣ − ∫2xeˣ |
| 2 | u = 2x | 2xeˣ − 2eˣ |

- [x] **검산** 완료
- [ ] ~~수치 적분~~ 생략

```python
from scipy.integrate import quad
import math
# 수치 검증
print(quad(lambda x: x**2 * math.exp(x), 0, 1))  # 0.718...
```

> 참고: `e − 2` 는 약 0.71828 입니다.
""".trimIndent()

    private val msgs = listOf(
        sm(Role.USER, "∫₀¹ x² eˣ dx 를 단계별로 풀고 검색으로 확인해줘"),
        sm(Role.ASSISTANT, "", reasoning = "The user wants the integral. Integration by parts twice...",
            calls = listOf(
                ToolCall("web_search", linkedMapOf<String, Any?>("query" to "integral x^2 e^x from 0 to 1")),
                ToolCall("calculator", linkedMapOf<String, Any?>("expression" to "e - 2")),
            )),
        sm(Role.TOOL, "Search results for \"integral x^2 e^x from 0 to 1\" (via DuckDuckGo):\n1. Integral of x^2 e^x - Symbolab\n   https://www.symbolab.com/solver/integral-calculator\n   Step-by-step: e^x(x^2-2x+2)+C\n2. Wolfram|Alpha: integrate x^2 e^x\n   https://www.wolframalpha.com/input?i=integrate+x%5E2+e%5Ex\n   Result: e - 2 ≈ 0.71828\nUse fetch_url on a result URL to read the page."),
        sm(Role.TOOL, "0.718281828459045"),
        sm(Role.ASSISTANT, answer, stats = MessageStats(512, 300, 400.0, 220, 7000.0, 520.0)),
    )

    @Composable
    private fun Screen() {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            UserBubble(msgs[0], busy = false) {}
            AssistantTurn(ChatRow.Turn("t", msgs.drop(1), live = false, isLast = true), "Ling-3.0-tiny", null, false) {}
            UserBubble(sm(Role.USER, "고마워! 다음은 파일 정리"), busy = true) {}
            AssistantTurn(
                ChatRow.Turn("t2", listOf(sm(Role.ASSISTANT, "", calls = listOf(ToolCall("list_files", linkedMapOf<String, Any?>("path" to "/Download"))))), live = true, isLast = true),
                "Ling-3.0-tiny",
                Streaming(phase = Phase.TOOL, toolName = "list_files"),
                true,
            ) {}
        }
    }

    @Before fun initLatex() = JLatexMathAndroid.init(paparazzi.context)

    @Test fun chatLight() = paparazzi.snapshot { LingTheme(themeMode = 1) { Screen() } }
    @Test fun chatDark() = paparazzi.snapshot { LingTheme(themeMode = 2) { Screen() } }
}
