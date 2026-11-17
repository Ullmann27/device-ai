package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class AvatarFramePacingTest {
    private fun frames(hz: Int, budget: Long, seconds: Int = 4): List<Long> {
        val pacing = AvatarFramePacing()
        val admitted = mutableListOf<Long>()
        var dueMillis = 0L
        for (tick in 1..hz * seconds) {
            val frame = tick * 1_000_000_000L / hz
            val nowMillis = frame / 1_000_000L
            if (nowMillis < dueMillis) continue
            if (pacing.admit(frame, budget)) admitted.add(frame)
            dueMillis = nowMillis + pacing.delayMillis(frame, budget, hz.toFloat())
        }
        return admitted
    }

    @Test fun budgetStaysBoundedAcrossRefreshRates() {
        for (hz in listOf(60, 90, 120)) for (budget in listOf(16L, 33L, 42L, 50L, 80L)) {
            val admitted = frames(hz, budget)
            assertTrue("$hz Hz/$budget ms must progress", admitted.size > 30)
            assertTrue("$hz Hz/$budget ms over budget", admitted.size <= 4_000 / budget + 1)
            assertTrue(admitted.zipWithNext().all { (a, b) -> b - a >= budget * 1_000_000L })
        }
    }

    @Test fun speechKeepsSixtyFramesOnSixtyAndOneTwentyHz() {
        for (hz in listOf(60, 120)) assertEquals(240, frames(hz, 16L).size)
        assertEquals(180, frames(90, 16L).size)
    }

    @Test fun idleKeepsThirtyFramesAcrossRefreshRates() {
        for (hz in listOf(60, 90, 120)) assertEquals(120, frames(hz, 33L).size)
    }

    @Test fun duplicateAndOutOfOrderFramesCannotRewindClock() {
        val pacing = AvatarFramePacing()
        assertTrue(pacing.admit(100_000_000L, 16L))
        assertFalse(pacing.admit(100_000_000L, 16L))
        assertFalse(pacing.admit(90_000_000L, 16L))
        assertFalse(pacing.admit(115_999_999L, 16L))
        assertTrue(pacing.admit(116_000_000L, 16L))
    }

    @Test fun pauseResetStartsFreshWithoutCatchUpFrames() {
        val pacing = AvatarFramePacing()
        assertTrue(pacing.admit(100_000_000L, 80L))
        pacing.reset()
        assertEquals(0L, pacing.delayMillis(110_000_000L, 80L, 120f))
        assertTrue(pacing.admit(110_000_000L, 80L))
        assertTrue(pacing.admit(5_000_000_000L, 80L))
        repeat(4) { assertFalse(pacing.admit(5_000_000_000L, 80L)) }
        assertFalse(pacing.admit(5_008_000_000L, 80L))
    }

    @Test fun thermalAndSpeechBudgetsApplyImmediately() {
        val pacing = AvatarFramePacing()
        assertTrue(pacing.admit(0L, 16L))
        assertFalse(pacing.admit(16_000_000L, 80L))
        assertFalse(pacing.admit(79_000_000L, 80L))
        assertTrue(pacing.admit(80_000_000L, 80L))
        assertTrue(pacing.admit(96_000_000L, 16L))
    }

    @Test fun refreshChangesAdjustWakeupWithoutChangingAdmission() {
        val pacing = AvatarFramePacing()
        assertTrue(pacing.admit(0L, 33L))
        assertEquals(16L, pacing.delayMillis(0L, 33L, 60f))
        assertEquals(24L, pacing.delayMillis(0L, 33L, 120f))
        assertFalse(pacing.admit(25_000_000L, 33L))
        assertTrue(pacing.admit(33_000_000L, 33L))
    }

    @Test fun missingRefreshRateUsesBoundedFallback() {
        val pacing = AvatarFramePacing()
        assertTrue(pacing.admit(0L, 33L))
        for (hz in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -120f)) {
            assertEquals(16L, pacing.delayMillis(0L, 33L, hz))
            assertFalse(pacing.admit(8_333_333L, 33L))
        }
    }

    @Test fun delayedCallbacksUseFrameTimeForMotion() {
        val pacing = AvatarFramePacing()
        assertTrue(pacing.admit(1_000_000_000L, 16L))
        assertEquals(0L, pacing.delayMillis(1_090_000_000L, 16L, 120f))
        assertTrue(pacing.admit(1_100_000_000L, 16L))
        assertFalse(pacing.admit(1_108_333_333L, 16L))
    }

    @Test fun extremeInputsDoNotOverflowIntoBusyCatchUp() {
        val pacing = AvatarFramePacing()
        assertFalse(pacing.admit(-1L, 16L))
        assertTrue(pacing.admit(0L, Long.MAX_VALUE))
        assertFalse(pacing.admit(100_000_000L, Long.MAX_VALUE))
        assertTrue(pacing.delayMillis(0L, Long.MAX_VALUE, 120f) > 0L)
        assertEquals(0L, pacing.delayMillis(Long.MAX_VALUE, 16L, Float.MIN_VALUE))
        assertEquals(0L, pacing.delayMillis(-1L, 16L, 120f))
    }
}
