package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.LocalChatTranscriptStore

/** Read-only presentation of the existing central transcript store; no second conversation database. */
internal class ContinuumChatHistory(private val store: LocalChatTranscriptStore) {
    data class Entry(val id: String, val title: String, val updatedAt: Long)

    data class Restore(
        val id: String,
        val entries: List<LocalChatTranscriptStore.Entry>,
        val context: List<LocalChatTranscriptStore.ContextTurn>,
        val endOffset: Long
    )

    /** Only conversations containing a real visible message are selectable. */
    fun index(): List<Entry> = store.conversations().mapNotNull { id ->
        val entries = store.load(id)
        val first = entries.firstOrNull { it.user && it.text.isNotBlank() }
            ?: entries.firstOrNull { it.text.isNotBlank() } ?: return@mapNotNull null
        val title = first.text.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(96)
        Entry(id, title, entries.maxOfOrNull { it.atMillis } ?: 0L)
    }.sortedWith(compareByDescending<Entry> { it.updatedAt }.thenBy { it.id }).take(100)

    /** Read before any UI/context/persistent selection is replaced. Missing files fail closed. */
    fun read(id: String): Restore {
        require(LocalChatTranscriptStore.ID.matches(id)) { "Ungültiges Gespräch" }
        check(id in store.conversations()) { "Gespräch nicht mehr vorhanden" }
        val snapshot = store.snapshot(id)
        check(snapshot.entries.isNotEmpty()) { "Gespräch enthält keinen lesbaren Verlauf" }
        val context = store.loadContextHistory(id)
        return Restore(id, snapshot.entries, context, snapshot.endOffset)
    }
}
