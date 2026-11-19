package at.heinz.deviceai.cognitive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-missions.jsonl` (development set) and `regression-missions-check.jsonl` (held-out check set) of the
 * mission layer: which sentences are a sequence of lane orders, in which order, for which lane and with which
 * arguments – and which sentences must keep today's route (`NONE`). Executed against the productive planner, which
 * itself runs the productive lane parsers. The check set was written and committed before the implementation and is
 * not used for tuning (docs/evaluation/continuum-eval-v1/README.md); its score is pinned here as it is.
 *
 * Two runners. `mission_plan`: the complete plan or `NONE`. `mission_reading` (19.11.2026): the sentence read clause
 * by clause – `PLAN`, `PARTIAL` (several orders, not all of them can run: the honest partial answer) or `NONE`, with
 * one code per order (`clauses`) and what is offered instead (`rest`).
 */
class OwnerMissionRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun list(json: JSONObject, key: String): List<String>? =
        json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) }

    private fun expected(case: JSONObject): String = case.getJSONObject("expect").let { expect ->
        if (case.getString("runner") == "mission_reading") expect.getString("reading") +
            (list(expect, "clauses")?.let { " [" + it.joinToString(" · ") + "]" } ?: "") +
            (list(expect, "rest")?.let { " rest [" + it.joinToString(" → ") + "]" } ?: "")
        else expect.optJSONArray("plan")?.let { plan -> (0 until plan.length()).joinToString(" → ") { plan.getString(it) } } ?: expect.getString("plan")
    }

    private fun planned(input: String): String = OwnerMissionPlanner.plan(input, "Jarvis")?.codes?.joinToString(" → ") ?: "NONE"

    /** The reading in the form of [expected]: only what the case states is compared. */
    private fun read(case: JSONObject): String {
        val expect = case.getJSONObject("expect")
        val reading = OwnerMissionPlanner.read(case.getString("input"), "Jarvis")
        val kind = reading?.kind ?: OwnerSentenceReading.Kind.NONE
        return kind.name + (if (expect.has("clauses")) " [" + reading?.codes.orEmpty().joinToString(" · ") + "]" else "") +
            (if (expect.has("rest")) " rest [" + reading?.rest.orEmpty().joinToString(" → ") { it.code } + "]" else "")
    }

    private fun got(case: JSONObject): String = when (case.getString("runner")) {
        "mission_plan" -> planned(case.getString("input"))
        "mission_reading" -> read(case)
        else -> "unknown runner " + case.getString("runner")
    }

    /** Ids of the cases of [name] whose plan differs from the expected one, with what was planned instead. */
    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            assertTrue(case.getString("id"), case.getString("runner") in setOf("mission_plan", "mission_reading"))
            // A superseded row is not a miss: it is held to what its sentence does now by its own test.
            if (case.getString("id") in SUPERSEDED || case.getString("id") in SUPERSEDED_READINGS) return@forEach
            val got = got(case)
            if (got != expected(case)) misses[case.getString("id")] = "got $got, expected ${expected(case)} · „${case.getString("input")}“"
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealPlanner() {
        val total = cases("regression-missions.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 50)
        val misses = misses("regression-missions.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /**
     * The held-out set is measured, not tuned: [KNOWN_CHECK_MISSES] lists the cases the planner does not get right
     * today, so the score in the README stays true and a new miss – or a silently changed one – fails this test.
     */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases("regression-missions-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 45)
        val misses = misses("regression-missions-check.jsonl")
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    /**
     * The same for a row that pinned „not yet“ for an ability that is a step since the second phase (19.11.2026):
     * the row is unchanged and no longer holds; its sentence is held to exactly the reading listed here.
     */
    @Test fun aSupersededReadingIsNamedWithWhatItsSentenceIsNow() {
        val all = (cases("regression-missions.jsonl") + cases("regression-missions-check.jsonl")).associateBy { it.getString("id") }
        SUPERSEDED_READINGS.forEach { (id, now) ->
            val case = checkNotNull(all[id]) { id }
            assertEquals(id, "mission_reading", case.getString("runner"))
            assertTrue("$id is pinned as something else", expected(case) != now)
            assertEquals(id, now, got(case))
        }
        // Every superseded row pinned an ability that was no step then: reading or sending mails, a message, an app.
        SUPERSEDED_READINGS.keys.forEach { id ->
            val clauses = list(checkNotNull(all[id]).getJSONObject("expect"), "clauses").orEmpty()
            assertTrue(id, clauses.any { it in setOf("NOTYET:MAIL", "LATER:SEND", "LATER:APP") })
        }
    }

    /**
     * A pinned row is never rewritten. Where the owner's later goal changed what a sentence must do, the row stays
     * as it was written, is listed here with the plan the sentence has now, and that plan is held to exactly.
     */
    @Test fun aSupersededRowIsNamedWithWhatItsSentenceDoesNow() {
        val all = (cases("regression-missions.jsonl") + cases("regression-missions-check.jsonl")).associateBy { it.getString("id") }
        SUPERSEDED.forEach { (id, now) ->
            val case = checkNotNull(all[id]) { id }
            assertEquals("$id is pinned as NONE", "NONE", expected(case))
            assertEquals(id, now, planned(case.getString("input")))
        }
    }

    /** No sentence that must keep today's route becomes a plan – in neither set, known misses included. */
    @Test fun noSentenceThatMustKeepItsRouteIsPlanned() {
        listOf("regression-missions.jsonl", "regression-missions-check.jsonl").forEach { name ->
            cases(name).filter { expected(it) == "NONE" && it.getString("id") !in SUPERSEDED }.forEach { case ->
                assertEquals("${case.getString("id")}: „${case.getString("input")}“", "NONE", planned(case.getString("input")))
            }
        }
    }

    /**
     * No sentence that must keep today's route gets the partial answer either: a single order, a sentence one lane
     * takes whole, a negation, a condition, an alternative, talk. (A `mission_plan` row says `NONE` about the plan
     * only – whether its sentence is answered with the honest partial answer say the `mission_reading` rows.)
     */
    @Test fun noSentenceThatMustKeepItsRouteIsReadAsAPartialOrder() {
        var checked = 0
        listOf("regression-missions.jsonl", "regression-missions-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("runner") == "mission_reading" && it.getJSONObject("expect").getString("reading") == "NONE" }.forEach { case ->
                checked++
                val reading = OwnerMissionPlanner.read(case.getString("input"), "Jarvis")
                assertEquals("${case.getString("id")}: „${case.getString("input")}“", OwnerSentenceReading.Kind.NONE,
                    reading?.kind ?: OwnerSentenceReading.Kind.NONE)
            }
        }
        assertTrue("too few: $checked", checked >= 6)
    }

    /** What the partial answer offers is never more than the sentence's own orders, and never an order that hangs on an open one. */
    @Test fun thePartialAnswerOffersOnlyStepsTheSentenceOrdered() {
        var checked = 0
        listOf("regression-missions.jsonl", "regression-missions-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("runner") == "mission_reading" }.forEach { case ->
                val reading = OwnerMissionPlanner.read(case.getString("input"), "Jarvis") ?: return@forEach
                if (reading.kind != OwnerSentenceReading.Kind.PARTIAL) return@forEach
                checked++
                val ordered = reading.clauses.filter { !it.open }.mapNotNull { it.step?.code }
                // „Such … und öffne es“ becomes one step: the offer never has more steps than the sentence has orders.
                assertTrue(case.getString("id"), reading.rest.size <= ordered.size)
                assertTrue(case.getString("id"), reading.open.isNotEmpty())
                assertTrue(case.getString("id"), reading.plan == null)
            }
        }
        assertTrue("too few: $checked", checked >= 6)
    }

    /**
     * No utterance of the other evaluation sets becomes a mission: their present routes are correct and must not
     * move (owner turns of the lanes, the chains, the understanding layer, the voice round and the model sets).
     */
    @Test fun noUtteranceOfTheOtherSetsBecomesAMission() {
        val moved = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String) {
            checked++
            OwnerMissionPlanner.plan(utterance, "Jarvis")?.let { moved += "$source „$utterance“ → ${it.codes}" }
        }
        others { id, utterance -> check(id, utterance) }
        assertTrue("too few utterances checked: $checked", checked >= 500)
        // Since the second phase: „Öffne WhatsApp und schreib …“ of the computer sets is one send step ([NOW_A_SEQUENCE]).
        val now = moved.filter { line -> NOW_A_SEQUENCE.any { line.startsWith("$it ") } }
        assertEquals(NOW_A_SEQUENCE.size, now.size)
        val other = moved - now.toSet()
        assertTrue("${other.size} existing utterances would become a mission:\n" + other.joinToString("\n"), other.isEmpty())
    }

    /**
     * The honest partial answer is given only where every lane has declined. Of all utterances of the other sets it
     * would concern exactly [KNOWN_PARTIAL_UTTERANCES] – each a sentence with several orders of which the app carried
     * out a part or none before. Any further utterance that would be answered this way fails this test.
     */
    @Test fun thePartialAnswerReachesOnlyTheNamedUtterancesOfTheOtherSets() {
        val partial = sortedSetOf<String>()
        val detail = mutableListOf<String>()
        others(partialOnly = true) { id, said ->
            // As the Activity hands a sentence to the lanes: stray keyboard dots are repaired first.
            val utterance = OwnerInputRepair.strayDots(said)
            val reading = OwnerMissionPlanner.read(utterance, "Jarvis") ?: return@others
            if (reading.kind == OwnerSentenceReading.Kind.PARTIAL) { partial += id; detail += "$id „$utterance“ → ${reading.codes} rest ${reading.rest.map { it.code }}" }
        }
        assertEquals("utterances read as a partial order:\n" + detail.joinToString("\n"), KNOWN_PARTIAL_UTTERANCES, partial)
    }

    private fun others(partialOnly: Boolean = false, check: (String, String) -> Unit) {
        // The computer sets (19.11.2026) are sentences with several orders by design; OwnerComputerRegressionTest reads them.
        root.listFiles { file -> file.name.endsWith(".jsonl") && !file.name.startsWith("regression-missions") &&
            !(partialOnly && file.name.startsWith("regression-computer")) }!!.sortedBy { it.name }.forEach { file ->
            cases(file.name).forEach { case ->
                // The talk sets (19.11.2026) state themselves which sentences are a sequence of lane orders: their
                // `owner_order` cases expect a plan and are held to it by OwnerTalkRegressionTest. Every other case
                // of those sets – and every `owner_order` case that expects NONE – is checked here like all the rest.
                if (case.optString("runner") == "owner_order" && case.getJSONObject("expect").opt("plan") is org.json.JSONArray) return@forEach
                val id = file.name + ":" + case.optString("id")
                listOf("input", "question", "utterance", "request").forEach { key ->
                    (case.opt(key) as? String)?.takeIf { it.isNotEmpty() }?.let { check(id, it) }
                }
                case.optJSONArray("turns")?.let { turns ->
                    (0 until turns.length()).forEach { index ->
                        when (val turn = turns.get(index)) {
                            is String -> check(id, turn)
                            is JSONObject -> if (turn.optString("role", "user") == "user") turn.optString("text").takeIf { it.isNotEmpty() }?.let { check(id, it) }
                        }
                    }
                }
            }
        }
    }

    /** Separate development and check data: no sentence of one set appears in the other. */
    @Test fun theDevelopmentAndTheCheckSetShareNoSentence() {
        fun inputs(name: String) = cases(name).map { it.getString("input").trim().lowercase() }
        val development = inputs("regression-missions.jsonl")
        val check = inputs("regression-missions-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val ids = (cases("regression-missions.jsonl") + cases("regression-missions-check.jsonl")).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    private companion object {
        val KNOWN_CHECK_MISSES: Set<String> = emptySet()

        /**
         * Held-out rows of 18.11.2026 whose sentence does something else since the owner's goal of 19.11.2026 („Such
         * die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen“ must run as one order and
         * show the summary without saving a file). The rows are unchanged; they no longer hold, and this map says why.
         *
         * MISC-038 „Öffne das PDF Argon4 und fass Seite 2 zusammen“ was pinned as NONE with the note „Zusammenfassen
         * ohne Speichern bleibt die Antwort im Gespräch“. It still is an answer in the chat and still saves nothing –
         * but it is now carried out as a sequence (open, then summarise page 2), because the same order with „such“
         * and „öffne sie“ in front is the owner's sentence and cannot mean something else in its short form.
         */
        val SUPERSEDED: Map<String, String> = mapOf("MISC-038" to "OPEN:Argon4 → PAGESUM:2",
            // Second phase (19.11.2026). MISC-037: a page summary that is shown and not saved is a typed result now, so
            // „… und schick es per Mail an …“ has what it sends. MIS-037: a message on WhatsApp is a step.
            "MISC-037" to "PAGESUM:2 → MAIL:anna@example.org",
            "MIS-037" to "OPEN:Argon4 → SEND:WHATSAPP:Anna")

        /**
         * `mission_reading` rows of 19.11.2026 that pinned the honest partial answer for an ability that was no step
         * then. With the second phase – a message on WhatsApp (`SEND`), opening an app (`APP`), searching, reading and
         * summarising mails through the Gmail lane (`MAILFIND`, `MAILREAD`, `MAILSUM`) – their sentences are complete
         * sequences, or offer more. The rows are unchanged. Whether Gmail is connected is not the planner's business:
         * without a connection the director answers with the partial answer that names it (OwnerMissionPhase2FlowTest).
         */
        val SUPERSEDED_READINGS: Map<String, String> = mapOf(
            "MIS-071" to "PLAN [MAILFIND:INBOX · SUM_IT · EXPORT:PDF · SEND:WHATSAPP:meinem Vater] rest []",
            "MIS-072" to "PLAN [SEND:WHATSAPP:meinem Vater] rest []",
            "MIS-073" to "PLAN [MAILFIND:LATEST:Anwalt · SUM_IT · EXPORT:PDF · OPEN_IT] rest []",
            "MIS-076" to "PLAN [NEWS · EXPORT:PDF · SEND:WHATSAPP:Anna] rest []",
            "MIS-078" to "PLAN [EXPORT:PDF · SEND:WHATSAPP:meinem Chef] rest []",
            "MIS-079" to "PLAN [OPEN:Argon4 · SEND:WHATSAPP:Anna] rest []",
            "MISC-059" to "PLAN [MAILFIND:NEW · SUM_IT · EXPORT:PDF] rest []",
            "MISC-060" to "PARTIAL [APP:FACEBOOK · LATER:SEND] rest [APP:FACEBOOK]",
            "MISC-061" to "PLAN [NEWS · SEND:WHATSAPP:Anna] rest []",
            "MISC-062" to "PLAN [OPEN:Argon4 · PAGE:2:DOCX · SEND:WHATSAPP:Anna] rest []",
            "MISC-063" to "PLAN [STORAGE:ANALYSE · MAILFIND:INBOX] rest []",
            "MISC-064" to "PLAN [MAILFIND:INBOX · MAILFIND:FROM:Bank · OPEN:Kontoauszug] rest []")

        /** `file:id` of utterances of the other sets that are a sequence now: one send step for „Öffne WhatsApp und schreib …“. */
        val NOW_A_SEQUENCE: Set<String> = sortedSetOf("regression-computer-check.jsonl:COMC-005", "regression-computer.jsonl:COM-001")

        /**
         * `file:id` of utterances of the other sets the honest partial answer would be given for. Each is a sentence
         * with several orders of which the app carried out none or silently only a part before (they went to the model,
         * which has no tool for the second half, or – with the cloud brain – the first half ran and the rest was
         * dropped). Their own sets pin something else about them (no plan, no page order, no export, no computer
         * order), and those tests are unchanged.
         *  - an order no lane knows behind one a lane knows: printing, deleting, moving an appointment, naming a figure
         *    from a file (HO-MULTI-01, BRC-111, LIBC-021, LIB-108, TLKC-ORD-004, TLK-ORD-005, REG-TOOL-004);
         *  - „schick es an Anna“ behind a save or a summary – sending is no step of a sequence yet (BRF-113, LIBC-048,
         *    LIB-107, NEWS-EXP-011, TLK-MAK-016);
         *  - a storage order that needs the owner's choice, followed by a second one (CHN-030);
         *  - the three understanding rows of this work, whose heard form („Watsapp“, „Krom“) is repaired before any
         *    lane sees the sentence (UND-075…077): they are sentences with a second order by design.
         */
        val KNOWN_PARTIAL_UTTERANCES: Set<String> = sortedSetOf("model-holdout.jsonl:HO-MULTI-01",
            "regression-briefing-check.jsonl:BRC-111", "regression-briefing.jsonl:BRF-113", "regression-chains.jsonl:CHN-030",
            "regression-library-check.jsonl:LIBC-021", "regression-library-check.jsonl:LIBC-048", "regression-library.jsonl:LIB-107",
            "regression-library.jsonl:LIB-108", "regression-news.jsonl:NEWS-EXP-011", "regression-talk-check.jsonl:TLKC-ORD-004",
            "regression-talk.jsonl:TLK-MAK-016", "regression-talk.jsonl:TLK-ORD-005", "regression-understanding.jsonl:UND-075",
            "regression-understanding.jsonl:UND-076", "regression-understanding.jsonl:UND-077", "regression.jsonl:REG-TOOL-004")
    }
}
