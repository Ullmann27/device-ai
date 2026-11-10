package at.heinz.deviceai.systemintelligence

import at.heinz.deviceai.benchmark.BenchmarkRun
import at.heinz.deviceai.benchmark.TaskMeasurement
import org.junit.Assert.*
import org.junit.Test

class SystemPerformanceTest {
    private val conditions = SystemPerformanceConditions("device", "build", "protocol-v1", "workload-sha",
        "inner-60hz", SystemPerformanceWarmState.WARM, 75, false, 30.0, 0, true)

    private fun capture(id: String, value: Double = 100.0,
        conditions: SystemPerformanceConditions = this.conditions,
        metric: SystemPerformanceMetric = SystemPerformanceMetric.APP_START_MILLIS,
        count: Int = metric.minimumSamples) = SystemPerformanceCapture(id, if (id == "before") 1L else 2L,
        conditions, mapOf(metric to List(count) { value }))

    @Test fun freeSpaceDriftCannotClaimPerformanceGainOrRegression() {
        for (afterBytes in listOf(999.0, 1001.0)) {
            val before = capture("before").copy(metrics = capture("before").metrics +
                (SystemPerformanceMetric.AVAILABLE_STORAGE_BYTES to listOf(1000.0)))
            val after = capture("after").copy(metrics = capture("after").metrics +
                (SystemPerformanceMetric.AVAILABLE_STORAGE_BYTES to listOf(afterBytes)))
            val compared = SystemPerformanceEvaluator.compare(before, after)
            assertEquals(SystemPerformanceVerdict.NO_MEASURED_GAIN, compared.verdict)
            assertEquals(afterBytes - 1000.0,
                compared.metrics.getValue(SystemPerformanceMetric.AVAILABLE_STORAGE_BYTES).medianDelta!!, 0.0)
        }
    }

    @Test fun missingIsNotZero() {
        val stats = SystemPerformanceEvaluator.stats(emptyList())
        assertEquals(0, stats.count)
        assertNull(stats.median)
        assertNull(stats.p95)
        assertEquals(0.0, SystemPerformanceEvaluator.stats(listOf(0.0)).median!!, 0.0)
    }

    @Test fun medianAndNearestRankTail() {
        val even = SystemPerformanceEvaluator.stats((1..20).map(Int::toDouble))
        assertEquals(10.5, even.median!!, 0.0)
        assertEquals(19.0, even.p95!!, 0.0)
        val odd = SystemPerformanceEvaluator.stats(listOf(5.0, 1.0, 3.0))
        assertEquals(3.0, odd.median!!, 0.0)
        assertEquals(5.0, odd.p95!!, 0.0)
    }

    @Test fun keepsMultiSecondStalls() {
        val values = List(18) { 16.0 } + listOf(1500.0, 5000.0)
        assertEquals(1500.0, SystemPerformanceEvaluator.stats(values).p95!!, 0.0)
    }

    @Test fun rejectsInvalidNumbersAndUnboundedSeries() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            assertThrows(IllegalArgumentException::class.java) { SystemPerformanceEvaluator.stats(listOf(it)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SystemPerformanceEvaluator.stats(List(SystemPerformanceCapture.MAX_SAMPLES + 1) { 0.0 })
        }
    }

    @Test fun medianAvoidsOverflow() {
        assertEquals(Double.MAX_VALUE, SystemPerformanceEvaluator.stats(listOf(Double.MAX_VALUE,
            Double.MAX_VALUE)).median!!, 0.0)
    }

    @Test fun ordinarySnapshotCannotClaimImprovement() {
        val ordinary = SystemPerformanceConditions("device", "build", "protocol", "workload", "display")
        val result = SystemPerformanceEvaluator.compare(capture("before", conditions = ordinary),
            capture("after", 1.0, ordinary))
        assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE, result.verdict)
        assertTrue(result.reasons.contains("BACKGROUND_UNCONTROLLED"))
        assertTrue(result.reasons.contains("THERMAL_UNKNOWN"))
        assertTrue(result.summaryGerman().contains("keine Geschwindigkeitsaussage"))
    }

    @Test fun allIdentityMismatchesReject() {
        listOf(conditions.copy(deviceKey = "other"), conditions.copy(buildKey = "other"),
            conditions.copy(protocolKey = "other"), conditions.copy(workloadKey = "other"),
            conditions.copy(displayKey = "cover-120hz")).forEach {
            assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE,
                SystemPerformanceEvaluator.compare(capture("before"), capture("after", 1.0, it)).verdict)
        }
    }

    @Test fun missingIdentityRejects() {
        val result = SystemPerformanceEvaluator.compare(capture("before", conditions = conditions.copy(buildKey = "")),
            capture("after"))
        assertTrue(result.reasons.contains("BUILD_UNKNOWN"))
    }

    @Test fun warmAndColdNotCompared() {
        val result = SystemPerformanceEvaluator.compare(capture("before"),
            capture("after", 1.0, conditions.copy(warmState = SystemPerformanceWarmState.COLD)))
        assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE, result.verdict)
        assertTrue(result.reasons.contains("WARM_STATE_MISMATCH"))
    }

    @Test fun thermalMismatchRetainsDescriptiveDelta() {
        val result = SystemPerformanceEvaluator.compare(capture("before"),
            capture("after", 200.0, conditions.copy(thermalStatus = 3)))
        assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE, result.verdict)
        assertEquals(100.0, result.metrics.getValue(SystemPerformanceMetric.APP_START_MILLIS).medianDelta!!, 0.0)
        assertTrue(result.reasons.contains("THERMAL_MISMATCH"))
    }

    @Test fun batteryChargingTemperatureAndBackgroundReject() {
        listOf(conditions.copy(batteryPercent = 69), conditions.copy(charging = true),
            conditions.copy(batteryTemperatureC = 32.01), conditions.copy(backgroundControlled = false),
            conditions.copy(batteryPercent = null), conditions.copy(charging = null),
            conditions.copy(batteryTemperatureC = null)).forEach {
            assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE,
                SystemPerformanceEvaluator.compare(capture("before"), capture("after", 1.0, it)).verdict)
        }
    }

    @Test fun toleranceBoundariesAreInclusive() {
        val after = capture("after", 100.0, conditions.copy(batteryPercent = 70, batteryTemperatureC = 32.0))
        assertEquals(SystemPerformanceVerdict.NO_MEASURED_GAIN,
            SystemPerformanceEvaluator.compare(capture("before"), after).verdict)
    }

    @Test fun reorderedOrSameCaptureRejects() {
        val before = capture("before")
        assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE, SystemPerformanceEvaluator.compare(before, before).verdict)
        assertEquals(SystemPerformanceVerdict.NOT_COMPARABLE,
            SystemPerformanceEvaluator.compare(capture("after"), before).verdict)
    }

    @Test fun insufficientSamplesNeverClaimSpeedup() {
        val result = SystemPerformanceEvaluator.compare(capture("before"), capture("after", 1.0, count = 19))
        assertEquals(SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE, result.verdict)
    }

    @Test fun unavailableReasonSurvivesNumericalSamples() {
        val after = capture("after", 1.0).copy(unavailable = mapOf(SystemPerformanceMetric.APP_START_MILLIS to "PARTIAL"))
        val result = SystemPerformanceEvaluator.compare(capture("before"), after)
        assertEquals(SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE, result.verdict)
        assertTrue(result.metrics.getValue(SystemPerformanceMetric.APP_START_MILLIS).reasons.contains("AFTER_UNAVAILABLE:PARTIAL"))
    }

    @Test fun failedSamplesCannotBeHiddenByFastSuccesses() {
        val after = capture("after", 1.0).copy(failedSamples = mapOf(SystemPerformanceMetric.APP_START_MILLIS to 1))
        assertEquals(SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE,
            SystemPerformanceEvaluator.compare(capture("before"), after).verdict)
    }

    @Test fun negativeEffectsOverrideImprovements() {
        val baseline = capture("before").copy(metrics = mapOf(
            SystemPerformanceMetric.APP_START_MILLIS to List(20) { 100.0 },
            SystemPerformanceMetric.AVAILABLE_STORAGE_BYTES to listOf(1000.0)))
        val after = capture("after").copy(metrics = mapOf(
            SystemPerformanceMetric.APP_START_MILLIS to List(20) { 110.0 },
            SystemPerformanceMetric.AVAILABLE_STORAGE_BYTES to listOf(9000.0)))
        assertEquals(SystemPerformanceVerdict.REGRESSION, SystemPerformanceEvaluator.compare(baseline, after).verdict)
    }

    @Test fun tailRegressionOverridesMedianGain() {
        val after = capture("after").copy(metrics = mapOf(SystemPerformanceMetric.APP_START_MILLIS to
            (List(18) { 50.0 } + listOf(300.0, 400.0))))
        assertEquals(SystemPerformanceVerdict.REGRESSION,
            SystemPerformanceEvaluator.compare(capture("before"), after).verdict)
    }

    @Test fun noiseDoesNotCreateRecommendation() {
        assertEquals(SystemPerformanceVerdict.NO_MEASURED_GAIN,
            SystemPerformanceEvaluator.compare(capture("before"), capture("after", 95.0)).verdict)
    }

    @Test fun observedGainIsOnlyExploratory() {
        val result = SystemPerformanceEvaluator.compare(capture("before"), capture("after", 94.0))
        assertEquals(SystemPerformanceVerdict.EXPLORATORY_GAIN, result.verdict)
        assertTrue(result.reasons.contains("DESCRIPTIVE_NOT_CAUSAL"))
    }

    @Test fun absenceInOneArmIsNotZero() {
        val after = capture("after").copy(metrics = emptyMap())
        val result = SystemPerformanceEvaluator.compare(capture("before"), after)
        assertEquals(SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE, result.verdict)
        assertNull(result.metrics.getValue(SystemPerformanceMetric.APP_START_MILLIS).medianDelta)
    }

    private fun row(id: String, success: Boolean = true, error: String? = null, latency: Long = 20) =
        TaskMeasurement("local", id, success, null, null, 0, 1, 10, latency, 0, null, error)

    @Test fun benchmarkAdapterDropsFailedZeroLatency() {
        val samples = SystemPerformanceBenchmarkAdapter.latencies(BenchmarkRun(listOf(row("a"),
            row("b", false, "ERROR", 0)), false, 0), "local", setOf("a", "b"))
        assertEquals(listOf(20.0), samples.values)
        assertEquals(1, samples.failed)
        assertNull(samples.unavailable)
    }

    @Test fun benchmarkAdapterRejectsCancelledMissingAndDuplicates() {
        val runs = listOf(BenchmarkRun(listOf(row("a")), true, 0),
            BenchmarkRun(listOf(row("a")), false, 1),
            BenchmarkRun(emptyList(), false, 0),
            BenchmarkRun(listOf(row("a"), row("a")), false, 0))
        runs.forEach {
            val samples = SystemPerformanceBenchmarkAdapter.latencies(it, "local", setOf("a"))
            assertNotNull(samples.unavailable)
            assertTrue(samples.values.isEmpty())
        }
    }

    @Test fun benchmarkAdapterScopesSystemAndTasks() {
        val samples = SystemPerformanceBenchmarkAdapter.latencies(BenchmarkRun(listOf(row("a"),
            row("b", latency = 900), row("a").copy(system = "remote")), false, 0), "local", setOf("a"))
        assertEquals(listOf(20.0), samples.values)
        assertEquals(0, samples.failed)
    }
}
