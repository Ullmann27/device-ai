package at.heinz.deviceai.chief

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog/oracle checks only. No test here is an Iris response, training result or device proof. */
class ChiefAvatarPracticeCurriculumTest {
    @Test fun sixBoundedIrisCasesHaveFourExercisesAndTwoDistinctTransferCases() {
        val cases = ChiefAvatarPracticeCurriculum.exercises()
        assertEquals(6, cases.size)
        assertEquals(6, cases.map { it.id }.toSet().size)
        assertTrue(cases.all { it.agentId == "bot-iris" })
        assertEquals(4, cases.count { !it.transfer })
        assertEquals(setOf("iris-replacement-transfer-v1", "iris-task-gaze-transfer-v1"),
            cases.filter { it.transfer }.map { it.id }.toSet())
        cases.forEach {
            assertTrue(it.id.matches(Regex("[a-z0-9][a-z0-9._:-]{0,79}")))
            assertTrue(it.title.length in 1..160)
            assertTrue(it.prompt.toByteArray(Charsets.UTF_8).size < 1_800)
            assertTrue(it.explanation.toByteArray(Charsets.UTF_8).size in 1..1_200)
            assertTrue(it.expected.size in 1..8)
        }
    }

    @Test fun promptsContainOnlyEmptyStringSchemasAndNeverTheAnswerOrCorrection() {
        ChiefAvatarPracticeCurriculum.exercises().forEach { case ->
            val rules = case.prompt.substringAfter("\nVERTRAG\n", "").substringBefore("\nEREIGNISSE\n")
            assertTrue(case.id + " lacks general contract rules", rules.length >= 80)
            val schemaText = case.prompt.substringAfter("\nANTWORTSCHEMA\n", "")
            assertTrue(case.id, schemaText.isNotBlank())
            val schema = JSONObject(schemaText)
            assertEquals(case.id, case.expected.keys, schema.keys().asSequence().toSet())
            case.expected.forEach { (key, value) ->
                assertTrue(key.matches(Regex("[a-z][a-z0-9_]{0,39}")))
                assertTrue(value.length in 1..160)
                assertEquals(case.id + "/" + key, "", schema.getString(key))
                assertFalse(case.id + " leaks a gold field", case.prompt.contains("\"$key\":\"$value\""))
            }
            assertFalse(case.id + " leaks feedback", case.prompt.contains(case.explanation))
        }
    }

    @Test fun priorityOraclePreservesTheExistingStateAndExpressionContract() {
        assertEquals(mapOf(
            "work_state" to "THINKING", "work_expression" to "FOCUSED",
            "approval_state" to "WAITING_FOR_APPROVAL", "approval_expression" to "ATTENTIVE",
            "final_state" to "IDLE",
        ), answers("iris-state-priority-v1"))
    }

    @Test fun interruptionClosesMouthWithoutInventingARuntimeStateChange() {
        assertEquals(mapOf(
            "mouth_before" to "true", "mouth_after" to "false", "energy_after" to "false",
            "viseme_after" to "SIL", "state_after" to "SPEAKING",
        ), answers("iris-speech-interruption-v1"))
    }

    @Test fun exactGazeExpiryIsIncludedButTheNextMillisecondAndLowConfidenceAreRejected() {
        assertEquals(mapOf(
            "accepted" to "true", "observed_at_limit" to "true",
            "observed_after_limit" to "false", "weak_accepted" to "false",
        ), answers("iris-gaze-expiry-v1"))
    }

    @Test fun revocationInvalidatesTheTokenAndCannotBeUndoneByAFreshLateFrame() {
        assertEquals(mapOf(
            "before_accepted" to "true", "gate_still_open" to "false", "late_accepted" to "false",
            "state" to "PERMISSION_REQUIRED", "sample_present" to "false",
        ), answers("iris-camera-revocation-v1"))
    }

    @Test fun replacementTransferKeepsNewSpeechAliveAndDropsEveryOldCallback() {
        assertEquals(mapOf(
            "old_events_emitted" to "false", "mouth_open" to "true",
            "energy_active" to "true", "state" to "SPEAKING",
        ), answers("iris-replacement-transfer-v1"))
        assertEquals("false", answers("iris-speech-interruption-v1")["mouth_after"])
    }

    @Test fun taskGazeTransferRejectsEvenFreshHighConfidenceObservation() {
        assertEquals(mapOf(
            "accepted_before" to "true", "accepted_during_task" to "false",
            "gaze_observed" to "false", "state" to "LEARNING", "expression" to "FOCUSED",
            "task_gaze" to "true",
        ), answers("iris-task-gaze-transfer-v1"))
    }

    @Test fun repeatedCatalogConstructionHasNoRetainedClockSpeechOrCameraState() {
        val first = ChiefAvatarPracticeCurriculum.exercises()
        repeat(3) {
            val next = ChiefAvatarPracticeCurriculum.exercises()
            assertNotSame(first, next)
            assertEquals(first.map { it.id to it.expected }, next.map { it.id to it.expected })
            assertEquals(first.map { it.prompt }, next.map { it.prompt })
        }
    }

    private fun answers(id: String): Map<String, String> =
        ChiefAvatarPracticeCurriculum.exercises().single { it.id == id }.expected
}
