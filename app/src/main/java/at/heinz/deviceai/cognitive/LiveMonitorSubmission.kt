package at.heinz.deviceai.cognitive

/** Render submission, not panel presentation. */
internal class LiveMonitorSubmission {
    internal class ImportedFrame internal constructor(
        internal val owner: LiveMonitorSubmission,
        internal val generation: Long,
        internal val status: LiveMonitorStatus,
        internal val sampledAtNanos: Long
    )

    private data class Receipt(val frame: ImportedFrame, val submittedAtNanos: Long)
    private var generation = 0L
    private var receipt: Receipt? = null

    /** After acquired-image call succeeds. */
    @Synchronized
    fun imported(status: LiveMonitorStatus, nowNanos: Long): ImportedFrame? {
        val binding = status.binding ?: return null
        if (status.state != LiveMonitorState.LIVE || status.meta == null || status.sequence <= 0L ||
            status.ageMillis == null || status.ageMillis !in 0..binding.staleAfterMillis) return null
        return ImportedFrame(this, generation, status, nowNanos)
    }

    /** Call after successful render/endFrame. */
    @Synchronized
    fun submitted(frame: ImportedFrame, current: LiveMonitorStatus, nowNanos: Long): Boolean {
        if (!usable(frame, current, nowNanos)) return false
        receipt = Receipt(frame, nowNanos)
        return true
    }

    @Synchronized
    fun isReady(current: LiveMonitorStatus, nowNanos: Long): Boolean = readyReceipt(current, nowNanos) != null

    /** Submitted source; freshness rechecked here. */
    @Synchronized
    fun submittedFrame(current: LiveMonitorStatus, nowNanos: Long): LiveMonitorStatus? {
        val frame = readyReceipt(current, nowNanos)?.frame ?: return null
        return frame.status.copy(ageMillis = requireNotNull(frame.status.ageMillis) +
            (nowNanos - frame.sampledAtNanos) / 1_000_000)
    }

    private fun readyReceipt(current: LiveMonitorStatus, nowNanos: Long): Receipt? {
        val latest = receipt ?: return null
        val elapsed = nowNanos - latest.submittedAtNanos
        return latest.takeIf { usable(it.frame, current, nowNanos) && elapsed >= 0L &&
            elapsed / 1_000_000 <= requireNotNull(current.binding).staleAfterMillis }
    }

    /** Older fresh images remain admissible. */
    @Synchronized
    fun usable(frame: ImportedFrame?, current: LiveMonitorStatus, nowNanos: Long): Boolean {
        if (frame == null || frame.owner !== this || frame.generation != generation) return false
        val imported = frame.status
        val binding = imported.binding ?: return false
        val meta = imported.meta ?: return false
        val age = imported.ageMillis ?: return false
        val currentMeta = current.meta ?: return false
        val elapsed = nowNanos - frame.sampledAtNanos
        return current.state == LiveMonitorState.LIVE && current.binding == binding &&
            current.sourceEpoch == imported.sourceEpoch &&
            current.sequence >= imported.sequence && currentMeta.sourceWindowId == meta.sourceWindowId &&
            currentMeta.sourceViewport == meta.sourceViewport && currentMeta.width == meta.width &&
            currentMeta.height == meta.height && elapsed >= 0L &&
            elapsed / 1_000_000 <= binding.staleAfterMillis - age
    }

    /** Surface, import, binding or visibility. */
    @Synchronized
    fun invalidate() {
        generation++
        receipt = null
    }
}
