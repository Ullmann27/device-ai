package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Code55 field finding F: standard German, consistent "du", no dialect invitation in the local model prompt. */
class OwnerLanguageStyleTest {
    @Test fun completedDialectAnswerIsRejectedByTheActualQualityGate() {
        val result = LocalDialogueQualityGate.evaluate("Wie funktioniert das?", "I hab ka Ahnung, des is ned klar.")
        assertFalse(result.success)
        assertTrue(result.notes.contains("Standarddeutsch"))
    }

    @Test fun correctedStandardGermanPasses() {
        assertTrue(LocalDialogueQualityGate.evaluate("Wie funktioniert das?",
            "Das weiß ich nicht sicher. Bitte zeige mir den betroffenen Abschnitt.").success)
    }

    @Test fun quotedDialectAndCodeRemainUnchanged() {
        assertNull(OwnerLanguageStyle.outputFeedback("Übersetze die Wendung.",
            "„I hab ka Ahnung“ bedeutet: Ich weiß es nicht. Der Bezeichner `ned` bleibt unverändert."))
    }

    @Test fun wordsContainingDialectFragmentsAreNotRejected() {
        assertNull(OwnerLanguageStyle.outputFeedback("Erkläre den Code.",
            "Der Parameter named und die Definition einer Methode bleiben erhalten."))
    }

    @Test fun explicitDialectRequestIsRespected() {
        assertNull(OwnerLanguageStyle.outputFeedback("Antworte bitte im Dialekt.", "I hab ka Ahnung."))
    }

    @Test fun properNamesRemainValid() {
        assertNull(OwnerLanguageStyle.outputFeedback("Wer ist Ned Stark?", "Ned Stark ist eine Romanfigur."))
    }

    @Test fun theInstructionDemandsStandardGermanAndDuAndForbidsDialect() {
        val s = OwnerLanguageStyle.INSTRUCTION
        assertTrue(s.contains("Standarddeutsch"))
        assertTrue(s.contains("keinen Dialekt"))
        assertTrue(s.contains("„du“"))
        assertTrue(s.contains("Formatvorgaben"))
        assertFalse(s.contains("österreichisch"))
    }

    @Test fun theLocalModelPromptUsesTheSharedInstructionAndNoLongerInvitesDialect() {
        val activity = File("src/main/java/at/heinz/deviceai/AgentActivity.kt").readText()
        assertTrue(activity.contains("OwnerLanguageStyle.INSTRUCTION"))
        assertFalse(activity.contains("österreichischen Sprachgefühl"))
    }
}
