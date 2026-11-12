package at.heinz.deviceai.cognitive

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * Persistent, app-private transcript of the central chat (Fold7 report #568: the chat was lost after leaving the
 * app; LocalDialogueRetention keeps model context only in memory).
 *
 * One JSON line per visible bubble in `<dir>/<conversationId>.jsonl`; the active conversation id survives an app
 * exit and Android process death. Nothing leaves the device (the app sets allowBackup="false"). Starting a new chat
 * never deletes an older one; only [delete] removes a conversation. Restoring returns text only: it can never
 * re-run a model request or a tool task. Not thread-safe across processes; one process-wide writer is expected.
 */
class LocalChatTranscriptStore(private val dir: File) {
    data class Entry(val user: Boolean, val text: String, val atMillis: Long)

    @Synchronized fun activeConversation(newId: () -> String): String {
        readActive()?.let { return it }
        return newId().also { setActive(it) }
    }

    @Synchronized fun setActive(id: String) {
        require(ID.matches(id)) { "Conversation id" }
        dir.mkdirs()
        val tmp = File(dir, "$ACTIVE.tmp")
        tmp.writeText(id)
        if (!tmp.renameTo(File(dir, ACTIVE))) { File(dir, ACTIVE).writeText(id); tmp.delete() }
    }

    @Synchronized fun append(id: String, user: Boolean, text: String, atMillis: Long) {
        require(ID.matches(id)) { "Conversation id" }
        if (text.isBlank()) return
        dir.mkdirs()
        appendLine(id, JSONObject().put("u", user).put("t", text.take(MAX_TEXT)).put("at", atMillis).toString())
    }

    private fun appendLine(id: String, line: String) {
        dir.mkdirs()
        val file = File(dir, "$id$SUFFIX")
        // A line torn by process death has no newline; start on a fresh line so this entry is not lost with it.
        val needsBreak = file.isFile && file.length() > 0 &&
            RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() != '\n'.code }
        file.appendText((if (needsBreak) "\n" else "") + line + "\n")
    }

    /**
     * A turn the app had admitted to the model's LocalConversationContext (successful local model answer), kept so
     * the same chat can continue with context after an app exit or process death. Unverified dialogue data only:
     * never a memory, fact proof or tool grant. Bubbles and context turns are separate line kinds.
     */
    data class ContextTurn(
        val request: String,
        val response: String,
        val requestComplete: Boolean = true,
        val responseComplete: Boolean = true,
        val modelResponse: Boolean = true,
        val responseHasEnd: Boolean = true
    ) {
        internal fun bounded(): ContextTurn {
            val ownerComplete = requestComplete && LocalDialogueContextLimits.completeOwner(request)
            val retainedResponse = LocalDialogueContextLimits.responseTail(response)
            return copy(request = if (ownerComplete) request else "", response = retainedResponse,
                requestComplete = ownerComplete,
                responseComplete = responseComplete && responseHasEnd && retainedResponse == response)
        }
    }

    @Synchronized fun appendContextTurn(id: String, request: String, response: String, atMillis: Long) =
        appendContextTurn(id, request, response, atMillis, true)

    @Synchronized fun appendContextTurn(id: String, request: String, response: String, atMillis: Long,
                                        responseComplete: Boolean) {
        require(ID.matches(id)) { "Conversation id" }
        if (request.isBlank() || response.isBlank()) return
        appendContextTurn(id, ContextTurn(request, response, responseComplete = responseComplete), atMillis)
    }

    @Synchronized fun appendContextTurn(id: String, turn: ContextTurn, atMillis: Long) {
        require(ID.matches(id)) { "Conversation id" }
        val bounded = turn.bounded()
        appendLine(id, JSONObject().put("k", CONTEXT).put("v", CONTEXT_VERSION)
            .put("q", bounded.request).put("r", bounded.response)
            .put("qc", bounded.requestComplete).put("rc", bounded.responseComplete)
            .put("model", bounded.modelResponse).put("end", bounded.responseHasEnd)
            .put("at", atMillis).toString())
    }

    /**
     * The app deliberately invalidated the model context of this conversation (non-model answer, topic reset,
     * correction); context turns stored before this marker are never restored. No file is created for it.
     */
    @Synchronized fun appendContextReset(id: String, atMillis: Long) {
        require(ID.matches(id)) { "Conversation id" }
        if (!File(dir, "$id$SUFFIX").isFile) return
        appendLine(id, JSONObject().put("k", CONTEXT_RESET).put("at", atMillis).toString())
    }

    /** The last [limit] context turns after the last reset marker, in order (the model context keeps at most four). */
    @Synchronized fun loadContextTurns(id: String, limit: Int = LocalDialogueContextLimits.MAX_TURNS): List<ContextTurn> {
        if (limit <= 0) return emptyList()
        return scanContext(id, LocalDialogueContextLimits.MAX_TURNS).turns.takeLast(limit)
    }

    /**
     * Code55 (field bug E): every context turn after the last reset marker, oldest first, at most [maxTurns]. The
     * owner object-state memory folds the whole conversation, not only the four-turn model window, so a reopened
     * app must replay all of it. When older turns of that range are not available (more than [maxTurns], or the
     * bounded tail read started inside the range) the result starts with the same "incomplete" placeholder a
     * corrupt line produces – a partial history is never presented as a complete one. Same file, same lines.
     */
    @Synchronized fun loadContextHistory(id: String, maxTurns: Int = MAX_HISTORY_TURNS): List<ContextTurn> {
        if (maxTurns <= 0) return emptyList()
        val scan = scanContext(id, maxTurns)
        return if (scan.olderMissing) listOf(incompleteTurn()) + scan.turns.takeLast(maxTurns - 1) else scan.turns
    }

    private class ContextScan(val turns: List<ContextTurn>, val olderMissing: Boolean)

    private fun incompleteTurn() = ContextTurn("", "(Gesprächskontext unvollständig)",
        requestComplete = false, responseComplete = false, modelResponse = false, responseHasEnd = false)

    private fun scanContext(id: String, keep: Int): ContextScan {
        val out = ArrayList<ContextTurn>()
        val tail = tail(id)
        // A tail read that starts inside the file may have cut turns after the last (unseen) reset marker.
        var olderMissing = tail.truncated
        fun invalidate() {
            out.clear()
            out += incompleteTurn()
            olderMissing = false
        }
        tail.lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val o = runCatching { JSONObject(line) }.getOrNull()
            if (o == null) { invalidate(); return@forEach }
            when (o.optString("k")) {
                CONTEXT_RESET -> { out.clear(); olderMissing = false }
                CONTEXT -> {
                    val turn = runCatching { readContextTurn(o) }.getOrNull()
                    if (turn == null) invalidate() else out += turn
                }
                else -> if (o.has("k")) invalidate()
            }
            while (out.size > keep) { out.removeAt(0); olderMissing = true }
        }
        return ContextScan(out, olderMissing)
    }

    private fun readContextTurn(o: JSONObject): ContextTurn {
        val request = o.getString("q")
        val response = o.getString("r")
        if (!o.has("v")) return ContextTurn(request, response,
            requestComplete = request.length < LEGACY_CONTEXT_REQUEST,
            responseComplete = response.length < LEGACY_CONTEXT_RESPONSE,
            responseHasEnd = response.length < LEGACY_CONTEXT_RESPONSE).bounded()
        require(o.get("v") == CONTEXT_VERSION)
        fun flag(key: String): Boolean = (o.get(key) as? Boolean) ?: error("Context completeness")
        return ContextTurn(request, response, flag("qc"), flag("rc"), flag("model"), flag("end")).bounded()
    }

    /** The last [limit] readable entries in order; a torn last line (process death mid-write) is skipped. */
    @Synchronized fun load(id: String, limit: Int = 200): List<Entry> {
        if (limit <= 0) return emptyList()
        return lines(id).mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                if (o.has("k")) null else Entry(o.getBoolean("u"), o.getString("t"), o.optLong("at"))
            }.getOrNull()
        }.toList().takeLast(limit)
    }

    private fun lines(id: String): Sequence<String> = tail(id).lines

    private class Tail(val lines: Sequence<String>, val truncated: Boolean)

    private fun tail(id: String): Tail {
        if (!ID.matches(id)) return Tail(emptySequence(), false)
        val file = File(dir, "$id$SUFFIX")
        if (!file.isFile) return Tail(emptySequence(), false)
        var truncated = false
        val text = RandomAccessFile(file, "r").use { raf ->
            val start = maxOf(0L, raf.length() - MAX_READ_BYTES)
            truncated = start > 0
            raf.seek(start)
            ByteArray((raf.length() - start).toInt()).also { raf.readFully(it) }.toString(Charsets.UTF_8)
                .let { if (start > 0) it.substringAfter('\n') else it }
        }
        return Tail(text.lineSequence(), truncated)
    }

    @Synchronized fun conversations(): List<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }.orEmpty()
            .map { it.name.removeSuffix(SUFFIX) }.filter { ID.matches(it) }.sorted()

    /** Explicit owner deletion of one conversation; clears the active pointer when it named it. */
    @Synchronized fun delete(id: String): Boolean {
        if (!ID.matches(id)) return false
        val removed = File(dir, "$id$SUFFIX").delete()
        if (readActive() == id) File(dir, ACTIVE).delete()
        return removed
    }

    private fun readActive(): String? = File(dir, ACTIVE).takeIf { it.isFile }
        ?.let { runCatching { it.readText().trim() }.getOrNull() }?.takeIf { ID.matches(it) }

    companion object {
        val ID = Regex("[a-f0-9-]{36}")
        private const val ACTIVE = "active-conversation"
        private const val CONTEXT = "ctx"
        private const val CONTEXT_RESET = "ctx-reset"
        private const val SUFFIX = ".jsonl"
        private const val MAX_TEXT = 32_000
        private const val CONTEXT_VERSION = 2
        private const val LEGACY_CONTEXT_REQUEST = 1_000
        private const val LEGACY_CONTEXT_RESPONSE = 2_000
        private const val MAX_READ_BYTES = 4L * 1024 * 1024
        /** Replay bound for the owner object-state memory after a reopen (UI-thread restore, bounded work). */
        const val MAX_HISTORY_TURNS = 256
    }
}
