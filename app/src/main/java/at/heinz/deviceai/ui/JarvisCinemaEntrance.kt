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

    enum class StageRole { ENTER, DEPART, STAY }

    const val STAGGER_MS = 70L
    const val SETTLE_MS = HollywoodFuiConstruct.DATA_END_MS
    const val EXIT_MS = HollywoodFuiConstruct.CLOSE_END_MS
    /** Close-screenplay length of the work-order fly-away before holograms rebuild. */
    const val DEPART_MS = EXIT_MS
    /** Key written by [at.heinz.deviceai.cockpit.CockpitAnimationTriggers.commandAcknowledged]. */
    const val ACK_STEP_KEY = "ack"

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
        /** 0 at rest, 1 fully receded into depth (idle clearance). */
        val recede: Float = 0f,
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
            if (event.steps.any { it.state == StepState.DONE && it.key != ACK_STEP_KEY }) Trigger.STEP
            else Trigger.IGNORE
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

    fun stageRole(slot: JarvisShellLayout.Slot?): StageRole = when (slot) {
        JarvisShellLayout.Slot.WORK_DOCUMENT,
        JarvisShellLayout.Slot.WORK_PROCESS,
        JarvisShellLayout.Slot.WORK_DRAFT -> StageRole.ENTER
        null,
        JarvisShellLayout.Slot.TILES,
        JarvisShellLayout.Slot.COCKPIT_PRIMARY,
        JarvisShellLayout.Slot.COCKPIT_SECONDARY,
        JarvisShellLayout.Slot.ROW_AGENTS,
        JarvisShellLayout.Slot.ROW_TASKS,
        JarvisShellLayout.Slot.ROW_MEMORY,
        JarvisShellLayout.Slot.ROW_MODEL,
        JarvisShellLayout.Slot.USER_CARD,
        JarvisShellLayout.Slot.NAV_RAIL -> StageRole.DEPART
        else -> StageRole.STAY
    }

    /**
     * Heinz: on a real work order everything cinematic must fly away — panels, the dock
     * and the three larger work boxes — then rebuild as living holograms where needed.
     * Avatar, status, input and dialog stay so speech and STOP remain reachable.
     */
    fun fliesAwayOnWorkOrder(slot: JarvisShellLayout.Slot?): Boolean = when (slot) {
        JarvisShellLayout.Slot.WORK_DOCUMENT,
        JarvisShellLayout.Slot.WORK_PROCESS,
        JarvisShellLayout.Slot.WORK_DRAFT,
        JarvisShellLayout.Slot.BOTTOM_NAV -> true
        else -> stageRole(slot) == StageRole.DEPART
    }

    /** Work windows and the dock return after the fly-away; idle tiles stay receded. */
    fun rebuildsAfterFlyAway(slot: JarvisShellLayout.Slot?): Boolean = when (slot) {
        JarvisShellLayout.Slot.WORK_DOCUMENT,
        JarvisShellLayout.Slot.WORK_PROCESS,
        JarvisShellLayout.Slot.WORK_DRAFT,
        JarvisShellLayout.Slot.BOTTOM_NAV -> true
        else -> false
    }

    fun workOrderDurationMs(count: Int, animatorScale: Float = 1f): Long {
        val last = staggerMs(Kind.WORK_WINDOW, (count - 1).coerceAtLeast(0))
        val scale = if (animatorScale <= 0f) 0f else animatorScale
        return if (scale == 0f) 0L else ((last + DEPART_MS + SETTLE_MS) * scale).toLong()
    }

    /**
     * Two-phase work-order choreography on the shared event clock: each participant
     * finishes its own [depart] (staggered) and only then, if it rebuilds, runs the
     * existing entrance screenplay. Not a second motion engine.
     */
    enum class WorkOrderReplay { IGNORE, CONTINUE_FLIGHT, RESTART_FROM_WORKING }

    /** Sentinel: return started from a fully settled work scene (not mid-flight). */
    const val WORKING_SETTLED_MS = Long.MAX_VALUE / 4

    fun workOrderReplay(sameTurn: Boolean, elapsedMs: Long): WorkOrderReplay = when {
        sameTurn -> WorkOrderReplay.IGNORE
        elapsedMs < DEPART_MS -> WorkOrderReplay.CONTINUE_FLIGHT
        else -> WorkOrderReplay.RESTART_FROM_WORKING
    }

    fun workOrderPose(
        slot: JarvisShellLayout.Slot?,
        rank: Int,
        elapsedMs: Long,
        side: Float = 0f,
        budget: JarvisRenderQuality.Budget? = null,
        animatorScale: Float = 1f,
        fromWorkingScene: Boolean = false,
    ): Pose {
        if (!fliesAwayOnWorkOrder(slot)) return SETTLED
        val kind = kindOf(slot)
        val rebuilds = rebuildsAfterFlyAway(slot)
        val reduced = animatorScale <= 0f || budget?.animate == false
        if (reduced) {
            return if (rebuilds) {
                pose(kind, rank, SETTLE_MS, exiting = false, side = 0f,
                    budget = budget, animatorScale = animatorScale)
            } else {
                depart(rank, EXIT_MS, side = 0f, budget = budget, animatorScale = animatorScale)
            }
        }
        if (fromWorkingScene && !rebuilds) {
            return depart(rank, EXIT_MS, side, budget, animatorScale)
        }
        val departLocal = elapsedMs - staggerMs(Kind.PANEL, rank)
        if (!rebuilds || departLocal < EXIT_MS) {
            return depart(rank, elapsedMs, side, budget, animatorScale)
        }
        return pose(
            kind, rank, elapsedMs - DEPART_MS, exiting = false,
            side = side, budget = budget, animatorScale = animatorScale,
        )
    }

    fun returnHoldMs(interruptedAfterMs: Long): Long {
        if (interruptedAfterMs >= WORKING_SETTLED_MS || interruptedAfterMs >= DEPART_MS) return EXIT_MS
        return (DEPART_MS - interruptedAfterMs).coerceAtLeast(0L)
    }

    fun returnDurationMs(count: Int, interruptedAfterMs: Long, animatorScale: Float = 1f): Long {
        val last = staggerMs(Kind.WORK_WINDOW, (count - 1).coerceAtLeast(0))
        val scale = if (animatorScale <= 0f) 0f else animatorScale
        return if (scale == 0f) 0L else ((returnHoldMs(interruptedAfterMs) + SETTLE_MS + last) * scale).toLong()
    }

    /**
     * Return flight after a finished, failed, stopped or superseded work order.
     * Visible holograms close first; items still departing keep receding (no pop
     * back to rest). Then the idle cockpit rebuilds via the entrance screenplay.
     */
    fun returnPose(
        slot: JarvisShellLayout.Slot?,
        rank: Int,
        elapsedMs: Long,
        interruptedAfterMs: Long = WORKING_SETTLED_MS,
        side: Float = 0f,
        budget: JarvisRenderQuality.Budget? = null,
        animatorScale: Float = 1f,
    ): Pose {
        if (!fliesAwayOnWorkOrder(slot)) return SETTLED
        val kind = kindOf(slot)
        val rebuilds = rebuildsAfterFlyAway(slot)
        val reduced = animatorScale <= 0f || budget?.animate == false
        if (reduced) {
            return pose(kind, rank, SETTLE_MS, exiting = false, side = 0f,
                budget = budget, animatorScale = animatorScale)
        }
        val hold = returnHoldMs(interruptedAfterMs)
        val rebuildLocal = when {
            !rebuilds -> Long.MIN_VALUE
            interruptedAfterMs >= WORKING_SETTLED_MS -> SETTLE_MS
            else -> interruptedAfterMs - DEPART_MS - staggerMs(kind, rank)
        }
        val hologramSolid = if (rebuildLocal > 0L) {
            val snap = pose(kind, rank, rebuildLocal, exiting = false, side = side,
                budget = budget, animatorScale = animatorScale)
            snap.settled || snap.contentAlpha > 0.5f
        } else false
        if (hologramSolid) {
            return if (elapsedMs < EXIT_MS) {
                pose(kind, rank, elapsedMs, exiting = true, side = side,
                    budget = budget, animatorScale = animatorScale)
            } else {
                pose(kind, rank, elapsedMs - EXIT_MS, exiting = false, side = side,
                    budget = budget, animatorScale = animatorScale)
            }
        }
        val continued = if (interruptedAfterMs >= WORKING_SETTLED_MS) EXIT_MS + elapsedMs
            else interruptedAfterMs + elapsedMs
        if (elapsedMs < hold) {
            return depart(rank, continued, side, budget, animatorScale)
        }
        return pose(
            kind, rank, elapsedMs - hold, exiting = false,
            side = side, budget = budget, animatorScale = animatorScale,
        )
    }

    /**
     * Idle tiles and unused panels recede on a real work order: sideways, into Z
     * (scale + [HollywoodFuiConstruct.layerShiftDp] on the rear glass plane) and fade
     * with the existing close choreography (data, then glass, then frame). Not a second engine.
     */
    fun depart(
        rank: Int,
        elapsedMs: Long,
        side: Float = 0f,
        budget: JarvisRenderQuality.Budget? = null,
        animatorScale: Float = 1f,
    ): Pose {
        val reduced = animatorScale <= 0f || budget?.animate == false
        if (reduced) {
            val frame = constructFrame(EXIT_MS, exiting = true, reducedMotion = true)
            return departedPose(frame, side = 0f, leave = 1f, reduced = true)
        }
        val local = elapsedMs - staggerMs(Kind.PANEL, rank)
        if (local < 0L) {
            val frame = constructFrame(SETTLE_MS, reducedMotion = true)
            return poseFrom(frame, travel = 1f, side = 0f, reduced = true)
        }
        val frame = constructFrame(local, exiting = true, reducedMotion = false)
        val leave = (1f - frame.closeFrame).coerceIn(0f, 1f)
        return departedPose(frame, side, leave, reduced = false)
    }

    /** After a real process step, the summary/result pane steps toward the camera. */
    fun resultAdvance(
        elapsedMs: Long,
        budget: JarvisRenderQuality.Budget? = null,
        animatorScale: Float = 1f,
    ): Pose {
        val base = pose(Kind.WORK_WINDOW, rankOf(JarvisShellLayout.Slot.WORK_DRAFT, 2),
            SETTLE_MS, budget = budget, animatorScale = animatorScale)
        val reduced = animatorScale <= 0f || budget?.animate == false
        if (reduced) return base.copy(scale = 1.06f, dy = -8f, settled = true)
        val forward = HollywoodFuiConstruct.flight(elapsedMs)
        return base.copy(
            scale = 1f + 0.07f * forward,
            dy = -10f * forward,
            tiltY = 0f,
            settled = forward >= 0.999f,
            recede = 0f,
        )
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
    /** Glowing ring platform under the three work windows; strength follows construct flight. */
    fun drawStagePlatform(p: ContinuumPainter, shell: JarvisShellLayout.Shell, progress: Float, reduced: Boolean) {
        if (progress <= 0.02f) return
        val boxes = listOf(
            JarvisShellLayout.Slot.WORK_DOCUMENT,
            JarvisShellLayout.Slot.WORK_PROCESS,
            JarvisShellLayout.Slot.WORK_DRAFT,
        ).mapNotNull { shell[it] }
        if (boxes.isEmpty()) return
        val left = boxes.minOf { it.left }
        val right = boxes.maxOf { it.right }
        val bottom = boxes.maxOf { it.bottom }
        val cx = (left + right) / 2f
        val cy = bottom + 8f
        val built = progress.coerceIn(0f, 1f)
        val rx = ((right - left) * 0.52f).coerceAtLeast(72f) * if (reduced) 1f else (0.70f + 0.30f * built)
        val ry = rx * 0.22f
        p.save()
        p.saveAlpha((0.28f + 0.62f * built).coerceIn(0f, 1f))
        JarvisNeon.ring(p, cx, cy, rx, ry, width = 2.2f)
        JarvisNeon.ring(p, cx, cy, rx * 0.74f, ry * 0.70f, width = 1.1f)
        JarvisNeon.halo(p, cx, cy, rx * 0.55f, Colors.cyan, 0.20f * built)
        p.restore()
        p.restore()
    }

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

    private fun departedPose(
        frame: HollywoodFuiConstruct.Frame,
        side: Float,
        leave: Float,
        reduced: Boolean,
    ): Pose {
        val recede = leave.coerceIn(0f, 1f)
        val spread = 88f * recede
        val depth = HollywoodFuiConstruct.layerShiftDp(
            HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES * recede,
            HollywoodFuiConstruct.REAR_GLASS_Z_DP,
            enabled = !reduced,
        )
        return Pose(
            travel = 1f - recede,
            dx = side.coerceIn(-1f, 1f) * spread,
            dy = 30f * recede - depth,
            scale = 1f - 0.32f * recede,
            tiltY = HollywoodFuiConstruct.orientedTilt(
                HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES * recede, side, reduced),
            rimAlpha = frame.brackets * frame.closeFrame,
            glassAlpha = frame.glass * frame.closeGlass,
            contentAlpha = frame.data * frame.closeData,
            settled = recede >= 0.999f,
            hidden = recede >= 0.999f,
            frame = frame,
            recede = recede,
        )
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
