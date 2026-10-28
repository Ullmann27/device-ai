package at.heinz.deviceai.localmodel

/** Bounded, device-local import progress. Never stores a source URI, file name,
 * model payload, owner key or free-form exception text. */
enum class LocalModelImportPhase {
    STREAM_OPENED,
    COPYING,
    COPY_COMPLETED,
    HASH_VERIFIED,
    INSPECTED,
    COMMITTED,
    FAILED
}

data class LocalModelImportSnapshot(
    val phase: LocalModelImportPhase,
    val copiedBytes: Long,
    val elapsedMs: Long
)

class LocalModelImportProgress(
    private val clockNanos: () -> Long = System::nanoTime
) {
    private val startedAt = clockNanos()
    private var latest: LocalModelImportSnapshot? = null

    /** The caller controls which phases are reached; this class never claims
     * model compatibility or native generation from a copied byte count. */
    @Synchronized
    fun record(phase: LocalModelImportPhase, copiedBytes: Long): LocalModelImportSnapshot {
        require(copiedBytes in 0L..MAX_MODEL_BYTES) { "Import progress byte limit" }
        val previous = latest
        require(previous?.phase != LocalModelImportPhase.COMMITTED &&
            previous?.phase != LocalModelImportPhase.FAILED) { "Import progress already terminal" }
        if (previous == null) {
            require(phase == LocalModelImportPhase.STREAM_OPENED ||
                phase == LocalModelImportPhase.FAILED) { "First import phase must open stream" }
        } else if (phase != LocalModelImportPhase.FAILED) {
            require(phase.ordinal == previous.phase.ordinal + 1 ||
                (phase == LocalModelImportPhase.COPYING &&
                    previous.phase == LocalModelImportPhase.COPYING)) {
                "Import phase out of sequence" }
        }
        if (previous != null) require(copiedBytes >= previous.copiedBytes) {
            "Import copied byte count cannot decrease" }
        if (phase == LocalModelImportPhase.COMMITTED ||
            phase == LocalModelImportPhase.HASH_VERIFIED ||
            phase == LocalModelImportPhase.INSPECTED ||
            phase == LocalModelImportPhase.COPY_COMPLETED) {
            require(copiedBytes > 0) { "Cannot certify an empty model" }
        }
        val elapsedNanos = clockNanos() - startedAt
        val ms = if (elapsedNanos >= 0) elapsedNanos / 1_000_000L else 0L
        val snapshot = LocalModelImportSnapshot(phase, copiedBytes, ms.coerceAtLeast(previous?.elapsedMs ?: 0L))
        latest = snapshot
        return snapshot
    }

    @Synchronized
    fun snapshot(): LocalModelImportSnapshot? = latest

    private companion object {
        const val MAX_MODEL_BYTES = 64L * 1024 * 1024 * 1024
    }
}
