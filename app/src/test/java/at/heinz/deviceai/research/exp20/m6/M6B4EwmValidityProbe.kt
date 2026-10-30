package at.heinz.deviceai.intelligence.ewm

/**
 * EXP-20 M6-B4 PRE-CHECK (structural, no semantic success claim, no new store): how does the EXISTING EWM memory
 * (Ewm1Memory in Ewm1EvidenceWorkspace.kt, unmodified) treat a dated correction such as
 * "Ich wohne in Wien." -> "Seit Dienstag wohne ich in Graz."? Which earlier facts become superseded, which stay?
 * Lives in the ewm package only because Ewm1Memory is internal; it is compiled in the host research module.
 *   main <outFile>
 */
object M6B4EwmValidityProbe {
    private data class Case(val name: String, val facts: List<Pair<String, String>>, val expectSupersedes: Map<String, Set<String>>)

    private val cases = listOf(
        Case("dated relocation replaces only the old residence", listOf(
            "f1" to "Ich wohne in Wien.", "f2" to "Meine Schwester Anna wohnt in Linz.", "f3" to "Ich arbeite bei der Post.",
            "f4" to "Seit Dienstag wohne ich in Graz."), mapOf("f4" to setOf("f1"))),
        Case("a visit is not a relocation", listOf(
            "f1" to "Ich wohne in Wien.", "f2" to "Am Montag war ich in Graz."), emptyMap()),
        Case("another person's move leaves my residence", listOf(
            "f1" to "Ich wohne in Wien.", "f2" to "Seit Dienstag wohnt meine Schwester Anna in Graz."), emptyMap()),
        Case("correction without date", listOf(
            "f1" to "Ich wohne in Wien.", "f2" to "Ich arbeite bei der Post.", "f3" to "Inzwischen wohne ich in Graz."), mapOf("f3" to setOf("f1"))),
        Case("unrelated dated fact replaces nothing", listOf(
            "f1" to "Ich wohne in Wien.", "f2" to "Seit Dienstag spiele ich Tennis."), emptyMap()))

    @JvmStatic fun main(args: Array<String>) {
        val sb = StringBuilder("EXP-20 M6-B4 pre-check on the unmodified Ewm1Memory (supersession only; no validity interval is stored)\n")
        var agree = 0
        for (c in cases) {
            val m = Ewm1Memory(c.facts)
            val got = m.supersedes.mapValues { it.value.toSet() }.filterValues { it.isNotEmpty() }
            val ok = got == c.expectSupersedes
            if (ok) agree++
            sb.append("%-50s expected=%-16s observed=%-16s %s\n".format(c.name, c.expectSupersedes, got, if (ok) "AS_EXPECTED" else "DIFFERS"))
        }
        sb.append("diagnosis of the inputs to Ewm1Memory.replaces() for the first-person facts:\n")
        for ((id, f) in Ewm1Memory(cases[0].facts + cases[3].facts.drop(2).map { "g3" to it.second }).byId) sb.append(
            "  %-3s subject=%-8s updateMarker=%-5s groups=%s stems=%s  \"%s\"\n".format(id, f.subject, f.hasUpdateMarker, f.groups, f.contentStems, f.text))
        sb.append("summary: $agree/${cases.size} as expected. Note: 'seit Dienstag' never becomes a start date - Ewm1Memory has no temporal validity interval.\n")
        java.io.File(args[0]).apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb)
    }
}
