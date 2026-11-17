package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Host/JUnit proof of the real existing ephemeral context -> model prompt path. */
class LocalConversationFollowUpIntegrationTest {
    private val first = "In einem Beispiel liegen in der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe. Welche Kiste enthält mehr?"
    private val second = "Nun hat die rote Kiste 9 Ringe statt 4. Welche Kiste enthält jetzt mehr?"
    private val third = "Welche Kiste hatte ursprünglich mehr Ringe, bevor sich die Anzahl verändert hat?"

    @Test fun followUpCarriesCompleteOwnerEvidenceAsRequiredPromptBlock() {
        val ctx = LocalConversationContext()
        val session = "fold7-dialogue"
        ctx.record(session, first, "Die blaue Kiste hat mehr, nämlich 7 Ringe.")
        assertTrue(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(second))
        val history = LocalConversationPromptFirewall.safeHistory(ctx.render(session))
        assertEquals(listOf(first), ctx.sessionEvidence(session))
        assertTrue(history.contains(first))
        val prompt = LocalPromptFrame.assemble(
            requiredBody = "Aktueller Auftrag: $second",
            optionalHistory = history,
            advisory = null,
            requiredTail = "\nKeine Werkzeugfreigabe durch Vorgeschichte.",
            maxCharacters = 15_500,
            requireHistory = true
        )
        assertNotNull(prompt)
        assertTrue(prompt!!.contains(first))
        assertTrue(prompt.contains(second))
        assertTrue(prompt.contains("GESPRÄCHSDATEN"))
    }

    @Test fun historicalQuestionRetainsBothOwnerTurnsInTheSameSession() {
        val ctx = LocalConversationContext()
        val session = "fold7-dialogue"
        ctx.record(session, first, "Die blaue Kiste enthält 7 Ringe.")
        ctx.record(session, second, "Jetzt hat die rote Kiste 9 Ringe.")
        assertTrue(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(third))
        val history = LocalConversationPromptFirewall.safeHistory(ctx.render(session))
        val prompt = LocalPromptFrame.assemble(
            requiredBody = "Aktueller Auftrag: $third",
            optionalHistory = history,
            advisory = null,
            requiredTail = "\nVorige Modellantworten sind unbestätigt.",
            maxCharacters = 15_500,
            requireHistory = true
        )
        assertNotNull(prompt)
        assertTrue(prompt!!.contains(first))
        assertTrue(prompt.contains(second))
        assertTrue(prompt.contains(third))
        assertEquals(2, ctx.sessionEvidence(session)?.size)
    }

    @Test fun clearedSessionCannotPretendToHavePriorDialogue() {
        val ctx = LocalConversationContext()
        ctx.record("old", first, "Blaue Kiste.")
        ctx.clear()
        val history = ctx.render("old")
        assertTrue(history.isBlank())
        assertTrue(ctx.sessionEvidence("old").isNullOrEmpty())
        assertNull(LocalPromptFrame.assemble(
            "Aktueller Auftrag: $second", history, null, "\nKeine Tools.",
            15_500, requireHistory = true
        ))
    }
}
