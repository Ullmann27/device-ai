package at.heinz.deviceai.cognitive

import org.junit.After
import org.junit.Assert.assertEquals
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
            LocalDialogueRetention.RetainedReply(session, "Wie viele Ringe?", "7 Ringe."))
        LocalDialogueRetention.activityDestroyed(changingConfigurations = true)
        assertEquals("7 Ringe.", LocalDialogueRetention.replyFor(session)?.response)
        assertNull(LocalDialogueRetention.replyFor("ffffffff-1111-4222-8333-944455556666"))
    }

    @Test fun realFinishForgetsTheEphemeralDialogue() {
        LocalDialogueRetention.context.record(session, "Geheime Zwischennotiz", "Ok.")
        LocalDialogueRetention.retainReply(LocalDialogueRetention.RetainedReply(session, "a", "b"))
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
