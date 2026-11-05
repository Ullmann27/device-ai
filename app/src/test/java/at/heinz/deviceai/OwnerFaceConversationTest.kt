package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class OwnerFaceConversationTest {
    private fun state(phase: OwnerFacePhase, active: Boolean = true) = OwnerFaceStatus(
        phase, hasProfile = true, displayName = "Testperson", sessionActive = active, message = "Status")

    @Test fun continuationNeedsAnExplicitCurrentComparison() {
        val conversation = OwnerFaceConversation()
        assertFalse(conversation.canContinue(4, state(OwnerFacePhase.RECOGNIZING)))
        conversation.beginComparison(4)
        assertTrue(conversation.canContinue(4, state(OwnerFacePhase.RECOGNIZING)))
        assertFalse(conversation.canContinue(5, state(OwnerFacePhase.MATCH)))
        assertFalse(conversation.canContinue(4, state(OwnerFacePhase.ENROLLING)))
        assertFalse(conversation.canContinue(4, state(OwnerFacePhase.LOADING)))
        assertFalse(conversation.canContinue(4, state(OwnerFacePhase.MATCH, false)))
        assertFalse(conversation.canContinue(4, state(OwnerFacePhase.MATCH).copy(hasProfile = false)))
    }

    @Test fun confirmedMatchGreetsOncePerSession() {
        val conversation = OwnerFaceConversation()
        conversation.beginComparison(7)
        assertNull(conversation.greeting(7, state(OwnerFacePhase.RECOGNIZING), true))
        assertEquals("Hallo, Testperson. Schön, dich zu sehen.",
            conversation.greeting(7, state(OwnerFacePhase.MATCH), true))
        assertNull(conversation.greeting(7, state(OwnerFacePhase.MATCH), true))
        assertNull(conversation.greeting(7, state(OwnerFacePhase.NO_FACE), true))
        assertNull(conversation.greeting(7, state(OwnerFacePhase.MATCH), true))
        conversation.stop()
        conversation.beginComparison(9)
        assertNotNull(conversation.greeting(9, state(OwnerFacePhase.MATCH), true))
    }

    @Test fun occupiedVoiceSkipsGreetingWithoutReplayingItLater() {
        val conversation = OwnerFaceConversation()
        conversation.beginComparison(10)
        assertNull(conversation.greeting(10, state(OwnerFacePhase.MATCH), false))
        assertNull(conversation.greeting(10, state(OwnerFacePhase.MATCH), true))
    }

    @Test fun stoppedExpiredOrOldSessionsCannotGreet() {
        val conversation = OwnerFaceConversation()
        conversation.beginComparison(11)
        assertNull(conversation.greeting(10, state(OwnerFacePhase.MATCH), true))
        assertNull(conversation.greeting(11, state(OwnerFacePhase.MATCH, false), true))
        conversation.stop()
        assertNull(conversation.greeting(11, state(OwnerFacePhase.MATCH), true))
        conversation.beginComparison(12)
        assertNull(conversation.greeting(11, state(OwnerFacePhase.MATCH), true))
        assertNotNull(conversation.greeting(12, state(OwnerFacePhase.MATCH), true))
    }

    @Test fun noProfileCannotGreetEvenIfAnInvalidMatchArrives() {
        val conversation = OwnerFaceConversation()
        conversation.beginComparison(3)
        assertNull(conversation.greeting(3, state(OwnerFacePhase.MATCH).copy(hasProfile = false), true))
        assertNull(conversation.greeting(3, state(OwnerFacePhase.MATCH).copy(displayName = null), true))
    }

    @Test fun badgeStopsClaimingAMatchWhenObservationExpires() {
        val conversation = OwnerFaceConversation()
        conversation.beginComparison(2)
        assertEquals("Vorlage passt: Testperson", conversation.statusLine(2, state(OwnerFacePhase.MATCH)))
        assertEquals("Kein Gesicht im Bild", conversation.statusLine(2, state(OwnerFacePhase.NO_FACE)))
        assertEquals("Bild noch nicht eindeutig", conversation.statusLine(2, state(OwnerFacePhase.LOW_QUALITY)))
        assertNull(conversation.statusLine(2, state(OwnerFacePhase.OFF, false)))
        conversation.stop()
        assertNull(conversation.statusLine(2, state(OwnerFacePhase.MATCH)))
    }
}
