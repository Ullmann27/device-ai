package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalContinuationPolicyRegressionTest {
    @Test fun continuationNeedsThePreviousAnswerContext() {
        for (text in listOf("Weiter", "Bitte fortsetzen.", "Schreib weiter!", "Fahre bitte fort.",
            "Setze deine Antwort fort.", "Kannst du bitte fortsetzen?")) {
            assertTrue(text, LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(text))
        }
    }

    @Test fun unrelatedUsesOfWeiterRemainStandalone() {
        for (text in listOf("Weiter oben im Text steht eine Adresse.", "Was bedeutet Weiterbildung?",
            "Erkläre mir die Fortsetzung einer Folge.")) {
            assertFalse(text, LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(text))
        }
    }
}
