package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter

/** Caller-clocked, finite document transition. */
class Video3FileTunnel(seed: Int = 12) {
    private val scene = Video3WarpScene(seed)
    private var startedAtMs = -1L
    private var lastFrameMs = -1L
    private var durationMs = DURATION_MS

    val isActive: Boolean get() = startedAtMs >= 0L

    /** Repeated events never extend playback. */
    fun start(nowMs: Long, animatorScale: Float = 1f): Boolean {
        if (nowMs < 0L || !motionAllowed(animatorScale)) {
            clear()
            return false
        }
        if (isActive) {
            if (nowMs < lastFrameMs) {
                clear()
                return false
            }
            if (nowMs - startedAtMs < durationMs) return false
        }
        startedAtMs = nowMs
        lastFrameMs = nowMs
        durationMs = (DURATION_MS * animatorScale.coerceAtMost(1f)).toLong().coerceAtLeast(1L)
        return true
    }

    /** True requests another caller frame. */
    fun draw(
        painter: ContinuumPainter,
        width: Float,
        height: Float,
        nowMs: Long,
        animatorScale: Float = 1f,
        visible: Boolean = true,
        glow: Boolean = true,
    ): Boolean {
        if (!isActive) return false
        if (!visible || !motionAllowed(animatorScale) || nowMs < lastFrameMs ||
            nowMs - startedAtMs >= durationMs
        ) {
            clear()
            return false
        }
        lastFrameMs = nowMs
        val seconds = (nowMs - startedAtMs).toFloat() / durationMs * Video3WarpScene.DURATION_S
        if (!scene.render(painter, width, height, seconds, glow)) {
            clear()
            return false
        }
        return true
    }

    /** STOP, hidden, thermal, detach. */
    fun clear() {
        startedAtMs = -1L
        lastFrameMs = -1L
    }

    private fun motionAllowed(scale: Float) = scale.isFinite() && scale > 0f

    companion object {
        const val DURATION_MS = 3_000L
    }
}
