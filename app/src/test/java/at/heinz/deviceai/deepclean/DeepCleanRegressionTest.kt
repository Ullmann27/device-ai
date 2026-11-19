package at.heinz.deviceai.deepclean

import at.heinz.deviceai.briefing.ContinuumBriefingCommands
import at.heinz.deviceai.cognitive.OwnerLibraryCommand
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.devicetools.DeviceToolCommand
import at.heinz.deviceai.routines.OwnerRoutineCommands
import at.heinz.deviceai.sending.OwnerSendIntentParser
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-deepclean.jsonl` (development set) and `regression-deepclean-check.jsonl` (held-out check set) of the
 * storage lane: which sentence is which order with which rights and restrictions, which answer to a selection means
 * what – and which sentence must keep today's route (`NONE`). Executed against the productive parser.
 */
class DeepCleanRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun route(input: String): String = DeepCleanCommands.parse(input, "Jarvis")?.code ?: "NONE"

    private fun reply(input: String): String = when (val r = DeepCleanCommands.reply(input, "Jarvis")) {
        null -> "NONE"
        is DeepCleanCommands.Reply.Pick -> "PICK:" + r.numbers.joinToString(",")
        is DeepCleanCommands.Reply.Count -> "COUNT:" + r.count
        DeepCleanCommands.Reply.All -> "ALL"
        DeepCleanCommands.Reply.None -> "NONE_OF_THEM"
        DeepCleanCommands.Reply.Yes -> "YES"
        DeepCleanCommands.Reply.No -> "NO"
        DeepCleanCommands.Reply.Stop -> "STOP"
        DeepCleanCommands.Reply.Restore -> "RESTORE"
        is DeepCleanCommands.Reply.Show -> "SHOW:" + r.group.name
        is DeepCleanCommands.Reply.Keep -> "KEEP:" + r.groups.map { it.name }.sorted().joinToString(",")
    }

    private fun kept(lane: String, input: String): Boolean = when (lane) {
        "DIAGNOSIS" -> SystemIntelligenceCommands.isDiagnosisRequest(input)
        "CACHE_PREVIEW" -> SystemIntelligenceCommands.isCachePreviewRequest(input)
        else -> error("unknown lane $lane")
    }

    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            val expect = case.getJSONObject("expect")
            val input = case.getString("input")
            val (got, wanted) = when (case.getString("runner")) {
                "deepclean_route" -> route(input) to expect.getString("route")
                "deepclean_reply" -> reply(input) to expect.getString("reply")
                else -> "unknown runner" to case.getString("runner")
            }
            if (got != wanted) misses[case.getString("id")] = "got $got, expected $wanted · „$input“"
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealParser() {
        val total = cases("regression-deepclean.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 120)
        val misses = misses("regression-deepclean.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /** The held-out set is measured, not tuned: [KNOWN_CHECK_MISSES] are the cases the parser gets wrong today. */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases("regression-deepclean-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 60)
        val misses = misses("regression-deepclean-check.jsonl")
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    @Test fun noSentenceThatMustKeepItsRouteIsTaken() {
        var checked = 0
        listOf("regression-deepclean.jsonl", "regression-deepclean-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("runner") == "deepclean_route" && it.getJSONObject("expect").getString("route") == "NONE" }.forEach { case ->
                checked++
                assertEquals("${case.getString("id")}: „${case.getString("input")}“", "NONE", route(case.getString("input")))
            }
        }
        assertTrue("too few negatives: $checked", checked >= 40)
    }

    @Test fun theLaneNamedForAKeptSentenceReallyTakesIt() {
        var checked = 0
        listOf("regression-deepclean.jsonl", "regression-deepclean-check.jsonl").forEach { name ->
            cases(name).forEach { case ->
                val lane = case.getJSONObject("expect").optString("keeps").takeIf { it.isNotEmpty() } ?: return@forEach
                checked++
                assertTrue("${case.getString("id")}: $lane does not take „${case.getString("input")}“", kept(lane, case.getString("input")))
            }
        }
        assertTrue("too few kept sentences: $checked", checked >= 6)
    }

    /** No order of this lane is taken by a lane the Activity asks before it. */
    @Test fun noStorageOrderBelongsToALaneInFrontOfIt() {
        listOf("regression-deepclean.jsonl", "regression-deepclean-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("runner") == "deepclean_route" && it.getJSONObject("expect").getString("route") != "NONE" }
                .filter { it.getString("id") !in KNOWN_CHECK_MISSES }
                .forEach { case ->
                    val input = case.getString("input")
                    val id = case.getString("id")
                    assertNull("$id mission: „$input“", OwnerMissionPlanner.plan(input, "Jarvis"))
                    assertNull("$id desk: „$input“", OwnerLibraryCommand.parseOpen(input, "Jarvis"))
                    assertNull("$id send: „$input“", OwnerSendIntentParser.parse(input, "Jarvis"))
                    assertNull("$id news: „$input“", OwnerNewsCommand.parse(input, "Jarvis"))
                    assertNull("$id routine: „$input“", OwnerRoutineCommands.parse(input))
                    assertNull("$id briefing: „$input“", ContinuumBriefingCommands.parse(input, "Jarvis"))
                    assertNull("$id device tools: „$input“", DeviceToolCommand.parse(input, "Jarvis"))
                    assertTrue("$id diagnosis: „$input“", !SystemIntelligenceCommands.isDiagnosisRequest(input))
                    assertTrue("$id cache preview: „$input“", !SystemIntelligenceCommands.isCachePreviewRequest(input))
                }
        }
    }

    /** No utterance of the other evaluation sets becomes an order of this lane, except the ones listed and explained. */
    @Test fun noUtteranceOfTheOtherSetsBecomesAStorageOrder() {
        val moved = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String) {
            checked++
            DeepCleanCommands.parse(utterance, "Jarvis")?.let { moved += "$source „$utterance“ → ${it.code}" }
        }
        root.listFiles { file -> file.name.endsWith(".jsonl") && !file.name.startsWith("regression-deepclean") }!!.sortedBy { it.name }.forEach { file ->
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
        assertEquals("existing utterances that would become a storage order:\n" + moved.joinToString("\n"),
            KNOWN_MOVED_UTTERANCES, moved.map { it.substringBefore(' ') }.toSet())
    }

    @Test fun theDevelopmentAndTheCheckSetShareNoCase() {
        fun keys(name: String) = cases(name).map { it.getString("runner") + "|" + it.getString("input").trim().lowercase() }
        val development = keys("regression-deepclean.jsonl")
        val check = keys("regression-deepclean-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val ids = (cases("regression-deepclean.jsonl") + cases("regression-deepclean-check.jsonl")).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    private companion object {
        /**
         * Measured on the first run of the held-out set and not tuned away: „Lösch den ganzen Datenmüll“ (no adjective
         * before the noun), „Wieso ist mein Speicher schon wieder voll?“ (two fillers), „Was belegt so viel
         * Speicherplatz?“ (compound noun) and „Warum ist mein Handy gerade so träge?“ (two fillers) are not taken – they
         * keep their present route (the model), nothing is deleted by them.
         */
        val KNOWN_CHECK_MISSES: Set<String> = setOf("DCC-005", "DCC-015", "DCC-017", "DCC-029")
        /**
         * Two sentences of the mission set that by design are no plan (`NONE`): a clean-up order with a restriction and a
         * check with „lösch nichts“. Without a plan they reach this lane, which takes them whole and narrows them – the
         * route the mission set expects them to keep.
         */
        val KNOWN_MOVED_UTTERANCES: Set<String> = setOf("regression-missions.jsonl:MIS-062", "regression-missions.jsonl:MIS-064")
    }
}
