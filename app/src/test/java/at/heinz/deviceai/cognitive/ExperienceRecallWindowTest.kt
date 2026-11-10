package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ExperienceRecallWindowTest {
    private val now = 4_000_000_000L
    private val age = 30L * 24 * 60 * 60 * 1000
    private val task = "repair battery service"

    private fun past(id: String, at: Long, request: String = task) = Experience(
        id = id, taskType = "agent_task", requestSummary = request,
        sourceIds = listOf("tool-call:probe:sha256:" + "a".repeat(64)),
        toolIds = listOf("get_device_info"), outcome = ExperienceOutcome.SUCCESS,
        startedAt = (at - 100).coerceAtLeast(0), completedAt = at,
        evaluation = Evaluation(true, 0.95)
    )

    @Test fun futureCandidatesCannotDisplaceEligibleRecall() {
        val store = InMemoryExperienceStore()
        store.store(past("a-valid", now - 1))
        repeat(64) { store.store(past("z-future-$it", now + 1)) }
        val hints = AgentExperienceRecall.hints(store, task, now)
        assertEquals(1, hints.size)
        assertEquals(now - 1, hints.single().updatedAt)
        assertEquals(ValidationState.UNVERIFIED, hints.single().validationState)
    }

    @Test fun higherRelevanceExpiredCandidatesCannotDisplaceEligibleRecall() {
        val store = InMemoryExperienceStore()
        repeat(64) { store.store(past("a-expired-$it", now - age - 1)) }
        store.store(past("z-current", now - 1, "$task extra detail"))
        val hints = AgentExperienceRecall.hints(store, task, now)
        assertEquals(1, hints.size)
        assertEquals(now - 1, hints.single().updatedAt)
    }
}
