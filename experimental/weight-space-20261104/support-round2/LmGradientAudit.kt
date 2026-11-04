package at.heinz.deviceai.learning.lm

import java.io.File
import kotlin.math.*

/** Synthetic, deterministic derivative audit; uses the unmodified production sources. */
object LmGradientAudit {
    private val ids = intArrayOf(4, 7, 4, 12, 7, 4)
    private val targets = intArrayOf(7, 4, 12, 7, 4, 1)
    private val steps = doubleArrayOf(1e-3, 3e-4, 1e-4)
    private const val ABS_TOL = 3e-5
    private const val REL_TOL = 3e-3
    private fun tolerance(x: Double) = ABS_TOL + REL_TOL * abs(x)
    private fun grads(model: LmModel, shared: LinkedHashMap<String, Mat>? = null): LinkedHashMap<String, Mat> {
        val g = shared ?: model.params.zeros()
        val tape = Tape()
        val nodes = model.params.mats.mapValues { (n, p) -> tape.param(p, g.getValue(n)) }
        tape.backward(tape.crossEntropy(model.logits(tape, nodes, ids), targets))
        return g
    }

    /** Separate input/output buffers while calling the actual model forward, whose tok is read twice. */
    private fun splitEmbedding(model: LmModel): Pair<Mat, Mat> {
        val tape = Tape()
        val tok = model.params.mats.getValue("tok")
        val input = Mat(tok.rows, tok.cols)
        val output = Mat(tok.rows, tok.cols)
        val nodes = model.frozenNodes(tape)
        var tokReads = 0
        val split = object : Map<String, Node> by nodes {
            override fun get(key: String): Node? = if (key == "tok") {
                tape.param(tok, if (tokReads++ == 0) input else output)
            } else nodes[key]
        }
        tape.backward(tape.crossEntropy(model.logits(tape, split, ids), targets))
        check(tokReads == 2) { "production tok lookup contract changed: $tokReads" }
        return input to output
    }

    /** Independent Float64 forward oracle: no Tape, Kernels, or production activation calls. */
    private fun oracle(model: LmModel, changed: String = "", index: Int = -1, delta: Double = 0.0): Double {
        val cfg = model.config
        val d = cfg.dim
        val n = ids.size
        val parameters = model.params.mats.mapValues { (name, p) ->
            DoubleArray(p.data.size) { i -> p.data[i].toDouble() + if (name == changed && i == index) delta else 0.0 }
        }
        fun p(name: String) = parameters.getValue(name)
        fun mm(a: DoubleArray, w: DoubleArray, k: Int, cols: Int): DoubleArray =
            DoubleArray(n * cols) { ij ->
                val r = ij / cols; val c = ij % cols
                var s = 0.0
                for (j in 0 until k) s += a[r * k + j] * w[j * cols + c]
                s
            }
        fun norm(a: DoubleArray, gain: DoubleArray): DoubleArray {
            val out = DoubleArray(a.size)
            for (r in 0 until n) {
                var sq = 0.0
                for (j in 0 until d) sq += a[r * d + j] * a[r * d + j]
                val inv = 1.0 / sqrt(sq / d + 1e-5f.toDouble())
                for (j in 0 until d) out[r * d + j] = a[r * d + j] * inv * gain[j]
            }
            return out
        }
        val emb = p("tok")
        var x = DoubleArray(n * d) { i -> emb[ids[i / d] * d + i % d] }
        for (l in 0 until cfg.layers) {
            val prefix = "l$l."
            val h = norm(x, p(prefix + "n1"))
            val z = mm(h, p(prefix + "wz"), d, d)
            val candidate = mm(h, p(prefix + "wc"), d, d)
            val state = DoubleArray(n * d)
            for (t in 0 until n) for (j in 0 until d) {
                val i = t * d + j
                val gate = 1.0 / (1.0 + exp(-(z[i] + p(prefix + "bz")[j])))
                state[i] = (1.0 - gate) * (if (t == 0) 0.0 else state[i - d]) + gate * candidate[i]
            }
            val gate = mm(h, p(prefix + "wg"), d, d)
            val mixed = mm(DoubleArray(n * d) { i -> state[i] * gate[i] / (1.0 + exp(-gate[i])) }, p(prefix + "wo"), d, d)
            x = DoubleArray(n * d) { i -> x[i] + mixed[i] }
            val ff = mm(norm(x, p(prefix + "n2")), p(prefix + "w1"), d, cfg.mlp)
            val geluC = sqrt(2.0 / Math.PI).toFloat().toDouble()
            for (i in ff.indices) {
                val v = ff[i] + p(prefix + "b1")[i % cfg.mlp]
                ff[i] = 0.5 * v * (1.0 + tanh(geluC * (v + 0.044715f.toDouble() * v * v * v)))
            }
            val residual = mm(ff, p(prefix + "w2"), cfg.mlp, d)
            x = DoubleArray(n * d) { i -> x[i] + residual[i] + p(prefix + "b2")[i % d] }
        }
        x = norm(x, p("nf"))
        var loss = 0.0
        for (t in 0 until n) {
            val logits = DoubleArray(cfg.vocab) { v -> (0 until d).sumOf { j -> x[t * d + j] * emb[v * d + j] } }
            val max = logits.max()
            loss += max + ln(logits.sumOf { exp(it - max) }) - logits[targets[t]]
        }
        return loss / n
    }

    private fun numeric(model: LmModel, name: String, index: Int, step: Double): Double {
        val values = model.params.mats.getValue(name).data
        val old = values[index]
        val plus = (old.toDouble() + step).toFloat()
        val minus = (old.toDouble() - step).toFloat()
        try {
            values[index] = plus
            val hi = model.nll(ids, targets).average()
            values[index] = minus
            val lo = model.nll(ids, targets).average()
            return (hi - lo) / (plus.toDouble() - minus.toDouble())
        } finally { values[index] = old }
    }

    @JvmStatic fun main(args: Array<String>) {
        val evidence = File(args.single()).also { it.mkdirs() }
        println("LM_GRADIENT_AUDIT_R2 seed=490514 vocab=258 dim=8 layers=2 context=8 mlp=12")
        println("ids=${ids.joinToString(",")} targets=${targets.joinToString(",")} unscaled_embeddings=true")
        println("fixed_tolerance=3e-5+0.003*abs(reference); float_steps=${steps.joinToString(",")}; double_steps=1e-5,5e-6")
        var allFailures = 0
        for (zeroOutput in listOf(false, true)) {
            val scenario = if (zeroOutput) "zero_l1_outputs" else "original_init"
            val params = LmParams.init(LmConfig(LmArch.MINGRU, 258, 8, 2, context = 8, mlp = 12), 490514L)
            if (zeroOutput) for (name in listOf("l1.wo", "l1.w2", "l1.b2")) params.mats.getValue(name).data.fill(0f)
            val model = LmModel(params)
            val g = grads(model)
            val (input, output) = splitEmbedding(model)
            var splitMax = 0.0
            for (i in input.data.indices) splitMax = max(splitMax, abs(g.getValue("tok").data[i].toDouble() - input.data[i] - output.data[i]))
            check(splitMax < 2e-6) { "split embedding accumulation mismatch: $splitMax" }
            val controlIndex = input.data.indices.maxBy { abs(input.data[it]) }
            val coordinates = mutableListOf<Pair<String, Int>>()
            for ((name, p) in params.mats) {
                val indices = if (name == "tok") ids.distinct().flatMap { row -> (0 until 8).map { row * 8 + it } } + listOf(257 * 8, controlIndex)
                    else listOf(0, p.data.size / 3, p.data.size / 2, p.data.lastIndex)
                for (i in indices.distinct()) coordinates += name to i
            }
            var doubleFailures = 0
            val floatFailures = IntArray(steps.size)
            var maxDoubleError = 0.0
            var maxConvergence = 0.0
            File(evidence, "$scenario.tsv").printWriter().use { out ->
                out.println("parameter\tindex\tanalytic\toracle_h1e-5\toracle_h5e-6\toracle_error\ttolerance\tfloat_h1e-3\tfloat_h3e-4\tfloat_h1e-4")
                for ((name, i) in coordinates) {
                    val analytic = g.getValue(name).data[i].toDouble()
                    fun fd(h: Double) = (oracle(model, name, i, h) - oracle(model, name, i, -h)) / (2 * h)
                    val d1 = fd(1e-5)
                    val d2 = fd(5e-6)
                    check(analytic.isFinite() && d1.isFinite() && d2.isFinite()) {
                        "$scenario $name[$i] nonfinite derivative: analytic=$analytic d1=$d1 d2=$d2"
                    }
                    val error = abs(analytic - d2)
                    maxDoubleError = max(maxDoubleError, error)
                    maxConvergence = max(maxConvergence, abs(d1 - d2))
                    if (error > tolerance(d2)) doubleFailures++
                    val actual = steps.mapIndexed { si, h -> numeric(model, name, i, h).also {
                        check(it.isFinite()) { "$scenario $name[$i] nonfinite Float derivative at h=$h: $it" }
                        if (abs(it - analytic) > tolerance(d2)) floatFailures[si]++
                    } }
                    out.println(listOf(name, i, analytic, d1, d2, error, tolerance(d2)).joinToString("\t") + "\t" + actual.joinToString("\t"))
                }
            }
            val h = 5e-6
            val reference = (oracle(model, "tok", controlIndex, h) - oracle(model, "tok", controlIndex, -h)) / (2 * h)
            val broken = output.data[controlIndex].toDouble()
            val brokenError = abs(reference - broken)
            check(brokenError > 10 * tolerance(reference)) { "missing-input-gradient negative control not discriminating" }
            val shared = params.zeros()
            grads(model, shared); grads(model, shared)
            var sharedMax = 0.0
            for ((name, p) in shared) for (i in p.data.indices) sharedMax = max(sharedMax, abs(p.data[i].toDouble() - 2.0 * g.getValue(name).data[i]))
            check(sharedMax < 3e-6) { "shared accumulation mismatch: $sharedMax" }
            println("scenario=$scenario parameters=${params.count} checked=${coordinates.size} nll=${model.nll(ids, targets).average()} oracle_nll=${oracle(model)}")
            println("double_failures=$doubleFailures max_abs_error=$maxDoubleError max_double_step_disagreement=$maxConvergence float_failures_by_step=${floatFailures.joinToString(",")}")
            println("split_embedding_max_error=$splitMax shared_two_tapes_max_error=$sharedMax negative_control_index=$controlIndex true=$reference broken_output_only=$broken broken_error=$brokenError tolerance=${tolerance(reference)} NEGATIVE_CONTROL_REJECTED")
            if (zeroOutput) {
                val blocked = listOf("n1", "wz", "bz", "wc", "wg", "n2", "w1", "b1")
                for (leaf in blocked) check(g.getValue("l1.$leaf").data.all { it == 0f }) { "upstream zero-output gradient is nonzero: $leaf" }
                for (leaf in listOf("wo", "w2", "b2")) {
                    val maxAbs = g.getValue("l1.$leaf").data.maxOf { abs(it) }
                    check(maxAbs > 0f)
                    println("zero_output_opening_gradient l1.$leaf max_abs=$maxAbs")
                }
                val oneConfig = params.config.copy(layers = 1)
                val oneParams = LmParams(oneConfig, LinkedHashMap(oneConfig.layout().associate { (n, _, _) -> n to params.mats.getValue(n).copy() }))
                val oneNll = LmModel(oneParams).nll(ids, targets)
                val twoNll = model.nll(ids, targets)
                check(oneNll.contentEquals(twoNll)) { "zero-output layer is not forward identity" }
                println("zero_output_upstream_exact_zero=true forward_identity_exact=true")
            }
            allFailures += doubleFailures
        }
        println("RESULT double_reference_failures=$allFailures; Float32 curves retained without changing tolerances")
        check(allFailures == 0) { "derivative discrepancies require investigation" }
    }
}
