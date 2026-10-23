package at.heinz.deviceai.learning.span

import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln

/**
 * Certified answer threshold for CSX1 via Learn-then-Test risk control (Angelopoulos, Bates, Candès, Jordan, Lei
 * 2021) in the form of conformal abstention (Abbasi-Yadkori et al., Google DeepMind 2024).
 *
 * Risk R(t) = P(the model answers with confidence >= t AND the answer is wrong), counted per question: wrong spans
 * plus unsupported answers. Thresholds are tested from strict to permissive (fixed-sequence testing) with the exact
 * binomial p-value P(Bin(n, alpha) <= errors(t)); the most permissive threshold still rejected at level delta is
 * returned. Guarantee: if the calibration questions and future questions are exchangeable,
 * P(R(t*) <= alpha) >= 1 - delta. Nothing is guaranteed for questions from another distribution; that must be
 * measured separately (frozen TEST). No model weight is touched.
 */
object ContinuumCertifiedAbstention {
    /** One calibration question: did the model answer (argmax not null), with which probability, and was it right. */
    data class Outcome(val answered: Boolean, val confidence: Double, val correct: Boolean) {
        init { require(confidence.isFinite() && confidence in 0.0..1.0) }
    }

    data class Certificate(
        val modelSha256: String,
        val threshold: Double,
        val alpha: Double,
        val delta: Double,
        val calibrationQuestions: Int,
        val calibrationErrors: Int,
        val calibrationAnswered: Int,
        val pValue: Double
    ) {
        init {
            require(modelSha256.matches(Regex("[0-9a-f]{64}")))
            require(threshold in 0.0..1.0 && alpha > 0.0 && alpha < 1.0 && delta > 0.0 && delta < 1.0)
            require(pValue <= delta && calibrationErrors in 0..calibrationAnswered && calibrationAnswered <= calibrationQuestions)
        }
    }

    /** Strict to permissive: 1.00, 0.99, ..., 0.00 (0.00 = plain argmax). */
    val GRID: List<Double> = (100 downTo 0).map { it / 100.0 }

    /** @return null when not even the strictest threshold can be certified: then the model must always abstain. */
    fun calibrate(modelSha256: String, outcomes: List<Outcome>, alpha: Double, delta: Double, grid: List<Double> = GRID): Certificate? {
        require(outcomes.isNotEmpty() && alpha > 0.0 && alpha < 1.0 && delta > 0.0 && delta < 1.0)
        require(grid.isNotEmpty() && grid.zipWithNext().all { (a, b) -> a > b } && grid.all { it in 0.0..1.0 })
        var certified: Certificate? = null
        for (t in grid) {
            val answered = outcomes.filter { it.answered && it.confidence >= t }
            val errors = answered.count { !it.correct }
            val p = binomialCdf(errors, outcomes.size, alpha)
            if (p > delta) break // fixed-sequence testing: the first failure ends the sequence
            certified = Certificate(modelSha256, t, alpha, delta, outcomes.size, errors, answered.size, p)
        }
        return certified
    }

    /** Exact P(Bin(n, p) <= k), summed in log space (no normal approximation). */
    fun binomialCdf(k: Int, n: Int, p: Double): Double {
        require(n > 0 && p > 0.0 && p < 1.0)
        if (k < 0) return 0.0
        if (k >= n) return 1.0
        val logRatio = ln(p) - ln(1.0 - p)
        val terms = DoubleArray(k + 1)
        terms[0] = n * ln(1.0 - p)
        for (i in 1..k) terms[i] = terms[i - 1] + ln((n - i + 1).toDouble() / i) + logRatio
        val top = terms.max()
        return minOf(1.0, exp(top) * terms.sumOf { exp(it - top) })
    }

    /**
     * Fail-closed, checkpoint-bound inference: calibration belongs to an exact model snapshot.
     * The current state hash and the prediction are computed while holding the SAME model lock,
     * preventing a concurrent training/update from swapping weights between the two operations.
     * No cached or externally supplied prediction may bypass this identity check.
     */
    fun answer(
        model: ContinuumResidualSpanCore,
        evidence: String,
        question: String,
        certificate: Certificate?
    ): String? {
        if (certificate == null) return null
        return synchronized(model) {
            val currentSha = checkpointSha256(model)
            if (currentSha != certificate.modelSha256) {
                null
            } else {
                val prediction = model.predict(evidence, question)
                if (prediction.answer != null &&
                    prediction.confidence >= certificate.threshold
                ) prediction.answer else null
            }
        }
    }

    /**
     * SHA-256 (lowercase hex) of the complete checkpoint: weights, feature flags and update counter.
     * Callers that also predict must hold the model lock across both steps (see [answer]).
     */
    internal fun checkpointSha256(model: ContinuumResidualSpanCore): String =
        MessageDigest.getInstance("SHA-256")
            .digest(model.save())
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
