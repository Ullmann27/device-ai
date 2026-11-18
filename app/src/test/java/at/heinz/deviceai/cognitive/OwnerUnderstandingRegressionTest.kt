package at.heinz.deviceai.cognitive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-understanding.jsonl`: misheard names, Austrian phrasings, near-miss pairs, valid words and dictated
 * text, executed against the productive understanding layer and the productive routing parsers.
 *
 * A case counts as handled when the utterance that reaches routing is the expected one AND it takes the expected
 * lane. „Before“ is the same probe with the layer switched off ([OwnerUnderstandingIntake.enabled] = false).
 */
class OwnerUnderstandingRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun strings(json: JSONObject, key: String): List<String> =
        json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()

    private val fixtures = JSONObject(File(root, "regression-understanding.apps.json").readText(Charsets.UTF_8))

    private fun apps(set: String): List<NativeAppCandidate> = fixtures.getJSONArray(set).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.map { NativeAppCandidate(it.getString("package"), it.getString("label")) }
    }

    /** The lane of [text]; an app lookup is resolved against the installed apps like the lookup path does. */
    private fun route(text: String, installed: List<NativeAppCandidate>): String {
        val signature = OwnerRouteSignature.of(text)
        if (!signature.startsWith("APP:OPEN:") && !signature.startsWith("APP:LOOKUP:")) return signature
        val kind = signature.split(':')[1]
        val query = signature.substringAfter("APP:$kind:")
        val matches = runCatching { NativeAppLookup.search(query, installed) }.getOrNull()
        return if (matches != null && matches.exactLabel && matches.total == 1)
            "APP:$kind:FOUND:${matches.candidates.single().packageName}" else "APP:$kind:UNKNOWN"
    }

    private class Outcome(val decision: String, val text: String, val route: String, val options: List<String>,
                          val answeredText: String?, val answeredRoute: String?)

    private fun run(case: JSONObject, layer: Boolean): Outcome {
        val installed = apps(case.getString("apps"))
        val intake = OwnerUnderstandingIntake().apply { enabled = layer }
        val lexicon = { OwnerUnderstandingLexicon.build(installed.map { it.label }, emptyList(), strings(case, "turns"), null) }
        val input = case.getString("input")
        return when (val decision = intake.onOwnerTurn("s", input, null, lexicon)) {
            is OwnerUnderstandingIntake.Decision.Ask -> {
                val options = (OwnerUnderstanding.interpret(input, lexicon()) as OwnerUnderstanding.Result.Clarify).options
                val answer = case.getJSONObject("expect").optString("answer").takeIf { it.isNotEmpty() }
                val chosen = answer?.let { intake.onOwnerTurn("s", it, null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed }
                Outcome("CLARIFY", input, "CLARIFY", options, chosen?.text, chosen?.let { route(it.text, installed) })
            }
            is OwnerUnderstandingIntake.Decision.Proceed -> Outcome(if (decision.changed) "REPAIRED" else "UNCHANGED",
                decision.text, route(decision.text, installed), emptyList(), null, null)
        }
    }

    private fun handled(case: JSONObject, outcome: Outcome): List<String> {
        val expect = case.getJSONObject("expect")
        val problems = mutableListOf<String>()
        val input = case.getString("input")
        when (expect.getString("decision")) {
            "REPAIRED" -> {
                if (outcome.text != expect.getString("text")) problems += "routed text „${outcome.text}“, expected „${expect.getString("text")}“"
                if (outcome.route != expect.getString("route")) problems += "route ${outcome.route}, expected ${expect.getString("route")}"
            }
            "UNCHANGED" -> {
                if (outcome.decision != "UNCHANGED" || outcome.text != input) problems += "changed to „${outcome.text}“ (${outcome.decision})"
                if (outcome.route != route(input, apps(case.getString("apps")))) problems += "route changed to ${outcome.route}"
            }
            "CLARIFY" -> {
                if (outcome.decision != "CLARIFY") problems += "no question; routed „${outcome.text}“ as ${outcome.route}"
                else {
                    if (outcome.options.toSet() != strings(expect, "options").toSet()) problems += "options ${outcome.options}"
                    if (expect.has("answer")) {
                        if (outcome.answeredText != expect.getString("text")) problems += "after answer „${outcome.answeredText}“"
                        if (outcome.answeredRoute != expect.getString("route")) problems += "after answer route ${outcome.answeredRoute}"
                    }
                }
            }
            else -> problems += "unknown decision"
        }
        return problems
    }

    @Test fun everyUnderstandingCaseHoldsWithTheLayerAndTheBeforeCountIsMeasured() {
        val all = cases("regression-understanding.jsonl")
        assertTrue("understanding set unexpectedly small: ${all.size}", all.size >= 40)
        assertEquals("duplicate ids", all.size, all.map { it.getString("id") }.toSet().size)
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        var before = 0
        var after = 0
        val beforeByDecision = sortedMapOf<String, Int>()
        // Repair cases in which the unrepaired utterance at least reached the right lane (the model lane with a wrong name).
        var beforeLaneOnly = 0
        val totalByDecision = sortedMapOf<String, Int>()
        all.forEach { case ->
            val id = case.getString("id")
            assertEquals("$id: runner", "understand", case.getString("runner"))
            val wanted = case.getJSONObject("expect").getString("decision")
            totalByDecision.merge(wanted, 1, Int::plus)
            val without = handled(case, run(case, layer = false))
            val withLayerOutcome = run(case, layer = true)
            val withLayer = handled(case, withLayerOutcome)
            if (without.isEmpty()) { before++; beforeByDecision.merge(wanted, 1, Int::plus) }
            if (wanted == "REPAIRED" && route(case.getString("input"), apps(case.getString("apps"))) ==
                case.getJSONObject("expect").getString("route")) beforeLaneOnly++
            if (withLayer.isEmpty()) after++ else failures += "$id „${case.getString("input")}“: ${withLayer.joinToString("; ")}"
            report.append(id).append('\t').append(if (without.isEmpty()) "before=OK" else "before=MISS").append('\t')
                .append(withLayerOutcome.decision).append('\t').append(withLayerOutcome.text).append('\t')
                .append(withLayerOutcome.route).append('\t').append(withLayerOutcome.options).append('\t')
                .append(withLayerOutcome.answeredText).append('\t').append(withLayerOutcome.answeredRoute).append('\n')
        }
        report.append("TOTAL ${all.size} before=$before after=$after beforeByDecision=$beforeByDecision totals=$totalByDecision " +
            "repairCasesWithRightLaneButWrongNameBefore=$beforeLaneOnly\n")
        runCatching { File(System.getProperty("java.io.tmpdir"), "continuum-understanding-report.txt").writeText(report.toString()) }
        println(report)
        if (failures.isNotEmpty()) fail("${failures.size} of ${all.size} understanding cases failed:\n" + failures.joinToString("\n"))
        // Pinned evidence (docs/research/OWNER-UNDERSTANDING-AND-TASK-CHAIN-20261118.md): without the layer only the
        // cases that need no repair are handled.
        assertEquals("handled without the layer", BEFORE_HANDLED, before)
        assertEquals("handled with the layer", all.size, after)
    }

    /** No utterance of the existing sets may be touched: their routing is correct today and must stay byte-identical. */
    @Test fun noUtteranceOfTheExistingRegressionAndModelSetsIsChanged() {
        val installed = apps("twins").map { it.label }
        val changed = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String, earlier: List<String> = emptyList()) {
            checked++
            val result = OwnerUnderstanding.interpret(utterance, OwnerUnderstandingLexicon.build(installed, emptyList(), earlier, null))
            if (result != OwnerUnderstanding.Result.Unchanged) changed += "$source „$utterance“ → $result"
        }
        listOf("regression.jsonl", "regression-voice.jsonl", "regression-references.jsonl").forEach { file ->
            cases(file).forEach { case ->
                val id = case.getString("id")
                val turns = strings(case, "turns").filterNot { it.startsWith("open:") }
                turns.forEachIndexed { index, turn -> check(id, turn, turns.take(index)) }
                listOf("input", "question").forEach { key -> case.optString(key).takeIf { it.isNotEmpty() }?.let { check(id, it, turns) } }
            }
        }
        listOf("model-dev.jsonl", "model-holdout.jsonl").forEach { file ->
            cases(file).forEach { case ->
                val turns = case.getJSONArray("turns")
                val owner = (0 until turns.length()).map { turns.getJSONObject(it) }.filter { it.getString("role") == "user" }
                    .map { it.getString("text") }
                owner.forEachIndexed { index, turn -> check(case.getString("id"), turn, owner.take(index)) }
            }
        }
        assertTrue("too few utterances checked: $checked", checked >= 150)
        assertTrue("${changed.size} existing utterances would be changed:\n" + changed.joinToString("\n"), changed.isEmpty())
    }

    @Test fun soundAndSpellingDistanceSeparatesMishearingsFromOtherWords() {
        assertTrue(GermanPhonetics.soundsAlike("Thermux", "Termux"))
        assertTrue(GermanPhonetics.soundsAlike("Javis", "Jarvis"))
        assertTrue(GermanPhonetics.soundsAlike("Kalenda", "Kalender"))
        assertFalse(GermanPhonetics.soundsAlike("Kammer", "Kamera"))
        assertTrue(GermanPhonetics.similarity("Thermux", "Termux") >= OwnerUnderstanding.ACCEPT_FREE)
        assertTrue(GermanPhonetics.similarity("Javis", "Jarvis") >= OwnerUnderstanding.ACCEPT_FREE)
        assertTrue(GermanPhonetics.similarity("Telegramm", "Telegram") >= OwnerUnderstanding.ACCEPT_IN_SLOT)
        assertTrue(GermanPhonetics.similarity("Kammer", "Kamera") < OwnerUnderstanding.CLARIFY_MIN)
        assertTrue(GermanPhonetics.similarity("Therme", "Termux") < OwnerUnderstanding.CLARIFY_MIN)
        assertTrue(GermanPhonetics.similarity("Cloud", "Claude") < OwnerUnderstanding.ACCEPT_IN_SLOT)
        assertEquals(1.0, GermanPhonetics.similarity("WhatsApp", "whatsapp"), 0.0)
        assertEquals(0.0, GermanPhonetics.similarity("", "Termux"), 0.0)
    }

    @Test fun theRepairNoticeIsStatedOnceAndOnlyForItsSession() {
        var now = 1_000L
        val intake = OwnerUnderstandingIntake { now }
        val lexicon = { OwnerUnderstandingLexicon.build(listOf("Termux")) }
        val decision = intake.onOwnerTurn("a", "Öffne Thermux", null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed
        assertEquals("Öffne Termux", decision.text)
        assertEquals("Antwort", intake.withNotice("b", "Antwort"))
        intake.onOwnerTurn("a", "Öffne Thermux", null, lexicon)
        assertEquals("Ich habe „Thermux“ als Termux verstanden.\nTermux wird geöffnet.", intake.withNotice("a", "Termux wird geöffnet."))
        assertEquals("Noch eine Antwort", intake.withNotice("a", "Noch eine Antwort"))
        intake.onOwnerTurn("a", "Öffne Thermux", null, lexicon)
        now += OwnerUnderstandingIntake.NOTICE_MILLIS + 1
        assertEquals("Spät", intake.withNotice("a", "Spät"))
        // An already correct utterance leaves no notice and is handed on as the same object content.
        val plain = intake.onOwnerTurn("a", "Öffne Termux", null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed
        assertFalse(plain.changed)
        assertEquals("Antwort", intake.withNotice("a", "Antwort"))
    }

    @Test fun aClarifyingQuestionIsAnsweredByNameByOrdinalOrDropped() {
        val lexicon = { OwnerUnderstandingLexicon.build(listOf("Calendar", "Kalender")) }
        fun ask(intake: OwnerUnderstandingIntake) =
            assertEquals("Meinst du Calendar oder Kalender?",
                (intake.onOwnerTurn("s", "Öffne Kalenda", null, lexicon) as OwnerUnderstandingIntake.Decision.Ask).question)
        OwnerUnderstandingIntake().let { intake ->
            ask(intake)
            assertEquals("Öffne Kalender", (intake.onOwnerTurn("s", "Kalender", null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed).text)
            assertEquals("Ich habe „Kalenda“ als Kalender verstanden.\nOk", intake.withNotice("s", "Ok"))
        }
        OwnerUnderstandingIntake().let { intake ->
            ask(intake)
            assertEquals("Öffne Calendar", (intake.onOwnerTurn("s", "das erste", null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed).text)
        }
        OwnerUnderstandingIntake().let { intake ->
            ask(intake)
            // Another request drops the question; it is routed as it is and the old utterance is not revived.
            val other = intake.onOwnerTurn("s", "Wie spät ist es", null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed
            assertEquals("Wie spät ist es", other.text)
            assertFalse(other.changed)
            assertEquals("Kalender", (intake.onOwnerTurn("s", "Kalender", null, lexicon) as OwnerUnderstandingIntake.Decision.Proceed).text)
        }
        assertNull(OwnerUnderstanding.chosenOption("beide", listOf("Calendar", "Kalender")))
        assertNull(OwnerUnderstanding.chosenOption("Calendar oder Kalender", listOf("Calendar", "Kalender")))
        assertEquals(1, OwnerUnderstanding.chosenOption("die zweite", listOf("Calendar", "Kalender")))
    }

    @Test fun theLexiconIsBoundedAndHoldsOnlyNamesAndRecentNouns() {
        val lexicon = OwnerUnderstandingLexicon.build(List(1_000) { "App$it" + "x".repeat(3) }, List(100) { "Dokument-$it.pdf" },
            List(50) { "Wir sprechen über das Projekt Helios$it und sonst nichts" }, "Nova")
        assertTrue(lexicon.entries.size <= OwnerUnderstandingLexicon.STATIC.size + 1 + OwnerUnderstandingLexicon.MAX_APPS +
            OwnerUnderstandingLexicon.MAX_DOCUMENTS + OwnerUnderstandingLexicon.MAX_SALIENT)
        assertTrue(lexicon.isName("Nova"))
        assertTrue(lexicon.isKnownWord("nicht"))
        assertFalse(lexicon.isKnownWord("Thermux"))
    }

    private companion object {
        /** Cases the unchanged routing handles without the layer: exactly the UNCHANGED guard cases. */
        const val BEFORE_HANDLED = 30
    }
}
