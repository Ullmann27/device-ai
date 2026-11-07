package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.motion.Easings

/** Driven by the existing clock. */
class AvatarCameraMotion {
    var progress = 0f
        private set
    var workActive = false
        private set
    private var from = 0f
    private var elapsedMs = 0.0
    private var lastMs = -1L

    fun setWorkActive(active: Boolean, nowMs: Long) {
        if (active == workActive) return
        from = progress
        workActive = active
        elapsedMs = 0.0
        lastMs = nowMs
    }

    fun advance(nowMs: Long, motion: Boolean, durationScale: Float = 1f): Float {
        if (!motion || !durationScale.isFinite() || durationScale <= 0f) {
            reset()
            return progress
        }
        if (lastMs < 0L) { lastMs = nowMs; return progress }
        if (nowMs <= lastMs) return progress
        val delta = (nowMs - lastMs).coerceAtMost(100L)
        lastMs = nowMs
        elapsedMs = (elapsedMs + delta / durationScale.toDouble()).coerceAtMost(DURATION_MS.toDouble())
        val target = if (workActive) 1f else 0f
        val t = (elapsedMs / DURATION_MS).toFloat()
        progress = if (t >= 1f) target else from + (target - from) * Easings.easeInOutCubic.at(t)
        return progress
    }

    fun reset() {
        progress = 0f
        workActive = false
        from = 0f
        elapsedMs = 0.0
        lastMs = -1L
    }

    companion object {
        const val DURATION_MS = 520L
    }
}
