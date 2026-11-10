package at.heinz.deviceai.systemintelligence

import at.heinz.deviceai.benchmark.BenchmarkRun
import kotlin.math.abs
import kotlin.math.ceil

enum class SystemPerformanceWarmState { COLD, WARM, UNKNOWN }

data class SystemPerformanceConditions(
    val deviceKey: String,
    val buildKey: String,
    val protocolKey: String,
    val workloadKey: String,
    val displayKey: String,
    val warmState: SystemPerformanceWarmState = SystemPerformanceWarmState.UNKNOWN,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val batteryTemperatureC: Double? = null,
    val thermalStatus: Int? = null,
    val backgroundControlled: Boolean = false
) {
    init {
        listOf(deviceKey, buildKey, protocolKey, workloadKey, displayKey).forEach {
            require(it.length <= 160 && it.none(Char::isISOControl))
        }
        require(batteryPercent == null || batteryPercent in 0..100)
        require(thermalStatus == null || thermalStatus in 0..6)
        require(batteryTemperatureC == null ||
            batteryTemperatureC.isFinite() && batteryTemperatureC in -40.0..100.0)
    }
}

enum class SystemPerformanceMetric(val higherIsBetter: Boolean, val minimumSamples: Int) {
    AVAILABLE_STORAGE_BYTES(true, 1),
    OWN_PSS_BYTES(false, 5),
    OWN_CPU_MILLIS(false, 20),
    APP_START_MILLIS(false, 20),
    UI_REACTION_MILLIS(false, 20),
    FRAME_MILLIS(false, 120),
    MODEL_TASK_MILLIS(false, 20)
}

data class SystemPerformanceCapture(
    val id: String,
    val observedAtMs: Long,
    val conditions: SystemPerformanceConditions,
    val metrics: Map<SystemPerformanceMetric, List<Double>>,
    val unavailable: Map<SystemPerformanceMetric, String> = emptyMap(),
    val failedSamples: Map<SystemPerformanceMetric, Int> = emptyMap()
) {
    init {
        require(id.isNotBlank() && id.length <= 160 && id.none(Char::isISOControl))
        require(observedAtMs >= 0)
        require(metrics.values.all { samples ->
            samples.size <= MAX_SAMPLES && samples.all { it.isFinite() && it >= 0.0 }
        })
        require(unavailable.values.all { it.length <= 160 && it.none(Char::isISOControl) })
        require(failedSamples.values.all { it in 0..MAX_SAMPLES })
    }

    companion object { const val MAX_SAMPLES = 4096 }
}

data class SystemPerformanceStats(val count: Int, val median: Double?, val p95: Double?)

enum class SystemPerformanceVerdict {
    NOT_COMPARABLE, INSUFFICIENT_EVIDENCE, REGRESSION, NO_MEASURED_GAIN, EXPLORATORY_GAIN
}

data class SystemPerformanceMetricChange(
    val before: SystemPerformanceStats,
    val after: SystemPerformanceStats,
    val medianDelta: Double?,
    val p95Delta: Double?,
    val verdict: SystemPerformanceVerdict,
    val reasons: List<String>
)

data class SystemPerformanceComparison(
    val verdict: SystemPerformanceVerdict,
    val reasons: List<String>,
    val metrics: Map<SystemPerformanceMetric, SystemPerformanceMetricChange>
) {
    fun summaryGerman(): String = when (verdict) {
        SystemPerformanceVerdict.NOT_COMPARABLE -> "Bedingungen nicht vergleichbar; keine Geschwindigkeitsaussage."
        SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE -> "Messreihe unvollständig; keine Dauerempfehlung."
        SystemPerformanceVerdict.REGRESSION -> "Mindestens ein Messwert verschlechtert; Maßnahme erneut prüfen."
        SystemPerformanceVerdict.NO_MEASURED_GAIN -> "Keine messbare Verbesserung; keine Dauerempfehlung."
        SystemPerformanceVerdict.EXPLORATORY_GAIN -> "Verbesserung beobachtet; kontrollierte Wiederholungen erforderlich."
    }
}

object SystemPerformanceEvaluator {
    fun stats(values: List<Double>): SystemPerformanceStats {
        require(values.size <= SystemPerformanceCapture.MAX_SAMPLES)
        require(values.all { it.isFinite() && it >= 0.0 })
        if (values.isEmpty()) return SystemPerformanceStats(0, null, null)
        val ordered = values.sorted()
        val middle = ordered.size / 2
        val median = if (ordered.size % 2 == 1) ordered[middle]
            else ordered[middle - 1] / 2.0 + ordered[middle] / 2.0
        val index = ceil(ordered.size * .95).toInt() - 1
        return SystemPerformanceStats(ordered.size, median, ordered[index])
    }

    fun compare(before: SystemPerformanceCapture, after: SystemPerformanceCapture): SystemPerformanceComparison {
        val conditions = conditionProblems(before.conditions, after.conditions).toMutableList()
        if (before.id == after.id) conditions += "SAME_CAPTURE"
        if (after.observedAtMs <= before.observedAtMs) conditions += "INVALID_TIME_ORDER"
        val keys = (before.metrics.keys + after.metrics.keys + before.unavailable.keys +
            after.unavailable.keys + before.failedSamples.keys + after.failedSamples.keys).sortedBy { it.ordinal }
        val changes = keys.associateWith { metric -> compareMetric(metric, before, after, conditions.isEmpty()) }
        val verdict = when {
            conditions.isNotEmpty() -> SystemPerformanceVerdict.NOT_COMPARABLE
            changes.values.any { it.verdict == SystemPerformanceVerdict.REGRESSION } -> SystemPerformanceVerdict.REGRESSION
            changes.isEmpty() || changes.values.any { it.verdict == SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE } ->
                SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE
            changes.values.any { it.verdict == SystemPerformanceVerdict.EXPLORATORY_GAIN } -> SystemPerformanceVerdict.EXPLORATORY_GAIN
            else -> SystemPerformanceVerdict.NO_MEASURED_GAIN
        }
        val reasons = conditions + when (verdict) {
            SystemPerformanceVerdict.EXPLORATORY_GAIN -> listOf("DESCRIPTIVE_NOT_CAUSAL", "REPEAT_CONTROLLED_ABBA")
            SystemPerformanceVerdict.NO_MEASURED_GAIN -> listOf("NO_DURATION_RECOMMENDATION")
            SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE -> listOf("MISSING_OR_FAILED_MEASUREMENTS")
            SystemPerformanceVerdict.REGRESSION -> listOf("NEGATIVE_EFFECT_RETAINED")
            else -> emptyList()
        }
        return SystemPerformanceComparison(verdict, reasons.distinct(), changes)
    }

    private fun conditionProblems(a: SystemPerformanceConditions, b: SystemPerformanceConditions): List<String> = buildList {
        val identities = listOf("DEVICE" to (a.deviceKey to b.deviceKey), "BUILD" to (a.buildKey to b.buildKey),
            "PROTOCOL" to (a.protocolKey to b.protocolKey), "WORKLOAD" to (a.workloadKey to b.workloadKey),
            "DISPLAY" to (a.displayKey to b.displayKey))
        identities.forEach { (name, pair) ->
            if (pair.first.isBlank() || pair.second.isBlank()) add("${name}_UNKNOWN")
            else if (pair.first != pair.second) add("${name}_MISMATCH")
        }
        if (a.warmState == SystemPerformanceWarmState.UNKNOWN || b.warmState == SystemPerformanceWarmState.UNKNOWN)
            add("WARM_STATE_UNKNOWN")
        else if (a.warmState != b.warmState) add("WARM_STATE_MISMATCH")
        if (a.batteryPercent == null || b.batteryPercent == null) add("BATTERY_UNKNOWN")
        else if (abs(a.batteryPercent - b.batteryPercent) > 5) add("BATTERY_MISMATCH")
        if (a.charging == null || b.charging == null) add("CHARGING_UNKNOWN")
        else if (a.charging != b.charging) add("CHARGING_MISMATCH")
        if (a.batteryTemperatureC == null || b.batteryTemperatureC == null) add("TEMPERATURE_UNKNOWN")
        else if (abs(a.batteryTemperatureC - b.batteryTemperatureC) > 2.0) add("TEMPERATURE_MISMATCH")
        if (a.thermalStatus == null || b.thermalStatus == null) add("THERMAL_UNKNOWN")
        else if (a.thermalStatus != b.thermalStatus) add("THERMAL_MISMATCH")
        if (!a.backgroundControlled || !b.backgroundControlled) add("BACKGROUND_UNCONTROLLED")
    }

    private fun compareMetric(metric: SystemPerformanceMetric, before: SystemPerformanceCapture,
        after: SystemPerformanceCapture, comparable: Boolean): SystemPerformanceMetricChange {
        val a = stats(before.metrics[metric].orEmpty())
        val b = stats(after.metrics[metric].orEmpty())
        val reasons = buildList {
            if (a.count < metric.minimumSamples || b.count < metric.minimumSamples) add("INSUFFICIENT_SAMPLES")
            if ((before.failedSamples[metric] ?: 0) > 0 || (after.failedSamples[metric] ?: 0) > 0) add("FAILED_SAMPLES")
            before.unavailable[metric]?.let { add("BEFORE_UNAVAILABLE:$it") }
            after.unavailable[metric]?.let { add("AFTER_UNAVAILABLE:$it") }
            if (!comparable) add("CONDITIONS_NOT_COMPARABLE")
        }
        val medianDelta = if (a.median != null && b.median != null) b.median - a.median else null
        val p95Delta = if (a.p95 != null && b.p95 != null) b.p95 - a.p95 else null
        val verdict = when {
            !comparable -> SystemPerformanceVerdict.NOT_COMPARABLE
            reasons.isNotEmpty() -> SystemPerformanceVerdict.INSUFFICIENT_EVIDENCE
            else -> {
                val sign = if (metric.higherIsBetter) 1.0 else -1.0
                val medianChange = sign * requireNotNull(medianDelta)
                val tailChange = sign * requireNotNull(p95Delta)
                val medianTolerance = tolerance(metric, requireNotNull(a.median))
                val tailTolerance = tolerance(metric, requireNotNull(a.p95))
                when {
                    medianChange < -medianTolerance || tailChange < -tailTolerance -> SystemPerformanceVerdict.REGRESSION
                    medianChange > medianTolerance || tailChange > tailTolerance -> SystemPerformanceVerdict.EXPLORATORY_GAIN
                    else -> SystemPerformanceVerdict.NO_MEASURED_GAIN
                }
            }
        }
        return SystemPerformanceMetricChange(a, b, medianDelta, p95Delta, verdict, reasons)
    }

    private fun tolerance(metric: SystemPerformanceMetric, baseline: Double): Double = when (metric) {
        SystemPerformanceMetric.AVAILABLE_STORAGE_BYTES -> 0.0
        SystemPerformanceMetric.OWN_PSS_BYTES -> maxOf(1024.0 * 1024.0, baseline * .05)
        else -> maxOf(1.0, baseline * .05)
    }
}

object SystemPerformanceBenchmarkAdapter {
    data class Samples(val values: List<Double>, val failed: Int, val unavailable: String?)

    fun latencies(run: BenchmarkRun, system: String, expectedTaskIds: Set<String>): Samples {
        require(system.isNotBlank() && system.length <= 160)
        require(expectedTaskIds.isNotEmpty() && expectedTaskIds.size <= SystemPerformanceCapture.MAX_SAMPLES)
        val rows = run.measurements.filter { it.system == system && it.taskId in expectedTaskIds }
        require(rows.size <= SystemPerformanceCapture.MAX_SAMPLES)
        val duplicate = rows.groupingBy { it.taskId }.eachCount().values.any { it != 1 }
        val failed = rows.count { !it.success || it.error != null }
        val missing = expectedTaskIds - rows.map { it.taskId }.toSet()
        val reason = when {
            run.cancelled -> "BENCHMARK_CANCELLED"
            run.skippedTasks > 0 || missing.isNotEmpty() -> "BENCHMARK_INCOMPLETE"
            duplicate -> "BENCHMARK_DUPLICATE_TASKS"
            else -> null
        }
        val values = if (reason != null) emptyList() else rows.filter { it.success && it.error == null }
            .map { it.latencyMillis.toDouble() }
        return Samples(values, failed, reason)
    }
}
