package at.heinz.deviceai.learning

import java.io.File
import java.util.Random
import kotlin.math.ln

/**
 * Experiment D (pre-registered: docs/research/CDX1-PATCH-ANOMALY-PREREG-20261023.md). CDX1 as an ADVISORY
 * patch-anomaly score versus the cheap parser verdict and a byte 4-gram, on fragments produced by
 * scripts/cdx1-patch-anomaly-dataset.mjs. Trains only on TRAIN texts, calibrates only on CAL VALID, evaluates TEST once.
 * Research only: nothing is approved, blocked, applied or promoted; no Android memory is read.
 * Run: java ... at.heinz.deviceai.learning.Cdx1PatchAnomalyExperiment <dataset.jsonl> <report.json> <dataset-sha256>
 */
object Cdx1PatchAnomalyExperiment {
    const val TRAIN_UPDATES = 400_000
    const val LEARNING_RATE = .035
    const val WINDOW = 16
    const val SEED = 20261023L

    data class Item(val id: String, val split: String, val group: String, val text: String, val parserDetected: Boolean)
    data class Scored(val item: Item, val cdx1Max: Double, val cdx1Mean: Double, val ngramMax: Double, val ngramMean: Double,
        val cdx1Nanos: Long, val ngramNanos: Long)

    /** Witten-Bell interpolated byte n-gram over 256 bytes + end marker; context padded with BOS like CDX1. */
    class ByteNgram(private val order: Int = 4) {
        private val tables = Array(order) { HashMap<Long, HashMap<Int, Int>>() }
        private fun key(history: IntArray, n: Int, k: Int): Long {
            var x = 0L
            for (i in n - k until n) x = x * 258 + (if (i < 0) 256 else history[i])
            return x
        }
        private fun symbols(text: String): IntArray {
            val b = text.toByteArray(Charsets.UTF_8)
            return IntArray(b.size + 1) { if (it == b.size) 257 else b[it].toInt() and 255 }
        }
        fun train(text: String) {
            val s = symbols(text)
            for (n in s.indices) for (k in 0 until order) tables[k].getOrPut(key(s, n, k)) { HashMap() }.merge(s[n], 1, Int::plus)
        }
        val entries: Int get() = tables.sumOf { t -> t.values.sumOf { it.size } }
        fun surprisalBits(text: String): DoubleArray {
            val s = symbols(text)
            return DoubleArray(s.size) { n ->
                var p = 1.0 / 257
                for (k in 0 until order) {
                    val m = tables[k][key(s, n, k)] ?: continue
                    val c = m.values.sum().toDouble(); val t = m.size.toDouble()
                    p = ((m[s[n]] ?: 0) + t * p) / (c + t)
                }
                -ln(p) / ln(2.0)
            }
        }
    }

    fun maxWindow(bits: DoubleArray): Double {
        if (bits.size <= WINDOW) return bits.average()
        var sum = 0.0
        for (i in 0 until WINDOW) sum += bits[i]
        var best = sum
        for (i in WINDOW until bits.size) { sum += bits[i] - bits[i - WINDOW]; if (sum > best) best = sum }
        return best / WINDOW
    }

    /** Mann-Whitney AUROC: probability that a positive scores above a negative, ties count one half. */
    fun auroc(positives: List<Double>, negatives: List<Double>): Double {
        if (positives.isEmpty() || negatives.isEmpty()) return Double.NaN
        var wins = 0.0
        for (p in positives) for (n in negatives) wins += if (p > n) 1.0 else if (p == n) .5 else 0.0
        return wins / (positives.size.toDouble() * negatives.size)
    }

    fun percentile95(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[(Math.ceil(.95 * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "usage: <dataset.jsonl> <report.json> <dataset-sha256>" }
        val items = mutableListOf<Item>(); val train = mutableListOf<String>()
        File(args[0]).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val o = StrictJson.parseObject(line)
            when (o["kind"]) {
                "item" -> items += Item(o["id"] as String, o["split"] as String, o["group"] as String, o["text"] as String,
                    (o["parser"] as Map<*, *>)["detected"] as Boolean)
                "train" -> train += o["text"] as String
            }
        }
        require(items.none { it.split == "TRAIN" && it.text in train && it.group != "VALID" }) { "corrupted text leaked into TRAIN" }

        // CDX1: fixed schedule, corpora packed within the decoder's limits, one pass per corpus visit.
        val cdx1 = TinyContextDecoder(seed = 91)
        val shuffled = train.shuffled(Random(SEED))
        val corpora = mutableListOf<MutableList<String>>(); var bytes = 0
        for (t in shuffled) {
            val size = t.toByteArray(Charsets.UTF_8).size
            if (corpora.isEmpty() || corpora.last().size == 64 || bytes + size > 8192) { corpora += mutableListOf<String>(); bytes = 0 }
            corpora.last() += t; bytes += size
        }
        val auditBefore = cdx1.evaluate(DecoderCorpus(shuffled.take(16), DecoderSplit.AUDIT))
        val t0 = System.nanoTime(); var visit = 0
        while (cdx1.updates < TRAIN_UPDATES) {
            val corpus = DecoderCorpus(corpora[visit++ % corpora.size], DecoderSplit.TRAIN)
            val samples = corpus.texts.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
            cdx1.train(corpus, steps = minOf(samples, TRAIN_UPDATES - cdx1.updates), learningRate = LEARNING_RATE, maxMillis = 5000)
        }
        val trainMillis = (System.nanoTime() - t0) / 1_000_000
        val auditAfter = cdx1.evaluate(DecoderCorpus(shuffled.take(16), DecoderSplit.AUDIT))
        val ngram = ByteNgram().also { m -> train.forEach(m::train) }

        val updates = cdx1.updates
        val scored = items.filter { it.split != "TRAIN" }.map { item ->
            val a = System.nanoTime(); val c = cdx1.byteSurprisalBits(item.text); val b = System.nanoTime()
            val n = ngram.surprisalBits(item.text); val e = System.nanoTime()
            Scored(item, maxWindow(c), c.average(), maxWindow(n), n.average(), b - a, e - b)
        }
        check(cdx1.updates == updates) { "scoring changed the model" }

        // the existing shadow study on TEST pairs (valid vs. variant): never a verdict, never an update
        val study = Cdx1PatchShadowStudy(cdx1)
        val validTest = scored.filter { it.item.split == "TEST" && it.item.group == "VALID" }.associateBy { it.item.id.substringBefore('-') }
        val pairWins = mutableMapOf<String, Pair<Int, Int>>()
        for (s in scored.filter { it.item.split == "TEST" && it.item.group != "VALID" }) {
            val original = validTest[s.item.id.substringBefore('-')] ?: continue
            val o = study.observe(Cdx1PatchShadowStudy.ReviewedPair(original.item.text, s.item.text, "exp-d-" + s.item.id))
            check(!o.canApprovePatch && !o.canOverrideVerifier && o.modelUpdatesUnchanged)
            val (w, n) = pairWins[s.item.group] ?: (0 to 0)
            pairWins[s.item.group] = (w + if (o.differenceCounterfactualMinusOriginalBits > 0) 1 else 0) to (n + 1)
        }

        fun model(name: String, score: (Scored) -> Double): String {
            val threshold = percentile95(scored.filter { it.item.split == "CAL" && it.item.group == "VALID" }.map(score))
            val test = scored.filter { it.item.split == "TEST" }
            val valid = test.filter { it.item.group == "VALID" }.map(score)
            val fpr = valid.count { it > threshold }.toDouble() / valid.size
            val groups = test.map { it.item.group }.distinct().filter { it != "VALID" }.sorted()
            val per = groups.joinToString(",") { g ->
                val xs = test.filter { it.item.group == g }
                val tpr = xs.count { score(it) > threshold }.toDouble() / xs.size
                val union = xs.count { it.item.parserDetected || score(it) > threshold }.toDouble() / xs.size
                val parser = xs.count { it.item.parserDetected }.toDouble() / xs.size
                "\"$g\":{\"n\":${xs.size},\"tpr\":${r(tpr)},\"auroc\":${r(auroc(xs.map(score), valid))},\"parser\":${r(parser)},\"parserOrModel\":${r(union)}}"
            }
            val undetected = test.filter { it.item.group != "VALID" && !it.item.parserDetected }
            val uTpr = undetected.count { score(it) > threshold }.toDouble() / undetected.size
            val uAuroc = auroc(undetected.map(score), valid)
            return "\"$name\":{\"threshold\":${r(threshold)},\"fpr\":${r(fpr)},\"groups\":{$per}," +
                "\"parserUndetected\":{\"n\":${undetected.size},\"tpr\":${r(uTpr)},\"auroc\":${r(uAuroc)}}}"
        }
        fun median(xs: List<Long>) = xs.sorted()[xs.size / 2] / 1e6
        fun p95(xs: List<Long>) = xs.sorted()[(Math.ceil(.95 * xs.size).toInt() - 1).coerceIn(0, xs.size - 1)] / 1e6
        val testScored = scored.filter { it.item.split == "TEST" }
        val report = buildString {
            append("{\"schema\":\"cdx1-patch-anomaly-report\",\"version\":1,\"datasetSha256\":\"${args[2]}\",")
            append("\"items\":{\"train\":${items.count { it.split == "TRAIN" }},\"cal\":${items.count { it.split == "CAL" }},\"test\":${testScored.size}},")
            append("\"cdx1\":{\"parameters\":${cdx1.parameterCount},\"bytes\":${cdx1.parameterCount * 8},\"updates\":$updates,")
            append("\"trainMillis\":$trainMillis,\"trainTexts\":${train.size},\"auditBitsBefore\":${r(auditBefore / ln(2.0))},\"auditBitsAfter\":${r(auditAfter / ln(2.0))}},")
            append("\"ngram\":{\"order\":4,\"entries\":${ngram.entries}},")
            append("\"timingMillisPerFragment\":{\"cdx1Median\":${r(median(testScored.map { it.cdx1Nanos }))},\"cdx1P95\":${r(p95(testScored.map { it.cdx1Nanos }))},")
            append("\"ngramMedian\":${r(median(testScored.map { it.ngramNanos }))},\"ngramP95\":${r(p95(testScored.map { it.ngramNanos }))}},")
            append("\"pairedWinRateCdx1Mean\":{" + pairWins.toSortedMap().entries.joinToString(",") { (g, wn) -> "\"$g\":${r(wn.first.toDouble() / wn.second)}" } + "},")
            append(model("cdx1Max") { it.cdx1Max } + "," + model("cdx1Mean") { it.cdx1Mean } + ",")
            append(model("ngramMax") { it.ngramMax } + "," + model("ngramMean") { it.ngramMean } + "}")
        }
        StrictJson.parseObject(report)
        File(args[1]).writeText(report + "\n")
        println(report)
    }

    private fun r(x: Double): String = if (x.isNaN()) "null" else "%.4f".format(java.util.Locale.ROOT, x)
}
