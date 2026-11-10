package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.WindowProjectedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowProjectedMotionSamplerTest {
    private val rest = JarvisShellLayout.Box(120f, 400f, 360f, 620f)
    private val origin = JarvisShellLayout.Box(40f, 620f, 88f, 656f)
    private val headX = 440f
    private val headY = 200f
    private val viewportW = 884f
    private val viewportH = 760f

    private fun sampler() = WindowProjectedMotionSampler()

    private fun consider(
        sampler: WindowProjectedMotionSampler,
        x: Float,
        y: Float,
        at: Long,
        phase: WindowProjectedMotion.Phase = WindowProjectedMotion.Phase.FLYING,
        reduced: Boolean = false,
        moving: Boolean = true,
        present: Boolean = true,
        turnId: String = "turn-1",
        windowId: String = "work-document",
        accent: Int? = 0xFF22C4D8.toInt(),
    ) = sampler.consider(
        turnId = turnId,
        atMillis = at,
        windowId = windowId,
        projectedX = x,
        projectedY = y,
        headX = headX,
        headY = headY,
        viewportWidth = viewportW,
        viewportHeight = viewportH,
        phase = phase,
        accentArgb = accent,
        reducedMotion = reduced,
        moving = moving,
        windowPresent = present,
    )

    @Test
    fun flyingSampleFollowsTheProjectedCentreNotTheRestRectangle() {
        val sampler = sampler()
        val restGaze = JarvisWindowGaze.fromProjection(
            rest.centerX, rest.centerY, headX, headY, viewportW, viewportH)
        val projected = JarvisCinemaEntrance.projectedWindow(origin, rest, 450L)
        val event = consider(sampler, projected.x, projected.y, at = 450L)
        assertNotNull(event)
        assertEquals(WindowProjectedMotion.KIND, event!!.kind)
        assertEquals("window-projected-motion", event.kind)
        assertEquals(WindowProjectedMotion.Phase.FLYING, event.phase)
        assertEquals("turn-1", event.turnId)
        assertEquals("work-document", event.windowId)
        assertEquals(0xFF22C4D8.toInt(), event.accentArgb)
        val expected = JarvisWindowGaze.fromProjection(
            projected.x, projected.y, headX, headY, viewportW, viewportH)
        assertEquals(expected.x, event.gazeX, 0.0001f)
        assertEquals(expected.y, event.gazeY, 0.0001f)
        assertNotEquals(restGaze.x, event.gazeX, 0.001f)
        assertTrue(projected.x != rest.centerX)
    }

    @Test
    fun noEventWithoutARealWindow() {
        val sampler = sampler()
        assertNull(consider(sampler, rest.centerX, rest.centerY, at = 10L, present = false))
        assertNull(consider(sampler, rest.centerX, rest.centerY, at = 10L, windowId = ""))
        assertNull(consider(sampler, rest.centerX, rest.centerY, at = 10L, turnId = ""))
    }

    @Test
    fun reducedMotionWithoutMovementDoesNotEmit() {
        val sampler = sampler()
        assertNull(consider(
            sampler, rest.centerX, rest.centerY, at = 20L,
            phase = WindowProjectedMotion.Phase.SETTLED,
            reduced = true, moving = false))
    }

    @Test
    fun stopEmitsClosedForARememberedWindow() {
        val sampler = sampler()
        val projected = JarvisCinemaEntrance.projectedWindow(origin, rest, 320L)
        val flying = consider(sampler, projected.x, projected.y, at = 320L)
        assertNotNull(flying)
        val closed = sampler.closed("turn-1", "work-document", 900L)
        assertNotNull(closed)
        assertEquals(WindowProjectedMotion.Phase.CLOSED, closed!!.phase)
        assertEquals("turn-1", closed.turnId)
        assertEquals("work-document", closed.windowId)
        assertEquals(flying!!.gazeX, closed.gazeX, 0.0001f)
        assertNull(sampler.closed("turn-1", "work-document", 910L))
    }

    @Test
    fun stopAfterReducedMotionStillClosesAPresentWindow() {
        val sampler = sampler()
        assertNull(consider(
            sampler, rest.centerX, rest.centerY, at = 10L,
            phase = WindowProjectedMotion.Phase.SETTLED,
            reduced = true, moving = false))
        val expected = JarvisWindowGaze.fromProjection(
            rest.centerX, rest.centerY, headX, headY, viewportW, viewportH)
        val closed = sampler.closeActive("turn-1", 40L).single()
        assertEquals(WindowProjectedMotion.Phase.CLOSED, closed.phase)
        assertEquals("work-document", closed.windowId)
        assertEquals(expected.x, closed.gazeX, 0.0001f)
        assertEquals(expected.y, closed.gazeY, 0.0001f)
    }

    @Test
    fun tinyGazeChangesAreRateLimited() {
        val sampler = sampler()
        val first = consider(sampler, 200f, 480f, at = 100L)
        assertNotNull(first)
        assertNull(consider(sampler, 201f, 480f, at = 116L))
        val later = consider(sampler, 360f, 420f, at = 200L)
        assertNotNull(later)
        assertTrue(kotlin.math.abs(later!!.gazeX - first!!.gazeX) >= WindowProjectedMotionSampler.MIN_GAZE_DELTA)
    }

    @Test
    fun windowIdsAreStablePerWorkSlot() {
        assertEquals("work-document", WindowProjectedMotionSampler.windowIdOf(JarvisShellLayout.Slot.WORK_DOCUMENT))
        assertEquals("work-process", WindowProjectedMotionSampler.windowIdOf(JarvisShellLayout.Slot.WORK_PROCESS))
        assertEquals("work-draft", WindowProjectedMotionSampler.windowIdOf(JarvisShellLayout.Slot.WORK_DRAFT))
        assertNull(WindowProjectedMotionSampler.windowIdOf(JarvisShellLayout.Slot.BOTTOM_NAV))
    }

    @Test
    fun doesNotReopenAfterClosedOnTheSameTurn() {
        val sampler = sampler()
        val projected = JarvisCinemaEntrance.projectedWindow(origin, rest, 320L)
        assertNotNull(consider(sampler, projected.x, projected.y, at = 320L))
        assertNotNull(sampler.closed("turn-1", "work-document", 900L))
        assertNull(consider(sampler, projected.x, projected.y, at = 920L,
            phase = WindowProjectedMotion.Phase.CLOSING))
    }

    @Test fun foreignEndCannotCloseAnotherTurnsWindow() {
        val sampler = sampler()
        consider(sampler, 200f, 480f, 100L, turnId = "b")
        assertNull(sampler.closed("a", "work-document", 120L))
        assertTrue(sampler.closeActive("a", 130L).isEmpty())
        assertEquals("b", sampler.closeActive("b", 140L).single().turnId)
    }

    @Test fun bulkCloseLeavesOtherTurnsActive() {
        val sampler = sampler()
        consider(sampler, 200f, 480f, 100L, turnId = "a", windowId = "old")
        consider(sampler, 300f, 480f, 110L, turnId = "b", windowId = "new")
        assertEquals(listOf("old"), sampler.closeActive("a", 120L).map { it.windowId })
        assertEquals(listOf("new"), sampler.closeActive("b", 130L).map { it.windowId })
    }

    @Test fun reducedMotionReuseKeepsTheLatestOwnerAndProjection() {
        val sampler = sampler()
        consider(sampler, 200f, 480f, 100L, turnId = "a")
        assertNull(consider(sampler, 350f, 420f, 110L, turnId = "b", reduced = true, moving = false))
        assertTrue(sampler.closeActive("a", 120L).isEmpty())
        val closed = sampler.closeActive("b", 130L).single()
        val expected = JarvisWindowGaze.fromProjection(350f, 420f, headX, headY, viewportW, viewportH)
        assertEquals(expected.x, closed.gazeX, 0.0001f)
        assertEquals(expected.y, closed.gazeY, 0.0001f)
    }

    @Test fun closedWindowCanBeReusedByANewTurn() {
        val sampler = sampler()
        consider(sampler, 200f, 480f, 100L, turnId = "a")
        assertNotNull(sampler.closed("a", "work-document", 110L))
        assertNull(consider(sampler, 300f, 480f, 120L, turnId = "a", reduced = true, moving = false))
        assertTrue(sampler.closeActive("a", 130L).isEmpty())
        assertNotNull(consider(sampler, 300f, 480f, 140L, turnId = "b"))
        assertNull(sampler.closed("a", "work-document", 150L))
        assertEquals("b", sampler.closeActive("b", 160L).single().turnId)
    }
}
