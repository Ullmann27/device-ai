package at.heinz.deviceai.research.exp20.m5

/** Pure, dependency-free negative controls for ranking; no CRE1 model is used by these checks. */
object M5BlindGateSelfTest {
    @JvmStatic fun main(args: Array<String>) {
        var passed = 0
        fun checkCase(label: String, fn: () -> Unit) { fn(); passed++; println("PASS $label") }
        checkCase("all-identical-embeddings-are-ties") {
            val r = M5Cre1BlindGate.rank(List(8) { doubleArrayOf(1.0, 0.0) })
            check(r.strictWinner == null && r.tiedMaximum)
        }
        checkCase("nonfinite-embeddings-rejected") {
            check(runCatching { M5Cre1BlindGate.rank(List(8) { doubleArrayOf(Double.NaN) }) }.isFailure)
        }
        checkCase("mismatched-dimensions-rejected") {
            check(runCatching { M5Cre1BlindGate.rank(List(7) { doubleArrayOf(1.0) } + doubleArrayOf(1.0, 2.0)) }.isFailure)
        }
        checkCase("query-blind-medoid-can-expose-cheat") {
            val centred = List(8) { i -> if (i == 0) doubleArrayOf(1.0, 0.0) else doubleArrayOf(0.7, (i - 4).toDouble() * 0.04) }
            val r = M5Cre1BlindGate.rank(centred)
            check(r.strictWinner != null)
            check(r.scores.size == 8)
        }
        checkCase("positions-not-forced-by-model") {
            val rows = M5Data.generate()
            val positions = rows.groupBy { it.group }.values.filter { it[0].split == M5Data.Split.VAL }.map { g ->
                g.filter { it.role != "QUERY" }.indexOfFirst { it.role == "TRUE" }
            }
            check((0..7).all { i -> positions.count { it == i } == 16 })
        }
        checkCase("VAL-gate-fails-above-threshold") {
            val ok = M5Cre1BlindGate.Summary(M5Data.Split.VAL, 128, 22, 25, 0)
            val fail = ok.copy(strictHits = 23)
            check(M5Cre1BlindGate.gatePassed(ok))
            check(!M5Cre1BlindGate.gatePassed(fail))
            check(!M5Cre1BlindGate.gatePassed(ok.copy(split = M5Data.Split.DEV)))
        }
        checkCase("query-text-not-given-to-embedder") {
            val rows = M5Data.generate()
            var calls = 0
            val embed: (String) -> DoubleArray = { memory ->
                check(memory.startsWith("Frau ") || memory.startsWith("Herr "))
                calls++
                doubleArrayOf(memory.length.toDouble(), 1.0)
            }
            M5Cre1BlindGate.evaluate(rows, M5Data.Split.VAL, embed)
            check(calls == 128 * 8)
        }
        checkCase("mutating-query-text-cannot-change-query-blind-score") {
            val rows = M5Data.generate()
            val changed = rows.map { row -> if (row.role == "QUERY") row.copy(text = "Absichtlich völlig andere Frage?") else row }
            val embed: (String) -> DoubleArray = { t -> doubleArrayOf(t.length.toDouble(), t.count { it == 'e' }.toDouble()) }
            val a = M5Cre1BlindGate.evaluate(rows, M5Data.Split.VAL, embed)
            val b = M5Cre1BlindGate.evaluate(changed, M5Data.Split.VAL, embed)
            check(a == b)
        }
        println("M5_BLIND_GATE_NEGATIVE_TESTS=$passed/8 CRE1_CHECKPOINT=NOT_RUN SEALED=UNOPENED")
    }
}
