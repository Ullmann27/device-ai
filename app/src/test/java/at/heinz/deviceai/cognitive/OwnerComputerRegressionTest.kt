package at.heinz.deviceai.cognitive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-computer.jsonl` (development set) and `regression-computer-check.jsonl` (held-out check set) of the
 * computer lane's one rule about sentences with several orders (review finding B5, 19.11.2026): a further order
 * behind the app, in the search text or in the recipient hands the sentence on (`NONE`) – it is neither refused as a
 * dead end nor typed into a search field – while the lane's own orders stay what they were.
 *
 * `computer_order` expects `computer`: `READY:<APP>:<GOAL>` · `ASK` · `UNSUPPORTED` · `NONE`, and optionally `query`
 * (what would be typed into the app's search). The check set was written and committed before the rule.
 */
class OwnerComputerRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            assertEquals(case.getString("id"), "computer_order", case.getString("runner"))
            val expect = case.getJSONObject("expect")
            val input = case.getString("input")
            val parsed = OwnerComputerIntentParser.parse(input)
            val got = EvalProbe.computer(input)
            val problems = mutableListOf<String>()
            if (got != expect.getString("computer")) problems += "got $got, expected ${expect.getString("computer")}"
            if (expect.has("query")) {
                val query = (parsed as? OwnerComputerIntentParser.Result.Ready)?.intent?.query
                if (query != expect.getString("query")) problems += "query „$query“, expected „${expect.getString("query")}“"
            }
            // Whatever the lane makes of a sentence: a further order is never part of what it would type.
            (parsed as? OwnerComputerIntentParser.Result.Ready)?.intent?.query?.let { query ->
                if (OwnerVerbLexicon.hasFurtherOrder(query)) problems += "the query carries a further order: „$query“"
            }
            if (problems.isNotEmpty()) misses[case.getString("id")] = problems.joinToString("; ") + " · „$input“"
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealParser() {
        val total = cases("regression-computer.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 20)
        val misses = misses("regression-computer.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /** Measured, not tuned: a new miss – or a silently repaired one – fails this test. */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases("regression-computer-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 16)
        val misses = misses("regression-computer-check.jsonl")
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    /**
     * A sentence handed on is then read clause by clause: its orders are found, none is lost and none runs by itself.
     * Since the second phase of the sequences (19.11.2026) „Öffne WhatsApp und schreib …“ is a complete sequence of
     * ONE send step ([NOW_A_SEQUENCE]): both orders are in it, and the send lane carries it out with its own gates.
     */
    @Test fun aSentenceHandedOnIsReadAsSeveralOrders() {
        var checked = 0
        val sequences = sortedSetOf<String>()
        listOf("regression-computer.jsonl", "regression-computer-check.jsonl").forEach { name ->
            cases(name).filter { it.getString("category") == "further_order" && it.getString("id") !in KNOWN_CHECK_MISSES }.forEach { case ->
                val input = case.getString("input")
                val reading = checkNotNull(OwnerMissionPlanner.read(input, "Jarvis")) { input }
                checked++
                val plan = reading.plan
                if (plan != null) {
                    sequences += case.getString("id")
                    assertTrue("${case.getString("id")}: „$input“ → ${plan.codes}", plan.codes.all { it.startsWith("SEND:WHATSAPP:") })
                    return@forEach
                }
                assertTrue("${case.getString("id")}: „$input“ has ${reading.orders.size} orders ${reading.codes}", reading.orders.size >= 2)
            }
        }
        assertTrue("too few: $checked", checked >= 12)
        assertEquals(NOW_A_SEQUENCE, sequences)
    }

    /** „Öffne WhatsApp und schreib <jemandem> <Text>“: opened and written by the send lane as one step. */
    private val NOW_A_SEQUENCE: Set<String> = sortedSetOf("COM-001", "COMC-005")

    @Test fun theDevelopmentAndTheCheckSetShareNoSentence() {
        fun inputs(name: String) = cases(name).map { it.getString("input").trim().lowercase() }
        val development = inputs("regression-computer.jsonl")
        val check = inputs("regression-computer-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val ids = (cases("regression-computer.jsonl") + cases("regression-computer-check.jsonl")).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    private companion object {
        val KNOWN_CHECK_MISSES: Set<String> = emptySet()
    }
}
