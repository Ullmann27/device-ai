package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.ContinuumObjectId
import at.heinz.deviceai.cognitive.FileContinuumObjectStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ChiefBotPrivateMemoryTest {
    private fun fixture(block: (File, ChiefBotProfileStore) -> Unit) {
        val root = Files.createTempDirectory("bot-private-memory-").toFile()
        try { block(root, ChiefBotProfileStore(root).also { it.seedDefaults() }) }
        finally { root.deleteRecursively() }
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejection") }
        catch (_: IllegalArgumentException) { }
        catch (_: java.nio.charset.CharacterCodingException) { }
    }

    private fun saveTurn(store: ChiefBotProfileStore, agent: String, request: String, response: String) {
        val profile = requireNotNull(store.get(agent))
        val history = store.loadConversation(agent, agent)
        store.saveConversation(profile, history.append(ChiefBotConversationEntry(request, response,
            "fixture", "v1", "a".repeat(64)))) { true }
    }

    @Test fun datedCurrentRecordsSurviveReopenAndRemainPrivate() = fixture { root, store ->
        val original = requireNotNull(store.get("bot-kora"))
        store.saveWorkingContext(original.agentId, original.agentId, "KORA_PRIVATE_NOTES", original.profileVersion)
        saveTurn(store, original.agentId, "KORA_PRIVATE_REQUEST", "KORA_PRIVATE_RESPONSE")
        val current = requireNotNull(store.get(original.agentId))
        val note = requireNotNull(FileContinuumObjectStore(File(root, "working-context/${original.memoryNamespace}"))
            .get(ContinuumObjectId(requireNotNull(current.contextObjectId))))
        val conversation = requireNotNull(FileContinuumObjectStore(File(root, "conversations/${original.memoryNamespace}"))
            .get(ContinuumObjectId(requireNotNull(current.conversationObjectId))))
        val reopened = ChiefBotProfileStore(root)
        val snapshot = reopened.privateRecords(original.agentId, original.agentId)
        assertEquals(current, snapshot.profile)
        assertEquals(2, snapshot.records.size)
        val notes = snapshot.records.single { it.kind == ChiefBotPrivateRecordKind.WORKING_NOTES }
        val turns = snapshot.records.single { it.kind == ChiefBotPrivateRecordKind.CONVERSATION_SNAPSHOT }
        assertEquals(current.contextObjectId, notes.objectId)
        assertEquals(note.metadata.createdAtMillis, notes.createdAtMillis)
        assertEquals("KORA_PRIVATE_NOTES", notes.text)
        assertEquals(current.conversationObjectId, turns.objectId)
        assertEquals(conversation.metadata.createdAtMillis, turns.createdAtMillis)
        assertTrue(turns.text.contains("KORA_PRIVATE_REQUEST"))
        assertTrue(turns.text.contains("KORA_PRIVATE_RESPONSE"))
        assertTrue(reopened.privateRecords("bot-lint", "bot-lint").records.isEmpty())
        rejects { reopened.privateRecords("bot-lint", original.agentId) }
        try {
            (snapshot.records as MutableList<*>).clear()
            fail("Mutable private snapshot")
        } catch (_: UnsupportedOperationException) { }
    }

    @Test fun searchUsesCurrentPointersAndNeverResurrectsClearedObjects() = fixture { root, store ->
        val original = requireNotNull(store.get("bot-kora"))
        val noted = store.saveWorkingContext(original.agentId, original.agentId,
            "ALTER_PRIVATER_ENTWURF", original.profileVersion)
        saveTurn(store, original.agentId, "ALTER_PRIVATER_AUFTRAG", "ALTE_PRIVATE_ANTWORT")
        val old = requireNotNull(store.get(original.agentId))
        assertEquals(2, store.privateRecords(original.agentId, original.agentId, "privat").records.size)
        val updated = store.saveWorkingContext(original.agentId, original.agentId,
            "NEUER_ENTWURF", old.profileVersion)
        assertTrue(File(root, "working-context/${original.memoryNamespace}/${noted.contextObjectId}.obj").isFile)
        assertTrue(File(root, "conversations/${original.memoryNamespace}/${old.conversationObjectId}.obj").isFile)
        assertTrue(ChiefBotProfileStore(root).privateRecords(original.agentId, original.agentId, "privat").records.isEmpty())
        assertEquals("NEUER_ENTWURF", store.privateRecords(original.agentId, original.agentId).records.single().text)
        saveTurn(store, original.agentId, "AKTUELLER_AUFTRAG", "AKTUELLE_ANTWORT")
        store.clearConversation(original.agentId)
        assertTrue(store.privateRecords(original.agentId, original.agentId, "AKTUELLE").records.isEmpty())
        val latest = requireNotNull(store.get(updated.agentId))
        store.saveWorkingContext(latest.agentId, latest.agentId, "", latest.profileVersion)
        assertTrue(ChiefBotProfileStore(root).privateRecords(latest.agentId, latest.agentId).records.isEmpty())
    }

    @Test fun contentDedupKeepsOriginalDateAndSearchHonorsTimeBounds() = fixture { root, store ->
        val profile = requireNotNull(store.get("bot-kora"))
        val noted = store.saveWorkingContext(profile.agentId, profile.agentId, "Prüfung Roter Entwurf", profile.profileVersion)
        val first = store.privateRecords(profile.agentId, profile.agentId).records.single()
        val objectFile = File(root, "working-context/${profile.memoryNamespace}/${noted.contextObjectId}.obj")
        val originalBytes = objectFile.readBytes()
        val renamed = store.rename(profile.agentId, "Kora Nord", noted.profileVersion)
        store.saveWorkingContext(profile.agentId, profile.agentId, first.text, renamed.profileVersion)
        val second = ChiefBotProfileStore(root).privateRecords(profile.agentId, profile.agentId).records.single()
        assertEquals(first, second)
        assertArrayEquals(originalBytes, objectFile.readBytes())
        assertEquals(listOf(second), store.privateRecords(profile.agentId, profile.agentId, "roter",
            first.createdAtMillis, first.createdAtMillis + 1).records)
        assertTrue(store.privateRecords(profile.agentId, profile.agentId, "roter", 0, first.createdAtMillis).records.isEmpty())
        assertTrue(store.privateRecords(profile.agentId, profile.agentId, "unbekannt").records.isEmpty())
        rejects { store.privateRecords(profile.agentId, profile.agentId, "x".repeat(161)) }
        rejects { store.privateRecords(profile.agentId, profile.agentId, "bad" + 0xd800.toChar()) }
        rejects { store.privateRecords(profile.agentId, profile.agentId, "", 2, 1) }
    }

    @Test fun corruptCurrentRecordFailsWithoutFallingBackToOlderPrivateContent() = fixture { root, store ->
        val original = requireNotNull(store.get("bot-kora"))
        val first = store.saveWorkingContext(original.agentId, original.agentId, "ALTER_STAND", original.profileVersion)
        val next = store.saveWorkingContext(original.agentId, original.agentId, "NEUER_STAND", first.profileVersion)
        val current = File(root, "working-context/${original.memoryNamespace}/${next.contextObjectId}.obj")
        val bytes = current.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        current.writeBytes(bytes)
        rejects { store.privateRecords(original.agentId, original.agentId) }
        assertArrayEquals(bytes, current.readBytes())
        assertTrue(File(root, "working-context/${original.memoryNamespace}/${first.contextObjectId}.obj").isFile)
        assertEquals(next, store.get(original.agentId))
    }
}
