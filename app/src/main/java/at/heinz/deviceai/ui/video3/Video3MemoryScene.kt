package at.heinz.deviceai.ui.video3

/** Finite, caller-clocked scene state. */
internal class Video3MemoryScene {
    class Frame {
        var elapsedMs = 0L; internal set
        var opacity = 0f; internal set
        var orbProgress = 0f; internal set
        var returnProgress = 0f; internal set
    }
    private var startedAt = -1L
    private var lastFrame = -1L
    private val frame = Frame()
    val isActive: Boolean get() = startedAt >= 0L

    fun start(nowMs: Long): Boolean {
        if (nowMs < 0L) { clear(); return false }
        if (isActive) return false
        startedAt = nowMs
        lastFrame = nowMs
        return true
    }

    fun sample(nowMs: Long): Frame? {
        if (!isActive) return null
        val elapsed = nowMs - startedAt
        if (nowMs < lastFrame || elapsed >= DURATION_MS) { clear(); return null }
        lastFrame = nowMs
        frame.elapsedMs = elapsed
        frame.opacity = minOf(elapsed / 180f, (DURATION_MS - elapsed) / 700f).coerceIn(0f, 1f)
        frame.orbProgress = ((elapsed - 1_800L) / 700f).coerceIn(0f, 1f)
        frame.returnProgress = ((elapsed - 3_800L) / 1_000f).coerceIn(0f, 1f)
        return frame
    }

    fun clear() { startedAt = -1L; lastFrame = -1L }

    companion object { const val DURATION_MS = 4_800L }
}
