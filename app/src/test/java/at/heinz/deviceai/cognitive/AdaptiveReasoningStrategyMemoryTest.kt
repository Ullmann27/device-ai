package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveReasoningStrategyMemoryTest {
    @Test fun oneLuckySuccessDoesNotBecomeCertain() {
        val once = AdaptiveReasoningStrategyMemory.Record("android_lifecycle","trace_first")
            .record(true, latencyMs = 500, thermalCost = .05f)
        assertEquals(1, once.samples)
        assertTrue(once.wilsonLowerBound() < .30f)
    }

    @Test fun repeatedVerifiedSuccessRaisesConservativeScore() {
        var r = AdaptiveReasoningStrategyMemory.Record("memory_conflict","provenance_first")
        repeat(18) { r = r.record(true, 400, .02f) }
        repeat(2) { r = r.record(false, 450, .02f) }
        assertTrue(r.wilsonLowerBound() > .65f)
    }

    @Test fun hotDeviceCanPreferSlightlyLessProvenButCheaperStrategy() {
        var heavy = AdaptiveReasoningStrategyMemory.Record("debug","deep_multi")
        var light = AdaptiveReasoningStrategyMemory.Record("debug","trace_first")
        repeat(20) {
            heavy = heavy.record(it < 18, 8_000, .9f)
            light = light.record(it < 16, 900, .05f)
        }
        val cool = AdaptiveReasoningStrategyMemory.select(listOf(heavy, light), currentThermalStress = 0f)!!
        val hot = AdaptiveReasoningStrategyMemory.select(listOf(heavy, light), currentThermalStress = 1f)!!
        assertTrue(cool.record.strategyId in setOf("deep_multi","trace_first"))
        assertEquals("trace_first", hot.record.strategyId)
    }

    @Test fun emaUsesMeasuredExternalOutcomesOnly() {
        var r = AdaptiveReasoningStrategyMemory.Record("tool_task","receipt_first")
        r = r.record(true, 1000, .2f)
        r = r.record(false, 2000, .4f)
        assertEquals(2, r.samples)
        assertEquals(1, r.successes)
        assertEquals(1, r.failures)
        assertTrue(r.emaLatencyMs in 1000f..2000f)
        assertTrue(r.emaThermalCost in .2f..4f)
    }
}
