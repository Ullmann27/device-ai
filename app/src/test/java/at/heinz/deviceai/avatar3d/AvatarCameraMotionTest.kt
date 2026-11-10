package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class AvatarCameraMotionTest {
    @Test fun opensAndClosesWithoutOvershoot() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 1_000L)
        var previous = 0f
        for (time in 1_016L..1_600L step 16L) {
            val value = camera.advance(time, true)
            assertTrue(value in previous..1f)
            previous = value
        }
        assertEquals(1f, camera.progress, 0f)
        camera.setWorkActive(false, 1_600L)
        for (time in 1_616L..2_200L step 16L) {
            val value = camera.advance(time, true)
            assertTrue(value in 0f..previous)
            previous = value
        }
        assertEquals(0f, camera.progress, 0f)
    }

    @Test fun reversalStartsAtTheCurrentProjection() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 0L)
        for (time in 16L..256L step 16L) camera.advance(time, true)
        val halfway = camera.progress
        assertTrue(halfway in .3f.. .6f)
        camera.setWorkActive(false, 256L)
        assertEquals(halfway, camera.progress, 0f)
        assertEquals(halfway, camera.advance(256L, true), 0f)
        assertTrue(camera.advance(272L, true) < halfway)
        for (time in 288L..896L step 16L) camera.advance(time, true)
        assertEquals(0f, camera.progress, 0f)
    }

    @Test fun repeatedRequestsDoNotRestartTheFlight() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 0L)
        for (time in 16L..640L step 16L) {
            camera.setWorkActive(true, time)
            camera.advance(time, true)
        }
        assertEquals(1f, camera.progress, 0f)
    }

    @Test fun cancelledMotionCannotReplayAfterResume() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 0L)
        camera.advance(100L, true)
        assertTrue(camera.progress > 0f)
        assertEquals(0f, camera.advance(110L, false), 0f)
        assertFalse(camera.workActive)
        for (time in 120L..1_000L step 20L) camera.advance(time, true)
        assertEquals(0f, camera.progress, 0f)
        camera.setWorkActive(true, 1_000L)
        for (time in 1_020L..1_600L step 20L) camera.advance(time, true)
        assertEquals(1f, camera.progress, 0f)
    }

    @Test fun disabledAndInvalidDurationScalesCancel() {
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val camera = AvatarCameraMotion()
            camera.setWorkActive(true, 0L)
            camera.advance(100L, true)
            assertEquals(0f, camera.advance(120L, true, scale), 0f)
            assertFalse(camera.workActive)
        }
    }

    @Test fun systemDurationScaleIsHonoured() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 0L)
        for (time in 20L..520L step 20L) camera.advance(time, true, 2f)
        assertEquals(.5f, camera.progress, .00001f)
        for (time in 540L..1_040L step 20L) camera.advance(time, true, 2f)
        assertEquals(1f, camera.progress, 0f)
    }

    @Test fun backwardsDuplicateAndDelayedFramesStayBounded() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 1_000L)
        val first = camera.advance(1_050L, true)
        assertEquals(first, camera.advance(1_050L, true), 0f)
        assertEquals(first, camera.advance(900L, true), 0f)
        val delayed = camera.advance(100_000L, true)
        assertTrue("Long pauses cannot jump endpoints", delayed > first && delayed < .2f)
    }

    @Test fun resetImmediatelyRestoresTheRestProjection() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 0L)
        camera.advance(100L, true)
        camera.reset()
        assertEquals(0f, camera.progress, 0f)
        assertFalse(camera.workActive)
        assertEquals(0f, camera.advance(200L, true), 0f)
    }

    @Test fun delayedWindowClosureStartsItsOwnFullReturnDuration() {
        val camera = AvatarCameraMotion()
        camera.setWorkActive(true, 0L)
        for (time in 16L..640L step 16L) camera.advance(time, true)
        assertEquals(1f, camera.progress, 0f)
        // Window retains projection while closing.
        for (time in 656L..1_120L step 16L) camera.advance(time, true)
        assertEquals(1f, camera.progress, 0f)
        val closedAt = 1_120L
        camera.setWorkActive(false, closedAt)
        for (elapsed in 16L..512L step 16L) camera.advance(closedAt + elapsed, true)
        assertEquals("512 ms is still inside the canonical return", 1.4543533E-5f, camera.progress, 0f)
        assertFalse(camera.workActive)
        assertEquals("First frame beyond 520 ms reaches exact rest", 0f,
            camera.advance(closedAt + 528L, true), 0f)
        assertEquals("Later frames cannot replay the return", 0f,
            camera.advance(closedAt + 544L, true), 0f)
    }
}
