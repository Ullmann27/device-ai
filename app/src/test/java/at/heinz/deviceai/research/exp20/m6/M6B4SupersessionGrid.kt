package at.heinz.deviceai.intelligence.ewm

/**
 * EXP-20 M6-B4 adversarial grid: earlier fact x later fact over holders, relations, places and update forms.
 * The gold label comes from a declarative rule written before any run, not from Ewm1Memory:
 *   replace  <=>  same holder (same surface) AND same relation AND relation is a state (not a visit)
 *                 AND (relation is single-valued "wohnen" OR the later fact carries an update form)
 *                 AND the later fact does not start in the future ("Ab nächstem Monat ...")
 * Every pair is evaluated in a memory that also holds two bystander facts (another person, another relation),
 * which must never be replaced. Run with the unmodified and with the patched Ewm1EvidenceWorkspace.kt.
 *   main <outFile>
 */
object M6B4SupersessionGrid {
    private class Holder(val name: String, val nominative: String, val person: Int) // 1 = ich, 4 = wir, 3 = 3rd sg
    private val holders = listOf(Holder("ich", "ich", 1), Holder("wir", "wir", 4), Holder("anna", "meine Schwester Anna", 3),
        Holder("max", "Max", 3))
    private val relations = listOf("wohnen", "arbeiten", "tennis", "besuch")
    private val places = listOf("Wien", "Graz", "Linz")
    private val forms = listOf("none", "seit", "inzwischen", "mittlerweile", "jetzt", "nichtmehr", "zukunft")

    private fun verb(rel: String, p: Int) = when (rel) {
        "wohnen" -> mapOf(1 to "wohne", 4 to "wohnen", 3 to "wohnt")
        "arbeiten" -> mapOf(1 to "arbeite", 4 to "arbeiten", 3 to "arbeitet")
        "tennis" -> mapOf(1 to "spiele", 4 to "spielen", 3 to "spielt")
        else -> mapOf(1 to "war", 4 to "waren", 3 to "war")
    }.getValue(p)

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** One sentence; inverted forms put the adverbial first (German V2). */
    private fun sentence(h: Holder, rel: String, place: String, form: String): String {
        val v = verb(rel, h.person)
        val tail = if (rel == "tennis") "in $place Tennis" else "in $place"
        val visit = if (rel == "besuch") " gestern" else ""
        return when (form) {
            "none" -> "${cap(h.nominative)} $v$visit $tail."
            "seit" -> "Seit Dienstag $v ${h.nominative} $tail."
            "inzwischen" -> "Inzwischen $v ${h.nominative} $tail."
            "mittlerweile" -> "Mittlerweile $v ${h.nominative} $tail."
            "jetzt" -> "${cap(h.nominative)} $v jetzt $tail."
            "zukunft" -> "Ab nächstem Monat $v ${h.nominative} $tail."
            else -> "${cap(h.nominative)} $v nicht mehr $tail."
        }
    }

    private fun gold(h1: Holder, r1: String, h2: Holder, r2: String, form: String) =
        h1 === h2 && r1 == r2 && r1 != "besuch" && form != "zukunft" && (r1 == "wohnen" || form != "none")

    @JvmStatic fun main(args: Array<String>) {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0; var bystanderLost = 0; var n = 0
        val byCell = java.util.TreeMap<String, IntArray>() // holder/form -> [errors, cases]
        val errors = java.util.TreeMap<String, Pair<Int, String>>() // error kind -> count, first example
        for (h1 in holders) for (r1 in relations) for (h2 in holders) for (r2 in relations) for (form in forms)
            for (p1 in places) for (p2 in places) {
                if (form == "nichtmehr" && p2 != p1) continue      // "nicht mehr in X" refers to the earlier place
                if (form != "nichtmehr" && p2 == p1) continue      // a changed value, not a repetition
                if (r2 == "besuch" && form != "none") continue
                val earlier = sentence(h1, r1, p1, "none"); val later = sentence(h2, r2, p2, form)
                // bystanders: another person and another relation of the speaker, both in a third city
                val bystanderPerson = "Mein Bruder Paul wohnt in Salzburg." // a third person, never a holder of the grid
                val bystanderRel = if (r1 == "tennis" || r2 == "tennis") "Ich lese in Salzburg Zeitung." else "Ich spiele in Salzburg Tennis."
                val m = Ewm1Memory(listOf("e" to earlier, "b1" to bystanderPerson, "b2" to bystanderRel, "l" to later))
                val got = "e" in m.supersedes["l"].orEmpty()
                val want = gold(h1, r1, h2, r2, form)
                val lost = listOf("b1", "b2").count { it in m.supersedes["l"].orEmpty() }
                n++; bystanderLost += lost
                when { got && want -> tp++; got && !want -> fp++; !got && want -> fn++; else -> tn++ }
                val cell = byCell.getOrPut("${h2.name}/$form") { IntArray(2) }
                cell[1]++
                if (got != want || lost > 0) {
                    cell[0]++
                    val lostIds = listOf("b1", "b2").filter { it in m.supersedes["l"].orEmpty() }
                    val kind = "${if (got == want) "OK" else if (got) "FP" else "FN"}${lostIds.joinToString("") { "+LOST_$it" }} " +
                        "${h1.name}/$r1 -> ${h2.name}/$r2/$form"
                    val ex = "\"$earlier\" -> \"$later\"" + lostIds.joinToString("") { " loses \"${m.byId.getValue(it).text}\"" }
                    errors[kind] = (errors[kind]?.first ?: 0) + 1 to (errors[kind]?.second ?: ex)
                }
            }
        val sb = StringBuilder("EXP-20 M6-B4 supersession grid (gold = declarative rule, see source)\n")
        sb.append("cases=$n TP=$tp FP=$fp FN=$fn TN=$tn bystanderLost=$bystanderLost accuracy=%.4f\n".format((tp + tn).toDouble() / n))
        sb.append("errors by later holder/form (errors/cases):\n")
        for ((k, v) in byCell) if (v[0] > 0) sb.append("  %-22s %d/%d\n".format(k, v[0], v[1]))
        sb.append("error kinds (count, first example):\n")
        for ((k, v) in errors) sb.append("  %-48s %4d  %s\n".format(k, v.first, v.second))
        java.io.File(args[0]).apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb.lines().take(40).joinToString("\n"))
    }
}
