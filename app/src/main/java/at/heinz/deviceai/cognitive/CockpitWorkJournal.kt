package at.heinz.deviceai.cognitive

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * What the owner asked JARVIS to do in the cockpit and what really happened (owner brief 06.11.2026): the request in the
 * owner's words, the source, the state actually reached, owner corrections and the open next step – so „Wo waren wir
 * gestern?“ is answered from the record instead of being re-explained.
 *
 * A work record, not a fact memory: nothing here becomes an owner fact (that stays [at.heinz.deviceai.learning
 * .ContinuumTemporalMemory] / USER_FACT). A draft is kept as the local model's unverified output. A run that was cut off
 * stays unfinished – [state] is only advanced by real events. App-private, bounded (40 episodes, 256 KB), atomically
 * rewritten, survives restarts.
 */
class CockpitWorkJournal(directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    enum class Kind { DOCUMENT }
    enum class State { READ, DRAFTING, DRAFTED, SAVED, ABORTED, NO_MODEL, FAILED }

    data class Episode(
        val id: String,
        val kind: Kind,
        /** The owner's own words (bounded), never model text. */
        val request: String,
        val startedAt: Long,
        val updatedAt: Long,
        val state: State,
        val source: String? = null,
        val sourcePages: Int = 0,
        val sourceChars: Int = 0,
        /** Characters of the source the draft was based on (0 = no draft). */
        val coveredChars: Int = 0,
        val documentUri: String? = null,
        /** The local model's draft as last shown or edited by the owner – unverified output. */
        val draft: String? = null,
        val draftUri: String? = null,
        val ownerEdits: Int = 0,
        /** The owner's last correction of this task in their own words („Nein, ich meinte …“). */
        val correction: String? = null
    )

    private val folder = File(directory, "cockpit-work")
    private val file = File(folder, "journal-v1.jsonl")

    fun all(): List<Episode> = synchronized(LOCK) { read() }

    /** A new episode (no I/O): the caller may [append] it off the UI thread, in order with later [update]s. */
    fun newEpisode(kind: Kind, request: String, state: State, source: String? = null, pages: Int = 0, chars: Int = 0,
                   documentUri: String? = null): Episode {
        val now = clock()
        return Episode("w-$now-${(now xor request.hashCode().toLong()).toULong().toString(36).takeLast(6)}", kind,
            request.trim().take(MAX_REQUEST), now, now, state, source?.take(MAX_NAME), pages.coerceAtLeast(0),
            chars.coerceAtLeast(0), documentUri = documentUri?.take(MAX_URI))
    }

    fun append(episode: Episode) = synchronized(LOCK) {
        write((read().filterNot { it.id == episode.id } + sanitize(episode)).takeLast(MAX_EPISODES))
    }

    fun begin(kind: Kind, request: String, state: State, source: String? = null, pages: Int = 0, chars: Int = 0,
              documentUri: String? = null): Episode =
        newEpisode(kind, request, state, source, pages, chars, documentUri).also(::append)

    /** Applies [change] to episode [id]; null when it is gone (bounded history). */
    fun update(id: String, change: (Episode) -> Episode): Episode? = synchronized(LOCK) {
        val rows = read()
        val old = rows.firstOrNull { it.id == id } ?: return@synchronized null
        val new = sanitize(change(old).copy(id = old.id, kind = old.kind, startedAt = old.startedAt, updatedAt = clock()))
        write(rows.map { if (it.id == id) new else it })
        new
    }

    private fun sanitize(e: Episode) = e.copy(
        request = e.request.take(MAX_REQUEST), source = e.source?.take(MAX_NAME), draft = e.draft?.take(MAX_DRAFT),
        documentUri = e.documentUri?.take(MAX_URI), draftUri = e.draftUri?.take(MAX_URI),
        correction = e.correction?.trim()?.take(MAX_REQUEST), ownerEdits = e.ownerEdits.coerceIn(0, 999))

    private fun read(): List<Episode> {
        if (!file.exists()) return emptyList()
        require(!Files.isSymbolicLink(file.toPath()) && file.length() <= MAX_BYTES) { "Ungültiges Arbeitsjournal" }
        return file.readLines(Charsets.UTF_8).filter(String::isNotBlank).mapNotNull { line ->
            runCatching { decode(JSONObject(line)) }.getOrNull()
        }.takeLast(MAX_EPISODES)
    }

    private fun write(rows: List<Episode>) {
        require(!Files.isSymbolicLink(folder.toPath()))
        check(folder.isDirectory || folder.mkdirs())
        var kept = rows
        var bytes = encodeAll(kept)
        while (bytes.size > MAX_BYTES && kept.isNotEmpty()) { kept = kept.drop(1); bytes = encodeAll(kept) }
        val pending = File.createTempFile("journal-", ".tmp", folder)
        try {
            pending.outputStream().use { it.write(bytes); it.fd.sync() }
            try {
                Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { pending.delete() }
    }

    private fun encodeAll(rows: List<Episode>) =
        rows.joinToString("") { encode(it).toString() + "\n" }.toByteArray(Charsets.UTF_8)

    private fun encode(e: Episode) = JSONObject().apply {
        put("id", e.id); put("kind", e.kind.name); put("request", e.request); put("startedAt", e.startedAt)
        put("updatedAt", e.updatedAt); put("state", e.state.name); e.source?.let { put("source", it) }
        put("sourcePages", e.sourcePages); put("sourceChars", e.sourceChars); put("coveredChars", e.coveredChars)
        e.documentUri?.let { put("documentUri", it) }; e.draft?.let { put("draft", it) }
        e.draftUri?.let { put("draftUri", it) }; put("ownerEdits", e.ownerEdits); e.correction?.let { put("correction", it) }
    }

    private fun decode(o: JSONObject) = Episode(
        o.getString("id"), Kind.valueOf(o.getString("kind")), o.getString("request"), o.getLong("startedAt"),
        o.getLong("updatedAt"), State.valueOf(o.getString("state")), o.optString("source").ifEmpty { null },
        o.optInt("sourcePages"), o.optInt("sourceChars"), o.optInt("coveredChars"),
        o.optString("documentUri").ifEmpty { null }, o.optString("draft").ifEmpty { null },
        o.optString("draftUri").ifEmpty { null }, o.optInt("ownerEdits"), o.optString("correction").ifEmpty { null })

    companion object {
        const val MAX_EPISODES = 40
        const val MAX_BYTES = 256_000L
        const val MAX_REQUEST = 240
        const val MAX_NAME = 120
        const val MAX_URI = 500
        const val MAX_DRAFT = 6_000
        private val LOCK = Any()

        /** A run that was still DRAFTING when the app went away (e.g. process killed) did not finish. */
        const val STALE_DRAFTING_MS = 10 * 60_000L

        /** Honest German state of an episode at [now]; a cut-off run is „unterbrochen“, never „erledigt“. */
        fun stateText(e: Episode, now: Long): String = when (e.state) {
            State.READ -> "Dokument gelesen, kein Entwurf beauftragt"
            State.DRAFTING -> if (now - e.updatedAt > STALE_DRAFTING_MS) "Entwurf unterbrochen, nicht fertig" else "Entwurf in Arbeit"
            State.DRAFTED -> if (e.ownerEdits > 0) "Entwurf erstellt und von dir bearbeitet, noch nicht gespeichert"
                else "Entwurf erstellt, noch nicht gespeichert"
            State.SAVED -> "Entwurf gespeichert"
            State.ABORTED -> "mit STOP abgebrochen, nicht erledigt"
            State.NO_MODEL -> "Entwurf nicht erstellt – kein lokales Modell geladen"
            State.FAILED -> "fehlgeschlagen"
        }

        /** The open next step, or null when the task is complete. */
        fun nextStep(e: Episode, now: Long): String? = when (e.state) {
            State.READ -> null
            State.DRAFTING -> if (now - e.updatedAt > STALE_DRAFTING_MS) "Entwurf neu starten" else null
            State.DRAFTED -> "Entwurf prüfen und speichern"
            State.SAVED -> null
            State.ABORTED -> "neu starten oder verwerfen"
            State.NO_MODEL -> "lokales Modell laden, dann den Entwurf erstellen"
            State.FAILED -> "Datei prüfen und erneut versuchen"
        }
    }
}
