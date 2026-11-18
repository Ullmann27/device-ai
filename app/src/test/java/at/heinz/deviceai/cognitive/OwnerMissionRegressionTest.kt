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
 */
class OwnerMissionRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun expected(case: JSONObject): String = case.getJSONObject("expect").let { expect ->
        expect.optJSONArray("plan")?.let { plan -> (0 until plan.length()).joinToString(" → ") { plan.getString(it) } } ?: expect.getString("plan")
    }

    private fun planned(input: String): String = OwnerMissionPlanner.plan(input, "Jarvis")?.codes?.joinToString(" → ") ?: "NONE"

    /** Ids of the cases of [name] whose plan differs from the expected one, with what was planned instead. */
    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            assertEquals("mission_plan", case.getString("runner"))
            val got = planned(case.getString("input"))
            if (got != expected(case)) misses[case.getString("id")] = "planned $got, expected ${expected(case)} · „${case.getString("input")}“"
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

    /** No sentence that must keep today's route becomes a plan – in neither set, known misses included. */
    @Test fun noSentenceThatMustKeepItsRouteIsPlanned() {
        listOf("regression-missions.jsonl", "regression-missions-check.jsonl").forEach { name ->
            cases(name).filter { expected(it) == "NONE" }.forEach { case ->
                assertEquals("${case.getString("id")}: „${case.getString("input")}“", "NONE", planned(case.getString("input")))
            }
        }
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
        root.listFiles { file -> file.name.endsWith(".jsonl") && !file.name.startsWith("regression-missions") }!!.sortedBy { it.name }.forEach { file ->
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
        assertTrue("${moved.size} existing utterances would become a mission:\n" + moved.joinToString("\n"), moved.isEmpty())
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
    }
}
