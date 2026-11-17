package at.heinz.deviceai.ui

/** Admission for the existing clock. */
internal class AvatarFramePacing {
    private var lastFrameNanos = Long.MIN_VALUE

    fun admit(frameTimeNanos: Long, frameDelayMillis: Long): Boolean {
        if (frameTimeNanos < 0L) return false
        val previous = lastFrameNanos
        if (previous != Long.MIN_VALUE &&
            (frameTimeNanos <= previous || frameTimeNanos - previous < intervalNanos(frameDelayMillis))) return false
        lastFrameNanos = frameTimeNanos
        return true
    }

    fun delayMillis(nowNanos: Long, frameDelayMillis: Long, refreshRate: Float): Long {
        val previous = lastFrameNanos
        if (previous == Long.MIN_VALUE || nowNanos < previous) return 0L
        val remaining = intervalNanos(frameDelayMillis) - (nowNanos - previous)
        if (remaining <= 0L) return 0L
        val hz = if (refreshRate.isFinite() && refreshRate > 0f) refreshRate.toDouble() else 60.0
        val vsyncLead = (1_000_000_000.0 / hz).toLong()
        // Wake early; admit at deadline.
        return if (vsyncLead >= remaining) 0L else (remaining - vsyncLead) / NANOS_PER_MILLI
    }

    fun reset() { lastFrameNanos = Long.MIN_VALUE }

    private fun intervalNanos(delayMillis: Long): Long =
        delayMillis.coerceIn(1L, Long.MAX_VALUE / NANOS_PER_MILLI) * NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
