package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class LocalChatTranscriptCompletenessTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun longOwnerCorrectionRoundTripsWithoutLosingItsNegation() {
        val dir = tmp.newFolder()
        val id = UUID.randomUUID().toString()
        val request = "Emma ist in Berg. " + "Zusatz. ".repeat(150) + "Emma ist nicht mehr in Berg."
        LocalChatTranscriptStore(dir).appendContextTurn(id, request, "Unbestätigte Antwort.", 1)

        val restored = LocalChatTranscriptStore(dir).loadContextTurns(id).single()
        assertEquals(request, restored.request)
        assertTrue(restored.request.endsWith("Emma ist nicht mehr in Berg."))
    }

    @Test fun completeMultilingualOwnerTextKeepsItsFinalCorrection() {
        val dir = tmp.newFolder()
        val id = UUID.randomUUID().toString()
        val request = "東京 🧠 ".repeat(220) + "Korrektur: nicht Wien, sondern Graz."
        assertTrue(request.toByteArray(Charsets.UTF_8).size < 7_900)
        LocalChatTranscriptStore(dir).appendContextTurn(id, request, "Nicht bestätigt.", 1)

        assertEquals(request, LocalChatTranscriptStore(dir).loadContextTurns(id).single().request)
    }
}
