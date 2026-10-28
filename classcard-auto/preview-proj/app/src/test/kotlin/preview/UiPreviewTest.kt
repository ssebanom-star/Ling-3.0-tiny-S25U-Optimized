package preview

import android.content.SharedPreferences
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import net.classcardauto.mobile.AppStyle
import net.classcardauto.mobile.FloatingPanel
import net.classcardauto.mobile.R
import net.classcardauto.mobile.Screens
import org.junit.Rule
import org.junit.Test

class MemoryPrefs : SharedPreferences {
    private val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = map[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { map[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { map[key] = values }
        override fun putInt(key: String, value: Int) = apply { map[key] = value }
        override fun putLong(key: String, value: Long) = apply { map[key] = value }
        override fun putFloat(key: String, value: Float) = apply { map[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { map[key] = value }
        override fun remove(key: String) = apply { map.remove(key) }
        override fun clear() = apply { map.clear() }
        override fun commit() = true
        override fun apply() {}
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

class UiPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2600),
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.NORMAL,
    )

    private val noop: () -> Unit = {}

    @Test
    fun clockFormat() {
        org.junit.Assert.assertEquals("0:00", AppStyle.formatClock(0))
        org.junit.Assert.assertEquals("0:45", AppStyle.formatClock(45))
        org.junit.Assert.assertEquals("9:32", AppStyle.formatClock(572))
        org.junit.Assert.assertEquals("10:00", AppStyle.formatClock(600))
        org.junit.Assert.assertEquals("1:02:05", AppStyle.formatClock(3725))
        org.junit.Assert.assertEquals("0:00", AppStyle.formatClock(-5))
    }

    @Test
    fun homeReady() {
        val h = Screens.home(paparazzi.context, MemoryPrefs(), { }, { }, { }, { }, { })
        paparazzi.snapshot(h.root, "home_ready")
    }

    @Test
    fun homeNoService() {
        val h = Screens.home(paparazzi.context, MemoryPrefs(), { }, { }, { }, { }, { })
        h.render(Screens.Link.NO_SERVICE, null)
        paparazzi.snapshot(h.root, "home_no_service")
    }

    @Test
    fun homeWithFailureBanner() {
        val h = Screens.home(paparazzi.context, MemoryPrefs(), { }, { }, { }, { }, { })
        h.showFailure("방금 실행이 멈췄어요 · 제시어 없음")
        paparazzi.snapshot(h.root, "home_failure_banner")
    }

    private fun sampleRecords(): List<net.classcardauto.mobile.FailureLog.Record> {
        val now = System.currentTimeMillis()
        val a = net.classcardauto.mobile.FailureLog.Record()
        a.time = now - 90_000; a.code = "PROMPT_NOT_FOUND"; a.progress = "7 | 20"; a.phase = "test"; a.pace = "human"; a.cards = 20
        val b = net.classcardauto.mobile.FailureLog.Record()
        b.time = now - 3_600_000; b.code = "CHOICE_AMBIGUOUS"; b.progress = "12 | 40"; b.phase = "test"; b.pace = "fast"; b.cards = 40
        val c = net.classcardauto.mobile.FailureLog.Record()
        c.time = now - 86_400_000; c.code = "UNCLASSIFIED"; c.message = "화면 전환 시간이 초과되었습니다"; c.phase = "intro"; c.pace = "fast"
        return listOf(a, b, c)
    }

    @Test
    fun failuresList() {
        val v = Screens.failures(paparazzi.context, sampleRecords(), { }, { }, { })
        paparazzi.snapshot(v, "failures_list")
    }

    @Test
    fun failuresEmpty() {
        val v = Screens.failures(paparazzi.context, emptyList(), { }, { }, { })
        paparazzi.snapshot(v, "failures_empty")
    }

    @Test
    fun homeRunning() {
        val h = Screens.home(paparazzi.context, MemoryPrefs(), { }, { }, { }, { }, { })
        h.render(Screens.Link.RUNNING, "3 | 20 · 2개 입력", 572)
        paparazzi.snapshot(h.root, "home_running")
    }

    @Test
    fun settings() {
        val help = listOf(
            Screens.Nav("접근성 설정", "‘단어 테스트 도우미’ 권한을 켜거나 끕니다") { },
            Screens.Nav("사용 안내 다시 보기", null) { },
            Screens.Nav("약관 동의 기록", "동의 시각 확인 · 동의 철회") { },
            Screens.Nav("개인정보처리방침", null) { },
            Screens.Nav("최근 실행", "마지막 실행의 결과와 오류 요약") { },
        )
        val v = Screens.settings(paparazzi.context, MemoryPrefs(), "0.6.14-dev", { }, help)
        paparazzi.snapshot(v, "settings")
    }

    @Test
    fun password() {
        val p = Screens.password(paparazzi.context, { }, { })
        p.error.text = "비밀번호가 맞지 않습니다. 다시 입력해주세요."
        paparazzi.snapshot(p.root, "password")
    }

    @Test
    fun activation() {
        val a = Screens.activation(
            paparazzi.context, "3f9a1c2b7d4e8a60",
            "내장 메모리/Android/data/net.classcardauto.mobile.nocert/files/auth",
            { }, { }, { }, { },
        )
        a.status.text = "classcard-auth.key: 다른 휴대폰(또는 다른 앱)용으로 발급된 파일이에요."
        paparazzi.snapshot(a.root, "activation")
    }

    @Test
    fun consentStep1() {
        val c = Screens.consent(paparazzi.context, false, "이 앱은 사용자가 시작한 실행 동안 선택한 화면의 글자를 읽고 필요한 화면 전환을 대신 수행합니다.\n\n1. 연습·허용된 화면에서만 사용하세요.\n2. 수집한 자료는 기기 안에서만 처리하고 종료 시 삭제합니다.\n3. 언제든 접근성 설정에서 끌 수 있습니다.", "접근성 권한으로 화면을 읽고 탭·스와이프를 수행합니다.", { }, { }, { })
        paparazzi.snapshot(c.root, "consent_1")
    }

    @Test
    fun consentStep2() {
        val c = Screens.consent(paparazzi.context, true, "", "접근성 권한으로 선택한 앱의 화면 텍스트를 읽고 탭과 스와이프를 수행합니다. 실행 중에만 동작하며 언제든 중지할 수 있습니다.", { }, { }, { })
        paparazzi.snapshot(c.root, "consent_2")
    }

    private fun overBackdrop(panel: FloatingPanel): View {
        val frame = FrameLayout(paparazzi.context)
        frame.setBackgroundColor(Color.rgb(236, 238, 240))
        val lp = FrameLayout.LayoutParams(-2, -2)
        lp.leftMargin = AppStyle.INSTANCE.dp(paparazzi.context, 120)
        lp.topMargin = AppStyle.INSTANCE.dp(paparazzi.context, 60)
        frame.addView(panel, lp)
        return frame
    }

    private fun host() = object : FloatingPanel.Host {
        override fun onPrimary() {}
        override fun onHide() {}
        override fun onDrag(dx: Float, dy: Float, finished: Boolean) {}
        override fun isBlocked() = false
        override fun onPause() {}
        override fun onPanelTouch(eventTime: Long) {}
        override fun onTurnOff() {}
        override fun isDockedRight() = true
        override fun onLayoutChanged() {}
    }

    @Test
    fun appIcon() {
        val ctx = paparazzi.context
        val row = android.widget.LinearLayout(ctx)
        row.setBackgroundColor(Color.WHITE)
        row.setPadding(40, 40, 40, 40)
        // the adaptive icon is previewed as its two layers (layoutlib cannot draw AdaptiveIconDrawable here)
        val mark = android.widget.ImageView(ctx)
        mark.setImageResource(R.drawable.app_mark)
        row.addView(mark, android.widget.LinearLayout.LayoutParams(300, 300).also { it.rightMargin = 40 })
        val adaptive = android.widget.ImageView(ctx)
        adaptive.setBackgroundResource(R.drawable.ic_launcher_background)
        adaptive.setImageResource(R.drawable.ic_launcher_foreground)
        row.addView(adaptive, android.widget.LinearLayout.LayoutParams(300, 300))
        paparazzi.snapshot(row, "app_icon")
    }

    @Test
    fun iconIdeas() {
        val ctx = paparazzi.context
        val grid = android.widget.GridLayout(ctx)
        grid.columnCount = 4
        grid.setBackgroundColor(Color.WHITE)
        grid.setPadding(20, 20, 20, 20)
        val ids = intArrayOf(R.drawable.icon_idea_1, R.drawable.icon_idea_2, R.drawable.icon_idea_3, R.drawable.icon_idea_4,
            R.drawable.icon_idea_5, R.drawable.icon_idea_6, R.drawable.icon_idea_7, R.drawable.icon_idea_8)
        for (id in ids) {
            val v = android.widget.ImageView(ctx)
            v.setImageResource(id)
            val lp = android.widget.GridLayout.LayoutParams()
            lp.width = 240
            lp.height = 240
            lp.setMargins(10, 10, 10, 10)
            grid.addView(v, lp)
        }
        paparazzi.snapshot(grid, "icon_ideas")
    }

    @Test
    fun iconPencil() {
        val ctx = paparazzi.context
        val row = android.widget.LinearLayout(ctx)
        row.setBackgroundColor(Color.WHITE)
        row.setPadding(20, 20, 20, 20)
        for (id in intArrayOf(R.drawable.icon_idea_9, R.drawable.icon_idea_10)) {
            val v = android.widget.ImageView(ctx)
            v.setImageResource(id)
            row.addView(v, android.widget.LinearLayout.LayoutParams(360, 360).also { it.rightMargin = 20 })
        }
        paparazzi.snapshot(row, "icon_pencil")
    }

    @Test
    fun overlayStartOnly() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(false, "", true, false)
        p.setMinimized(true)
        paparazzi.snapshot(overBackdrop(p), "overlay_start_only")
    }

    @Test
    fun overlayCollectingWithHide() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "단어 수집 12개", true, true)
        p.setRemaining(572)
        p.renderRun(false, 0, 2, false, true, 0)
        paparazzi.snapshot(overBackdrop(p), "overlay_collecting_hide")
    }

    @Test
    fun overlayCollapsedTab() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(false, "", true, false)
        p.setCollapsed(true)
        paparazzi.snapshot(overBackdrop(p), "overlay_collapsed_tab")
    }

    @Test
    fun overlayIdle() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(false, "", true, false)
        paparazzi.snapshot(overBackdrop(p), "overlay_idle")
    }

    @Test
    fun overlayCollecting() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "단어 수집 12개", true, true)
        p.setRemaining(572)
        paparazzi.snapshot(overBackdrop(p), "overlay_collecting")
    }

    @Test
    fun overlayTestingWithTouches() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "3/20 · 2개 입력", false, false)
        p.setRemaining(300)
        p.renderRun(false, 1, 2, true)
        paparazzi.snapshot(overBackdrop(p), "overlay_testing_touch1")
    }

    @Test
    fun overlayLocked() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "3/20 · 2개 입력", false, false)
        p.setRemaining(300)
        p.renderRun(false, 0, 2, true, true, 3)
        paparazzi.snapshot(overBackdrop(p), "overlay_locked")
    }

    @Test
    fun overlayCompact() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "3/20 · 2개 입력", false, false)
        p.setRemaining(300)
        p.renderRun(false, 0, 2, true, true, 3)
        paparazzi.snapshot(overBackdrop(p), "overlay_compact")
    }

    @Test
    fun overlayCompactPaused() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "일시정지", false, false)
        p.setRemaining(45)
        p.renderRun(true, 2, 2, true, false, 0)
        paparazzi.snapshot(overBackdrop(p), "overlay_compact_paused")
    }

    @Test
    fun overlayPaused() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "일시정지됨 · 직접 터치 2회", false, false)
        p.setRemaining(300)
        p.renderRun(true, 2, 2, true)
        paparazzi.snapshot(overBackdrop(p), "overlay_paused")
    }

    @Test
    fun homePaused() {
        val h = Screens.home(paparazzi.context, MemoryPrefs(), { }, { }, { }, { }, { })
        h.render(Screens.Link.PAUSED, "일시정지됨 · 직접 터치 2회", 300, "직접 터치 2회. 직접 조작한 뒤 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요.")
        paparazzi.snapshot(h.root, "home_paused")
    }

    @Test
    fun overlayTesting() {
        val p = FloatingPanel(paparazzi.context, MemoryPrefs(), host())
        p.render(true, "3/20 · 2개 입력", false, false)
        p.setRemaining(45)
        paparazzi.snapshot(overBackdrop(p), "overlay_testing")
    }
}
