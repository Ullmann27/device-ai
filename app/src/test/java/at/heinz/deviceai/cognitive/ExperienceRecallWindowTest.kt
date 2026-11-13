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

    @Test fun windowIncludesBothEndpointsAndExcludesAdjacentTimes() {
        val store = InMemoryExperienceStore()
        store.store(past("before", now - age - 1))
        store.store(past("lower", now - age))
        store.store(past("upper", now))
        store.store(past("after", now + 1))
        assertEquals(listOf("upper", "lower"),
            store.similarWithin("agent_task", task, now - age, now, 2).map { it.id })
        assertEquals(listOf(now, now - age),
            AgentExperienceRecall.hints(store, task, now).map { it.updatedAt })
    }

    @Test fun windowPreservesRelevanceRecencyIdsAndLimit() {
        val store = InMemoryExperienceStore()
        store.store(past("z-relevant-old", now - 10))
        store.store(past("z-equal-new", now - 1))
        store.store(past("a-equal-new", now - 1))
        store.store(past("a-less-relevant", now, "$task additional detail"))
        store.store(past("wrong-type", now).copy(taskType = "other"))
        assertEquals(listOf("a-equal-new", "z-equal-new", "z-relevant-old", "a-less-relevant"),
            store.similarWithin("agent_task", task, now - age, now, 5).map { it.id })
        assertEquals(listOf("a-equal-new"),
            store.similarWithin("agent_task", task, now - age, now, 1).map { it.id })
    }

    @Test fun eligibilityPrecedesRankingWithoutChangingOrderingOrLimits() {
        val store = InMemoryExperienceStore()
        repeat(96) { store.store(past("drop-$it", now)) }
        store.store(past("keep-z", now - 1))
        store.store(past("keep-a", now - 1))
        store.store(past("keep-old", now - 10))
        store.store(past("keep-less-relevant", now, "$task extra detail"))
        val matches = store.similarWithin("agent_task", task, now - age, now, 3) {
            it.id.startsWith("keep-")
        }
        assertEquals(listOf("keep-a", "keep-z", "keep-old"), matches.map { it.id })
        assertEquals(64, store.similarWithin("agent_task", task, now - age, now, 64) { true }.size)
    }

    @Test fun invalidWindowAndNegativeLimitsAreRejected() {
        val store = InMemoryExperienceStore()
        val legacy = object : ExperienceLookup {
            override fun similar(taskType: String, request: String, limit: Int): List<Experience> =
                error("invalid inputs must not reach provider")
        }
        for (lookup in listOf<ExperienceLookup>(store, legacy)) {
            assertThrows(IllegalArgumentException::class.java) {
                lookup.similarWithin("agent_task", task, 0, now, -1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                lookup.similarWithin("agent_task", task, -1, now, 1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                lookup.similarWithin("agent_task", task, now, now - 1, 1)
            }
            assertTrue(lookup.similarWithin("agent_task", task, 0, now, 0).isEmpty())
            assertThrows(IllegalArgumentException::class.java) {
                lookup.similarWithin("agent_task", task, 0, now, -1) { true }
            }
            assertThrows(IllegalArgumentException::class.java) {
                lookup.similarWithin("agent_task", task, -1, now, 1) { true }
            }
            assertThrows(IllegalArgumentException::class.java) {
                lookup.similarWithin("agent_task", task, now, now - 1, 1) { true }
            }
            assertTrue(lookup.similarWithin("agent_task", task, 0, now, 0) {
                error("zero limit must not evaluate candidates")
            }.isEmpty())
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.similar("agent_task", task, -1)
        }
    }

    @Test fun legacyDefaultFiltersBoundedShortlistWithoutOverscan() {
        val records = listOf(past("future", now + 1), past("expired", now - age - 1), past("valid", now))
        val requestedLimits = mutableListOf<Int>()
        val legacy = object : ExperienceLookup {
            override fun similar(taskType: String, request: String, limit: Int): List<Experience> {
                requestedLimits += limit
                return records.take(limit)
            }
        }
        assertTrue(legacy.similarWithin("agent_task", task, now - age, now, 2).isEmpty())
        assertEquals(listOf("valid"), legacy.similarWithin("agent_task", task, now - age, now, 3).map { it.id })
        assertEquals(listOf(2, 3), requestedLimits)
    }

    @Test fun eligibilityFallbackKeepsLegacyRequestAndInspectionBounded() {
        val records = listOf(past("skip-1", now), past("skip-2", now), past("keep", now))
        val requestedLimits = mutableListOf<Int>()
        val inspected = mutableListOf<String>()
        val legacy = object : ExperienceLookup {
            override fun similar(taskType: String, request: String, limit: Int): List<Experience> =
                error("window API required")

            override fun similarWithin(
                taskType: String, request: String,
                completedAtFrom: Long, completedAtThrough: Long, limit: Int
            ): List<Experience> {
                requestedLimits += limit
                return records
            }
        }
        assertTrue(legacy.similarWithin("agent_task", task, now - age, now, 2) {
            inspected += it.id
            it.id == "keep"
        }.isEmpty())
        assertEquals(listOf("skip-1", "skip-2"), inspected)
        assertEquals(listOf("keep"), legacy.similarWithin("agent_task", task, now - age, now, 3) {
            it.id == "keep"
        }.map { it.id })
        assertEquals(listOf(2, 3), requestedLimits)
    }

    @Test fun recallRevalidatesUncooperativeProviderAndCapsReturnedCandidates() {
        val valid = past("valid", now - 1)
        val failure = valid.copy(id = "failure", outcome = ExperienceOutcome.FAILURE,
            evaluation = Evaluation(false, 0.1), failures = listOf("Observed verification failure"))
        var returned = listOf(
            valid.copy(id = "partial", outcome = ExperienceOutcome.PARTIAL),
            valid.copy(id = "assumed", sourceIds = emptyList()),
            valid.copy(id = "low-score", evaluation = Evaluation(true, 0.4)),
            valid.copy(id = "no-tools", toolIds = emptyList()),
            valid.copy(id = "bad-digest", sourceIds = listOf("model:assumed-success")),
            valid.copy(id = "wrong-type", taskType = "owner_fact"),
            past("irrelevant", now, "different task"),
            past("future", now + 1),
            past("expired", now - age - 1),
            failure.copy(id = "failure-without-observation", failures = emptyList()),
            valid, failure
        )
        val lookup = object : ExperienceLookup {
            override fun similar(taskType: String, request: String, limit: Int): List<Experience> =
                error("eligibility API required")

            override fun similarWithin(
                taskType: String, request: String,
                completedAtFrom: Long, completedAtThrough: Long,
                limit: Int, eligible: (Experience) -> Boolean
            ): List<Experience> {
                assertEquals(64, limit)
                return returned
            }
        }
        val hints = AgentExperienceRecall.hints(lookup, task, now)
        assertEquals(2, hints.size)
        assertEquals(1, hints.count { "success-hint" in it.tags })
        assertEquals(1, hints.count { "failure-warning" in it.tags })
        assertTrue(hints.all { it.validationState == ValidationState.UNVERIFIED })
        returned = List(64) { valid.copy(id = "partial-$it", outcome = ExperienceOutcome.PARTIAL) } + valid
        assertTrue(AgentExperienceRecall.hints(lookup, task, now).isEmpty())
    }

    @Test fun recallSuppliesExplicitWindowAtEpochAndLongMaximum() {
        for (clock in listOf(0L, age - 1, now, Long.MAX_VALUE)) {
            var calls = 0
            val lookup = object : ExperienceLookup {
                override fun similar(taskType: String, request: String, limit: Int): List<Experience> =
                    error("window API required")

                override fun similarWithin(
                    taskType: String, request: String,
                    completedAtFrom: Long, completedAtThrough: Long, limit: Int
                ): List<Experience> {
                    calls++
                    assertEquals("agent_task", taskType)
                    assertEquals(task, request)
                    assertEquals((clock - age).coerceAtLeast(0L), completedAtFrom)
                    assertEquals(clock, completedAtThrough)
                    assertEquals(64, limit)
                    return listOf(past("at-clock", clock))
                }
            }
            assertEquals(clock, AgentExperienceRecall.hints(lookup, task, clock).single().updatedAt)
            assertEquals(1, calls)
        }
    }

    @Test fun recallStillRejectsOutOfWindowProviderResults() {
        val lookup = object : ExperienceLookup {
            override fun similar(taskType: String, request: String, limit: Int): List<Experience> =
                error("window API required")

            override fun similarWithin(
                taskType: String, request: String,
                completedAtFrom: Long, completedAtThrough: Long, limit: Int
            ) = listOf(past("future", now + 1), past("expired", now - age - 1))
        }
        assertTrue(AgentExperienceRecall.hints(lookup, task, now).isEmpty())
    }
}
