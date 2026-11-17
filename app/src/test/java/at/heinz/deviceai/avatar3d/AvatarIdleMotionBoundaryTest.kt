package at.heinz.deviceai.avatar3d

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarIdleMotionBoundaryTest {
    @Test fun signedZeroAndWholeCycleOffsetsShareTheNeutralPhase() {
        val reference = AvatarIdleMotion().apply { update(0f) }
        for (seconds in listOf(-0.0f, -460f, -920f, 460f, 920f)) {
            val actual = AvatarIdleMotion().apply { update(seconds) }
            assertEquals("head yaw at $seconds", reference.headYaw, actual.headYaw, 0f)
            assertEquals("head pitch at $seconds", reference.headPitch, actual.headPitch, 0f)
            assertEquals("eye yaw at $seconds", reference.eyeYaw, actual.eyeYaw, 0f)
            assertEquals("eye pitch at $seconds", reference.eyePitch, actual.eyePitch, 0f)
            assertEquals("blink at $seconds", reference.blinkWeight, actual.blinkWeight, 0f)
        }
    }

    @Test fun negativeSubnormalPhaseIsBoundedAtTheCycleBoundary() {
        val actual = AvatarIdleMotion().apply { update(-Float.MIN_VALUE) }
        for (value in listOf(actual.headYaw, actual.headPitch, actual.eyeYaw, actual.eyePitch, actual.blinkWeight)) {
            org.junit.Assert.assertTrue("boundary outputs must stay finite", value.isFinite())
        }
    }
}
