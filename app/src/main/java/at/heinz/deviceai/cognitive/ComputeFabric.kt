package at.heinz.deviceai.cognitive

import java.util.concurrent.ConcurrentHashMap

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
    val maxInputBytes: Int,
    val maxOutputBytes: Int,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long
) {
    init {
        require(ownerBinding.matches(Regex("[A-Za-z0-9._:@/-]{1,256}")))
        require(taskTypes.isNotEmpty() && backends.isNotEmpty())
        require(maxRamMb in 128..1_048_576)
        require(maxRuntimeMillis in 100..3_600_000)
        require(maxInputBytes in 1..MAX_INPUT_BYTES)
        require(maxOutputBytes in 1..4 * 1024 * 1024)
        require(issuedAtMillis >= 0 && expiresAtMillis > issuedAtMillis)
    }

    fun canonicalBytes(): ByteArray = buildString {
        append(nodeId.value).append('|').append(ownerBinding).append('|')
        append(taskTypes.map { it.name }.sorted().joinToString(",")).append('|')
        append(backends.map { it.name }.sorted().joinToString(",")).append('|')
        append(maxRamMb).append('|').append(maxRuntimeMillis).append('|')
        append(maxInputBytes).append('|').append(maxOutputBytes).append('|')
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

    fun register(envelope: SignedNodeCapabilityManifest): NodeCapabilityManifest {
        require(verifier.verify(envelope)) { "Compute node capability signature verification failed." }
        val manifest = envelope.manifest
        require(manifest.expiresAtMillis > nowMillis()) { "Compute node capability manifest is expired." }
        require(manifest.nodeId !in revoked) { "Compute node is revoked." }
        manifests[manifest.nodeId] = manifest
        return manifest
    }

    fun revoke(nodeId: ComputeNodeId) {
        revoked += nodeId
        manifests.remove(nodeId)
    }

    fun isRevoked(nodeId: ComputeNodeId): Boolean = nodeId in revoked

    fun authorized(): List<NodeCapabilityManifest> =
        manifests.values.filter { it.nodeId !in revoked && it.expiresAtMillis > nowMillis() }
            .sortedBy { it.nodeId.value }

    fun get(nodeId: ComputeNodeId): NodeCapabilityManifest? =
        manifests[nodeId]?.takeIf { nodeId !in revoked && it.expiresAtMillis > nowMillis() }
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
    val assignedNodeId: ComputeNodeId? = null,
    val maxInputBytes: Int = MAX_INPUT_BYTES
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(inputObjectIds.size <= 64 && inputObjectIds.distinct().size == inputObjectIds.size)
        require(inlineContext.toByteArray(Charsets.UTF_8).size <= maxInputBytes)
        require(!containsCredentialLikeMaterial(inlineContext)) {
            "Credential-like material is not permitted in compute tasks."
        }
        require(maxInputBytes in 1..MAX_INPUT_BYTES)
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
        if (task.deadlineMillis <= nowMillis()) return null
        return registry.authorized().asSequence()
            .filter { task.type in it.taskTypes }
            .filter { task.requiredBackend == null || task.requiredBackend in it.backends }
            .filter { task.requiredRamMb <= it.maxRamMb }
            .filter { task.maxRuntimeMillis <= it.maxRuntimeMillis }
            .filter { task.maxInputBytes <= it.maxInputBytes }
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

class ComputeResultVerifier(
    private val registry: ComputeNodeRegistry,
    private val signatureVerifier: ComputeResultSignatureVerifier,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val acceptedTaskIds = HashSet<String>()
    private val cancelledTaskIds = HashSet<String>()

    fun cancel(taskId: String): Boolean = synchronized(lock) {
        if (!taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) return false
        if (taskId in acceptedTaskIds || taskId in cancelledTaskIds) return false
        if (acceptedTaskIds.size + cancelledTaskIds.size >= MAX_TRACKED_TASKS) return false
        cancelledTaskIds.add(taskId)
    }

    fun validate(task: ComputeTask, envelope: SignedComputeResult): Boolean = synchronized(lock) {
        runCatching {
            val result = envelope.result
            if (task.taskId in cancelledTaskIds || task.taskId in acceptedTaskIds) return false
            if (acceptedTaskIds.size + cancelledTaskIds.size >= MAX_TRACKED_TASKS) return false
            if (task.deadlineMillis <= nowMillis()) return false
            if (result.taskId != task.taskId) return false
            if (task.assignedNodeId == null || result.nodeId != task.assignedNodeId) return false
            val node = registry.get(result.nodeId) ?: return false
            if (task.type !in node.taskTypes) return false
            if (task.requiredBackend != null && task.requiredBackend !in node.backends) return false
            if (task.requiredRamMb > node.maxRamMb || task.maxRuntimeMillis > node.maxRuntimeMillis) return false
            if (task.maxInputBytes > node.maxInputBytes || task.maxOutputBytes > node.maxOutputBytes) return false
            if (!result.verifyHash()) return false
            if (result.output.toByteArray(Charsets.UTF_8).size > task.maxOutputBytes) return false
            if (result.completedAtMillis > task.deadlineMillis || result.completedAtMillis > nowMillis()) {
                return false
            }
            if (!signatureVerifier.verify(envelope)) return false
            acceptedTaskIds.add(task.taskId)
        }.getOrDefault(false)
    }
}

private const val MAX_INPUT_BYTES = 4 * 1024 * 1024
private const val MAX_TRACKED_TASKS = 10_000

fun interface LocalComputeWorker {
    fun execute(task: ComputeTask): ComputeResult
}

private fun containsCredentialLikeMaterial(text: String): Boolean {
    if (text.isBlank()) return false
    val credentialAssignment = Regex(
        "(?i)\\b(password|passwd|token|secret|credential|authorization|api[_-]?key|private[_-]?key)\\b\\s*[:=]\\s*\\S+"
    )
    val bearer = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}")
    val privateKey = Regex("(?i)-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")
    return credentialAssignment.containsMatchIn(text) ||
        bearer.containsMatchIn(text) ||
        privateKey.containsMatchIn(text)
}
