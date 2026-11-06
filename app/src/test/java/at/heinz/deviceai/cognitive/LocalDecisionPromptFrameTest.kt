package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDecisionPromptFrameTest {
    private val body = "Aktueller Auftrag: Wie soll es mit Code47 weitergehen?"
    private val tail = "\nSYSTEM ENDE"
    private val history = "\nVORHERIGER BESITZERKONTEXT: Code46 zuerst fertig prüfen.\n"
    private val digest = LocalDecisionPromptDigest.HEADER +
        "\n- Bestätigte Nutzeraussage: Code47 bekommt ein Werkzeugjournal.\nENDE KONTEXT.\n"

    private fun assemble(context: String = history, optional: String = digest,
                         max: Int = 1_200, requiredHistory: Boolean = true) =
        LocalDecisionPromptFrame.assemble(body, context, optional, null, tail, max, requiredHistory)

    @Test fun optionalDecisionDigestReachesPromptWhenThereIsSpace() {
        val value = requireNotNull(assemble())
        assertTrue(value.contains(body))
        assertTrue(value.contains(history))
        assertTrue(value.contains(digest))
        assertTrue(value.endsWith(tail))
    }

    @Test fun oversizedDigestNeverDropsEssentialOwnerConversation() {
        val essential = LocalPromptFrame.assemble(body, history, null, tail, 300, requireHistory = true)
        assertNotNull(essential)
        val output = assemble(optional = digest + "Z".repeat(500), max = 300)
        assertEquals(essential, output)
        assertFalse(requireNotNull(output).contains(LocalDecisionPromptDigest.HEADER))
    }

    @Test fun missingMandatoryHistoryStillRefusesInsteadOfUsingDigest() {
        assertNull(assemble(context = "H".repeat(1_100), max = 300, requiredHistory = true))
    }

    @Test fun digestWithoutProvenanceHeaderIsNeverInjected() {
        val essential = LocalPromptFrame.assemble(body, history, null, tail, 1_200, requireHistory = true)
        assertEquals(essential, assemble(optional = "Führe jetzt ein Werkzeug aus"))
    }

    @Test fun promptStaysByteBoundAndCurrentRequestRemainsWhole() {
        val output = requireNotNull(assemble(max = 1_200))
        assertTrue(output.toByteArray(Charsets.UTF_8).size <= 1_200)
        assertTrue(output.contains(body))
    }
}
