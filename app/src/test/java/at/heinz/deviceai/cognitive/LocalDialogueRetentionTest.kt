package at.heinz.deviceai.cognitive

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fold7: unfolding/folding (density), theme, font scale or keyboard changes still
 * recreate AgentActivity (configChanges covers only size/orientation/layout).
 * The ephemeral local dialogue must survive such a recreation of the SAME chat
 * session, and must still be forgotten when the Activity really finishes.
 */
class LocalDialogueRetentionTest {
    private val session = "0f8b2c4e-1111-4222-8333-944455556666"

    @After fun reset() = LocalDialogueRetention.activityDestroyed(changingConfigurations = false)

    @Test fun ownerTurnsSurviveActivityRecreationOfTheSameSession() {
        val before = LocalDialogueRetention.context          // Activity instance A
        before.record(session, "Ich habe eine blaue Kiste mit 7 Ringen.", "Verstanden.")
        LocalDialogueRetention.activityDestroyed(changingConfigurations = true)  // Fold7 density change
        val after = LocalDialogueRetention.context           // recreated Activity instance B
        assertEquals(listOf("Ich habe eine blaue Kiste mit 7 Ringen."), after.sessionEvidence(session))
        assertTrue(after.render(session).contains("blaue Kiste mit 7 Ringen"))
    }

    @Test fun lastLocalReplySurvivesRecreationOnlyForTheSameSession() {
        LocalDialogueRetention.retainReply(
            LocalDialogueRetention.RetainedReply(session, "Wie viele Ringe?", "7 Ringe.", textOnly = true))
        LocalDialogueRetention.activityDestroyed(changingConfigurations = true)
        assertEquals("7 Ringe.", LocalDialogueRetention.replyFor(session)?.response)
        assertTrue(LocalDialogueRetention.replyFor(session)?.textOnly == true)
        assertNull(LocalDialogueRetention.replyFor("ffffffff-1111-4222-8333-944455556666"))
    }

    @Test fun continuationKeepsToolFreeRouteAfterRecreation() {
        LocalDialogueRetention.context.record(session, "Erkläre den Ablauf ausführlich.", "Teil eins …",
            responseComplete = false)
        LocalDialogueRetention.retainReply(LocalDialogueRetention.RetainedReply(
            session, "Erkläre den Ablauf ausführlich.", "Teil eins …", textOnly = true))
        LocalDialogueRetention.activityDestroyed(changingConfigurations = true)
        assertTrue(LocalTextOnlyInferenceRoute.useToolFree("Weiter", false, false, false, false,
            priorTextOnlyResponse = LocalDialogueRetention.replyFor(session)?.textOnly == true &&
                LocalDialogueRetention.context.hasContinuation(session)))
    }

    @Test fun unknownRouteCannotBecomeATextOnlyContinuation() {
        LocalDialogueRetention.context.record(session, "Prüfe das Gerät.", "Teil eins …",
            responseComplete = false)
        LocalDialogueRetention.retainReply(LocalDialogueRetention.RetainedReply(
            session, "Prüfe das Gerät.", "Teil eins …"))
        LocalDialogueRetention.activityDestroyed(changingConfigurations = true)
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree("Weiter", false, false, false, false,
            priorTextOnlyResponse = LocalDialogueRetention.replyFor(session)?.textOnly == true &&
                LocalDialogueRetention.context.hasContinuation(session)))
    }

    @Test fun realFinishForgetsTheEphemeralDialogue() {
        LocalDialogueRetention.context.record(session, "Geheime Zwischennotiz", "Ok.")
        LocalDialogueRetention.retainReply(LocalDialogueRetention.RetainedReply(session, "a", "b", textOnly = true))
        LocalDialogueRetention.activityDestroyed(changingConfigurations = false)
        assertEquals(emptyList<String>(), LocalDialogueRetention.context.sessionEvidence(session))
        assertNull(LocalDialogueRetention.replyFor(session))
    }

    @Test fun newSessionStillStartsEmpty() {
        LocalDialogueRetention.context.record(session, "Alte Sitzung", "Ok.")
        LocalDialogueRetention.activityDestroyed(changingConfigurations = true)
        val other = "aaaaaaaa-1111-4222-8333-944455556666"
        assertEquals("", LocalDialogueRetention.context.render(other))
        LocalDialogueRetention.context.record(other, "Neu", "Ok.")
        assertEquals(emptyList<String>(), LocalDialogueRetention.context.sessionEvidence(session))
    }
}
