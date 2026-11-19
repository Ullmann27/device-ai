package at.heinz.deviceai.codingagents

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-coding-agents.jsonl` (development set) and `regression-coding-agents-check.jsonl` (held-out check set):
 * which owner sentences are an order for the cloud coding agents, for which repository of the allow-list and of which
 * kind – and which sentences must keep today's route (`NONE`). Executed against the productive parser. The check set
 * was written and committed before the parser was (docs/evaluation/continuum-eval-v1/README.md) and is not used for
 * tuning; its score is pinned here as it is.
 */
class CodingAgentOrderRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun allowed(case: JSONObject): List<String> = case.optJSONArray("repos")
        ?.let { repos -> (0 until repos.length()).map(repos::getString) } ?: DEFAULT_REPOSITORIES

    private fun parsed(case: JSONObject): CodingAgentCommand? =
        CodingAgentOrderParser.parse(case.getString("input"), ASSISTANT, allowed(case))

    /** Ids of the cases of [name] whose result differs from the expected one, with what was parsed instead. */
    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            assertEquals("coding_order", case.getString("runner"))
            val expect = case.getJSONObject("expect")
            val command = parsed(case)
            val got = CodingAgentOrderParser.code(command)
            val task = (command as? CodingAgentCommand.Order)?.task
            val wrongTask = expect.has("task") && task != expect.getString("task")
            if (got != expect.getString("order") || wrongTask) misses[case.getString("id")] =
                "parsed $got" + (if (wrongTask) " with task „$task“" else "") + ", expected ${expect.getString("order")} · „${case.getString("input")}“"
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealParser() {
        val total = cases(DEVELOPMENT).size
        assertTrue("development set unexpectedly small: $total", total >= 50)
        val misses = misses(DEVELOPMENT)
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /**
     * The held-out set is measured, not tuned: [KNOWN_CHECK_MISSES] lists the cases the parser does not get right
     * today, so the score in the README stays true and a new miss – or a silently changed one – fails this test.
     */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases(CHECK).size
        assertTrue("check set unexpectedly small: $total", total >= 50)
        val misses = misses(CHECK)
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    /** No sentence that must keep today's route becomes a command – in neither set, known misses included. */
    @Test fun noSentenceThatMustKeepItsRouteIsTaken() {
        listOf(DEVELOPMENT, CHECK).forEach { name ->
            cases(name).filter { it.getJSONObject("expect").getString("order") == "NONE" }.forEach { case ->
                assertEquals("${case.getString("id")}: „${case.getString("input")}“", "NONE", CodingAgentOrderParser.code(parsed(case)))
            }
        }
    }

    /** A run is only ever proposed for a repository of the allow-list of that case – in neither set. */
    @Test fun noCaseEverNamesARepositoryOutsideItsAllowList() {
        listOf(DEVELOPMENT, CHECK).forEach { name ->
            cases(name).forEach { case ->
                val repository = when (val choice = (parsed(case) as? CodingAgentCommand.Order)?.repository) {
                    is CodingAgentRepoChoice.Named -> choice.repository
                    is CodingAgentRepoChoice.OnlyAllowed -> choice.repository
                    else -> null
                }
                if (repository != null) assertTrue("${case.getString("id")} proposed $repository", repository in allowed(case))
            }
        }
    }

    /**
     * No utterance of the other evaluation sets becomes a command of the coding agents: their present routes are
     * correct and must not move (owner turns of the lanes, missions, chains, understanding, voice and the model sets).
     */
    @Test fun noUtteranceOfTheOtherSetsIsTaken() {
        val moved = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String) {
            checked++
            CodingAgentOrderParser.parse(utterance, ASSISTANT, DEFAULT_REPOSITORIES)?.let {
                moved += "$source „$utterance“ → ${CodingAgentOrderParser.code(it)}"
            }
        }
        root.listFiles { file -> file.name.endsWith(".jsonl") && !file.name.startsWith("regression-coding-agents") }!!.sortedBy { it.name }.forEach { file ->
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
        assertTrue("${moved.size} existing utterances would become a coding-agent command:\n" + moved.joinToString("\n"), moved.isEmpty())
    }

    /** Separate development and check data: no sentence of one set appears in the other, and every id is unique. */
    @Test fun theDevelopmentAndTheCheckSetShareNoSentence() {
        fun inputs(name: String) = cases(name).map { it.getString("input").trim().lowercase() }.toSet()
        val shared = inputs(DEVELOPMENT) intersect inputs(CHECK)
        assertTrue(shared.toString(), shared.isEmpty())
        val ids = (cases(DEVELOPMENT) + cases(CHECK)).map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
    }

    private companion object {
        const val DEVELOPMENT = "regression-coding-agents.jsonl"
        const val CHECK = "regression-coding-agents-check.jsonl"
        const val ASSISTANT = "Jarvis"
        /** The allow-list of every case that does not bring its own (`repos`). */
        val DEFAULT_REPOSITORIES = listOf("Ullmann27/device-ai", "Ullmann27/continuum-web")
        val KNOWN_CHECK_MISSES: Set<String> = emptySet()
    }
}
