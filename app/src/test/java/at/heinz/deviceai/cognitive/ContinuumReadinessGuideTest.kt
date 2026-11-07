package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ContinuumReadinessGuideTest {
    private fun ready() = ContinuumReadinessGuide.Snapshot(
        offlineVoiceAvailable = true, voiceEnabled = true, onDeviceSpeechAvailable = true,
        microphoneGranted = true, localModelIdentityBound = true, cameraHardwareSupported = true,
        cameraPermissionGranted = true, cameraOwnerEnabled = true, cameraState = CameraTrackingState.TRACKING)

    @Test fun unknownOrMissingCapabilitiesNeverClaimSuccessfulChecks() {
        val rows = ContinuumReadinessGuide.rows(ContinuumReadinessGuide.Snapshot())
        assertTrue(rows.none { it.status == ContinuumReadinessGuide.Status.AVAILABLE })
        assertTrue(rows.none { "erfolgreich geprüft" in it.message })
    }
    @Test fun availableHardwareRemainsARequestForOwnerTesting() {
        val rows = ContinuumReadinessGuide.rows(ready())
        assertTrue(rows.filter { it.id != "connectors" }.all { it.status == ContinuumReadinessGuide.Status.AVAILABLE })
        assertTrue(rows.first { it.id == "voice" }.message.contains("Test"))
        assertEquals(ContinuumReadinessGuide.Status.NOT_IMPLEMENTED, rows.first { it.id == "connectors" }.status)
    }
    @Test fun noOfflineVoiceNeverSuggestsCloudFallback() {
        val row = ContinuumReadinessGuide.rows(ready().copy(offlineVoiceAvailable = false)).first { it.id == "voice" }
        assertEquals(ContinuumReadinessGuide.Status.UNAVAILABLE, row.status)
        assertTrue(row.message.contains("offline"))
        assertEquals(ContinuumReadinessGuide.Action.SHOW_VOICE_HELP, row.action)
    }
    @Test fun mutedVoiceNeedsExplicitEnableBeforeSample() {
        val row = ContinuumReadinessGuide.rows(ready().copy(voiceEnabled = false)).first { it.id == "voice" }
        assertEquals(ContinuumReadinessGuide.Action.ENABLE_VOICE, row.action)
    }
    @Test fun microphoneAndCameraPermissionsAreIndependent() {
        val rows = ContinuumReadinessGuide.rows(ready().copy(microphoneGranted = false, cameraPermissionGranted = false))
        assertEquals(ContinuumReadinessGuide.Action.REQUEST_MICROPHONE, rows.first { it.id == "speech" }.action)
        assertEquals(ContinuumReadinessGuide.Action.REQUEST_CAMERA, rows.first { it.id == "camera" }.action)
    }
    @Test fun unsupportedCameraDoesNotRequestUselessPermission() {
        val row = ContinuumReadinessGuide.rows(ready().copy(cameraHardwareSupported = false, cameraPermissionGranted = false)).first { it.id == "camera" }
        assertEquals(ContinuumReadinessGuide.Status.UNAVAILABLE, row.status)
        assertEquals(ContinuumReadinessGuide.Action.NONE, row.action)
    }
    @Test fun cameraPermissionAloneDoesNotEnableCapture() {
        val row = ContinuumReadinessGuide.rows(ready().copy(cameraOwnerEnabled = false)).first { it.id == "camera" }
        assertEquals(ContinuumReadinessGuide.Action.ENABLE_CAMERA_TEST, row.action)
        assertEquals(ContinuumReadinessGuide.Status.NEEDS_SETUP, row.status)
    }
    @Test fun noFaceAndAmbiguityHaveHonestInstructions() {
        for (state in listOf(CameraTrackingState.NO_FACE, CameraTrackingState.AMBIGUOUS)) {
            val row = ContinuumReadinessGuide.rows(ready().copy(cameraState = state)).first { it.id == "camera" }
            assertNotEquals(ContinuumReadinessGuide.Status.AVAILABLE, row.status)
        }
    }
    @Test fun staleOrUnboundModelNeverBecomesReadyFromVoiceAvailability() {
        val row = ContinuumReadinessGuide.rows(ready().copy(localModelIdentityBound = false)).first { it.id == "model" }
        assertEquals(ContinuumReadinessGuide.Action.OPEN_MODELS, row.action)
        assertEquals(ContinuumReadinessGuide.Status.NEEDS_SETUP, row.status)
    }
    @Test fun recognizerSampleChecksWholeExpectedPhraseOnly() {
        assertTrue(ContinuumReadinessGuide.matchesSpeechSample("Continuum, ich bin bereit!"))
        assertFalse(ContinuumReadinessGuide.matchesSpeechSample("Ich bin bereit, starte Gmail."))
        assertFalse(ContinuumReadinessGuide.matchesSpeechSample("Continuum ich bin bereit und lösche alles"))
        assertFalse(ContinuumReadinessGuide.matchesSpeechSample("x".repeat(3_000)))
    }
    @Test fun connectorInformationNeverClaimsAccountOrScopeAuthorization() {
        val services = ContinuumReadinessGuide.connectors()
        assertEquals(listOf("Google / Gmail", "Facebook", "TikTok"), services.map { it.name })
        assertTrue(services.all { it.status == ContinuumReadinessGuide.Status.NOT_IMPLEMENTED })
        assertTrue(services.all { it.prerequisite.isNotBlank() })
    }
}
