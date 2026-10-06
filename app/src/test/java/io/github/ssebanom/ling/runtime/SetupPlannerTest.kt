package io.github.ssebanom.ling.runtime

import io.github.ssebanom.ling.runtime.SetupPlanner.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPlannerTest {
    private val GiB = 1024L * 1024 * 1024
    private val s25u = 11L * GiB + 300L * 1024 * 1024 // 12GB 모델의 OS 보고값 근사

    @Test fun s25uWithSpaceGetsQ4_0() {
        val p = SetupPlanner.plan(s25u, 100 * GiB, emptySet(), emptyMap())
        assertTrue(p is Plan.Download)
        assertEquals("Q4_0", (p as Plan.Download).variant.id)
        assertEquals(0L, p.resumeFrom)
        assertEquals(16384, SetupPlanner.defaultContext(s25u))
    }

    @Test fun existingCompleteModelIsReused() {
        val p = SetupPlanner.plan(s25u, 1 * GiB, setOf("IQ4_XS", "Q4_K_M"), emptyMap())
        assertEquals("Q4_K_M", (p as Plan.UseExisting).variant.id)
    }

    @Test fun partialDownloadIsResumed() {
        val p = SetupPlanner.plan(s25u, 100 * GiB, emptySet(), mapOf("Q4_0" to 0L, "IQ4_XS" to 2_000_000_000L))
        p as Plan.Download
        assertEquals("IQ4_XS", p.variant.id)
        assertEquals(2_000_000_000L, p.resumeFrom)
    }

    @Test fun lowStorageFallsBackToSmallerQuant() {
        // Q4_0(4.62GB)+여유 는 안 되고 IQ4_XS(4.39GB)+여유 는 되는 공간
        val p = SetupPlanner.plan(s25u, 4_750_000_000L, emptySet(), emptyMap())
        assertEquals("IQ4_XS", (p as Plan.Download).variant.id)
    }

    @Test fun lowRamDeviceGetsIq4Xs() {
        val p = SetupPlanner.plan(8 * GiB, 100 * GiB, emptySet(), emptyMap())
        assertEquals("IQ4_XS", (p as Plan.Download).variant.id)
        assertEquals(8192, SetupPlanner.defaultContext(8 * GiB))
    }

    @Test fun notEnoughStorage() {
        val p = SetupPlanner.plan(s25u, 2 * GiB, emptySet(), emptyMap())
        assertTrue(p is Plan.NotEnoughStorage)
    }
}
