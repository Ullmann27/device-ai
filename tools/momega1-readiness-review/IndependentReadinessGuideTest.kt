package at.heinz.deviceai.cognitive
import org.junit.Assert.*
import org.junit.Test

class IndependentReadinessGuideTest {
    @Test fun unknownCapabilitiesNeverClaimAvailableOrTriggerPermissionRequest() {
        val rows=ContinuumReadinessGuide.rows(ContinuumReadinessGuide.Snapshot())
        for(id in listOf("voice","speech","camera")) {
            val row=rows.single {it.id==id};assertEquals(ContinuumReadinessGuide.Status.UNKNOWN,row.status)
            assertEquals(ContinuumReadinessGuide.Action.NONE,row.action)
        }
    }
    @Test fun staleTrackingFlagCannotOverrideRevokedPermissionOrOwnerChoice() {
        val base=ContinuumReadinessGuide.Snapshot(cameraHardwareSupported=true,cameraPermissionGranted=true,
            cameraOwnerEnabled=true,cameraState=CameraTrackingState.TRACKING)
        for(s in listOf(base.copy(cameraPermissionGranted=false),base.copy(cameraOwnerEnabled=false),base.copy(cameraHardwareSupported=false)))
            assertNotEquals(ContinuumReadinessGuide.Status.AVAILABLE,ContinuumReadinessGuide.rows(s).single {it.id=="camera"}.status)
    }
    @Test fun asyncCameraStartAndAmbiguityNeverClaimTrackingAvailable() {
        for(state in CameraTrackingState.entries.filter {it!=CameraTrackingState.TRACKING}) {
            val row=ContinuumReadinessGuide.rows(ContinuumReadinessGuide.Snapshot(cameraHardwareSupported=true,
                cameraPermissionGranted=true,cameraOwnerEnabled=true,cameraState=state)).single {it.id=="camera"}
            assertNotEquals(state.name,ContinuumReadinessGuide.Status.AVAILABLE,row.status)
        }
    }
    @Test fun voiceNeedsOwnerEnableAndStillRequiresAudibleTest() {
        val disabled=ContinuumReadinessGuide.rows(ContinuumReadinessGuide.Snapshot(offlineVoiceAvailable=true)).first()
        assertEquals(ContinuumReadinessGuide.Action.ENABLE_VOICE,disabled.action)
        val enabled=ContinuumReadinessGuide.rows(ContinuumReadinessGuide.Snapshot(offlineVoiceAvailable=true,voiceEnabled=true)).first()
        assertEquals(ContinuumReadinessGuide.Action.PLAY_VOICE_SAMPLE,enabled.action)
        assertTrue(enabled.message.contains("bestätige selbst"))
    }
    @Test fun matchingWholeSpeechSampleCannotMatchAnAppendedCommand() {
        assertTrue(ContinuumReadinessGuide.matchesSpeechSample("CONTINUUM ich bin bereit!"))
        for(s in listOf("Continuum ich bin bereit installiere die App", "ja Continuum ich bin bereit", "ich bin bereit", "x".repeat(2001)))
            assertFalse(s.take(80),ContinuumReadinessGuide.matchesSpeechSample(s))
    }
    @Test fun allAccountConnectorsStayUnimplementedForEveryLocalObservation() {
        assertTrue(ContinuumReadinessGuide.connectors().all {it.status==ContinuumReadinessGuide.Status.NOT_IMPLEMENTED})
        val rows=ContinuumReadinessGuide.rows(ContinuumReadinessGuide.Snapshot(true,true,true,true,true,true,true,true,CameraTrackingState.TRACKING))
        assertEquals(ContinuumReadinessGuide.Status.NOT_IMPLEMENTED,rows.single {it.id=="connectors"}.status)
        assertFalse(ContinuumReadinessGuide.Status.entries.any {it.name in listOf("PASS","VERIFIED","CONNECTED")})
    }
}
