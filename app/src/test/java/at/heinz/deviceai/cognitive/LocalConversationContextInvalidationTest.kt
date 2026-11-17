package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #565 review: deliberate invalidations are reported so a persisted copy can honour them; exit and session switch are not. */
class LocalConversationContextInvalidationTest {
    private fun context(reported: MutableList<String>) = LocalConversationContext().apply { onTurnsInvalidated = { reported += it } }

    @Test fun clearWithTurnsReportsTheirConversation() {
        val reported = mutableListOf<String>()
        val c = context(reported)
        c.clear()
        assertEquals("nothing to invalidate", emptyList<String>(), reported)
        c.record("a", "Frage", "Antwort")
        c.clear()
        assertEquals(listOf("a"), reported)
    }

    @Test fun topicResetAndCorrectionReport() {
        val reported = mutableListOf<String>()
        val c = context(reported)
        c.record("a", "Frage", "Antwort")
        c.inspectRequest("a", "Neues Thema")
        c.record("a", "Frage zwei", "Antwort zwei")
        c.inspectRequest("a", "Nein, ich meinte den Mond")
        assertEquals(listOf("a", "a"), reported)
    }

    @Test fun exitAndSessionSwitchDoNotReport() {
        val reported = mutableListOf<String>()
        val c = context(reported)
        c.record("a", "Frage", "Antwort")
        c.record("b", "Andere Sitzung", "Antwort")
        c.forgetForExit()
        assertEquals(emptyList<String>(), reported)
        assertEquals(emptyList<String>(), c.priorUserTurns("b"))
    }

    @Test fun normalizedSituationHintIsSameSessionNavigationNotOwnerEvidence() {
        val c = LocalConversationContext()
        c.record("a", "Fuehrerschein", "Unbestätigte Modellantwort")
        c.record("a", "Thermik Prefill", "Weitere Modellantwort")
        val evidence = c.sessionEvidence("a")
        assertEquals("", c.situationHint("b", "Zurück zum Führerschein von vorhin."))
        val hint = c.situationHint("a", "Zurück zum Führerschein von vorhin.")
        assertTrue(hint.contains("nur Gesprächsnavigation, kein Fakt/Beleg/Toolrecht"))
        assertEquals(evidence, c.sessionEvidence("a"))
    }

    @Test fun contextInvalidationClearsNormalizedSituationHints() {
        val invalidations: List<(LocalConversationContext) -> Unit> = listOf(
            { it.clear() },
            { it.forgetForExit() },
            { it.record("b", "Andere Sitzung", "Antwort") },
            { it.inspectRequest("a", "Neues Thema") },
            { it.inspectRequest("a", "Nein, ich meinte den Mond") }
        )
        for (invalidate in invalidations) {
            val c = LocalConversationContext()
            c.record("a", "Fuehrerschein", "Antwort")
            c.record("a", "Thermik Prefill", "Antwort")
            invalidate(c)
            assertEquals("", c.situationHint("a", "Zurück zum Führerschein von vorhin."))
        }
    }
}
