package at.heinz.deviceai.research.exp20.p2

import at.heinz.deviceai.cognitive.ContinuumTaskClassifier
import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy
import at.heinz.deviceai.research.argon4.DialogStateKernel
import at.heinz.deviceai.research.exp20.BoundedCtrnn
import at.heinz.deviceai.research.exp20.LeakyIntegrators
import java.nio.ByteBuffer
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p

/** A: real router path. A0: same readout, no state. A_DSK: existing (unwired) DialogStateKernel. B: leaky. C: P1 CTRNN. */
enum class ArmId { A, A0, A_DSK, B, C }

/** State kernel behind one temporal arm; both wrap the unchanged P1 classes. */
interface TemporalKernel {
    val dim: Int
    fun reset()
    fun step(u: DoubleArray, dt: Double): Boolean
    fun state(): DoubleArray
}

class LeakyKernel(taus: DoubleArray = P2Config.taus()) : TemporalKernel {
    private val k = LeakyIntegrators(taus)
    override val dim = taus.size
    override fun reset() = k.reset()
    override fun step(u: DoubleArray, dt: Double) = k.step(u, dt)
    override fun state() = k.state()
}

class CtrnnKernel(taus: DoubleArray = P2Config.taus(), w: Array<DoubleArray> = P2Config.ctrnnWeights()) : TemporalKernel {
    private val k = BoundedCtrnn(taus, w, DoubleArray(taus.size))
    override val dim = taus.size
    override fun reset() = k.reset(DoubleArray(dim))
    override fun step(u: DoubleArray, dt: Double) = k.step(u, dt)
    override fun state() = k.state()
}

enum class TimeStatus { OK, DUPLICATE_IGNORED, REJECTED_NON_FINITE, REJECTED_NON_MONOTONIC, REJECTED_INPUT }

/**
 * Event protocol shared by every temporal arm: on a turn at monotonic time t, first decay with zero input over
 * dt = t - t_prev, then hold the turn's channel input for INJECT_SEC. Validation happens BEFORE any state change, so
 * a rejected event (non-finite t, t going backwards, bad input) leaves the state exactly as it was. A redelivered
 * event (same t, same input) is ignored. A restart is reset(): no state survives a session or a process.
 */
class TemporalTrack(private val kernel: TemporalKernel) {
    private var lastT: Double? = null
    private var lastU: DoubleArray? = null
    private val zero = DoubleArray(kernel.dim)
    var lastDt = 0.0
        private set

    fun reset() { kernel.reset(); lastT = null; lastU = null; lastDt = 0.0 }

    fun observe(t: Double, u: DoubleArray): TimeStatus {
        if (!t.isFinite()) return TimeStatus.REJECTED_NON_FINITE
        if (u.size != kernel.dim || u.any { !it.isFinite() }) return TimeStatus.REJECTED_INPUT
        val prev = lastT
        val dt = if (prev == null) 0.0 else t - prev
        if (dt < 0.0 || !dt.isFinite()) return TimeStatus.REJECTED_NON_MONOTONIC
        if (dt == 0.0 && lastU?.contentEquals(u) == true) return TimeStatus.DUPLICATE_IGNORED
        if (dt > 0.0) check(kernel.step(zero, dt)) { "validated decay step refused" }
        check(kernel.step(u, P2Config.INJECT_SEC)) { "validated injection step refused" }
        lastT = t; lastU = u.copyOf(); lastDt = dt
        return TimeStatus.OK
    }

    fun state() = kernel.state()
}

object P2Features {
    fun gap(dt: Double) = ln1p(dt / P2Config.DT_SCALE_SEC)

    /** Readout input at the decision turn, or null when the decision turn's time was rejected (no proposal). */
    fun vector(arm: ArmId, d: Dialog): DoubleArray? = when (arm) {
        ArmId.A0 -> {
            val t = d.turns; val dt = if (t.size >= 2) t.last().tSec - t[t.size - 2].tSec else 0.0
            if (!dt.isFinite() || dt < 0.0) null else P2Encoder.encode(d.decision.text) + gap(dt)
        }
        ArmId.B, ArmId.C -> {
            val track = TemporalTrack(if (arm == ArmId.B) LeakyKernel() else CtrnnKernel())
            var status = TimeStatus.OK
            var x = DoubleArray(P2Config.FEATURES)
            for (turn in d.turns) { x = P2Encoder.encode(turn.text); status = track.observe(turn.tSec, P2Config.channels(x)) }
            if (status != TimeStatus.OK && status != TimeStatus.DUPLICATE_IGNORED) null else x + gap(track.lastDt) + track.state()
        }
        else -> error("$arm has no readout")
    }
}

/** L2-regularised logistic readout (bias unpenalised) on z-scored inputs, fitted by damped Newton; deterministic. */
class LogisticReadout private constructor(private val mean: DoubleArray, private val sd: DoubleArray, val weights: DoubleArray) {
    fun p(z: DoubleArray): Double {
        var s = weights[0]
        for (i in z.indices) s += weights[i + 1] * (z[i] - mean[i]) / sd[i]
        return 1.0 / (1.0 + exp(-s))
    }

    fun sha256(): String {
        val bb = ByteBuffer.allocate(8 * (weights.size + mean.size + sd.size))
        for (a in listOf(weights, mean, sd)) for (v in a) bb.putLong(java.lang.Double.doubleToRawLongBits(v))
        return P2Tsv.sha256(bb.array())
    }

    companion object {
        fun fit(zs: List<DoubleArray>, y: IntArray, lambda: Double): LogisticReadout {
            require(zs.isNotEmpty() && zs.size == y.size && lambda > 0.0)
            val n = zs.size; val m = zs[0].size; val d = m + 1
            val mean = DoubleArray(m) { j -> zs.sumOf { it[j] } / n }
            val sd = DoubleArray(m) { j -> val v = zs.sumOf { (it[j] - mean[j]) * (it[j] - mean[j]) } / n; if (v < 1e-24) 1.0 else kotlin.math.sqrt(v) }
            val x = Array(n) { i -> DoubleArray(d) { j -> if (j == 0) 1.0 else (zs[i][j - 1] - mean[j - 1]) / sd[j - 1] } }
            val w = DoubleArray(d)
            fun loss(v: DoubleArray): Double {
                var l = 0.0
                for (i in 0 until n) { var s = 0.0; for (j in 0 until d) s += v[j] * x[i][j]
                    l += if (y[i] == 1) ln1p(exp(-s)).let { if (it.isFinite()) it else -s } else ln1p(exp(s)).let { if (it.isFinite()) it else s } }
                var r = 0.0; for (j in 1 until d) r += v[j] * v[j]
                return l / n + 0.5 * lambda * r
            }
            repeat(200) {
                val g = DoubleArray(d); val h = Array(d) { DoubleArray(d) }
                for (i in 0 until n) {
                    var s = 0.0; for (j in 0 until d) s += w[j] * x[i][j]
                    val p = 1.0 / (1.0 + exp(-s)); val e = p - y[i]; val q = p * (1.0 - p)
                    for (j in 0 until d) { g[j] += e * x[i][j] / n; for (k in 0 until d) h[j][k] += q * x[i][j] * x[i][k] / n }
                }
                for (j in 1 until d) { g[j] += lambda * w[j]; h[j][j] += lambda }
                h[0][0] += 1e-12
                val step = solve(h, g)
                var a = 1.0; val l0 = loss(w)
                while (a > 1e-8) { val c = DoubleArray(d) { w[it] - a * step[it] }; if (loss(c) <= l0) { c.copyInto(w); break }; a /= 2 }
                if (a <= 1e-8 || step.maxOf { kotlin.math.abs(it) } * a < 1e-11) return LogisticReadout(mean, sd, w)
            }
            return LogisticReadout(mean, sd, w)
        }

        private fun solve(a0: Array<DoubleArray>, b0: DoubleArray): DoubleArray {
            val n = b0.size; val a = Array(n) { a0[it].copyOf() }; val b = b0.copyOf()
            for (c in 0 until n) {
                var p = c; for (r in c + 1 until n) if (kotlin.math.abs(a[r][c]) > kotlin.math.abs(a[p][c])) p = r
                val tr = a[c]; a[c] = a[p]; a[p] = tr; val tb = b[c]; b[c] = b[p]; b[p] = tb
                for (r in c + 1 until n) { val f = a[r][c] / a[c][c]; for (k in c until n) a[r][k] -= f * a[c][k]; b[r] -= f * b[c] }
            }
            val out = DoubleArray(n)
            for (r in n - 1 downTo 0) { var s = b[r]; for (k in r + 1 until n) s -= a[r][k] * out[k]; out[r] = s / a[r][r] }
            return out
        }
    }
}

/** Signals built exactly as ChatReasoningRouter.advise builds them (ed408a7), except the arm's unresolvedReference. */
object RouterBridge {
    fun signals(task: String, unresolvedReference: Boolean) = ComputeAllocationPolicy.Signals(
        evidence = ComputeAllocationPolicy.EvidenceDecision.NONE,          // no groundedRecall in the synthetic set
        coverage = 0.0, claimFlags = 0, unresolvedReference = unresolvedReference, freshnessNeeded = false,
        toolIntent = ContinuumTaskClassifier.classify(task).expectedToolUse,
        personal = false, modelReady = true, internetAllowed = false, remainingModelCalls = 4
    )

    fun action(task: String, unresolvedReference: Boolean): ComputeAllocationPolicy.Action =
        ComputeAllocationPolicy().choose(signals(task, unresolvedReference)).action
}

/** The already existing turn-indexed kernel, observed on the same public turns; it never sees the clock. */
object DskBridge {
    fun wouldClarify(d: Dialog): Boolean = runCatching {
        val k = DialogStateKernel("p2-${d.clusterId}-${d.variant}", { LocalDate.of(2026, 10, 30) })
        var last: DialogStateKernel.Resolution? = null
        for (t in d.turns) last = k.observeUser(t.text)
        last?.decision == DialogStateKernel.Decision.CLARIFY
    }.getOrDefault(false)
}
