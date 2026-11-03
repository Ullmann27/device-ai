package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ChiefWorkspaceWiringTest {
    private fun scope(worker: String = "worker-alpha") = WorkspaceTaskScope(
        worker, "t-" + "a".repeat(16), "b".repeat(40),
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "c".repeat(64),
        "dddddddd-dddd-4ddd-8ddd-dddddddddddd", "node-continuum", 256, 1, 64, 1_000)
    private fun brief(scope: WorkspaceTaskScope) = WorkerBrief(ChiefWorkspaceWiring.briefId(scope), "synthetic bounded host profile", emptySet(), 2_000)
    private fun observation(scope: WorkspaceTaskScope) = WorkspaceObservation(scope,
        "continuum-" + "e".repeat(24), "f".repeat(64), true, 0)

    @Test fun existingPoolSendsCorrelatedObservationThroughExistingMailbox() {
        val scope = scope()
        val mailbox = AgentMailbox()
        val deliveries = ConcurrentLinkedQueue<WorkspaceDelivery>()
        val delivered = CountDownLatch(1)
        val port = object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope) = observation(scope)
            override fun cancel(scope: WorkspaceTaskScope) = Unit
        }
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { deliveries += it; delivered.countDown() })
        val pool = ChiefWorkerPool(1, 0) { ChiefWorkspaceWiring.job(it, scope, port) }
        try {
            assertTrue(pool.submit(brief(scope), sink))
            assertTrue(pool.awaitIdle(5_000))
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            val message = mailbox.drain("chief").single()
            assertEquals("workspace:" + scope.workerId, message.from)
            assertTrue(message.body.contains(scope.taskId))
            assertTrue(message.body.contains("verification=PENDING"))
            assertEquals(WorkspaceDeliveryStatus.DELIVERED, deliveries.single().status)
        } finally { pool.shutdown() }
    }

    @Test fun mismatchedScopeOrMissingTeardownNeverReportsProfileSuccess() {
        val scope = scope()
        for (result in listOf(observation(scope).copy(scope = scope("worker-other")), observation(scope).copy(teardownConfirmed = false))) {
            val port = object : ChiefWorkspacePort {
                override fun execute(scope: WorkspaceTaskScope) = result
                override fun cancel(scope: WorkspaceTaskScope) = Unit
            }
            assertThrows(IllegalStateException::class.java) { ChiefWorkspaceWiring.job(brief(scope), scope, port).run() }
        }
        assertThrows(IllegalArgumentException::class.java) { scope.copy(sourceCommit = "main") }
        assertThrows(IllegalArgumentException::class.java) { scope.copy(profile = "free-shell") }
        assertThrows(IllegalArgumentException::class.java) { scope.copy(memoryMb = 99_999) }
        assertThrows(IllegalArgumentException::class.java) { ChiefWorkspaceWiring.job(brief(scope("worker-other").copy(jobId="eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee")), scope, object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope) = observation(scope)
            override fun cancel(scope: WorkspaceTaskScope) = Unit
        }) }
    }

    @Test fun failedProfileAndRepeatedJobNeverBecomeDoneObservations() {
        val scope = scope()
        val port = object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope) = observation(scope).copy(profileExitCode = 2)
            override fun cancel(scope: WorkspaceTaskScope) = Unit
        }
        val job = ChiefWorkspaceWiring.job(brief(scope), scope, port)
        assertThrows(WorkerBlocked::class.java) { job.run() }
        assertThrows(IllegalStateException::class.java) { job.run() }
    }

    @Test fun cancellationClosesTrustedPortAndStopsBeforeSubsequentExecution() {
        val scope = scope()
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val calls = AtomicInteger()
        val port = object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope): WorkspaceObservation {
                calls.incrementAndGet(); started.countDown()
                cancelled.await(5, TimeUnit.SECONDS)
                throw java.util.concurrent.CancellationException()
            }
            override fun cancel(scope: WorkspaceTaskScope) { cancelled.countDown() }
        }
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { ChiefWorkspaceWiring.job(it, scope, port) }
        try {
            assertTrue(pool.submit(brief(scope)) { reports += it })
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(pool.cancel(ChiefWorkspaceWiring.briefId(scope))); assertTrue(pool.awaitIdle(5_000))
            assertEquals(0L, cancelled.count)
            assertEquals(1, calls.get())
            assertEquals(WorkerStatus.CANCELLED, reports.single().status)
        } finally { pool.shutdown() }
        val job = ChiefWorkspaceWiring.job(brief(scope), scope, port)
        job.cancel()
        assertThrows(java.util.concurrent.CancellationException::class.java) { job.run() }
        assertEquals(1, calls.get())
    }

    @Test fun fullMailboxAndMismatchedReportsReachRequiredDeliveryCallback() {
        val scope = scope()
        val mailbox = AgentMailbox(maxPerAgent = 1)
        assertTrue(mailbox.send(AgentMessage("already-full", "fixture", "chief", AgentPriority.INFO, "occupied")))
        val deliveries = ConcurrentLinkedQueue<WorkspaceDelivery>()
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { deliveries += it })
        sink(WorkerReport(ChiefWorkspaceWiring.briefId(scope), WorkerStatus.FAILED, "failed profile", 1))
        sink(WorkerReport("other-worker", WorkerStatus.DONE, "fake scope", 1))
        assertEquals(listOf(WorkspaceDeliveryStatus.MAILBOX_REJECTED, WorkspaceDeliveryStatus.REPORT_SCOPE_MISMATCH), deliveries.map { it.status })
        assertEquals(1, mailbox.pending("chief"))
    }
}
