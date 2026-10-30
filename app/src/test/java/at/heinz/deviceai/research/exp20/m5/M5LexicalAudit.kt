package at.heinz.deviceai.research.exp20.m5

import java.io.File

/**
 * Simple query-conditioned Jaccard retrieval on M5 DEV/VAL.
 * This is a characterisation baseline; it neither fits a model nor opens SEALED.
 */
object M5LexicalAudit {
    private val words = Regex("[\\p{L}\\p{N}]+")
    private fun tokens(s: String): Set<String> = words.findAll(s.lowercase()).map { it.value }.toSet()
    fun jaccard(a: String, b: String): Double {
        val aTerms = tokens(a); val bTerms = tokens(b)
        return (aTerms intersect bTerms).size.toDouble() / (aTerms union bTerms).size.coerceAtLeast(1)
    }
    fun top1(group: List<M5Data.Row>): Boolean {
        val query = group.single { it.role == "QUERY" }.text
        val records = group.filter { it.role != "QUERY" }
        val scores = records.map { jaccard(query, it.text) }
        val gold = records.indexOfFirst { it.role == "TRUE" }
        return scores.indices.all { it == gold || scores[it] < scores[gold] - 1e-12 }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "usage: M5LexicalAudit <DEV_VAL.tsv>" }
        val file = File(args[0]); val rows = M5Data.read(file)
        val groups = rows.groupBy { it.group }.values
        for (split in M5Data.Split.values()) for (kind in M5Data.Kind.values()) {
            val selected = groups.filter { it[0].split == split && it[0].kind == kind }
            val count = selected.count(::top1)
            println("M5_JACCARD split=$split kind=$kind top1=$count/${selected.size}")
            // Expected on the frozen synthetic specification only. Not a general semantic quality claim.
            check(count == if (kind == M5Data.Kind.L) selected.size else 0)
        }
        println("M5_JACCARD_AUDIT=PASS DATA_SHA256=${M5Data.sha256(file)}")
        println("CRE1=NOT_RUN SEALED=UNOPENED FOLD7=DEVICE_NOT_RUN")
    }
}
