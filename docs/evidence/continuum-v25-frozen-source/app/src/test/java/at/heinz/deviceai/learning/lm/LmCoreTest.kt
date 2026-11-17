package at.heinz.deviceai.learning.lm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Random
import kotlin.math.abs

class LmCoreTest {
    private fun randomMat(rnd: Random, r: Int, c: Int, scale: Double = .5) = Mat(r, c, FloatArray(r * c) { (rnd.nextGaussian() * scale).toFloat() })

    /**
     * Compares the tape gradient of L = Σ w ⊙ f(inputs) with central finite differences for every input coordinate.
     */
    private fun check(name: String, inputs: List<Mat>, f: (Tape, List<Node>) -> Node) {
        val rnd = Random(name.hashCode().toLong())
        fun loss(tape: Tape, nodes: List<Node>, weights: Mat?): Pair<Node, Mat> {
            val out = f(tape, nodes)
            val w = weights ?: randomMat(rnd, out.value.rows, out.value.cols, 1.0)
            val prod = tape.mul(out, tape.constant(w))
            val ones = tape.constant(Mat(1, prod.value.rows, FloatArray(prod.value.rows) { 1f }))
            val rowSums = tape.matmul(ones, prod)
            val total = tape.matmul(rowSums, tape.constant(Mat(prod.value.cols, 1, FloatArray(prod.value.cols) { 1f })))
            return total to w
        }
        val tape = Tape()
        val grads = inputs.map { Mat(it.rows, it.cols) }
        val nodes = inputs.zip(grads).map { (m, g) -> tape.param(m, g) }
        val (total, weights) = loss(tape, nodes, null)
        tape.backward(total)
        val eps = 1e-2f
        var worst = 0.0
        for ((k, m) in inputs.withIndex()) for (i in m.data.indices) {
            val keep = m.data[i]
            fun value(): Double { val t = Tape(false); return loss(t, inputs.map { t.constant(it) }, weights).first.value.data[0].toDouble() }
            m.data[i] = keep + eps; val up = value()
            m.data[i] = keep - eps; val down = value()
            m.data[i] = keep
            val numeric = (up - down) / (2 * eps)
            val analytic = grads[k].data[i].toDouble()
            val err = abs(numeric - analytic) / maxOf(1.0, abs(numeric), abs(analytic))
            worst = maxOf(worst, err)
            if (err > 2e-2) fail("$name: input $k[$i] analytic $analytic numeric $numeric")
        }
        assertTrue("$name worst relative error $worst", worst < 2e-2)
    }

    @Test fun everyOperationHasAnExactGradient() {
        val r = Random(7)
        check("gather", listOf(randomMat(r, 5, 3))) { t, n -> t.gather(n[0], intArrayOf(4, 1, 1, 0)) }
        check("rows", listOf(randomMat(r, 6, 3))) { t, n -> t.rows(n[0], 2, 3) }
        check("add", listOf(randomMat(r, 3, 4), randomMat(r, 3, 4))) { t, n -> t.add(n[0], n[1]) }
        check("addRow", listOf(randomMat(r, 3, 4), randomMat(r, 1, 4))) { t, n -> t.addRow(n[0], n[1]) }
        check("mul", listOf(randomMat(r, 3, 4), randomMat(r, 3, 4))) { t, n -> t.mul(n[0], n[1]) }
        check("matmul", listOf(randomMat(r, 3, 4), randomMat(r, 4, 2))) { t, n -> t.matmul(n[0], n[1]) }
        check("matmulT", listOf(randomMat(r, 3, 4), randomMat(r, 5, 4))) { t, n -> t.matmulT(n[0], n[1]) }
        check("rmsnorm", listOf(randomMat(r, 3, 5), randomMat(r, 1, 5))) { t, n -> t.rmsnorm(n[0], n[1]) }
        check("gelu", listOf(randomMat(r, 3, 4, 1.5))) { t, n -> t.gelu(n[0]) }
        check("silu", listOf(randomMat(r, 3, 4, 1.5))) { t, n -> t.silu(n[0]) }
        check("sigmoid", listOf(randomMat(r, 3, 4, 1.5))) { t, n -> t.sigmoid(n[0]) }
        check("causalSoftmax", listOf(randomMat(r, 4, 4, 1.0))) { t, n -> t.causalSoftmax(n[0], .7f) }
        check("colSlice+concat", listOf(randomMat(r, 3, 6))) { t, n -> t.colConcat(listOf(t.colSlice(n[0], 4, 2), t.colSlice(n[0], 0, 3))) }
        check("minGruScan", listOf(Mat(4, 3, FloatArray(12) { r.nextFloat() }), randomMat(r, 4, 3))) { t, n -> t.minGruScan(n[0], n[1]) }
        check("crossEntropy", listOf(randomMat(r, 4, 6, 1.0))) { t, n -> t.crossEntropy(n[0], intArrayOf(2, -1, 5, 0)) }
    }

    private fun tinyConfig(arch: LmArch) = LmConfig(arch, vocab = 262, dim = 8, layers = 2, heads = 2, context = 16, mlp = 12)

    @Test fun bothArchitecturesHaveExactEndToEndGradients() {
        for (arch in LmArch.values()) {
            val model = LmModel(LmParams.init(tinyConfig(arch), 3))
            // Embeddings start at std 0.02; scaled up here so that a finite-difference step of 0.01 is small
            // relative to the vectors that RMSNorm normalizes (the analytic gradient does not depend on the scale).
            for (n in listOf("tok", "pos")) model.params.mats[n]?.data?.let { d -> for (i in d.indices) d[i] *= 25f }
            val ids = intArrayOf(260, 5, 9, 9, 200, 7)
            val targets = intArrayOf(5, 9, 9, 200, 7, 261)
            val grads = model.params.zeros()
            val tape = Tape()
            val nodes = model.params.mats.mapValues { (n, m) -> tape.param(m, grads.getValue(n)) }
            val loss = tape.crossEntropy(model.logits(tape, nodes, ids), targets)
            tape.backward(loss)
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
                assertTrue("$arch $name[$i] analytic $analytic numeric $numeric",
                    abs(numeric - analytic) <= 2e-2 * maxOf(1e-2, abs(numeric), abs(analytic)) + 2e-4)
            }
        }
    }

    @Test fun predictionsNeverDependOnFutureTokens() {
        for (arch in LmArch.values()) {
            val model = LmModel(LmParams.init(tinyConfig(arch), 5))
            val a = intArrayOf(260, 1, 2, 3, 4, 5); val b = intArrayOf(260, 1, 2, 3, 99, 100)
            val t1 = Tape(false); val t2 = Tape(false)
            val la = model.logits(t1, model.frozenNodes(t1), a).value; val lb = model.logits(t2, model.frozenNodes(t2), b).value
            for (pos in 0..3) for (v in 0 until la.cols) assertEquals("$arch position $pos", la[pos, v], lb[pos, v], 1e-6f)
            assertNotEquals(la[4, 0], lb[4, 0])
        }
    }

    @Test fun tokenizerRoundTripsAnyTextAndIsBoundToItsMerges() {
        val train = listOf("Der Kern lernt lokal. Der Kern lernt sicher.", "Lernen braucht Daten; Daten brauchen Schutz.")
        val tok = LmBpeTokenizer.train(train, 300)
        assertTrue(tok.vocabSize in 259..300)
        for (text in listOf("Der Kern lernt", "Größe: 12,5 % – ÄÖÜß 🚀\nneue Zeile", "", "x")) {
            assertEquals(text, tok.decode(tok.encode(text)))
        }
        assertTrue("merges compress seen words", tok.encode("Der Kern lernt").size < "Der Kern lernt".toByteArray().size)
        val copy = LmBpeTokenizer.deserialize(tok.serialize())
        assertEquals(tok.sha256, copy.sha256)
        assertArrayEquals(tok.encode("Kern lernt Daten"), copy.encode("Kern lernt Daten"))
        assertNotEquals(tok.sha256, LmBpeTokenizer.train(listOf("xyz xyz xyz qqq qqq qqq"), 300).sha256)
        assertEquals(258, LmBpeTokenizer.bytes().vocabSize)
    }

    @Test fun checkpointsRoundTripAndRejectDamageOrForeignTokenizers() {
        val tok = LmBpeTokenizer.train(listOf("abc abc abd abd"), 262)
        val config = LmConfig(LmArch.MINGRU, tok.vocabSize, 8, 1, 1, 16, 12)
        val params = LmParams.init(config, 9)
        val bytes = LmCheckpoint.encode(params, tok, mapOf("seed" to "9", "corpus" to "test"))
        val back = LmCheckpoint.decode(bytes, tok)
        assertEquals(mapOf("corpus" to "test", "seed" to "9"), back.meta)
        for ((n, m) in params.mats) assertArrayEquals(n, m.data, back.params.mats.getValue(n).data, 0f)
        val damaged = bytes.copyOf(); damaged[100] = (damaged[100] + 1).toByte()
        assertTrue(runCatching { LmCheckpoint.decode(damaged, tok) }.isFailure)
        val other = LmBpeTokenizer.train(listOf("xyz xyz xyw xyw"), 262)
        assertTrue(runCatching { LmCheckpoint.decode(bytes, other) }.isFailure)

        val dir = Files.createTempDirectory("lm-store").toFile()
        val store = LmCheckpointStore(dir)
        val sha = store.put(bytes)
        assertEquals(sha, store.put(bytes))
        assertArrayEquals(bytes, store.get(sha))
        assertEquals(listOf(sha), store.list())
        File(dir, "$sha.lm1").writeBytes(damaged)
        assertTrue("damaged file is reported", runCatching { store.get(sha) }.isFailure)
        assertTrue("damaged file is not overwritten", runCatching { store.put(bytes) }.isFailure)
    }

    @Test fun bothArchitecturesLearnARepeatedPhrase() {
        val text = "der kern lernt. der kern lernt. der kern lernt."
        val tok = LmBpeTokenizer.bytes()
        val seq = intArrayOf(tok.bos) + tok.encode(text) + tok.eos
        for (arch in LmArch.values()) {
            val model = LmModel(LmParams.init(LmConfig(arch, tok.vocabSize, 16, 1, 2, 64, 32), 1))
            LmTrainer(model, 1e-2f, 0f, 5, 150, threads = 1).use { trainer ->
                val first = trainer.step(listOf(seq))
                var last = first
                repeat(149) { last = trainer.step(listOf(seq)) }
                assertTrue("$arch loss $first -> $last", last < first * .25)
            }
            val (bits, bytes) = LmEval.segmentBits(model, tok, text)
            assertTrue("$arch ${bits / bytes} bits/byte", bits / bytes < 1.5)
        }
    }

    @Test fun trainingIsDeterministicForAFixedSeedAndThreadCount() {
        val tok = LmBpeTokenizer.bytes()
        val seqs = listOf("abc abc", "abd abd", "xyz").map { intArrayOf(tok.bos) + tok.encode(it) + tok.eos }
        fun run(): FloatArray {
            val model = LmModel(LmParams.init(LmConfig(LmArch.TRANSFORMER, tok.vocabSize, 8, 1, 2, 16, 16), 4))
            LmTrainer(model, 5e-3f, .01f, 2, 10, threads = 2).use { t -> repeat(10) { t.step(seqs) } }
            return model.params.mats.getValue("l0.wq").data
        }
        assertArrayEquals(run(), run(), 0f)
    }
}
