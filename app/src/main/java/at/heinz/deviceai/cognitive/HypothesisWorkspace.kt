package at.heinz.deviceai.cognitive

import kotlin.math.ln

/**
 * Small, bounded hypothesis workspace for diagnostic reasoning.
 * Probabilities are heuristic weights, not calibrated truth probabilities.
 */
internal class HypothesisWorkspace(
    private val maxHypotheses: Int = 12,
    private val maxPerBucket: Int = 2
) {
    enum class Bucket { CONFIG, DATA, LIFECYCLE, EXECUTION, MODEL, UI, OTHER }

    data class Hypothesis(
        val id: String,
        val bucket: Bucket,
        val claim: String,
        var weight: Float,
        val supportingEvidence: MutableList<String> = mutableListOf(),
        val contradictingEvidence: MutableList<String> = mutableListOf()
    ) {
        init {
            require(id.matches(Regex("[A-Za-z0-9._:-]{1,64}")))
            require(claim.isNotBlank() && claim.length <= 300)
            require(weight.isFinite() && weight > 0f)
        }
    }

    data class Probe(
        val id: String,
        val latencyCostMs: Float,
        val thermalCost: Float,
        /** expected true/false result for hypotheses this probe discriminates; absent = neutral */
        val expectedByHypothesis: Map<String, Boolean>
    ) {
        init {
            require(id.matches(Regex("[A-Za-z0-9._:-]{1,64}")))
            require(latencyCostMs.isFinite() && latencyCostMs >= 0f)
            require(thermalCost.isFinite() && thermalCost >= 0f)
        }
    }

    private val hypotheses = LinkedHashMap<String, Hypothesis>()

    fun add(candidate: Hypothesis): Boolean {
        if (candidate.id in hypotheses || hypotheses.size >= maxHypotheses) return false
        if (hypotheses.values.count { it.bucket == candidate.bucket } >= maxPerBucket) return false
        hypotheses[candidate.id] = candidate
        normalize()
        return true
    }

    fun snapshot(): List<Hypothesis> = hypotheses.values.map {
        it.copy(
            supportingEvidence = it.supportingEvidence.toMutableList(),
            contradictingEvidence = it.contradictingEvidence.toMutableList()
        )
    }

    fun entropyBits(): Float = entropy(hypotheses.values.map { it.weight })

    /**
     * Chooses the most discriminating cheap probe. Returns null when there are fewer than two
     * live hypotheses or no probe can change their relative weights.
     */
    fun selectNextProbe(probes: List<Probe>): Probe? {
        if (hypotheses.size < 2) return null
        val currentEntropy = entropyBits()
        return probes.asSequence()
            .map { it to informationGainPerCost(it, currentEntropy) }
            .filter { it.second > 0f }
            .maxWithOrNull(compareBy<Pair<Probe, Float>> { it.second }.thenByDescending { it.first.id })
            ?.first
    }

    fun observe(probe: Probe, result: Boolean, evidence: String) {
        require(evidence.isNotBlank() && evidence.length <= 300)
        for (h in hypotheses.values) {
            val expected = probe.expectedByHypothesis[h.id] ?: continue
            if (expected == result) {
                h.weight *= SUPPORT_MULTIPLIER
                h.supportingEvidence += evidence
            } else {
                h.weight *= CONTRADICTION_MULTIPLIER
                h.contradictingEvidence += evidence
            }
        }
        normalize()
    }

    fun best(): Hypothesis? = hypotheses.values.maxByOrNull { it.weight }

    private fun informationGainPerCost(probe: Probe, currentEntropy: Float): Float {
        val positiveMass = hypotheses.values.sumOf {
            if (probe.expectedByHypothesis[it.id] == true) it.weight.toDouble() else 0.0
        }.toFloat()
        val negativeMass = hypotheses.values.sumOf {
            if (probe.expectedByHypothesis[it.id] == false) it.weight.toDouble() else 0.0
        }.toFloat()
        if (positiveMass <= 0f || negativeMass <= 0f) return 0f

        val pTrue = positiveMass / (positiveMass + negativeMass)
        val posteriorTrue = posterior(probe, true)
        val posteriorFalse = posterior(probe, false)
        val expectedEntropy = pTrue * entropy(posteriorTrue) + (1f - pTrue) * entropy(posteriorFalse)
        val gain = (currentEntropy - expectedEntropy).coerceAtLeast(0f)
        val cost = (probe.latencyCostMs + THERMAL_COST_MS_EQUIVALENT * probe.thermalCost)
            .coerceAtLeast(MIN_COST)
        return gain / cost
    }

    private fun posterior(probe: Probe, result: Boolean): List<Float> {
        val raw = hypotheses.values.map { h ->
            when (probe.expectedByHypothesis[h.id]) {
                null -> h.weight
                result -> h.weight * SUPPORT_MULTIPLIER
                else -> h.weight * CONTRADICTION_MULTIPLIER
            }
        }
        val sum = raw.sum().coerceAtLeast(1e-6f)
        return raw.map { it / sum }
    }

    private fun normalize() {
        val sum = hypotheses.values.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1e-6f)
        hypotheses.values.forEach { it.weight /= sum }
    }

    private fun entropy(values: Collection<Float>): Float {
        var out = 0.0
        values.forEach { p ->
            if (p > 1e-7f) out -= p * (ln(p.toDouble()) / LN_2)
        }
        return out.toFloat()
    }

    companion object {
        private const val SUPPORT_MULTIPLIER = 1.8f
        private const val CONTRADICTION_MULTIPLIER = 0.05f
        private const val THERMAL_COST_MS_EQUIVALENT = 50f
        private const val MIN_COST = 0.05f
        private val LN_2 = ln(2.0)
    }
}
