package io.github.ssebanom.ling.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 화면 제어 툴의 실행 주체. 사용자가 시스템 설정 → 접근성에서 켜야 연결된다.
 * - 화면 읽기: 보이는 노드 중 텍스트/설명이 있거나 상호작용 가능한 것만 번호를 매겨 덤프
 * - 동작: 번호(마지막 읽기 기준)로 탭/길게 누르기/입력/스크롤, 전역 키(뒤로·홈·최근 앱·알림)
 * - 승인 오버레이: 앱이 백그라운드여도 TYPE_ACCESSIBILITY_OVERLAY 로 허용/거부를 묻는다(추가 권한 불필요)
 */
class LingAccessibilityService : AccessibilityService() {

    data class Item(val node: AccessibilityNodeInfo, val line: String)

    @Volatile private var items: List<Item> = emptyList()
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    // ---------------- 화면 읽기 ----------------

    fun readScreen(maxItems: Int = 150): String {
        val root = rootInActiveWindow ?: return "error: no active window (screen off or secure window)"
        val out = ArrayList<Item>()
        walk(root, out, 0, maxItems)
        items = out
        val pkg = root.packageName?.toString().orEmpty()
        return buildString {
            append("app: ").append(pkg).append('\n')
            if (out.isEmpty()) append("(no readable elements)")
            out.forEachIndexed { i, it -> append('[').append(i).append("] ").append(it.line).append('\n') }
        }
    }

    private fun walk(n: AccessibilityNodeInfo, out: MutableList<Item>, depth: Int, max: Int) {
        if (out.size >= max || depth > 40 || !n.isVisibleToUser) return
        val text = n.text?.toString()?.trim().orEmpty()
        val desc = n.contentDescription?.toString()?.trim().orEmpty()
        val hint = n.hintText?.toString()?.trim().orEmpty()
        val interactive = n.isClickable || n.isLongClickable || n.isEditable || n.isScrollable || n.isCheckable
        if (text.isNotEmpty() || desc.isNotEmpty() || (interactive && n.isEnabled)) {
            val flags = buildList {
                if (n.isClickable) add("click")
                if (n.isEditable) add("edit")
                if (n.isScrollable) add("scroll")
                if (n.isCheckable) add(if (n.isChecked) "checked" else "unchecked")
                if (n.isSelected) add("selected")
                if (!n.isEnabled) add("disabled")
            }
            val cls = n.className?.toString()?.substringAfterLast('.').orEmpty()
            val label = buildString {
                append(cls)
                if (text.isNotEmpty()) append(" \"").append(text.take(120)).append('"')
                if (desc.isNotEmpty() && desc != text) append(" (").append(desc.take(80)).append(')')
                if (n.isEditable && text.isEmpty() && hint.isNotEmpty()) append(" hint=\"").append(hint.take(60)).append('"')
                if (flags.isNotEmpty()) append(" {").append(flags.joinToString(",")).append('}')
            }
            out += Item(n, label)
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            walk(c, out, depth + 1, max)
            if (out.size >= max) return
        }
    }

    private fun item(index: Int): AccessibilityNodeInfo {
        val it = items.getOrNull(index) ?: error("no element [$index]; call read_screen first")
        it.node.refresh()
        return it.node
    }

    fun findByText(query: String): Int? {
        val q = query.trim().lowercase()
        if (items.isEmpty()) readScreen()
        return items.indexOfFirst { it.line.lowercase().contains("\"$q\"") }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.line.lowercase().contains(q) }.takeIf { it >= 0 }
    }

    // ---------------- 동작 ----------------

    private fun clickableAncestor(n: AccessibilityNodeInfo, long: Boolean): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops < 6) {
            if (if (long) cur.isLongClickable else cur.isClickable) return cur
            cur = cur.parent
            hops++
        }
        return null
    }

    suspend fun tap(index: Int, long: Boolean = false): String {
        val n = item(index)
        val target = clickableAncestor(n, long)
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        if (target != null && target.performAction(action)) return "ok"
        // 노드 동작이 안 되면 좌표 제스처
        val r = Rect().also { n.getBoundsInScreen(it) }
        return if (gesture(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), if (long) 700 else 60)) "ok (gesture)" else "error: tap failed"
    }

    fun setText(index: Int, text: String): String {
        val n = item(index)
        if (!n.isEditable) return "error: element [$index] is not editable"
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "ok" else "error: set text failed"
    }

    /** 입력창에서 엔터(검색/전송). API 30+ IME 동작 */
    fun submit(index: Int): Boolean {
        val n = item(index)
        return n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    suspend fun scroll(direction: String, index: Int?): String {
        val node = index?.let { item(it) } ?: items.firstOrNull { it.node.isScrollable }?.node?.also { it.refresh() }
        val forward = direction.lowercase() in setOf("down", "right", "forward", "next")
        if (node != null) {
            var cur: AccessibilityNodeInfo? = node
            while (cur != null && !cur.isScrollable) cur = cur.parent
            val act = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (cur != null && cur.performAction(act)) return "ok"
        }
        // 스와이프 제스처(화면 중앙)
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val (y1, y2) = if (forward) dm.heightPixels * 0.7f to dm.heightPixels * 0.3f else dm.heightPixels * 0.3f to dm.heightPixels * 0.7f
        return if (gesture(x, y1, x, y2, 350)) "ok (swipe)" else "error: scroll failed"
    }

    fun global(key: String): String {
        val a = when (key.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent_apps" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock_screen" -> GLOBAL_ACTION_LOCK_SCREEN
            else -> return "error: unknown key '$key' (back, home, recents, notifications, quick_settings, lock_screen)"
        }
        return if (performGlobalAction(a)) "ok" else "error: action failed"
    }

    private suspend fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }

    // ---------------- 승인 오버레이 ----------------

    /** 화면 위에 허용/거부 창을 띄우고 결과를 기다린다 */
    suspend fun confirmOverlay(title: String, detail: String): Boolean {
        val result = CompletableDeferred<Boolean>()
        val wm = getSystemService(WindowManager::class.java)
        var view: LinearLayout? = null
        main.post {
            val pad = (16 * resources.displayMetrics.density).toInt()
            val v = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(Color.argb(240, 32, 33, 36))
                addView(TextView(context).apply { text = title; setTextColor(Color.WHITE); textSize = 16f })
                addView(TextView(context).apply { text = detail; setTextColor(Color.LTGRAY); textSize = 14f; setPadding(0, pad / 2, 0, pad / 2) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    addView(Button(context).apply { text = "거부"; setOnClickListener { result.complete(false) } })
                    addView(Button(context).apply { text = "허용"; setOnClickListener { result.complete(true) } })
                })
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.BOTTOM }
            runCatching { wm.addView(v, lp); view = v }.onFailure { result.complete(false) }
        }
        return try {
            result.await()
        } finally {
            main.post { view?.let { runCatching { wm.removeView(it) } } }
        }
    }

    companion object {
        @Volatile var instance: LingAccessibilityService? = null
            private set
    }
}
