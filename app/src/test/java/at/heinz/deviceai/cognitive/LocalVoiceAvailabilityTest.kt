package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.LocalVoiceAvailability.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVoiceAvailabilityTest {
    @Test fun stateFollowsTheEngineReportBeforeAnyVoiceQuestion() {
        assertEquals(State.STARTING, LocalVoiceAvailability.state(false, false, false))
        assertEquals("a voice flag without an engine report proves nothing",
            State.STARTING, LocalVoiceAvailability.state(false, true, true))
        assertEquals(State.ENGINE_FAILED, LocalVoiceAvailability.state(true, false, false))
        assertEquals("a failed engine is never ready", State.ENGINE_FAILED, LocalVoiceAvailability.state(true, false, true))
        assertEquals(State.NO_OFFLINE_VOICE, LocalVoiceAvailability.state(true, true, false))
        assertEquals(State.READY, LocalVoiceAvailability.state(true, true, true))
    }

    @Test fun everyUnavailableStateNamesItsOwnCauseAndTheRemedy() {
        assertNull(LocalVoiceAvailability.ownerNotice(State.READY, "Deutsch"))
        val starting = LocalVoiceAvailability.ownerNotice(State.STARTING, "Deutsch")!!
        val failed = LocalVoiceAvailability.ownerNotice(State.ENGINE_FAILED, "Deutsch")!!
        val missing = LocalVoiceAvailability.ownerNotice(State.NO_OFFLINE_VOICE, "Deutsch")!!
        assertEquals(3, setOf(starting, failed, missing).size)
        assertTrue(starting.contains("noch nicht bereit"))
        assertTrue(failed.contains("konnte nicht gestartet werden"))
        assertTrue(missing.contains("Keine Offline-Stimme für Deutsch"))
        for (lasting in listOf(failed, missing)) {
            assertTrue("the reply itself is not lost", lasting.contains("Antworten bleiben im Chat lesbar"))
            assertTrue("the remedy is named", lasting.contains("Text-in-Sprache"))
            assertFalse("a lasting fault is not announced as temporary", lasting.contains("noch nicht bereit"))
        }
        assertTrue(LocalVoiceAvailability.ownerNotice(State.NO_OFFLINE_VOICE, "English")!!.contains("für English"))
    }

    @Test fun onlyAReadyInstalledOfflineVoiceContinuesAFailedNetworkReply() {
        assertTrue(LocalVoiceAvailability.mayContinueFailedNetworkReply(State.READY, false))
        assertFalse("a network voice never takes over", LocalVoiceAvailability.mayContinueFailedNetworkReply(State.READY, true))
        assertFalse("an unknown voice is not assumed to be local",
            LocalVoiceAvailability.mayContinueFailedNetworkReply(State.READY, null))
        for (state in State.entries.filter { it != State.READY })
            assertFalse(state.name, LocalVoiceAvailability.mayContinueFailedNetworkReply(state, false))
    }
}
