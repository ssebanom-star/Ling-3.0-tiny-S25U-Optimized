package io.github.ssebanom.ling.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuQuirksTest {
    @Test fun roundTrip() {
        for (q in GpuQuirks.CANDIDATES) assertEquals(q, GpuQuirks.decode(q.encode()))
        assertEquals(GpuQuirks(), GpuQuirks.decode(""))
    }

    @Test fun envUnsetsDefaults() {
        assertTrue(GpuQuirks().env().values.all { it == null })
        val e = GpuQuirks(opFilter = "GATED_DELTA_NET", noAdrenoMoe = true).env()
        assertEquals("GATED_DELTA_NET", e["GGML_OPENCL_OPFILTER"])
        assertEquals("1", e["LING_OPENCL_NO_ADRENO_MOE"])
        assertNull(e["GGML_OPENCL_DISABLE_FUSION"])
    }

    @Test fun candidatesStartWithDefaultAndAreDistinct() {
        assertEquals(GpuQuirks(), GpuQuirks.CANDIDATES.first())
        assertEquals(GpuQuirks.CANDIDATES.size, GpuQuirks.CANDIDATES.distinct().size)
        // 정규식은 ggml_op_desc 이름과 전체 일치해야 한다
        val re = Regex(GpuQuirks.CANDIDATES.last().opFilter, RegexOption.IGNORE_CASE)
        listOf("GATED_DELTA_NET", "SSM_CONV", "L2_NORM", "FLASH_ATTN_EXT", "SOFT_MAX", "ROPE").forEach { assertTrue(it, re.matches(it)) }
        listOf("MUL_MAT", "MUL_MAT_ID", "RMS_NORM", "ROPE_BACK").forEach { assertTrue(it, !re.matches(it)) }
    }
}
