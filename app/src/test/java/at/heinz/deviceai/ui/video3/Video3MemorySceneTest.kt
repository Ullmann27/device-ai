package at.heinz.deviceai.ui.video3

import org.junit.Assert.*
import org.junit.Test

class Video3MemorySceneTest {
    @Test fun repeatedEventsCannotExtendTheEpisode() {
        val scene = Video3MemoryScene()
        assertTrue(scene.start(1_000L))
        for (now in 1_001L until 5_800L step 73L) assertFalse(scene.start(now))
        assertNotNull(scene.sample(5_799L))
        assertNull(scene.sample(5_800L))
        assertFalse(scene.isActive)
    }

    @Test fun stopAndClockRollbackCannotReplay() {
        val scene = Video3MemoryScene()
        assertTrue(scene.start(1_000L))
        scene.clear()
        assertNull(scene.sample(2_000L))
        assertTrue(scene.start(3_000L))
        assertNotNull(scene.sample(4_000L))
        assertNull(scene.sample(3_999L))
        assertNull(scene.sample(5_000L))
    }

    @Test fun flightOrbAndReturnAreOrderedAndFinite() {
        val scene = Video3MemoryScene()
        assertTrue(scene.start(0L))
        assertEquals(0f, scene.sample(1_200L)!!.orbProgress)
        val orb = scene.sample(2_800L)!!
        assertEquals(1f, orb.orbProgress)
        assertEquals(0f, orb.returnProgress)
        val returning = scene.sample(4_300L)!!
        assertTrue(returning.returnProgress in .01f.. .99f)
        assertTrue(returning.opacity in 0f..1f)
        assertNull(scene.sample(Long.MAX_VALUE))
    }
}
