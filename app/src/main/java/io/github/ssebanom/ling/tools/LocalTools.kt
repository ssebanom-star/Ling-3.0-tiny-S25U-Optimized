package io.github.ssebanom.ling.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 온디바이스 툴(네트워크 불필요). Ling-3.0 의 네이티브 툴 호출 포맷으로 노출된다.
 * spec 은 OpenAI 스타일 function 스키마(LinkedHashMap 으로 키 순서 고정 → 프롬프트 바이트 안정).
 */
interface LocalTool {
    val name: String
    val spec: Map<String, Any?>
    fun execute(args: Map<String, Any?>): String
}

private fun fn(name: String, description: String, properties: Map<String, Any?>, required: List<String>) =
    linkedMapOf<String, Any?>(
        "type" to "function",
        "function" to linkedMapOf<String, Any?>(
            "name" to name,
            "description" to description,
            "parameters" to linkedMapOf<String, Any?>(
                "type" to "object",
                "properties" to properties,
                "required" to required,
            ),
        ),
    )

class CalculatorTool : LocalTool {
    override val name = "calculator"
    override val spec = fn(
        name, "Evaluate an arithmetic expression. Supports + - * / % ^, parentheses, sqrt, sin, cos, tan, log, ln, exp, abs, pi, e.",
        linkedMapOf("expression" to linkedMapOf("type" to "string", "description" to "e.g. (17*23)+sqrt(2)")),
        listOf("expression"),
    )

    override fun execute(args: Map<String, Any?>): String {
        val expr = args["expression"]?.toString() ?: return "error: missing expression"
        return runCatching { ExprEval(expr).eval() }.fold(
            { v -> if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString() },
            { "error: ${it.message}" },
        )
    }
}

class TimeTool : LocalTool {
    override val name = "current_time"
    override val spec = fn(name, "Get the current local date, time and timezone of the device.", linkedMapOf(), emptyList())
    override fun execute(args: Map<String, Any?>): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE VV (xxx)"))
}

class DeviceStatusTool(private val context: Context) : LocalTool {
    override val name = "device_status"
    override val spec = fn(name, "Get battery level, charging state and thermal status of this phone.", linkedMapOf(), emptyList())
    override fun execute(args: Map<String, Any?>): String {
        val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val pm = context.getSystemService(PowerManager::class.java)
        val thermal = when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            else -> "emergency"
        }
        return "{\"battery_percent\": ${if (level >= 0) level * 100 / scale else -1}, \"charging\": $plugged, " +
            "\"battery_temp_c\": $temp, \"thermal_status\": \"$thermal\", \"power_save\": ${pm.isPowerSaveMode}}"
    }
}

class ToolRegistry(context: Context) {
    val tools: List<LocalTool> = listOf(CalculatorTool(), TimeTool(), DeviceStatusTool(context))
    val specs: List<Map<String, Any?>> get() = tools.map { it.spec }
    fun execute(name: String, args: Map<String, Any?>): String =
        tools.firstOrNull { it.name == name }?.let { runCatching { it.execute(args) }.getOrElse { e -> "error: ${e.message}" } }
            ?: "error: unknown tool '$name'"
}

/** 재귀 하강 수식 평가기 (eval/스크립트 엔진 미사용) */
class ExprEval(private val src: String) {
    private var pos = 0

    fun eval(): Double {
        val v = expr()
        skipWs()
        require(pos == src.length) { "unexpected '${src.substring(pos)}'" }
        return v
    }

    private fun skipWs() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
    private fun peek(): Char? { skipWs(); return src.getOrNull(pos) }
    private fun eat(c: Char): Boolean = if (peek() == c) { pos++; true } else false

    private fun expr(): Double {
        var v = term()
        while (true) v = when {
            eat('+') -> v + term()
            eat('-') -> v - term()
            else -> return v
        }
    }

    private fun term(): Double {
        var v = power()
        while (true) v = when {
            eat('*') || eat('×') -> v * power()
            eat('/') || eat('÷') -> v / power()
            eat('%') -> v % power()
            else -> return v
        }
    }

    private fun power(): Double {
        val b = unary()
        return if (eat('^')) Math.pow(b, power()) else b
    }

    private fun unary(): Double = when {
        eat('-') -> -unary()
        eat('+') -> unary()
        else -> atom()
    }

    private fun atom(): Double {
        if (eat('(')) { val v = expr(); require(eat(')')) { "missing )" }; return v }
        skipWs()
        val start = pos
        if (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) {
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' ||
                    ((src[pos] == '-' || src[pos] == '+') && (src[pos - 1] == 'e' || src[pos - 1] == 'E')))) pos++
            return src.substring(start, pos).toDouble()
        }
        while (pos < src.length && src[pos].isLetter()) pos++
        val id = src.substring(start, pos).lowercase()
        require(id.isNotEmpty()) { "unexpected '${src.getOrNull(pos)}'" }
        return when (id) {
            "pi" -> Math.PI
            "e" -> Math.E
            else -> {
                require(eat('(')) { "unknown identifier $id" }
                val a = expr()
                require(eat(')')) { "missing )" }
                when (id) {
                    "sqrt" -> Math.sqrt(a); "sin" -> Math.sin(a); "cos" -> Math.cos(a); "tan" -> Math.tan(a)
                    "log" -> Math.log10(a); "ln" -> Math.log(a); "exp" -> Math.exp(a); "abs" -> Math.abs(a)
                    else -> throw IllegalArgumentException("unknown function $id")
                }
            }
        }
    }
}
