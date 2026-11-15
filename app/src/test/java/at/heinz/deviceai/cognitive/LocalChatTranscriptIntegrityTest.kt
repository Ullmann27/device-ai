package at.heinz.deviceai.cognitive

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class LocalChatTranscriptIntegrityTest {
    @get:Rule val tmp = TemporaryFolder()
    private val id = UUID.randomUUID().toString()

    @Test fun legacyClippingBoundaryNeverBecomesCompleteOwnerEvidence() {
        val dir = tmp.newFolder()
        val prefix = "Emma ist in Berg. ".padEnd(1_000, 'x')
        dir.resolve("$id.jsonl").writeText(JSONObject().put("k", "ctx")
            .put("q", prefix).put("r", "Altantwort.").toString() + "\n")
        val turn = LocalChatTranscriptStore(dir).loadContextTurns(id).single()
        assertFalse(turn.requestComplete)
        assertEquals("", turn.request)
    }

    @Test fun legacyShortTurnSurvivesButClippedAnswerHasNoKnownEnd() {
        val dir = tmp.newFolder()
        dir.resolve("$id.jsonl").writeText(JSONObject().put("k", "ctx")
            .put("q", "Vollständige Frage.").put("r", "a".repeat(2_000)).toString() + "\n")
        val turn = LocalChatTranscriptStore(dir).loadContextTurns(id).single()
        assertTrue(turn.requestComplete)
        assertFalse(turn.responseComplete)
        assertFalse(turn.responseHasEnd)
    }

    @Test fun oversizedUnicodeOwnerTurnPersistsAnIncompleteMarker() {
        val store = LocalChatTranscriptStore(tmp.newFolder())
        store.appendContextTurn(id, "Emma ist in Berg.", "Altantwort.", 1)
        val oversized = "東京".repeat(LocalDialogueContextLimits.MAX_OWNER_CHARACTERS / 6 + 1) + "Nicht mehr Berg."
        assertTrue(oversized.length <= LocalDialogueContextLimits.MAX_OWNER_CHARACTERS)
        assertFalse(LocalDialogueContextLimits.completeOwner(oversized))
        store.appendContextTurn(id, oversized, "Antwort.", 2)
        val turns = store.loadContextTurns(id)
        assertEquals(2, turns.size)
        assertFalse(turns.last().requestComplete)
        assertEquals("", turns.last().request)
    }

    @Test fun unknownOrDamagedContextCannotReviveAnEarlierAssertion() {
        for (damaged in listOf("{broken", "{\"k\":\"ctx\",\"q\":\"Korrektur\"}",
            "{\"k\":\"ctx\",\"v\":99,\"q\":\"Korrektur\",\"r\":\"Antwort\"}",
            "{\"k\":\"future-context\"}")) {
            val dir = tmp.newFolder()
            val store = LocalChatTranscriptStore(dir)
            store.appendContextTurn(id, "Emma ist in Berg.", "Altantwort.", 1)
            dir.resolve("$id.jsonl").appendText(damaged + "\n")
            store.appendContextTurn(id, "Wo ist Emma?", "Ungeklärt.", 3)
            val turns = LocalChatTranscriptStore(dir).loadContextTurns(id)
            assertTrue(damaged, turns.any { !it.requestComplete })
            assertFalse(damaged, turns.any { "Emma ist in Berg" in it.request })
        }
    }

    @Test fun partialModelTextRemainsPartialAfterRestart() {
        val dir = tmp.newFolder()
        LocalChatTranscriptStore(dir).appendContextTurn(id, "Erkläre die Schritte.",
            "Schritt eins. Schritt zwei beginnt", 1, responseComplete = false)
        val turn = LocalChatTranscriptStore(dir).loadContextTurns(id).single()
        assertTrue(turn.requestComplete)
        assertTrue(turn.modelResponse)
        assertFalse(turn.responseComplete)
        assertTrue(turn.responseHasEnd)
        assertTrue(turn.response.endsWith("Schritt zwei beginnt"))
    }

    @Test fun responseStorageKeepsTheActualEndAndMarksOmittedText() {
        val store = LocalChatTranscriptStore(tmp.newFolder())
        val response = "ALTER ANFANG " + "🧠".repeat(11_000) + " TATSÄCHLICHES ENDE"
        store.appendContextTurn(id, "Erkläre die Schritte.", response, 1)
        val turn = store.loadContextTurns(id).single()
        assertFalse(turn.responseComplete)
        assertTrue(turn.responseHasEnd)
        assertFalse(turn.response.startsWith("ALTER ANFANG"))
        assertFalse(turn.response.first().isLowSurrogate())
        assertTrue(turn.response.endsWith("TATSÄCHLICHES ENDE"))
        assertTrue(turn.response.length <= LocalDialogueContextLimits.MAX_RESPONSE_CHARACTERS)
    }

    @Test fun explicitResetClearsIncompleteHistoryBeforeNewTurns() {
        val store = LocalChatTranscriptStore(tmp.newFolder())
        store.appendContextTurn(id, "x".repeat(LocalDialogueContextLimits.MAX_OWNER_CHARACTERS + 1), "Antwort.", 1)
        assertFalse(store.loadContextTurns(id).single().requestComplete)
        store.appendContextReset(id, 2)
        store.appendContextTurn(id, "Neues Thema.", "Neue Antwort.", 3)
        assertEquals(listOf(LocalChatTranscriptStore.ContextTurn("Neues Thema.", "Neue Antwort.")),
            store.loadContextTurns(id))
    }
}
