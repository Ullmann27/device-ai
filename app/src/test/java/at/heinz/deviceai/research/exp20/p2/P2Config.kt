package at.heinz.deviceai.research.exp20.p2

import kotlin.math.abs
import kotlin.random.Random

/**
 * EXP-20 P2 frozen configuration. Its SHA-256 is part of the preregistration; nothing here may be tuned on any data.
 * Every temporal arm sees the same 8 encoder features, each feeding one short and one long channel (16 channels,
 * the P1 size), on the same monotonic session clock, with the same injection protocol and the same readout grid.
 */
object P2Config {
    const val FEATURES = 8
    const val CHANNELS = 2 * FEATURES
    const val TAU_SHORT_SEC = 60.0
    const val TAU_LONG_SEC = 3600.0
    /** A turn holds its encoded input for this long; between turns the input is zero (decay phase). */
    const val INJECT_SEC = 10.0
    const val CTRNN_WEIGHT_SEED = 2026103002L
    const val CTRNN_RHO = 0.9
    /** Readout gap feature: ln(1 + dt / 60 s), dt = time since the previous turn of the same session. */
    const val DT_SCALE_SEC = 60.0

    val LAMBDAS = doubleArrayOf(1e-3, 1e-2, 1e-1, 1.0)
    val THRESHOLDS = doubleArrayOf(0.3, 0.4, 0.5, 0.6, 0.7)

    // Gold semantics (task definition, not model parameters)
    const val STALE_SEC = 1800.0              // short-term situational context older than 30 min is stale
    const val PERSON_WINDOW_SEC = 7200.0      // a person mentioned within 2 h is a live pronoun candidate
    const val PRESENCE_WINDOW_SEC = 600.0     // a presence-uncertainty cue within 10 min blocks device commands

    const val DEV_SEED = 20261030L
    const val TRAIN_CLUSTERS_PER_SCENARIO = 40
    const val VALIDATION_CLUSTERS_PER_SCENARIO = 20
    const val HOLDOUT_CLUSTERS_PER_SCENARIO = 40
    const val VARIANTS = 4

    // Statistics (fixed before the first run)
    const val MDE = 0.05                      // minimum effect: +5 percentage points cluster solve rate
    const val ALPHA = 0.05
    const val BOOTSTRAP = 10_000
    const val BOOTSTRAP_SEED = 5L

    // Resource ceilings for a deployable kernel (host JVM, measured separately; not a Fold7 budget)
    const val MAX_STATE_UPDATE_P99_NS = 10_000.0
    const val MAX_PAYLOAD_BYTES = 4096

    fun taus(): DoubleArray = DoubleArray(CHANNELS) { if (it % 2 == 0) TAU_SHORT_SEC else TAU_LONG_SEC }

    /** P1 bench convention: uniform [-1, 1], rows scaled to an absolute row sum of rho (< 1, contraction holds). */
    fun ctrnnWeights(): Array<DoubleArray> {
        val r = Random(CTRNN_WEIGHT_SEED)
        val w = Array(CHANNELS) { DoubleArray(CHANNELS) { r.nextDouble(-1.0, 1.0) } }
        for (row in w) { val s = row.sumOf { abs(it) }; for (j in row.indices) row[j] *= CTRNN_RHO / s }
        return w
    }

    /** Channel input: feature k drives channels 2k (short tau) and 2k + 1 (long tau). */
    fun channels(x: DoubleArray): DoubleArray = DoubleArray(CHANNELS) { x[it / 2] }
}
