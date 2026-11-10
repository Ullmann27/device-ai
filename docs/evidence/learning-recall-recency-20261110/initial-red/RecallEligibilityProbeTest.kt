package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class RecallEligibilityProbeTest {
    private val now = 4_000_000_000L
    private val task = "repair battery service"
    private fun past(id: String, at: Long = now - 1000, success: Boolean = true) = Experience(
        id = id, taskType = "agent_task", requestSummary = task,
        sourceIds = if (success) listOf("tool-call:probe:sha256:" + "a".repeat(64)) else emptyList(),
        toolIds = listOf("get_device_info"), failures = if (success) emptyList() else listOf("Observed failure"),
        outcome = if (success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
        startedAt = at - 100, completedAt = at, evaluation = Evaluation(success, if (success) 0.95 else 0.1))

    @Test fun recentExperienceIsAValidPositiveControl() {
        val store = InMemoryExperienceStore()
        store.store(past("z-current"))
        assertEquals(1, AgentExperienceRecall.hints(store, task, now).size)
    }

    @Test fun expiredMatchesCannotHideCurrentExperience() {
        val store = InMemoryExperienceStore()
        repeat(64) { store.store(past("a-stale-$it", now - 31L * 24 * 60 * 60 * 1000)) }
        store.store(past("z-current"))
        assertEquals("Current eligible experience was excluded before eligibility filtering", 1,
            AgentExperienceRecall.hints(store, task, now).size)
    }

    @Test fun olderSuccessesCannotHideLatestFailureWarning() {
        val store = InMemoryExperienceStore()
        repeat(64) { store.store(past("a-success-$it", now - 2000)) }
        store.store(past("z-failure", now - 1000, success = false))
        assertEquals("Latest similar failure was outside the ID-ordered cutoff", 1,
            AgentExperienceRecall.hints(store, task, now).count { "failure-warning" in it.tags })
    }
}
