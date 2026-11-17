package at.heinz.deviceai.cognitive

/**
 * Read-only resource projection of the existing signed registry. The JSON is a
 * planning snapshot, not a signed grant. Never includes task context, owner
 * identity, keys, endpoints or assertions about model weights/locality.
 * Recheck the live registry and owner grants before any later execution.
 */
class VirtualModelFabricSnapshotExporter(
    private val registry: ComputeNodeRegistry,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun export(task: ComputeTask, taskSha256: String): String {
        require(taskSha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid task fingerprint." }
        val now = nowMillis()
        require(now in 0..MAX_SAFE_INTEGER - SNAPSHOT_TTL_MILLIS &&
            task.deadlineMillis in (now + 1)..MAX_SAFE_INTEGER) { "Task deadline expired or unsupported." }
        val expires = minOf(now + SNAPSHOT_TTL_MILLIS, task.deadlineMillis)
        val scheduler = ComputeScheduler(registry) { now }
        val authorized = registry.authorized()
        require(authorized.size <= 32) { "Too many nodes for a bounded planning snapshot." }
        // Use the canonical scheduler with explicit assignment, so resource
        // compatibility cannot diverge into a second scheduling implementation.
        val nodes = authorized.filter { node ->
            (task.assignedNodeId == null || task.assignedNodeId == node.nodeId) &&
                node.issuedAtMillis <= now && node.expiresAtMillis >= now + task.maxRuntimeMillis &&
                node.expiresAtMillis <= MAX_SAFE_INTEGER &&
                scheduler.select(task.copy(assignedNodeId = node.nodeId)) == node &&
                registry.get(node.nodeId) == node
        }
        // IDs and hashes are constrained ASCII; enum strings and decimal numbers
        // need no JSON escaping. No caller-controlled free text is serialized.
        return buildString {
            append("{\"schema\":\"continuum-compute-planning-snapshot\",\"version\":1,")
            append("\"createdAtMillis\":").append(now).append(",\"expiresAtMillis\":").append(expires)
            append(",\"executable\":false,\"task\":{")
            append("\"id\":\"").append(task.taskId).append("\",\"sha256\":\"").append(taskSha256)
            append("\",\"type\":\"").append(task.type.name).append("\",\"requiredRamMb\":").append(task.requiredRamMb)
            append(",\"requiredBackend\":").append(task.requiredBackend?.let { "\"${it.name}\"" } ?: "null")
            append(",\"maxRuntimeMillis\":").append(task.maxRuntimeMillis)
            append(",\"maxOutputBytes\":").append(task.maxOutputBytes)
            append(",\"inputBytes\":").append(task.inlineContext.toByteArray(Charsets.UTF_8).size)
            append(",\"deadlineMillis\":").append(task.deadlineMillis)
            append(",\"assignedNodeId\":").append(task.assignedNodeId?.let { "\"${it.value}\"" } ?: "null")
            append("},\"nodes\":[")
            nodes.forEachIndexed { index, n ->
                if (index > 0) append(',')
                append("{\"nodeId\":\"").append(n.nodeId.value).append("\",\"manifestSha256\":\"")
                append(sha256Bytes(n.canonicalBytes()).toHex()).append("\",\"taskTypes\":[")
                append(n.taskTypes.map { "\"${it.name}\"" }.sorted().joinToString(","))
                append("],\"backends\":[").append(n.backends.map { "\"${it.name}\"" }.sorted().joinToString(","))
                append("],\"maxRamMb\":").append(n.maxRamMb)
                append(",\"maxRuntimeMillis\":").append(n.maxRuntimeMillis)
                append(",\"maxOutputBytes\":").append(n.maxOutputBytes)
                append(",\"issuedAtMillis\":").append(n.issuedAtMillis)
                append(",\"expiresAtMillis\":").append(n.expiresAtMillis).append('}')
            }
            append("]}")
        }
    }

    private companion object {
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        const val SNAPSHOT_TTL_MILLIS = 30_000L
    }
}
