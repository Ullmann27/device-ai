package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumCertifiedAbstention.Outcome

/**
 * Milestone I2: certified CONDITIONAL answer quality for CSX1, next to the marginal I1 certificate.
 *
 * I1 bounds R(t) = P(answers with confidence >= t AND is wrong), counted over ALL questions. A model that rarely
 * answers passes that bound even when most of its few answers are wrong. I2 bounds the selective risk
 * S(t) = P(wrong | answered at t) = R(t) / P(answered at t), i.e. the quality of the answers that are actually shown.
 *
 * Method: Learn-then-Test (Angelopoulos, Bates, Candès, Jordan, Lei 2021) with Holm's step-down correction over a FIXED
 * threshold grid chosen before calibration; the selective-risk bound itself follows the binomial-tail argument of
 * Geifman & El-Yaniv 2017 (SGR).
 *  - Per threshold t: n_t answered calibration questions, k_t of them wrong. For exchangeable (i.i.d.) questions,
 *    k_t given n_t is Bin(n_t, S(t)). Hence pS(t) = P(Bin(n_t, alphaSelective) <= k_t) is a valid p-value for the
 *    null "S(t) > alphaSelective". Thresholds with fewer than [minAnswered] answers get p = 1 (never certified).
 *  - Optional joint mode with I1's marginal null "R(t) > alphaMarginal": pM(t) = P(Bin(n, alphaMarginal) <= k_t).
 *    The union null "R > alphaMarginal OR S > alphaSelective" has the valid p-value max(pS, pM).
 *  - Holm controls the family-wise error at delta under ANY dependence between thresholds: with probability
 *    >= 1 - delta every certified threshold satisfies its bound(s) at the same time. The most permissive certified
 *    threshold (largest coverage) is returned.
 * Why not I1's fixed sequence: ordered strict -> permissive, the strictest thresholds have almost no answers, p = 1,
 * and the sequence would stop immediately. Holm needs no ordering and no monotonicity of S(t).
 *
 * Scope of the guarantee: only questions exchangeable with the calibration set. Dependence between similar questions
 * (shared entities within one generated draw) and distribution shift are NOT covered by the proof and are measured
 * separately (draw-level re-splits, frozen TEST). No model weight is touched; without a certificate the model abstains.
 */
object ContinuumSelectiveRiskCertification {
    data class ThresholdTest(
        val threshold: Double,
        val answered: Int,
        val errors: Int,
        val pSelective: Double,
        /** 0.0 when no marginal bound is requested. */
        val pMarginal: Double,
        val pValue: Double
    )

    data class Certificate(
        val modelSha256: String,
        val threshold: Double,
        val alphaSelective: Double,
        /** null: selective risk only; otherwise the marginal I1 risk is certified jointly. */
        val alphaMarginal: Double?,
        val delta: Double,
        val calibrationQuestions: Int,
        val calibrationAnswered: Int,
        val calibrationErrors: Int,
        val pValue: Double,
        /** Holm critical value this threshold was rejected at (between delta / gridSize and delta). */
        val holmLevel: Double,
        val gridSize: Int,
        val minAnswered: Int
    ) {
        init {
            require(modelSha256.matches(Regex("[0-9a-f]{64}")))
            require(threshold in 0.0..1.0 && alphaSelective > 0.0 && alphaSelective < 1.0 && delta > 0.0 && delta < 1.0)
            require(alphaMarginal == null || (alphaMarginal > 0.0 && alphaMarginal < 1.0))
            require(gridSize > 0 && minAnswered >= 1 && holmLevel in delta / gridSize..delta && pValue <= holmLevel)
            require(calibrationErrors in 0..calibrationAnswered && calibrationAnswered in minAnswered..calibrationQuestions)
        }

        val calibrationCoverage: Double get() = calibrationAnswered.toDouble() / calibrationQuestions
        val calibrationSelectiveError: Double get() = calibrationErrors.toDouble() / calibrationAnswered
    }

    /** Per-threshold counts and p-values, in grid order. O(n log n + grid) instead of one pass per threshold. */
    fun tests(
        outcomes: List<Outcome>,
        alphaSelective: Double,
        alphaMarginal: Double? = null,
        minAnswered: Int = 30,
        grid: List<Double> = ContinuumCertifiedAbstention.GRID
    ): List<ThresholdTest> {
        require(outcomes.isNotEmpty() && alphaSelective > 0.0 && alphaSelective < 1.0 && minAnswered >= 1)
        require(alphaMarginal == null || (alphaMarginal > 0.0 && alphaMarginal < 1.0))
        require(grid.isNotEmpty() && grid.zipWithNext().all { (a, b) -> a > b } && grid.all { it in 0.0..1.0 })
        val answeredByConfidence = outcomes.filter { it.answered }.sortedByDescending { it.confidence }
        var next = 0
        var answered = 0
        var errors = 0
        return grid.map { t ->
            while (next < answeredByConfidence.size && answeredByConfidence[next].confidence >= t) {
                answered++
                if (!answeredByConfidence[next].correct) errors++
                next++
            }
            val pS = if (answered < minAnswered) 1.0
                else ContinuumCertifiedAbstention.binomialCdf(errors, answered, alphaSelective)
            val pM = if (alphaMarginal == null) 0.0
                else ContinuumCertifiedAbstention.binomialCdf(errors, outcomes.size, alphaMarginal)
            ThresholdTest(t, answered, errors, pS, pM, maxOf(pS, pM))
        }
    }

    /**
     * Holm's step-down procedure: sort p ascending and reject while p(j) <= delta / (m - j) (j counted from 0).
     * @return per input index: the critical value it was rejected at, or null if not rejected.
     */
    fun holm(pValues: List<Double>, delta: Double): List<Double?> {
        require(pValues.isNotEmpty() && delta > 0.0 && delta < 1.0 && pValues.all { it.isFinite() && it in 0.0..1.0 })
        val m = pValues.size
        val levels = arrayOfNulls<Double>(m)
        val order = pValues.indices.sortedWith(compareBy<Int>({ pValues[it] }, { it }))
        for ((rank, index) in order.withIndex()) {
            val critical = delta / (m - rank)
            if (pValues[index] > critical) break
            levels[index] = critical
        }
        return levels.toList()
    }

    /** @return null when no threshold can be certified: then the model must always abstain. */
    fun calibrate(
        modelSha256: String,
        outcomes: List<Outcome>,
        alphaSelective: Double,
        delta: Double,
        alphaMarginal: Double? = null,
        minAnswered: Int = 30,
        grid: List<Double> = ContinuumCertifiedAbstention.GRID
    ): Certificate? {
        require(delta > 0.0 && delta < 1.0)
        val tests = tests(outcomes, alphaSelective, alphaMarginal, minAnswered, grid)
        val levels = holm(tests.map { it.pValue }, delta)
        // Grid runs strict -> permissive: the last rejected threshold has the largest coverage.
        val chosen = tests.indices.lastOrNull { levels[it] != null } ?: return null
        val test = tests[chosen]
        return Certificate(modelSha256, test.threshold, alphaSelective, alphaMarginal, delta, outcomes.size,
            test.answered, test.errors, test.pValue, levels[chosen]!!, grid.size, minAnswered)
    }

    /**
     * One-sided exact Clopper-Pearson upper bound u for a binomial rate: P(Bin(n, u) <= errors) = level (u = 1 when
     * errors == n). It is the same decision as the p-value test: P(Bin(n, a) <= errors) <= level exactly when u <= a.
     */
    fun clopperPearsonUpper(errors: Int, n: Int, level: Double): Double {
        require(n > 0 && errors in 0..n && level > 0.0 && level < 1.0)
        if (errors == n) return 1.0
        // Bisection on the decreasing function p -> P(Bin(n, p) <= errors), which is 1 at p = 0 and 0 at p = 1.
        var lo = 0.0
        var hi = 1.0
        for (iteration in 0 until 200) {
            val mid = (lo + hi) / 2
            if (mid <= lo || mid >= hi) break // no representable midpoint left; mid stays strictly inside (0, 1)
            if (ContinuumCertifiedAbstention.binomialCdf(errors, n, mid) > level) lo = mid else hi = mid
        }
        return hi
    }

    /** Same fail-closed, checkpoint-bound inference as I1: hash and prediction under one model lock. */
    fun answer(model: ContinuumResidualSpanCore, evidence: String, question: String, certificate: Certificate?): String? {
        if (certificate == null) return null
        return synchronized(model) {
            if (ContinuumCertifiedAbstention.checkpointSha256(model) != certificate.modelSha256) null
            else model.predict(evidence, question).let {
                if (it.answer != null && it.confidence >= certificate.threshold) it.answer else null
            }
        }
    }
}
