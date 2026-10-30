package at.heinz.deviceai.research.exp20.m5

import at.heinz.deviceai.learning.Cre1InferenceEncoder
import at.heinz.deviceai.research.exp20.p2.P2Stats
import at.heinz.deviceai.research.exp20.p2.P2Tsv
import at.heinz.deviceai.research.exp20.p3.P3Probe
import at.heinz.deviceai.research.exp20.p3.P4Associative
import java.io.File

/**
 * EXP-20 M5-A3/A4 (preregistered in docs/research/exp20/EXP-20-M5-A3-VAL-PROBE-PREREG-20261030.md): parameter-free arms
 * on M5 DEV/VAL with the frozen parity CRE1 checkpoint. Internal development evidence only; SEALED is never read.
 *   run <dev-val.tsv> <cre1Checkpoint> <expectedDataSha> <expectedCre1Sha> <outFile>
 */
object M5ArmProbe {
    private val ARMS = listOf("JACCARD", "HYBRID_LEX", "CRE1", "HYBRID_CRE1", "QUERY_BLIND_MEDOID", "HOPFIELD2_B4")

    private fun scores(arm: String, cre: Cre1InferenceEncoder, q: String, mems: List<String>): DoubleArray = when (arm) {
        "JACCARD" -> DoubleArray(mems.size) { P3Probe.jaccard(q, mems[it]) }
        "QUERY_BLIND_MEDOID" -> { val mv = mems.map { cre.embed(it, "m") }
            DoubleArray(mems.size) { j -> mv.indices.sumOf { i -> mv[i].indices.sumOf { d -> mv[i][d] * mv[j][d] } } } }
        else -> P4Associative.scores(arm, cre, q, mems)
    }

    @JvmStatic fun main(args: Array<String>) {
        val data = File(args[0]); val ckpt = File(args[1])
        check(P2Tsv.sha256(data) == args[2]) { "M5 data hash mismatch" }
        check(P2Tsv.sha256(ckpt) == args[3]) { "CRE1 checkpoint hash mismatch" }
        val cre = Cre1InferenceEncoder.restore(ckpt.readBytes())
        val groups = M5Data.read(data).groupBy { it.group }.toSortedMap().values.toList()
        // (split, kind, query, memories with TRUE first)
        val cases = groups.map { g -> listOf(g[0].split.name, g[0].kind.name, g.single { it.role == "QUERY" }.text) to
            (listOf(g.single { it.role == "TRUE" }.text) + g.filter { it.role == "CANDIDATE" }.map { it.text }) }
        val sb = StringBuilder("EXP-20 M5-A3/A4 VAL probe (internal; SEALED not read) data=${args[2]} cre1=${args[3]}\n")
        val hit = HashMap<String, BooleanArray>()
        for (arm in ARMS) {
            val ok = BooleanArray(cases.size) { i -> val s = scores(arm, cre, cases[i].first[2], cases[i].second); s.drop(1).all { it < s[0] } }
            hit[arm] = ok
            fun n(split: String, kind: String) = cases.indices.count { cases[it].first[0] == split && cases[it].first[1] == kind && ok[it] }
            val valS = n("VAL", "S"); val valL = n("VAL", "L")
            val pass = valL >= 0.8 * 64 && valS >= 0.9 * 64
            sb.append("%-18s DEV L=%2d/64 S=%2d/64 | VAL L=%2d/64 S=%2d/64 nearMissConfusion_S=%2d/64 | precondition=%s\n"
                .format(arm, n("DEV", "L"), n("DEV", "S"), valL, valS, 64 - valS, if (pass) "PASS" else "FAIL"))
        }
        val valIdx = cases.indices.filter { cases[it].first[0] == "VAL" }
        val plan = listOf("HYBRID_CRE1" to "HYBRID_LEX", "HYBRID_CRE1" to "JACCARD", "HYBRID_CRE1" to "QUERY_BLIND_MEDOID")
        val bc = plan.map { (x, y) -> val a = hit.getValue(x); val b = hit.getValue(y); valIdx.count { a[it] && !b[it] } to valIdx.count { !a[it] && b[it] } }
        val ps = bc.map { P2Stats.mcnemarExact(it.first, it.second) }.toDoubleArray(); val holm = P2Stats.holm(ps)
        sb.append("\nVAL comparisons (128 groups, exact McNemar, Holm over 3, MDE +5 pp) - development evidence, not confirmation\n")
        plan.forEachIndexed { i, (x, y) ->
            val a = hit.getValue(x); val b = hit.getValue(y)
            val ci = P2Stats.bootstrapDiff(DoubleArray(valIdx.size) { if (a[valIdx[it]]) 1.0 else 0.0 }, DoubleArray(valIdx.size) { if (b[valIdx[it]]) 1.0 else 0.0 })
            sb.append("  %s vs %s: b=%d c=%d Holm=%.3g diff=%+.4f 95%%CI[%+.4f,%+.4f] -> %s\n".format(x, y, bc[i].first, bc[i].second, holm[i], ci.estimate, ci.lo95, ci.hi95, P2Stats.verdict(holm[i], ci)))
        }
        sb.append("SEALED=UNOPENED FOLD7=DEVICE_NOT_RUN\n")
        File(args[4]).apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb)
    }
}
