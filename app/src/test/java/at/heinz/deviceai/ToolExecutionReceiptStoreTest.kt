package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class ToolExecutionReceiptStoreTest {
    @Test
    fun gatePersistsSuccessfulReceiptAndSuppressesDuplicateAfterRestart() {
        val directory = Files.createTempDirectory("device-ai-receipts").toFile()
        try {
            val calls = AtomicInteger()
            val store = FileToolExecutionReceiptStore(directory)
            val proposal = ToolProposal("get_device_info", emptyMap())
            val invocation = proposal.invocation("call-1")
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                calls.incrementAndGet()
                "device details"
            }, receiptStore = store)
            gate.recordProposal(invocation)
            assertEquals(ToolReceiptState.PROPOSED, store.get("call-1")?.state)
            val result = gate.execute(invocation)

            assertEquals(ToolExecutionStatus.SUCCEEDED, result.record.status)
            assertEquals(1, calls.get())
            val receipt = store.get("call-1")
            assertEquals(ToolReceiptState.SUCCEEDED, receipt?.state)
            assertEquals(
                listOf(
                    ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED,
                    ToolReceiptState.START_PENDING, ToolReceiptState.DISPATCH_COMMITTED, ToolReceiptState.SUCCEEDED
                ),
                receipt?.transitions?.map { it.state }
            )
            assertEquals("device details".length, receipt?.outputLength)
            assertEquals(
                MessageDigest.getInstance("SHA-256").digest("device details".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                receipt?.outputSha256
            )

            val restarted = FileToolExecutionReceiptStore(directory)
            val replay = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                calls.incrementAndGet()
                "should not run"
            }, receiptStore = restarted).execute(proposal.invocation("call-1"))
            assertEquals(ToolExecutionStatus.SUCCEEDED, replay.record.status)
            assertTrue(replay.record.decisionReason.contains("already completed"))
            assertEquals(1, calls.get())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun startedReceiptSurvivesRestartAndBlocksAmbiguousReplay() {
        val directory = Files.createTempDirectory("device-ai-started-receipt").toFile()
        try {
            val store = FileToolExecutionReceiptStore(directory)
            val now = 10L
            val request = ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app"))
                .invocation("ambiguous-1")
            val proposal = ToolExecutionReceipt(
                callId = request.id,
                toolName = request.request.name,
                argumentFingerprint = toolArgumentFingerprint(request.request.arguments),
                sanitizedArguments = mapOf("packageOrName" to "[STRING 15 chars]"),
                risk = Risk.CONFIRM,
                createdAtMillis = now,
                transitions = listOf(ToolReceiptTransition(ToolReceiptState.PROPOSED, now))
            )
            store.propose(proposal)
            assertNotNull(store.transition("ambiguous-1", ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED, 11))
            assertNotNull(store.transition("ambiguous-1", ToolReceiptState.APPROVED, ToolReceiptState.STARTED, 12))

            var executions = 0
            val replay = ToolGate(
                ToolPolicy(), ToolRunner { executions++; "ran" }, receiptStore = FileToolExecutionReceiptStore(directory)
            ).execute(request, userConfirmed = true)
            assertEquals(ToolExecutionStatus.REJECTED, replay.record.status)
            assertTrue(replay.record.decisionReason.contains("operator review"))
            assertEquals(0, executions)
            assertEquals(ToolReceiptState.STARTED, FileToolExecutionReceiptStore(directory).get("ambiguous-1")?.state)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun receiptCodecRejectsTamperedDataAndNeverPersistsRawArgumentStrings() {
        val directory = Files.createTempDirectory("device-ai-corrupt-receipt").toFile()
        try {
            val store = FileToolExecutionReceiptStore(directory)
            val proposal = ToolProposal("get_app_info", mapOf("packageOrName" to "sensitive.app.name"))
                .invocation("safe-1")
            ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "ok" }, receiptStore = store)
                .execute(proposal)
            val file = directory.resolve("tool-execution-receipts.bin")
            assertFalse(file.readBytes().decodeToString().contains("sensitive.app.name"))

            val bytes = file.readBytes()
            val schemaOffset = 2 + "DEVICE_AI_TOOL_RECEIPTS".length
            val staleSchema = bytes.copyOf().apply { this[schemaOffset + 3] = 0 }
            file.writeBytes(staleSchema)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                FileToolExecutionReceiptStore(directory).get("safe-1")
            }
            file.writeBytes(bytes)
            bytes[bytes.lastIndex - 3] = (bytes[bytes.lastIndex - 3].toInt() xor 0x40).toByte()
            file.writeBytes(bytes)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                FileToolExecutionReceiptStore(directory).get("safe-1")
            }
            file.writeBytes(ByteArray(300 * 1024))
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                FileToolExecutionReceiptStore(directory).get("safe-1")
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun callIdCannotBeReusedForDifferentArguments() {
        val directory = Files.createTempDirectory("device-ai-receipt-conflict").toFile()
        try {
            val store = FileToolExecutionReceiptStore(directory)
            val calls = AtomicInteger()
            val first = ToolProposal("get_app_info", mapOf("packageOrName" to "com.example.app"))
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                calls.incrementAndGet()
                "ok"
            }, receiptStore = store)
            gate.execute(first.invocation("same-id"))

            val changed = ToolProposal("get_app_info", mapOf("packageOrName" to "org.example.app"))
            val replay = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                calls.incrementAndGet()
                "must not execute"
            }, receiptStore = store).execute(changed.invocation("same-id"))
            assertEquals(ToolExecutionStatus.REJECTED, replay.record.status)
            assertEquals(1, calls.get())
            assertTrue(
                toolArgumentFingerprint(first.arguments) != toolArgumentFingerprint(changed.arguments)
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun deniedCallIsTerminalAndCannotBeReplayedAsApproval() {
        val directory = Files.createTempDirectory("device-ai-denied-receipt").toFile()
        try {
            val store = FileToolExecutionReceiptStore(directory)
            val proposal = ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app"))
            val denied = ToolGate(ToolPolicy(), ToolRunner { "ran" }, receiptStore = store)
                .execute(proposal.invocation("denied-1"))
            assertEquals(ToolExecutionStatus.REJECTED, denied.record.status)
            assertEquals(ToolReceiptState.REJECTED, store.get("denied-1")?.state)
            val replay = ToolGate(ToolPolicy(), ToolRunner { "ran" }, receiptStore = store)
                .execute(proposal.invocation("denied-1"), userConfirmed = true)
            assertEquals(ToolExecutionStatus.REJECTED, replay.record.status)
            assertNull(replay.output)
        } finally {
            directory.deleteRecursively()
        }
    }
}
