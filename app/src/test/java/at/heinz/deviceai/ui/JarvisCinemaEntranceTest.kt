package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JarvisCinemaEntranceTest {
    private val high = JarvisRenderQuality.resolve(true, false, 0, false, false)
    private val reduced = JarvisRenderQuality.resolve(true, false, 0, true, false)
    private val paused = JarvisRenderQuality.resolve(false, false, 0, false, false)

    @Test
    fun springTravelStartsAtRestAndSettlesOnTheExistingWidgetSpring() {
        assertEquals(0f, JarvisCinemaEntrance.springTravel(0L), 0.0001f)
        assertEquals(1f, JarvisCinemaEntrance.springTravel(JarvisCinemaEntrance.SPRING_MS), 0.0001f)
        val mid = JarvisCinemaEntrance.springTravel(JarvisCinemaEntrance.SPRING_MS / 2)
        assertTrue(mid > 0.25f && mid < 1.08f)
        assertTrue(abs(mid - 0.5f) > 0.02f)
        val early = JarvisCinemaEntrance.springTravel(80L)
        val later = JarvisCinemaEntrance.springTravel(240L)
        assertTrue(later > early)
    }

    @Test
    fun lightBuildsRimThenGlassThenContent() {
        val start = JarvisCinemaEntrance.poseAt(0f, side = -1f)
        assertTrue(start.rimAlpha < 0.02f && start.glassAlpha < 0.02f && start.contentAlpha < 0.02f)
        assertTrue(start.scale < 1f)
        assertTrue(start.tiltY < 0f)

        val rimPhase = JarvisCinemaEntrance.poseAt(0.20f, side = 1f)
        assertTrue(rimPhase.rimAlpha > 0.35f)
        assertTrue(rimPhase.glassAlpha < rimPhase.rimAlpha)
        assertTrue(rimPhase.contentAlpha < 0.05f)

        val glassPhase = JarvisCinemaEntrance.poseAt(0.48f, side = 1f)
        assertTrue(glassPhase.glassAlpha > 0.35f)
        assertTrue(glassPhase.contentAlpha < glassPhase.glassAlpha)
        assertTrue(glassPhase.rimAlpha >= glassPhase.glassAlpha - 0.001f)

        val settled = JarvisCinemaEntrance.poseAt(1f, side = 1f)
        assertEquals(1f, settled.rimAlpha, 0.0001f)
        assertEquals(1f, settled.glassAlpha, 0.0001f)
        assertEquals(1f, settled.contentAlpha, 0.0001f)
        assertEquals(1f, settled.scale, 0.0001f)
        assertEquals(0f, settled.tiltY, 0.0001f)
        assertTrue(settled.settled)
        assertFalse(settled.hidden)
    }

    @Test
    fun staggerDelaysDockAndWorkWindows() {
        val panel = JarvisCinemaEntrance.pose(JarvisCinemaEntrance.Kind.PANEL, 0, 80L, budget = high)
        val laterPanel = JarvisCinemaEntrance.pose(JarvisCinemaEntrance.Kind.PANEL, 2, 80L, budget = high)
        assertTrue(panel.travel > laterPanel.travel)

        val dock0 = JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.DOCK, 0)
        val dock1 = JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.DOCK, 1)
        val work0 = JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, 0)
        val work2 = JarvisCinemaEntrance.staggerMs(JarvisCinemaEntrance.Kind.WORK_WINDOW, 2)
        assertTrue(dock1 > dock0)
        assertTrue(work0 > dock0)
        assertTrue(work2 > work0)
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
    fun flyOutReversesLightAndHidesAfterTheExitWindow() {
        val start = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, 0L, exiting = true, budget = high)
        assertTrue(start.contentAlpha > 0.98f)
        assertFalse(start.hidden)

        val mid = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, JarvisCinemaEntrance.EXIT_MS / 2, exiting = true, budget = high)
        assertTrue(mid.contentAlpha < start.contentAlpha)
        assertTrue(mid.contentAlpha <= mid.glassAlpha + 0.001f)

        val gone = JarvisCinemaEntrance.pose(
            JarvisCinemaEntrance.Kind.PANEL, 0, JarvisCinemaEntrance.EXIT_MS, exiting = true, budget = high)
        assertTrue(gone.hidden || gone.contentAlpha < 0.02f)
        assertTrue(gone.travel < 0.02f)
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
            JarvisCinemaEntrance.Trigger.IGNORE,
            JarvisCinemaEntrance.triggerFor(CockpitAnimationEvent.MemoryStored("memory", 16L, "e1", "CONFIRMED")))
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
}
