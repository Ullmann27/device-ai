package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

/** Synthetic vectors, no identity benchmark. */
class OwnerFacePolicyTest {
    private fun feature(axis: Int = 0) = FloatArray(OwnerFaceModelContract.FEATURE_SIZE) { if (it == axis) 1f else 0f }
    private fun profile(name: String = "Fixture") = OwnerFaceProfile(name, 1_000,
        OwnerFaceModelContract.RECOGNIZER_SHA256, List(OwnerFacePolicy.ENROLLMENT_SAMPLES) { feature() })
    private fun quality() = OwnerFaceQuality(320, 240, 70f, 20f, 140f, 180f, .98f, 48f, .05f, .1f, 110.0, 150.0)
    private fun observation(time: Long, vector: FloatArray = feature(), faces: Int = 1) =
        OwnerFaceObservation(faces, time, quality(), vector)

    @Test fun missingProfileNeverMatches() {
        val policy = OwnerFacePolicy()
        repeat(10) { assertEquals(OwnerFacePhase.NOT_ENROLLED, policy.accept(observation(it * 500L), it * 500L, 1_000).phase) }
    }

    @Test fun enrollmentNeedsEightSeparatedConsistentFrames() {
        val policy = OwnerFacePolicy(enrollingName = "Fixture")
        repeat(7) { index ->
            val decision = policy.accept(observation(index * 500L), index * 500L, 1_000)
            assertEquals(OwnerFacePhase.ENROLLING, decision.phase)
            assertEquals(index + 1, decision.acceptedSamples)
            assertNull(decision.profile)
        }
        val saved = policy.accept(observation(3_500), 3_500, 1_000)
        assertEquals(OwnerFacePhase.ENROLLED, saved.phase)
        assertTrue(saved.profile!!.valid())
        assertEquals("Fixture", saved.profile!!.displayName)
        assertEquals(OwnerFacePhase.OFF, policy.accept(observation(4_000), 4_000, 1_000).phase)
    }

    @Test fun switchedPersonCannotEnterExistingEnrollment() {
        val policy = OwnerFacePolicy(enrollingName = "Fixture")
        policy.accept(observation(0), 0, 1_000)
        policy.accept(observation(500), 500, 1_000)
        val switched = policy.accept(observation(1_000, feature(1)), 1_000, 1_000)
        assertEquals(OwnerFacePhase.NO_MATCH, switched.phase)
        assertEquals(0, switched.acceptedSamples)
        assertNull(switched.profile)
    }

    @Test fun multipleFacesResetEnrollmentAndNeverSelectClosest() {
        val enrollment = OwnerFacePolicy(enrollingName = "Fixture")
        enrollment.accept(observation(0), 0, 1_000)
        val interrupted = enrollment.accept(observation(500, faces = 2), 500, 1_000)
        assertEquals(OwnerFacePhase.MULTIPLE_FACES, interrupted.phase)
        assertEquals(0, interrupted.acceptedSamples)
        val verification = OwnerFacePolicy(profile = profile())
        repeat(5) { index ->
            assertEquals(OwnerFacePhase.MULTIPLE_FACES,
                verification.accept(observation(index * 500L, faces = 2), index * 500L, 1_000).phase)
        }
    }

    @Test fun matchRequiresThreeFramesAndDisappearsWhenEvidenceChanges() {
        val policy = OwnerFacePolicy(profile = profile())
        assertEquals(OwnerFacePhase.RECOGNIZING, policy.accept(observation(0), 0, 1_000).phase)
        assertEquals(OwnerFacePhase.RECOGNIZING, policy.accept(observation(500), 500, 1_000).phase)
        assertEquals(OwnerFacePhase.MATCH, policy.accept(observation(1_000), 1_000, 1_000).phase)
        assertEquals(OwnerFacePhase.NO_MATCH, policy.accept(observation(1_500, feature(1)), 1_500, 1_000).phase)
        assertEquals(OwnerFacePhase.RECOGNIZING, policy.accept(observation(2_000), 2_000, 1_000).phase)
        assertEquals(OwnerFacePhase.NO_FACE, policy.accept(observation(2_500, faces = 0), 2_500, 1_000).phase)
    }

    @Test fun replayRapidStaleFutureAndWrongModelFramesDoNotAdvance() {
        val policy = OwnerFacePolicy(enrollingName = "Fixture")
        policy.accept(observation(1_000), 1_000, 1_000)
        for ((captured, now) in listOf(1_000L to 1_000L, 1_100L to 1_100L, 1_200L to 4_000L, 5_000L to 4_000L)) {
            val result = policy.accept(observation(captured), now, 1_000)
            assertEquals(OwnerFacePhase.LOW_QUALITY, result.phase)
            assertEquals(1, result.acceptedSamples)
            assertNull(result.profile)
        }
        val wrong = OwnerFaceObservation(1, 6_000, quality(), feature(), "0".repeat(64))
        assertEquals(OwnerFacePhase.LOW_QUALITY, policy.accept(wrong, 6_000, 1_000).phase)
    }

    @Test fun nonFiniteZeroWrongSizeAndDamagedProfileFailClosed() {
        for (bad in listOf(FloatArray(128), FloatArray(127), feature().apply { this[0] = Float.NaN },
            feature().apply { this[0] = Float.POSITIVE_INFINITY })) {
            val policy = OwnerFacePolicy(profile = profile())
            assertEquals(OwnerFacePhase.LOW_QUALITY, policy.accept(observation(0, bad), 0, 1_000).phase)
        }
        val damaged = profile().also { it.features[0][0] = Float.NaN }
        assertFalse(damaged.valid())
        assertEquals(OwnerFacePhase.NOT_ENROLLED,
            OwnerFacePolicy(profile = damaged).accept(observation(0), 0, 1_000).phase)
    }

    @Test fun lowQualityBoundsAndAnglesReject() {
        val bad = listOf(quality().copy(detectionScore = .8f), quality().copy(faceWidth = 10f),
            quality().copy(faceX = -1f), quality().copy(faceY = 220f), quality().copy(eyeSlope = 1f),
            quality().copy(noseOffset = 1f), quality().copy(brightness = 10.0),
            quality().copy(sharpnessVariance = 1.0), quality().copy(brightness = Double.NaN))
        for (sample in bad) {
            assertFalse(sample.acceptable())
            assertEquals(OwnerFacePhase.LOW_QUALITY, OwnerFacePolicy(profile = profile())
                .accept(OwnerFaceObservation(1, 0, sample, feature()), 0, 1_000).phase)
        }
    }

    @Test fun closedPolicyCannotPublishMatchOrEnrollment() {
        val policy = OwnerFacePolicy(profile = profile())
        policy.accept(observation(0), 0, 1_000)
        policy.close()
        repeat(4) { assertEquals(OwnerFacePhase.OFF, policy.accept(observation(500L * (it + 1)), 500L * (it + 1), 1_000).phase) }
    }
}
