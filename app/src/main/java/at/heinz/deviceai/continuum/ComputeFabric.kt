package at.heinz.deviceai.continuum

import at.heinz.deviceai.cognitive.IdentitySigner
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class ComputeNodeId(val value: String) {
    init { require(value.matches(Regex("[a-f0-9]{16,64}"))) }
}

enum class ComputeTaskType { REASONING, VERIFICATION, EMBEDDING, CLASSIFICATION, SUMMARIZATION }
enum class ComputePermission { NONE, READ_ONLY_DATA }

data class NodeCapabilityManifest(
    val nodeId: ComputeNodeId,
    val expiresAtMillis: Long,
    val maxRamMb: Int,
    val taskClasses: Set<ComputeTaskType>,
    val maxInputBytes: Int,
    val maxOutputBytes: Int,
    val permissions: Set<ComputePermission>
) {
    init {
        require(expiresAtMillis > 0)
        require(maxRamMb in 1..1_048_576)
        require(taskClasses.isNotEmpty() && taskClasses.size <= ComputeTaskType.values().size)
        require(maxInputBytes in 1..MAX_COMPUTE_INPUT_BYTES)
        require(maxOutputBytes in 1..MAX_COMPUTE_OUTPUT_BYTES)
        require(permissions.all { it == ComputePermission.NONE || it == ComputePermission.READ_ONLY_DATA })
    }

    companion object {
        const val MAX_COMPUTE_INPUT_BYTES = 512 * 1024
        const val MAX_COMPUTE_OUTPUT_BYTES = 64 * 1024
    }
}

data class SignedNodeCapabilityManifest(
    val manifest: NodeCapabilityManifest,
    val signature: ByteArray
) {
    init { require(signature.isNotEmpty() && signature.size <= MAX_SIGNATURE_BYTES) }
    fun verify(signer: IdentitySigner): Boolean =
        signer.verify(ComputeProtocolCodec.manifestSigningBytes(manifest), signature)

    companion object {
        const val MAX_SIGNATURE_BYTES = 256
        fun sign(manifest: NodeCapabilityManifest, signer: IdentitySigner) =
            SignedNodeCapabilityManifest(manifest, signer.sign(ComputeProtocolCodec.manifestSigningBytes(manifest)))
    }
}

fun interface NodeManifestVerifier {
    fun verify(signedManifest: SignedNodeCapabilityManifest): Boolean
}

data class ComputeTask(
    val taskId: String,
    val taskType: ComputeTaskType,
    val inputData: ByteArray,
    val requiredRamMb: Int,
    val deadlineMillis: Long,
    val maxOutputBytes: Int,
    val permission: ComputePermission,
    val parentStateRoot: String
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(inputData.size <= NodeCapabilityManifest.MAX_COMPUTE_INPUT_BYTES)
        require(requiredRamMb in 1..1_048_576 && deadlineMillis > 0)
        require(maxOutputBytes in 1..NodeCapabilityManifest.MAX_COMPUTE_OUTPUT_BYTES)
        require(parentStateRoot.matches(Regex("[a-f0-9]{64}")))
    }

    companion object { const val MAX_RUNTIME_MILLIS = 120_000L }
}

data class ComputeResult(
    val taskId: String,
    val nodeId: ComputeNodeId,
    val parentStateRoot: String,
    val output: ByteArray,
    val outputHash: String,
    val completedAtMillis: Long,
    val signature: ByteArray
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(parentStateRoot.matches(Regex("[a-f0-9]{64}")))
        require(output.size <= NodeCapabilityManifest.MAX_COMPUTE_OUTPUT_BYTES)
        require(outputHash.matches(Regex("[a-f0-9]{64}")))
        require(outputHash == sha256Hex(output))
        require(completedAtMillis >= 0)
        require(signature.isNotEmpty() && signature.size <= SignedNodeCapabilityManifest.MAX_SIGNATURE_BYTES)
    }
}

object ComputeProtocolCodec {
    fun manifestSigningBytes(manifest: NodeCapabilityManifest): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF("DEVICE_AI_COMPUTE_CAPABILITY")
                out.writeUTF(manifest.nodeId.value)
                out.writeLong(manifest.expiresAtMillis)
                out.writeInt(manifest.maxRamMb)
                out.writeInt(manifest.taskClasses.size)
                manifest.taskClasses.sortedBy { it.ordinal }.forEach { out.writeInt(it.ordinal) }
                out.writeInt(manifest.maxInputBytes)
                out.writeInt(manifest.maxOutputBytes)
                out.writeInt(manifest.permissions.size)
                manifest.permissions.sortedBy { it.ordinal }.forEach { out.writeInt(it.ordinal) }
            }
        }.toByteArray()

    fun resultSigningBytes(
        taskId: String,
        nodeId: ComputeNodeId,
        parentStateRoot: String,
        outputHash: String,
        outputSizeBytes: Int,
        completedAtMillis: Long
    ): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            require(taskId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
            require(parentStateRoot.matches(Regex("[a-f0-9]{64}")))
            require(outputHash.matches(Regex("[a-f0-9]{64}")))
            require(outputSizeBytes in 0..NodeCapabilityManifest.MAX_COMPUTE_OUTPUT_BYTES)
            require(completedAtMillis >= 0)
            DataOutputStream(buffer).use { out ->
                out.writeUTF("DEVICE_AI_COMPUTE_RESULT")
                out.writeUTF(taskId)
                out.writeUTF(nodeId.value)
                out.writeUTF(parentStateRoot)
                out.writeUTF(outputHash)
                out.writeInt(outputSizeBytes)
                out.writeLong(completedAtMillis)
            }
        }.toByteArray()

    fun signResult(
        taskId: String,
        nodeId: ComputeNodeId,
        parentStateRoot: String,
        output: ByteArray,
        completedAtMillis: Long,
        signer: IdentitySigner
    ): ComputeResult {
        val stableOutput = output.copyOf()
        val hash = sha256Hex(stableOutput)
        val signedPayload = resultSigningBytes(
            taskId, nodeId, parentStateRoot, hash, stableOutput.size, completedAtMillis
        )
        return ComputeResult(
            taskId, nodeId, parentStateRoot, stableOutput, hash, completedAtMillis,
            signer.sign(signedPayload)
        )
    }

    fun verifyResult(result: ComputeResult, signer: IdentitySigner): Boolean =
        signer.verify(
            resultSigningBytes(
                result.taskId, result.nodeId, result.parentStateRoot, result.outputHash,
                result.output.size, result.completedAtMillis
            ),
            result.signature
        )
}

fun interface ComputeResultVerifier {
    fun verify(result: ComputeResult): Boolean
}

fun interface LocalComputeNode {
    fun execute(task: ComputeTask, guard: ComputeExecutionGuard): ComputeResult
}

class ComputeExecutionGuard internal constructor(
    private val deadlineMillis: Long,
    private val deadlineNanos: Long,
    private val cancelled: () -> Boolean
) {
    fun checkActive(nowMillis: Long = System.currentTimeMillis()) {
        if (cancelled()) throw CancellationException("Compute task cancelled.")
        if (nowMillis >= deadlineMillis || System.nanoTime() >= deadlineNanos) {
            throw IllegalStateException("Compute task deadline exceeded.")
        }
    }
}

class ComputeNodeRegistry(
    authorizedNodeIds: Set<ComputeNodeId>,
    private val manifestVerifier: NodeManifestVerifier
) {
    private val authorizedNodeIds = authorizedNodeIds.toSet()
    private val nodes = linkedMapOf<ComputeNodeId, RegisteredComputeNode>()
    private val revoked = mutableSetOf<ComputeNodeId>()

    @Synchronized
    fun register(
        signedManifest: SignedNodeCapabilityManifest,
        adapter: LocalComputeNode,
        nowMillis: Long
    ) {
        val manifest = signedManifest.manifest.copy(
            taskClasses = signedManifest.manifest.taskClasses.toSet(),
            permissions = signedManifest.manifest.permissions.toSet()
        )
        val stableSignedManifest = SignedNodeCapabilityManifest(manifest, signedManifest.signature.copyOf())
        require(nowMillis >= 0)
        require(manifest.nodeId in authorizedNodeIds) { "Compute node is not owner-authorized." }
        require(manifest.nodeId !in revoked) { "Compute node has been revoked." }
        require(manifest.expiresAtMillis > nowMillis) { "Compute node capability manifest has expired." }
        require(manifestVerifier.verify(stableSignedManifest)) { "Compute node capability signature is invalid." }
        nodes[manifest.nodeId] = RegisteredComputeNode(
            manifest,
            adapter
        )
    }

    @Synchronized
    fun revoke(nodeId: ComputeNodeId) {
        revoked += nodeId
        nodes.remove(nodeId)
    }

    @Synchronized
    internal fun isAvailable(nodeId: ComputeNodeId, nowMillis: Long): Boolean =
        nodeId in authorizedNodeIds && nodeId !in revoked &&
            nodes[nodeId]?.manifest?.expiresAtMillis?.let { it > nowMillis } == true

    @Synchronized
    internal fun compatible(task: ComputeTask, nowMillis: Long): List<RegisteredComputeNode> =
        nodes.values.filter { node ->
            node.manifest.nodeId in authorizedNodeIds &&
                node.manifest.nodeId !in revoked &&
                node.manifest.expiresAtMillis > nowMillis &&
                task.deadlineMillis <= node.manifest.expiresAtMillis &&
                task.taskType in node.manifest.taskClasses &&
                task.requiredRamMb <= node.manifest.maxRamMb &&
                task.inputData.size <= node.manifest.maxInputBytes &&
                task.maxOutputBytes <= node.manifest.maxOutputBytes &&
                task.permission in node.manifest.permissions
        }.sortedBy { it.manifest.nodeId.value }
}

internal data class RegisteredComputeNode(
    val manifest: NodeCapabilityManifest,
    val adapter: LocalComputeNode
)

class ComputeScheduler(
    private val registry: ComputeNodeRegistry,
    private val resultVerifier: ComputeResultVerifier,
    maxConcurrentTasks: Int = 2
) : AutoCloseable {
    private val workers: ThreadPoolExecutor

    init {
        require(maxConcurrentTasks in 1..4)
        workers = ThreadPoolExecutor(
            maxConcurrentTasks,
            maxConcurrentTasks,
            30,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(maxConcurrentTasks),
            { runnable -> Thread(runnable, "continuum-local-compute").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        ).apply { allowCoreThreadTimeOut(true) }
    }

    fun execute(
        task: ComputeTask,
        nowMillis: Long,
        isCancelled: () -> Boolean = { false }
    ): ComputeResult {
        require(nowMillis >= 0)
        require(task.deadlineMillis > nowMillis) { "Compute task deadline has passed." }
        require(task.deadlineMillis - nowMillis <= ComputeTask.MAX_RUNTIME_MILLIS) {
            "Compute task exceeds the maximum runtime."
        }
        val stableTask = task.copy(inputData = task.inputData.copyOf())
        val node = registry.compatible(stableTask, nowMillis).firstOrNull()
            ?: throw IllegalStateException("No authorized compatible compute node is available.")
        val deadlineNanos = System.nanoTime() +
            (stableTask.deadlineMillis - nowMillis) * NANOS_PER_MILLI
        val guard = ComputeExecutionGuard(stableTask.deadlineMillis, deadlineNanos) {
            isCancelled() || !registry.isAvailable(node.manifest.nodeId, System.currentTimeMillis())
        }
        guard.checkActive(nowMillis)
        val future = try {
            workers.submit<ComputeResult> {
                node.adapter.execute(stableTask.copy(inputData = stableTask.inputData.copyOf()), guard)
            }
        } catch (_: RejectedExecutionException) {
            throw IllegalStateException("Local compute capacity is exhausted.")
        }
        val result = try {
            var completed: ComputeResult? = null
            while (completed == null) {
                guard.checkActive()
                val remainingNanos = deadlineNanos - System.nanoTime()
                try {
                    completed = checkNotNull(future.get(
                        minOf(remainingNanos, COMPUTE_CANCELLATION_POLL_NANOS),
                        TimeUnit.NANOSECONDS
                    ))
                } catch (_: TimeoutException) {
                    continue
                }
            }
            checkNotNull(completed)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: Throwable) {
            future.cancel(true)
            workers.queue.remove(future as Runnable)
            throw failure
        }
        guard.checkActive()
        val verifiedResult = result.copy(
            output = result.output.copyOf(),
            signature = result.signature.copyOf()
        )
        require(resultVerifier.verify(verifiedResult)) { "Compute result signature is invalid." }
        require(verifiedResult.taskId == stableTask.taskId) { "Compute result task identity mismatch." }
        require(verifiedResult.nodeId == node.manifest.nodeId) { "Compute result node identity mismatch." }
        require(verifiedResult.parentStateRoot == stableTask.parentStateRoot) { "Compute result state root mismatch." }
        require(verifiedResult.completedAtMillis in nowMillis..stableTask.deadlineMillis) {
            "Compute result completion time is outside the task deadline."
        }
        require(verifiedResult.output.size <= stableTask.maxOutputBytes) { "Compute result exceeds its output bound." }
        require(verifiedResult.outputHash == sha256Hex(verifiedResult.output)) {
            "Compute result content hash mismatch."
        }
        guard.checkActive()
        return verifiedResult
    }

    override fun close() {
        workers.shutdownNow()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val COMPUTE_CANCELLATION_POLL_NANOS = 50_000_000L
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
