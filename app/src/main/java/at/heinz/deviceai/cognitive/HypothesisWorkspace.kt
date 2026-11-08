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

    /**
     * Initializes an empty workspace from one explicit prior set, normalized once.
     * Unlike sequential [add], admission order does not change the supplied prior ratios.
     * A rejected batch leaves the workspace unchanged; no evidence or tool authority is inferred.
     */
    fun admitPriors(candidates: List<Hypothesis>): Boolean {
        if (hypotheses.isNotEmpty() || candidates.isEmpty() || candidates.size > maxHypotheses) return false
        if (candidates.map { it.id }.toSet().size != candidates.size) return false
        if (candidates.groupingBy { it.bucket }.eachCount().values.any { it > maxPerBucket }) return false
        val copies = candidates.map {
            it.copy(
                supportingEvidence = it.supportingEvidence.toMutableList(),
                contradictingEvidence = it.contradictingEvidence.toMutableList()
            )
        }
        val total = copies.sumOf { it.weight.toDouble() }
        val admitted = copies.map {
            val normalized = (it.weight.toDouble() / total).toFloat()
            require(normalized.isFinite() && normalized > 0f) { "Prior ratio is not representable" }
            it.copy(weight = normalized)
        }
        admitted.forEach { hypotheses[it.id] = it }
        return true
    }

    fun snapshot(): List<Hypothesis> = hypotheses.values.map {
        it.copy(
            supportingEvidence = it.supportingEvidence.toMutableList(),
            contradictingEvidence = it.contradictingEvidence.toMutableList()
        )
    }

    fun entropyBits(): Float = entropy(hypotheses.values.map { it.weight })

    data class ProbeObservation(val probeId: String, val result: Boolean?) {
        init { require(probeId.matches(Regex("[A-Za-z0-9._:-]{1,64}"))) }
    }

    /**
     * Bounded, read-only experimental design under an explicit observation-error bound.
     * Prioritizes causes certifiable for every admissible error placement, per cost, not priors.
     * A certificate is conditional on the supplied model/error bound, never tool authority.
     */
    fun selectCertificateProbe(
        probes: List<Probe>,
        observations: List<ProbeObservation>,
        remainingProbes: Int,
        costBudget: Float,
        maxErrors: Int = 1
    ): Probe? {
        require(probes.size <= 12 && hypotheses.size <= 12)
        require(remainingProbes in 0..3 && observations.size + remainingProbes <= 3)
        require(maxErrors in 0..1 && costBudget.isFinite() && costBudget >= 0f)
        require(probes.map { it.id }.distinct().size == probes.size)
        require(observations.map { it.probeId }.distinct().size == observations.size)
        val catalog = probes.associateBy { it.id }
        require(observations.all { it.probeId in catalog }) { "Unknown probe observation" }
        require(probes.all { probe -> probe.expectedByHypothesis.keys.all { it in hypotheses } })
        if (remainingProbes == 0 || hypotheses.isEmpty()) return null

        val ids = hypotheses.keys.toList()
        val errors = ids.map { id ->
            observations.count { observation ->
                val expected = catalog.getValue(observation.probeId).expectedByHypothesis[id]
                expected != null && observation.result != null && expected != observation.result
            }
        }
        if (errors.count { it <= maxErrors } < 2) return null
        fun cost(probe: Probe): Double =
            (probe.latencyCostMs.toDouble() + THERMAL_COST_MS_EQUIVALENT * probe.thermalCost)
                .coerceAtLeast(MIN_COST.toDouble())
        val available = probes.filter { probe ->
            observations.none { it.probeId == probe.id } && cost(probe) <= costBudget
        }.sortedBy { it.id }
        var bestGuarantee = 0.0
        var bestScore = 0.0
        var bestCost = Double.POSITIVE_INFINITY
        var bestPlan: List<Probe> = emptyList()

        fun evaluate(plan: List<Probe>, totalCost: Double) {
            var certificates = 0
            val guaranteed = BooleanArray(ids.size) { errors[it] <= maxErrors }
            for (transcript in 0 until (1 shl plan.size)) {
                val compatible = mutableListOf<Int>()
                ids.forEachIndexed { index, id ->
                    var contradictions = errors[index]
                    plan.forEachIndexed { position, probe ->
                        val expected = probe.expectedByHypothesis[id]
                        if (expected != null && expected != (transcript and (1 shl position) != 0)) contradictions++
                    }
                    if (contradictions <= maxErrors) compatible += index
                }
                if (compatible.size == 1) certificates++
                else compatible.forEach { guaranteed[it] = false }
            }
            val guarantee = guaranteed.count { it }.toDouble() / ids.size / totalCost
            val score = certificates.toDouble() / (1 shl plan.size) / totalCost
            if (guarantee > bestGuarantee || guarantee == bestGuarantee &&
                (score > bestScore || score > 0.0 && score == bestScore && totalCost < bestCost)) {
                bestGuarantee = guarantee
                bestScore = score
                bestCost = totalCost
                bestPlan = plan.toList()
            }
        }

        fun search(start: Int, plan: List<Probe>, totalCost: Double) {
            if (plan.isNotEmpty()) evaluate(plan, totalCost)
            if (plan.size == remainingProbes) return
            for (index in start until available.size) {
                val nextCost = totalCost + cost(available[index])
                if (nextCost <= costBudget) search(index + 1, plan + available[index], nextCost)
            }
        }
        search(0, emptyList(), 0.0)
        return bestPlan.minWithOrNull(compareBy<Probe> { cost(it) }.thenBy { it.id })
    }

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
