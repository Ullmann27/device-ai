package at.heinz.deviceai.chief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owner-facing Chief dispatch: one specialist, least authority, exactly one report, proof-gated learning. */
class ChiefDelegationCoordinatorTest {
    private fun specialist(id: String, capability: String = "coding", tools: Set<String> = emptySet()) =
        ChiefSpecialist(id, setOf(capability), tools)

    @Test fun newSpecialistsRequireTrustedOwnerApprovalAndAreCappedAtTwenty() {
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { "ok" } }
        try {
            val denied = ChiefDelegationCoordinator(pool)
            assertFalse(denied.register(specialist("a")))
            assertEquals(emptyList<ChiefSpecialist>(), denied.registeredSpecialists())

            val chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
            repeat(20) { index -> assertTrue(chief.register(specialist("w$index"))) }
            assertFalse(chief.register(specialist("w20")))
            assertFalse(chief.register(specialist("w0")))
            assertEquals(20, chief.registeredSpecialists().size)
        } finally {
            pool.shutdown()
        }
    }

    @Test fun chiefDelegatesOnlyToQualifiedAuthorizedSpecialistAndReportsToChief() {
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        lateinit var chief: ChiefDelegationCoordinator
        val assigned = ConcurrentLinkedQueue<String>()
        val pool = ChiefWorkerPool(1, 1) { brief ->
            val worker = chief.specialistForTask(brief.id)!!
            WorkerJob { assigned += worker.id; "done by ${worker.id}" }
        }
        try {
            chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
            assertTrue(chief.register(specialist("memory", "memory", setOf("get_storage_info"))))
            assertTrue(chief.register(specialist("coding", "coding", setOf("get_battery_info"))))
            val request = ChiefDelegation("task1", "Batterie prüfen", "coding", setOf("get_battery_info"))
            assertEquals(ChiefDispatchOutcome.SUBMITTED, chief.delegate(request) { reports += it })
            assertEquals(ChiefDispatchOutcome.DUPLICATE_TASK, chief.delegate(request) { reports += it })
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(listOf("coding"), assigned.toList())
            assertEquals(WorkerStatus.DONE, reports.single().status)
            assertEquals("done by coding", reports.single().summary)
            assertEquals(ChiefDispatchOutcome.TOOL_SCOPE_REJECTED,
                chief.delegate(ChiefDelegation("task2", "Daten löschen", "coding", setOf("delete_file"))) { })
            assertEquals(ChiefDispatchOutcome.NO_SPECIALIST,
                chief.delegate(ChiefDelegation("task3", "Gesundheit", "medical")) { })
            assertTrue(chief.activeAssignments().isEmpty())
        } finally {
            pool.shutdown()
        }
    }

    @Test fun unverifiedWorkerClaimsNeverTrainReliabilityAndValidProofUpdatesOnce() {
        val reported = ConcurrentLinkedQueue<WorkerReport>()
        lateinit var chief: ChiefDelegationCoordinator
        val selected = ConcurrentLinkedQueue<String>()
        val pool = ChiefWorkerPool(1, 0) { brief ->
            val worker = chief.specialistForTask(brief.id)!!
            WorkerJob { selected += worker.id; "done" }
        }
        try {
            chief = ChiefDelegationCoordinator(
                pool = pool, approveSpecialist = { true },
                verifyOutcome = { proof, run -> proof.receiptSha256.startsWith("a") && run.id == proof.taskId })
            assertTrue(chief.register(specialist("alpha")))
            assertTrue(chief.register(specialist("beta")))
            assertEquals(ChiefDispatchOutcome.SUBMITTED,
                chief.delegate(ChiefDelegation("first", "Code prüfen", "coding")) { reported += it })
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(listOf("alpha"), selected.toList())
            assertEquals(ChiefSpecialistStats(0, 0), chief.stats("alpha"))

            val good = ChiefOutcomeEvidence("first", "a".repeat(64), false)
            assertFalse("different receipt rejected by independent verifier",
                chief.recordVerifiedOutcome(ChiefOutcomeEvidence("first", "b".repeat(64), false)))
            assertTrue(chief.recordVerifiedOutcome(good))
            assertFalse("same report must never train twice", chief.recordVerifiedOutcome(good))
            assertFalse("different hash for same task still must not train twice",
                chief.recordVerifiedOutcome(ChiefOutcomeEvidence("first", "a".repeat(63) + "1", false)))
            assertEquals(ChiefSpecialistStats(0, 1), chief.stats("alpha"))

            assertEquals(ChiefDispatchOutcome.SUBMITTED,
                chief.delegate(ChiefDelegation("second", "Code prüfen", "coding")) { reported += it })
            assertTrue(pool.awaitIdle(2_000))
            assertEquals(listOf("alpha", "beta"), selected.toList())
            assertEquals(2, reported.size)
        } finally {
            pool.shutdown()
        }
    }

    @Test fun busyWorkerDoesNotRunTwiceAndUnknownToolsDoNotEscalate() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = ChiefWorkerPool(1, 0) { WorkerJob {
            started.countDown()
            release.await(2, TimeUnit.SECONDS)
            "ok"
        } }
        try {
            val chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
            assertTrue(chief.register(specialist("only", "memory", setOf("get_storage_info"))))
            assertEquals(ChiefDispatchOutcome.SUBMITTED,
                chief.delegate(ChiefDelegation("one", "Prüfe Speicher", "memory")) { })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertEquals(ChiefDispatchOutcome.ALL_BUSY,
                chief.delegate(ChiefDelegation("two", "Prüfe Speicher erneut", "memory")) { })
            assertEquals(ChiefDispatchOutcome.TOOL_SCOPE_REJECTED,
                chief.delegate(ChiefDelegation("three", "Lösche alles", "memory", setOf("delete_file"))) { })
            release.countDown()
            assertTrue(pool.awaitIdle(2_000))
        } finally {
            release.countDown()
            pool.shutdown()
        }
    }
}
