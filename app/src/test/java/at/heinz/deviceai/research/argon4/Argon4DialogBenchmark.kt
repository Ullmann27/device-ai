package at.heinz.deviceai.research.argon4

import at.heinz.deviceai.cognitive.LocalConversationContext
import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import org.json.JSONObject

/**
 * ARGON4 F1 benchmark: conversation continuity with a control group (synthetic German dialogs, generated
 * deterministically from seed; one author; no owner data). Same dialogs for every system:
 *
 *  CTRL-A  the real LocalConversationContext (4 turn pairs, 300/350 chars): is the gold referent still in the
 *          model context at probe time? (necessary, not sufficient, for the model to resolve it)
 *  CTRL-B  a naive recency resolver: the last capitalised non-initial word of earlier turns
 *  KERNEL  DialogStateKernel: CORRECT / WRONG / CLARIFY against the gold label
 *
 * Gold "CLARIFY" means a careful human would ask back. Categories and N are fixed before the first run.
 */
object Argon4DialogBenchmark {
    private val FEMALE = listOf("Anna", "Rita", "Lea", "Eva", "Nora", "Mira", "Sara", "Lena", "Jana", "Ida", "Vera", "Ute")
    private val MALE = listOf("Max", "Paul", "Jonas", "Felix", "Leon", "Tim", "Ben", "Noah", "Emil", "Jan", "Kurt", "Otto")
    private val ROLE_F = listOf("Schwester", "Tochter", "Nachbarin", "Kollegin", "Tante", "Cousine")
    private val ROLE_M = listOf("Bruder", "Sohn", "Nachbar", "Kollege", "Onkel", "Cousin")
    private val PLACES = listOf("Linz", "Graz", "Wels", "Steyr", "Krems", "Villach", "Bregenz", "Gmunden", "Melk", "Tulln", "Enns", "Hallein")
    private val FILLERS = listOf("Wie spät ist es?", "Erzähl mir einen kurzen Witz.", "Was kann ich heute kochen?",
        "Wie lange kocht man Nudeln?", "Was ist ein gutes Frühstück?", "Gib mir einen Tipp gegen Müdigkeit.")
    val CATEGORIES = listOf("DIRECT", "LONG_RANGE", "GENDER_COMPETITOR", "SAME_SENTENCE_AMBIGUOUS", "CORRECTION", "PLACE",
        "TOPIC_SWITCH", "NO_GENDER_CUE", "TEMPORAL")
    const val PER_CATEGORY = 12

    data class Turn(val user: String, val answer: String)
    data class Probe(val category: String, val turns: List<Turn>, val probe: String, val gold: String)

    /** Holdout vocabulary, written before the name/noun repair of run 1 and sealed by hash in the same commit. */
    private val H_FEMALE = listOf("Greta", "Hanna", "Klara", "Luise", "Marie", "Pia", "Romy", "Sophie", "Tina", "Wilma", "Zoe", "Berta")
    private val H_MALE = listOf("Anton", "Bruno", "Carl", "David", "Erik", "Fritz", "Georg", "Hugo", "Igor", "Karl", "Lukas", "Moritz")
    private val H_ROLE_F = listOf("Enkelin", "Chefin", "Freundin", "Ärztin", "Lehrerin", "Partnerin")
    private val H_ROLE_M = listOf("Enkel", "Chef", "Freund", "Arzt", "Lehrer", "Partner")
    private val H_PLACES = listOf("Salzburg", "Innsbruck", "Klagenfurt", "Eisenstadt", "Dornbirn", "Leoben", "Baden", "Mödling",
        "Amstetten", "Braunau", "Zwettl", "Lienz")
    private val H_FILLERS = listOf("Hol mir bitte den Kalender.", "Wie funktioniert eine Wärmepumpe?", "Mach eine Liste für den Einkauf.",
        "Was bedeutet Photosynthese?", "Spiel ruhige Musik.", "Gibt es heute Regen?")

    fun dialogs(seed: Long = 20261029L, today: LocalDate = LocalDate.of(2026, 10, 29), holdout: Boolean = false): List<Probe> {
        val FEMALE = if (holdout) H_FEMALE else FEMALE; val MALE = if (holdout) H_MALE else MALE
        val ROLE_F = if (holdout) H_ROLE_F else ROLE_F; val ROLE_M = if (holdout) H_ROLE_M else ROLE_M
        val PLACES = if (holdout) H_PLACES else PLACES; val FILLERS = if (holdout) H_FILLERS else FILLERS
        val rnd = Random(seed)
        val out = ArrayList<Probe>()
        fun echo(text: String, name: String) = if (rnd.nextBoolean()) "Verstanden, $name." else "Verstanden."
        for (i in 0 until PER_CATEGORY) {
            val f = FEMALE[i]; val f2 = FEMALE[(i + 5) % FEMALE.size]; val m = MALE[i]
            val rf = ROLE_F[i % ROLE_F.size]; val rf2 = ROLE_F[(i + 2) % ROLE_F.size]; val rm = ROLE_M[i % ROLE_M.size]
            val p = PLACES[i]; val p2 = PLACES[(i + 3) % PLACES.size]
            val introF = "Meine $rf $f wohnt in $p."
            out += Probe("DIRECT", listOf(Turn(introF, echo(introF, f))), "Wo arbeitet sie?", f.lowercase())
            out += Probe("LONG_RANGE", listOf(Turn(introF, echo(introF, f))) + (0 until 5).map { Turn(FILLERS[(i + it) % FILLERS.size], "Hier ist meine Antwort.") },
                "Und was macht sie beruflich?", f.lowercase())
            out += Probe("GENDER_COMPETITOR", listOf(Turn(introF, echo(introF, f)), Turn("Mein $rm $m fährt nach $p2.", echo("", m))),
                "Wann besucht sie mich?", f.lowercase())
            out += Probe("SAME_SENTENCE_AMBIGUOUS", listOf(Turn("Meine $rf $f und meine $rf2 $f2 waren gestern da.", "Schön.")),
                "Was hat sie erzählt?", "CLARIFY")
            out += Probe("CORRECTION", listOf(Turn(introF, echo(introF, f)), Turn("Meine $rf2 $f2 wohnt in $p2.", echo("", f2)),
                Turn("Wo arbeitet sie?", "Das weiß ich nicht.")), "Nein, ich meinte $f.", f.lowercase())
            out += Probe("PLACE", listOf(Turn("Ich war am Wochenende in $p.", "Wie schön.")), "Wie war das Essen dort?", p.lowercase())
            out += Probe("TOPIC_SWITCH", listOf(Turn(introF, echo(introF, f)), Turn("Anderes Thema.", "Gerne.")), "Wo wohnt sie?", "CLARIFY")
            out += Probe("NO_GENDER_CUE", listOf(Turn("$f und $m kamen gestern zu Besuch.", "Schön.")), "Was hat er erzählt?", m.lowercase())
            val offset = (i % 4) + 1
            val expr = listOf("gestern", "vorgestern", "vor drei Tagen", "vor vier Tagen")[offset - 1]
            out += Probe("TEMPORAL", emptyList(), "Was habe ich $expr gemacht?", today.minusDays(offset.toLong()).toString())
        }
        return out
    }

    data class Tally(val correct: Int = 0, val wrong: Int = 0, val clarify: Int = 0, val notApplicable: Int = 0) {
        fun add(o: String) = when (o) { "CORRECT" -> copy(correct = correct + 1); "WRONG" -> copy(wrong = wrong + 1)
            "CLARIFY" -> copy(clarify = clarify + 1); else -> copy(notApplicable = notApplicable + 1) }
        fun json() = JSONObject().put("correct", correct).put("wrong", wrong).put("clarify", clarify).put("notApplicable", notApplicable)
    }

    fun run(today: LocalDate = LocalDate.of(2026, 10, 29), holdout: Boolean = false): JSONObject {
        val kernel = HashMap<String, Tally>(); val ctrlA = HashMap<String, Tally>(); val ctrlB = HashMap<String, Tally>()
        var nanos = 0L
        for ((n, d) in dialogs(today = today, holdout = holdout).withIndex()) {
            // KERNEL
            val k = DialogStateKernel("bench-$n", { today })
            val start = System.nanoTime()
            d.turns.forEach { k.observeUser(it.user); k.observeAnswer(it.answer) }
            val r = k.observeUser(d.probe)
            nanos += System.nanoTime() - start
            val kOut = when {
                d.category == "TEMPORAL" -> if (r.temporal?.from?.toString() == d.gold) "CORRECT" else "WRONG"
                d.gold == "CLARIFY" -> if (r.decision == DialogStateKernel.Decision.CLARIFY) "CORRECT" else "WRONG"
                r.decision == DialogStateKernel.Decision.CLARIFY -> "CLARIFY"
                r.bindings.lastOrNull()?.entity == d.gold -> "CORRECT"
                else -> "WRONG"
            }
            kernel.merge(d.category, Tally().add(kOut)) { a, _ -> a.add(kOut) }
            // CTRL-A: what the existing model context still contains
            val ctx = LocalConversationContext()
            d.turns.forEach { ctx.record("bench-$n", it.user, it.answer) }
            val rendered = ctx.render("bench-$n")
            val aOut = when {
                d.gold == "CLARIFY" -> "NA"
                d.category == "TEMPORAL" -> if (rendered.contains(d.gold)) "CORRECT" else "WRONG"
                rendered.lowercase().contains(d.gold) -> "CORRECT"
                else -> "WRONG"
            }
            ctrlA.merge(d.category, Tally().add(aOut)) { a, _ -> a.add(aOut) }
            // CTRL-B: naive recency
            val words = d.turns.flatMap { DialogStateKernel.words(it.user).drop(1) }.filter { it.first().isUpperCase() &&
                it.lowercase() !in setOf("meine", "mein", "anderes", "thema") }
            val naive = if (d.category == "CORRECTION") DialogStateKernel.words(d.probe).lastOrNull()?.lowercase() else words.lastOrNull()?.lowercase()
            val bOut = when {
                d.category == "TEMPORAL" -> "WRONG"
                d.gold == "CLARIFY" -> "WRONG"
                naive == d.gold -> "CORRECT"
                else -> "WRONG"
            }
            ctrlB.merge(d.category, Tally().add(bOut)) { a, _ -> a.add(bOut) }
        }
        fun table(t: Map<String, Tally>) = JSONObject().apply { CATEGORIES.forEach { c -> put(c, (t[c] ?: Tally()).json()) } }
        fun total(t: Map<String, Tally>) = t.values.fold(Tally()) { a, b -> Tally(a.correct + b.correct, a.wrong + b.wrong, a.clarify + b.clarify, a.notApplicable + b.notApplicable) }.json()
        return JSONObject().put("benchmark", "ARGON4-F1-dialog").put("split", if (holdout) "HOLDOUT" else "DEV").put("seed", 20261029L).put("probes", CATEGORIES.size * PER_CATEGORY)
            .put("kernel", table(kernel)).put("kernelTotal", total(kernel))
            .put("ctrlA_localConversationContext", table(ctrlA)).put("ctrlA_total", total(ctrlA))
            .put("ctrlB_naiveRecency", table(ctrlB)).put("ctrlB_total", total(ctrlB))
            .put("kernelMicrosPerDialog", nanos / 1000.0 / (CATEGORIES.size * PER_CATEGORY))
    }

    /** Canonical text of every holdout probe; its SHA-256 is recorded before the holdout is run (once). */
    fun holdoutText(): String = dialogs(holdout = true).joinToString("\n") { d ->
        d.category + "|" + d.turns.joinToString("/") { it.user + "=>" + it.answer } + "|" + d.probe + "|" + d.gold }

    @JvmStatic fun main(args: Array<String>) {
        val holdout = args.getOrNull(1) == "HOLDOUT"
        if (args.getOrNull(1) == "HOLDOUT_SHA") {
            println(java.security.MessageDigest.getInstance("SHA-256").digest(holdoutText().toByteArray()).joinToString("") { "%02x".format(it) })
            return
        }
        val result = run(holdout = holdout)
        if (args.isNotEmpty()) File(args[0]).writeText(result.toString(2) + "\n")
        println(result.toString())
    }
}
