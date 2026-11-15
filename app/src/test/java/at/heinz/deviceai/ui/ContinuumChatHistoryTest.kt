package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.cognitive.LocalConversationContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContinuumChatHistoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val first = "11111111-1111-4111-8111-111111111111"
    private val second = "22222222-2222-4222-8222-222222222222"

    @Test fun indexContainsOnlyStoredMessagesInActualRecencyOrder() {
        val store = LocalChatTranscriptStore(folder.root)
        store.setActive(first)
        val history = ContinuumChatHistory(store)
        assertTrue(history.index().isEmpty())
        store.append(first, true, "Meine Werkzeugkiste\nist blau.", 10)
        store.append(second, true, "Eine spätere Frage", 30)
        assertEquals(listOf(second, first), history.index().map { it.id })
        assertEquals("Meine Werkzeugkiste ist blau.", history.index().last().title)
        assertEquals(first, store.activeConversation { error("No new chat") })
    }

    @Test fun readRestoresExactlyOneConversationWithoutChangingSelectionOrAppending() {
        val store = LocalChatTranscriptStore(folder.root)
        store.setActive(first)
        store.append(first, true, "Die Kiste ist blau.", 1)
        store.appendContextTurn(first, "Die Kiste ist blau.", "Verstanden.", 2)
        store.append(second, true, "Der Ball ist rot.", 3)
        store.appendContextTurn(second, "Der Ball ist rot.", "Verstanden.", 4)
        val before = folder.root.listFiles()!!.associate { it.name to it.readBytes().toList() }
        val restored = ContinuumChatHistory(store).read(second)
        assertEquals(second, restored.id)
        assertEquals(listOf("Der Ball ist rot."), restored.entries.map { it.text })
        assertEquals(listOf("Der Ball ist rot."), restored.context.map { it.request })
        assertEquals(first, store.activeConversation { error("No new chat") })
        assertEquals(before, folder.root.listFiles()!!.associate { it.name to it.readBytes().toList() })
    }

    @Test fun missingEmptyAndTraversalSelectionsLeaveTheExistingConversationIntact() {
        val store = LocalChatTranscriptStore(folder.root)
        store.setActive(first)
        store.append(first, true, "Bleibt offen", 1)
        val history = ContinuumChatHistory(store)
        for (id in listOf(second, "../active", "", "../../chat-history")) {
            assertTrue("Reject $id", runCatching { history.read(id) }.isFailure)
            assertEquals(first, store.activeConversation { error("No new chat") })
        }
        store.appendContextTurn(second, "Kontext ohne sichtbaren Chat", "Antwort", 2)
        assertTrue(history.index().none { it.id == second })
        assertTrue(runCatching { history.read(second) }.isFailure)
    }

    @Test fun navigationDoesNotPersistAResetOrLeakTheOtherChatsContext() {
        val store = LocalChatTranscriptStore(folder.root)
        store.append(first, true, "Meine Kiste ist blau.", 1)
        store.appendContextTurn(first, "Meine Kiste ist blau.", "Verstanden.", 2)
        store.append(second, true, "Mein Ball ist rot.", 3)
        store.appendContextTurn(second, "Mein Ball ist rot.", "Verstanden.", 4)
        val history = ContinuumChatHistory(store)
        val context = LocalConversationContext().apply {
            onTurnsInvalidated = { store.appendContextReset(it, 5) }
        }
        for (id in listOf(first, second, first)) {
            val data = history.read(id)
            context.forgetForExit()
            data.context.forEach { context.restore(id, it) }
            assertEquals(data.context.map { it.request }, context.priorUserTurns(id))
            assertTrue(context.priorUserTurns(if (id == first) second else first).isEmpty())
        }
        assertEquals(1, store.loadContextHistory(first).size)
        assertEquals(1, store.loadContextHistory(second).size)
    }
}
