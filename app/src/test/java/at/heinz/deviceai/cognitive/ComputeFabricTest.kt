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
        val task = task()
        val result = ComputeResult.create(task.taskId, manifest.nodeId, "answer", 2_000)
        val signed = SignedComputeResult(result, byteArrayOf(0))

        assertFalse(
            ComputeResultValidator.validate(
                task,
                signed,
                registry,
                ComputeResultSignatureVerifier { it.signature.contentEquals(byteArrayOf(1)) }
            )
        )

        val valid = SignedComputeResult(result, byteArrayOf(1))
        assertTrue(
            ComputeResultValidator.validate(
                task,
                valid,
                registry,
                ComputeResultSignatureVerifier { it.signature.contentEquals(byteArrayOf(1)) }
            )
        )
    }

    private fun registry() = ComputeNodeRegistry(
        verifier = NodeManifestSignatureVerifier { it.signature.contentEquals(byteArrayOf(1)) },
        nowMillis = { 1_500 }
    )

    private fun manifest(id: String) = NodeCapabilityManifest(
        nodeId = ComputeNodeId(id),
        ownerBinding = "owner:test",
        taskTypes = setOf(ComputeTaskType.REASONING, ComputeTaskType.VERIFY),
        backends = setOf(ComputeBackend.CPU),
        maxRamMb = 8_192,
        maxRuntimeMillis = 60_000,
        maxOutputBytes = 16_000,
        issuedAtMillis = 1_000,
        expiresAtMillis = 10_000
    )

    private fun task(
        inlineContext: String = "bounded task context",
        requiredRamMb: Int = 4_096
    ) = ComputeTask(
        taskId = "task-1",
        type = ComputeTaskType.REASONING,
        inputObjectIds = emptyList(),
        inlineContext = inlineContext,
        requiredRamMb = requiredRamMb,
        requiredBackend = ComputeBackend.CPU,
        maxRuntimeMillis = 10_000,
        maxOutputBytes = 8_000,
        permission = ComputePermission.READ_ONLY_DATA,
        deadlineMillis = 5_000,
        parentStateRoot = null
    )
}
