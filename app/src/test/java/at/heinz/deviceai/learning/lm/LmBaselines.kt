package at.heinz.deviceai.learning.lm

import at.heinz.deviceai.learning.DecoderCorpus
import at.heinz.deviceai.learning.DecoderSplit
import at.heinz.deviceai.learning.TinyContextDecoder
import kotlin.math.ln
import kotlin.math.max

/** Bits needed to code one segment (content bytes only) and its byte count. */
fun interface SegmentScorer { fun bits(segment: String): Double }

/** Add-one byte unigram trained on TRAIN segments. */
class UnigramBytes(train: List<String>) : SegmentScorer {
    private val counts = LongArray(256)
    private val total: Long
    init {
        for (s in train) for (b in s.toByteArray(Charsets.UTF_8)) counts[b.toInt() and 255]++
        total = counts.sum()
    }
    override fun bits(segment: String) = segment.toByteArray(Charsets.UTF_8)
        .sumOf { -ln((counts[it.toInt() and 255] + 1.0) / (total + 256.0)) } / ln(2.0)
}

/**
 * Interpolated Kneser–Ney byte n-gram (Chen & Goodman 1999) with one absolute discount per order, D = n1/(n1 + 2 n2).
 * The highest order uses raw counts, lower orders continuation counts; the base distribution is uniform over 256
 * bytes. Segment starts are padded with byte 0 (never part of the text).
 */
class KneserNeyBytes(val order: Int, train: List<String>) : SegmentScorer {
    private val counts = Array(order + 1) { HashMap<Long, Int>() }      // k-gram -> count (raw at top, continuation below)
    private val ctxTotal = Array(order + 1) { HashMap<Long, Long>() }
    private val ctxTypes = Array(order + 1) { HashMap<Long, Int>() }
    private val discount = DoubleArray(order + 1)

    init {
        require(order in 1..7)
        val raw = Array(order + 1) { HashMap<Long, Int>() }
        for (s in train) {
            val b = s.toByteArray(Charsets.UTF_8)
            for (i in b.indices) for (k in 1..order) raw[k].merge(pack(b, i, k), 1, Int::plus)
        }
        counts[order] = raw[order]
        for (k in 1 until order) {
            val cont = HashMap<Long, Int>()
            for (key in raw[k + 1].keys) cont.merge(dropFirst(key, k + 1), 1, Int::plus)
            counts[k] = cont
        }
        for (k in 1..order) {
            var n1 = 0; var n2 = 0
            for ((key, c) in counts[k]) {
                if (c == 1) n1++ else if (c == 2) n2++
                val ctx = dropLast(key, k)
                ctxTotal[k].merge(ctx, c.toLong(), Long::plus)
                ctxTypes[k].merge(ctx, 1, Int::plus)
            }
            discount[k] = if (n1 + n2 == 0) .5 else n1.toDouble() / (n1 + 2.0 * n2)
        }
    }

    override fun bits(segment: String): Double {
        val b = segment.toByteArray(Charsets.UTF_8)
        var nats = 0.0
        for (i in b.indices) nats -= ln(prob(b, i, order))
        return nats / ln(2.0)
    }

    /** Per byte of [segment]: its probability, the order of the longest context seen in TRAIN and that context's count. */
    class Detail(val prob: DoubleArray, val order: IntArray, val count: LongArray)

    fun detail(segment: String): Detail {
        val b = segment.toByteArray(Charsets.UTF_8)
        val o = IntArray(b.size); val n = LongArray(b.size)
        for (i in b.indices) for (k in order downTo 1) {
            val total = ctxTotal[k][dropLast(pack(b, i, k), k)] ?: continue
            o[i] = k; n[i] = total; break
        }
        return Detail(DoubleArray(b.size) { prob(b, it, order) }, o, n)
    }

    private fun prob(b: ByteArray, i: Int, k: Int): Double {
        val lower = if (k == 1) 1.0 / 256 else prob(b, i, k - 1)
        val key = pack(b, i, k)
        val ctx = dropLast(key, k)
        val total = ctxTotal[k][ctx] ?: return lower
        val c = counts[k][key] ?: 0
        val d = discount[k]
        return max(c - d, 0.0) / total + d * ctxTypes[k].getValue(ctx) / total * lower
    }

    private companion object {
        fun sym(b: ByteArray, i: Int) = if (i < 0) 0L else (b[i].toLong() and 255)
        /** k-gram ending at i, tagged with k so that different orders never collide. */
        fun pack(b: ByteArray, i: Int, k: Int): Long { var key = k.toLong(); for (j in i - k + 1..i) key = (key shl 8) or sym(b, j); return key }
        fun dropFirst(key: Long, k: Int): Long = ((k - 1).toLong() shl (8 * (k - 1))) or (key and ((1L shl (8 * (k - 1))) - 1))
        /** The context (k − 1 symbols) of a tagged k-gram, tagged with k to stay unique per order. */
        fun dropLast(key: Long, k: Int): Long = (k.toLong() shl 60) or ((key ushr 8) and ((1L shl (8 * (k - 1))) - 1))
    }
}

/**
 * CDX1 (the existing TinyContextDecoder, unchanged) trained and scored only through its public API:
 * TRAIN segments are fed in corpora of ≤ 64 texts / ≤ 8 KB (its limits); scoring uses `evaluate` (mean NLL over
 * bytes + EOS) minus the EOS term from `nextTokenProbabilities`, so only content bytes are counted.
 */
class Cdx1Baseline(seed: Long) : SegmentScorer {
    val model = TinyContextDecoder(seed)

    fun trainEpoch(train: List<String>, learningRate: Double = .035) {
        for (chunk in chunks(train)) {
            val corpus = DecoderCorpus(chunk, DecoderSplit.TRAIN)
            val samples = chunk.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
            var done = 0
            while (done < samples) done += model.train(corpus, minOf(20000, samples - done), learningRate, maxMillis = 5000).steps
        }
    }

    override fun bits(segment: String): Double {
        val n = segment.toByteArray(Charsets.UTF_8).size
        val mean = model.evaluate(DecoderCorpus(listOf(segment), DecoderSplit.AUDIT))
        val eos = -ln(model.nextTokenProbabilities(segment)[257].toDouble().coerceAtLeast(1e-12))
        return (mean * (n + 1) - eos) / ln(2.0)
    }

    private fun chunks(texts: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>(); var cur = ArrayList<String>(); var bytes = 0
        for (t in texts) {
            val n = t.toByteArray(Charsets.UTF_8).size
            if (cur.size == 64 || bytes + n > 8192) { out += cur; cur = ArrayList(); bytes = 0 }
            cur += t; bytes += n
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}
