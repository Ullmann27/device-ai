package at.heinz.deviceai.research.m11

import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.MemoryQuestionResolver
import at.heinz.deviceai.cognitive.MemoryTimeline
import at.heinz.deviceai.intelligence.ewm.Ewm1Brain
import at.heinz.deviceai.intelligence.ewm.Ewm1SourceIndex
import at.heinz.deviceai.research.argon4.DialogStateKernel
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONObject

/**
 * M11 · same tasks, same budget, production path vs research components (no model, no owner data).
 * Part A (long dialogues): CTRL = production LocalConversationContext (is the referent still in the model context?)
 *   vs KERNEL = ARGON F1 DialogStateKernel (bound referent / ask back).
 * Part B (memory): CTRL = lexical top-1 retrieval over active facts (the retriever's lexical share, which is all
 *   the production path scores today) vs EWM2 = Ewm1Brain.ask (cited facts, outdated facts, abstention).
 * DEV and HOLDOUT use different names, places and fillers; HOLDOUT is sealed by hash before its single run.
 */
object M11ConversationMemoryBench {
    data class Dialog(val id: String, val category: String, val turns: List<String>, val probe: String, val gold: String)
    data class MemoryCase(val id: String, val category: String, val facts: List<Pair<String, String>>, val question: String, val gold: String)

    private data class Voc(val f: List<String>, val m: List<String>, val places: List<String>, val fillers: List<String>, val roleF: List<String>, val roleM: List<String>)
    private val DEV = Voc(listOf("Anna", "Rita", "Lea", "Eva", "Nora", "Mira"), listOf("Max", "Paul", "Jonas", "Felix", "Leon", "Emil"),
        listOf("Linz", "Graz", "Wels", "Steyr", "Krems", "Villach"),
        listOf("Wie spät ist es?", "Erzähl mir einen Witz.", "Was kann ich kochen?", "Wie lange kocht Reis?", "Gib mir einen Tipp.", "Wie wird das Wetter?"),
        listOf("Schwester", "Nachbarin", "Kollegin"), listOf("Bruder", "Nachbar", "Kollege"))
    private val HOLDOUT = Voc(listOf("Greta", "Hanna", "Klara", "Luise", "Pia", "Romy"), listOf("Anton", "Bruno", "Carl", "Erik", "Fritz", "Hugo"),
        listOf("Salzburg", "Innsbruck", "Leoben", "Baden", "Zwettl", "Lienz"),
        listOf("Mach eine Einkaufsliste.", "Was ist Photosynthese?", "Spiel ruhige Musik.", "Wie funktioniert ein Akku?", "Nenn mir ein Synonym für schnell.", "Wann ist Vollmond?"),
        listOf("Tochter", "Freundin", "Chefin"), listOf("Sohn", "Freund", "Chef"))
    val DIALOG_CATEGORIES = listOf("MULTI_PERSON_LONG", "INTERRUPTION_RETURN", "LATE_CORRECTION", "FALSE_REFERENCE", "TEMPORAL_LONG")
    val MEMORY_CATEGORIES = listOf("OUTDATED", "CORRECTED", "CONTRADICTORY", "TWO_STEP", "DISTRACTOR", "UNANSWERABLE")

    fun dialogs(split: String): List<Dialog> {
        val v = if (split == "holdout") HOLDOUT else DEV
        return (0 until 6).flatMap { i ->
            val f = v.f[i]; val f2 = v.f[(i + 2) % 6]; val m = v.m[i]; val p = v.places[i]; val p2 = v.places[(i + 3) % 6]
            val rf = v.roleF[i % 3]; val rm = v.roleM[i % 3]
            val fill = { n: Int -> (0 until n).map { v.fillers[(i + it) % v.fillers.size] } }
            listOf(
                Dialog("$split-mp-$i", "MULTI_PERSON_LONG", listOf("Meine $rf $f wohnt in $p.", "Mein $rm $m arbeitet in $p2.",
                    "Und $f2 kommt am Wochenende zu Besuch.") + fill(8), "Was hat er beruflich vor?", m.lowercase()),
                Dialog("$split-ir-$i", "INTERRUPTION_RETURN", listOf("Mein $rm $m hat heute angerufen.") + fill(12), "Was wollte er eigentlich?", m.lowercase()),
                Dialog("$split-lc-$i", "LATE_CORRECTION", listOf("Mein $rm ${v.m[(i + 1) % 6]} hat ein neues Auto.") + fill(3) +
                    listOf("Wie teuer war es ungefähr?", "Nein, ich meinte meinen $rm $m."), "Wie alt ist er?", m.lowercase()),
                Dialog("$split-fr-$i", "FALSE_REFERENCE", listOf("Meine $rf $f wohnt in $p.", "Und $f2 besucht sie oft.") + fill(4),
                    "Was macht er dort?", "CLARIFY"),
                Dialog("$split-tl-$i", "TEMPORAL_LONG", listOf("Gestern war ich mit $f in $p.") + fill(6), "Was haben wir an dem Tag gegessen?", "YESTERDAY"))
        }
    }

    fun memory(split: String): List<MemoryCase> {
        val v = if (split == "holdout") HOLDOUT else DEV
        return (0 until 6).flatMap { i ->
            val f = v.f[i]; val m = v.m[i]; val p = v.places[i]; val p2 = v.places[(i + 3) % 6]; val other = v.m[(i + 1) % 6]
            listOf(
                MemoryCase("$split-out-$i", "OUTDATED", listOf("a" to "$f wohnte bis 2023 in $p2.", "b" to "$f wohnt seit 2024 in $p."), "Wo wohnt $f?", "b"),
                MemoryCase("$split-cor-$i", "CORRECTED", listOf("a" to "Der Geburtstag von $m ist am 3. Mai.",
                    "b" to "Korrektur: Der Geburtstag von $m ist nicht am 3. Mai, sondern am 5. Mai."), "Wann hat $m Geburtstag?", "b"),
                MemoryCase("$split-con-$i", "CONTRADICTORY", listOf("a" to "$m fährt einen roten Kombi.", "b" to "$m fährt einen blauen Kombi."),
                    "Welche Farbe hat der Kombi von $m?", "CLARIFY_OR_BOTH"),
                MemoryCase("$split-two-$i", "TWO_STEP", listOf("a" to "$f ist die Schwester von $m.", "b" to "$m wohnt in $p.",
                    "c" to "$other wohnt in $p2."), "Wo wohnt der Bruder von $f?", "b"),
                MemoryCase("$split-dis-$i", "DISTRACTOR", listOf("a" to "Das Auto von $f steht in der Garage in $p.",
                    "b" to "$f arbeitet als Ärztin in $p2.", "c" to "Die Garage von $m ist blau."), "Als was arbeitet $f?", "b"),
                MemoryCase("$split-una-$i", "UNANSWERABLE", listOf("a" to "$f wohnt in $p.", "b" to "$m arbeitet in $p2."),
                    "Wo wohnt ${v.f[(i + 3) % 6]}?", "ABSTAIN"))
        }
    }

    private val WORD = Regex("\\p{L}+")
    private val STOP = setOf("der", "die", "das", "von", "in", "ist", "am", "wo", "wann", "was", "wie", "als", "hat", "welche", "einen", "den", "dem", "mit")
    /** Production-like lexical retrieval: top-1 by shared content words (ties: first fact). */
    fun lexicalTop(facts: List<Pair<String, String>>, q: String): String? {
        val qw = WORD.findAll(q.lowercase()).map { it.value }.filter { it !in STOP }.toSet()
        val scored = facts.map { (id, t) -> id to WORD.findAll(t.lowercase()).map { it.value }.count { it in qw } }
        return scored.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    fun seal(split: String): String = MessageDigest.getInstance("SHA-256").digest(
        (dialogs(split).joinToString("\n") { it.toString() } + memory(split).joinToString("\n") { it.toString() }).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun evaluate(split: String): JSONObject {
        val today = LocalDate.of(2026, 10, 30)
        val a = JSONObject(); val b = JSONObject()
        val tally = { -> mutableMapOf("CORRECT" to 0, "WRONG" to 0, "CLARIFY" to 0, "NA" to 0) }
        val kernelT = DIALOG_CATEGORIES.associateWith { tally() }; val ctrlT = DIALOG_CATEGORIES.associateWith { tally() }
        for ((n, d) in dialogs(split).withIndex()) {
            val k = DialogStateKernel("m11-$n", { today })
            d.turns.forEach { k.observeUser(it); k.observeAnswer("Verstanden.") }
            val r = k.observeUser(d.probe)
            val kOut = when (d.gold) {
                "CLARIFY" -> if (r.decision == DialogStateKernel.Decision.CLARIFY) "CORRECT" else "WRONG"
                "YESTERDAY" -> if (r.temporal?.from == today.minusDays(1)) "CORRECT" else "WRONG"
                else -> when { r.decision == DialogStateKernel.Decision.CLARIFY -> "CLARIFY"; r.bindings.lastOrNull()?.entity == d.gold -> "CORRECT"; else -> "WRONG" }
            }
            kernelT.getValue(d.category).merge(kOut, 1, Int::plus)
            val ctx = LocalConversationContext()
            d.turns.forEach { ctx.record("m11-$n", it, "Verstanden.") }
            val rendered = ctx.render("m11-$n").lowercase()
            val cOut = when (d.gold) { "CLARIFY" -> "NA"; "YESTERDAY" -> if (rendered.contains(today.minusDays(1).toString())) "CORRECT" else "WRONG"
                else -> if (rendered.contains(d.gold)) "CORRECT" else "WRONG" }
            ctrlT.getValue(d.category).merge(cOut, 1, Int::plus)
        }
        DIALOG_CATEGORIES.forEach { c -> a.put(c, JSONObject().put("kernel", JSONObject(kernelT.getValue(c) as Map<*, *>)).put("ctrl", JSONObject(ctrlT.getValue(c) as Map<*, *>))) }
        val lexT = MEMORY_CATEGORIES.associateWith { tally() }; val ewmT = MEMORY_CATEGORIES.associateWith { tally() }
        val plusT = MEMORY_CATEGORIES.associateWith { tally() }
        for (c in memory(split)) {
            val lex = lexicalTop(c.facts, c.question)
            val lexOut = when (c.gold) { "ABSTAIN" -> if (lex == null) "CORRECT" else "WRONG"; "CLARIFY_OR_BOTH" -> "WRONG"; else -> if (lex == c.gold) "CORRECT" else "WRONG" }
            lexT.getValue(c.category).merge(lexOut, 1, Int::plus)
            val brain = Ewm1Brain(c.facts, Ewm1SourceIndex(emptyList())) { _, s -> s.toList() }
            val ans = brain.ask(c.question)
            val cited = ans.cited
            val ewmOut = when (c.gold) {
                "ABSTAIN" -> if (ans.decision != "QUERY" || cited.isEmpty()) "CORRECT" else "WRONG"
                "CLARIFY_OR_BOTH" -> if (ans.decision == "CLARIFY" || cited.containsAll(listOf("a", "b"))) "CORRECT" else if (ans.decision != "QUERY") "CLARIFY" else "WRONG"
                else -> when { ans.decision != "QUERY" -> "CLARIFY"; cited.firstOrNull() == c.gold -> "CORRECT"; else -> "WRONG" }
            }
            ewmT.getValue(c.category).merge(ewmOut, 1, Int::plus)
            // EWM2+ : relation resolver before, timeline check after the frozen brain (research arm)
            val q2 = MemoryQuestionResolver.resolve(c.question, c.facts) ?: c.question
            val ans2 = Ewm1Brain(c.facts, Ewm1SourceIndex(emptyList())) { _, s -> s.toList() }.ask(q2)
            val v = if (ans2.decision == "QUERY") MemoryTimeline.check(ans2.cited, c.facts) else MemoryTimeline.Verdict("UNCHANGED", ans2.cited, "NO_QUERY")
            val decision2 = if (v.decision == "CLARIFY") "CLARIFY" else ans2.decision
            val cited2 = v.factIds
            val plusOut = when (c.gold) {
                "ABSTAIN" -> if (decision2 != "QUERY" || cited2.isEmpty()) "CORRECT" else "WRONG"
                "CLARIFY_OR_BOTH" -> if (decision2 == "CLARIFY") "CORRECT" else "WRONG"
                else -> when { decision2 != "QUERY" -> "CLARIFY"; cited2.firstOrNull() == c.gold -> "CORRECT"; else -> "WRONG" }
            }
            plusT.getValue(c.category).merge(plusOut, 1, Int::plus)
        }
        MEMORY_CATEGORIES.forEach { c -> b.put(c, JSONObject().put("ewm2", JSONObject(ewmT.getValue(c) as Map<*, *>)).put("lexical", JSONObject(lexT.getValue(c) as Map<*, *>))
            .put("ewm2Plus", JSONObject(plusT.getValue(c) as Map<*, *>))) }
        fun sum(t: Map<String, MutableMap<String, Int>>) = JSONObject(listOf("CORRECT", "WRONG", "CLARIFY", "NA").associateWith { k -> t.values.sumOf { it[k] ?: 0 } } as Map<*, *>)
        return JSONObject().put("split", split).put("seal", seal(split)).put("dialogs", a).put("memory", b)
            .put("totals", JSONObject().put("kernel", sum(kernelT)).put("ctrl", sum(ctrlT)).put("ewm2", sum(ewmT)).put("lexical", sum(lexT)).put("ewm2Plus", sum(plusT)))
    }

    @JvmStatic fun main(args: Array<String>) {
        val r = evaluate(args.firstOrNull() ?: "dev")
        println(r.toString())
        args.getOrNull(1)?.let { File(it).writeText(r.toString(1) + "\n") }
    }
}
