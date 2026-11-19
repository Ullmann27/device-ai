package at.heinz.deviceai.cognitive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-talk.jsonl` (development set) and `regression-talk-check.jsonl` (held-out check set) of the owner's
 * conversation package (19.11.2026): the tone command, „Nenn mich …“, the greeting, the repair of stray keyboard
 * dots, „Erstelle eine PDF“ without a pointing word, the order a whole sentence is, what the cloud brain may map a
 * free sentence to, the greeting switch, the pause computed from two timestamps, the direct question which model
 * runs, the desk's search in the wish form and a name that speech recognition broke apart. Executed against the
 * productive parsers. The first 136 + 80 cases were written and committed before any implementation (`f541508c9`);
 * the further cases were written before the code they test (the pause cases after it – see the README). The check
 * set is measured, not tuned, and its score is pinned here as it is.
 */
class OwnerTalkRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }
    private val name = "Jarvis"

    private fun cases(file: String): List<JSONObject> = File(root, file).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun codes(value: Any?): String = when (value) {
        is org.json.JSONArray -> (0 until value.length()).joinToString(" → ") { value.getString(it) }
        else -> value.toString()
    }

    /** What the case expects and what the productive code answers, in the same notation. */
    private fun verdict(case: JSONObject): Pair<String, String> {
        val input = case.getString("input")
        val expect = case.getJSONObject("expect")
        return when (val runner = case.getString("runner")) {
            "tone_command" -> expect.getString("tone") to (OwnerToneCommand.parse(input, name)?.name ?: "NONE")
            "name_command" -> expect.getString("name") to when (val command = OwnerNameCommand.parse(input, name)) {
                is OwnerNameCommand.Command.Set -> "SET:" + command.name
                OwnerNameCommand.Command.Forget -> "FORGET"
                null -> "NONE"
            }
            "greeting" -> expect.getString("greeting") to if (OwnerGreeting.isGreeting(input, name)) "YES" else "NO"
            "input_repair" -> expect.getString("text") to OwnerInputRepair.strayDots(input)
            "make_export" -> expect.getString("export") to
                ((OwnerExportCommand.parse(input, name) ?: OwnerExportCommand.parseMake(input, name))?.format?.name ?: "NONE")
            // The owner's sentence as the Activity hands it to the lanes: repaired first, then planned.
            "owner_order" -> codes(expect.get("plan")) to
                (OwnerMissionPlanner.plan(OwnerInputRepair.strayDots(input), name)?.codes?.joinToString(" → ") ?: "NONE")
            "order_wanted" -> expect.getBoolean("wanted").toString() to
                (OwnerMissionModelPlan.wants(input, name) || OwnerMissionModelPlan.wantsOrder(input, name)).toString()
            "order_mapping" -> expect.getString("order") to
                when (val checked = OwnerMissionModelPlan.check(input, case.optString("model"), name, allowSingle = true)) {
                    is OwnerMissionModelPlan.Result.Accepted ->
                        (if (OwnerMissionModelPlan.runsWithoutQuestion(checked.plan, name)) "RUN:" else "ASK:") + checked.plan.codes.joinToString(" → ")
                    // The plan leaves an order of the sentence open: nothing runs, the rest is only offered.
                    is OwnerMissionModelPlan.Result.Uncovered -> "PARTIAL:" + checked.rest.joinToString(" → ") { it.code }
                    is OwnerMissionModelPlan.Result.Rejected -> "REJECTED"
                }
            "greeting_switch" -> expect.getString("greeting_switch") to
                when (OwnerGreetingSwitchCommand.parse(input, name)) { true -> "ON"; false -> "OFF"; null -> "NONE" }
            "identity_question" -> expect.getString("identity") to
                if (OwnerChatIntentRouter.route(input) == OwnerChatLocalIntent.MODEL_IDENTITY) "YES" else "NO"
            // The document desk's own parser: FIND = search by name and show the hits, OPEN = open when it is clear.
            "desk_search" -> expect.getString("open") to
                (OwnerLibraryCommand.parseOpen(input, name)?.let { (if (it.openWhenClear) "OPEN:" else "FIND:") + it.query } ?: "NONE")
            "near_name" -> expect.getString("match") to run {
                val listed = case.getJSONArray("files")
                val files = (0 until listed.length()).map { index ->
                    OwnerLibraryFile(listed.getString(index), "Download", "/storage/emulated/0/Download/" + listed.getString(index),
                        10_000L + index, 1_700_000_000_000L + index)
                }
                OwnerLibraryMatcher.rank(input, files).firstOrNull()?.let { it.tier.name + ":" + it.file.name } ?: "NONE"
            }
            "absence" -> expect.getString("absence") to run {
                val zone = java.time.ZoneId.of(case.getString("zone"))
                fun millis(key: String): Long? = case.optString(key).takeIf { !case.isNull(key) && it.isNotEmpty() }
                    ?.let { java.time.LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() }
                when (val absence = OwnerReturn.absence(millis("last"), millis("now")!!, zone)) {
                    null -> "NONE"
                    OwnerAbsence.Brief -> "BRIEF"
                    else -> OwnerReturn.spoken(absence)!!
                }
            }
            else -> error("unknown runner $runner")
        }
    }

    /** Ids of the cases of [file] that do not hold, with what the code answered instead. */
    private fun misses(file: String): Map<String, String> {
        val all = cases(file)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            val (expected, got) = runCatching { verdict(case) }.getOrElse { "?" to "ERROR ${it.javaClass.simpleName}: ${it.message}" }
            if (got != expected) misses[case.getString("id")] = "got $got, expected $expected · „${case.getString("input")}“"
        }
        println("$file: ${all.size - misses.size} / ${all.size} cases hold")
        all.groupBy { it.getString("runner") }.forEach { (runner, rows) ->
            println("  $runner: ${rows.count { it.getString("id") !in misses }} / ${rows.size}")
        }
        misses.forEach { println("  MISS ${it.key}: ${it.value}") }
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealParsers() {
        val total = cases("regression-talk.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 194)
        val misses = misses("regression-talk.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /**
     * The held-out set is measured, not tuned: [KNOWN_CHECK_MISSES] lists the cases the code does not get right
     * today, so the score in the README stays true and a new miss – or a silently changed one – fails this test.
     */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases("regression-talk-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 114)
        val misses = misses("regression-talk-check.jsonl")
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    /** Separate development and check data: no (runner, sentence, model answer) of one set appears in the other. */
    @Test fun theDevelopmentAndTheCheckSetShareNoCase() {
        // Exact sentences: „Nenn mich Heinz“ and „nenn mich heinz“ are two cases on purpose (typed in lower case).
        fun keys(file: String) = cases(file).map {
            it.getString("runner") + "|" + it.getString("input").trim() + "|" + it.optString("model") + "|" + it.optJSONArray("files")
        }
        val development = keys("regression-talk.jsonl")
        val check = keys("regression-talk-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val ids = (cases("regression-talk.jsonl") + cases("regression-talk-check.jsonl")).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** The owner's two field cases of 19.11.2026 are in the development set and marked as such. */
    @Test fun theOwnersFieldCasesAreMarkedAndPresent() {
        val field = cases("regression-talk.jsonl").filter { it.getString("category") == "field" }
        assertTrue(field.any { it.getString("input") == "Erstelle.bitte eine pdf und zeig sie mir.dann" && it.getString("runner") == "owner_order" })
        assertTrue(field.any { it.getString("input") == "ich schaue meinen Standort Ordner ich suche eine PDF die heißt a und 4 Experimente" &&
            it.getString("runner") == "order_mapping" })
        assertTrue(field.any { it.getString("input") == "Ich suche eine PDF, die heißt Argon4 Experimente" })
        // The same sentences on the fixed route both brains share: the desk's own search, and the name as a near miss.
        assertTrue(field.any { it.getString("runner") == "desk_search" && it.getJSONObject("expect").getString("open") == "FIND:a und 4 Experimente" })
        assertTrue(field.any { it.getString("runner") == "near_name" && it.getJSONObject("expect").getString("match") == "NEAR:Argon4 Experimente.pdf" })
        field.forEach { assertTrue(it.getString("id"), it.getString("note").contains("Feldfall")) }
    }

    private companion object {
        val KNOWN_CHECK_MISSES: Set<String> = emptySet()
    }
}
