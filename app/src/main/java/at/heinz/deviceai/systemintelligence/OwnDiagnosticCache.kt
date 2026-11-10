package at.heinz.deviceai.systemintelligence

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.SecureDirectoryStream
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

data class DiagnosticCacheRecord(
    val observedAtEpochMs: Long,
    val availableStorageBytes: Long?,
    val appPssBytes: Long?,
    val batteryPercent: Int?,
    val thermalStatus: Int?
) {
    init {
        require(observedAtEpochMs >= 0)
        require(availableStorageBytes == null || availableStorageBytes >= 0)
        require(appPssBytes == null || appPssBytes >= 0)
        require(batteryPercent == null || batteryPercent in 0..100)
        require(thermalStatus == null || thermalStatus in 0..6)
    }
}

data class OwnCacheEntry(val id: String, val bytes: Long, val observedAtEpochMs: Long)
data class OwnCachePreview(
    val previewId: String,
    val planDigest: String,
    val entries: List<OwnCacheEntry>,
    val eligibleBytes: Long,
    val protectedFileCount: Int,
    val complete: Boolean
) {
    val protectedEntryCount: Int get() = protectedFileCount
}
data class OwnCacheCleanupResult(
    val status: String,
    val removedFiles: Int = 0,
    val logicalBytesRemoved: Long = 0,
    val availableBytesDelta: Long? = null,
    val undoId: String? = null
)

class OwnDiagnosticCache(
    cacheDirectory: File,
    ownershipDirectory: File,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class Owned(val id: String, val digest: String, val bytes: Int)
    private data class Identity(val key: Any, val modifiedMs: Long, val bytes: Long)
    private data class Pending(val preview: OwnCachePreview, val entries: List<Owned>,
        val identities: Map<String, Identity>, val expires: Long)
    private data class Undo(val id: String, val files: Map<Owned, ByteArray>, val expires: Long)
    private val root = cacheDirectory.canonicalFile.toPath()
    private val ownershipRoot = ownershipDirectory.canonicalFile.toPath()
    private val directory = root.resolve("system-diagnostics")
    private val ledger = ownershipRoot.resolve("system-diagnostic-cache-v1.index")
    private var pending: Pending? = null
    private var undo: Undo? = null

    @Synchronized
    fun history(): List<DiagnosticCacheRecord> = readLedger().mapNotNull { verified(it)?.let(::decode) }
        .sortedByDescending { it.observedAtEpochMs }

    @Synchronized
    fun retain(record: DiagnosticCacheRecord): String {
        checkRoot(root)
        checkRoot(ownershipRoot)
        if (!Files.exists(directory, NOFOLLOW_LINKS)) Files.createDirectory(directory)
        checkDirectory()
        val owned = readLedger().filter { Files.exists(path(it.id), NOFOLLOW_LINKS) }
        check(owned.size < MAX_RECORDS) { "DIAGNOSTIC_HISTORY_FULL" }
        val id = UUID.randomUUID().toString().replace("-", "")
        val bytes = encode(record)
        Files.write(path(id), bytes, CREATE_NEW, WRITE)
        val new = Owned(id, sha(bytes), bytes.size)
        try {
            writeLedger(owned + new)
        } catch (failure: Exception) {
            throw IllegalStateException("DIAGNOSTIC_HISTORY_INDEX_FAILED", failure)
        }
        pending = null
        return id
    }

    @Synchronized
    fun preview(cancelled: () -> Boolean = { false }): OwnCachePreview {
        pending = null
        val start = monotonicMs()
        fun stopped(): Boolean {
            val elapsed = monotonicMs() - start
            return cancelled() || Thread.currentThread().isInterrupted || elapsed < 0 || elapsed >= WORK_BUDGET_MS
        }
        checkRoot(root)
        checkRoot(ownershipRoot)
        val entries = mutableListOf<OwnCacheEntry>()
        val accepted = mutableListOf<Owned>()
        val identities = mutableMapOf<String, Identity>()
        var complete = true
        val owned = readLedger()
        for (entry in owned) {
            if (stopped()) {
                complete = false
                break
            }
            if (!Files.exists(path(entry.id), NOFOLLOW_LINKS)) continue
            val before = identity(entry)
            val bytes = verified(entry)
            val record = bytes?.let(::decode)
            if (record == null || before == null || identity(entry) != before) complete = false
            else {
                accepted += entry
                identities[entry.id] = before
                entries += OwnCacheEntry(entry.id, entry.bytes.toLong(), record.observedAtEpochMs)
            }
        }
        var protected = 0
        if (Files.exists(directory, NOFOLLOW_LINKS)) {
            checkDirectory()
            Files.newDirectoryStream(directory).use { stream ->
                val iterator = stream.iterator()
                var observed = 0
                while (iterator.hasNext() && observed < MAX_INVENTORY) {
                    if (stopped()) {
                        complete = false
                        break
                    }
                    val name = iterator.next().fileName.toString()
                    observed++
                    if (accepted.none { "summary-${it.id}.cdiag" == name }) protected++
                }
                if (iterator.hasNext()) complete = false
            }
        }
        Files.newDirectoryStream(root).use { stream ->
            val iterator = stream.iterator()
            var observed = 0
            while (iterator.hasNext() && observed < MAX_INVENTORY) {
                if (stopped()) {
                    complete = false
                    break
                }
                val child = iterator.next()
                observed++
                if (child != directory) protected++
            }
            if (iterator.hasNext()) complete = false
        }
        val id = UUID.randomUUID().toString()
        val planDigest = sha(accepted.joinToString("\n") { "${it.id}|${it.digest}|${it.bytes}" }
            .toByteArray(Charsets.US_ASCII))
        val preview = OwnCachePreview(id, planDigest, entries.toList(),
            entries.sumOf { it.bytes }, protected, complete)
        if (complete) pending = Pending(preview, accepted.toList(), identities.toMap(), monotonicMs() + PREVIEW_TTL_MS)
        return preview
    }

    @Synchronized
    fun cleanup(previewId: String, planDigest: String, cancelled: () -> Boolean = { false }): OwnCacheCleanupResult {
        val plan = pending ?: return OwnCacheCleanupResult("PREVIEW_REQUIRED")
        pending = null
        val now = monotonicMs()
        if (plan.preview.previewId != previewId || plan.preview.planDigest != planDigest)
            return OwnCacheCleanupResult("PREVIEW_MISMATCH")
        if (now < plan.expires - PREVIEW_TTL_MS || now >= plan.expires)
            return OwnCacheCleanupResult("PREVIEW_EXPIRED")
        if (cancelled() || Thread.currentThread().isInterrupted) return OwnCacheCleanupResult("CANCELLED")
        if (plan.entries.isEmpty()) return OwnCacheCleanupResult("NO_AUDITED_DISPOSABLE_FILES")
        val current = readLedger()
        if (plan.entries.any { it !in current }) return OwnCacheCleanupResult("STALE_PREVIEW")
        val backups = linkedMapOf<Owned, ByteArray>()
        for (entry in plan.entries) {
            if (cancelled() || Thread.currentThread().isInterrupted) return OwnCacheCleanupResult("CANCELLED")
            if (monotonicMs() - now !in 0 until WORK_BUDGET_MS) return OwnCacheCleanupResult("TIME_LIMIT")
            if (identity(entry) != plan.identities[entry.id]) return OwnCacheCleanupResult("STALE_PREVIEW")
            val bytes = verified(entry) ?: return OwnCacheCleanupResult("STALE_PREVIEW")
            backups[entry] = bytes
        }
        val before = usableBytes()
        val removed = linkedMapOf<Owned, ByteArray>()
        var status = "COMPLETED"
        for ((entry, bytes) in backups) {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                status = "CANCELLED_PARTIAL"
                break
            }
            if (monotonicMs() - now !in 0 until WORK_BUDGET_MS) {
                status = "TIME_LIMIT_PARTIAL"
                break
            }
            if (verified(entry) == null || identity(entry) != plan.identities[entry.id]) {
                status = "STALE_PARTIAL"
                break
            }
            try {
                if (!secureDelete(entry, plan.identities.getValue(entry.id))) {
                    status = "SECURE_DELETE_UNAVAILABLE"
                    break
                }
                removed[entry] = bytes
            } catch (failure: Exception) {
                status = if (failure.message == "CACHE_QUARANTINED_REVIEW_REQUIRED")
                    "QUARANTINED_REVIEW_REQUIRED" else "FAILED_PARTIAL"
                break
            }
        }
        val token = if (removed.isEmpty()) null else UUID.randomUUID().toString()
        if (token != null) undo = Undo(token, removed.toMap(), monotonicMs() + UNDO_TTL_MS)
        try {
            writeLedger(current.filterNot { it in removed })
        } catch (_: Exception) {
            status = "INDEX_UPDATE_FAILED"
        }
        val after = usableBytes()
        return OwnCacheCleanupResult(status, removed.size, removed.keys.sumOf { it.bytes.toLong() },
            if (before != null && after != null) after - before else null, token)
    }

    @Synchronized
    fun restore(undoId: String): OwnCacheCleanupResult {
        val backup = undo ?: return OwnCacheCleanupResult("UNDO_UNAVAILABLE")
        if (backup.id != undoId) return OwnCacheCleanupResult("UNDO_MISMATCH")
        val now = monotonicMs()
        if (now < backup.expires - UNDO_TTL_MS || now >= backup.expires) {
            undo = null
            return OwnCacheCleanupResult("UNDO_EXPIRED")
        }
        checkDirectory()
        val current = readLedger()
        if ((current + backup.files.keys).distinctBy { it.id }.size > MAX_RECORDS)
            return OwnCacheCleanupResult("HISTORY_FULL")
        if (backup.files.keys.any { Files.exists(path(it.id), NOFOLLOW_LINKS) })
            return OwnCacheCleanupResult("RESTORE_TARGET_EXISTS")
        val restored = mutableListOf<Owned>()
        for ((entry, bytes) in backup.files) {
            try {
                checkDirectory()
                Files.write(path(entry.id), bytes, CREATE_NEW, WRITE)
                restored += entry
            } catch (_: Exception) {
                break
            }
        }
        writeLedger((current + restored).distinctBy { it.id })
        val left = backup.files.filterKeys { it !in restored }
        undo = if (left.isEmpty()) null else backup.copy(files = left)
        pending = null
        return OwnCacheCleanupResult(if (left.isEmpty()) "RESTORED" else "RESTORE_PARTIAL")
    }

    private fun verified(entry: Owned): ByteArray? = try {
        checkDirectory()
        val file = path(entry.id)
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || Files.size(file) != entry.bytes.toLong()) null
        else Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
            val bytes = readBounded(input, MAX_RECORD_BYTES)
            bytes.takeIf { it.size == entry.bytes && sha(it) == entry.digest && decode(it) != null }
        }
    } catch (_: Exception) { null }

    private fun readLedger(): List<Owned> {
        checkRoot(ownershipRoot)
        if (!Files.exists(ledger, NOFOLLOW_LINKS)) return emptyList()
        check(Files.isRegularFile(ledger, NOFOLLOW_LINKS) && Files.size(ledger) <= 8_192) { "INVALID_CACHE_INDEX" }
        val text = Files.newInputStream(ledger, NOFOLLOW_LINKS).use { String(readBounded(it, 8_192), Charsets.US_ASCII) }
        val rows = text.lines().filter { it.isNotEmpty() }
        check(rows.size <= MAX_RECORDS) { "INVALID_CACHE_INDEX" }
        val parsed = rows.map { row ->
            val parts = row.split('|')
            check(parts.size == 3 && parts[0].matches(ID) && parts[1].matches(HASH)) { "INVALID_CACHE_INDEX" }
            val bytes = parts[2].toIntOrNull()
            check(bytes != null && bytes in 1..MAX_RECORD_BYTES) { "INVALID_CACHE_INDEX" }
            Owned(parts[0], parts[1], bytes)
        }
        check(parsed.distinctBy { it.id }.size == parsed.size) { "INVALID_CACHE_INDEX" }
        return parsed
    }

    private fun writeLedger(entries: List<Owned>) {
        checkRoot(ownershipRoot)
        require(entries.size <= MAX_RECORDS)
        check(!Files.isSymbolicLink(ledger)) { "INVALID_CACHE_INDEX" }
        val temporary = ownershipRoot.resolve(".system-diagnostic-${UUID.randomUUID()}.tmp")
        try {
            Files.write(temporary, entries.joinToString("\n", postfix = "\n") {
                "${it.id}|${it.digest}|${it.bytes}"
            }.toByteArray(Charsets.US_ASCII), CREATE_NEW, WRITE)
            Files.move(temporary, ledger, ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun checkRoot(path: Path) {
        check(Files.isDirectory(path, NOFOLLOW_LINKS) && path.toFile().canonicalFile.toPath() == path) {
            "INVALID_CACHE_ROOT"
        }
    }
    private fun identity(entry: Owned): Identity? = try {
        checkDirectory()
        val attrs = Files.readAttributes(path(entry.id), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (!attrs.isRegularFile || attrs.fileKey() == null) null
        else Identity(attrs.fileKey(), attrs.lastModifiedTime().toMillis(), attrs.size())
    } catch (_: Exception) { null }

    private fun secureDelete(entry: Owned, expected: Identity): Boolean {
        checkDirectory()
        Files.newDirectoryStream(root).use { opened ->
            val anchoredRoot = opened as? SecureDirectoryStream<Path> ?: return false
            anchoredRoot.newDirectoryStream(Paths.get("system-diagnostics"), NOFOLLOW_LINKS).use { anchored ->
                val original = Paths.get("summary-${entry.id}.cdiag")
                val held = Paths.get(".cleanup-${UUID.randomUUID()}")
                anchored.move(original, anchored, held)
                try {
                    val attrs = anchored.getFileAttributeView(held,
                        java.nio.file.attribute.BasicFileAttributeView::class.java, NOFOLLOW_LINKS).readAttributes()
                    val observed = attrs.fileKey()?.let { Identity(it, attrs.lastModifiedTime().toMillis(), attrs.size()) }
                    check(attrs.isRegularFile && observed == expected) { "CACHE_REPLACED" }
                    val bytes = anchored.newByteChannel(held, setOf(java.nio.file.StandardOpenOption.READ, NOFOLLOW_LINKS))
                        .use { channel -> java.nio.channels.Channels.newInputStream(channel).use { readBounded(it, MAX_RECORD_BYTES) } }
                    check(bytes.size == entry.bytes && sha(bytes) == entry.digest && decode(bytes) != null) { "CACHE_CHANGED" }
                    anchored.deleteFile(held)
                } catch (failure: Exception) {
                    throw IllegalStateException("CACHE_QUARANTINED_REVIEW_REQUIRED", failure)
                }
            }
        }
        return true
    }
    private fun checkDirectory() {
        checkRoot(root)
        check(Files.isDirectory(directory, NOFOLLOW_LINKS) && directory.toFile().canonicalFile.toPath() == directory) {
            "INVALID_CACHE_DIRECTORY"
        }
    }
    private fun path(id: String): Path {
        require(id.matches(ID))
        return directory.resolve("summary-$id.cdiag")
    }
    private fun usableBytes(): Long? = root.toFile().usableSpace.takeIf { it > 0 }

    companion object {
        const val MAX_RECORDS = 32
        const val MAX_RECORD_BYTES = 1_024
        const val PREVIEW_TTL_MS = 120_000L
        const val UNDO_TTL_MS = 600_000L
        private const val MAX_INVENTORY = 512
        private const val WORK_BUDGET_MS = 5_000L
        private val ID = Regex("[0-9a-f]{32}")
        private val HASH = Regex("[0-9a-f]{64}")
        private const val HEADER = "CONTINUUM_DIAGNOSTIC_SUMMARY_V1"
        private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(minOf(limit + 1, 1_024))
            while (output.size() <= limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                if (count < 0) break
                check(count > 0) { "CACHE_READ_STALLED" }
                output.write(buffer, 0, count)
            }
            check(output.size() <= limit) { "CACHE_READ_LIMIT" }
            return output.toByteArray()
        }
        private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        private fun encode(record: DiagnosticCacheRecord): ByteArray = listOf(HEADER,
            record.observedAtEpochMs, record.availableStorageBytes ?: "-", record.appPssBytes ?: "-",
            record.batteryPercent ?: "-", record.thermalStatus ?: "-")
            .joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII)
        private fun decode(bytes: ByteArray): DiagnosticCacheRecord? = try {
            if (bytes.size > MAX_RECORD_BYTES) null else {
                val lines = String(bytes, Charsets.US_ASCII).split('\n')
                check(lines.size == 7 && lines[0] == HEADER && lines[6].isEmpty())
                fun long(index: Int): Long? = if (lines[index] == "-") null else lines[index].toLong()
                fun int(index: Int): Int? = if (lines[index] == "-") null else lines[index].toInt()
                val record = DiagnosticCacheRecord(lines[1].toLong(), long(2), long(3), int(4), int(5))
                record.takeIf { encode(it).contentEquals(bytes) }
            }
        } catch (_: Exception) { null }
    }
}
