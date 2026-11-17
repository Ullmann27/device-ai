package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ComputeResultVerifierTest {
    private val node = ComputeNodeId("verified-node")

    private fun registry(): ComputeNodeRegistry =
        ComputeNodeRegistry(NodeManifestSignatureVerifier {
            it.signature.contentEquals(byteArrayOf(1))
        }, nowMillis = { 1500L }).also {
            it.register(SignedNodeCapabilityManifest(
                NodeCapabilityManifest(
                    nodeId = node, ownerBinding = "owner:test",
                    taskTypes = setOf(ComputeTaskType.REASONING),
                    backends = setOf(ComputeBackend.CPU),
                    maxRamMb = 4096, maxRuntimeMillis = 5000,
                    maxOutputBytes = 4096, issuedAtMillis = 100L,
                    expiresAtMillis = 5000L
                ), byteArrayOf(1)))
        }

    private fun task(id: String = "task-test") = ComputeTask(
        taskId = id,
        type = ComputeTaskType.REASONING,
        inputObjectIds = emptyList(),
        inlineContext = "bounded",
        requiredRamMb = 2048,
        requiredBackend = ComputeBackend.CPU,
        maxRuntimeMillis = 3000,
        maxOutputBytes = 2048,
        permission = ComputePermission.READ_ONLY_DATA,
        deadlineMillis = 4000L,
        parentStateRoot = null,
        assignedNodeId = node
    )

    private fun signed(taskId: String, by: ComputeNodeId = node, at: Long = 1500L,
                       bytes: Byte = 1): SignedComputeResult =
        SignedComputeResult(ComputeResult.create(taskId, by, "answer", at), byteArrayOf(bytes))

    @Test fun oneShotPreventsDuplicateOrWrongNodeResult() {
        val verifier = ComputeResultVerifier(registry(),
            ComputeResultSignatureVerifier { it.signature.contentEquals(byteArrayOf(1)) },
            nowMillis = { 2000L })
        assertFalse(verifier.validate(task().copy(assignedNodeId = null), signed("task-test")))
        assertFalse(verifier.validate(task(), signed("task-test", ComputeNodeId("unknown-node"))))
        assertFalse(verifier.validate(task(), signed("task-test", bytes = 0)))
        assertTrue(verifier.validate(task(), signed("task-test")))
        assertFalse(verifier.validate(task(), signed("task-test")))
        assertFalse(verifier.cancel("task-test"))
    }

    @Test fun cancellationAndLateSubmissionFailClosed() {
        val verifier = ComputeResultVerifier(registry(),
            ComputeResultSignatureVerifier { true }, nowMillis = { 1500L })
        assertTrue(verifier.cancel("task-cancelled"))
        assertFalse(verifier.validate(task("task-cancelled"), signed("task-cancelled")))
        assertFalse(verifier.cancel("task-cancelled"))
        assertFalse(verifier.validate(task("task-late"), signed("task-late", at = 4500L)))
        val expired = ComputeResultVerifier(registry(),
            ComputeResultSignatureVerifier { true }, nowMillis = { 4500L })
        assertFalse(expired.validate(task("task-expired"), signed("task-expired")))
    }

    @Test fun revokedNodeOrPrivateKeyContextRejected() {
        val registry = registry()
        registry.revoke(node)
        val verifier = ComputeResultVerifier(registry, ComputeResultSignatureVerifier { true },
            nowMillis = { 1500L })
        assertFalse(verifier.validate(task(), signed("task-test")))
        assertThrows(IllegalArgumentException::class.java) {
            task().copy(inlineContext = "private_key = passwordValue")
        }
        assertThrows(IllegalArgumentException::class.java) {
            task().copy(inlineContext = "-----BEGIN RSA PRIVATE KEY-----")
        }
    }
}
