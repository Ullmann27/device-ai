package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalPromptFrameTest {
    @Test fun ownerCorrectionAndRequiredTailSurviveTheFullBoundary() {
        val correction="WICHTIG: Das ist NICHT Graz, sondern Bad Ischl."
        val task="Bitte vergleiche die Orte. "+("Zeitlicher Kontext. ".repeat(240))+correction
        val body="Identität und Befugnis: READ_ONLY.\n\n## USER REQUEST\n"+task
        val tail="\nToolGate ist immer erforderlich."
        val prompt=LocalPromptFrame.assemble(body,"\nUnbestätigte Vorgeschichte",
            "Modell rät einen Ort.",tail,15_500)
        assertNotNull(prompt)
        assertEquals(1,Regex("## USER REQUEST").findAll(prompt!!).count())
        assertTrue(prompt.contains(correction))
        assertTrue(prompt.contains(task))
        assertTrue(prompt.endsWith(tail))
    }

    @Test fun optionalHistoryIsNotClippedOrPromotedIntoMandatoryEvidence() {
        val body="## USER REQUEST\nAntwort mit belegter Verneinung.\nNICHT WIEN."
        val tail="\nEigene Berechtigungen bleiben unverändert."
        val history="\n## HISTORY\n"+("vorheriger Kontext; ".repeat(400))
        val cap=body.length+tail.length+70
        val prompt=LocalPromptFrame.assemble(body,history,null,tail,cap)
        assertNotNull(prompt)
        assertFalse(prompt!!.contains("HISTORY"))
        assertTrue(prompt.contains("NICHT WIEN"))
        assertTrue(prompt.endsWith(tail))
    }

    @Test fun impossibleMandatoryInputNeverGivesAClippedPrefixToTheModel() {
        val negative="; widerrufe den vorherigen Ort, nicht mehr Salzburg."
        val body="## USER REQUEST\n"+("beliebiger Kontext ".repeat(600))+negative
        val tail="\nKeine Aktionen ohne Freigabe."
        assertNull(LocalPromptFrame.assemble(body,"",null,tail,6_000))
        assertNull(LocalPromptFrame.assemble(" ", "", null,tail,6_000))
    }

    @Test fun unicodeAndAdvisoryCannotRemoveRequiredEvidence() {
        val sentence="Korrektur: 東京ではありません 🧠🚫; sondern Wien."
        val body="## USER REQUEST\n"+sentence.repeat(70)
        val tail="\nPrompt-Ende"
        val prompt=LocalPromptFrame.assemble(body,"", "vielleicht Paris?",tail,15_500)
        assertNotNull(prompt)
        assertTrue(prompt!!.contains(sentence.repeat(70)))
        assertTrue(prompt.endsWith(tail))
    }

    @Test fun utf8ByteBudgetRejectsCompleteEmojiRequestInsteadOfCuttingIt() {
        val mandatory = "## USER REQUEST\n" + "🧠".repeat(4_200)
        assertTrue(mandatory.length < 15_500)
        assertTrue(mandatory.toByteArray(Charsets.UTF_8).size > 15_500)
        assertNull(LocalPromptFrame.assemble(mandatory, "", null,
            "\nBerechtigungen bleiben unverändert.", 15_500))
    }

    @Test fun unicodeHistoryAndAdvisoryMayBeOmittedWithoutLosingOwnerCorrection() {
        val owner = "## USER REQUEST\n" + "東京".repeat(1_500) + "Nicht mehr Graz."
        val tail = "\nToolGate und Besitzerfreigabe bleiben verbindlich."
        val history = "frühere Aussage: Graz. ".repeat(400)
        val advisory = "🧠".repeat(1_700)
        val response = LocalPromptFrame.assemble(owner, history, advisory, tail, 15_500)
        assertNotNull(response)
        assertTrue(response!!.contains("Nicht mehr Graz."))
        assertTrue(response.endsWith(tail))
        assertTrue(response.toByteArray(Charsets.UTF_8).size <= 15_500)
    }

    @Test fun modelAdvisoryCannotFitIfItWouldStealOwnerBudget() {
        val body="Eindeutiger, vollständiger Auftrag. "+"x".repeat(8_600)
        val tail="\nDie letzte Kontrolle darf nie fehlen."
        val max=body.length+tail.length
        val prompt=LocalPromptFrame.assemble(body,"","UNVERIFIED".repeat(300),tail,max)
        assertEquals(body+tail,prompt)
    }
}
