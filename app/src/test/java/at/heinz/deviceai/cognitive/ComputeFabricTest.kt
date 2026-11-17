package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeFabricTest {
    @Test
    fun mutatingSignedCollectionsCannotEscalateRegisteredCapabilities() {
        val types = mutableSetOf(ComputeTaskType.VERIFY)
        val backends = mutableSetOf(ComputeBackend.CPU)
        val registry = registry()
        val signed = manifest("mutable").copy(taskTypes = types, backends = backends)
        registry.register(SignedNodeCapabilityManifest(signed, byteArrayOf(1)))
        types += ComputeTaskType.REASONING
        backends += ComputeBackend.GPU
        assertNull(ComputeScheduler(registry) { 1_500 }.select(task()))
        assertEquals(setOf(ComputeTaskType.VERIFY), registry.get(signed.nodeId)!!.taskTypes)
        assertEquals(setOf(ComputeBackend.CPU), registry.get(signed.nodeId)!!.backends)
        // Returned values also must not expose a mutable internal permission set.
        assertThrows(UnsupportedOperationException::class.java) {
            (registry.get(signed.nodeId)!!.taskTypes as MutableSet).add(ComputeTaskType.REASONING)
        }
    }

    @Test
    fun futureIssuedManifestCannotBeRegisteredOrUsedAfterClockRollback() {
        var now = 1_500L
        val registry = ComputeNodeRegistry(NodeManifestSignatureVerifier { true }) { now }
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(SignedNodeCapabilityManifest(manifest("future").copy(issuedAtMillis = 1_600), byteArrayOf(1)))
        }
        registry.register(SignedNodeCapabilityManifest(manifest("past"), byteArrayOf(1)))
        now = 900L
        assertNull(registry.get(ComputeNodeId("past")))
        assertTrue(registry.authorized().isEmpty())
    }

    @Test
    fun schedulerCannotSubstituteAnExplicitlyAssignedNode() {
        val registry = registry()
        registry.register(SignedNodeCapabilityManifest(manifest("node-a"), byteArrayOf(1)))
        registry.register(SignedNodeCapabilityManifest(manifest("node-b"), byteArrayOf(1)))
        assertEquals(ComputeNodeId("node-b"), ComputeScheduler(registry) { 1_500 }
            .select(task().copy(assignedNodeId = ComputeNodeId("node-b")))!!.nodeId)
        assertNull(ComputeScheduler(registry) { 1_500 }
            .select(task().copy(assignedNodeId = ComputeNodeId("missing"))))
    }

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
    fun schedulerRejectsGrantExpiringBeforeMaximumTaskRuntimeEnds() {
        val registry = registry()
        val short = manifest("expires-during-task").copy(expiresAtMillis = 10_000)
        val valid = manifest("valid-full-window")
        registry.register(SignedNodeCapabilityManifest(short, byteArrayOf(1)))
        val scheduler = ComputeScheduler(registry) { 1_500 }

        // The task can run for 10,000ms, but this node has only 8,500ms
        // of authorized time left. It must not be used.
        assertNull(scheduler.select(task()))

        registry.register(SignedNodeCapabilityManifest(valid, byteArrayOf(1)))
        assertEquals(valid.nodeId, scheduler.select(task())!!.nodeId)
        assertNull(scheduler.select(task().copy(assignedNodeId = short.nodeId)))
    }

    @Test
    fun schedulerRechecksManifestIssueTimeAgainstItsOwnClock() {
        // The registry and scheduler clocks may differ, for example when
        // using test clocks or after a system clock adjustment.
        val registry = registry()
        registry.register(SignedNodeCapabilityManifest(manifest("clock-skew"),
            byteArrayOf(1)))
        val scheduler = ComputeScheduler(registry) { 900 }
        assertNull(scheduler.select(task()))
    }

    @Test
    fun credentialLikeInlineContextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            task(inlineContext = "token=abcdefgh12345678")
        }
    }

    @Test
    fun unicodeInlineContextCannotBypassByteBudget() {
        val oversized="€".repeat(6_000) // 6,000 characters but 18,000 UTF-8 bytes
        assertThrows(IllegalArgumentException::class.java) {
            task(inlineContext=oversized)
        }
        // The same policy still allows a valid bounded Unicode description.
        assertEquals(300,task(inlineContext="€".repeat(100)).inlineContext.toByteArray(Charsets.UTF_8).size)
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
        expiresAtMillis = 15_000
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
