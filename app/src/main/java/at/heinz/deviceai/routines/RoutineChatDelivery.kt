package at.heinz.deviceai.routines

import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import java.io.IOException

internal class RoutineChatDelivery(
    private val book: RoutineBook,
    private val mailbox: AgentMailbox,
    private val transcript: LocalChatTranscriptStore
) {
    data class Delivered(val messageId: String, val conversationId: String, val text: String,
                         val atMillis: Long, val appended: Boolean)

    // Use the existing transcript executor.
    fun deliver(messageId: String, conversationId: String): Delivered? = synchronized(book) {
        val pending = book.pendingChatReports().singleOrNull { it.message?.id == messageId }
        if (pending == null) {
            mailbox.acknowledge("chief", messageId)
            return null
        }
        val message = requireNotNull(pending.message)
        val prepared = pending.chatAppend ?: transcript.prepareAssistantAppend(
            conversationId, message.body, pending.atMillis).let {
            RoutineChatAppend(it.conversationId, it.offset, it.text).also { checkpoint ->
                if (!book.prepareChatAppend(messageId, checkpoint))
                    throw IOException("Routine delivery was revoked.")
            }
        }
        val appended = transcript.appendPrepared(LocalChatTranscriptStore.PreparedAppend(
            prepared.conversationId, prepared.offset, prepared.text))
        if (!book.acknowledgeChat(messageId)) throw IOException("Routine delivery could not be acknowledged.")
        mailbox.acknowledge("chief", messageId)
        Delivered(messageId, prepared.conversationId, message.body, pending.atMillis, appended)
    }
}
