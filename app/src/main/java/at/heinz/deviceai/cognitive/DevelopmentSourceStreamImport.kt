package at.heinz.deviceai.cognitive

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID

/**
 * Disk-backed, bounded first stage for owner-selected UTF-8 development sources.
 * It never constructs a SourceSnapshot/DevelopmentTask, invokes a model or exports content.
 * Caller must supply an app-private directory and run this blocking operation off the UI thread.
 */
object DevelopmentSourceStreamImport {
    const val MAX_SOURCE_BYTES: Long = 100L * 1024L * 1024L
    private const val CHUNK_BYTES = 64 * 1024
    private val extensions = setOf("kt", "java", "js", "mjs", "ts", "tsx", "jsx", "json", "md", "txt", "xml", "py", "c", "h", "cpp", "hpp", "rs", "go", "css", "html", "sh")
    private val forbiddenNameParts = listOf("backup", "restore", "archive", "checkpoint", "wallet", "credential", "token", "password", "private", "secret")

    data class ImportedSource(val sourcePath: String, val file: File, val bytes: Long, val sha256: String)

    /**
     * Streams to a private temporary file, validates UTF-8 without materializing text, then
     * renames to a random private filename. A failure removes only its own temporary file.
     * The returned file is NOT yet indexed and must never be passed wholesale to a model.
     */
    fun importUtf8(
        input: InputStream,
        sourcePath: String,
        privateDirectory: File,
        maxBytes: Long = MAX_SOURCE_BYTES,
        cancelled: () -> Boolean = { false },
        onProgress: (Long) -> Unit = {}
    ): ImportedSource {
        require(maxBytes in 1..MAX_SOURCE_BYTES) { "Invalid per-file budget" }
        require(privateDirectory.isDirectory) { "Import destination must be an existing app-private directory" }
        DevelopmentPathPolicy.validate(sourcePath)?.let { throw IllegalArgumentException("Source path rejected: $it") }
        val name = sourcePath.substringAfterLast('/').lowercase(java.util.Locale.ROOT)
        require(name.substringAfterLast('.', "") in extensions && forbiddenNameParts.none { it in name }) {
            "Only non-sensitive, supported UTF-8 source files are accepted"
        }
        val temporary = File.createTempFile(".development-import-", ".part", privateDirectory)
        var committed = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(CHUNK_BYTES)
                    var emptyReads = 0
                    while (true) {
                        if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Import cancelled")
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) {
                            if (++emptyReads > 8) throw IOException("Input stream made no progress")
                            continue
                        }
                        emptyReads = 0
                        if (count.toLong() > maxBytes - total) throw IOException("Source exceeds per-file byte limit")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        total += count
                        onProgress(total)
                    }
                    if (total == 0L) throw IOException("Empty source")
                    output.fd.sync()
                }
            }
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            InputStreamReader(FileInputStream(temporary), decoder).use { reader ->
                val chars = CharArray(8192)
                while (true) {
                    if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Import cancelled")
                    val n = reader.read(chars)
                    if (n < 0) break
                    for (i in 0 until n) if (chars[i] == '\u0000') throw IOException("NUL in text source")
                }
            }
            val finalFile = File(privateDirectory, "source-${UUID.randomUUID()}.utf8")
            if (!temporary.renameTo(finalFile)) throw IOException("Atomic local import commit failed")
            committed = true
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return ImportedSource(sourcePath, finalFile, total, hash)
        } finally {
            if (!committed) temporary.delete()
        }
    }
}
