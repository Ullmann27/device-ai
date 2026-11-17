package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Evidence replay must not turn one successful attempt into a learned skill. */
class ExperienceEvidenceRegressionTest {
    private fun experience(id: String, success: Boolean = true) = Experience(
        id = id, taskType = "DEVICE-INFO", requestSummary = "Check device health",
        toolIds = listOf("get_device_info"), outcome = if (success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
        startedAt = 100, completedAt = 200, evaluation = Evaluation(success, if (success) 0.95 else 0.1)
    )

    @Test fun replayedSuccessCannotMeetOccurrenceThreshold() {
        val one = experience("one")
        assertTrue(ExperienceCompiler().compile(List(3) { one }).isEmpty())
    }

    @Test fun replayDoesNotInflateSuccessRateOrMeanScore() {
        val one = experience("one")
        val evidence = List(20) { one } + listOf(experience("two", false), experience("three", false))
        assertTrue(ExperienceCompiler().compile(evidence).isEmpty())
    }

    @Test fun conflictingIdentityFailsClosedBeforeEligibilityFiltering() {
        val one = experience("one")
        val conflicting = one.copy(toolIds = listOf("unregistered_tool"), resultSummary = "altered")
        assertThrows(SecurityException::class.java) {
            ExperienceCompiler().compile(listOf(one, experience("two"), experience("three"), conflicting))
        }
    }

    @Test fun uniqueExperiencesRemainEligibleButAlwaysRequireOwnerReview() {
        val records = (1..3).map { experience("e$it") }
        val candidate = ExperienceCompiler().compile(records + records).single()
        assertEquals(3, candidate.occurrenceCount)
        assertEquals(listOf("e1", "e2", "e3"), candidate.sourceExperienceIds)
        assertTrue(candidate.eligibleForOwnerReview)
        assertTrue(candidate.requiresOwnerApproval)
    }

    @Test fun candidateCannotDisableOwnerApproval() {
        val candidate = ExperienceCompiler().compile((1..3).map { experience("e$it") }).single()
        assertThrows(IllegalArgumentException::class.java) { candidate.copy(requiresOwnerApproval = false) }
    }

    @Test fun candidateCannotCarryDuplicatedEvidenceIds() {
        val candidate = ExperienceCompiler().compile((1..3).map { experience("e$it") }).single()
        assertThrows(IllegalArgumentException::class.java) {
            candidate.copy(sourceExperienceIds = listOf("e1", "e1", "e1"))
        }
    }

    @Test fun candidateIdentityDoesNotDependOnDeviceLocale() {
        val before = Locale.getDefault()
        val records = (1..3).map { experience("e$it") }
        try {
            Locale.setDefault(Locale.US)
            val expected = ExperienceCompiler().compile(records).single().candidateId
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(expected, ExperienceCompiler().compile(records).single().candidateId)
        } finally { Locale.setDefault(before) }
    }

    @Test fun destructiveSequenceRemainsBlocked() {
        val records = (1..3).map { experience("e$it").copy(toolIds = listOf("delete_file")) }
        val candidate = ExperienceCompiler().compile(records).single()
        assertFalse(candidate.eligibleForOwnerReview)
        assertTrue(candidate.requiresOwnerApproval)
        assertNotNull(candidate.promotionBlockedReason)
    }
}
