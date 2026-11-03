package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

/**
 * M5 milestone 5: when the durable journal becomes unreadable right before
 * dispatch, ToolGate correctly stays fail-closed (uncertain, blocked), but the
 * owner was told "Eine bereits ausgelöste Wirkung kann fortbestehen" although
 * nothing was dispatched in this run. The block must remain; the claim must not.
 */
class JournalUnavailableOwnerMessageTest {
    private val proposal = ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app"))

    /** Real file journal that fails every propose after the first [healthyProposals]. */
    private class FlakyJournal(private val real: ToolExecutionReceiptStore, private val healthyProposals: Int) :
        ToolExecutionReceiptStore by real {
        var proposals = 0
        override fun propose(receipt: ToolExecutionReceipt): ToolExecutionReceipt {
            if (++proposals > healthyProposals) throw IOException("journal unreadable")
            return real.propose(receipt)
        }
    }

    private fun run(store: ToolExecutionReceiptStore, onEffect: () -> Unit): AgentRunResult {
        val model = AgentModel { _, _, _, observations ->
            if (observations.isEmpty()) AgentModelOutput("HOST_SIMULATED", "", proposal)
            else AgentModelOutput("HOST_SIMULATED", "done")
        }
        return BoundedAgentOrchestrator(model, { emptyList() },
            ToolGate(ToolPolicy(), ToolRunner { onEffect(); "stopped" }, receiptStore = store),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, InMemoryExperienceStore(), callIdPrefix = "journal-loss",
            limits = AgentLimits(maxRetries = 0), approvalProvider = { _, _ -> AgentToolApproval(true) }
        ).run("Authorized synthetic host task")
    }

    @Test fun unreadableJournalBeforeDispatchStaysBlockedButDoesNotClaimAFiredEffect() {
        val root = Files.createTempDirectory("continuum-journal-loss").toFile()
        try {
            var effects = 0
            val result = run(FlakyJournal(FileToolExecutionReceiptStore(root), healthyProposals = 1)) { effects++ }
            assertEquals(0, effects)
            val blocker = checkNotNull(result.blocker)
            assertNull(result.experience)
            assertFalse(blocker, blocker.contains("bereits ausgelöste Wirkung"))
            assertTrue(blocker, blocker.contains("nichts ausgeführt"))
            assertTrue(blocker, blocker.contains("Besitzerprüfung"))
        } finally { root.deleteRecursively() }
    }

    @Test fun genuineUnknownAfterDispatchKeepsTheEffectMayPersistWarning() {
        val root = Files.createTempDirectory("continuum-journal-started").toFile()
        try {
            val store = FileToolExecutionReceiptStore(root)
            val gate = ToolGate(ToolPolicy(), ToolRunner { error("unused") }, receiptStore = store)
            val prior = gate.recordProposal(proposal.invocation("journal-loss-1"))!!
            val t = prior.transitions.last().atMillis
            assertNotNull(store.transition(prior.callId, ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED, t + 1))
            assertNotNull(store.transition(prior.callId, ToolReceiptState.APPROVED, ToolReceiptState.START_PENDING, t + 2))
            assertNotNull(store.transition(prior.callId, ToolReceiptState.START_PENDING,
                ToolReceiptState.UNKNOWN_AFTER_DISPATCH, t + 3))
            var effects = 0
            val result = run(store) { effects++ }
            assertEquals(0, effects)
            val blocker = checkNotNull(result.blocker)
            assertTrue(blocker, blocker.contains("Besitzerprüfung"))
            assertFalse(blocker, blocker.contains("nichts ausgeführt"))
        } finally { root.deleteRecursively() }
    }
}
