package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.motion.Easings

/** Samples the existing frame clock. */
class AvatarEntranceMotion {
    var scale = 1f
        private set
    private var startedAt = -1L
    private var shrinkAt = -1L
    private var shrinkFrom = PEAK_SCALE

    fun start(nowMs: Long) {
        startedAt = nowMs
        shrinkAt = -1L
        shrinkFrom = PEAK_SCALE
        scale = DEPTH_SCALE
    }

    fun shrink(nowMs: Long) {
        advance(nowMs)
        if (startedAt < 0L || shrinkAt >= 0L || nowMs - startedAt >= SHRINK_AT_MS) return
        shrinkAt = (nowMs - startedAt).coerceIn(0L, SHRINK_AT_MS)
        shrinkFrom = scale
    }

    fun advance(nowMs: Long) {
        if (startedAt < 0L) return
        val elapsed = (nowMs - startedAt).coerceAtLeast(0L)
        if (elapsed >= END_MS) { reset(); return }
        if (shrinkAt >= 0L || elapsed >= SHRINK_AT_MS) {
            val start = if (shrinkAt >= 0L) shrinkAt else SHRINK_AT_MS
            val from = if (shrinkAt >= 0L) shrinkFrom else PEAK_SCALE
            val t = ((elapsed - start).toFloat() / SHRINK_MS).coerceIn(0f, 1f)
            if (t >= 1f) reset() else scale = from + (1f - from) * Easings.easeInOutCubic.at(t)
        } else {
            val t = (elapsed.toFloat() / ARRIVE_MS).coerceIn(0f, 1f)
            scale = DEPTH_SCALE + (PEAK_SCALE - DEPTH_SCALE) * Easings.easeOutCubic.at(t)
        }
    }

    fun reset() {
        scale = 1f
        startedAt = -1L
        shrinkAt = -1L
        shrinkFrom = PEAK_SCALE
    }

    private companion object {
        const val DEPTH_SCALE = .12f
        const val PEAK_SCALE = 1.14f
        const val ARRIVE_MS = 2_000L
        const val SHRINK_AT_MS = 3_000L
        const val SHRINK_MS = 600L
        const val END_MS = SHRINK_AT_MS + SHRINK_MS
    }
}
