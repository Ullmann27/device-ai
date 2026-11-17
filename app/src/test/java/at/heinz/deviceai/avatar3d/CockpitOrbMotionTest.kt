package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class CockpitOrbMotionTest {
    @Test fun fiveOriginalRingsKeepRestPoseAndOppositeDirections() {
        for (index in 0..4) {
            val rest = CockpitOrbMotion.sample(index, .4f)
            assertEquals(0f, rest.yawDegrees, 0f)
            assertEquals(0f, rest.pitchDegrees, 0f)
        }
        assertTrue(CockpitOrbMotion.sample(0, 1f).yawDegrees > 0f)
        assertTrue(CockpitOrbMotion.sample(1, 1f).yawDegrees < 0f)
        assertTrue(CockpitOrbMotion.sample(3, 1f).yawDegrees < 0f)
        assertEquals(CockpitOrbMotion.sample(0, 1f).yawDegrees * .45f,
            CockpitOrbMotion.sample(4, 1f).yawDegrees, .0001f)
    }

    @Test fun enlargedOrbCannotPutItsFrontRingAheadOfAvatar() {
        for ((width, height) in listOf(388f to 531f, 852f to 412f, 216f to 640f)) {
            val frame = CockpitStageFrame(width, height, width * .1f, height * .55f, width * .9f, height * .95f)
            val diameter = minOf(width * .94f, height * .525f)
            val left = (width - diameter) * .5f
            val matrix = frame.orbTransform(left, 0f, left + diameter, diameter)
            assertTrue(matrix[14] + matrix[0] * CockpitStageFrame.ORB_MAX_RADIUS <= -.3999f)
            val projectedCenter = frame.project(matrix[12], matrix[13], matrix[14])
            assertEquals(width * .5f, projectedCenter.first, .001f)
            assertEquals(diameter * .5f, projectedCenter.second, .001f)
        }
    }
}
