package at.heinz.deviceai.intelligence.ewm

/**
 * Gold-free metamorphic stress test for the original production Ewm1Memory implementation.
 * Inserting an unrelated fact must not change the link between the original two facts,
 * and the unrelated fact must never be superseded or supersede another fact.
 *
 * This is research validation only. The two target facts' original label is NOT provided.
 * No learning, persistence, APK code or productive state is modified.
 */
object M6MetamorphicInvariantProbe {
    private data class Seed(val earlier: String, val later: String)

    private val seeds = listOf(
        Seed("Mira wohnt in Wien.", "Mira wohnt jetzt in Graz."),
        Seed("Anna arbeitet in Linz.", "Anna arbeitet inzwischen in Wien."),
        Seed("Die Monatskarte kostet 18 Euro.", "Die Monatskarte kostet jetzt 24 Euro."),
        Seed("Die Musikprobe beginnt am Freitag.", "Korrektur: Die Musikprobe beginnt am Samstag."),
        Seed("Mira wohnt in Graz.", "Mira hat gestern Wien besucht."),
        Seed("Noah wohnt in Wien.", "Seine Schwester Anna wohnt jetzt in Linz.")
    )
    private val controls = listOf(
        "Die Lampe im Keller ist gelb.",
        "Ein Fahrrad hat drei Gänge.",
        "Der Ozean ist tief.",
        "Das Museum öffnet am Sonntag.",
        "Der Apfel schmeckt süß.",
        "Die Schraube liegt in der Werkzeugkiste.",
        "Eine Brücke führt über den Fluss.",
        "Das Wetter bleibt mild."
    )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "M6_METAMORPHIC_NO_ARGS" }
        var total = 0
        var failed = 0
        val diagnostic = ArrayList<String>()

        seeds.forEachIndexed { seedIndex, seed ->
            for (renamed in listOf(false, true)) {
                val earlierId = if (renamed) "old" else "before"
                val laterId = if (renamed) "new" else "after"
                val unrelatedId = if (renamed) "control" else "sentinel"
                val original = Ewm1Memory(listOf(earlierId to seed.earlier, laterId to seed.later))
                val originalEdge = earlierId in original.supersedes[laterId].orEmpty()
                controls.forEachIndexed { controlIndex, control ->
                    for (position in 0..2) {
                        val facts = ArrayList<Pair<String, String>>()
                        val first = earlierId to seed.earlier
                        val next = laterId to seed.later
                        val extra = unrelatedId to control
                        when (position) {
                            0 -> facts.addAll(listOf(extra, first, next))
                            1 -> facts.addAll(listOf(first, extra, next))
                            else -> facts.addAll(listOf(first, next, extra))
                        }
                        val observed = Ewm1Memory(facts)
                        val sameEdge = originalEdge == (earlierId in observed.supersedes[laterId].orEmpty())
                        val untouched = unrelatedId !in observed.supersededBy &&
                            unrelatedId !in observed.supersedes.keys &&
                            observed.supersedes.values.none { unrelatedId in it } &&
                            unrelatedId in observed.byId
                        total++
                        if (!sameEdge || !untouched) {
                            failed++
                            if (diagnostic.size < 20) {
                                diagnostic += "seed=$seedIndex,control=$controlIndex,position=$position," +
                                    "renamed=$renamed,sameEdge=$sameEdge,untouched=$untouched"
                            }
                        }
                    }
                }
            }
        }
        println("M6_METAMORPHIC_CASES=$total FAILED=$failed")
        diagnostic.forEach { println("M6_METAMORPHIC_VIOLATION:$it") }
        check(total == 288) { "M6_METAMORPHIC_CASE_COUNT" }
        check(failed == 0) { "M6_METAMORPHIC_INVARIANT_FAILURES=$failed" }
        println("M6_METAMORPHIC_ALL_INVARIANTS_GREEN")
    }
}
