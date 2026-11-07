package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Admission-contract fixture: authority is synthetic, no durable/OS/model proof. */
class IndependentBotHandoffTest {
    private val task = "t-" + "a".repeat(16)
    private val jobId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val request = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
    private val message = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
    private val hash = "e".repeat(64)
    private val source = "f".repeat(40)

    private fun handoff(id: String = message, from: String = "bot-atlas", models: Int = 1,
                        tools: Set<String> = setOf("fixed-node-test")) = ChiefBotHandoff(
        messageId = id, fromAgentId = from, toAgentId = "bot-forge", stage = ChiefBotStage.BUILD,
        taskId = task, jobId = jobId, sourceCommit = source, attemptId = "attempt-1",
        requestMessageId = request, requestHash = hash, grantFingerprint = hash,
        persistentWorkspaceId = "workspace-bot-forge", expiresAt = 4_000,
        requestedTools = tools, modelCalls = models, toolCalls = 1, evidenceRefs = emptyList())

    // The stricter candidate adds a trusted sender map. Supply the SAME fixed
    // authority when present, never derive it from the submitted message.
    private fun admission(): ChiefBotTaskAdmission {
        val args = arrayListOf<Any>(task, jobId, source, "attempt-1", request, hash, hash,
            true, false, false, "bot-forge", "bot-lint", setOf("fixed-node-test"), 2, 2,
            5_000L, emptySet<String>(), emptyMap<ChiefBotStage, String>())
        val ctor = ChiefBotTaskAdmission::class.java.constructors.single {
            it.parameterCount in 18..19 && it.parameterTypes.last() == Map::class.java
        }
        if (ctor.parameterCount == 19) args += mapOf(ChiefBotStage.BUILD to "bot-atlas", ChiefBotStage.VERIFY to "bot-forge")
        return ctor.newInstance(*args.toTypedArray()) as ChiefBotTaskAdmission
    }

    private inner class Authority : ChiefBotHandoffAuthority {
        @Volatile var state = admission()
        var onClaim: (() -> Unit)? = null
        val claims = AtomicInteger()
        override fun inspect(taskId: String) = if (taskId == task) state else null
        @Synchronized override fun claim(handoff: ChiefBotHandoff): Boolean {
            val s = state
            if (!s.currentGrant || s.terminal || s.unknownEffect || handoff.messageId in s.seenMessageIds ||
                handoff.modelCalls > s.remainingModelCalls || handoff.toolCalls > s.remainingToolCalls) return false
            state = s.copy(remainingModelCalls = s.remainingModelCalls - handoff.modelCalls,
                remainingToolCalls = s.remainingToolCalls - handoff.toolCalls,
                seenMessageIds = s.seenMessageIds + handoff.messageId)
            claims.incrementAndGet(); onClaim?.invoke(); return true
        }
        override fun claimCurrent(handoff: ChiefBotHandoff) = handoff.messageId in state.seenMessageIds
        override fun evidenceCurrent(handoff: ChiefBotHandoff) = false
    }
    private inner class Fixture : AutoCloseable {
        val root = Files.createTempDirectory("genesis-handoff-review").toFile()
        val store = ChiefBotProfileStore(root)
        val mailbox = AgentMailbox(maxPerAgent = 1)
        val authority = Authority()
        var now = 1_000L
        val adapter = ChiefBotHandoffAdmission(store, mailbox, authority) { now }
        val effects = AtomicInteger()
        val cancels = AtomicInteger()
        var onExecute: (() -> Unit)? = null
        init { for (id in listOf("atlas", "forge", "lint", "rogue")) store.create("bot-$id", id, "review", "bounded") }
        fun scope(profile: String = "node-continuum") = WorkspaceTaskScope("bot-forge", task, source,
            request, hash, jobId, profile, 256, 1, 64, 1_000)
        val port = object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope): WorkspaceObservation {
                effects.incrementAndGet(); onExecute?.invoke()
                return WorkspaceObservation(scope, "continuum-" + "a".repeat(24), hash, true, 0)
            }
            override fun cancel(scope: WorkspaceTaskScope) { cancels.incrementAndGet() }
        }
        fun worker(h: ChiefBotHandoff = handoff(), profile: String = "node-continuum",
                   allowedTools: Set<String> = setOf("fixed-node-test")): WorkerJob {
            val s = scope(profile)
            return adapter.workspaceJob(WorkerBrief(ChiefWorkspaceWiring.briefId(s), "review", allowedTools, 2_000), h, s, port)
        }
        override fun close() { root.deleteRecursively() }
    }
    @Test fun positiveControlUsesActualAdapterAndExistingMailboxAndJob() = Fixture().use { f ->
        assertEquals(ChiefBotHandoffDecision.ADMIT, f.adapter.send(handoff()))
        val delivery = f.adapter.receive("bot-forge").single()
        assertEquals(ChiefBotHandoffDecision.ADMIT, delivery.decision)
        assertTrue(f.worker(delivery.handoff!!).run().contains("verification=PENDING"))
        assertEquals(1, f.effects.get()); assertEquals(1, f.authority.claims.get())
    }
    @Test fun registeredButUnassignedSenderCannotIssueWork() = Fixture().use { f ->
        assertEquals(ChiefBotHandoffDecision.REJECT, f.adapter.admit(handoff(from = "bot-rogue")))
    }
    @Test fun recipientProfileCapCannotBeExpandedByParentRemainder() = Fixture().use { f ->
        assertEquals(1, f.store.get("bot-forge")!!.resourceBudget.maxModelCalls)
        assertEquals(ChiefBotHandoffDecision.REJECT, f.adapter.admit(handoff(models = 2)))
    }
    @Test fun undeclaredProfileCannotHideBehindEmptyToolRequest() = Fixture().use { f ->
        assertThrows(RuntimeException::class.java) { f.worker(handoff(tools = emptySet()), "kotlin-fixture") }
        assertEquals(0, f.effects.get())
    }
    @Test fun existingWorkerBriefMustAlsoPermitTheRequestedTool() = Fixture().use { f ->
        assertThrows(RuntimeException::class.java) { f.worker(allowedTools = emptySet()) }
        assertEquals(0, f.effects.get()); assertEquals(0, f.authority.claims.get())
    }
    @Test fun expiryAndSourceChangesAfterSendAreRecheckedOnConsumption() = Fixture().use { f ->
        assertEquals(ChiefBotHandoffDecision.ADMIT, f.adapter.send(handoff()))
        f.now = 4_000
        assertEquals(ChiefBotHandoffDecision.REJECT, f.adapter.receive("bot-forge").single().decision)
        assertEquals(0, f.effects.get()); assertEquals(0, f.authority.claims.get())
    }
    @Test fun sourceChangeAfterSendCannotAdvanceTask() = Fixture().use { f ->
        assertEquals(ChiefBotHandoffDecision.ADMIT, f.adapter.send(handoff()))
        f.authority.state = f.authority.state.copy(sourceCommit = "0".repeat(40))
        assertEquals(ChiefBotHandoffDecision.REJECT, f.adapter.receive("bot-forge").single().decision)
        assertEquals(0, f.effects.get())
    }
    @Test fun mailboxFullDoesNotConsumeBudgetAndRetryCannotDoubleExecute() = Fixture().use { f ->
        assertTrue(f.mailbox.send(AgentMessage("occupied", "chief", "bot-forge", AgentPriority.INFO, "occupied")))
        assertEquals(ChiefBotHandoffDecision.MAILBOX_REJECTED, f.adapter.send(handoff()))
        assertEquals(0, f.authority.claims.get()); assertEquals(2, f.authority.state.remainingToolCalls)
        f.mailbox.drain("bot-forge")
        assertEquals(ChiefBotHandoffDecision.ADMIT, f.adapter.send(handoff()))
        f.worker(f.adapter.receive("bot-forge").single().handoff!!).run()
        assertThrows(IllegalStateException::class.java) { f.worker().run() }
        assertEquals(1, f.effects.get()); assertEquals(1, f.authority.claims.get())
    }
    @Test fun revokedDuringClaimNeverReachesExecution() = Fixture().use { f ->
        f.authority.onClaim = { f.authority.state = f.authority.state.copy(currentGrant = false) }
        assertThrows(IllegalStateException::class.java) { f.worker().run() }
        assertEquals(0, f.effects.get())
    }
    @Test fun terminalChangeDuringExecutionSuppressesSuccess() = Fixture().use { f ->
        f.onExecute = { f.authority.state = f.authority.state.copy(terminal = true) }
        assertThrows(IllegalStateException::class.java) { f.worker().run() }
        assertEquals(1, f.effects.get())
    }
    @Test fun cancelBeforeRunDoesNotConsumeOrExecute() = Fixture().use { f ->
        val worker = f.worker(); worker.cancel()
        assertThrows(java.util.concurrent.CancellationException::class.java) { worker.run() }
        assertEquals(0, f.effects.get()); assertEquals(0, f.authority.claims.get())
    }
    @Test fun simultaneousAdmissionsCannotMultiplySingleParentReservation() = Fixture().use { f ->
        f.authority.state = f.authority.state.copy(remainingModelCalls = 1, remainingToolCalls = 1)
        val a = f.worker(); val b = f.worker(handoff(id = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"))
        val start = CountDownLatch(1); val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(a,b).map { w -> executor.submit<Boolean> { start.await(); runCatching { w.run() }.isSuccess } }
            start.countDown(); futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, f.effects.get()); assertEquals(1, f.authority.claims.get())
            assertEquals(0, f.authority.state.remainingModelCalls); assertEquals(0, f.authority.state.remainingToolCalls)
        } finally { executor.shutdownNow() }
    }
}
