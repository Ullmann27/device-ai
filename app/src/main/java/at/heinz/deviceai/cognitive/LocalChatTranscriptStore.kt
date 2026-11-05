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
        val line = JSONObject().put("u", user).put("t", text.take(MAX_TEXT)).put("at", atMillis).toString()
        val file = File(dir, "$id$SUFFIX")
        // A line torn by process death has no newline; start on a fresh line so this entry is not lost with it.
        val needsBreak = file.isFile && file.length() > 0 &&
            RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() != '\n'.code }
        file.appendText((if (needsBreak) "\n" else "") + line + "\n")
    }

    /** The last [limit] readable entries in order; a torn last line (process death mid-write) is skipped. */
    @Synchronized fun load(id: String, limit: Int = 200): List<Entry> {
        if (!ID.matches(id) || limit <= 0) return emptyList()
        val file = File(dir, "$id$SUFFIX")
        if (!file.isFile) return emptyList()
        val text = RandomAccessFile(file, "r").use { raf ->
            val start = maxOf(0L, raf.length() - MAX_READ_BYTES)
            raf.seek(start)
            ByteArray((raf.length() - start).toInt()).also { raf.readFully(it) }.toString(Charsets.UTF_8)
                .let { if (start > 0) it.substringAfter('\n') else it }
        }
        return text.lineSequence().mapNotNull { line ->
            runCatching {
                val o = JSONObject(line)
                Entry(o.getBoolean("u"), o.getString("t"), o.optLong("at"))
            }.getOrNull()
        }.toList().takeLast(limit)
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
        private const val SUFFIX = ".jsonl"
        private const val MAX_TEXT = 32_000
        private const val MAX_READ_BYTES = 4L * 1024 * 1024
    }
}
