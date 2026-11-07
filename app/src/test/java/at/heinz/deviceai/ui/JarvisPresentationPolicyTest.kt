package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-state presentation contracts, independent of screenshot likeness or a running camera. */
class JarvisPresentationPolicyTest {
    @Test fun absentSourcesDoNotClaimLiveSpeechCameraOrAudio() {
        val status = ContinuumDashboardState.PresentationStatus()
        assertFalse(status.live)
        assertFalse(status.tracking)
        assertNull(status.measuredAudio)
        assertNull(status.localOnly)
    }

    @Test fun trackingRequiresBothAnActiveSessionAndAnObservedTrackingState() {
        for (session in listOf(false, true)) for (observed in listOf(false, true)) {
            val status = ContinuumDashboardState.PresentationStatus(
                cameraSessionActive = session, cameraTracking = observed)
            assertEquals("session=$session, observed=$observed", session && observed, status.tracking)
            assertFalse("Camera state is not evidence of a running reasoning or speech turn", status.live)
        }
    }

    @Test fun onlyActualListeningSpeakingOrProcessingMakesTheDialogLive() {
        val idle = ContinuumDashboardState.PresentationStatus(localOnly = true, audioLevel = .5f)
        assertFalse("A cached measurement and local routing are not an active turn", idle.live)
        assertTrue(idle.copy(listening = true).live)
        assertTrue(idle.copy(speaking = true).live)
        assertTrue(idle.copy(processing = true).live)
        assertNull("Speaking must not synthesize a PCM envelope",
            ContinuumDashboardState.PresentationStatus(speaking = true).measuredAudio)
    }

    @Test fun malformedAudioMeasurementsStayUnknownAndValidBoundariesArePreserved() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.001f, 1.001f)) {
            assertNull("Invalid measured audio=$invalid", ContinuumDashboardState.PresentationStatus(audioLevel = invalid).measuredAudio)
        }
        for (measured in listOf(0f, .4f, 1f)) {
            assertEquals(measured, ContinuumDashboardState.PresentationStatus(audioLevel = measured).measuredAudio!!, 0f)
        }
    }

    @Test fun aLoadedModelCannotFabricateLogicLearningMemoryOrWorkers() {
        val state = ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(online = true, stateLabel = "Bereit"))
        val modules = JarvisCockpitGridContent.modules(state).associateBy { it.label }
        assertTrue(modules.getValue("Lokales Modell").ok)
        for (name in listOf("Gedächtnis", "Denken", "Assistenten", "Lernen")) {
            assertFalse("Loading a model does not start $name", modules.getValue(name).ok)
        }
    }

    @Test fun logicOnlyReportsWorkDuringTheActualWorkingPhase() {
        for (phase in listOf(null) + TurnPhase.entries) {
            val module = JarvisCockpitGridContent.modules(
                ContinuumDashboardState(cockpit = CockpitStatus(turnPhase = phase)))
                .single { it.label == "Denken" }
            assertEquals("Actual turn phase=$phase", phase == TurnPhase.WORKING, module.ok)
        }
        val agents = JarvisCockpitGridContent.modules(
            ContinuumDashboardState(cockpit = CockpitStatus(workers = emptyList(), workerCapacity = 4)))
            .single { it.label == "Assistenten" }
        assertEquals("0 aktiv", agents.value)
        assertFalse("Pool capacity is not a running-worker count", agents.ok)
    }
}
