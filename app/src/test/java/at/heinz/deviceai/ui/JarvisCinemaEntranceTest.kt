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

    @Test
    fun workOrderFliesTheThreeTopWindowsAwayBeforeTheyRebuild() {
        val slots = listOf(
            JarvisShellLayout.Slot.WORK_DOCUMENT,
            JarvisShellLayout.Slot.WORK_PROCESS,
            JarvisShellLayout.Slot.WORK_DRAFT,
        )
        slots.forEachIndexed { rank, slot ->
            assertTrue(JarvisCinemaEntrance.fliesAwayOnWorkOrder(slot))
            assertTrue(JarvisCinemaEntrance.rebuildsAfterFlyAway(slot))
            val rest = JarvisCinemaEntrance.workOrderPose(slot, rank, 0L, side = 1f, budget = high)
            assertTrue(rest.contentAlpha > 0.98f)
            assertEquals(0f, rest.recede, 0.05f)
            assertFalse(rest.hidden)

            val midAt = 280L + JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, rank)
            val mid = JarvisCinemaEntrance.workOrderPose(slot, rank, midAt, side = 1f, budget = high)
            assertTrue(mid.recede > 0.12f)
            assertTrue(mid.dx > 8f)
            assertTrue(mid.scale < 0.97f)
            assertTrue(mid.contentAlpha < rest.contentAlpha)

            val seam = JarvisCinemaEntrance.DEPART_MS +
                JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, rank)
            val leaving = JarvisCinemaEntrance.workOrderPose(slot, rank, seam - 1L, side = 1f, budget = high)
            assertTrue(leaving.hidden || leaving.recede > 0.85f || leaving.contentAlpha < 0.12f)

            val rebuildStart = JarvisCinemaEntrance.workOrderPose(slot, rank, seam, side = 1f, budget = high)
            assertTrue(rebuildStart.contentAlpha < 0.15f)
            assertTrue(rebuildStart.recede < 0.05f)

            val rebuilt = JarvisCinemaEntrance.workOrderPose(
                slot, rank, seam + JarvisCinemaEntrance.SETTLE_MS, side = 1f, budget = high)
            assertTrue(rebuilt.settled)
            assertFalse(rebuilt.hidden)
            assertEquals(1f, rebuilt.contentAlpha, 0.02f)
            assertEquals(0f, rebuilt.recede, 0.02f)
        }
    }

    @Test
    fun workOrderFliesTheDockAwayThenRebuildsIt() {
        val slot = JarvisShellLayout.Slot.BOTTOM_NAV
        assertTrue(JarvisCinemaEntrance.fliesAwayOnWorkOrder(slot))
        assertTrue(JarvisCinemaEntrance.rebuildsAfterFlyAway(slot))
        assertEquals(JarvisCinemaEntrance.StageRole.STAY, JarvisCinemaEntrance.stageRole(slot))

        val rest = JarvisCinemaEntrance.workOrderPose(slot, 0, 0L, side = 1f, budget = high)
        assertTrue(rest.contentAlpha > 0.98f)
        assertFalse(rest.hidden)

        val mid = JarvisCinemaEntrance.workOrderPose(slot, 0, 280L, side = 1f, budget = high)
        assertTrue(mid.recede > 0.12f)
        assertTrue(mid.dx > 8f)

        val rebuilt = JarvisCinemaEntrance.workOrderPose(
            slot, 0, JarvisCinemaEntrance.DEPART_MS + JarvisCinemaEntrance.SETTLE_MS, side = 1f, budget = high)
        assertTrue(rebuilt.settled)
        assertFalse(rebuilt.hidden)
        assertEquals(1f, rebuilt.contentAlpha, 0.02f)
    }

    @Test
    fun idleTilesStayGoneAfterTheWorkOrderFlyAway() {
        assertTrue(JarvisCinemaEntrance.fliesAwayOnWorkOrder(JarvisShellLayout.Slot.TILES))
        assertFalse(JarvisCinemaEntrance.rebuildsAfterFlyAway(JarvisShellLayout.Slot.TILES))
        val gone = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.TILES, 0,
            JarvisCinemaEntrance.DEPART_MS + JarvisCinemaEntrance.SETTLE_MS,
            side = -1f, budget = high)
        assertTrue(gone.hidden || gone.recede >= 0.999f)

        val feature = JarvisCinemaEntrance.workOrderPose(
            null, 1, JarvisCinemaEntrance.DEPART_MS + 800L, side = 1f, budget = high)
        assertTrue(feature.hidden || feature.recede >= 0.999f)
    }

    @Test
    fun avatarAndStatusStayPutDuringTheWorkOrder() {
        assertFalse(JarvisCinemaEntrance.fliesAwayOnWorkOrder(JarvisShellLayout.Slot.AVATAR))
        assertFalse(JarvisCinemaEntrance.fliesAwayOnWorkOrder(JarvisShellLayout.Slot.STATUS))
        val avatar = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.AVATAR, 0, 240L, budget = high)
        assertFalse(avatar.hidden)
        assertEquals(0f, avatar.recede, 0.001f)
        assertTrue(avatar.contentAlpha > 0.98f)
        val status = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.STATUS, 0, 240L, budget = high)
        assertFalse(status.hidden)
        assertEquals(0f, status.recede, 0.001f)
    }

    @Test
    fun acknowledgementTimelineDoesNotRetriggerWorkWindows() {
        assertEquals(
            JarvisCinemaEntrance.Trigger.IGNORE,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.ProcessTimeline("t", 1L, listOf(
                ProcessStep(JarvisCinemaEntrance.ACK_STEP_KEY, "Auftrag angenommen", StepState.DONE),
                ProcessStep("work", "Bearbeitung", StepState.ACTIVE)))))
        assertEquals(
            JarvisCinemaEntrance.Trigger.STEP,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.ProcessTimeline("t", 2L, listOf(
                ProcessStep(JarvisCinemaEntrance.ACK_STEP_KEY, "Auftrag angenommen", StepState.DONE),
                ProcessStep("tool:1", "Werkzeug: read", StepState.DONE)))))
        assertEquals(
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.CommandFlyIn("t", 3L, "lies die pdf")))
        assertEquals(
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.ActionStarted(
                "t", 4L, CockpitAction.TOOL, "read_document")))
    }

    @Test
    fun reducedMotionRebuildsWorkWindowsWithoutSweeping() {
        val window = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.WORK_DOCUMENT, 0, 40L, side = 1f, budget = reduced)
        assertTrue(window.settled)
        assertFalse(window.hidden)
        assertEquals(0f, window.dx, 0.0001f)
        val tile = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.TILES, 0, 40L, side = -1f, budget = reduced)
        assertTrue(tile.hidden || tile.recede >= 0.999f)
        assertEquals(0f, tile.dx, 0.0001f)
    }

    @Test
    fun returnFromASettledWorkSceneFliesHologramsOutThenRebuildsTheCockpit() {
        val slot = JarvisShellLayout.Slot.WORK_DOCUMENT
        val start = JarvisCinemaEntrance.returnPose(slot, 0, 0L, budget = high)
        assertTrue(start.contentAlpha > 0.98f)
        assertFalse(start.hidden)

        val afterData = JarvisCinemaEntrance.returnPose(slot, 0, 160L, budget = high)
        assertTrue(afterData.contentAlpha < 0.05f)

        val gone = JarvisCinemaEntrance.returnPose(
            slot, 0, JarvisCinemaEntrance.EXIT_MS, budget = high)
        assertTrue(gone.hidden || gone.contentAlpha < 0.15f)

        val rest = JarvisCinemaEntrance.returnPose(
            slot, 0, JarvisCinemaEntrance.EXIT_MS + JarvisCinemaEntrance.SETTLE_MS, budget = high)
        assertTrue(rest.settled)
        assertFalse(rest.hidden)
        assertEquals(1f, rest.contentAlpha, 0.02f)
    }

    @Test
    fun returnKeepsIdleTilesHiddenUntilTheCockpitRebuilds() {
        val hidden = JarvisCinemaEntrance.returnPose(
            JarvisShellLayout.Slot.TILES, 0, 80L, budget = high)
        assertTrue(hidden.hidden || hidden.recede >= 0.999f)

        val rebuilt = JarvisCinemaEntrance.returnPose(
            JarvisShellLayout.Slot.TILES, 0,
            JarvisCinemaEntrance.EXIT_MS + JarvisCinemaEntrance.SETTLE_MS, budget = high)
        assertTrue(rebuilt.settled)
        assertFalse(rebuilt.hidden)
        assertEquals(1f, rebuilt.contentAlpha, 0.02f)
    }

    @Test
    fun returnFliesTheDockOutThenBringsItBack() {
        val slot = JarvisShellLayout.Slot.BOTTOM_NAV
        val start = JarvisCinemaEntrance.returnPose(slot, 0, 0L, budget = high)
        assertTrue(start.contentAlpha > 0.98f)
        val rest = JarvisCinemaEntrance.returnPose(
            slot, 0, JarvisCinemaEntrance.EXIT_MS + JarvisCinemaEntrance.SETTLE_MS, budget = high)
        assertTrue(rest.settled)
        assertFalse(rest.hidden)
    }

    @Test
    fun abortDuringFlyAwayDoesNotPopWindowsBackToRest() {
        val slot = JarvisShellLayout.Slot.WORK_DOCUMENT
        val mid = JarvisCinemaEntrance.workOrderPose(slot, 0, 200L, side = 1f, budget = high)
        val abort = JarvisCinemaEntrance.returnPose(
            slot, 0, 0L, interruptedAfterMs = 200L, side = 1f, budget = high)
        assertEquals(mid.recede, abort.recede, 0.08f)
        assertTrue(abort.contentAlpha < 0.2f)
        assertTrue(mid.contentAlpha < 0.2f)

        val later = JarvisCinemaEntrance.returnPose(
            slot, 0, JarvisCinemaEntrance.returnHoldMs(200L) + JarvisCinemaEntrance.SETTLE_MS,
            interruptedAfterMs = 200L, side = 1f, budget = high)
        assertTrue(later.settled)
        assertFalse(later.hidden)
        assertEquals(1f, later.contentAlpha, 0.02f)
    }

    @Test
    fun doubleOrderDuringFlyAwayContinuesInsteadOfRestartingFromRest() {
        assertEquals(
            JarvisCinemaEntrance.WorkOrderReplay.IGNORE,
            JarvisCinemaEntrance.workOrderReplay(sameTurn = true, elapsedMs = 180L))
        assertEquals(
            JarvisCinemaEntrance.WorkOrderReplay.CONTINUE_FLIGHT,
            JarvisCinemaEntrance.workOrderReplay(sameTurn = false, elapsedMs = 180L))
        assertEquals(
            JarvisCinemaEntrance.WorkOrderReplay.RESTART_FROM_WORKING,
            JarvisCinemaEntrance.workOrderReplay(
                sameTurn = false, elapsedMs = JarvisCinemaEntrance.workOrderDurationMs(3)))
    }

    @Test
    fun restartFromAWorkingSceneKeepsIdleTilesHidden() {
        val tiles = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.TILES, 0, 0L, side = -1f, budget = high,
            fromWorkingScene = true)
        assertTrue(tiles.hidden || tiles.recede >= 0.999f)

        val window = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.WORK_DOCUMENT, 0, 0L, side = 1f, budget = high,
            fromWorkingScene = true)
        assertTrue(window.contentAlpha > 0.98f)
        assertFalse(window.hidden)

        val gone = JarvisCinemaEntrance.workOrderPose(
            JarvisShellLayout.Slot.WORK_DOCUMENT, 0, JarvisCinemaEntrance.DEPART_MS,
            side = 1f, budget = high, fromWorkingScene = true)
        assertTrue(gone.hidden || gone.recede >= 0.85f || gone.contentAlpha < 0.12f)
    }

    @Test
    fun avatarStaysPutOnReturnAndReducedMotionSettlesToRest() {
        val avatar = JarvisCinemaEntrance.returnPose(
            JarvisShellLayout.Slot.AVATAR, 0, 120L, interruptedAfterMs = 200L, budget = high)
        assertFalse(avatar.hidden)
        assertEquals(0f, avatar.recede, 0.001f)
        assertTrue(avatar.contentAlpha > 0.98f)

        val rest = JarvisCinemaEntrance.returnPose(
            JarvisShellLayout.Slot.WORK_DOCUMENT, 0, 40L,
            interruptedAfterMs = 200L, budget = reduced)
        assertTrue(rest.settled)
        assertFalse(rest.hidden)
        assertEquals(0f, rest.dx, 0.0001f)
    }
    @Test fun secondOrderWaitsForTheLastStaggeredRebuild() {
        for (count in listOf(1, 3, 8, 20)) {
            val end = JarvisCinemaEntrance.workOrderDurationMs(count)
            for (time in listOf(420L, 500L, end - 1L)) {
                assertEquals(JarvisCinemaEntrance.WorkOrderReplay.CONTINUE_FLIGHT,
                    JarvisCinemaEntrance.workOrderReplay(false, time, count))
            }
            assertEquals(JarvisCinemaEntrance.WorkOrderReplay.RESTART_FROM_WORKING,
                JarvisCinemaEntrance.workOrderReplay(false, end, count))
            assertEquals(JarvisCinemaEntrance.WorkOrderReplay.IGNORE,
                JarvisCinemaEntrance.workOrderReplay(true, end, count))
        }
    }

    @Test fun resultReturnStartsAtTheDisplayedResultPose() {
        for (time in 0L..1000L step 23) {
            val before = JarvisCinemaEntrance.resultAdvance(time, high)
            val after = JarvisCinemaEntrance.returnPose(
                JarvisShellLayout.Slot.WORK_DRAFT, 2, 0L,
                interruptedAfterMs = 3000L, budget = high, returnResultElapsedMs = time)
            assertEquals("Result at $time", before, after)
            val restored = JarvisCinemaEntrance.returnPose(
                JarvisShellLayout.Slot.WORK_DRAFT, 2, 3000L,
                interruptedAfterMs = 3000L, budget = high, returnResultElapsedMs = time)
            assertTrue(restored.settled && !restored.hidden)
            assertEquals(1f, restored.scale, .0001f)
        }
    }
}
