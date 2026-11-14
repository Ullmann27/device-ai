package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalTextOnlyInferenceRouteTest {
    @Test fun ordinaryExplanationsAndLongFormSummariesDoNotNeedToolSchemas() {
        listOf(
            "Erkläre mir ausführlich den Unterschied zwischen einem Modellfenster und dem Langzeitgedächtnis.",
            "Bitte beschreibe mir in zehn Sätzen, wie Androids Speicherverwaltung funktioniert.",
            "Was sind die wichtigsten Gründe, warum ein Dialog bei vier Tausend Tokens abbricht?",
            "Fasse die bereitgestellte Dokumentpassage ausführlich in eigenen Worten zusammen."
        ).forEach { question ->
            assertTrue(question, LocalTextOnlyInferenceRoute.useToolFree(
                question, false, false, false, false))
        }
    }

    @Test fun deviceAndWebOperationsNeverLoseTheirExistingToolRoute() {
        listOf(
            "Öffne meinen Downloads-Ordner und klicke auf die PDF.",
            "Suche die installierte WhatsApp-App und sende eine Nachricht.",
            "Recherchiere im Internet die aktuellen Aktienkurse.",
            "Lösche unnötige Dateien auf dem Handy.",
            "Analysiere mein Handy mit Shizuku."
        ).forEach { instruction ->
            assertFalse(instruction, LocalTextOnlyInferenceRoute.useToolFree(
                instruction, false, false, false, false))
        }
    }

    @Test fun observedWorkBotAndCodingTurnsKeepToolAndConsentBoundaries() {
        val question = "Erkläre mir genau, was passiert ist."
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, true, false, false, false))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, false, true, false, false))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, false, false, true, false))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree(question, false, false, false, true))
        assertFalse(LocalTextOnlyInferenceRoute.useToolFree("", false, false, false, false))
    }
}
