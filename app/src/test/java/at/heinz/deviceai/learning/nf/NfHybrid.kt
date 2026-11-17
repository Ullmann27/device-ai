package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.KneserNeyBytes
import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.LmCorpus.Split
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln

/**
 * NF-6b-lite (pre-registered in docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md, section 8c): a byte-level neural model
 * mixed per byte with Kneser-Ney 7, p = λ p_neural + (1 − λ) p_KN. Fixed λ against a count-anchored gate
 * λ = σ(a + b ln(1 + n) + c o / 7), n = count of the longest context seen in TRAIN, o its order. Both are fitted on one
 * half of VALIDATION (by document hash) and scored on the other, then the halves swap (cross-fitting).
 */
object NfHybrid {
    /** Per byte of the scored text: neural probability, n-gram probability and the n-gram's context evidence. */
    class Bytes(val neural: DoubleArray, val kn: DoubleArray, val order: IntArray, val count: LongArray) {
        val size get() = neural.size
    }

    fun features(n: Long, o: Int) = doubleArrayOf(1.0, ln(1.0 + n), o / 7.0)

    fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))

    /** Bits of the mixture with weight [lambda] on the neural model. */
    fun fixedBits(d: Bytes, lambda: Double): Double {
        var nats = 0.0
        for (i in 0 until d.size) nats -= ln(lambda * d.neural[i] + (1 - lambda) * d.kn[i])
        return nats / ln(2.0)
    }

    /** Bits of the count-anchored mixture with gate parameters [w] (a, b, c). */
    fun gateBits(d: Bytes, w: DoubleArray): Double {
        var nats = 0.0
        for (i in 0 until d.size) {
            val f = features(d.count[i], d.order[i])
            val l = sigmoid(w[0] * f[0] + w[1] * f[1] + w[2] * f[2])
            nats -= ln(l * d.neural[i] + (1 - l) * d.kn[i])
        }
        return nats / ln(2.0)
    }

    fun bestFixed(d: Bytes): Double = (0..40).map { it / 40.0 }.minBy { fixedBits(d, it.coerceIn(1e-6, 1 - 1e-6)) }

    /**
     * Maximum-likelihood gate by gradient ascent from the fixed optimum (b = c = 0): the log-likelihood of a two-component
     * mixture in the gate logit has the gradient (p_n − p_k) λ (1 − λ) / p_mix per byte.
     */
    fun fitGate(d: Bytes, start: Double, steps: Int = 400, rate: Double = .5): DoubleArray {
        val w = doubleArrayOf(ln(start.coerceIn(1e-3, 1 - 1e-3) / (1 - start.coerceIn(1e-3, 1 - 1e-3))), 0.0, 0.0)
        repeat(steps) {
            val g = DoubleArray(3)
            for (i in 0 until d.size) {
                val f = features(d.count[i], d.order[i])
                val l = sigmoid(w[0] * f[0] + w[1] * f[1] + w[2] * f[2])
                val mix = l * d.neural[i] + (1 - l) * d.kn[i]
                val s = (d.neural[i] - d.kn[i]) * l * (1 - l) / mix
                for (j in 0..2) g[j] += s * f[j]
            }
            for (j in 0..2) w[j] += rate * g[j] / d.size
        }
        return w
    }

    fun concat(parts: List<Bytes>) = Bytes(parts.flatMap { it.neural.asList() }.toDoubleArray(),
        parts.flatMap { it.kn.asList() }.toDoubleArray(), parts.flatMap { it.order.asList() }.toIntArray(),
        parts.flatMap { it.count.asList() }.toLongArray())

    /** Fold of a VALIDATION document: parity of the first byte of SHA-256("nf6b\0" + path). */
    fun fold(path: String) = (MessageDigest.getInstance("SHA-256").digest(("nf6b\u0000" + path).toByteArray())[0].toInt() and 1)

    fun bytesOf(model: NfModel, tok: LmBpeTokenizer, kn: KneserNeyBytes, text: String): Bytes {
        val tokens = tok.encode(text)
        val raw = text.toByteArray(Charsets.UTF_8)
        require(tokens.size == raw.size) { "the neural model must use the byte tokenizer (vocab=258)" }
        val ids = intArrayOf(tok.bos) + tokens
        val targets = tokens + tok.eos
        targets[targets.size - 1] = -1
        val nll = model.nll(ids, targets)
        val k = kn.detail(text)
        return Bytes(DoubleArray(raw.size) { exp(-nll[it]) }, k.prob, k.order, k.count)
    }

    /**
     * NF-7 TEST (section 8f, item 6), at most once: KN-7 from TRAIN; λ of each model fitted on all of VALIDATION; neural,
     * KN-7 and the fixed mixture scored on TEST. Refuses to run twice for the same checkpoint store.
     */
    fun runTest(docs: List<NfCorpus.Doc>, tok: LmBpeTokenizer, args: Array<String>) {
        require(NfExperiment.arg(args, "corpus") == "nfc2" && NfExperiment.arg(args, "once") == "NF7") { "NF-7 TEST needs corpus=nfc2 once=NF7" }
        val store = NfExperiment.store(args)
        val marker = java.io.File(NfExperiment.arg(args, "store") ?: "nf-checkpoints", "nf7-test-done")
        check(!marker.exists()) { "NF-7 TEST has already been run with this store: ${marker.readText()}" }
        marker.writeText("started ${java.time.Instant.now()}\n")
        val kn = KneserNeyBytes(7, NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TRAIN))
        val validation = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.VALIDATION)
        val test = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TEST)
        println("HYBRID-TEST validationSegments=${validation.size} testSegments=${test.size} testBytes=${NfCorpus.bytes(test)}")
        for (sha in args.filter { it.startsWith("ckpt=") }.map { it.substringAfter('=') }) {
            val restored = NfCheckpoint.decode(store.get(sha))
            val model = NfModel(restored.params)
            val fit = concat(validation.map { bytesOf(model, tok, kn, it) })
            val lambda = bestFixed(fit).coerceIn(1e-6, 1 - 1e-6)
            val scored = concat(test.map { bytesOf(model, tok, kn, it) })
            val n = scored.size.toDouble()
            println("HYBRID-TEST ${restored.meta["family"]} seed=${restored.meta["seed"]} epoch=${restored.meta["epoch"]} ckpt=$sha " +
                "lambda=${"%.3f".format(lambda)} neuralBPB=${"%.4f".format(fixedBits(scored, 1.0) / n)} " +
                "knBPB=${"%.4f".format(fixedBits(scored, 0.0) / n)} fixedBPB=${"%.4f".format(fixedBits(scored, lambda) / n)}")
            System.out.flush()
        }
        marker.appendText("finished ${java.time.Instant.now()}\n")
    }

    fun run(docs: List<NfCorpus.Doc>, tok: LmBpeTokenizer, args: Array<String>) {
        val train = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TRAIN)
        val validationDocs = docs.filter { it.domain == NfCorpus.Domain.DOC && it.split == Split.VALIDATION }.sortedBy { it.path }
        val kn = KneserNeyBytes(7, train)
        val store = NfExperiment.store(args)
        val bytesTotal = validationDocs.sumOf { d -> d.segments.sumOf { it.toByteArray(Charsets.UTF_8).size } }
        println("HYBRID validationDocs=${validationDocs.size} folds=${validationDocs.groupBy { fold(it.path) }.mapValues { it.value.size }} bytes=$bytesTotal")
        for (sha in args.filter { it.startsWith("ckpt=") }.map { it.substringAfter('=') }) {
            val restored = NfCheckpoint.decode(store.get(sha))
            val model = NfModel(restored.params)
            val perFold = (0..1).associateWith { f ->
                concat(validationDocs.filter { fold(it.path) == f }.flatMap { d -> d.segments.map { bytesOf(model, tok, kn, it) } })
            }
            val all = concat(listOf(perFold.getValue(0), perFold.getValue(1)))
            val n = all.size.toDouble()
            var fixed = 0.0; var gate = 0.0
            val chosen = ArrayList<String>()
            for (f in 0..1) {
                val fitOn = perFold.getValue(f); val scoreOn = perFold.getValue(1 - f)
                val lambda = bestFixed(fitOn)
                val w = fitGate(fitOn, lambda)
                fixed += fixedBits(scoreOn, lambda.coerceIn(1e-6, 1 - 1e-6))
                gate += gateBits(scoreOn, w)
                chosen += "fold$f:lambda=${"%.3f".format(lambda)} gate=(${w.joinToString(",") { "%.3f".format(it) }})"
            }
            println("HYBRID ${restored.meta["family"]} seed=${restored.meta["seed"]} epoch=${restored.meta["epoch"]} ckpt=$sha " +
                "neuralBPB=${"%.4f".format(fixedBits(all, 1.0) / n)} knBPB=${"%.4f".format(fixedBits(all, 0.0) / n)} " +
                "fixedBPB=${"%.4f".format(fixed / n)} anchoredBPB=${"%.4f".format(gate / n)} ${chosen.joinToString(" ")}")
            System.out.flush()
        }
    }
}
