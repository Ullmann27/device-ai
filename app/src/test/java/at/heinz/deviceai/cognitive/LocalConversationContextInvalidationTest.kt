package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
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
}
