package at.heinz.deviceai.research.exp20.p3

import at.heinz.deviceai.learning.Cre1InferenceEncoder
import java.io.File

/**
 * EXP-20 P4 POST-HOC CONTROL (not preregistered; added after seeing HOPFIELD2 win). In P3 every near miss is the TRUE
 * memory with one slot changed, so TRUE is the medoid of its candidate set by construction. A Hopfield retrieval step
 * xi = sum_i p_i m_i favours that medoid. This control ranks candidates WITHOUT the query: score_j = sum_i <m_i, m_j>.
 * If it matches HOPFIELD2, the P4 gain is a dataset-construction artifact, not associative intelligence.
 *   run <p3-dataset.tsv> <cre1Checkpoint> <outFile>
 */
object P4MedoidControl {
    @JvmStatic fun main(args: Array<String>) {
        val rows = P3Probe.read(File(args[0])); val cre = Cre1InferenceEncoder.restore(File(args[1]).readBytes())
        var ok = 0; var okL = 0; var okS = 0; var n = 0
        for ((_, rs) in rows.groupBy { it.group }.toSortedMap()) {
            val mems = listOf(rs.single { it.role == "TRUE" }.text) + rs.filter { it.role != "QUERY" && it.role != "TRUE" }.map { it.text }
            val mv = mems.map { cre.embed(it, "m") }
            val s = DoubleArray(mems.size) { j -> mv.indices.sumOf { i -> mv[i].indices.sumOf { d -> mv[i][d] * mv[j][d] } } }
            n++
            if (s.drop(1).all { it < s[0] }) { ok++; if (rs[0].type == 'L') okL++ else okS++ }
        }
        val out = "EXP-20 P4 POST-HOC query-blind MEDOID control: top1=$ok/$n (L $okL/60, S $okS/60)\n" +
            "Reading: if this approaches HOPFIELD2_B4 (99/120, S 40/60), the Hopfield gain is explained by TRUE being the medoid of the near-miss star, not by the query.\n"
        File(args[2]).writeText(out); print(out)
    }
}
