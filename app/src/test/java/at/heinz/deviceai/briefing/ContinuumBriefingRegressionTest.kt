package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.OwnerChatIntentRouter
import at.heinz.deviceai.cognitive.OwnerChatLocalIntent
import at.heinz.deviceai.cognitive.OwnerDeviceClockReply
import at.heinz.deviceai.cognitive.OwnerLibraryCommand
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.WorkRecall
import at.heinz.deviceai.routines.OwnerRoutineCommands
import at.heinz.deviceai.sending.OwnerSendIntentParser
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * `regression-briefing.jsonl` (development set) and `regression-briefing-check.jsonl` (held-out check set) of the
 * briefing lane: which sentence is an order for the system check, the briefing, appointments, weather, the place
 * setting, a call or the daily offer – and which sentence must keep today's route (`NONE`). Executed against the
 * productive parsers. Both files were written and committed before the implementation; the check set is not used for
 * tuning (docs/evaluation/continuum-eval-v1/README.md) and its score is pinned here as it is.
 */
class ContinuumBriefingRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun route(input: String): String = ContinuumBriefingCommands.parse(input, "Jarvis")?.code ?: "NONE"

    private fun choice(case: JSONObject): String {
        val offers = case.getJSONArray("offers").let { array ->
            (0 until array.length()).map { ContinuumBriefingBoard.OfferKind.valueOf(array.getString(it)) }
        }
        return when (val choice = ContinuumNextSteps.choose(case.getString("input"), offers, "Jarvis")) {
            null -> "NONE"
            is ContinuumNextSteps.Choice.Pick -> "PICK:" + (choice.index + 1)
            ContinuumNextSteps.Choice.Ask -> "ASK"
            ContinuumNextSteps.Choice.Decline -> "DECLINE"
        }
    }

    /** true when the lane named by `keeps` really takes [input] today – asked of that lane's own productive parser. */
    private fun kept(lane: String, input: String): Boolean = when (lane) {
        "NEWS" -> OwnerNewsCommand.parse(input, "Jarvis") != null
        "CLOCK" -> OwnerDeviceClockReply.answer(input, Instant.parse("2026-11-19T08:00:00Z"), ZoneId.of("Europe/Vienna")) != null
        "GREETING" -> OwnerChatIntentRouter.route(input) == OwnerChatLocalIntent.GREETING
        "CAPABILITIES" -> OwnerChatIntentRouter.route(input) == OwnerChatLocalIntent.CAPABILITIES
        "DIAGNOSIS" -> SystemIntelligenceCommands.isDiagnosisRequest(input)
        "SEND" -> OwnerSendIntentParser.parse(input, "Jarvis") != null
        "DESK" -> OwnerLibraryCommand.parseOpen(input, "Jarvis") != null
        "ROUTINE" -> OwnerRoutineCommands.parse(input) != null
        "RECALL" -> WorkRecall.parse(input) != null
        else -> error("unknown lane $lane")
    }

    /** Ids of the cases of [name] that do not hold, with what came out instead. */
    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            val expect = case.getJSONObject("expect")
            val input = case.getString("input")
            val (got, wanted) = when (case.getString("runner")) {
                "briefing_route" -> route(input) to expect.getString("route")
                "briefing_choice" -> choice(case) to expect.getString("choice")
                else -> "unknown runner" to case.getString("runner")
            }
            if (got != wanted) misses[case.getString("id")] = "got $got, expected $wanted · „$input“"
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealParsers() {
        val total = cases("regression-briefing.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 90)
        val misses = misses("regression-briefing.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /**
     * The held-out set is measured, not tuned: [KNOWN_CHECK_MISSES] lists the cases the parsers do not get right
     * today, so the score in the README stays true and a new miss – or a silently repaired one – fails this test.
     */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases("regression-briefing-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 80)
        val misses = misses("regression-briefing-check.jsonl")
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    /** No sentence that must keep today's route is taken – in neither set, known misses included. */
    @Test fun noSentenceThatMustKeepItsRouteIsTaken() {
        var checked = 0
        listOf("regression-briefing.jsonl", "regression-briefing-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("runner") == "briefing_route" && it.getJSONObject("expect").getString("route") == "NONE" }
                .forEach { case ->
                    checked++
                    assertEquals("${case.getString("id")}: „${case.getString("input")}“", "NONE", route(case.getString("input")))
                }
        }
        assertTrue("too few negatives: $checked", checked >= 50)
    }

    /** Where a case names the lane that keeps the sentence, that lane's own parser really takes it today. */
    @Test fun theLaneNamedForAKeptSentenceReallyTakesIt() {
        var checked = 0
        listOf("regression-briefing.jsonl", "regression-briefing-check.jsonl").forEach { name ->
            cases(name).forEach { case ->
                val lane = case.getJSONObject("expect").optString("keeps").takeIf { it.isNotEmpty() } ?: return@forEach
                checked++
                assertTrue("${case.getString("id")}: $lane does not take „${case.getString("input")}“", kept(lane, case.getString("input")))
            }
        }
        assertTrue("too few kept sentences: $checked", checked >= 20)
    }

    /**
     * No order of this lane is something a lane in front of it takes today: the mission planner, the document desk
     * and the send lane are asked before the briefing lane in the Activity. Known held-out misses are left out.
     */
    @Test fun noBriefingOrderBelongsToALaneInFrontOfIt() {
        listOf("regression-briefing.jsonl", "regression-briefing-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("runner") == "briefing_route" && it.getJSONObject("expect").getString("route") != "NONE" }
                .filter { it.getString("id") !in KNOWN_CHECK_MISSES }
                .forEach { case ->
                    val input = case.getString("input")
                    val id = case.getString("id")
                    assertNull("$id mission: „$input“", OwnerMissionPlanner.plan(input, "Jarvis"))
                    assertNull("$id desk: „$input“", OwnerLibraryCommand.parseOpen(input, "Jarvis"))
                    assertNull("$id send: „$input“", OwnerSendIntentParser.parse(input, "Jarvis"))
                    assertNull("$id news: „$input“", OwnerNewsCommand.parse(input, "Jarvis"))
                    assertNull("$id routine: „$input“", OwnerRoutineCommands.parse(input))
                }
        }
    }

    /** A sequence of orders stays a mission, a mission never becomes a briefing order (asked in front of this lane). */
    @Test fun aMissionSentenceStaysAMission() {
        listOf("Such das neueste PDF in Downloads und öffne es",
            "Öffne das PDF Argon4 und fass Seite 2 zusammen und speichere es als Word").forEach { input ->
            assertNotNull(input, OwnerMissionPlanner.plan(input, "Jarvis"))
            assertEquals(input, "NONE", route(input))
        }
    }

    /**
     * No utterance of the other evaluation sets becomes an order of this lane: their present routes are correct and
     * must not move (owner turns of the lanes, the chains, the understanding layer, the voice round, the model sets).
     */
    @Test fun noUtteranceOfTheOtherSetsBecomesABriefingOrder() {
        val moved = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String) {
            checked++
            ContinuumBriefingCommands.parse(utterance, "Jarvis")?.let { moved += "$source „$utterance“ → ${it.code}" }
        }
        root.listFiles { file -> file.name.endsWith(".jsonl") && !file.name.startsWith("regression-briefing") }!!.sortedBy { it.name }.forEach { file ->
            cases(file.name).forEach { case ->
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
        assertTrue("too few utterances checked: $checked", checked >= 500)
        assertEquals("existing utterances that would become a briefing order:\n" + moved.joinToString("\n"),
            KNOWN_MOVED_UTTERANCES, moved.map { it.substringBefore(' ') }.toSet())
    }

    /** Separate development and check data: no sentence of one set appears in the other. */
    @Test fun theDevelopmentAndTheCheckSetShareNoCase() {
        fun keys(name: String) = cases(name).map { case ->
            case.getString("runner") + "|" + case.getString("input").trim().lowercase() + "|" + case.optJSONArray("offers")
        }
        val development = keys("regression-briefing.jsonl")
        val check = keys("regression-briefing-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val ids = (cases("regression-briefing.jsonl") + cases("regression-briefing-check.jsonl")).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    private companion object {
        val KNOWN_CHECK_MISSES: Set<String> = emptySet()
        /**
         * `file:id` of utterances of other sets this lane takes. All five are off-topic filler turns in their own
         * sets („Wie wird das Wetter morgen?“, „Wie ist das Wetter?“) or a sentence-end example („Ruf Anna an“): they
         * had no lane of their own and went to the model. Answering them is what this lane is for; their own tests
         * are untouched. Any further utterance that moves fails this test.
         */
        val KNOWN_MOVED_UTTERANCES: Set<String> = setOf("regression-library-check.jsonl:LIBC-041",
            "regression-references.jsonl:REF-010", "regression-references.jsonl:REF-023",
            "regression-voice.jsonl:VOI-END-007", "regression.jsonl:REG-LONG-001")
    }
}
