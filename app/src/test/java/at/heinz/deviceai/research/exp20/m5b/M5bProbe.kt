package at.heinz.deviceai.research.exp20.m5b

import at.heinz.deviceai.learning.Cre1InferenceEncoder
import at.heinz.deviceai.research.exp20.p2.P2Stats
import at.heinz.deviceai.research.exp20.p2.P2Tsv
import at.heinz.deviceai.research.exp20.p3.P3Probe
import at.heinz.deviceai.research.exp20.p3.P4Associative
import java.io.File

/**
 * EXP-20 M5b evaluation (preregistered in EXP-20-M5B-PREREGISTRATION-20261031.md). Reads TRAIN/VAL only.
 *   generate-val <out-val.tsv>
 *   run <train.tsv> <val.tsv> <parity.cre1> <trained.cre1> <outFile>
 */
object M5bProbe {
    private class Case(val kind: M5bData.Kind, val dayFar: Boolean, val placeFar: Boolean, val query: String, val mems: List<String>)

    private fun blind(cre: Cre1InferenceEncoder, mems: List<String>): DoubleArray {
        val mv = mems.map { cre.embed(it, "m") }
        return DoubleArray(mems.size) { j -> mv.indices.sumOf { i -> mv[i].indices.sumOf { d -> mv[i][d] * mv[j][d] } } }
    }

    @JvmStatic fun main(args: Array<String>) {
        when (args[0]) {
            "generate-val" -> { val f = File(args[1]); M5bData.write(M5bData.generate(M5bData.Split.VAL, M5bValFamilies.families), f); println("M5B_VAL sha256=${P2Tsv.sha256(f)}") }
            "run" -> run(File(args[1]), File(args[2]), File(args[3]), File(args[4]), File(args[5]))
        }
    }

    private fun run(trainF: File, valF: File, parityF: File, trainedF: File, out: File) {
        val regenerated = File.createTempFile("m5btrain", ".tsv").apply { deleteOnExit() }
        M5bData.write(M5bData.generate(M5bData.Split.TRAIN, M5bData.train), regenerated)
        check(P2Tsv.sha256(regenerated) == P2Tsv.sha256(trainF) && P2Tsv.sha256(trainF) == "8466dbccfec589fc1c54a0702c6a9e2f9241fbb78100fb415b5e4147a48f52e4") { "TRAIN changed" }
        check(P2Tsv.sha256(parityF) == "2290194d25cc98cf6cc42e6b57f5ddcb2105ba14115e2e5295ad50c906f10db6") { "parity checkpoint changed" }
        val parity = Cre1InferenceEncoder.restore(parityF.readBytes()); val trained = Cre1InferenceEncoder.restore(trainedF.readBytes())
        val rows = M5bData.read(valF); require(rows.all { it.split == M5bData.Split.VAL })
        val cases = rows.groupBy { it.group }.toSortedMap().values.map { g ->
            val idx = g[0].group.takeLast(3).toInt()
            Case(g[0].kind, idx % 2 == 1, (idx / 2) % 2 == 1, g.single { it.role == "QUERY" }.text,
                listOf(g.single { it.role == "TRUE" }.text) + g.filter { it.role == "CANDIDATE" }.map { it.text })
        }
        val sb = StringBuilder("EXP-20 M5b VAL evaluation train=${P2Tsv.sha256(trainF)} val=${P2Tsv.sha256(valF)} parity=${P2Tsv.sha256(parityF)} trained=${P2Tsv.sha256(trainedF)} steps=${trained.steps}\n")
        fun top1(s: DoubleArray) = s.drop(1).all { it < s[0] }
        // data validity gate with both checkpoints
        for ((name, cre) in listOf("PARITY" to parity, "TRAINED" to trained)) {
            val hits = cases.count { top1(blind(cre, it.mems)) }
            sb.append("GATE query-blind medoid ($name) VAL strict top1=$hits/${cases.size} -> ${if (hits <= 0.175 * cases.size) "PASS" else "INVALID"}\n")
        }
        val arms: Map<String, (Case) -> DoubleArray> = linkedMapOf(
            "JACCARD" to { c -> DoubleArray(8) { P3Probe.jaccard(c.query, c.mems[it]) } },
            "HYBRID_LEX" to { c -> P4Associative.scores("HYBRID_LEX", parity, c.query, c.mems) },
            "CRE1" to { c -> P4Associative.scores("CRE1", parity, c.query, c.mems) },
            "HYBRID_CRE1" to { c -> P4Associative.scores("HYBRID_CRE1", parity, c.query, c.mems) },
            "CRE1_TRAINED" to { c -> P4Associative.scores("CRE1", trained, c.query, c.mems) },
            "HYBRID_CRE1_TRAINED" to { c -> P4Associative.scores("HYBRID_CRE1", trained, c.query, c.mems) },
            "NORM_JACCARD" to { c -> val q = M5bNormalizer.normalize(c.query); DoubleArray(8) { P3Probe.jaccard(q, c.mems[it]) } },
            "NORM_HYBRID" to { c -> P4Associative.scores("HYBRID_LEX", parity, M5bNormalizer.normalize(c.query), c.mems) },
            "QUERY_BLIND_MEDOID" to { c -> blind(parity, c.mems) })
        val hit = LinkedHashMap<String, BooleanArray>()
        fun sub(ok: BooleanArray, p: (Case) -> Boolean) = cases.indices.filter { p(cases[it]) }.let { ix -> "${ix.count { ok[it] }}/${ix.size}" }
        sb.append("\narm                  L        S        S:day-near S:day-far S:place-near S:place-far S:far-far  precondition\n")
        for ((name, f) in arms) {
            val ok = BooleanArray(cases.size) { top1(f(cases[it])) }; hit[name] = ok
            val l = cases.indices.count { cases[it].kind == M5bData.Kind.L && ok[it] }; val s = cases.indices.count { cases[it].kind == M5bData.Kind.S && ok[it] }
            sb.append("%-20s %-8s %-8s %-10s %-9s %-12s %-11s %-9s %s\n".format(name, "$l/64", "$s/64",
                sub(ok) { it.kind == M5bData.Kind.S && !it.dayFar }, sub(ok) { it.kind == M5bData.Kind.S && it.dayFar },
                sub(ok) { it.kind == M5bData.Kind.S && !it.placeFar }, sub(ok) { it.kind == M5bData.Kind.S && it.placeFar },
                sub(ok) { it.kind == M5bData.Kind.S && it.dayFar && it.placeFar },
                if (l >= 0.8 * 64 && s >= 0.9 * 64) "PASS" else "FAIL"))
        }
        // expected strict hits when choosing uniformly among the lexically maximal (Jaccard) candidates
        fun tieChance(kind: M5bData.Kind) = cases.filter { it.kind == kind }.sumOf { c ->
            val s = DoubleArray(8) { P3Probe.jaccard(c.query, c.mems[it]) }; val m = s.max(); val tied = s.indices.filter { s[it] == m }
            if (0 in tied) 1.0 / tied.size else 0.0 }
        sb.append("TIE_CHANCE expected hits: L=%.1f/64 S=%.1f/64\n".format(tieChance(M5bData.Kind.L), tieChance(M5bData.Kind.S)))
        val a = "HYBRID_CRE1_TRAINED"; val b = "NORM_HYBRID"
        val best = if (hit.getValue(a).count { it } >= hit.getValue(b).count { it }) a else b
        val plan = listOf(a to "HYBRID_CRE1", b to "HYBRID_LEX", a to b, best to "QUERY_BLIND_MEDOID")
        val bc = plan.map { (x, y) -> val p = hit.getValue(x); val q = hit.getValue(y); p.indices.count { p[it] && !q[it] } to p.indices.count { !p[it] && q[it] } }
        val ps = bc.map { P2Stats.mcnemarExact(it.first, it.second) }.toDoubleArray(); val holm = P2Stats.holm(ps)
        sb.append("\nVAL comparisons (128 groups, exact McNemar, Holm over 4, MDE +5 pp)\n")
        plan.forEachIndexed { i, (x, y) ->
            val p = hit.getValue(x); val q = hit.getValue(y)
            val ci = P2Stats.bootstrapDiff(DoubleArray(p.size) { if (p[it]) 1.0 else 0.0 }, DoubleArray(q.size) { if (q[it]) 1.0 else 0.0 })
            sb.append("  %s vs %s: b=%d c=%d Holm=%.3g diff=%+.4f 95%%CI[%+.4f,%+.4f] -> %s\n".format(x, y, bc[i].first, bc[i].second, holm[i], ci.estimate, ci.lo95, ci.hi95, P2Stats.verdict(holm[i], ci)))
        }
        sb.append("SEALED=UNOPENED FOLD7=DEVICE_NOT_RUN\n")
        out.apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb)
    }
}
