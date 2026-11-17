package at.heinz.deviceai.cognitive

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Conservative in-memory strategy statistics.
 * Promotion requires externally verified success/failure signals; model self-confidence is not accepted.
 */
internal object AdaptiveReasoningStrategyMemory {
    data class Record(
        val taskSignature: String,
        val strategyId: String,
        val successes: Int = 0,
        val failures: Int = 0,
        val emaLatencyMs: Float = 0f,
        val emaThermalCost: Float = 0f
    ) {
        init {
            require(taskSignature.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
            require(strategyId.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
            require(successes >= 0 && failures >= 0)
            require(emaLatencyMs.isFinite() && emaLatencyMs >= 0f)
            require(emaThermalCost.isFinite() && emaThermalCost >= 0f)
        }

        val samples: Int get() = successes + failures

        fun record(verifiedSuccess: Boolean, latencyMs: Long, thermalCost: Float): Record {
            require(latencyMs >= 0 && thermalCost.isFinite() && thermalCost >= 0f)
            val first = samples == 0
            val latency = if (first) latencyMs.toFloat()
                else emaLatencyMs + EMA_ALPHA * (latencyMs - emaLatencyMs)
            val thermal = if (first) thermalCost
                else emaThermalCost + EMA_ALPHA * (thermalCost - emaThermalCost)
            return copy(
                successes = successes + if (verifiedSuccess) 1 else 0,
                failures = failures + if (verifiedSuccess) 0 else 1,
                emaLatencyMs = latency.coerceAtLeast(0f),
                emaThermalCost = thermal.coerceAtLeast(0f)
            )
        }

        fun wilsonLowerBound(z: Float = 1.96f): Float {
            require(z.isFinite() && z > 0f)
            val n = samples.toFloat()
            if (n == 0f) return UNKNOWN_PRIOR
            val p = successes / n
            val z2 = z * z
            val denominator = 1f + z2 / n
            val numerator = p + z2 / (2f * n) -
                z * sqrt((p * (1f - p) + z2 / (4f * n)) / n)
            return (numerator / denominator).coerceIn(0f, 1f)
        }
    }

    data class Selection(
        val record: Record,
        val score: Float
    )

    fun select(
        candidates: List<Record>,
        currentThermalStress: Float,
        maxLatencyMs: Float = 10_000f
    ): Selection? {
        if (candidates.isEmpty()) return null
        require(currentThermalStress.isFinite() && currentThermalStress in 0f..1f)
        require(maxLatencyMs.isFinite() && maxLatencyMs > 0f)
        val totalSamples = candidates.sumOf { it.samples }.coerceAtLeast(1)
        return candidates.map { record ->
            val conservative = record.wilsonLowerBound()
            val exploration = EXPLORATION_WEIGHT *
                sqrt(ln(totalSamples.toDouble() + 1.0) / (record.samples + 1.0)).toFloat()
            val latencyPenalty = (record.emaLatencyMs / maxLatencyMs).coerceIn(0f, MAX_LATENCY_PENALTY)
            val thermalPenalty = record.emaThermalCost.coerceAtMost(1f) *
                currentThermalStress * THERMAL_WEIGHT
            Selection(record, conservative + exploration - latencyPenalty - thermalPenalty)
        }.maxWithOrNull(compareBy<Selection> { it.score }.thenByDescending { it.record.strategyId })
    }

    private const val EMA_ALPHA = .2f
    private const val UNKNOWN_PRIOR = .35f
    private const val EXPLORATION_WEIGHT = .15f
    private const val MAX_LATENCY_PENALTY = .25f
    private const val THERMAL_WEIGHT = .50f
}
