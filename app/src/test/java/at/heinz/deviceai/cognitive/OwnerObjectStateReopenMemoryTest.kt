package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

/**
 * Code55 field bug E: after leaving and reopening the app the conversation context is rebuilt from the existing
 * transcript file. The owner object-state memory must see every turn since the last reset – not only the four-turn
 * model window – without a new file format, and a partial history must never look complete.
 */
class OwnerObjectStateReopenMemoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val statement = "Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf Schrauben."
    private val recall = "Welche Farbe hat meine Werkzeugkiste, wo steht sie und wie viele Schrauben sind darin?"
    private val others = listOf("Erzähl mir einen kurzen Witz.", "Was ist die Hauptstadt von Frankreich?",
        "Erkläre mir kurz die Photosynthese.", "Nenne mir ein Synonym für schnell.")

    private fun reopen(store: LocalChatTranscriptStore, id: String) = LocalConversationContext().apply {
        store.loadContextHistory(id).forEach { restore(id, it) }
    }

    @Test fun historyReplayRestoresTheObjectStateBeyondTheModelWindow() {
        val store = LocalChatTranscriptStore(tmp.newFolder("chat-history"))
        val id = store.activeConversation { UUID.randomUUID().toString() }
        store.appendContextTurn(id, statement, "Alles klar.", 1)
        store.appendContextTurn(id, "Nicht zwölf, sondern siebzehn Schrauben.", "Verstanden.", 2)
        others.forEachIndexed { i, text -> store.appendContextTurn(id, text, "Antwort.", 3L + i) }
        assertEquals("the model window stays bounded", 4, store.loadContextTurns(id).size)
        assertEquals(6, store.loadContextHistory(id).size)
        val context = reopen(store, id)
        assertEquals(others, context.priorUserTurns(id))
        val answer = context.objectStateAnswer(id, recall)
        assertNotNull(answer)
        assertTrue(answer, answer!!.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 17"))
        assertTrue("the bounded four-turn restore of the same file loses it",
            LocalConversationContext().apply { store.loadContextTurns(id).forEach { restore(id, it) } }
                .objectStateAnswer(id, recall) == null)
    }

    @Test fun aResetMarkerStillEndsTheRestoredHistory() {
        val store = LocalChatTranscriptStore(tmp.newFolder("chat-history"))
        val id = store.activeConversation { UUID.randomUUID().toString() }
        store.append(id, true, statement, 1)
        store.appendContextTurn(id, statement, "Alles klar.", 1)
        store.appendContextReset(id, 2)
        store.appendContextTurn(id, others.first(), "Antwort.", 3)
        assertEquals(listOf(others.first()), store.loadContextHistory(id).map { it.request })
        assertNull(reopen(store, id).objectStateAnswer(id, recall))
    }

    @Test fun aHistoryLongerThanTheReplayBoundIsMarkedIncomplete() {
        val store = LocalChatTranscriptStore(tmp.newFolder("chat-history"))
        val id = store.activeConversation { UUID.randomUUID().toString() }
        store.appendContextTurn(id, statement, "Alles klar.", 1)
        repeat(5) { store.appendContextTurn(id, "Frage $it zu einem anderen Thema.", "Antwort.", 2L + it) }
        val bounded = store.loadContextHistory(id, maxTurns = 4)
        assertEquals(4, bounded.size)
        assertFalse("a cut history starts with the incomplete placeholder", bounded.first().requestComplete)
        val context = LocalConversationContext().apply { bounded.forEach { restore(id, it) } }
        assertNull("no deterministic answer from a partial history", context.objectStateAnswer(id, recall))
        assertEquals("the model window keeps its existing four-turn view", 4, store.loadContextTurns(id).size)
        assertTrue(store.loadContextTurns(id).all { it.requestComplete })
    }

    @Test fun legacyAndCorruptLinesKeepTheirExistingMeaning() {
        val dir = tmp.newFolder("chat-history")
        val store = LocalChatTranscriptStore(dir)
        val id = store.activeConversation { UUID.randomUUID().toString() }
        // Pre-version context line written by an older app version stays readable.
        dir.resolve("$id.jsonl").appendText("{\"k\":\"ctx\",\"q\":\"$statement\",\"r\":\"Alles klar.\",\"at\":1}\n")
        assertTrue(reopen(store, id).objectStateAnswer(id, recall)!!.contains("Anzahl Schrauben: 12"))
        dir.resolve("$id.jsonl").appendText("{kaputt\n")
        store.appendContextTurn(id, others.first(), "Antwort.", 3)
        val history = store.loadContextHistory(id)
        assertFalse("an unreadable line still invalidates what came before it", history.first().requestComplete)
        assertNull(reopen(store, id).objectStateAnswer(id, recall))
    }

    @Test fun holdsSessionIsIndependentOfThePromptBudget() {
        val context = LocalConversationContext()
        val id = UUID.randomUUID().toString()
        assertFalse(context.holdsSession(id))
        context.record(id, statement, "Alles klar.")
        assertTrue(context.holdsSession(id))
        assertFalse(context.holdsSession(UUID.randomUUID().toString()))
        context.forgetForExit()
        assertFalse(context.holdsSession(id))
    }

    @Test fun startupMessageGreetsOnlyAGenuinelyNewConversation() {
        val notice = ConversationStartupNotice
        val kinds = ConversationStartupNotice.ContextRestore.values()
        assertEquals(notice.GREETING, notice.message(false, 0, ConversationStartupNotice.ContextRestore.NOTHING_STORED))
        for (kind in kinds.filter { it != ConversationStartupNotice.ContextRestore.FAILED })
            assertNull("restored conversation, $kind", notice.message(false, 3, kind))
        assertNull(notice.message(false, 0, ConversationStartupNotice.ContextRestore.RESTORED))
        assertNull(notice.message(false, 0, ConversationStartupNotice.ContextRestore.KEPT_IN_MEMORY))
        assertEquals(notice.CONTEXT_NOT_LOADED, notice.message(false, 3, ConversationStartupNotice.ContextRestore.FAILED))
        assertEquals(notice.CONVERSATION_NOT_LOADED, notice.message(false, 0, ConversationStartupNotice.ContextRestore.FAILED))
        for (kind in kinds) assertEquals(notice.CONVERSATION_NOT_LOADED, notice.message(true, 0, kind))
        assertEquals(notice.CONVERSATION_NOT_LOADED, notice.message(false, null, ConversationStartupNotice.ContextRestore.FAILED))
        assertEquals(notice.CONVERSATION_NOT_LOADED,
            notice.message(false, null, ConversationStartupNotice.ContextRestore.NOTHING_STORED))
        assertEquals(notice.HISTORY_NOT_SHOWN, notice.message(false, null, ConversationStartupNotice.ContextRestore.RESTORED))
        assertEquals(notice.HISTORY_NOT_SHOWN,
            notice.message(false, null, ConversationStartupNotice.ContextRestore.KEPT_IN_MEMORY))
        for (text in listOf(notice.CONTEXT_NOT_LOADED, notice.CONVERSATION_NOT_LOADED, notice.HISTORY_NOT_SHOWN)) {
            assertTrue(text, text.startsWith("Hinweis:"))
            assertTrue("never claims data loss: $text", text.contains("nicht gelöscht"))
        }
    }
}
