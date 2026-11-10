package at.heinz.deviceai.systemintelligence

import java.io.InputStream
import java.security.MessageDigest

data class StorageScanLimits(
    val maxEntries: Int = 512,
    val maxDepth: Int = 12,
    val maxHashBytes: Long = 128L * 1024 * 1024,
    val maxFileHashBytes: Long = 32L * 1024 * 1024,
    val maxDurationMs: Long = 8_000
) {
    init {
        require(maxEntries in 1..2_000 && maxDepth in 1..24)
        require(maxHashBytes in 1..512L * 1024 * 1024)
        require(maxFileHashBytes in 1..maxHashBytes)
        require(maxDurationMs in 1..30_000)
    }
}

data class StorageDocument(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedEpochMs: Long?,
    val directory: Boolean = false,
    val virtual: Boolean = false,
    val stableKey: String = id
)

interface StorageDocumentSource {
    fun children(parentId: String, limit: Int): List<StorageDocument>
    fun metadata(id: String): StorageDocument?
    fun open(id: String): InputStream
}

data class StorageFileObservation(
    val document: StorageDocument,
    val contentSha256: String?
)

data class StorageDuplicateGroup(val documentIds: List<String>, val bytesPerFile: Long)

data class StorageAnalysis(
    val files: List<StorageFileObservation>,
    val duplicates: List<StorageDuplicateGroup>,
    val scannedBytes: Long,
    val hashedBytes: Long,
    val complete: Boolean,
    val limitations: Set<String>
) {
    fun summaryForTool(): String = buildString {
        append("Speicheranalyse: ${files.size} Dateien, $scannedBytes erfasste Byte; ")
        append("${duplicates.size} inhaltlich geprüfte Duplikatgruppen. ")
        append("$hashedBytes Byte vollständig oder teilweise gelesen. ")
        append(if (complete) "Analyse im gewählten Bereich vollständig." else "Analyse begrenzt/unvollständig.")
        append(" Keine Dateien gelöscht. Dateinamen und Inhalte bleiben in der lokalen Ansicht.")
        if (limitations.isNotEmpty()) append(" Grenzen: ${limitations.sorted().joinToString()}.")
    }
}

class StorageContentAnalyzer(
    private val limits: StorageScanLimits = StorageScanLimits(),
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    fun analyze(
        source: StorageDocumentSource,
        selected: List<StorageDocument>,
        cancelled: () -> Boolean = { false }
    ): StorageAnalysis {
        val start = monotonicMs()
        val limitations = linkedSetOf<String>()
        val files = mutableListOf<StorageDocument>()
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<StorageDocument, Int>>()
        var visited = 0
        var hashedBytes = 0L
        var scannedBytes = 0L
        fun allowed(): Boolean {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                limitations += "CANCELLED"
                return false
            }
            val elapsed = monotonicMs() - start
            if (elapsed < 0 || elapsed >= limits.maxDurationMs) {
                limitations += "TIME_LIMIT"
                return false
            }
            return true
        }
        allowed()
        selected.take(limits.maxEntries).forEach { queue.addLast(it to 0) }
        if (selected.size > limits.maxEntries) limitations += "ENTRY_LIMIT"
        while (queue.isNotEmpty() && allowed()) {
            if (visited >= limits.maxEntries) {
                limitations += "ENTRY_LIMIT"
                break
            }
            val (document, depth) = queue.removeFirst()
            if (!seen.add(document.stableKey)) {
                limitations += "REPEATED_DOCUMENT"
                continue
            }
            visited++
            if (document.directory) {
                if (depth >= limits.maxDepth) {
                    limitations += "DEPTH_LIMIT"
                    continue
                }
                val remaining = limits.maxEntries - visited - queue.size
                if (remaining <= 0) {
                    limitations += "ENTRY_LIMIT"
                    continue
                }
                try {
                    val children = source.children(document.id, remaining + 1)
                    if (children.size > remaining) limitations += "ENTRY_LIMIT"
                    children.take(remaining).forEach { queue.addLast(it to depth + 1) }
                } catch (_: Exception) {
                    limitations += "DIRECTORY_UNAVAILABLE"
                }
            } else {
                files += document
                val size = document.sizeBytes
                if (size == null || size < 0) limitations += "SIZE_UNAVAILABLE"
                else if (Long.MAX_VALUE - scannedBytes < size) limitations += "SIZE_OVERFLOW"
                else scannedBytes += size
            }
        }
        val matchingSizes = files.mapNotNull { it.sizeBytes?.takeIf { size -> size > 0 } }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val observations = files.map { document ->
            var digest: String? = null
            val size = document.sizeBytes
            if (size in matchingSizes) {
                if (document.virtual) limitations += "VIRTUAL_DOCUMENT"
                else if (size == null || size > limits.maxFileHashBytes || size >= limits.maxHashBytes - hashedBytes) {
                    limitations += "HASH_BYTE_LIMIT"
                } else if (allowed()) {
                    try {
                        val before = source.metadata(document.id)
                        check(before == document) { "STALE_DOCUMENT" }
                        val sha = MessageDigest.getInstance("SHA-256")
                        var readBytes = 0L
                        source.open(document.id).use { input ->
                            val buffer = ByteArray(32 * 1024)
                            while (allowed()) {
                                val remaining = minOf(size - readBytes, limits.maxHashBytes - hashedBytes)
                                if (remaining <= 0) break
                                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (count < 0) break
                                check(count > 0) { "STALLED_DOCUMENT" }
                                readBytes += count
                                hashedBytes += count
                                sha.update(buffer, 0, count)
                            }
                            check(allowed() && readBytes == size) { "INCOMPLETE_DOCUMENT" }
                            check(hashedBytes < limits.maxHashBytes) { "HASH_LIMIT_NO_EOF" }
                            val extra = input.read()
                            if (extra >= 0) hashedBytes++
                            check(extra < 0) { "CHANGED_DOCUMENT" }
                        }
                        check(source.metadata(document.id) == before) { "STALE_DOCUMENT" }
                        check(allowed()) { "EXPIRED_DOCUMENT" }
                        digest = sha.digest().joinToString("") { "%02x".format(it) }
                    } catch (_: Exception) {
                        limitations += "CONTENT_UNVERIFIED"
                    }
                }
            }
            StorageFileObservation(document, digest)
        }
        val duplicates = observations.filter { it.contentSha256 != null }
            .groupBy { it.document.sizeBytes to it.contentSha256 }
            .values.filter { it.size > 1 }.map { group ->
                StorageDuplicateGroup(group.map { it.document.id }, group.first().document.sizeBytes!!)
            }
        return StorageAnalysis(observations, duplicates, scannedBytes, hashedBytes,
            limitations.isEmpty(), limitations.toSet())
    }
}
