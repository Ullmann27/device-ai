package at.heinz.deviceai.intelligence.ewm

/**
 * EXP-20 M6-B4 oracle grid (v2): earlier x later statements over holders, predicates, tense and update forms.
 * Each sentence is generated from an explicit frame (holder, predicate, aspect, tense, correction, reported). The gold
 * relation is computed from the frames alone by [relation] - the reference representation - never from Ewm1Memory:
 *   SUPERSEDES_STATE  same holder, same predicate, both present states, and the predicate is single-valued (wohnen)
 *                     or the later statement is an update ("seit", "inzwischen", "mittlerweile", "jetzt",
 *                     "nicht mehr", "nicht ..., sondern ...")
 *   CORRECTS_EVENT    same holder, same past event, same time anchor, later is "nicht ..., sondern ..."
 *   COEXISTS          everything else: other holder or predicate, past events and history, two temporary stays,
 *                     future states, reported or quoted speech (unknown holder: no automatic replacement)
 * Metamorphic checks: equal decisions across the four update paraphrases, with and without bystanders,
 * and with bystanders before or between the two statements.
 *   main <outFile> [decisionsFile: one line per pair "key<TAB>got<TAB>gold" for cross-version edge diffs]
 */
object M6B4SupersessionGrid {
    private class Holder(val name: String, val nominative: String, val person: Int)
    private val holders = listOf(Holder("ich", "ich", 1), Holder("wir", "wir", 4), Holder("schwester", "meine Schwester Anna", 3),
        Holder("mann", "mein Mann Paul", 3), Holder("sohn", "mein Sohn Leo", 3), Holder("max", "Max", 3))
    private val aspect = mapOf("wohnen" to "STATE", "arbeiten" to "STATE", "tennis" to "STATE", "urlaub" to "EVENT", "besuch" to "EVENT")
    private val places = listOf("Wien", "Graz", "Linz")
    private val updates = listOf("seit", "inzwischen", "mittlerweile", "jetzt")
    private val laterForms = listOf("none") + updates + listOf("nichtmehr", "korrektur", "zukunft", "frueher", "zitat", "bericht")

    private data class Frame(val holder: String, val predicate: String, val tense: Char, val form: String) {
        val correction get() = form == "korrektur"
        val reported get() = form == "zitat" || form == "bericht"
    }

    /** The reference relation, from frames only. */
    private fun relation(e: Frame, l: Frame): String = when {
        l.reported || e.holder != l.holder || e.predicate != l.predicate || l.tense == 'f' -> "COEXISTS"
        e.tense == 'p' || l.tense == 'p' ->
            if (aspect[e.predicate] == "EVENT" && e.tense == 'p' && l.tense == 'p' && l.correction) "CORRECTS_EVENT" else "COEXISTS"
        aspect[e.predicate] == "EVENT" -> "COEXISTS"
        e.predicate == "wohnen" || l.form != "none" -> "SUPERSEDES_STATE"
        else -> "COEXISTS"
    }

    private fun verb(rel: String, p: Int, past: Boolean): String {
        val i = when (p) { 1 -> 0; 4 -> 1; else -> 2 }
        return when (rel) {
            "wohnen" -> if (past) listOf("wohnte", "wohnten", "wohnte") else listOf("wohne", "wohnen", "wohnt")
            "arbeiten" -> if (past) listOf("arbeitete", "arbeiteten", "arbeitete") else listOf("arbeite", "arbeiten", "arbeitet")
            "tennis" -> if (past) listOf("spielte", "spielten", "spielte") else listOf("spiele", "spielen", "spielt")
            "urlaub" -> listOf("mache", "machen", "macht")
            else -> listOf("war", "waren", "war")
        }[i]
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    private fun tail(rel: String, place: String) = when (rel) {
        "tennis" -> "in $place Tennis"; "urlaub" -> "gerade Urlaub in $place"; "besuch" -> "gestern in $place"; else -> "in $place" }

    private fun sentence(h: Holder, rel: String, place: String, form: String, earlierPlace: String): String {
        val past = rel == "besuch" || form == "frueher" || form == "pastEarlier"
        val v = verb(rel, h.person, past)
        val t = tail(rel, place)
        return when (form) {
            "none" -> "${cap(h.nominative)} $v $t."
            "pastEarlier" -> "Früher $v ${h.nominative} $t."
            "seit" -> "Seit Dienstag $v ${h.nominative} $t."
            "inzwischen" -> "Inzwischen $v ${h.nominative} $t."
            "mittlerweile" -> "Mittlerweile $v ${h.nominative} $t."
            "jetzt" -> "${cap(h.nominative)} $v jetzt $t."
            "nichtmehr" -> "${cap(h.nominative)} $v nicht mehr $t."
            "korrektur" -> if (rel == "besuch") "${cap(h.nominative)} $v gestern nicht in $earlierPlace, sondern in $place."
                           else "${cap(h.nominative)} $v nicht in $earlierPlace, sondern in $place."
            "zukunft" -> "Ab nächstem Monat $v ${h.nominative} $t."
            "frueher" -> "Früher $v ${h.nominative} $t."
            "zitat" -> "„${cap(h.nominative)} $v jetzt $t“, sagt Bernd."
            else -> "Bernd sagt, ${h.nominative} $v jetzt $t."
        }
    }

    private fun allowed(rel: String, form: String, sameHolder: Boolean): Boolean = when (rel) {
        "besuch" -> form == "none" || form == "korrektur"
        "urlaub" -> form == "none"
        else -> !(form == "zitat" || form == "bericht") || sameHolder
    }

    private class Counts { var tp = 0; var fp = 0; var fn = 0; var tn = 0
        fun add(got: Boolean, want: Boolean) { when { got && want -> tp++; got -> fp++; want -> fn++; else -> tn++ } }
        override fun toString(): String {
            val p = if (tp + fp == 0) "-" else "%.3f".format(tp.toDouble() / (tp + fp))
            val r = if (tp + fn == 0) "-" else "%.3f".format(tp.toDouble() / (tp + fn))
            return "TP=%-5d FP=%-5d FN=%-5d TN=%-6d precision=%s recall=%s".format(tp, fp, fn, tn, p, r)
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val total = Counts(); val byCategory = java.util.TreeMap<String, Counts>()
        val kinds = java.util.TreeMap<String, Pair<Int, String>>()
        var lostBystanders = 0; var insertionChanged = 0; var orderChanged = 0; var paraphraseGroups = 0; var paraphraseInconsistent = 0
        val relCount = java.util.TreeMap<String, Int>()
        val paraphrase = HashMap<String, MutableSet<Boolean>>()
        val dump = args.getOrNull(1)?.let { java.io.File(it).bufferedWriter() }
        val b1 = "Mein Bruder Klaus wohnt in Salzburg."; val b2 = "Ich lese in Salzburg Zeitung."
        for (h1 in holders) for (r1 in aspect.keys) for (t1 in listOf('n', 'p')) {
            if (r1 == "besuch" && t1 == 'n' || r1 == "urlaub" && t1 == 'p') continue
            for (h2 in holders) for (r2 in aspect.keys) for (form in laterForms) for (p1 in places) for (p2 in places) {
                if (!allowed(r2, form, h1 === h2)) continue
                if (form == "nichtmehr" && p2 != p1 || form != "nichtmehr" && p2 == p1) continue
                val e = Frame(h1.name, r1, if (r1 == "besuch") 'p' else t1, "none")
                val l = Frame(h2.name, r2, when (form) { "zukunft" -> 'f'; "frueher" -> 'p'; else -> if (r2 == "besuch") 'p' else 'n' }, form)
                val rel = relation(e, l); relCount.merge(rel, 1, Int::plus)
                val want = rel != "COEXISTS"
                val earlier = sentence(h1, r1, p1, if (t1 == 'p' && r1 != "besuch") "pastEarlier" else "none", p1)
                val later = sentence(h2, r2, p2, form, p1)
                fun decide(facts: List<Pair<String, String>>) = Ewm1Memory(facts).supersedes["l"].orEmpty()
                val withBystanders = decide(listOf("e" to earlier, "b1" to b1, "b2" to b2, "l" to later))
                val got = "e" in withBystanders
                val lost = withBystanders.count { it == "b1" || it == "b2" }
                lostBystanders += lost
                if (("e" in decide(listOf("e" to earlier, "l" to later))) != got) insertionChanged++
                if (("e" in decide(listOf("b1" to b1, "b2" to b2, "e" to earlier, "l" to later))) != got) orderChanged++
                if (form in updates) paraphrase.getOrPut("${h1.name}|$r1|$t1|${h2.name}|$r2|$p1|$p2") { HashSet() } += got
                total.add(got, want)
                dump?.write("${h1.name}/$r1($t1)/$p1 -> ${h2.name}/$r2/$form/$p2\t${if (got) 1 else 0}\t${if (want) 1 else 0}\t$rel\n")
                val transition = "${aspect[r1]}(${e.tense})->${aspect[r2]}(${l.tense})"
                val formClass = if (form in updates) "update" else form
                for (cat in listOf("transition $transition", "holder ${if (h1 === h2) "same" else "different"}",
                    "form $formClass", "relation $rel")) byCategory.getOrPut(cat) { Counts() }.add(got, want)
                if (got != want || lost > 0) {
                    val kind = "${if (got == want) "OK" else if (got) "FP" else "FN"}${if (lost > 0) "+LOST" else ""} ${h1.name}/$r1($t1) -> ${h2.name}/$r2/$form"
                    kinds[kind] = (kinds[kind]?.first ?: 0) + 1 to (kinds[kind]?.second ?: "\"$earlier\" -> \"$later\"")
                }
            }
        }
        dump?.close()
        for (v in paraphrase.values) { paraphraseGroups++; if (v.size > 1) paraphraseInconsistent++ }
        val sb = StringBuilder("EXP-20 M6-B4 oracle grid v2 (gold = reference relation on frames, see source)\n")
        sb.append("cases=${total.tp + total.fp + total.fn + total.tn} $total\n")
        sb.append("gold relations: $relCount\n")
        sb.append("bystanders lost=$lostBystanders  metamorphic: insertion changed=$insertionChanged order changed=$orderChanged " +
            "paraphrase groups inconsistent=$paraphraseInconsistent/$paraphraseGroups\n")
        sb.append("by category:\n"); for ((k, v) in byCategory) sb.append("  %-34s %s\n".format(k, v))
        sb.append("error kinds (count, first example):\n")
        for ((k, v) in kinds) sb.append("  %-52s %5d  %s\n".format(k, v.first, v.second))
        java.io.File(args[0]).apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb.lines().take(4).joinToString("\n") + "\n")
    }
}
