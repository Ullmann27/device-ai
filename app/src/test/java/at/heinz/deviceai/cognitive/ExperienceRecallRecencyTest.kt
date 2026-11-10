package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ExperienceRecallRecencyTest {
    private val now = 4_000_000_000L
    private val task = "repair battery service"
    private fun past(
        id: String,
        at: Long = now - 1_000,
        success: Boolean = true,
        request: String = task
    ) = Experience(
        id = id, taskType = "agent_task", requestSummary = request,
        sourceIds = if (success) listOf("tool-call:probe:sha256:" + "a".repeat(64)) else emptyList(),
        toolIds = listOf("get_device_info"), failures = if (success) emptyList() else listOf("Observed failure"),
        outcome = if (success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
        startedAt = at - 100, completedAt = at, evaluation = Evaluation(success, if (success) 0.95 else 0.1))

    @Test fun recentExperienceIsAValidPositiveControl() {
        val store = InMemoryExperienceStore()
        store.store(past("z-current"))
        assertEquals(1, AgentExperienceRecall.hints(store, task, now).size)
    }

    @Test fun equallyRelevantExpiredMatchesCannotHideCurrentExperience() {
        val store = InMemoryExperienceStore()
        repeat(64) { store.store(past("a-stale-$it", now - 31L * 24 * 60 * 60 * 1000)) }
        store.store(past("z-current"))
        assertEquals(1, AgentExperienceRecall.hints(store, task, now).size)
    }

    @Test fun equallyRelevantOlderSuccessesCannotHideLatestFailureWarning() {
        val store = InMemoryExperienceStore()
        repeat(64) { store.store(past("a-success-$it", now - 2_000)) }
        store.store(past("z-failure", now - 1_000, success = false))
        val hints = AgentExperienceRecall.hints(store, task, now)
        assertEquals(1, hints.count { "failure-warning" in it.tags })
        assertEquals(2, hints.count { "success-hint" in it.tags })
        assertTrue(hints.all { it.validationState == ValidationState.UNVERIFIED })
    }

    @Test fun lexicalRelevanceRemainsPrimary() {
        val store = InMemoryExperienceStore()
        store.store(past("z-relevant", now - 20_000))
        store.store(past("a-newer", now - 1_000, request = "repair unrelated display"))
        assertEquals(listOf("z-relevant", "a-newer"), store.similar("agent_task", task, 2).map { it.id })
    }

    @Test fun identicalScoreAndTimeKeepStableIdOrdering() {
        val first = InMemoryExperienceStore()
        val second = InMemoryExperienceStore()
        val records = listOf(past("z"), past("a"), past("m"))
        records.forEach(first::store)
        records.reversed().forEach(second::store)
        assertEquals(listOf("a", "m", "z"), first.similar("agent_task", task, 3).map { it.id })
        assertEquals(first.similar("agent_task", task, 3), second.similar("agent_task", task, 3))
    }

    @Test fun completedTimeBreaksOnlyRelevanceTies() {
        val store = InMemoryExperienceStore()
        store.store(past("a-old", now - 3_000))
        store.store(past("z-new", now - 1_000))
        store.store(past("m-middle", now - 2_000))
        assertEquals(listOf("z-new", "m-middle", "a-old"), store.similar("agent_task", task, 3).map { it.id })
    }

    @Test fun futureAndInvalidEvidenceDoNotBecomeHints() {
        val store = InMemoryExperienceStore()
        store.store(past("future", now + 10_000))
        store.store(past("no-receipt").copy(sourceIds = emptyList()))
        store.store(past("partial").copy(outcome = ExperienceOutcome.PARTIAL))
        store.store(past("low-score").copy(evaluation = Evaluation(true, 0.1)))
        assertTrue(AgentExperienceRecall.hints(store, task, now).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            past("invalid-time").copy(completedAt = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            past("invalid-score").copy(evaluation = Evaluation(true, Double.NaN))
        }
    }

    @Test fun retrievalCapsAndZeroLimitAreUnchanged() {
        val store = InMemoryExperienceStore()
        repeat(100) { store.store(past("entry-$it", now - 2000 + it)) }
        assertEquals(64, store.similar("agent_task", task, 64).size)
        assertTrue(store.similar("agent_task", task, 0).isEmpty())
        assertEquals(2, AgentExperienceRecall.hints(store, task, now).size)
    }

    @Test fun higherRelevanceExpiredRecordsRemainKnownLimitation() {
        val store = InMemoryExperienceStore()
        repeat(64) { store.store(past("old-$it", now - 31L * 24 * 60 * 60 * 1000)) }
        store.store(past("current", request = "$task extra detail"))
        assertTrue(AgentExperienceRecall.hints(store, task, now).isEmpty())
    }
}
