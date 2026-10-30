package at.heinz.deviceai.intelligence.ewm

/**
 * EXP-20 M6-B4 blast-radius check: every supersession edge Ewm1Memory derives on the existing fact corpora.
 * Run once with the unmodified and once with the patched Ewm1EvidenceWorkspace.kt and diff the two outputs.
 *   main <facts.tsv: list<TAB>text> <outFile>
 */
object M6B4SupersessionDiff {
    @JvmStatic fun main(args: Array<String>) {
        val lists = java.io.File(args[0]).readLines().filter { it.isNotBlank() }.map { it.split('\t', limit = 2) }
            .groupBy({ it[0] }, { it[1] })
        val sb = StringBuilder()
        var edges = 0
        for ((list, texts) in lists.toSortedMap()) {
            val m = Ewm1Memory(texts.mapIndexed { i, t -> "f$i" to t })
            for ((later, earlier) in m.supersedes.toSortedMap()) for (e in earlier.sorted()) {
                edges++
                sb.append("$list\t$later->$e\t${m.byId.getValue(later).text} | ${m.byId.getValue(e).text}\n")
            }
        }
        sb.append("lists=${lists.size} facts=${lists.values.sumOf { it.size }} edges=$edges\n")
        java.io.File(args[1]).writeText(sb.toString()); print(sb.takeLast(80))
    }
}
