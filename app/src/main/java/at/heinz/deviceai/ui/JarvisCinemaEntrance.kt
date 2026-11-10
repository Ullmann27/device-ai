package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Calculated cinema entrance and exit for cockpit panels, dock buttons and the three work windows.
 *
 * One clock: callers pass milliseconds since a real [CockpitAnimationEvent]. Light builds
 * rim → glass → content (and tears down in reverse on the way out). Perspective uses the existing
 * [JarvisMotion.Depth.entranceTilt] plus a Z scale offset. Reduced motion and
 * [JarvisRenderQuality.Budget.animate] == false settle instantly and never sweep.
 */
object JarvisCinemaEntrance {
    enum class Kind { PANEL, DOCK, WORK_WINDOW }

    enum class Trigger { ENTER, STEP, EXIT, IGNORE }

    val SPRING_MS: Long get() = JarvisMotion.widgetSpring.settleMs
    const val STAGGER_MS = 70L
    const val DOCK_EXTRA_MS = 40L
    const val WORK_EXTRA_MS = 90L
    const val EXIT_MS = 420L
    const val Z_START = 0.22f
    const val RISE_DP = 18f
    const val RIM_UNTIL = 0.32f
    const val GLASS_UNTIL = 0.62f

    data class Pose(
        val travel: Float,
        val dx: Float,
        val dy: Float,
        val scale: Float,
        val tiltY: Float,
        val rimAlpha: Float,
        val glassAlpha: Float,
        val contentAlpha: Float,
        val settled: Boolean,
        val hidden: Boolean,
    )

    val SETTLED = Pose(1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, settled = true, hidden = false)

    fun staggerMs(kind: Kind, rank: Int): Long {
        val r = rank.coerceAtLeast(0)
        val extra = when (kind) {
            Kind.PANEL -> 0L
            Kind.DOCK -> DOCK_EXTRA_MS
            Kind.WORK_WINDOW -> WORK_EXTRA_MS
        }
        return r * STAGGER_MS + extra
    }

    fun ensembleDurationMs(count: Int, animatorScale: Float = 1f, exiting: Boolean = false): Long {
        val last = staggerMs(Kind.WORK_WINDOW, (count - 1).coerceAtLeast(0))
        val body = if (exiting) EXIT_MS else SPRING_MS
        val scale = if (animatorScale <= 0f) 0f else animatorScale
        return if (scale == 0f) 0L else ((last + body) * scale).toLong()
    }

    fun springTravel(elapsedMs: Long, durationMs: Long = SPRING_MS): Float {
        if (elapsedMs <= 0L) return 0f
        if (elapsedMs >= durationMs) return 1f
        if (durationMs == SPRING_MS) return JarvisMotion.widgetSpring.valueAtMs(elapsedMs)
        val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        return JarvisMotion.widgetSpring.valueAt(t * JarvisMotion.widgetSpring.settleSec)
    }

    fun pose(
        kind: Kind,
        rank: Int,
        elapsedMs: Long,
        exiting: Boolean = false,
        side: Float = 0f,
        budget: JarvisRenderQuality.Budget? = null,
        animatorScale: Float = 1f,
    ): Pose {
        val reduced = animatorScale <= 0f || budget?.animate == false
        if (!exiting && elapsedMs < 0L) return poseAt(0f, side).copy(hidden = true)
        if (reduced) return if (exiting) poseAt(0f, 0f).copy(hidden = true, settled = true) else SETTLED
        val local = elapsedMs - staggerMs(kind, rank)
        val travel = if (exiting) {
            1f - springTravel(local, EXIT_MS)
        } else {
            springTravel(local, SPRING_MS)
        }
        return poseAt(travel, side)
    }

    fun poseAt(travel: Float, side: Float): Pose {
        val visual = travel.coerceIn(0f, 1f)
        val remaining = 1f - visual
        val rim = smoothstep(0f, RIM_UNTIL, visual)
        val glass = smoothstep(RIM_UNTIL, GLASS_UNTIL, visual)
        val content = smoothstep(GLASS_UNTIL, 1f, visual)
        return Pose(
            travel = travel,
            dx = 0f,
            dy = RISE_DP * remaining * remaining,
            scale = 1f - Z_START * remaining,
            tiltY = JarvisMotion.Depth.entranceTilt(visual, side),
            rimAlpha = rim,
            glassAlpha = glass,
            contentAlpha = content,
            settled = visual >= 0.999f,
            hidden = visual <= 0.001f && rim <= 0.001f,
        )
    }

    fun triggerFor(event: CockpitAnimationEvent): Trigger = when (event) {
        is CockpitAnimationEvent.CommandFlyIn,
        is CockpitAnimationEvent.ActionStarted,
        is CockpitAnimationEvent.DocumentQuestion -> Trigger.ENTER
        is CockpitAnimationEvent.ProcessTimeline ->
            if (event.steps.any { it.state == StepState.DONE }) Trigger.STEP else Trigger.IGNORE
        is CockpitAnimationEvent.TurnEnded -> Trigger.EXIT
        else -> Trigger.IGNORE
    }

    fun kindOf(slot: JarvisShellLayout.Slot?): Kind = when (slot) {
        JarvisShellLayout.Slot.WORK_DOCUMENT,
        JarvisShellLayout.Slot.WORK_PROCESS,
        JarvisShellLayout.Slot.WORK_DRAFT -> Kind.WORK_WINDOW
        JarvisShellLayout.Slot.BOTTOM_NAV -> Kind.DOCK
        else -> Kind.PANEL
    }

    fun rankOf(slot: JarvisShellLayout.Slot?, fallback: Int): Int = when (slot) {
        JarvisShellLayout.Slot.WORK_DOCUMENT -> 0
        JarvisShellLayout.Slot.WORK_PROCESS -> 1
        JarvisShellLayout.Slot.WORK_DRAFT -> 2
        else -> fallback
    }

    /**
     * Scanline / Fresnel overlay for the existing holographic head. Host preview uses this on top of
     * [at.heinz.deviceai.avatar.ContinuumAvatarRenderer]; the device path composites it over Filament.
     * Not a second renderer.
     */
    fun drawHologramOverlay(p: ContinuumPainter, box: Box, timeSec: Float, energy: Float) {
        if (box.width <= 2f || box.height <= 2f) return
        val pulse = (0.55f + 0.45f * energy.coerceIn(0f, 1f)).coerceIn(0.2f, 1f)
        val phase = ((timeSec * 28f) % 3.2f)
        p.setAdditive(true)
        var y = box.top + phase
        while (y < box.bottom) {
            val a = 0.055f * pulse
            p.drawLine(box.left + 6f, y, box.right - 6f, y, 0.7f, Fill.Solid(Colors.cyan), a)
            y += 3.1f
        }
        val r = minOf(box.width, box.height) * 0.42f
        p.strokeOval(box.centerX - r * 0.78f, box.centerY - r * 1.05f,
            box.centerX + r * 0.78f, box.centerY + r * 1.05f, 1.6f, Fill.Solid(Colors.cyan), 0.38f * pulse)
        p.strokeOval(box.centerX - r * 0.86f, box.centerY - r * 1.14f,
            box.centerX + r * 0.86f, box.centerY + r * 1.14f, 0.7f, Fill.Solid(Colors.violet), 0.22f * pulse)
        p.setAdditive(false)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
