package at.heinz.deviceai.cognitive

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Persistent, bounded UTF-8 chunk locator. Does not read private data or invoke a model. */
object DevelopmentSourceChunkIndex {
    const val CHUNK_BYTES = 64 * 1024
    private const val MAX_CHUNKS = 2000
    private const val MAGIC = "CONTINUUM-SOURCE-INDEX-1"
    data class Chunk(val start: Long, val end: Long, val firstLine: Long, val lastLine: Long, val sha256: String)
    data class Index(val source: DevelopmentSourceStreamImport.ImportedSource, val indexFile: File, val chunks: List<Chunk>)
    data class Excerpt(val text: String, val start: Long, val end: Long, val firstLine: Long, val lastLine: Long)

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun hex(digest: MessageDigest): String = digest.digest()
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Index cancelled")
    }
    private fun sidecar(source: DevelopmentSourceStreamImport.ImportedSource): File =
        File(source.file.parentFile, source.file.name + ".cidx")

    /** Run off the UI thread. One full source scan, O(64 KiB) I/O buffering, <=2000 index records. */
    fun build(source: DevelopmentSourceStreamImport.ImportedSource,
              cancelled: () -> Boolean = { false }): Index {
        require(source.bytes in 1..DevelopmentSourceStreamImport.MAX_SOURCE_BYTES)
        require(source.file.isFile && source.file.length() == source.bytes)
        val target = sidecar(source)
        if (target.exists()) throw IOException("Index already exists; load or remove it explicitly")
        val chunks = ArrayList<Chunk>()
        val all = MessageDigest.getInstance("SHA-256")
        var part = MessageDigest.getInstance("SHA-256")
        var offset = 0L
        var start = 0L
        var line = 1L
        var firstLine = 1L
        var lastNewline = false
        fun finish() {
            if (offset == start) return
            if (chunks.size >= MAX_CHUNKS) throw IOException("Too many source chunks")
            chunks.add(Chunk(start, offset, firstLine,
                if (lastNewline) line - 1 else line, hex(part)))
            start = offset
            firstLine = line
            part = MessageDigest.getInstance("SHA-256")
        }
        BufferedInputStream(FileInputStream(source.file), CHUNK_BYTES).use { input ->
            while (true) {
                if (offset % CHUNK_BYTES == 0L) checkCancelled(cancelled)
                val value = input.read()
                if (value < 0) break
                // Never split within a UTF-8 code point. A long line may span chunks.
                if (offset - start >= CHUNK_BYTES && value and 0xC0 != 0x80) finish()
                val byte = value.toByte()
                all.update(byte)
                part.update(byte)
                offset++
                lastNewline = value == 10
                if (lastNewline) line++
            }
        }
        checkCancelled(cancelled)
        finish()
        if (offset != source.bytes || hex(all) != source.sha256)
            throw IOException("Source changed since verified import")
        val temp = File.createTempFile(".source-index-", ".part", source.file.parentFile)
        try {
            FileOutputStream(temp).use { out ->
                DataOutputStream(out).use { data ->
                    data.writeUTF(MAGIC)
                    data.writeLong(source.bytes)
                    data.writeUTF(source.sha256)
                    data.writeInt(chunks.size)
                    for (chunk in chunks) {
                        data.writeLong(chunk.start); data.writeLong(chunk.end)
                        data.writeLong(chunk.firstLine); data.writeLong(chunk.lastLine)
                        data.writeUTF(chunk.sha256)
                    }
                    data.flush()
                    out.fd.sync()
                }
            }
            checkCancelled(cancelled)
            if (target.exists() || !temp.renameTo(target)) throw IOException("Index commit failed")
        } finally { temp.delete() }
        return Index(source, target, chunks.toList())
    }

    /** Verifies source hash and all index bounds before trusting a persisted sidecar. */
    fun load(source: DevelopmentSourceStreamImport.ImportedSource,
             cancelled: () -> Boolean = { false }): Index {
        val target = sidecar(source)
        if (!target.isFile || source.file.length() != source.bytes) throw IOException("Missing/stale source index")
        val chunks = DataInputStream(FileInputStream(target)).use { input ->
            if (input.readUTF() != MAGIC || input.readLong() != source.bytes ||
                input.readUTF() != source.sha256) throw IOException("Index identity mismatch")
            val count = input.readInt()
            if (count !in 1..MAX_CHUNKS) throw IOException("Invalid index count")
            val entries = ArrayList<Chunk>(count)
            var next = 0L
            var previousLine = 1L
            repeat(count) {
                val entry = Chunk(input.readLong(), input.readLong(), input.readLong(),
                    input.readLong(), input.readUTF())
                if (entry.start != next || entry.end <= entry.start ||
                    entry.end - entry.start > CHUNK_BYTES + 3 || entry.end > source.bytes ||
                    entry.firstLine < previousLine || entry.lastLine < entry.firstLine ||
                    !entry.sha256.matches(Regex("[0-9a-f]{64}"))) throw IOException("Corrupt index entry")
                entries.add(entry)
                next = entry.end
                previousLine = entry.firstLine
            }
            if (next != source.bytes || input.read() != -1) throw IOException("Incomplete index")
            entries
        }
        // A whole-file hash alone does not authenticate the line numbers or
        // chunk boundaries stored in a sidecar. Reconstruct them from the same
        // bounded stream while checking each chunk digest and UTF-8 boundary.
        val digest = MessageDigest.getInstance("SHA-256")
        var line = 1L
        var lastNewline = false
        FileInputStream(source.file).buffered(CHUNK_BYTES).use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            chunks.forEachIndexed { index, chunk ->
                checkCancelled(cancelled)
                val part = MessageDigest.getInstance("SHA-256")
                val firstLine = line
                var remaining = (chunk.end - chunk.start).toInt()
                var firstRead = true
                while (remaining > 0) {
                    checkCancelled(cancelled)
                    val n = input.read(buffer, 0, minOf(remaining, buffer.size))
                    if (n <= 0) throw IOException("Source truncated while verifying index")
                    if (firstRead && index > 0 && (buffer[0].toInt() and 0xC0) == 0x80)
                        throw IOException("Index splits a UTF-8 code point")
                    firstRead = false
                    digest.update(buffer, 0, n)
                    part.update(buffer, 0, n)
                    for (i in 0 until n) {
                        if (buffer[i] == 10.toByte()) {
                            line++
                            lastNewline = true
                        } else lastNewline = false
                    }
                    remaining -= n
                }
                val lastLine = if (lastNewline) line - 1 else line
                if (chunk.firstLine != firstLine || chunk.lastLine != lastLine ||
                    hex(part) != chunk.sha256)
                    throw IOException("Index chunk provenance mismatch")
            }
            if (input.read() != -1) throw IOException("Unexpected source bytes after index")
        }
        if (hex(digest) != source.sha256) throw IOException("Source content changed")
        return Index(source, target, chunks)
    }

    /** Bounded excerpt with independent per-chunk SHA and strict UTF-8 decoding. */
    fun read(index: Index, number: Int): Excerpt {
        val chunk = index.chunks.getOrNull(number) ?: throw IndexOutOfBoundsException("No such chunk")
        val length = (chunk.end - chunk.start).toInt()
        if (length !in 1..CHUNK_BYTES + 3) throw IOException("Invalid chunk length")
        val bytes = ByteArray(length)
        RandomAccessFile(index.source.file, "r").use { file ->
            file.seek(chunk.start)
            file.readFully(bytes)
        }
        if (sha(bytes) != chunk.sha256) throw IOException("Source chunk changed")
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        return Excerpt(text, chunk.start, chunk.end, chunk.firstLine, chunk.lastLine)
    }
}
