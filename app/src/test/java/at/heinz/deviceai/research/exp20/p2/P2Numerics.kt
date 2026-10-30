package at.heinz.deviceai.research.exp20.p2

import at.heinz.deviceai.research.exp20.BoundedCtrnn
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.tanh
import kotlin.random.Random

/**
 * EXP-20 P2 · Part N (preregistered in EXP-20-P2-PREREGISTRATION-20261030.md section 7), separate from the quality
 * comparison and run on TRAIN/VALIDATION dialogs only. Same weights, taus, inputs and event protocol as arm C.
 *   tau_i dh_i/dt = -h_i + sum_j W_ij tanh(h_j) + clip(u_i) + b_i,  u piecewise constant per phase
 * DENSE_P1 is arm C itself (BoundedCtrnn): one exponential-Euler step per phase with the drive frozen at the start
 * of the phase. That is exact only for the linear leak under a frozen drive, not for the coupled nonlinear system.
 *   numerics <dataDir> <outFile>
 */
object P2Numerics {
    private val tau = P2Config.taus()
    private val w = P2Config.ctrnnWeights()
    private val n = P2Config.CHANNELS
    private val rho = w.maxOf { r -> r.sumOf { abs(it) } }

    /** A phase = (input, duration). The event protocol of TemporalTrack: decay with zero input, then 10 s injection. */
    fun phases(d: Dialog): List<Pair<DoubleArray, Double>> {
        val out = ArrayList<Pair<DoubleArray, Double>>(); var prev: Double? = null
        for (t in d.turns) {
            val dt = prev?.let { t.tSec - it } ?: 0.0
            if (dt > 0.0) out += DoubleArray(n) to dt
            out += P2Config.channels(P2Encoder.encode(t.text)) to P2Config.INJECT_SEC
            prev = t.tSec
        }
        return out
    }

    /** out_i = clip(u_i) + b_i + sum_j W_ij tanh(h_j), b = 0; same summation order as BoundedCtrnn.step. */
    private fun drives(h: DoubleArray, u: DoubleArray, act: DoubleArray, out: DoubleArray) {
        for (j in 0 until n) act[j] = tanh(h[j])
        for (i in 0 until n) { var s = 0.0 + u[i].coerceIn(-1.0, 1.0); val row = w[i]; for (j in 0 until n) s += row[j] * act[j]; out[i] = s }
    }

    interface Variant { fun step(u: DoubleArray, dt: Double); fun state(): DoubleArray }

    class DenseP1 : Variant {
        private val k = BoundedCtrnn(tau, w, DoubleArray(n))
        override fun step(u: DoubleArray, dt: Double) { check(k.step(u, dt)) }
        override fun state() = k.state()
    }

    /** Exponential Euler with frozen drive; oneMinusA chooses 1 - exp(-x) (P1) or -expm1(-x). */
    class ExpEuler(private val maxSub: Double, private val useExpm1: Boolean, private val leakOnlyInGaps: Boolean = false) : Variant {
        private val h = DoubleArray(n); private val d = DoubleArray(n); private val act = DoubleArray(n)
        override fun step(u: DoubleArray, dt: Double) {
            val gap = u.all { it == 0.0 }
            val m = if (maxSub.isInfinite()) 1 else maxOf(1, ceil(dt / maxSub).toInt())
            val s = dt / m
            repeat(m) {
                drives(h, u, act, d)
                for (i in 0 until n) {
                    val x = s / tau[i]; val a = exp(-x); val om = if (useExpm1) -expm1(-x) else 1.0 - a
                    h[i] = if (leakOnlyInGaps && gap) a * h[i] else a * h[i] + om * d[i]
                }
            }
        }
        override fun state() = h.copyOf()
    }

    /**
     * Event-driven sparse drive: cached activations c_j; column j is pushed into the incremental recurrent drive r only
     * when |tanh(h_j) - c_j| > theta. tanh(h_j) must still be evaluated for every j to detect the event.
     * One step differs from the dense step by at most max_i (1 - a_i) * rho * theta plus the propagated old error.
     */
    class Sparse(private val theta: Double) : Variant {
        private val h = DoubleArray(n); private val c = DoubleArray(n); private val r = DoubleArray(n)
        var columnUpdates = 0L; var columnChecks = 0L
        override fun step(u: DoubleArray, dt: Double) {
            for (j in 0 until n) {
                val t = tanh(h[j]); columnChecks++
                val delta = t - c[j]
                if (abs(delta) > theta) { for (i in 0 until n) r[i] += w[i][j] * delta; c[j] = t; columnUpdates++ }
            }
            for (i in 0 until n) { val a = exp(-dt / tau[i]); h[i] = a * h[i] + (1.0 - a) * (u[i].coerceIn(-1.0, 1.0) + r[i]) }
        }
        override fun state() = h.copyOf()
        fun oneStepSlack(dt: Double) = (0 until n).maxOf { 1.0 - exp(-dt / tau[it]) } * rho * theta
        fun q(dt: Double) = (0 until n).maxOf { val a = exp(-dt / tau[it]); a + (1.0 - a) * rho }
    }

    /** Classic RK4 of the original ODE with constant input per phase and step <= hMax. */
    class Rk4(private val hMax: Double) : Variant {
        private val h = DoubleArray(n)
        private val act = DoubleArray(n); private val dr = DoubleArray(n)
        private fun f(y: DoubleArray, u: DoubleArray, out: DoubleArray) { drives(y, u, act, dr); for (i in 0 until n) out[i] = (-y[i] + dr[i]) / tau[i] }
        private val k1 = DoubleArray(n); private val k2 = DoubleArray(n); private val k3 = DoubleArray(n); private val k4 = DoubleArray(n); private val y = DoubleArray(n)
        override fun step(u: DoubleArray, dt: Double) {
            val m = maxOf(1, ceil(dt / hMax).toInt()); val s = dt / m
            repeat(m) {
                f(h, u, k1); for (i in 0 until n) y[i] = h[i] + 0.5 * s * k1[i]
                f(y, u, k2); for (i in 0 until n) y[i] = h[i] + 0.5 * s * k2[i]
                f(y, u, k3); for (i in 0 until n) y[i] = h[i] + s * k3[i]
                f(y, u, k4); for (i in 0 until n) h[i] += s / 6.0 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i])
            }
        }
        override fun state() = h.copyOf()
    }

    private fun inf(a: DoubleArray, b: DoubleArray) = a.indices.maxOf { abs(a[it] - b[it]) }

    @JvmStatic fun main(args: Array<String>) {
        val dataDir = File(args[0]); val out = File(args[1])
        val sb = StringBuilder("EXP-20 P2 Part N numerics (host JVM ${System.getProperty("java.version")}, ${System.getProperty("os.arch")})\n")
        val train = P2Tsv.read(File(dataDir, "train.tsv")); val validation = P2Tsv.read(File(dataDir, "validation.tsv"))
        val minTau = tau.min()
        sb.append("n=$n rho=$rho minTau=$minTau maxTau=${tau.max()} injection=${P2Config.INJECT_SEC}s\n\n")

        // N1a: reference validity (h vs h/2) on VALIDATION
        var refSelf = 0.0
        val refStates = validation.map { d ->
            val a = Rk4(0.02 * minTau); val b = Rk4(0.01 * minTau)
            for ((u, dt) in phases(d)) { a.step(u, dt); b.step(u, dt) }
            refSelf = maxOf(refSelf, inf(a.state(), b.state())); b.state()
        }
        sb.append("N1 reference RK4 h=0.02*minTau vs h=0.01*minTau on ${validation.size} VALIDATION dialogs: max ||.||inf=%.3e (valid iff < 1e-9: %s)\n".format(refSelf, refSelf < 1e-9))

        // N1b: variants vs DENSE_P1 (same discrete contract) and vs reference, at the decision turn
        val makers = linkedMapOf<String, () -> Variant>(
            "DENSE_SUBSTEP" to { ExpEuler(minTau / 10, false) }, "EXPM1" to { ExpEuler(Double.POSITIVE_INFINITY, true) },
            "P1_REIMPL" to { ExpEuler(Double.POSITIVE_INFINITY, false) }, "LEAK_ONLY_GAP" to { ExpEuler(Double.POSITIVE_INFINITY, false, true) },
            "SPARSE_1e-3" to { Sparse(1e-3) }, "SPARSE_1e-2" to { Sparse(1e-2) })
        val denseVal = validation.map { d -> DenseP1().also { v -> for ((u, dt) in phases(d)) v.step(u, dt) }.state() }
        sb.append("DENSE_P1 vs reference at decision: max ||.||inf=%.3e\n".format(validation.indices.maxOf { inf(denseVal[it], refStates[it]) }))
        val variantVal = LinkedHashMap<String, List<DoubleArray>>()
        for ((name, mk) in makers) {
            var vsDense = 0.0; var vsRef = 0.0; var bound = 0; var steps = 0L; var upd = 0L; var chk = 0L
            val states = validation.mapIndexed { idx, d ->
                val v = mk(); val dense = DenseP1(); var e = 0.0
                for ((u, dt) in phases(d)) {
                    val slack = (v as? Sparse)?.let { it.q(dt) * e + it.oneStepSlack(dt) }
                    v.step(u, dt); dense.step(u, dt); steps++
                    e = inf(v.state(), dense.state())
                    vsDense = maxOf(vsDense, e)
                    if (slack != null && e > slack + 1e-12) bound++
                }
                (v as? Sparse)?.let { upd += it.columnUpdates; chk += it.columnChecks }
                vsRef = maxOf(vsRef, inf(v.state(), refStates[idx])); v.state()
            }
            variantVal[name] = states
            sb.append("%-14s max_step ||v-dense||inf=%.3e  decision ||v-ref||inf=%.3e".format(name, vsDense, vsRef) +
                (if (name.startsWith("SPARSE")) "  bound violations=$bound/$steps  column updates=$upd/$chk (%.1f%%)".format(100.0 * upd / chk) else "") + "\n")
        }

        // N1c: routing flips with the frozen C readout (refit from TRAIN, hash must equal the freeze)
        val (zs, y) = P2Eval.trainingSet(ArmId.C, train)
        val readout = LogisticReadout.fit(zs, y, 0.001)
        sb.append("\nfrozen C readout sha256=${readout.sha256()} (freeze a6ed4695bbeb41df14b39dacf01c9f6fdb43b7d5c54c20ac5b3285c02e44ef26: ${readout.sha256() == "a6ed4695bbeb41df14b39dacf01c9f6fdb43b7d5c54c20ac5b3285c02e44ef26"})\n")
        fun propose(d: Dialog, s: DoubleArray): Boolean {
            val t = d.turns; val dt = if (t.size >= 2) t.last().tSec - t[t.size - 2].tSec else 0.0
            return readout.p(P2Encoder.encode(d.decision.text) + P2Features.gap(dt) + s) >= 0.5
        }
        val both = train + validation
        val denseBoth = both.map { d -> DenseP1().also { v -> for ((u, dt) in phases(d)) v.step(u, dt) }.state() }
        val denseProp = both.indices.map { propose(both[it], denseBoth[it]) }
        for ((name, mk) in makers) {
            val flips = both.indices.count { i -> val v = mk(); for ((u, dt) in phases(both[i])) v.step(u, dt); propose(both[i], v.state()) != denseProp[i] }
            sb.append("routing flips vs DENSE_P1 on TRAIN+VALIDATION (${both.size}): %-14s %d\n".format(name, flips))
        }
        val refFlips = validation.indices.count { propose(validation[it], refStates[it]) != propose(validation[it], denseVal[it]) }
        sb.append("routing flips vs DENSE_P1 on VALIDATION (${validation.size}): REFERENCE_ODE  $refFlips\n")

        // N2: 1 - exp(-x) vs -expm1(-x)
        sb.append("\nN2 relative error against a 50-digit series of 1 - e^-x\n")
        val mc = MathContext(50)
        for (x in doubleArrayOf(1e-6, 1e-8, 1e-10, 1e-12, 1e-14, 1e-15, 1e-16, 1e-17)) {
            val bx = BigDecimal(x); var term = bx; var sum = BigDecimal.ZERO
            for (k in 1..8) { sum = sum.add(term, mc); term = term.multiply(bx, mc).divide(BigDecimal(-(k + 1)), mc) }
            fun rel(v: Double) = BigDecimal(v).subtract(sum, mc).abs().divide(sum, mc).toDouble()
            sb.append("  x=%.0e  1-exp(-x): rel=%.3e   -expm1(-x): rel=%.3e\n".format(x, rel(1.0 - exp(-x)), rel(-expm1(-x))))
        }
        for ((dt, steps) in listOf(1e-6 to 1_000_000, 1e-17 to 1_000_000)) {
            var p1 = 0.0; var em = 0.0
            repeat(steps) { val a = exp(-dt); p1 = a * p1 + (1.0 - a) * 1.0; em = a * em + (-expm1(-dt)) * 1.0 }
            val exact = -expm1(-dt * steps)
            sb.append("  accumulate tau=1 u=1 dt=%.0e x %d: exact=%.15e  P1 abs err=%.3e  expm1 abs err=%.3e\n".format(dt, steps, exact, abs(p1 - exact), abs(em - exact)))
        }
        var convexViol = 0; var nonUnit = 0; var trials = 0
        val r = Random(20261030)
        repeat(2000) {
            val m = r.nextInt(2, 33); val rr = r.nextDouble(0.0, 0.95)
            val ww = Array(m) { DoubleArray(m) { r.nextDouble(-1.0, 1.0) } }; for (row in ww) { val s = row.sumOf { abs(it) }; for (j in row.indices) row[j] *= rr / s }
            val tt = DoubleArray(m) { exp(r.nextDouble(kotlin.math.ln(0.05), kotlin.math.ln(20.0))) }
            val bb = DoubleArray(m) { r.nextDouble(-0.5, 0.5) }; val h = DoubleArray(m) { r.nextDouble(-5.0, 5.0) }; val h0 = h.copyOf()
            repeat(200) {
                val x = DoubleArray(m) { r.nextDouble(-3.0, 3.0) }; val dt = exp(r.nextDouble(kotlin.math.ln(1e-4), kotlin.math.ln(50.0)))
                val act = DoubleArray(m) { tanh(h[it]) }
                for (i in 0 until m) {
                    var dr = bb[i] + x[i].coerceIn(-1.0, 1.0); for (j in 0 until m) dr += ww[i][j] * act[j]
                    val a = exp(-dt / tt[i]); val om = -expm1(-dt / tt[i]); if (a + om != 1.0) nonUnit++
                    h[i] = a * h[i] + om * dr; trials++
                    if (abs(h[i]) > maxOf(abs(h0[i]), ww[i].sumOf { abs(it) } + abs(bb[i]) + 1.0) + 1e-12) convexViol++
                }
            }
        }
        sb.append("  expm1 form in the P1 random protocol (2000 nets x 200 steps): bound violations=$convexViol/$trials, a+(1-a)!=1 exactly in $nonUnit/$trials updates\n")

        // N3: degenerate inputs on the real P1 class
        sb.append("\nN3 degenerate cases on BoundedCtrnn (P1 class, after the aliasing fix)\n")
        val net = BoundedCtrnn(doubleArrayOf(1.0, 5.0), arrayOf(doubleArrayOf(0.3, 0.2), doubleArrayOf(-0.1, 0.4)), doubleArrayOf(0.0, 0.0))
        net.reset(doubleArrayOf(0.7, -0.3)); val s0 = net.state()
        for ((label, dt) in listOf("dt=0" to 0.0, "dt<0" to -1.0, "dt=NaN" to Double.NaN, "dt=+Inf" to Double.POSITIVE_INFINITY, "dt=-Inf" to Double.NEGATIVE_INFINITY))
            sb.append("  %-8s step=%s unchanged=%s\n".format(label, net.step(doubleArrayOf(0.5, 0.5), dt), net.state().contentEquals(s0)))
        sb.append("  tau=0 rejected=${runCatching { BoundedCtrnn(doubleArrayOf(0.0), arrayOf(doubleArrayOf(0.0)), doubleArrayOf(0.0)) }.isFailure}\n")
        for ((label, t, dt) in listOf(Triple("dt=1e300", 1.0, 1e300), Triple("tau=1e-300,dt=1", 1e-300, 1.0), Triple("tau=1e-300,dt=1e10", 1e-300, 1e10), Triple("tau=1e300,dt=1e-300", 1e300, 1e-300))) {
            val k = BoundedCtrnn(doubleArrayOf(t), arrayOf(doubleArrayOf(0.5)), doubleArrayOf(0.1)); k.reset(doubleArrayOf(0.9))
            val ok = k.step(doubleArrayOf(0.4), dt)
            sb.append("  %-20s step=%s state=%s finite=%s\n".format(label, ok, k.state()[0], k.state()[0].isFinite()))
        }
        val rep = BoundedCtrnn(tau, w, DoubleArray(n)); val u1 = DoubleArray(n) { 1.0 }
        repeat(1_000_000) { rep.step(u1, P2Config.INJECT_SEC) }
        sb.append("  1e6 repeated injections: finite=${rep.state().all { it.isFinite() }} max|h|=%.6f <= bound %.6f\n".format(rep.state().maxOf { abs(it) }, (0 until n).maxOf { rep.bound(it) }))

        out.parentFile?.mkdirs(); out.writeText(sb.toString()); print(sb)
    }
}
