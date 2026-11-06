package at.heinz.deviceai.cognitive

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
    enum class Kind { DOCUMENT, TOOL }
    enum class State { READ, DRAFTING, DRAFTED, SAVED, ABORTED, NO_MODEL, FAILED, INTERRUPTED }
    enum class ToolState { EXECUTING, SUCCEEDED, REJECTED, FAILED, CANCELLED, UNKNOWN }

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
        val correction: String? = null,
        /** When [correction] was spoken (journal clock); null without a correction or for rows written before it existed. */
        val correctedAt: Long? = null,
        /** Actual tool receipts only; no arguments, output, secrets or permissions. */
        val toolCallId: String? = null,
        val toolName: String? = null,
        val toolState: ToolState? = null,
        val toolOutputSha256: String? = null
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

    /** A real tool dispatch is recorded under its trusted call ID, never from model text. */
    fun beginTool(callId: String, toolName: String): Episode {
        require(CALL_ID.matches(callId) && TOOL_NAME.matches(toolName))
        all().firstOrNull { it.kind == Kind.TOOL && it.toolCallId == callId }?.let { return it }
        val now = clock()
        val suffix = MessageDigest.getInstance("SHA-256").digest(callId.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        val episode = Episode("t-$suffix", Kind.TOOL, "Werkzeugabfrage: $toolName", now, now,
            State.READ, source = toolName, toolCallId = callId, toolName = toolName,
            toolState = ToolState.EXECUTING)
        append(episode)
        return episode
    }

    /** A terminal tool outcome may only come from actual gate evidence. */
    fun finishTool(callId: String, toolName: String, outcome: ToolState, outputSha256: String? = null): Episode? {
        require(CALL_ID.matches(callId) && TOOL_NAME.matches(toolName))
        require(outcome != ToolState.EXECUTING)
        val current = all().firstOrNull { it.kind == Kind.TOOL && it.toolCallId == callId }
            ?: beginTool(callId, toolName)
        if (current.toolState != ToolState.EXECUTING) return current
        return update(current.id) {
            it.copy(source = toolName, toolName = toolName, toolState = outcome,
                toolOutputSha256 = outputSha256?.takeIf { hash ->
                    outcome == ToolState.SUCCEEDED && HASH.matches(hash)
                })
        }
    }

    fun append(episode: Episode) = synchronized(LOCK) {
        val rows = read().filterNot { it.id == episode.id } + sanitize(episode)
        // Keep the original 40 document slots, plus ten small tool entries.
        val documentIds = rows.filter { it.kind == Kind.DOCUMENT }.takeLast(MAX_EPISODES)
            .map { it.id }.toSet()
        val toolIds = rows.filter { it.kind == Kind.TOOL }.takeLast(MAX_TOOL_EPISODES)
            .map { it.id }.toSet()
        write(rows.filter { e ->
            if (e.kind == Kind.DOCUMENT) e.id in documentIds else e.id in toolIds
        })
    }

    fun begin(kind: Kind, request: String, state: State, source: String? = null, pages: Int = 0, chars: Int = 0,
              documentUri: String? = null): Episode =
        newEpisode(kind, request, state, source, pages, chars, documentUri).also(::append)

    /** Applies [change] to episode [id]; null when it is gone (bounded history). */
    fun update(id: String, change: (Episode) -> Episode): Episode? = synchronized(LOCK) {
        val rows = read()
        val old = rows.firstOrNull { it.id == id } ?: return@synchronized null
        val now = clock()
        val changed = change(old)
        // A new or changed correction keeps its own time: later edits of the episode must not move it.
        val correctedAt = when {
            changed.correction.isNullOrBlank() -> null
            changed.correction != old.correction -> now
            else -> old.correctedAt
        }
        val new = sanitize(changed.copy(id = old.id, kind = old.kind, startedAt = old.startedAt, updatedAt = now,
            correctedAt = correctedAt))
        write(rows.map { if (it.id == id) new else it })
        new
    }

    /**
     * A new app process runs no draft of an earlier one: every episode still DRAFTING whose last change is older than
     * this process ([processStartedAt]) was cut off (app closed or killed) and becomes INTERRUPTED – never „in Arbeit“
     * after a restart. Drafts of this process are left alone. Returns the number of episodes changed.
     */
    fun recoverInterrupted(processStartedAt: Long): Int = synchronized(LOCK) {
        val rows = read()
        fun cutOff(e: Episode) = e.updatedAt < processStartedAt &&
            (e.kind == Kind.DOCUMENT && e.state == State.DRAFTING ||
                e.kind == Kind.TOOL && e.toolState == ToolState.EXECUTING)
        val count = rows.count(::cutOff)
        if (count == 0) return@synchronized 0
        val now = clock()
        write(rows.map { e ->
            if (!cutOff(e)) e else if (e.kind == Kind.TOOL)
                e.copy(toolState = ToolState.UNKNOWN, updatedAt = now)
            else e.copy(state = State.INTERRUPTED, updatedAt = now)
        })
        count
    }

    /** The open next steps, newest first: „source: step“ (for the cockpit's process panel). */
    fun openSteps(now: Long, limit: Int = 3): List<String> = all().sortedByDescending { it.startedAt }
        .mapNotNull { e -> nextStep(e, now)?.let { "${e.source ?: "Auftrag"}: $it" } }.take(limit)

    private fun sanitize(e: Episode) = e.copy(
        request = e.request.take(MAX_REQUEST), source = e.source?.take(MAX_NAME), draft = e.draft?.take(MAX_DRAFT),
        documentUri = e.documentUri?.take(MAX_URI), draftUri = e.draftUri?.take(MAX_URI),
        correction = e.correction?.trim()?.take(MAX_REQUEST), ownerEdits = e.ownerEdits.coerceIn(0, 999),
        correctedAt = e.correctedAt?.takeIf { !e.correction.isNullOrBlank() },
        toolCallId = e.toolCallId?.takeIf(CALL_ID::matches),
        toolName = e.toolName?.takeIf(TOOL_NAME::matches),
        toolOutputSha256 = e.toolOutputSha256?.takeIf(HASH::matches)
    )

    private fun read(): List<Episode> {
        if (!file.exists()) return emptyList()
        require(!Files.isSymbolicLink(file.toPath()) && file.length() <= MAX_BYTES) { "Ungültiges Arbeitsjournal" }
        return file.readLines(Charsets.UTF_8).filter(String::isNotBlank).mapNotNull { line ->
            runCatching { decode(JSONObject(line)) }.getOrNull()
        }.takeLast(MAX_EPISODES + MAX_TOOL_EPISODES)
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
        e.correctedAt?.let { put("correctedAt", it) }
        e.toolCallId?.let { put("toolCallId", it) }
        e.toolName?.let { put("toolName", it) }
        e.toolState?.let { put("toolState", it.name) }
        e.toolOutputSha256?.let { put("toolOutputSha256", it) }
    }

    private fun decode(o: JSONObject) = Episode(
        o.getString("id"), Kind.valueOf(o.getString("kind")), o.getString("request"), o.getLong("startedAt"),
        o.getLong("updatedAt"), State.valueOf(o.getString("state")), o.optString("source").ifEmpty { null },
        o.optInt("sourcePages"), o.optInt("sourceChars"), o.optInt("coveredChars"),
        o.optString("documentUri").ifEmpty { null }, o.optString("draft").ifEmpty { null },
        o.optString("draftUri").ifEmpty { null }, o.optInt("ownerEdits"), o.optString("correction").ifEmpty { null },
        if (o.has("correctedAt")) o.getLong("correctedAt") else null,
        o.optString("toolCallId").ifEmpty { null },
        o.optString("toolName").ifEmpty { null },
        o.optString("toolState").takeIf(String::isNotEmpty)?.let(ToolState::valueOf),
        o.optString("toolOutputSha256").ifEmpty { null })

    companion object {
        const val MAX_EPISODES = 40
        const val MAX_TOOL_EPISODES = 10
        const val MAX_BYTES = 256_000L
        const val MAX_REQUEST = 240
        const val MAX_NAME = 120
        const val MAX_URI = 500
        const val MAX_DRAFT = 6_000
        private val LOCK = Any()
        private val CALL_ID = Regex("[A-Za-z0-9._:-]{1,64}")
        private val TOOL_NAME = Regex("[A-Za-z0-9._:-]{1,80}")
        private val HASH = Regex("[a-f0-9]{64}")

        /** A run that was still DRAFTING when the app went away (e.g. process killed) did not finish. */
        const val STALE_DRAFTING_MS = 10 * 60_000L

        /** Honest German state of an episode at [now]; a cut-off run is „unterbrochen“, never „erledigt“. */
        fun stateText(e: Episode, now: Long): String = if (e.kind == Kind.TOOL) when (e.toolState) {
            ToolState.EXECUTING -> if (now - e.updatedAt > STALE_DRAFTING_MS)
                "Werkzeuglauf möglicherweise unterbrochen; Ausgang prüfen" else "Werkzeug läuft"
            ToolState.SUCCEEDED -> "Werkzeug meldet frischen Erfolg; nicht unabhängig bestätigt"
            ToolState.REJECTED -> "Werkzeug abgelehnt; nichts ausgeführt"
            ToolState.FAILED -> "Werkzeug fehlgeschlagen"
            ToolState.CANCELLED -> "Werkzeug abgebrochen; Wirkung nicht behauptet"
            ToolState.UNKNOWN, null -> "Werkzeugausgang unklar; Wirkung prüfen"
        } else when (e.state) {
            State.READ -> "Dokument gelesen, kein Entwurf beauftragt"
            State.DRAFTING -> if (now - e.updatedAt > STALE_DRAFTING_MS) "Entwurf unterbrochen, nicht fertig" else "Entwurf in Arbeit"
            State.DRAFTED -> if (e.ownerEdits > 0) "Entwurf erstellt und von dir bearbeitet, noch nicht gespeichert"
                else "Entwurf erstellt, noch nicht gespeichert"
            State.SAVED -> "Entwurf gespeichert"
            State.ABORTED -> "mit STOP abgebrochen, nicht erledigt"
            State.NO_MODEL -> "Entwurf nicht erstellt – kein lokales Modell geladen"
            State.FAILED -> "fehlgeschlagen"
            State.INTERRUPTED -> "Entwurf unterbrochen (App wurde beendet), nicht fertig"
        }

        /** The open next step, or null when the task is complete. */
        fun nextStep(e: Episode, now: Long): String? = if (e.kind == Kind.TOOL) when (e.toolState) {
            ToolState.EXECUTING -> if (now - e.updatedAt > STALE_DRAFTING_MS)
                "Ausgang prüfen; nicht automatisch wiederholen" else null
            ToolState.UNKNOWN, null -> "Wirkung prüfen; nicht automatisch wiederholen"
            ToolState.FAILED -> "Fehler prüfen, bei Bedarf neu beauftragen"
            ToolState.SUCCEEDED, ToolState.REJECTED, ToolState.CANCELLED -> null
        } else when (e.state) {
            State.READ -> null
            State.DRAFTING -> if (now - e.updatedAt > STALE_DRAFTING_MS) "Entwurf neu starten" else null
            State.DRAFTED -> "Entwurf prüfen und speichern"
            State.SAVED -> null
            State.ABORTED -> "neu starten oder verwerfen"
            State.NO_MODEL -> "lokales Modell laden, dann den Entwurf erstellen"
            State.FAILED -> "Datei prüfen und erneut versuchen"
            State.INTERRUPTED -> "Entwurf neu starten"
        }
    }
}
