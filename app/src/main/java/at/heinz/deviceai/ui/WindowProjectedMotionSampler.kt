package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.WindowProjectedMotion
import kotlin.math.hypot

/**
 * Rate-limited publisher of [WindowProjectedMotion] samples for Bot 03.
 * Converts the actually projected visual centre (after dx/dy/scale/tilt) into
 * [at.heinz.deviceai.avatar.GazeTarget] space and never substitutes the rest rectangle.
 * Not a second clock: [atMillis] is the caller's cinema timestamp.
 */
class WindowProjectedMotionSampler {
    private val lastByWindow = linkedMapOf<String, WindowProjectedMotion>()
    private val lastSeen = linkedMapOf<String, WindowProjectedMotion>()
    private val active = linkedSetOf<String>()

    fun consider(
        turnId: String,
        atMillis: Long,
        windowId: String,
        projectedX: Float,
        projectedY: Float,
        headX: Float,
        headY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        phase: WindowProjectedMotion.Phase,
        accentArgb: Int? = null,
        reducedMotion: Boolean = false,
        moving: Boolean = true,
        windowPresent: Boolean = true,
    ): WindowProjectedMotion? {
        if (!windowPresent || turnId.isBlank() || windowId.isBlank()) return null
        if (!projectedX.isFinite() || !projectedY.isFinite()) return null
        if (viewportWidth <= 0f || viewportHeight <= 0f) return null
        if (phase != WindowProjectedMotion.Phase.CLOSED) active.add(windowId)
        if (phase == WindowProjectedMotion.Phase.CLOSED) return closed(turnId, windowId, atMillis)
        val gaze = JarvisWindowGaze.fromProjection(
            projectedX, projectedY, headX, headY, viewportWidth, viewportHeight)
        val next = WindowProjectedMotion(
            turnId = turnId,
            atMillis = atMillis,
            windowId = windowId,
            gazeX = gaze.x,
            gazeY = gaze.y,
            accentArgb = accentArgb,
            phase = phase,
        )
        lastSeen[windowId] = next
        if (reducedMotion && !moving) return null
        return publishIfChanged(next)
    }

    fun closed(turnId: String, windowId: String, atMillis: Long): WindowProjectedMotion? {
        if (turnId.isBlank() || windowId.isBlank()) return null
        val prev = lastByWindow[windowId] ?: lastSeen[windowId]
        if (prev?.phase == WindowProjectedMotion.Phase.CLOSED && prev.turnId == turnId) return null
        val event = WindowProjectedMotion(
            turnId = turnId,
            atMillis = atMillis,
            windowId = windowId,
            gazeX = prev?.gazeX ?: 0f,
            gazeY = prev?.gazeY ?: 0f,
            accentArgb = prev?.accentArgb,
            phase = WindowProjectedMotion.Phase.CLOSED,
        )
        lastByWindow[windowId] = event
        active.remove(windowId)
        return event
    }

    fun closeActive(turnId: String, atMillis: Long): List<WindowProjectedMotion> {
        if (turnId.isBlank()) return emptyList()
        return active.toList().mapNotNull { closed(turnId, it, atMillis) }
    }

    private fun publishIfChanged(next: WindowProjectedMotion): WindowProjectedMotion? {
        val prev = lastByWindow[next.windowId]
        if (prev?.phase == WindowProjectedMotion.Phase.CLOSED &&
            prev.turnId == next.turnId &&
            next.phase != WindowProjectedMotion.Phase.CLOSED) {
            return null
        }
        if (prev == null || prev.phase != next.phase || prev.turnId != next.turnId ||
            prev.accentArgb != next.accentArgb) {
            lastByWindow[next.windowId] = next
            return next
        }
        val delta = hypot(next.gazeX - prev.gazeX, next.gazeY - prev.gazeY)
        if (delta < MIN_GAZE_DELTA) return null
        val dt = next.atMillis - prev.atMillis
        if (dt < MIN_INTERVAL_MS && delta < FAST_GAZE_DELTA) return null
        lastByWindow[next.windowId] = next
        return next
    }

    companion object {
        const val MIN_GAZE_DELTA = 0.02f
        const val FAST_GAZE_DELTA = 0.08f
        const val MIN_INTERVAL_MS = 32L

        fun windowIdOf(slot: JarvisShellLayout.Slot): String? = when (slot) {
            JarvisShellLayout.Slot.WORK_DOCUMENT -> "work-document"
            JarvisShellLayout.Slot.WORK_PROCESS -> "work-process"
            JarvisShellLayout.Slot.WORK_DRAFT -> "work-draft"
            else -> null
        }
    }
}
