package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cockpit.TransitionPattern
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Binding of cockpit panels, dock buttons and the three work windows onto the existing
 * [HollywoodFuiConstruct] screenplay — not a second motion engine.
 *
 * One clock: callers pass milliseconds since a real [CockpitAnimationEvent]. Light follows the
 * construct (anchor 0–140, brackets 140–320, grid 320–560, glass 480–760, data 640–900;
 * close 420 ms, data first). The curved flight uses [HollywoodFuiConstruct.arcLiftDp] from a
 * real origin box; Z-slicing uses [HollywoodFuiConstruct.REAR_GLASS_Z_DP] / [CONTENT_Z_DP] /
 * [FRONT_BRACKET_Z_DP]. Reduced motion and [JarvisRenderQuality.Budget.animate] == false
 * settle instantly.
 */
object JarvisCinemaEntrance {
    enum class Kind { PANEL, DOCK, WORK_WINDOW }

    enum class Trigger { ENTER, STEP, EXIT, IGNORE }

    const val STAGGER_MS = 70L
    const val SETTLE_MS = HollywoodFuiConstruct.DATA_END_MS
    const val EXIT_MS = HollywoodFuiConstruct.CLOSE_END_MS

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
        val frame: HollywoodFuiConstruct.Frame = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS),
    )

    data class ProjectedWindow(
        val x: Float,
        val y: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val progress: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    val SETTLED = pose(Kind.PANEL, 0, SETTLE_MS, budget = null, animatorScale = 0f)

    fun staggerMs(kind: Kind, rank: Int): Long = rank.coerceAtLeast(0) * STAGGER_MS

    fun ensembleDurationMs(count: Int, animatorScale: Float = 1f, exiting: Boolean = false): Long {
        val last = staggerMs(Kind.WORK_WINDOW, (count - 1).coerceAtLeast(0))
        val body = if (exiting) EXIT_MS else SETTLE_MS
        val scale = if (animatorScale <= 0f) 0f else animatorScale
        return if (scale == 0f) 0L else ((last + body) * scale).toLong()
    }

    /** Screenplay travel: [HollywoodFuiConstruct.flight] (0 at rest, 1 at glass stand / [GLASS_END_MS]). */
    fun springTravel(elapsedMs: Long, durationMs: Long = SETTLE_MS): Float {
        if (elapsedMs <= 0L) return 0f
        if (durationMs == SETTLE_MS) return HollywoodFuiConstruct.flight(elapsedMs)
        if (elapsedMs >= durationMs) return 1f
        return HollywoodFuiConstruct.flight(
            (elapsedMs.toFloat() / durationMs.toFloat() * HollywoodFuiConstruct.GLASS_END_MS).toLong()
        )
    }

    fun constructFrame(
        elapsedMs: Long,
        exiting: Boolean = false,
        reducedMotion: Boolean = false,
    ): HollywoodFuiConstruct.Frame =
        if (exiting) HollywoodFuiConstruct.frame(
            HollywoodFuiConstruct.DATA_END_MS,
            closingElapsedMs = elapsedMs.coerceAtLeast(0L),
            reducedMotion = reducedMotion,
        ) else HollywoodFuiConstruct.frame(elapsedMs.coerceAtLeast(0L), reducedMotion = reducedMotion)

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
        if (!exiting && elapsedMs < 0L) {
            val frame = constructFrame(0L, reducedMotion = false)
            return poseFrom(frame, travel = 0f, side = side, reduced = false).copy(hidden = true)
        }
        if (reduced && !exiting) {
            val frame = constructFrame(SETTLE_MS, reducedMotion = true)
            return poseFrom(frame, travel = 1f, side = 0f, reduced = true)
        }
        if (reduced && exiting) {
            val frame = constructFrame(EXIT_MS, exiting = true, reducedMotion = true)
            return poseFrom(frame, travel = 0f, side = 0f, reduced = true).copy(hidden = true, settled = true)
        }
        val local = elapsedMs - staggerMs(kind, rank)
        val frame = constructFrame(local, exiting = exiting, reducedMotion = false)
        val travel = if (exiting) frame.closeFrame else HollywoodFuiConstruct.flight(local)
        return poseFrom(frame, travel, side, reduced = false)
    }

    /**
     * Event interface for Bot 03 (avatar gaze / window light): the projected centre of a window
     * flying from a real [origin] to its [destination] along [HollywoodFuiConstruct.flight] and
     * [HollywoodFuiConstruct.arcLiftDp]. Does not drive eyes or materials itself.
     */
    fun projectedWindow(
        origin: Box,
        destination: Box,
        elapsedMs: Long,
        exiting: Boolean = false,
        reducedMotion: Boolean = false,
        rank: Int = 0,
    ): ProjectedWindow {
        val local = elapsedMs - staggerMs(Kind.WORK_WINDOW, rank)
        val frame = constructFrame(local, exiting = exiting, reducedMotion = reducedMotion)
        val progress = when {
            reducedMotion && exiting -> 0f
            reducedMotion -> 1f
            exiting -> frame.closeFrame
            else -> HollywoodFuiConstruct.flight(local)
        }
        val lift = HollywoodFuiConstruct.arcLiftDp(progress, enabled = !reducedMotion)
        val left = origin.left + (destination.left - origin.left) * progress
        val top = origin.top + (destination.top - origin.top) * progress + lift
        val right = origin.right + (destination.right - origin.right) * progress
        val bottom = origin.bottom + (destination.bottom - origin.bottom) * progress + lift
        return ProjectedWindow(
            x = (left + right) / 2f,
            y = (top + bottom) / 2f,
            left = left, top = top, right = right, bottom = bottom,
            progress = progress,
        )
    }

    fun triggerFor(event: CockpitAnimationEvent): Trigger = when (event) {
        is CockpitAnimationEvent.CommandFlyIn,
        is CockpitAnimationEvent.ActionStarted,
        is CockpitAnimationEvent.DocumentQuestion -> Trigger.ENTER
        is CockpitAnimationEvent.ProcessTimeline ->
            if (event.steps.any { it.state == StepState.DONE }) Trigger.STEP else Trigger.IGNORE
        is CockpitAnimationEvent.TurnEnded -> Trigger.EXIT
        is CockpitAnimationEvent.AreaChanged -> when {
            event.toTarget == JarvisCockpitPanels.STOP_TARGET -> Trigger.EXIT
            event.pattern == TransitionPattern.B_FLY_IN -> Trigger.ENTER
            else -> Trigger.EXIT
        }
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
     * Not a second renderer. Gaze and window light belong to Bot 03.
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

    private fun poseFrom(
        frame: HollywoodFuiConstruct.Frame,
        travel: Float,
        side: Float,
        reduced: Boolean,
    ): Pose {
        val visual = travel.coerceIn(0f, 1f)
        val lift = HollywoodFuiConstruct.arcLiftDp(visual, enabled = !reduced)
        val remaining = 1f - visual
        return Pose(
            travel = travel,
            dx = 0f,
            dy = lift,
            scale = 1f - 0.08f * remaining,
            tiltY = HollywoodFuiConstruct.orientedTilt(frame.tiltDegrees, side, reduced),
            rimAlpha = frame.brackets * frame.closeFrame,
            glassAlpha = frame.glass * frame.closeGlass,
            contentAlpha = frame.data * frame.closeData,
            settled = frame.settled && (!reduced || visual >= 0.999f),
            hidden = visual <= 0.001f && frame.brackets * frame.closeFrame <= 0.001f,
            frame = frame,
        )
    }
}
