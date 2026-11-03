package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class AvatarCameraPolicyTest {
    private var now = 1_000L
    private fun policy() = AvatarCameraPolicy { now }
    private val geometry = CameraFrameGeometry(0, 0, 1000, 1000, 0, 0)
    private fun face(x: Int = 700, y: Int = 500, score: Int = 90) =
        CameraFaceBox(x - 50, y - 50, x + 50, y + 50, score)
    private fun start(p: AvatarCameraPolicy) = p.start(true, true, true, true)!!

    @Test fun allFourAdmissionConditionsAreRequired() {
        for (missing in 0..3) {
            val gates = MutableList(4) { true }; gates[missing] = false
            val p = policy()
            assertNull(p.start(gates[0], gates[1], gates[2], gates[3]))
            assertNull(p.latest())
            assertNotEquals(CameraTrackingState.TRACKING, p.state)
        }
    }
    @Test fun freshSingleHardwareFaceProducesBoundedPositionOnly() {
        val p = policy(); val token = start(p)
        val sample = p.acceptFrame(token, listOf(face()), geometry)!!
        assertTrue(sample.x in -1f..1f); assertTrue(sample.y in -1f..1f)
        assertTrue(sample.x < 0f) // front-camera mirror after sensor rotation
        assertEquals(90, sample.confidence); assertEquals(now, sample.observedAtElapsedMillis)
        assertEquals(CameraTrackingState.TRACKING, p.state)
    }
    @Test fun noFaceOrMultipleFacesClearsPreviouslyObservedGaze() {
        for (faces in listOf(emptyList(), listOf(face(), face(300)))) {
            val p = policy(); val token = start(p)
            assertNotNull(p.acceptFrame(token, listOf(face()), geometry))
            now += 100
            assertNull(p.acceptFrame(token, faces, geometry)); assertNull(p.latest())
            assertNotEquals(CameraTrackingState.TRACKING, p.state)
        }
    }
    @Test fun lowConfidenceOrInvalidBoundsCannotMasqueradeAsTracking() {
        for (f in listOf(face(score = 59), face(score = 101), face(-200), CameraFaceBox(500, 500, 400, 600, 90))) {
            val p = policy(); val token = start(p)
            assertNull(p.acceptFrame(token, listOf(f), geometry)); assertNull(p.latest())
        }
    }
    @Test fun oldGenerationCannotRestoreGazeAfterStopOrRestart() {
        val p = policy(); val old = start(p)
        p.acceptFrame(old, listOf(face()), geometry); p.stop()
        assertNull(p.acceptFrame(old, listOf(face()), geometry))
        val fresh = start(p); assertNotEquals(old, fresh)
        assertNull(p.acceptFrame(old, listOf(face()), geometry)); assertNull(p.latest())
        assertNotNull(p.acceptFrame(fresh, listOf(face()), geometry))
    }
    @Test fun revocationAndBackgroundClearImmediately() {
        for (gate in 0..2) {
            val p = policy(); val token = start(p)
            p.acceptFrame(token, listOf(face()), geometry)
            val gates = MutableList(3) { true }; gates[gate] = false
            assertFalse(p.updateGate(token, gates[0], gates[1], gates[2]))
            assertNull(p.latest()); assertFalse(p.isCurrent(token))
        }
    }
    @Test fun stalePositionExpiresWithoutInventingContinuedTracking() {
        val p = policy(); val token = start(p)
        p.acceptFrame(token, listOf(face()), geometry)
        now += 750; assertNotNull(p.latest())
        now++; assertNull(p.latest()); assertEquals(CameraTrackingState.NO_FACE, p.state)
    }
    @Test fun missingFrameHeartbeatTerminatesSession() {
        val p = policy(); val token = start(p)
        now += 4_999; assertFalse(p.expireHeartbeat(token))
        now++; assertTrue(p.expireHeartbeat(token)); assertFalse(p.isCurrent(token))
        assertEquals(CameraTrackingState.ERROR, p.state)
    }
    @Test fun emptyButRealFramesKeepHeartbeatWithoutInventingFace() {
        val p = policy(); val token = start(p)
        repeat(10) { now += 1_000; p.acceptFrame(token, emptyList(), geometry); assertFalse(p.expireHeartbeat(token)) }
        assertNull(p.latest()); assertEquals(CameraTrackingState.NO_FACE, p.state)
    }
    @Test fun clockRollbackFailsClosed() {
        val p = policy(); val token = start(p)
        p.acceptFrame(token, listOf(face()), geometry); now--
        assertNull(p.acceptFrame(token, listOf(face()), geometry)); assertFalse(p.isCurrent(token))
        assertEquals(CameraTrackingState.ERROR, p.state)
    }
    @Test fun motionIsRateLimitedRatherThanJumpingBetweenFramePositions() {
        val p = policy(); val token = start(p)
        var previous = p.acceptFrame(token, listOf(face(900)), geometry)!!
        repeat(8) {
            now += 100
            val next = p.acceptFrame(token, listOf(face(100)), geometry)!!
            assertTrue(kotlin.math.abs(next.x - previous.x) <= 0.2001f)
            previous = next
        }
    }
    @Test fun duplicateRapidFramesDoNotAccelerateSmoothing() {
        val p = policy(); val token = start(p)
        val first = p.acceptFrame(token, listOf(face()), geometry)!!
        repeat(30) { assertEquals(first, p.acceptFrame(token, listOf(face()), geometry)) }
        assertEquals(first, p.latest())
    }
    @Test fun frontSensorRotationAndDisplayRotationUseOneBoundedCoordinateContract() {
        val point = CameraCoordinateMapping.normalized(face(750, 500), geometry)!!
        assertEquals(-0.5f, point.first, 0.0001f); assertEquals(0f, point.second, 0.0001f)
        val rotated = CameraCoordinateMapping.normalized(face(750, 500), geometry.copy(sensorOrientationDegrees = 90))!!
        assertEquals(0f, rotated.first, 0.0001f); assertEquals(0.5f, rotated.second, 0.0001f)
        val display = CameraCoordinateMapping.normalized(face(750, 500), geometry.copy(displayRotationDegrees = 90))!!
        assertEquals(rotated, display)
        assertNull(CameraCoordinateMapping.normalized(face(), geometry.copy(sensorOrientationDegrees = 45)))
        assertNull(CameraCoordinateMapping.normalized(face(), geometry.copy(right = 0)))
    }
    @Test fun overflowAndUnboundedMetadataAreRejected() {
        val p = policy(); val token = start(p)
        assertNull(p.acceptFrame(token, List(9) { face() }, geometry))
        assertNull(CameraCoordinateMapping.normalized(face(), geometry.copy(left = Int.MIN_VALUE, right = Int.MAX_VALUE)))
    }
    @Test fun delayedNativeCallbackCannotRelabelOldCaptureAsCurrent() {
        val p = policy(); val token = start(p)
        val capturedAt = now
        now += 751
        assertNull(p.acceptFrame(token, listOf(face()), geometry, capturedAt))
        assertNull(p.latest())
        assertEquals(CameraTrackingState.NO_FACE, p.state)
    }
    @Test fun futureOrMissingCaptureTimeCannotRenewHeartbeat() {
        for (invalid in listOf(-1L, Long.MAX_VALUE)) {
            val p = policy(); val token = start(p)
            repeat(5) {
                now += 1_000
                assertNull(p.acceptFrame(token, listOf(face()), geometry, invalid))
            }
            assertTrue(p.expireHeartbeat(token))
            assertEquals(CameraTrackingState.ERROR, p.state)
        }
    }
    @Test fun validCaptureKeepsItsActualTimestampAndOriginalExpiry() {
        val p = policy(); val token = start(p)
        val capturedAt = now
        now += 600
        val observed = p.acceptFrame(token, listOf(face()), geometry, capturedAt)!!
        assertEquals(capturedAt, observed.observedAtElapsedMillis)
        now += 150
        assertNotNull(p.latest())
        now++
        assertNull(p.latest())
    }
}
