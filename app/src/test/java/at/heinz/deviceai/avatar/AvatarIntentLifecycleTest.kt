package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runtime refresh must preserve explicit acceptance. */
class AvatarIntentLifecycleTest {
    private var now = 1_000L
    private val reducer = AvatarPoseReducer()
    private val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }

    @Test fun repeatedListeningPreservesTheFiniteConfirmationNod() {
        val signals = AvatarRuntimeSignals(listening = true)
        director.onRuntime(signals)
        assertTrue(director.confirmationAccepted("explicit-one"))
        now += 120
        director.onRuntime(signals)
        now += 205
        director.onRuntime(signals)
        assertEquals("Refresh must not cancel acceptance", .5f, reducer.pose(now).confirmationProgress, 1e-6f)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(reducer.pose(now), now / 1000f, true)
        assertEquals(3f, mapper.headPitch, 1e-5f)
        assertEquals(0f, mapper.jawPitch, 0f)
        now += 325
        assertEquals("Natural expiry remains finite", -1f, reducer.pose(now).confirmationProgress, 0f)
    }

    @Test fun approvalExitToListeningPreservesAcceptanceButStopClearsIt() {
        director.onRuntime(AvatarRuntimeSignals(listening = true, awaitingApproval = true))
        assertTrue(director.confirmationAccepted("explicit-two"))
        director.onRuntime(AvatarRuntimeSignals(listening = true))
        now += 325
        assertEquals(.5f, reducer.pose(now).confirmationProgress, 1e-6f)
        director.clearAnimationIntents()
        assertEquals(-1f, reducer.pose(now).confirmationProgress, 0f)
        director.onRuntime(AvatarRuntimeSignals(listening = true))
        assertEquals("Refresh cannot replay a cleared nod", -1f, reducer.pose(now).confirmationProgress, 0f)
    }

    @Test fun closeAfterGlanceExpiryClearsTheLastPoseWithoutReplayingOldWindows() {
        assertTrue(director.windowOpened("expired-window"))
        assertTrue(reducer.pose(now).gazeDirected)
        now += AvatarPoseReducer.WINDOW_GAZE_TTL_MS + 1L
        assertFalse("Refit expires the short opening glance", director.windowMoved("expired-window", GazeTarget.WORK_WINDOW))
        // Last-event pose would stay directed forever without a terminal event from close.
        assertTrue("Reproduce the stale-pose case", reducer.pose().gazeDirected)
        assertFalse("Already-expired intent cannot be closed as an active one", director.windowClosed("expired-window"))
        assertFalse("Physical close must clear the reducer", reducer.pose().gazeDirected)

        assertTrue(director.windowOpened("successor-window"))
        assertTrue(reducer.pose(now).gazeDirected)
        assertFalse("Stale close cannot retire its successor", director.windowClosed("expired-window"))
        assertTrue("Successor gaze is retained", reducer.pose(now).gazeDirected)
        assertTrue(director.windowClosed("successor-window"))
        assertFalse(reducer.pose().gazeDirected)
    }

    @Test fun reducedMotionNeverLatchesAWindowHeadPose() {
        assertTrue(director.windowOpened("reduced-window"))
        val active = reducer.pose(now)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(active, now / 1000f, false)
        assertEquals("Static render uses the underlying gaze", 0f, mapper.headYaw, 0f)
        assertEquals(0f, mapper.headPitch, 0f)
        assertEquals(0f, mapper.eyeYaw, 0f)
        assertEquals(0f, mapper.eyePitch, 0f)
        val policyPose = reducer.javaClass.methods.firstOrNull {
            it.name == "pose" && it.parameterCount == 2
        }
        assertTrue("2D and 3D share motion policy", policyPose != null)
        val still = policyPose!!.invoke(reducer, now, false) as AvatarPose
        assertEquals(0f, still.gazeX, 0f)
        assertEquals(0f, still.gazeY, 0f)
        assertEquals(-1f, still.confirmationProgress, 0f)
        now += 2_500
        assertEquals("No expiry timer is required", 0f, reducer.pose(now).gazeX, 0f)
    }
}
