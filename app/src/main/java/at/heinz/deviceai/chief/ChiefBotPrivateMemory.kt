package at.heinz.deviceai.chief

import java.util.Collections

enum class ChiefBotPrivateRecordKind { WORKING_NOTES, CONVERSATION_SNAPSHOT }

/** Dates describe entire content snapshots. */
data class ChiefBotPrivateRecord(
    val kind: ChiefBotPrivateRecordKind,
    val objectId: String,
    val createdAtMillis: Long,
    val text: String
) {
    init {
        require(objectId.matches(Regex("[a-f0-9]{64}")) && createdAtMillis >= 0)
        require(text.isNotEmpty())
    }
}

/** Only currently reachable private records. */
class ChiefBotPrivateMemorySnapshot(
    val profile: ChiefBotProfile,
    records: List<ChiefBotPrivateRecord>
) {
    val records: List<ChiefBotPrivateRecord> = Collections.unmodifiableList(ArrayList(records))
}

internal fun ChiefBotConversationHistory.privateRecordText(): String = buildString {
    if (discardedTurns > 0) append("Frühere entfernte Gesprächspaare: $discardedTurns\n\n")
    if (turns.isEmpty()) append("Keine gespeicherten Gesprächspaare.")
    turns.forEachIndexed { index, entry ->
        if (index > 0) append("\n\n")
        append("Anfrage:\n").append(entry.request).append("\nAntwort:\n").append(entry.response)
        entry.practiceFeedback?.let { append("\nProgrammrückmeldung:\n").append(it.presentation()) }
    }
}
