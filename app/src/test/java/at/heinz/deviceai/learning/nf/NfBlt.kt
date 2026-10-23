package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.LmCorpus.Split
import at.heinz.deviceai.learning.lm.Mat
import at.heinz.deviceai.learning.lm.Node
import at.heinz.deviceai.learning.lm.Tape
import java.util.IdentityHashMap
import java.util.Random
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Entropy of the next byte under an interpolated absolute-discount byte n-gram learned from TRAIN only:
 * p_k(b | c) = max(n(c, b) − D, 0) / n(c) + D · types(c) / n(c) · p_{k−1}(b | c'), p_0 = 1/256, D = 0.75.
 * Used only to place patch boundaries (NF-3); it never scores text.
 */
class ByteNgramEntropy(private val order: Int, train: List<String>) {
    private val counts = Array(order + 1) { HashMap<Long, HashMap<Int, Int>>() }
    private val totals = Array(order + 1) { HashMap<Long, Int>() }

    init {
        for (s in train) {
            val b = s.toByteArray(Charsets.UTF_8)
            for (i in b.indices) for (k in 1..order) {
                val ctx = context(b, i, k - 1)
                counts[k].getOrPut(ctx) { HashMap() }.merge(b[i].toInt() and 255, 1, Int::plus)
                totals[k].merge(ctx, 1, Int::plus)
            }
        }
    }

    /** Context of the k−1 bytes before position i, tagged with its length. */
    private fun context(b: ByteArray, i: Int, len: Int): Long {
        var key = len.toLong()
        for (j in i - len until i) key = (key shl 8) or (if (j < 0) 0L else (b[j].toLong() and 255))
        return key
    }

    /** Entropy in bits of the byte at position [i] given the bytes before it. */
    fun entropy(b: ByteArray, i: Int): Double {
        val p = DoubleArray(256) { 1.0 / 256 }
        for (k in 1..order) {
            val ctx = context(b, i, k - 1)
            val n = totals[k][ctx] ?: continue
            val seen = counts[k].getValue(ctx)
            val back = .75 * seen.size / n
            for (j in 0 until 256) p[j] *= back
            for ((sym, c) in seen) p[sym] += (c - .75) / n
        }
        var h = 0.0
        for (x in p) if (x > 0) h -= x * ln(x)
        return h / ln(2.0)
    }
}

/**
 * NF-3 byte-latent core: a cheap byte model (local encoder and decoder, width [local]) runs for every byte; a wider
 * recurrent global model (width [global], 2 layers) runs once per patch. A byte in patch j is decoded with the global
 * state after patch j−1 (a learned start vector for the first patch), so everything stays causal.
 * Patches come from [ByteNgramEntropy] (new patch where the next byte is hard to predict) or have a fixed length.
 */
class NfBlt(val local: Int, val global: Int, seed: Long) {
    val mats = LinkedHashMap<String, Mat>()

    init {
        val rnd = Random(seed)
        fun add(name: String, r: Int, c: Int, kind: Char) {
            val m = Mat(r, c)
            when (kind) {
                'e' -> for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() * .02).toFloat()
                'g' -> m.data.fill(1f)
                'b' -> Unit
                'o' -> for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() / sqrt(r.toFloat()) / 2).toFloat()
                else -> for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() / sqrt(r.toFloat())).toFloat()
            }
            mats[name] = m
        }
        fun block(prefix: String, d: Int) {
            add("$prefix.n1", 1, d, 'g')
            add("$prefix.wz", d, d, 'w'); add("$prefix.bz", 1, d, 'b'); add("$prefix.wc", d, d, 'w'); add("$prefix.wg", d, d, 'w')
            add("$prefix.wo", d, d, 'o')
            add("$prefix.n2", 1, d, 'g'); add("$prefix.w1", d, 4 * d, 'w'); add("$prefix.b1", 1, 4 * d, 'b')
            add("$prefix.w2", 4 * d, d, 'o'); add("$prefix.b2", 1, d, 'b')
        }
        add("emb", VOCAB, local, 'e')
        block("enc", local)
        add("up", local, global, 'w')
        block("g0", global); block("g1", global)
        add("start", 1, global, 'e')
        add("down", global, local, 'o')
        block("dec", local)
        add("nf", 1, local, 'g')
    }

    val count: Int get() = mats.values.sumOf { it.data.size }

    private fun block(tape: Tape, p: (String) -> Node, prefix: String, x: Node): Node {
        val h = tape.rmsnorm(x, p("$prefix.n1"))
        val z = tape.sigmoid(tape.addRow(tape.matmul(h, p("$prefix.wz")), p("$prefix.bz")))
        val s = tape.minGruScan(z, tape.matmul(h, p("$prefix.wc")))
        val y = tape.add(x, tape.matmul(tape.mul(s, tape.silu(tape.matmul(h, p("$prefix.wg")))), p("$prefix.wo")))
        val h2 = tape.rmsnorm(y, p("$prefix.n2"))
        return tape.add(y, tape.addRow(tape.matmul(tape.gelu(tape.addRow(tape.matmul(h2, p("$prefix.w1")), p("$prefix.b1"))), p("$prefix.w2")), p("$prefix.b2")))
    }

    /** Logits for byte ids (BOS first); [patch][t] is the patch index of position t, non-decreasing from 0. */
    fun logits(tape: Tape, nodes: Map<String, Node>, ids: IntArray, patch: IntArray): Node {
        require(patch.size == ids.size && patch[0] == 0)
        fun p(name: String) = nodes[name] ?: error("missing parameter $name")
        val x = tape.gather(p("emb"), ids)
        val h = block(tape, ::p, "enc", x)
        val last = (ids.indices).filter { t -> t == ids.lastIndex || patch[t + 1] != patch[t] }.toIntArray()
        val g = block(tape, ::p, "g1", block(tape, ::p, "g0", tape.matmul(tape.pickRows(h, last), p("up"))))
        val context = tape.pickRows(tape.rowConcat(p("start"), g), patch)       // row j = state after patch j−1
        val z = block(tape, ::p, "dec", tape.add(h, tape.matmul(context, p("down"))))
        return tape.matmulT(tape.rmsnorm(z, p("nf")), p("emb"))
    }

    fun frozenNodes(tape: Tape): Map<String, Node> = mats.mapValues { tape.constant(it.value) }

    /** Content bits of one segment (EOS excluded) and its bytes. */
    fun segmentBits(text: String, patcher: (ByteArray) -> IntArray): Pair<Double, Int> {
        val (ids, targets) = example(text)
        targets[targets.size - 1] = -1
        val tape = Tape(false)
        val l = logits(tape, frozenNodes(tape), ids, patcher(text.toByteArray(Charsets.UTF_8))).value
        var nats = 0.0
        for (t in 0 until ids.size - 1) nats += NfModel.logSumExp(l.data, t * l.cols, l.cols) - l.data[t * l.cols + targets[t]]
        return nats / ln(2.0) to ids.size - 1
    }

    companion object {
        const val VOCAB = 258
        const val BOS = 256
        const val EOS = 257

        /** (BOS + bytes, bytes + EOS) of one segment. */
        fun example(text: String): Pair<IntArray, IntArray> {
            val b = text.toByteArray(Charsets.UTF_8).map { it.toInt() and 255 }
            return (intArrayOf(BOS) + b) to (b + EOS).toIntArray()
        }

        /** Patch index per input position (BOS + bytes): a new patch starts where the byte at the position is hard. */
        fun entropyPatches(model: ByteNgramEntropy, threshold: Double, maxLength: Int): (ByteArray) -> IntArray = { b ->
            val out = IntArray(b.size + 1)
            var patch = 0; var length = 1
            for (t in 1..b.size) {
                if (model.entropy(b, t - 1) > threshold || length >= maxLength) { patch++; length = 0 }
                out[t] = patch; length++
            }
            out
        }

        fun fixedPatches(length: Int): (ByteArray) -> IntArray = { b -> IntArray(b.size + 1) { t -> if (t == 0) 0 else (t - 1) / length + 1 } }
    }
}

/**
 * NF-3 run: entropy patches against fixed patches of the same mean length, both with identical models, data order,
 * schedule and budget; BPE references come from the `lm` mode (G-S / G-M). Threshold chosen on TRAIN only.
 */
object NfBltRun {
    fun run(docs: List<NfCorpus.Doc>, args: Array<String>) {
        val train = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TRAIN)
        val validation = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.VALIDATION)
        val epochs = NfExperiment.intArg(args, "epochs", 6)
        val meanPatch = NfExperiment.floatArg(args, "meanPatch", 3f)
        val local = NfExperiment.intArg(args, "local", 32)
        val global = NfExperiment.intArg(args, "global", 64)
        val entropyModel = ByteNgramEntropy(4, train)
        val hs = ArrayList<Double>()
        for (s in train) { val b = s.toByteArray(Charsets.UTF_8); for (i in b.indices) hs += entropyModel.entropy(b, i) }
        hs.sort()
        val threshold = hs[((1.0 - 1.0 / meanPatch) * (hs.size - 1)).toInt()]
        println("BLT threshold=${"%.4f".format(threshold)} bits meanPatchTarget=$meanPatch trainBytes=${hs.size}")
        val variants = listOf("entropy" to NfBlt.entropyPatches(entropyModel, threshold, 16), "fixed" to NfBlt.fixedPatches(meanPatch.toInt()))
        for ((name, patcher) in variants) {
            val trainPatches = IdentityHashMap<IntArray, IntArray>()
            val examples = train.map { s -> val e = NfBlt.example(s); trainPatches[e.first] = patcher(s.toByteArray(Charsets.UTF_8)); e }
            val patches = trainPatches.values.sumOf { it.last() + 1 }; val positions = trainPatches.values.sumOf { it.size }
            println("BLT $name meanPatchLength=${"%.3f".format(positions.toDouble() / patches)}")
            for (seed in NfExperiment.seeds(args)) {
                val model = NfBlt(local, global, seed)
                val batch = 16
                val stepsPerEpoch = (examples.size + batch - 1) / batch
                val rnd = Random(seed * 31 + 7)
                val start = System.nanoTime()
                NfTrainer(model.mats, 2e-3f, .1f, 100, stepsPerEpoch * epochs, threads = NfExperiment.threads(args)) { tape, nodes, ids, _, _, _ ->
                    model.logits(tape, nodes, ids, trainPatches.getValue(ids))
                }.use { trainer ->
                    for (epoch in 1..epochs) {
                        val order = examples.indices.shuffled(rnd)
                        var loss = 0.0
                        for (b in 0 until stepsPerEpoch) loss += trainer.step(order.subList(b * batch, minOf(order.size, (b + 1) * batch)).map { examples[it] })
                        var bits = 0.0; var bytes = 0L
                        for (s in validation) { val (bt, n) = model.segmentBits(s, patcher); bits += bt; bytes += n }
                        println("RUN BLT-$name seed=$seed epoch=$epoch trainNll=${"%.4f".format(loss / stepsPerEpoch)} valBPB=${"%.4f".format(bits / bytes)} " +
                            "params=${model.count} trainSeconds=${"%.0f".format((System.nanoTime() - start) / 1e9)}")
                        System.out.flush()
                    }
                }
                val sample = validation.take(80)
                repeat(2) { for (s in sample.take(10)) model.segmentBits(s, patcher) }
                val t0 = System.nanoTime(); var n = 0
                for (s in sample) n += model.segmentBits(s, patcher).second
                println("SPEED BLT-$name seed=$seed msPerByte=${"%.4f".format((System.nanoTime() - t0) / 1e6 / n)} (patching included)")
                System.out.flush()
            }
        }
    }
}
