package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/**
 * M5 safety layer (Heinz rule: nothing sent/deleted/installed/changed without an
 * explicit owner yes). Pins the ToolGate seam that every caller (cloud calls,
 * BoundedAgent, a future chief/worker loop) has to pass.
 */
class ConsequentialActionApprovalTest {
    private val sample = mapOf(
        "packageOrName" to "com.example.app", "path" to "/storage/emulated/0/Download/x.apk",
        "source" to "/storage/emulated/0/Download/a.txt", "destination" to "/storage/emulated/0/Documents/a.txt",
        "query" to "wetter wien", "url" to "https://example.org/", "page" to "wifi", "seconds" to 5,
        "snapshotId" to "snapshot-fixture", "previewId" to "preview-fixture",
        "planDigest" to "0".repeat(64), "undoId" to "undo-fixture"
    )

    private fun proposalFor(spec: ToolSpec): ToolProposal {
        val proposal = ToolProposal(spec.name, spec.required.associateWith { sample.getValue(it) })
        assertNull("Valid fixture for ${spec.name}", proposal.validate())
        return proposal
    }

    @Test fun everyConsequentialRegistryToolIsRefusedWithoutAnExplicitOwnerYes() {
        val consequential = ToolRegistry.all().filter { it.risk != Risk.READ_ONLY }
        val checked = mutableListOf<String>()
        consequential.forEach { spec ->
            val proposal = proposalFor(spec)
            var effects = 0
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true), ToolRunner { effects++; "done" })
            val result = gate.execute(proposal.invocation("no-yes-${spec.name}"))
            assertEquals(spec.name, ToolExecutionStatus.REJECTED, result.record.status)
            assertEquals(spec.name, 0, effects)
            checked += spec.name
        }
        assertEquals(consequential.map { it.name }.toSet(), checked.toSet())
    }

    @Test fun destructiveToolsNeedTheStrongSecondConfirmationAndOperatorPolicy() {
        listOf("delete_file", "uninstall_app", "cleanup_own_cache").forEach { name ->
            val proposal = proposalFor(ToolRegistry.get(name)!!)
            var effects = 0
            val runner = ToolRunner { effects++; "done" }
            assertEquals(ToolExecutionStatus.REJECTED, ToolGate(ToolPolicy(destructiveEnabled = true), runner)
                .execute(proposal.invocation("$name-weak"), userConfirmed = true).record.status)
            assertEquals(ToolExecutionStatus.REJECTED, ToolGate(ToolPolicy(destructiveEnabled = false), runner)
                .execute(proposal.invocation("$name-policy"), userConfirmed = true, destructiveConfirmed = true).record.status)
            assertEquals(0, effects)
        }
    }

    @Test fun unknownToolsAreTreatedAsDestructiveAndNeverDispatched() {
        assertEquals(Risk.DESTRUCTIVE, ToolRegistry.risk("send_sms"))
        assertNotNull(ToolProposal("send_sms", mapOf("to" to "+43", "text" to "hi")).validate())
    }

    /**
     * An owner yes covers exactly the call that was shown. Re-using its call id for
     * different arguments (e.g. path /a approved, path /b submitted) must be refused
     * as a binding violation, not reported as a journal outage.
     */
    @Test fun confirmationForOneCallCannotAuthorizeDifferentArgumentsUnderTheSameCallId() {
        val root = Files.createTempDirectory("continuum-confirm-binding").toFile()
        try {
            var effects = 0
            val gate = ToolGate(ToolPolicy(destructiveEnabled = true), ToolRunner { effects++; "deleted" },
                receiptStore = FileToolExecutionReceiptStore(root))
            gate.recordProposal(ToolProposal("delete_file", mapOf("path" to "/storage/emulated/0/Download/a.txt"))
                .invocation("owner-shown-1"))
            val swapped = ToolProposal("delete_file", mapOf("path" to "/storage/emulated/0/Documents/b.txt"))
                .invocation("owner-shown-1")
            val result = gate.execute(swapped, userConfirmed = true, destructiveConfirmed = true)
            assertEquals(0, effects)
            assertEquals(ToolExecutionStatus.REJECTED, result.record.status)
            assertFalse("binding violation is not a journal outage", result.journalUnavailableBeforeDispatch)
            assertTrue(result.record.decisionReason, result.record.decisionReason.contains("different arguments"))
        } finally { root.deleteRecursively() }
    }
}
