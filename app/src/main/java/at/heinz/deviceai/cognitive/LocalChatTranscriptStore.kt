package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelGenerationPolicy
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

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
        val end = minOf(text.length, MAX_TEXT).let {
            if (it < text.length && text[it - 1].isHighSurrogate()) it - 1 else it
        }
        appendLine(id, JSONObject().put("u", user).put("t", text.substring(0, end)).put("at", atMillis).toString())
    }

    internal data class PreparedAppend(val conversationId: String, val offset: Long, val text: String)

    // The caller serializes all writers.
    @Synchronized internal fun prepareAssistantAppend(id: String, text: String, atMillis: Long): PreparedAppend {
        require(text.isNotBlank() && text.length <= MAX_TEXT && atMillis >= 0)
        val line = JSONObject().put("u", false).put("t", text).put("at", atMillis).toString() + "\n"
        strictUtf8(line)
        val file = appendTarget(id, create = true)
        val prepared = FileChannel.open(file.toPath(), CREATE, READ, WRITE, NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            val last = ByteBuffer.allocate(1)
            val needsBreak = size > 0 && channel.read(last, size - 1) == 1 && last[0].toInt() != '\n'.code
            channel.force(true)
            PreparedAppend(id, size, (if (needsBreak) "\n" else "") + line)
        }
        forceDirectory()
        return prepared
    }

    // Recovery proves the exact bytes.
    @Synchronized internal fun appendPrepared(prepared: PreparedAppend): Boolean {
        require(prepared.offset >= 0 && prepared.text.endsWith("\n"))
        val line = prepared.text.removePrefix("\n").removeSuffix("\n")
        require('\n' !in line && '\r' !in line)
        val record = at.heinz.deviceai.learning.StrictJson.parseObject(line)
        require(record.keys == setOf("u", "t", "at") && record["u"] == false)
        val text = record["t"] as? String ?: error("Assistant text required.")
        val at = record["at"] as? Long ?: error("Assistant timestamp required.")
        require(text.isNotBlank() && text.length <= MAX_TEXT && at >= 0)
        val expected = strictUtf8(prepared.text)
        require(expected.size <= MAX_TEXT * 6 + 256 && prepared.offset <= Long.MAX_VALUE - expected.size)
        val file = appendTarget(prepared.conversationId, create = false)
        val appended = FileChannel.open(file.toPath(), READ, WRITE, NOFOLLOW_LINKS).use { channel ->
            val length = channel.size()
            if (length < prepared.offset) throw IOException("Chat shortened before delivery.")
            val available = minOf(length - prepared.offset, expected.size.toLong()).toInt()
            val actual = ByteBuffer.allocate(available)
            channel.position(prepared.offset)
            while (actual.hasRemaining()) {
                if (channel.read(actual) <= 0) throw IOException("Chat changed during delivery.")
            }
            if (!actual.array().contentEquals(expected.copyOf(available)))
                throw IOException("Chat delivery bytes conflict.")
            if (available < expected.size) {
                val remaining = ByteBuffer.wrap(expected, available, expected.size - available)
                while (remaining.hasRemaining()) channel.write(remaining)
            }
            channel.force(true)
            available < expected.size
        }
        forceDirectory()
        return appended
    }

    private fun appendTarget(id: String, create: Boolean): File {
        require(ID.matches(id)) { "Conversation id" }
        val path = dir.absoluteFile.toPath().normalize()
        require(path == dir.canonicalFile.toPath()) { "Unsafe chat directory." }
        if (create) Files.createDirectories(path)
        if (!Files.isDirectory(path, NOFOLLOW_LINKS)) throw IOException("Chat directory missing.")
        val file = File(dir, "$id$SUFFIX")
        val target = file.toPath()
        if (Files.isSymbolicLink(target) || Files.exists(target, NOFOLLOW_LINKS) &&
            !Files.isRegularFile(target, NOFOLLOW_LINKS)) throw IOException("Unsafe chat file.")
        if (!create && !Files.exists(target, NOFOLLOW_LINKS)) throw IOException("Chat was deleted.")
        return file
    }

    private fun forceDirectory() = FileChannel.open(dir.toPath(), READ).use { it.force(true) }

    private fun strictUtf8(text: String): ByteArray {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        return ByteArray(encoded.remaining()).also(encoded::get)
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
        val directory = dir.absoluteFile.toPath().normalize()
        if (directory != dir.canonicalFile.toPath() || Files.exists(directory, NOFOLLOW_LINKS) &&
            !Files.isDirectory(directory, NOFOLLOW_LINKS)) throw IOException("Unsafe chat directory.")
        if (Files.notExists(File(dir, "$id$SUFFIX").toPath(), NOFOLLOW_LINKS)) return
        val file = appendTarget(id, create = false)
        val line = JSONObject().put("k", CONTEXT_RESET).put("at", atMillis).toString() + "\n"
        FileChannel.open(file.toPath(), READ, WRITE, NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            val last = ByteBuffer.allocate(1)
            val needsBreak = size > 0 && channel.read(last, size - 1) == 1 && last[0].toInt() != '\n'.code
            val bytes = ByteBuffer.wrap(strictUtf8((if (needsBreak) "\n" else "") + line))
            channel.position(size)
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        forceDirectory()
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
        return snapshot(id, limit).entries
    }

    internal data class Snapshot(val entries: List<Entry>, val endOffset: Long)

    @Synchronized internal fun snapshot(id: String, limit: Int = 200): Snapshot {
        if (limit <= 0) return Snapshot(emptyList(), 0)
        val tail = tail(id)
        val entries = tail.lines.mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                if (o.has("k")) null else Entry(o.getBoolean("u"), o.getString("t"), o.optLong("at"))
            }.getOrNull()
        }.toList().takeLast(limit)
        return Snapshot(entries, tail.endOffset)
    }

    private class Tail(val lines: Sequence<String>, val truncated: Boolean, val endOffset: Long = 0)

    private fun tail(id: String): Tail {
        if (!ID.matches(id)) return Tail(emptySequence(), false)
        val file = File(dir, "$id$SUFFIX")
        if (!file.isFile) return Tail(emptySequence(), false)
        var truncated = false
        var endOffset = 0L
        val text = RandomAccessFile(file, "r").use { raf ->
            endOffset = raf.length()
            val start = maxOf(0L, endOffset - MAX_READ_BYTES)
            truncated = start > 0
            raf.seek(start)
            ByteArray((endOffset - start).toInt()).also { raf.readFully(it) }.toString(Charsets.UTF_8)
                .let { if (start > 0) it.substringAfter('\n') else it }
        }
        return Tail(text.lineSequence(), truncated, endOffset)
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
        // Keep the partial-answer status too.
        private const val MAX_TEXT = LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS + 4_096
        private const val CONTEXT_VERSION = 2
        private const val LEGACY_CONTEXT_REQUEST = 1_000
        private const val LEGACY_CONTEXT_RESPONSE = 2_000
        private const val MAX_READ_BYTES = 4L * 1024 * 1024
        /** Replay bound for the owner object-state memory after a reopen (UI-thread restore, bounded work). */
        const val MAX_HISTORY_TURNS = 256
    }
}
