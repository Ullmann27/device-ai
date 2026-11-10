package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cockpit.TransitionPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JarvisCinemaEntranceTest {
    private val high = JarvisRenderQuality.resolve(true, false, 0, false, false)
    private val reduced = JarvisRenderQuality.resolve(true, false, 0, true, false)
    private val paused = JarvisRenderQuality.resolve(false, false, 0, false, false)
    private val origin = JarvisShellLayout.Box(40f, 620f, 88f, 656f)
    private val destination = JarvisShellLayout.Box(24f, 390f, 300f, 610f)

    @Test
    fun flightFollowsHollywoodConstructNotASecondSpring() {
        assertEquals(0f, JarvisCinemaEntrance.springTravel(0L), 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.flight(HollywoodFuiConstruct.ANCHOR_END_MS), 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.flight(HollywoodFuiConstruct.GLASS_END_MS), 0.0001f)
        assertEquals(1f, JarvisCinemaEntrance.springTravel(HollywoodFuiConstruct.DATA_END_MS), 0.0001f)
        val mid = JarvisCinemaEntrance.springTravel(450L)
        assertTrue(mid > 0.25f && mid < 0.85f)
        assertTrue(abs(mid - 0.5f) < 0.08f)
        assertTrue(JarvisCinemaEntrance.springTravel(240L) > JarvisCinemaEntrance.springTravel(80L))
    }

    @Test
    fun screenplayBuildsAnchorBracketsGridGlassThenNativeContent() {
        val t0 = JarvisCinemaEntrance.pose(JarvisCinemaEntrance.Kind.WORK_WINDOW, 0, 0L, budget = high)
        assertTrue(t0.hidden || t0.rimAlpha < 0.02f)
        assertTrue(t0.contentAlpha < 0.02f && t0.glassAlpha < 0.02f)

        val brackets = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.WORK_WINDOW, 0, HollywoodFuiConstruct.BRACKETS_END_MS, budget = high)
        assertTrue(brackets.rimAlpha > 0.95f)
        assertTrue(brackets.glassAlpha < 0.05f)
        assertTrue(brackets.contentAlpha < 0.02f)
        assertTrue(abs(brackets.tiltY) <= HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES)

        val beforeGlass = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.WORK_WINDOW, 0, HollywoodFuiConstruct.GRID_END_MS, budget = high)
        assertTrue(beforeGlass.contentAlpha < 0.02f)
        assertTrue(beforeGlass.glassAlpha > 0.15f)

        val glassStanding = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.WORK_WINDOW, 0, HollywoodFuiConstruct.GLASS_END_MS, budget = high)
        assertTrue(glassStanding.glassAlpha > 0.95f)
        assertTrue(glassStanding.contentAlpha < 0.85f)

        val dataFree = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.WORK_WINDOW, 0, HollywoodFuiConstruct.DATA_END_MS, budget = high)
        assertEquals(1f, dataFree.rimAlpha, 0.0001f)
        assertEquals(1f, dataFree.glassAlpha, 0.0001f)
        assertEquals(1f, dataFree.contentAlpha, 0.0001f)
        assertEquals(0f, dataFree.tiltY, 0.0001f)
        assertTrue(dataFree.settled)
        assertFalse(dataFree.hidden)
    }

    @Test
    fun staggerDelaysLaterWindowsWithoutShiftingRankZeroOffTheScreenplay() {
        assertEquals(0L, JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, 0))
        val first = JarvisCinemaEntrance.pose(JarvisCinemaEntrance.Kind.PANEL, 0, 200L, budget = high)
        val later = JarvisCinemaEntrance.pose(JarvisCinemaEntrance.Kind.PANEL, 2, 200L, budget = high)
        assertTrue(first.travel > later.travel)
        assertTrue(JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.DOCK, 1) >
            JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.DOCK, 0))
        assertTrue(JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, 2) >
            JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, 0))
    }

    @Test
    fun negativeElapsedIsHiddenUntilARealEventClockStarts() {
        val hidden = JarvisCinemaEntrance.pose(JarvisCinemaEntrance.Kind.PANEL, 0, -1L, budget = high)
        assertTrue(hidden.hidden)
        assertTrue(hidden.contentAlpha < 0.01f)
    }

    @Test
    fun reducedMotionAndFrozenBudgetSettleWithoutSweeping() {
        val settled = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 3, 40L, budget = reduced, animatorScale = 1f)
        assertTrue(settled.settled)
        assertEquals(1f, settled.contentAlpha, 0.0001f)
        assertEquals(0f, settled.tiltY, 0.0001f)

        val frozen = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.WORK_WINDOW, 1, 40L, budget = paused, animatorScale = 1f)
        assertTrue(frozen.settled)

        val scaleOff = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.DOCK, 0, 40L, budget = high, animatorScale = 0f)
        assertTrue(scaleOff.settled)
    }

    @Test
    fun flyOutDropsNativeContentFirstThenGlassThenTheFrame() {
        val start = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, 0L, exiting = true, budget = high)
        assertTrue(start.contentAlpha > 0.98f)
        assertFalse(start.hidden)

        val afterData = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, 160L, exiting = true, budget = high)
        assertTrue(afterData.contentAlpha < 0.05f)
        assertTrue(afterData.glassAlpha > afterData.contentAlpha)

        val gone = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, JarvisCinemaEntrance.EXIT_MS, exiting = true, budget = high)
        assertTrue(gone.hidden || gone.contentAlpha < 0.02f)
        assertTrue(gone.travel < 0.02f)
        assertEquals(HollywoodFuiConstruct.CLOSE_END_MS, JarvisCinemaEntrance.EXIT_MS)
    }

    @Test
    fun onlyRealCockpitEventsTriggerEntranceOrExit() {
        assertEquals(
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.CommandFlyIn("t", 10L, "baue")))
        assertEquals(
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.ActionStarted("t", 11L, CockpitAction.DEVELOP, "step")))
        assertEquals(
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.DocumentQuestion("t", 12L, "a.pdf", "application/pdf", "was steht drin?")))
        assertEquals(
            JarvisCinemaEntrance.Trigger.STEP,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.ProcessTimeline("t", 13L, listOf(
                ProcessStep("s1", "lesen", StepState.DONE)))))
        assertEquals(
            JarvisCinemaEntrance.Trigger.IGNORE,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.ProcessTimeline("t", 14L, listOf(
                ProcessStep("s1", "lesen", StepState.ACTIVE)))))
        assertEquals(
            JarvisCinemaEntrance.Trigger.EXIT,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.TurnEnded("t", 15L, delivered = true)))
        assertEquals(
            JarvisCinemaEntrance.Trigger.EXIT,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.TurnEnded("t", 16L, delivered = false)))
        assertEquals(
            JarvisCinemaEntrance.Trigger.EXIT,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.AreaChanged(
                "nav", 17L, "think", JarvisCockpitPanels.STOP_TARGET, TransitionPattern.A_DEPTH)))
        assertEquals(
            JarvisCinemaEntrance.Trigger.EXIT,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.AreaChanged(
                "nav", 18L, "think", "memory", TransitionPattern.A_DEPTH)))
        assertEquals(
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.AreaChanged(
                "nav", 19L, "memory", "home", TransitionPattern.B_FLY_IN)))
        assertEquals(
            JarvisCinemaEntrance.Trigger.IGNORE,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.MemoryStored("memory", 20L, "e1", "CONFIRMED")))
        assertEquals(
            JarvisCinemaEntrance.Trigger.IGNORE,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.BootStarted("boot", 1L, "hallo")))
    }

    @Test
    fun adaptiveAnimationStillFreezesWhenTheSharedBudgetSaysNo() {
        val cinemaFrozen = JarvisRenderQuality.adaptiveAnimation(
            foreground = true, powerSave = false, thermalStatus = 0,
            reducedMotion = true, inferenceActive = false,
            cinemaOptIn = true, experimentEnabled = true)
        assertFalse(cinemaFrozen.animate)
        val pose = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, 90L, budget = cinemaFrozen)
        assertTrue(pose.settled)
    }

    @Test
    fun zSlicingMatchesTheScreenplayPlanes() {
        assertEquals(-24f, HollywoodFuiConstruct.REAR_GLASS_Z_DP, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.CONTENT_Z_DP, 0.0001f)
        assertEquals(20f, HollywoodFuiConstruct.FRONT_BRACKET_Z_DP, 0.0001f)
        assertEquals(14f, HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES, 0.0001f)
        val mid = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.WORK_WINDOW, 0, 320L, side = 1f, budget = high)
        assertTrue(abs(mid.tiltY) <= HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES)
        assertTrue(mid.tiltY > 0f)
    }

    @Test
    fun projectedWindowFollowsTheArcFromARealOriginAndStaysAClosedFolder() {
        val start = JarvisCinemaEntrance.projectedWindow(origin, destination, 0L)
        assertEquals(origin.centerX, start.x, 0.01f)
        assertEquals(origin.centerY, start.y, 0.01f)
        assertEquals(0f, start.progress, 0.0001f)
        assertTrue(start.width > 0f && start.height > 0f && start.left < start.right && start.top < start.bottom)

        val mid = JarvisCinemaEntrance.projectedWindow(origin, destination, 450L)
        val linearY = origin.centerY + (destination.centerY - origin.centerY) * mid.progress
        assertTrue(mid.x > origin.centerX && mid.x < destination.centerX)
        assertTrue(mid.y < linearY - 8f)
        assertTrue(mid.width > start.width && mid.height > start.height)
        assertTrue(mid.left < mid.right && mid.top < mid.bottom)

        val end = JarvisCinemaEntrance.projectedWindow(origin, destination, HollywoodFuiConstruct.DATA_END_MS)
        assertEquals(destination.centerX, end.x, 0.01f)
        assertEquals(destination.centerY, end.y, 0.01f)
        assertEquals(1f, end.progress, 0.0001f)

        val reduced = JarvisCinemaEntrance.projectedWindow(
            origin, destination, 450L, reducedMotion = true)
        assertEquals(destination.centerX, reduced.x, 0.01f)
        assertEquals(1f, reduced.progress, 0.0001f)
    }

    @Test
    fun idlePanelsDepartSidewaysIntoDepthOnAWorkOrder() {
        val rest = JarvisCinemaEntrance.depart(0, 0L, side = 1f, budget = high)
        assertEquals(0f, rest.recede, 0.02f)
        assertTrue(rest.contentAlpha > 0.98f)
        assertEquals(0f, rest.dx, 0.5f)

        val mid = JarvisCinemaEntrance.depart(0, 280L, side = 1f, budget = high)
        assertTrue(mid.recede > 0.15f && mid.recede < 0.95f)
        assertTrue(mid.dx > 12f)
        assertTrue(mid.scale < 0.95f)
        assertTrue(mid.contentAlpha < 0.05f)
        assertTrue(mid.glassAlpha > mid.contentAlpha)

        val left = JarvisCinemaEntrance.depart(0, 280L, side = -1f, budget = high)
        assertTrue(left.dx < -12f)

        val gone = JarvisCinemaEntrance.depart(0, JarvisCinemaEntrance.EXIT_MS, side = 1f, budget = high)
        assertTrue(gone.hidden || gone.recede >= 0.999f)
        assertTrue(gone.travel < 0.02f)
        assertTrue(gone.scale < 0.72f)
    }

    @Test
    fun reducedMotionHidesDepartingPanelsWithoutASweep() {
        val reducedGone = JarvisCinemaEntrance.depart(2, 40L, side = 1f, budget = reduced)
        assertTrue(reducedGone.hidden || reducedGone.recede >= 0.999f)
        assertEquals(0f, reducedGone.dx, 0.0001f)
        assertEquals(0f, reducedGone.tiltY, 0.0001f)

        val frozen = JarvisCinemaEntrance.depart(0, 40L, side = -1f, budget = paused)
        assertTrue(frozen.hidden || frozen.recede >= 0.999f)
    }

    @Test
    fun stageRolesKeepChromeAndSendIdleCardsAway() {
        assertEquals(JarvisCinemaEntrance.StageRole.ENTER,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.WORK_DOCUMENT))
        assertEquals(JarvisCinemaEntrance.StageRole.ENTER,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.WORK_PROCESS))
        assertEquals(JarvisCinemaEntrance.StageRole.ENTER,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.WORK_DRAFT))
        assertEquals(JarvisCinemaEntrance.StageRole.DEPART,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.TILES))
        assertEquals(JarvisCinemaEntrance.StageRole.DEPART,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.COCKPIT_PRIMARY))
        assertEquals(JarvisCinemaEntrance.StageRole.DEPART, JarvisCinemaEntrance.stageRole(null))
        assertEquals(JarvisCinemaEntrance.StageRole.STAY,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.BOTTOM_NAV))
        assertEquals(JarvisCinemaEntrance.StageRole.STAY,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.STATUS))
        assertEquals(JarvisCinemaEntrance.StageRole.STAY,
            JarvisCinemaEntrance.stageRole(JarvisShellLayout.Slot.AVATAR))
    }

    @Test
    fun resultAdvanceStepsTheDraftTowardTheCameraOnARealStep() {
        val start = JarvisCinemaEntrance.resultAdvance(0L, budget = high)
        val end = JarvisCinemaEntrance.resultAdvance(HollywoodFuiConstruct.GLASS_END_MS, budget = high)
        assertTrue(end.scale > start.scale)
        assertTrue(end.dy < start.dy)
        val reduced = JarvisCinemaEntrance.resultAdvance(40L, budget = this.reduced)
        assertTrue(reduced.scale > 1f)
        assertTrue(reduced.settled)
    }
}
