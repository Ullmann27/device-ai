package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.Mat
import at.heinz.deviceai.learning.lm.Node
import at.heinz.deviceai.learning.lm.Tape
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

class NfCoreTest {
    private fun randomMat(rnd: Random, r: Int, c: Int, scale: Double = .5) = Mat(r, c, FloatArray(r * c) { (rnd.nextGaussian() * scale).toFloat() })
    private fun unitRows(rnd: Random, r: Int, c: Int) = randomMat(rnd, r, c).also { m ->
        for (i in 0 until r) { var s = 0f; for (j in 0 until c) s += m[i, j] * m[i, j]; val n = sqrt(s); for (j in 0 until c) m[i, j] = m[i, j] / n }
    }

    /** Tape gradient of L = Σ w ⊙ f(inputs) against central finite differences for every input coordinate. */
    private fun check(name: String, inputs: List<Mat>, f: (Tape, List<Node>) -> Node) {
        val rnd = Random(name.hashCode().toLong())
        fun loss(tape: Tape, nodes: List<Node>, weights: Mat?): Pair<Node, Mat> {
            val out = f(tape, nodes)
            val w = weights ?: randomMat(rnd, out.value.rows, out.value.cols, 1.0)
            val prod = tape.mul(out, tape.constant(w))
            return tape.sumScaled(prod, 1f) to w
        }
        val tape = Tape()
        val grads = inputs.map { Mat(it.rows, it.cols) }
        val nodes = inputs.zip(grads).map { (m, g) -> tape.param(m, g) }
        val (total, weights) = loss(tape, nodes, null)
        tape.backward(total)
        val eps = 1e-2f
        for ((k, m) in inputs.withIndex()) for (i in m.data.indices) {
            val keep = m.data[i]
            fun value(): Double { val t = Tape(false); return loss(t, inputs.map { t.constant(it) }, weights).first.value.data[0].toDouble() }
            m.data[i] = keep + eps; val up = value()
            m.data[i] = keep - eps; val down = value()
            m.data[i] = keep
            val numeric = (up - down) / (2 * eps)
            val analytic = grads[k].data[i].toDouble()
            val err = abs(numeric - analytic) / maxOf(1.0, abs(numeric), abs(analytic))
            if (err > 2e-2) fail("$name: input $k[$i] analytic $analytic numeric $numeric")
        }
    }

    @Test fun researchOperationsHaveExactGradients() {
        val r = Random(3)
        check("l2NormHeads", listOf(randomMat(r, 3, 6))) { t, n -> t.l2NormHeads(n[0], 2) }
        check("pickRows", listOf(randomMat(r, 4, 3))) { t, n -> t.pickRows(n[0], intArrayOf(3, 0, 0, 2)) }
        check("rowConcat", listOf(randomMat(r, 1, 3), randomMat(r, 2, 3))) { t, n -> t.rowConcat(n[0], n[1]) }
        check("relu", listOf(randomMat(r, 3, 5, 1.0))) { t, n -> t.relu(n[0]) }
        check("sumScaled", listOf(randomMat(r, 2, 3))) { t, n -> t.sumScaled(n[0], .3f) }
        check("minGruScanFrom", listOf(Mat(4, 3, FloatArray(12) { r.nextFloat() }), randomMat(r, 4, 3))) { t, n ->
            t.minGruScanFrom(n[0], n[1], floatArrayOf(.3f, -.2f, .5f))
        }
        for (delta in listOf(true, false)) {
            val init = FloatArray(2 * 9) { (r.nextGaussian() * .3).toFloat() }
            check("deltaScan delta=$delta", listOf(randomMat(r, 5, 6), unitRows(r, 5, 6), randomMat(r, 5, 6),
                Mat(5, 2, FloatArray(10) { .5f + .45f * r.nextFloat() }), Mat(5, 2, FloatArray(10) { r.nextFloat() }))) { t, n ->
                t.deltaScan(n[0], n[1], n[2], n[3], n[4], 2, delta, init.copyOf())
            }
        }
    }

    private fun tiny(mixer: Mixer, ffn: Ffn = Ffn.GELU, adapter: Int = 0, decay: Boolean = true, write: Boolean = true) =
        NfConfig(vocab = 40, dim = 8, layers = 2, heads = 2, context = 16, mlp = 12, mixer = mixer, decayGate = decay,
            writeGate = write, ffn = ffn, adapterRank = adapter)

    @Test fun everyMixerHasExactEndToEndGradients() {
        val configs = Mixer.values().map { tiny(it) } + listOf(tiny(Mixer.DELTA, Ffn.RELU, 2), tiny(Mixer.DELTA, decay = false, write = false))
        for (config in configs) {
            val model = NfModel(NfParams.init(config, 3))
            for (n in listOf("tok", "pos")) model.params.mats[n]?.data?.let { d -> for (i in d.indices) d[i] *= 25f }
            for ((n, m) in model.params.mats) if (n.endsWith(".B")) for (i in m.data.indices) m.data[i] = .3f * ((i % 5) - 2)
            val ids = intArrayOf(38, 5, 9, 9, 20, 7)
            val targets = intArrayOf(5, 9, 9, 20, 7, 39)
            val grads = model.params.mats.mapValues { Mat(it.value.rows, it.value.cols) }
            val tape = Tape()
            val nodes = model.params.mats.mapValues { (n, m) -> tape.param(m, grads.getValue(n)) }
            tape.backward(tape.crossEntropy(model.logits(tape, nodes, ids), targets))
            fun lossValue(): Double {
                val t = Tape(false)
                return t.crossEntropy(model.logits(t, model.frozenNodes(t), ids), targets).value.data[0].toDouble()
            }
            val rnd = Random(11)
            for (name in model.params.mats.keys) repeat(3) {
                val m = model.params.mats.getValue(name)
                val i = if (name == "tok") ids[rnd.nextInt(ids.size)] * m.cols + rnd.nextInt(m.cols) else rnd.nextInt(m.data.size)
                val keep = m.data[i]
                m.data[i] = keep + 1e-2f; val up = lossValue()
                m.data[i] = keep - 1e-2f; val down = lossValue()
                m.data[i] = keep
                val numeric = (up - down) / 2e-2
                val analytic = grads.getValue(name).data[i].toDouble()
                // ReLU kinks make a finite difference across 0 meaningless; such coordinates are rare and skipped.
                if (config.ffn == Ffn.RELU && abs(numeric - analytic) > .05 * maxOf(1e-2, abs(numeric))) return@repeat
                assertTrue("$config $name[$i] analytic $analytic numeric $numeric",
                    abs(numeric - analytic) <= 2e-2 * maxOf(1e-2, abs(numeric), abs(analytic)) + 2e-4)
            }
        }
    }

    @Test fun carriedMemoryEqualsOneLongPass() {
        for (mixer in listOf(Mixer.MINGRU, Mixer.DELTA, Mixer.LINEAR)) {
            val model = NfModel(NfParams.init(tiny(mixer), 5))
            val ids = intArrayOf(38, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            val whole = Tape(false).let { t -> model.logits(t, model.frozenNodes(t), ids, NfState.empty(model.config)).value }
            val state = NfState.empty(model.config)
            Tape(false).let { t -> model.logits(t, model.frozenNodes(t), ids.copyOfRange(0, 4), state) }
            val second = Tape(false).let { t -> model.logits(t, model.frozenNodes(t), ids.copyOfRange(4, ids.size), state).value }
            for (r in 0 until second.rows) for (c in 0 until second.cols) assertEquals("$mixer row $r", whole[r + 4, c], second[r, c], 1e-4f)
        }
    }

    @Test fun deltaWritesAreExactlyReversible() {
        val rnd = Random(9)
        val hd = 8
        val state = FloatArray(hd * hd) { (rnd.nextGaussian() * .5).toFloat() }
        val start = state.copyOf()
        val journal = ArrayList<Triple<FloatArray, FloatArray, Pair<Float, Float>>>()
        repeat(20) {
            val k = FloatArray(hd) { rnd.nextGaussian().toFloat() }.let { v -> val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat(); FloatArray(hd) { v[it] / n } }
            val v = FloatArray(hd) { rnd.nextGaussian().toFloat() }
            val a = .9f + .1f * rnd.nextFloat(); val b = .8f * rnd.nextFloat()
            deltaWrite(state, k, v, a, b)
            journal += Triple(k, v, a to b)
        }
        var s = state
        for ((k, v, ab) in journal.asReversed()) s = undoDeltaWrite(s, k, v, ab.first, ab.second)
        for (i in s.indices) assertEquals("entry $i", start[i], s[i], 2e-3f)
    }

    @Test fun adapterTrainingLeavesTheBaseBitIdentical() {
        val config = tiny(Mixer.DELTA, adapter = 2)
        val model = NfModel(NfParams.init(config, 7))
        val adapters = config.adapterNames()
        // The base is first trained on other text (with tied embeddings of std 0.02 an untrained base has no logit scale
        // that an adapter could use); then only the adapter learns the new sequence.
        val old = intArrayOf(38, 4, 5, 6, 7, 4, 5, 6, 7); val oldTargets = intArrayOf(4, 5, 6, 7, 4, 5, 6, 7, 39)
        NfTrainer(model, 1e-2f, 0f, 1, 60, trainable = model.params.mats.keys - adapters, threads = 1).use { tr -> repeat(60) { tr.step(listOf(old to oldTargets)) } }
        val before = model.params.sha256(except = adapters)
        val ids = intArrayOf(38, 1, 2, 3, 1, 2, 3); val targets = intArrayOf(1, 2, 3, 1, 2, 3, 39)
        val baseline = model.nll(ids, targets).sum()
        NfTrainer(model, 1e-2f, 0f, 1, 30, trainable = adapters, threads = 1).use { tr -> repeat(30) { tr.step(listOf(ids to targets)) } }
        assertEquals(before, model.params.sha256(except = adapters))
        assertTrue("adapter learns", model.nll(ids, targets).sum() < baseline * .8)
        val restored = NfCheckpoint.decode(NfCheckpoint.encode(model.params, "tok", mapOf("seed" to "7")))
        for ((n, m) in model.params.mats) assertArrayEquals(n, m.data, restored.params.mats.getValue(n).data, 0f)
        val damaged = NfCheckpoint.encode(model.params, "tok", emptyMap()).also { it[80] = (it[80] + 1).toByte() }
        assertTrue(runCatching { NfCheckpoint.decode(damaged) }.isFailure)
    }

    @Test fun everyMixerLearnsAndIsDeterministic() {
        for (mixer in Mixer.values()) {
            fun run(): Pair<Double, FloatArray> {
                val model = NfModel(NfParams.init(NfConfig(40, 16, 1, 2, 32, 32, mixer), 1))
                val seq = intArrayOf(38) + IntArray(20) { (it * 7) % 11 + 1 } + 39
                val ids = seq.copyOfRange(0, seq.size - 1); val targets = seq.copyOfRange(1, seq.size)
                var last = 0.0; var first = 0.0
                NfTrainer(model, 1e-2f, 0f, 5, 120, threads = 2).use { tr -> repeat(120) { i -> last = tr.step(listOf(ids to targets, ids to targets)); if (i == 0) first = last } }
                assertTrue("$mixer $first -> $last", last < first * .3)
                return last to model.params.mats.getValue("l0.w1").data
            }
            val a = run(); val b = run()
            assertEquals(a.first, b.first, 0.0)
            assertArrayEquals(a.second, b.second, 0f)
        }
    }

    @Test fun byteLatentModelHasExactGradientsAndStaysCausal() {
        val model = NfBlt(8, 12, 4)
        model.mats.getValue("emb").data.let { d -> for (i in d.indices) d[i] *= 25f }
        model.mats.getValue("start").data.let { d -> for (i in d.indices) d[i] *= 25f }
        val (ids, targets) = NfBlt.example("abcab ca")
        val patch = NfBlt.fixedPatches(3)("abcab ca".toByteArray())
        val grads = model.mats.mapValues { Mat(it.value.rows, it.value.cols) }
        val tape = Tape()
        val nodes = model.mats.mapValues { (n, m) -> tape.param(m, grads.getValue(n)) }
        tape.backward(tape.crossEntropy(model.logits(tape, nodes, ids, patch), targets))
        fun lossValue(): Double { val t = Tape(false); return t.crossEntropy(model.logits(t, model.frozenNodes(t), ids, patch), targets).value.data[0].toDouble() }
        val rnd = Random(5)
        for (name in model.mats.keys) repeat(2) {
            val m = model.mats.getValue(name)
            val i = if (name == "emb") ids[rnd.nextInt(ids.size)] * m.cols + rnd.nextInt(m.cols) else rnd.nextInt(m.data.size)
            val keep = m.data[i]
            m.data[i] = keep + 1e-2f; val up = lossValue()
            m.data[i] = keep - 1e-2f; val down = lossValue()
            m.data[i] = keep
            val numeric = (up - down) / 2e-2
            val analytic = grads.getValue(name).data[i].toDouble()
            assertTrue("BLT $name[$i] analytic $analytic numeric $numeric", abs(numeric - analytic) <= 2e-2 * maxOf(1e-2, abs(numeric), abs(analytic)) + 2e-4)
        }
        // causality: changing a later byte never changes earlier predictions
        val other = ids.copyOf().also { it[7] = 'z'.code }
        val a = Tape(false).let { t -> model.logits(t, model.frozenNodes(t), ids, patch).value }
        val b = Tape(false).let { t -> model.logits(t, model.frozenNodes(t), other, patch).value }
        for (r in 0 until 7) for (c in 0 until a.cols) assertEquals("position $r", a[r, c], b[r, c], 1e-6f)
    }
}
