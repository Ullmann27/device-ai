package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

/** #568: the central chat survives an app exit and process death; a new chat keeps older ones. */
class LocalChatTranscriptStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun id() = UUID.randomUUID().toString()

    @Test fun conversationAndActiveIdSurviveANewProcess() {
        val dir = tmp.newFolder("chat-history")
        val first = LocalChatTranscriptStore(dir)
        val conversation = first.activeConversation { id() }
        first.append(conversation, true, "Merke dir: Mein Testwort lautet Nebel 731.", 1)
        first.append(conversation, false, "Gespeichert.", 2)
        val afterRestart = LocalChatTranscriptStore(dir) // a fresh instance = new process
        assertEquals(conversation, afterRestart.activeConversation { error("must reuse the stored id") })
        assertEquals(listOf(true to "Merke dir: Mein Testwort lautet Nebel 731.", false to "Gespeichert."),
            afterRestart.load(conversation).map { it.user to it.text })
    }

    @Test fun newChatKeepsOlderConversationsUntilExplicitDelete() {
        val store = LocalChatTranscriptStore(tmp.newFolder("chat-history"))
        val old = store.activeConversation { id() }
        store.append(old, true, "Alter Chat", 1)
        val next = id(); store.setActive(next); store.append(next, true, "Neuer Chat", 2)
        assertEquals(next, store.activeConversation { error("active id kept") })
        assertEquals("Alter Chat", store.load(old).single().text)
        assertEquals(setOf(old, next), store.conversations().toSet())
        assertTrue(store.delete(old))
        assertTrue(store.load(old).isEmpty())
        assertEquals("deleting one chat leaves the other", "Neuer Chat", store.load(next).single().text)
    }

    @Test fun tornLinesAndInvalidIdsAreSafe() {
        val dir = tmp.newFolder("chat-history")
        val store = LocalChatTranscriptStore(dir)
        val c = store.activeConversation { id() }
        store.append(c, true, "eins", 1)
        dir.resolve("$c.jsonl").appendText("{\"u\":false,\"t\":\"abgebroch") // process died mid-write
        store.append(c, false, "zwei", 2)
        assertEquals(listOf("eins", "zwei"), store.load(c).map { it.text })
        assertTrue(store.load("../../etc/passwd").isEmpty())
        assertFalse(store.delete("../x"))
        assertEquals(listOf("zwei"), store.load(c, limit = 1).map { it.text })
    }
}
