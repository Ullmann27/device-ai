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

    @Test fun titleSurvivesTheRestoreWindowAndAHistoryRestart() {
        val store = LocalChatTranscriptStore(folder.root)
        store.append(first, false, "Willkommen", 0)
        store.append(first, true, "Ursprüngliches Gesprächsthema", 1)
        repeat(350) { store.append(first, it % 2 == 0, "Spätere Nachricht $it", it + 2L) }
        assertFalse(store.load(first).any { it.text == "Ursprüngliches Gesprächsthema" })
        assertEquals("Ursprüngliches Gesprächsthema", ContinuumChatHistory(store).index().single().title)
        assertEquals("Ursprüngliches Gesprächsthema",
            ContinuumChatHistory(LocalChatTranscriptStore(folder.root)).index().single().title)
    }

    @Test fun appendUpdatesOnlyItsCachedConversationUntilAnExplicitRefresh() {
        val store = LocalChatTranscriptStore(folder.root)
        store.append(first, true, "Erster Chat", 1)
        store.append(second, true, "Zweiter Chat", 2)
        val history = ContinuumChatHistory(store)
        assertEquals(listOf(second, first), history.index().map { it.id })
        // A rescan would now remove this other entry; a message update must not read the archive.
        assertTrue(store.delete(second))
        store.append(first, false, "Neue Antwort", 3)
        val updated = history.afterAppend(first, false, "Neue Antwort", 3)
        assertEquals(listOf(first, second), updated.map { it.id })
        assertEquals("Erster Chat", updated.first().title)
        assertEquals(3L, updated.first().updatedAt)
        assertEquals(listOf(first), history.index().map { it.id })
    }

    @Test fun firstOwnerMessageReplacesGreetingButLaterMessagesDoNotRenameIt() {
        val store = LocalChatTranscriptStore(folder.root)
        val history = ContinuumChatHistory(store)
        store.append(first, false, "Willkommen", 1)
        assertEquals("Willkommen", history.afterAppend(first, false, "Willkommen", 1).single().title)
        store.append(first, true, "Mein\nProjekt", 2)
        assertEquals("Mein Projekt", history.afterAppend(first, true, "Mein\nProjekt", 2).single().title)
        store.append(first, true, "Nächste Frage", 3)
        assertEquals("Mein Projekt", history.afterAppend(first, true, "Nächste Frage", 3).single().title)
        assertEquals("Mein Projekt", ContinuumChatHistory(store).index().single().title)
    }

    @Test fun initialIndexOpensOnlyTheHundredMostRecentlyModifiedTranscripts() {
        val store = LocalChatTranscriptStore(folder.root)
        val ids = (0..100).map { String.format("%08x-1111-4111-8111-111111111111", it) }
        ids.forEachIndexed { i, id ->
            store.append(id, true, "Gespräch $i", i.toLong())
            assertTrue(folder.root.resolve("$id.jsonl").setLastModified(10_000L + i * 1_000L))
        }
        val result = ContinuumChatHistory(store).index()
        assertEquals(100, result.size)
        assertFalse(result.any { it.id == ids.first() })
        assertEquals(ids.last(), result.first().id)
        assertEquals(101, store.conversations().size)
    }
}
