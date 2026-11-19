package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.LocalChatTranscriptStore

/** Read-only presentation of the existing central transcript store; no second conversation database. */
internal class ContinuumChatHistory(private val store: LocalChatTranscriptStore) {
    data class Entry(val id: String, val title: String, val updatedAt: Long)
    private data class CachedEntry(val entry: Entry, val userTitle: Boolean)
    private val cached = linkedMapOf<String, CachedEntry>()

    data class Restore(
        val id: String,
        val entries: List<LocalChatTranscriptStore.Entry>,
        val context: List<LocalChatTranscriptStore.ContextTurn>,
        val endOffset: Long
    )

    /** Explicit opening/refresh only; messages use [afterAppend] on the same serial transcript writer. */
    fun index(): List<Entry> {
        val refreshed = store.recentConversations(100).mapNotNull { id -> readEntry(id)?.let { id to it } }
        cached.clear()
        cached.putAll(refreshed)
        return sortedEntries()
    }

    /** Called only after the existing store successfully persisted this bubble. No archive rescan. */
    fun afterAppend(id: String, user: Boolean, text: String, atMillis: Long): List<Entry> {
        require(LocalChatTranscriptStore.ID.matches(id)) { "Ungültiges Gespräch" }
        if (text.isBlank()) return sortedEntries()
        val previous = cached[id] ?: readEntry(id)
        val useUserTitle = user && previous?.userTitle == false
        val title = if (previous == null || useUserTitle) title(text) else previous.entry.title
        cached[id] = CachedEntry(Entry(id, title, maxOf(previous?.entry?.updatedAt ?: atMillis, atMillis)),
            previous?.userTitle == true || user)
        return sortedEntries()
    }

    private fun readEntry(id: String): CachedEntry? {
        val visible = store.load(id).filter { it.text.isNotBlank() }
        if (visible.isEmpty()) return null
        val first = store.firstVisibleEntry(id)
        // If a damaged/oversized prefix has no readable title, keep an honest generic label.
        return CachedEntry(Entry(id, first?.let { title(it.text) } ?: "Gespräch",
            visible.maxOf { it.atMillis }), first?.user == true)
    }

    private fun sortedEntries(): List<Entry> = cached.values.map { it.entry }
        .sortedWith(compareByDescending<Entry> { it.updatedAt }.thenBy { it.id }).take(100)

    private fun title(text: String): String = text.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim()
        .take(96).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }

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
