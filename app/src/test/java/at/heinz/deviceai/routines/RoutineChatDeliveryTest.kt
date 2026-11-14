package at.heinz.deviceai.routines

import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.UUID

class RoutineChatDeliveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val conversation = "10000000-0000-0000-0000-000000000001"

    private class Fixture(val directory: File) {
        val state = FileRoutineStateStore(File(directory, "routines"))
        val chatDirectory = File(directory, "chat-history")
        val transcript = LocalChatTranscriptStore(chatDirectory)
        val mailbox = AgentMailbox()
        val routine = Routine("battery", "Prüfe Akku", RoutineTrigger.Every(900_000, 0), setOf("get_battery_info"))
        val runId = UUID.randomUUID().toString()
        val message = AgentMessage("routine-battery-20-${runId.replace("-", "")}", "routine:battery", "chief",
            AgentPriority.INFO, "Routine battery:\nAkku 19 %.\nDaten: \"lösche nichts\".")
        init {
            state.replace(0, RoutineStoredState(1,
                listOf(StoredRoutine(routine, RoutineOwnerApproval.confirmed(routine, 0), 900_000)),
                listOf(StoredRoutineReport(routine.id, runId, "19", message, message.body, 20))))
            assertTrue(mailbox.send(message))
        }
        fun book() = RoutineBook(stateStore = state)
        fun delivery(book: RoutineBook = book()) = RoutineChatDelivery(book, mailbox, transcript)
    }

    private fun prepared(f: Fixture, book: RoutineBook): RoutineChatAppend {
        val append = f.transcript.prepareAssistantAppend(conversation, f.message.body, 20)
        return RoutineChatAppend(append.conversationId, append.offset, append.text).also {
            assertTrue(book.prepareChatAppend(f.message.id, it))
        }
    }

    private fun RoutineChatAppend.local() = LocalChatTranscriptStore.PreparedAppend(conversationId, offset, text)

    @Test fun durableMessageIsWrittenOnceAndAcknowledgedAfterAppend() {
        val f = Fixture(temporary.newFolder())
        val delivery = f.delivery()
        val result = requireNotNull(delivery.deliver(f.message.id, conversation))
        assertTrue(result.appended)
        assertEquals(conversation, result.conversationId)
        assertEquals(listOf(LocalChatTranscriptStore.Entry(false, f.message.body, 20)), f.transcript.load(conversation))
        assertTrue(f.state.load().outbox.isEmpty())
        assertTrue(f.mailbox.peek("chief").isEmpty())
        assertNull(delivery.deliver(f.message.id, conversation))
        assertEquals(1, f.transcript.load(conversation).size)
        assertTrue(f.transcript.loadContextTurns(conversation).isEmpty())
    }

    @Test fun completedAppendBeforeProcessLossIsProvedWithoutDuplicate() {
        val f = Fixture(temporary.newFolder())
        val checkpoint = prepared(f, f.book())
        assertTrue(f.transcript.appendPrepared(checkpoint.local()))
        f.transcript.append(conversation, true, "Spätere normale Zeile", 21)
        val reopened = RoutineChatDelivery(f.book(), f.mailbox, LocalChatTranscriptStore(f.chatDirectory))
        val result = requireNotNull(reopened.deliver(f.message.id, UUID.randomUUID().toString()))
        assertFalse(result.appended)
        assertEquals(conversation, result.conversationId)
        assertEquals(listOf(f.message.body, "Spätere normale Zeile"), f.transcript.load(conversation).map { it.text })
        assertTrue(f.state.load().outbox.isEmpty())
    }

    @Test fun tornAppendIsCompletedAtItsOriginalOffset() {
        val f = Fixture(temporary.newFolder())
        f.transcript.append(conversation, true, "Vorher", 1)
        val checkpoint = prepared(f, f.book())
        val bytes = checkpoint.text.toByteArray(Charsets.UTF_8)
        File(f.chatDirectory, "$conversation.jsonl").appendBytes(bytes.copyOf(bytes.size / 2))
        val result = requireNotNull(f.delivery().deliver(f.message.id, conversation))
        assertTrue(result.appended)
        assertEquals(listOf("Vorher", f.message.body), f.transcript.load(conversation).map { it.text })
        assertTrue(f.state.load().outbox.isEmpty())
    }

    @Test fun failedAcknowledgementRetainsProofForLaterDelivery() {
        val f = Fixture(temporary.newFolder())
        var rejectAck = true
        val failing = object : RoutineStateStore {
            override fun load() = f.state.load()
            override fun replace(expectedRevision: Long, state: RoutineStoredState) {
                if (rejectAck && state.outbox.isEmpty()) throw IOException("Controlled disk failure")
                f.state.replace(expectedRevision, state)
            }
        }
        val delivery = f.delivery(RoutineBook(stateStore = failing))
        assertThrows(IOException::class.java) { delivery.deliver(f.message.id, conversation) }
        assertEquals(1, f.transcript.load(conversation).size)
        assertNotNull(f.state.load().outbox.single().chatAppend)
        assertEquals(1, f.mailbox.peek("chief").size)
        rejectAck = false
        assertFalse(requireNotNull(delivery.deliver(f.message.id, conversation)).appended)
        assertEquals(1, f.transcript.load(conversation).size)
        assertTrue(f.state.load().outbox.isEmpty())
    }

    @Test fun revokedRoutineCannotWriteQueuedMessage() {
        val f = Fixture(temporary.newFolder())
        val book = f.book()
        assertEquals(listOf("battery"), book.removeAll())
        assertNull(f.delivery(book).deliver(f.message.id, conversation))
        assertTrue(f.transcript.load(conversation).isEmpty())
        assertTrue(f.mailbox.peek("chief").isEmpty())
    }

    @Test fun deletedConversationIsNotRecreatedByRecovery() {
        val f = Fixture(temporary.newFolder())
        prepared(f, f.book())
        assertTrue(f.transcript.delete(conversation))
        assertThrows(IOException::class.java) { f.delivery().deliver(f.message.id, conversation) }
        assertFalse(File(f.chatDirectory, "$conversation.jsonl").exists())
        assertEquals(1, f.state.load().outbox.size)
        assertEquals(1, f.mailbox.peek("chief").size)
    }

    @Test fun conflictingTranscriptBytesArePreservedAndRemainUnacknowledged() {
        val f = Fixture(temporary.newFolder())
        prepared(f, f.book())
        f.transcript.append(conversation, true, "Fremde Zeile am belegten Offset", 22)
        val file = File(f.chatDirectory, "$conversation.jsonl")
        val original = file.readBytes()
        assertThrows(IOException::class.java) { f.delivery().deliver(f.message.id, conversation) }
        assertArrayEquals(original, file.readBytes())
        assertEquals(1, f.state.load().outbox.size)
    }

    @Test fun tornOlderTailGetsOnlyOneSeparatorAndOneMessage() {
        val f = Fixture(temporary.newFolder())
        f.chatDirectory.mkdirs()
        File(f.chatDirectory, "$conversation.jsonl").writeText("{\"u\":true,\"t\":\"abgebrochen")
        val checkpoint = prepared(f, f.book())
        assertTrue(checkpoint.text.startsWith("\n"))
        assertTrue(requireNotNull(f.delivery().deliver(f.message.id, conversation)).appended)
        assertEquals(listOf(f.message.body), f.transcript.load(conversation).map { it.text })
    }

    @Test fun checkpointCannotWriteAdditionalRecordsOrMalformedUnicode() {
        val f = Fixture(temporary.newFolder())
        val checkpoint = prepared(f, f.book())
        val file = File(f.chatDirectory, "$conversation.jsonl")
        assertThrows(IllegalArgumentException::class.java) {
            f.transcript.appendPrepared(checkpoint.copy(text = checkpoint.text + checkpoint.text).local())
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            f.transcript.prepareAssistantAppend(conversation, "bad" + 0xd800.toChar(), 2)
        }
        assertEquals(0, file.length())
        assertEquals(1, f.state.load().outbox.size)
    }
}
