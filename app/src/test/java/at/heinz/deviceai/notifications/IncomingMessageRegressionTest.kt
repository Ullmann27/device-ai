package at.heinz.deviceai.notifications

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `regression-incoming.jsonl` (development set) and `regression-incoming-check.jsonl` (held-out check set) of
 * „Eingehende Nachrichten melden“: sender, subject, text and app of an invented message → reported, not reported or
 * unclear, with the reason. Executed against the productive [IncomingMessageClassifier]. The check set was written
 * and committed before the classifier and is not used for tuning (docs/evaluation/continuum-eval-v1/README.md); its
 * score is pinned here as it is.
 */
class IncomingMessageRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun list(case: JSONObject, key: String): List<String> = case.optJSONArray(key)?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    } ?: emptyList()

    private fun decided(case: JSONObject): ImportanceDecision {
        val app = case.getString("package")
        return IncomingMessageClassifier.classify(
            ImportanceInput(IncomingMessageClassifier.kindOf(app, null), case.getString("sender"), case.getString("subject"),
                case.getString("text"), case.optString("conversation"), case.optBoolean("contact"), case.optBoolean("group")),
            ImportanceRules(list(case, "always"), list(case, "never"), case.optBoolean("groups_on")))
    }

    private fun expected(case: JSONObject): String = case.getJSONObject("expect").let { it.getString("verdict") + "/" + it.getString("reason") }

    /** Ids of the cases of [name] that are decided differently, with what was decided instead. */
    private fun misses(name: String): Map<String, String> {
        val all = cases(name)
        val misses = LinkedHashMap<String, String>()
        all.forEach { case ->
            assertEquals("incoming_importance", case.getString("runner"))
            val got = decided(case).let { "${it.verdict}/${it.reason}" }
            if (got != expected(case)) misses[case.getString("id")] =
                "decided $got, expected ${expected(case)} · ${case.getString("sender")} · ${case.getString("subject")}"
        }
        println("$name: ${all.size - misses.size} / ${all.size} cases hold")
        return misses
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealClassifier() {
        val total = cases("regression-incoming.jsonl").size
        assertTrue("development set unexpectedly small: $total", total >= 120)
        val misses = misses("regression-incoming.jsonl")
        if (misses.isNotEmpty()) fail("${misses.size} of $total development cases failed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /**
     * The held-out set is measured, not tuned: [KNOWN_CHECK_MISSES] lists the cases the classifier does not get right
     * today, so the score in the README stays true and a new miss – or a silently changed one – fails this test.
     */
    @Test fun theHeldOutCheckSetKeepsItsMeasuredScore() {
        val total = cases("regression-incoming-check.jsonl").size
        assertTrue("check set unexpectedly small: $total", total >= 75)
        val misses = misses("regression-incoming-check.jsonl")
        assertEquals("held-out misses changed:\n" + misses.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            KNOWN_CHECK_MISSES, misses.keys.toSet())
    }

    /** The owner's order in one line each: courts, authorities and institutes are reported, advertising never is. */
    @Test fun noCourtOrAuthorityIsSilentAndNoAdvertisingIsReportedInEitherSet() {
        listOf("regression-incoming.jsonl", "regression-incoming-check.jsonl").forEach { name ->
            cases(name).forEach { case ->
                val expect = case.getJSONObject("expect")
                val got = decided(case)
                val id = case.getString("id")
                if (expect.getString("reason") in setOf("AUTHORITY", "INSTITUTION", "OFFICIAL_MATTER"))
                    assertTrue("$id must not be silent: ${got.reason}", got.verdict != ImportanceVerdict.SILENT)
                if (expect.getString("reason") in setOf("PROMOTION", "SOCIAL_DIGEST") && id !in KNOWN_CHECK_MISSES)
                    assertEquals("$id must stay silent: ${got.reason}", ImportanceVerdict.SILENT, got.verdict)
            }
        }
    }

    /** Every decision explains itself in one German sentence; the deciding word is named and bounded. */
    @Test fun everyDecisionCarriesAReasonTheOwnerCanBeTold() {
        (cases("regression-incoming.jsonl") + cases("regression-incoming-check.jsonl")).forEach { case ->
            val got = decided(case)
            assertTrue(case.getString("id"), got.explanation.length in 20..220 && got.explanation.endsWith("."))
            assertTrue(case.getString("id"), got.cue.length <= 48)
            if (got.reason in setOf(ImportanceReason.AUTHORITY, ImportanceReason.INSTITUTION, ImportanceReason.OFFICIAL_MATTER,
                    ImportanceReason.PROMOTION, ImportanceReason.SOCIAL_DIGEST, ImportanceReason.OWNER_ALWAYS, ImportanceReason.OWNER_NEVER))
                assertTrue(case.getString("id") + ": the deciding word is named", got.cue.isNotEmpty() && got.explanation.contains(got.cue))
        }
    }

    /** Separate development and check data, well-formed lines, and only invented addresses. */
    @Test fun theSetsAreWellFormedAndShareNoCase() {
        fun keys(name: String) = cases(name).map { listOf(it.getString("package"), it.getString("sender"), it.getString("subject"),
            it.getString("text"), list(it, "always"), list(it, "never"), it.optBoolean("groups_on")).toString().lowercase() }
        val development = keys("regression-incoming.jsonl")
        val check = keys("regression-incoming-check.jsonl")
        assertEquals(development.size, development.toSet().size)
        assertEquals(check.size, check.toSet().size)
        assertTrue((development.toSet() intersect check.toSet()).toString(), (development.toSet() intersect check.toSet()).isEmpty())
        val all = cases("regression-incoming.jsonl") + cases("regression-incoming-check.jsonl")
        assertEquals(all.size, all.map { it.getString("id") }.toSet().size)
        all.forEach { case ->
            val id = case.getString("id")
            assertEquals(id, "incoming", case.getString("category"))
            assertTrue(id, case.has("note"))
            assertTrue(id, case.getJSONObject("expect").getString("verdict") in ImportanceVerdict.entries.map { it.name })
            assertTrue(id, case.getJSONObject("expect").getString("reason") in ImportanceReason.entries.map { it.name })
            // Invented data only: an address is a reserved example domain or an authority's function mailbox.
            Regex("[\\w.+-]+@([\\w.-]+)").findAll(case.getString("sender") + " " + case.getString("text")).forEach {
                assertTrue("$id: ${it.value}", it.groupValues[1].endsWith(".example") || it.groupValues[1].endsWith("gv.at"))
            }
            Regex("\\+43[\\d ]+").findAll(case.getString("sender")).forEach { assertTrue("$id: ${it.value}", " 000000" in it.value) }
        }
    }

    /** The README names both files with their real case counts and the measured scores. */
    @Test fun theReadmeRowsMatchTheFiles() {
        val readme = File(root, "README.md").readText(Charsets.UTF_8)
        listOf("regression-incoming.jsonl", "regression-incoming-check.jsonl").forEach { name ->
            assertTrue(name, readme.contains("| `$name` | ${cases(name).size} | **Host, automatisch** (`IncomingMessageRegressionTest`)"))
        }
        val development = cases("regression-incoming.jsonl").size
        val check = cases("regression-incoming-check.jsonl").size
        assertTrue(readme.contains("| $development / $development | ${check - KNOWN_CHECK_MISSES.size} / $check |"))
        assertTrue(readme.contains("`incoming_importance`"))
    }

    private companion object {
        val KNOWN_CHECK_MISSES: Set<String> = emptySet()
    }
}
