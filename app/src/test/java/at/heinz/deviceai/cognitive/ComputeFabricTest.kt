package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeFabricTest {
    @Test
    fun onlyVerifiedAuthorizedCompatibleNodeIsScheduled() {
        val registry = registry()
        val manifest = manifest("node-a")
        registry.register(SignedNodeCapabilityManifest(manifest, byteArrayOf(1)))

        val task = task()
        val selected = ComputeScheduler(registry) { 1_500 }.select(task)

        assertEquals(manifest.nodeId, selected!!.nodeId)
    }

    @Test
    fun badSignatureAndRevokedNodeAreRejected() {
        val registry = registry()
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(SignedNodeCapabilityManifest(manifest("node-b"), byteArrayOf(0)))
        }

        val good = manifest("node-c")
        registry.register(SignedNodeCapabilityManifest(good, byteArrayOf(1)))
        registry.revoke(good.nodeId)

        assertNull(ComputeScheduler(registry) { 1_500 }.select(task()))
    }

    @Test
    fun credentialLikeInlineContextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            task(inlineContext = "token=abcdefgh12345678")
        }
    }

    @Test
    fun overBudgetTaskIsNotScheduled() {
        val registry = registry()
        registry.register(SignedNodeCapabilityManifest(manifest("node-d"), byteArrayOf(1)))
        val tooLarge = task(requiredRamMb = 16_384)

        assertNull(ComputeScheduler(registry) { 1_500 }.select(tooLarge))
    }

    @Test
    fun forgedResultMetadataFailsValidation() {
        val registry = registry()
        val manifest = manifest("node-e")
        registry.register(SignedNodeCapabilityManifest(manifest, byteArrayOf(1)))
        val task = task(assignedNodeId = manifest.nodeId)
        val result = ComputeResult.create(task.taskId, manifest.nodeId, "answer", 2_000)
        val verifier = resultVerifier(registry) { it.signature.contentEquals(byteArrayOf(1)) }
        val signed = SignedComputeResult(result, byteArrayOf(0))

        assertFalse(verifier.validate(task, signed))

        val valid = SignedComputeResult(result, byteArrayOf(1))
        assertTrue(verifier.validate(task, valid))
    }

    @Test
    fun resultFromDifferentRegisteredNodeIsRejected() {
        val registry = registry()
        val assigned = manifest("assigned")
        val forged = manifest("forged")
        registry.register(SignedNodeCapabilityManifest(assigned, byteArrayOf(1)))
        registry.register(SignedNodeCapabilityManifest(forged, byteArrayOf(1)))
        val task = task(assignedNodeId = assigned.nodeId)
        val result = ComputeResult.create(task.taskId, forged.nodeId, "answer", 2_000)

        assertFalse(resultVerifier(registry) { true }.validate(task, SignedComputeResult(result, byteArrayOf(1))))
    }

    @Test
    fun revokedAndUnknownNodesAreRejected() {
        val registry = registry()
        val revoked = manifest("revoked")
        registry.register(SignedNodeCapabilityManifest(revoked, byteArrayOf(1)))
        registry.revoke(revoked.nodeId)

        val revokedTask = task(assignedNodeId = revoked.nodeId)
        val revokedResult = ComputeResult.create(revokedTask.taskId, revoked.nodeId, "answer", 2_000)
        assertFalse(
            resultVerifier(registry) { true }
                .validate(revokedTask, SignedComputeResult(revokedResult, byteArrayOf(1)))
        )

        val unknownId = ComputeNodeId("unknown")
        val unknownTask = task(taskId = "unknown-task", assignedNodeId = unknownId)
        val unknownResult = ComputeResult.create(unknownTask.taskId, unknownId, "answer", 2_000)
        assertFalse(
            resultVerifier(registry) { true }
                .validate(unknownTask, SignedComputeResult(unknownResult, byteArrayOf(1)))
        )
    }

    @Test
    fun duplicateResultForTaskIsIgnored() {
        val registry = registry()
        val manifest = manifest("duplicate")
        registry.register(SignedNodeCapabilityManifest(manifest, byteArrayOf(1)))
        val task = task(assignedNodeId = manifest.nodeId)
        val result = ComputeResult.create(task.taskId, manifest.nodeId, "answer", 2_000)
        val envelope = SignedComputeResult(result, byteArrayOf(1))
        val verifier = resultVerifier(registry) { true }

        assertTrue(verifier.validate(task, envelope))
        assertFalse(verifier.validate(task, envelope))
    }

    @Test
    fun expiredTaskAndOversizedResultAreRejected() {
        val registry = registry()
        val manifest = manifest("bounded")
        registry.register(SignedNodeCapabilityManifest(manifest, byteArrayOf(1)))

        val expiredTask = task(assignedNodeId = manifest.nodeId, deadlineMillis = 2_499)
        val expiredResult = ComputeResult.create(expiredTask.taskId, manifest.nodeId, "answer", 2_000)
        assertFalse(
            resultVerifier(registry) { true }
                .validate(expiredTask, SignedComputeResult(expiredResult, byteArrayOf(1)))
        )

        val boundedTask = task(assignedNodeId = manifest.nodeId, maxOutputBytes = 8_000)
        val oversizedResult = ComputeResult.create(boundedTask.taskId, manifest.nodeId, "x".repeat(8_001), 2_000)
        assertFalse(
            resultVerifier(registry) { true }
                .validate(boundedTask, SignedComputeResult(oversizedResult, byteArrayOf(1)))
        )
    }

    @Test
    fun cancelledTaskRejectsResult() {
        val registry = registry()
        val manifest = manifest("cancelled")
        registry.register(SignedNodeCapabilityManifest(manifest, byteArrayOf(1)))
        val task = task(assignedNodeId = manifest.nodeId)
        val result = ComputeResult.create(task.taskId, manifest.nodeId, "answer", 2_000)
        val verifier = resultVerifier(registry) { true }

        assertTrue(verifier.cancel(task.taskId))
        assertFalse(verifier.validate(task, SignedComputeResult(result, byteArrayOf(1))))
    }

    @Test
    fun inputBoundAndOwnerPrivateKeysAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            task(inlineContext = "x".repeat(16_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            task(inlineContext = "-----BEGIN PRIVATE KEY-----")
        }
    }

    private fun registry() = ComputeNodeRegistry(
        verifier = NodeManifestSignatureVerifier { it.signature.contentEquals(byteArrayOf(1)) },
        nowMillis = { 2_500 }
    )

    private fun resultVerifier(
        registry: ComputeNodeRegistry,
        signatureVerifier: (SignedComputeResult) -> Boolean
    ) = ComputeResultVerifier(registry, ComputeResultSignatureVerifier(signatureVerifier)) { 2_500 }

    private fun manifest(id: String) = NodeCapabilityManifest(
        nodeId = ComputeNodeId(id),
        ownerBinding = "owner:test",
        taskTypes = setOf(ComputeTaskType.REASONING, ComputeTaskType.VERIFY),
        backends = setOf(ComputeBackend.CPU),
        maxRamMb = 8_192,
        maxRuntimeMillis = 60_000,
        maxInputBytes = 64_000,
        maxOutputBytes = 16_000,
        issuedAtMillis = 1_000,
        expiresAtMillis = 10_000
    )

    private fun task(
        taskId: String = "task-1",
        inlineContext: String = "bounded task context",
        requiredRamMb: Int = 4_096,
        assignedNodeId: ComputeNodeId? = null,
        maxOutputBytes: Int = 8_000,
        deadlineMillis: Long = 5_000
    ) = ComputeTask(
        taskId = taskId,
        type = ComputeTaskType.REASONING,
        inputObjectIds = emptyList(),
        inlineContext = inlineContext,
        requiredRamMb = requiredRamMb,
        requiredBackend = ComputeBackend.CPU,
        maxRuntimeMillis = 10_000,
        maxOutputBytes = maxOutputBytes,
        permission = ComputePermission.READ_ONLY_DATA,
        deadlineMillis = deadlineMillis,
        parentStateRoot = null,
        assignedNodeId = assignedNodeId,
        maxInputBytes = 16_000
    )
}
