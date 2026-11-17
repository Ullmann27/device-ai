package at.heinz.deviceai.cognitive

import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

@JvmInline
value class ComputeNodeId(val value: String) {
    init { require(value.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "Invalid compute node id." } }
    override fun toString(): String = value
}

enum class ComputeBackend { CPU, GPU, NPU }
enum class ComputePermission { NONE, READ_ONLY_DATA }
enum class ComputeTaskType {
    REASONING,
    CRITIQUE,
    VERIFY,
    RESEARCH_SYNTHESIS,
    EMBEDDING,
    BENCHMARK
}

data class NodeCapabilityManifest(
    val nodeId: ComputeNodeId,
    val ownerBinding: String,
    val taskTypes: Set<ComputeTaskType>,
    val backends: Set<ComputeBackend>,
    val maxRamMb: Int,
    val maxRuntimeMillis: Long,
    val maxOutputBytes: Int,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long
) {
    init {
        require(ownerBinding.matches(Regex("[A-Za-z0-9._:@/-]{1,256}")))
        require(taskTypes.isNotEmpty() && backends.isNotEmpty())
        require(maxRamMb in 128..1_048_576)
        require(maxRuntimeMillis in 100..3_600_000)
        require(maxOutputBytes in 1..4 * 1024 * 1024)
        require(issuedAtMillis >= 0 && expiresAtMillis > issuedAtMillis)
    }

    fun canonicalBytes(): ByteArray = buildString {
        append(nodeId.value).append('|').append(ownerBinding).append('|')
        append(taskTypes.map { it.name }.sorted().joinToString(",")).append('|')
        append(backends.map { it.name }.sorted().joinToString(",")).append('|')
        append(maxRamMb).append('|').append(maxRuntimeMillis).append('|').append(maxOutputBytes).append('|')
        append(issuedAtMillis).append('|').append(expiresAtMillis)
    }.toByteArray(Charsets.UTF_8)
}

data class SignedNodeCapabilityManifest(
    val manifest: NodeCapabilityManifest,
    val signature: ByteArray
) {
    init { require(signature.isNotEmpty() && signature.size <= 4_096) }
}

fun interface NodeManifestSignatureVerifier {
    fun verify(envelope: SignedNodeCapabilityManifest): Boolean
}

class ComputeNodeRegistry(
    private val verifier: NodeManifestSignatureVerifier,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val manifests = ConcurrentHashMap<ComputeNodeId, NodeCapabilityManifest>()
    private val revoked = ConcurrentHashMap.newKeySet<ComputeNodeId>()

    @Synchronized
    fun register(envelope: SignedNodeCapabilityManifest): NodeCapabilityManifest {
        // Verify exactly the immutable values retained by the registry. Kotlin Set
        // is only read-only at the type level; retaining caller-owned sets lets a
        // valid signature become permission to mutate capabilities afterwards.
        val manifest = envelope.manifest.copy(
            taskTypes = Collections.unmodifiableSet(HashSet(envelope.manifest.taskTypes)),
            backends = Collections.unmodifiableSet(HashSet(envelope.manifest.backends))
        )
        val snapshot = SignedNodeCapabilityManifest(manifest, envelope.signature.copyOf())
        require(verifier.verify(snapshot)) { "Compute node capability signature verification failed." }
        val now = nowMillis()
        require(manifest.issuedAtMillis <= now && manifest.expiresAtMillis > now) {
            "Compute node capability manifest is not currently valid."
        }
        require(manifest.nodeId !in revoked) { "Compute node is revoked." }
        manifests[manifest.nodeId] = manifest
        return manifest
    }

    @Synchronized fun revoke(nodeId: ComputeNodeId) {
        revoked += nodeId
        manifests.remove(nodeId)
    }

    @Synchronized fun isRevoked(nodeId: ComputeNodeId): Boolean = nodeId in revoked

    @Synchronized fun authorized(): List<NodeCapabilityManifest> {
        val now = nowMillis()
        return manifests.values.filter {
            it.nodeId !in revoked && it.issuedAtMillis <= now && it.expiresAtMillis > now
        }.sortedBy { it.nodeId.value }
    }

    @Synchronized fun get(nodeId: ComputeNodeId): NodeCapabilityManifest? {
        val now = nowMillis()
        return manifests[nodeId]?.takeIf {
            nodeId !in revoked && it.issuedAtMillis <= now && it.expiresAtMillis > now
        }
    }
}

data class ComputeTask(
    val taskId: String,
    val type: ComputeTaskType,
    val inputObjectIds: List<ContinuumObjectId>,
    val inlineContext: String,
    val requiredRamMb: Int,
    val requiredBackend: ComputeBackend? = null,
    val maxRuntimeMillis: Long,
    val maxOutputBytes: Int,
    val permission: ComputePermission,
    val deadlineMillis: Long,
    val parentStateRoot: ContinuumObjectId?,
    val assignedNodeId: ComputeNodeId? = null
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(inputObjectIds.size <= 64 && inputObjectIds.distinct().size == inputObjectIds.size)
        // Limit the actual UTF-8 payload; Unicode can require multiple bytes
        // per character and must not bypass the compute-task resource budget.
        require(inlineContext.toByteArray(Charsets.UTF_8).size <= 16_000)
        require(!containsCredentialLikeMaterial(inlineContext)) {
            "Credential-like material is not permitted in compute tasks."
        }
        require(requiredRamMb in 0..1_048_576)
        require(maxRuntimeMillis in 100..3_600_000)
        require(maxOutputBytes in 1..4 * 1024 * 1024)
        require(deadlineMillis >= 0)
    }
}

class ComputeScheduler(
    private val registry: ComputeNodeRegistry,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun select(task: ComputeTask): NodeCapabilityManifest? {
        val now = nowMillis()
        if (task.deadlineMillis <= now) return null
        return registry.authorized().asSequence()
            // Registry and scheduler may use independently supplied clocks. Never
            // assign work to a node before its grant starts or when its grant
            // could expire before the task's maximum permitted runtime ends.
            .filter { it.issuedAtMillis <= now && it.expiresAtMillis > now &&
                it.expiresAtMillis - now >= task.maxRuntimeMillis }
            .filter { task.assignedNodeId == null || task.assignedNodeId == it.nodeId }
            .filter { task.type in it.taskTypes }
            .filter { task.requiredBackend == null || task.requiredBackend in it.backends }
            .filter { task.requiredRamMb <= it.maxRamMb }
            .filter { task.maxRuntimeMillis <= it.maxRuntimeMillis }
            .filter { task.maxOutputBytes <= it.maxOutputBytes }
            .sortedWith(
                compareBy<NodeCapabilityManifest> { it.maxRamMb - task.requiredRamMb }
                    .thenBy { it.nodeId.value }
            )
            .firstOrNull()
    }
}

data class ComputeResult(
    val taskId: String,
    val nodeId: ComputeNodeId,
    val output: String,
    val outputSha256: String,
    val completedAtMillis: Long
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(output.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024)
        require(outputSha256.matches(Regex("[a-f0-9]{64}")))
        require(completedAtMillis >= 0)
    }

    fun verifyHash(): Boolean = sha256Bytes(output.toByteArray(Charsets.UTF_8)).toHex() == outputSha256

    companion object {
        fun create(taskId: String, nodeId: ComputeNodeId, output: String, completedAtMillis: Long): ComputeResult =
            ComputeResult(
                taskId,
                nodeId,
                output,
                sha256Bytes(output.toByteArray(Charsets.UTF_8)).toHex(),
                completedAtMillis
            )
    }
}

data class SignedComputeResult(val result: ComputeResult, val signature: ByteArray) {
    init { require(signature.isNotEmpty() && signature.size <= 4_096) }
}

fun interface ComputeResultSignatureVerifier {
    fun verify(envelope: SignedComputeResult): Boolean
}

object ComputeResultValidator {
    fun validate(
        task: ComputeTask,
        envelope: SignedComputeResult,
        registry: ComputeNodeRegistry,
        verifier: ComputeResultSignatureVerifier
    ): Boolean {
        val result = envelope.result
        val node = registry.get(result.nodeId) ?: return false
        if (result.taskId != task.taskId) return false
        if (task.type !in node.taskTypes) return false
        if (!result.verifyHash()) return false
        if (result.output.toByteArray(Charsets.UTF_8).size > task.maxOutputBytes) return false
        if (result.completedAtMillis > task.deadlineMillis) return false
        return verifier.verify(envelope)
    }
}

/**
 * Strict one-shot verifier for results of explicitly assigned compute tasks.
 * A result may be accepted only once; cancellations are terminal. This is an
 * opt-in replacement for the legacy stateless validation helper, not a device
 * execution path or an automatic external compute enrollment facility.
 */
class ComputeResultVerifier(
    private val registry: ComputeNodeRegistry,
    private val signatureVerifier: ComputeResultSignatureVerifier,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val accepted = HashSet<String>()
    private val cancelled = HashSet<String>()

    fun cancel(taskId: String): Boolean = synchronized(lock) {
        if (!taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) return@synchronized false
        if (taskId in accepted || taskId in cancelled) return@synchronized false
        if (accepted.size + cancelled.size >= MAX_TRACKED_RESULTS) return@synchronized false
        cancelled.add(taskId)
    }

    fun validate(task: ComputeTask, envelope: SignedComputeResult): Boolean = synchronized(lock) {
        val result = envelope.result
        if (task.taskId in cancelled || task.taskId in accepted) return@synchronized false
        if (accepted.size + cancelled.size >= MAX_TRACKED_RESULTS) return@synchronized false
        if (nowMillis() >= task.deadlineMillis) return@synchronized false
        if (task.taskId != result.taskId) return@synchronized false
        if (task.assignedNodeId == null || task.assignedNodeId != result.nodeId) return@synchronized false
        val node = registry.get(result.nodeId) ?: return@synchronized false
        if (task.type !in node.taskTypes) return@synchronized false
        if (task.requiredBackend != null && task.requiredBackend !in node.backends) return@synchronized false
        if (task.requiredRamMb > node.maxRamMb ||
            task.maxRuntimeMillis > node.maxRuntimeMillis ||
            task.maxOutputBytes > node.maxOutputBytes) return@synchronized false
        if (!result.verifyHash()) return@synchronized false
        if (result.output.toByteArray(Charsets.UTF_8).size > task.maxOutputBytes) return@synchronized false
        if (result.completedAtMillis > task.deadlineMillis ||
            result.completedAtMillis > nowMillis()) return@synchronized false
        if (!runCatching { signatureVerifier.verify(envelope) }.getOrDefault(false)) return@synchronized false
        accepted.add(task.taskId)
    }
}

private const val MAX_TRACKED_RESULTS = 10_000

fun interface LocalComputeWorker {
    fun execute(task: ComputeTask): ComputeResult
}

private fun containsCredentialLikeMaterial(text: String): Boolean {
    if (text.isBlank()) return false
    val credentialAssignment = Regex(
        "(?i)\\b(password|passwd|token|secret|credential|authorization|api[_-]?key)\\b\\s*[:=]\\s*\\S+"
    )
    val bearer = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}")
    val privateKey = Regex("(?i)-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")
    val privateAssignment = Regex("(?i)\\bprivate[_-]?key\\b\\s*[:=]\\s*\\S+")
    return credentialAssignment.containsMatchIn(text) ||
        bearer.containsMatchIn(text) || privateKey.containsMatchIn(text) ||
        privateAssignment.containsMatchIn(text)
}
