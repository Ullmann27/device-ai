package at.heinz.deviceai.research.exp20.p3

import at.heinz.deviceai.cognitive.HybridMemoryRetriever
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.learning.Cre1InferenceEncoder
import at.heinz.deviceai.research.exp20.p2.P2Stats
import at.heinz.deviceai.research.exp20.p2.P2Tsv
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * EXP-20 P4 (preregistered, EXPLORATORY on the used P3 set): modern-Hopfield retrieval and quantised keys on the same
 * CRE1 vectors, plus the real HybridMemoryRetriever with and without CRE1 as its semantic scorer.
 *   run <p3-dataset.tsv> <cre1Checkpoint> <outFile>
 */
object P4Associative {
    private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

    /** Candidate scores for one group; the TRUE memory is index 0 of `mems`. */
    fun scores(arm: String, cre: Cre1InferenceEncoder, q: String, mems: List<String>): DoubleArray {
        val qv by lazy { cre.embed(q, "q") }
        val mv by lazy { mems.map { cre.embed(it, "m") } }
        fun softmax(beta: Double): DoubleArray { val s = mv.map { beta * dot(qv, it) }; val mx = s.max(); val e = s.map { exp(it - mx) }; val z = e.sum(); return e.map { it / z }.toDoubleArray() }
        return when (arm) {
            "CRE1" -> DoubleArray(mems.size) { dot(qv, mv[it]) }
            "HOPFIELD1" -> softmax(8.0)
            "HOPFIELD2_B4", "HOPFIELD2_B16" -> {
                val p = softmax(if (arm.endsWith("B4")) 4.0 else 16.0)
                val xi = DoubleArray(qv.size) { d -> mv.indices.sumOf { p[it] * mv[it][d] } }
                DoubleArray(mems.size) { dot(xi, mv[it]) }
            }
            "INT8" -> { fun qz(v: DoubleArray): IntArray { val s = 127.0 / v.maxOf { abs(it) }; return IntArray(v.size) { (v[it] * s).roundToInt() } }
                val a = qz(qv); DoubleArray(mems.size) { j -> val b = qz(mv[j]); a.indices.sumOf { a[it] * b[it] }.toDouble() } }
            "SIGN32" -> { fun bits(v: DoubleArray) = v.fold(0L) { acc, x -> (acc shl 1) or (if (x >= 0) 1L else 0L) }
                val a = bits(qv); DoubleArray(mems.size) { (32 - java.lang.Long.bitCount(a xor bits(mv[it]))).toDouble() } }
            "HYBRID_LEX", "HYBRID_CRE1" -> {
                val recs = mems.mapIndexed { i, t -> MemoryRecord("m$i", MemoryType.EPISODIC, t, ProvenanceType.USER_PROVIDED, 0.9, 1_000L, 1_000L) }
                val scorer: ((String, MemoryRecord) -> Double)? = if (arm == "HYBRID_CRE1") { qq, r -> cre.score(qq, r.content) } else null
                val got = HybridMemoryRetriever.retrieve(q, recs, limit = recs.size, nowMillis = 2_000L, semanticScorer = scorer).associate { it.memory.id to it.score.total }
                DoubleArray(mems.size) { got["m$it"] ?: Double.NEGATIVE_INFINITY }
            }
            else -> error(arm)
        }
    }

    val ARMS = listOf("HYBRID_LEX", "HYBRID_CRE1", "CRE1", "HOPFIELD1", "HOPFIELD2_B4", "HOPFIELD2_B16", "INT8", "SIGN32")

    @JvmStatic fun main(args: Array<String>) {
        val rows = P3Probe.read(File(args[0])); val bytes = File(args[1]).readBytes(); val cre = Cre1InferenceEncoder.restore(bytes)
        val groups = rows.groupBy { it.group }.toSortedMap().values.map { rs ->
            Triple(rs.single { it.role == "QUERY" }.text, listOf(rs.single { it.role == "TRUE" }.text) + rs.filter { it.role != "QUERY" && it.role != "TRUE" }.map { it.text },
                rs.filter { it.role != "QUERY" && it.role != "TRUE" }.map { it.role }) to rs[0].type }
        val sb = StringBuilder("EXP-20 P4 EXPLORATORY associative/quantised retrieval  data=${P2Tsv.sha256(File(args[0]))} cre1=${P2Tsv.sha256(bytes)}\n")
        val top1 = HashMap<String, BooleanArray>(); var rankMismatch = 0
        for (arm in ARMS) {
            val ok = BooleanArray(groups.size); var near = 0; var rr = 0.0; var okL = 0; var okS = 0
            groups.forEachIndexed { g, (t, type) ->
                val s = scores(arm, cre, t.first, t.second); val others = s.drop(1)
                ok[g] = others.all { it < s[0] }; if (ok[g]) { if (type == 'L') okL++ else okS++ }
                if (others.withIndex().any { (i, v) -> t.third[i].startsWith("NEAR") && v >= s[0] }) near++
                rr += 1.0 / (1 + others.count { it >= s[0] })
                if (arm == "HOPFIELD1") { val base = scores("CRE1", cre, t.first, t.second)
                    if (s.indices.sortedByDescending { s[it] } != base.indices.sortedByDescending { base[it] }) rankMismatch++ }
            }
            top1[arm] = ok
            sb.append("%-14s top1=%d/%d (L %d/60, S %d/60) near_conf=%d MRR=%.3f\n".format(arm, ok.count { it }, ok.size, okL, okS, near, rr / groups.size))
        }
        sb.append("HOPFIELD1 rank mismatches vs CRE1: $rankMismatch/${groups.size} (expected 0: softmax is strictly monotone)\n")
        val plan = listOf("HOPFIELD2_B4" to "CRE1", "HOPFIELD2_B16" to "CRE1", "INT8" to "CRE1", "SIGN32" to "CRE1", "HYBRID_CRE1" to "HYBRID_LEX")
        val bc = plan.map { (x, y) -> val a = top1.getValue(x); val b = top1.getValue(y); a.indices.count { a[it] && !b[it] } to a.indices.count { !a[it] && b[it] } }
        val ps = bc.map { P2Stats.mcnemarExact(it.first, it.second) }.toDoubleArray(); val holm = P2Stats.holm(ps)
        sb.append("\nPLANNED COMPARISONS (exact McNemar, Holm over 5)\n")
        plan.forEachIndexed { i, (x, y) ->
            val a = top1.getValue(x); val b = top1.getValue(y)
            val ci = P2Stats.bootstrapDiff(DoubleArray(a.size) { if (a[it]) 1.0 else 0.0 }, DoubleArray(b.size) { if (b[it]) 1.0 else 0.0 })
            sb.append("  %s vs %s: b=%d c=%d Holm=%.3g diff=%+.4f 95%%CI[%+.4f,%+.4f] -> %s\n".format(x, y, bc[i].first, bc[i].second, holm[i], ci.estimate, ci.lo95, ci.hi95, P2Stats.verdict(holm[i], ci)))
        }
        sb.append("\nCOST per query, uncached, host JVM (block means over 100 x 20 queries after 2000 warm-up); bytes per stored key\n")
        val keyBytes = mapOf("CRE1" to 256, "HOPFIELD1" to 256, "HOPFIELD2_B4" to 256, "HOPFIELD2_B16" to 256, "INT8" to 32, "SIGN32" to 4, "HYBRID_LEX" to 0, "HYBRID_CRE1" to 256)
        var sink = 0.0
        for (arm in ARMS) {
            fun one(k: Int) { val (t, _) = groups[k % groups.size]; sink += scores(arm, cre, t.first, t.second)[0] }
            repeat(2000) { one(it) }
            val blocks = DoubleArray(100); var k = 0
            for (b in 0 until 100) { val t0 = System.nanoTime(); repeat(20) { one(k++) }; blocks[b] = (System.nanoTime() - t0) / 20.0 / 1000.0 }
            blocks.sort()
            sb.append("  %-14s p50=%.1f us p95=%.1f us  key=%d B (+ text/record kept authoritative)\n".format(arm, blocks[50], blocks[95], keyBytes.getValue(arm)))
        }
        sb.append("sink=$sink\n")
        File(args[2]).apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb)
    }
}
