package io.github.ssebanom.ling.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ExprEvalTest {
    private fun ev(s: String) = ExprEval(s).eval()

    @Test fun arithmetic() {
        assertEquals(391.0, ev("17 * 23"), 0.0)
        assertEquals(7.0, ev("1 + 2 * 3"), 0.0)
        assertEquals(9.0, ev("(1 + 2) * 3"), 0.0)
        assertEquals(512.0, ev("2^3^2"), 0.0)
        assertEquals(-4.0, ev("-2*2"), 0.0)
        assertEquals(1.5e3, ev("1.5e3"), 0.0)
        assertEquals(2.0, ev("sqrt(4)"), 1e-12)
        assertEquals(Math.PI, ev("pi"), 0.0)
    }

    @Test fun calculatorFormatsIntegers() = kotlinx.coroutines.runBlocking {
        assertEquals("391", CalculatorTool().execute(mapOf("expression" to "17*23")))
        assertEquals(true, CalculatorTool().execute(mapOf("expression" to "1+")).startsWith("error"))
    }
}
