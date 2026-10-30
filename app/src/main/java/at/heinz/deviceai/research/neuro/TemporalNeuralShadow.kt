package at.heinz.deviceai.research.neuro

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.tanh

/**
 * Event-driven, contractive recurrent research cell. NOT an answer source,
 * learning policy or authority to execute code. Every input comes from a
 * currently authorized, already verified evidence selection (EWM2/CRE1).
 *
 * Dynamics: dx_i/dt = (-x_i + tanh(sum_j W_ij * x_j + u_i)) / tau_i.
 * At each event, the recurrent drive is FROZEN at the previous state: the
 * implemented exponential step is exact for that frozen drive, NOT an exact
 * continuous-time solution of the nonlinear recurrent ODE.
 */
class TemporalNeuralShadow(
    timeConstantsMs: List<Double>,
    recurrentWeights: List<List<Double>>,
    private val maxGapMs: Long = 60_000L
) {
    private val tau = timeConstantsMs.toList()
    private val weights = recurrentWeights.map { it.toList() }
    private val rowNorms: List<Double>
    private var lastTimeMs: Long? = null
    private var state = DoubleArray(tau.size)

    init {
        require(tau.size in 1..32) { "NEURO_DIMENSION_INVALID" }
        require(maxGapMs in 1..86_400_000L) { "NEURO_GAP_INVALID" }
        require(tau.all { it.isFinite() && it in 1.0..86_400_000.0 }) { "NEURO_TIME_CONSTANT_INVALID" }
        require(weights.size == tau.size && weights.all { it.size == tau.size }) { "NEURO_MATRIX_SHAPE_INVALID" }
        require(weights.flatten().all { it.isFinite() }) { "NEURO_MATRIX_NONFINITE" }
        rowNorms = weights.map { row -> row.sumOf { abs(it) } }
        // tanh is 1-Lipschitz. ||W||_infinity < 1 yields an explicit
        // contraction for any dt>0 under the frozen-drive recurrent update.
        require(rowNorms.all { it < 0.95 }) { "NEURO_CONTRACTION_NOT_PROVED" }
    }

    data class Observation(
        val status: Status,
        val activity: List<Double>,
        val maxStepContractionBound: Double,
        val sourceAuthority: String = "EWM2_VERIFIED_INPUT_ONLY",
        val executable: Boolean = false,
        val answerAuthority: Boolean = false,
        val learnsWeights: Boolean = false
    )

    enum class Status { SHADOW_ONLY, INITIALIZED, REVOKED_RESET, THERMAL_RESET, STALE_RESET }

    @Synchronized
    fun reset() {
        state.fill(0.0)
        lastTimeMs = null
    }

    private fun snapshot(status: Status, bound: Double = 1.0): Observation =
        Observation(status, state.toList(), bound)

    @Synchronized
    fun observe(
        atMillis: Long,
        features: List<Double>,
        evidenceAuthorized: Boolean,
        evidenceCurrent: Boolean,
        thermalAllowed: Boolean = true
    ): Observation {
        // Revocation always wins, even if the feature vector is malformed.
        if (!evidenceAuthorized || !evidenceCurrent) {
            reset()
            return snapshot(Status.REVOKED_RESET)
        }
        if (!thermalAllowed) {
            reset()
            return snapshot(Status.THERMAL_RESET)
        }
        require(atMillis >= 0L) { "NEURO_CLOCK_INVALID" }
        require(features.size == tau.size && features.all { it.isFinite() && it in -1.0..1.0 }) {
            "NEURO_FEATURE_INVALID"
        }
        val previous = lastTimeMs
        if (previous == null) {
            lastTimeMs = atMillis
            return snapshot(Status.INITIALIZED)
        }
        require(atMillis >= previous) { "NEURO_CLOCK_ROLLBACK" }
        val delta = atMillis - previous
        if (delta > maxGapMs) {
            reset()
            lastTimeMs = atMillis
            return snapshot(Status.STALE_RESET)
        }
        if (delta == 0L) return snapshot(Status.SHADOW_ONLY)
        val old = state.copyOf()
        val next = DoubleArray(tau.size)
        var bound = 0.0
        for (i in next.indices) {
            val alpha = exp(-delta.toDouble() / tau[i])
            // -expm1 is numerically stable for very small delta/tau.
            val complement = -expm1(-delta.toDouble() / tau[i])
            var drive = features[i]
            for (j in old.indices) drive += weights[i][j] * old[j]
            next[i] = alpha * old[i] + complement * tanh(drive)
            require(next[i].isFinite() && abs(next[i]) <= 1.000000000001) { "NEURO_NUMERICAL_FAILURE" }
            bound = maxOf(bound, alpha + complement * rowNorms[i])
        }
        state = next
        lastTimeMs = atMillis
        return snapshot(Status.SHADOW_ONLY, bound.coerceIn(0.0, 1.0))
    }
}
