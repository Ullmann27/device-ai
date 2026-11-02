package at.heinz.deviceai.cognitive

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom

/**
 * File store for [DevelopmentBacklog] with the same guarantees as BacklogStore
 * in `.github/continuum/development-backlog.mjs`:
 *
 * - cross-process lock file `<file>.lock` (exclusive create; a lock older than
 *   [staleLockMillis] is moved aside and broken, a fresh one is put back);
 * - read-modify-write with compare-and-swap: the file must still hold the bytes
 *   that were read, otherwise CONFLICT and nothing is written;
 * - atomic replace (synced temp file + rename); the previous version is kept as
 *   `<file>.bak`; interrupted writes leave only temp files that are cleaned up;
 * - a corrupt file is never deleted or silently reset: it is recovered from the
 *   backup and quarantined as `<file>.corrupt-*`, or loading fails.
 */
class DevelopmentBacklogStore(
    file: File,
    private val actor: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val staleLockMillis: Long = 60_000,
    private val waitMillis: Long = 10_000
) {
    val file: File = file.absoluteFile
    private val lockFile = File(this.file.path + ".lock")
    private val backupFile = File(this.file.path + ".bak")
    private val random = SecureRandom()

    data class Recovery(val from: String, val reason: String, val quarantined: String?)

    data class Loaded(
        val doc: Map<String, Any?>, val bytes: ByteArray?, val migratedFrom: Int?, val recovered: Recovery?, val exists: Boolean
    )

    data class UpdateResult(
        val doc: Map<String, Any?>, val written: Boolean, val recovered: Recovery?, val migratedFrom: Int?,
        val results: List<DevelopmentBacklog.OpReport> = emptyList()
    )

    /** Read with recovery but without modifying any file. */
    fun read(): Loaded = load(repair = false)

    private fun decodeFile(f: File): Pair<DevelopmentBacklog.Decoded, ByteArray> {
        val bytes = f.readBytes()
        return DevelopmentBacklog.decode(bytes, clock(), actor) to bytes
    }

    private fun tryBackup(): DevelopmentBacklog.Decoded? {
        if (!backupFile.exists()) return null
        return try { decodeFile(backupFile).first } catch (e: BacklogException) { null }
    }

    private fun load(repair: Boolean): Loaded {
        if (file.exists()) {
            try {
                val (decoded, bytes) = decodeFile(file)
                return Loaded(decoded.doc, bytes, decoded.migratedFrom, null, true)
            } catch (e: BacklogException) {
                if (e.code == "UNSUPPORTED_VERSION") throw e
                val backup = tryBackup() ?: throw BacklogException(e.code, "backlog unreadable and no valid backup: ${e.detail}")
                var quarantined: String? = null
                if (repair) {
                    val target = File(file.path + ".corrupt-" + clock() + "-" + hex(3))
                    Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    quarantined = target.path
                }
                return Loaded(backup.doc, null, backup.migratedFrom, Recovery("backup", "${e.code}: ${e.detail}", quarantined), true)
            }
        }
        val backup = tryBackup()
        if (backup != null) return Loaded(backup.doc, null, backup.migratedFrom, Recovery("backup", "backlog file missing", null), false)
        return Loaded(DevelopmentBacklog.emptyDocument(clock(), actor), null, null, null, false)
    }

    private fun hex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun writeSynced(target: File, bytes: ByteArray) {
        FileChannel.open(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun syncDirectory(dir: File) {
        try { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } } catch (e: IOException) {
            // not supported on every platform; the rename itself is atomic
        }
    }

    private fun holds(token: String): Boolean = try {
        lockFile.readText(Charsets.UTF_8).contains("\"token\":\"$token\"")
    } catch (e: IOException) {
        false
    }

    private fun acquire(): String {
        val token = DevelopmentBacklog.newToken()
        val body = (DevelopmentBacklog.canonical(linkedMapOf("owner" to actor, "token" to token, "at" to clock())) + "\n")
            .toByteArray(Charsets.UTF_8)
        val deadline = System.currentTimeMillis() + waitMillis
        var round = 0
        while (true) {
            try {
                writeSynced(lockFile, body)
                return token
            } catch (e: FileAlreadyExistsException) {
                // held by someone else
            }
            val modified = try { Files.getLastModifiedTime(lockFile.toPath()).toMillis() } catch (e: NoSuchFileException) { continue }
            if (System.currentTimeMillis() - modified > staleLockMillis) {
                // Move the stale lock aside under a unique name; if what was moved is
                // in fact fresh (taken over in between), put it back.
                val aside = File(lockFile.path + ".stale-" + token)
                try { Files.move(lockFile.toPath(), aside.toPath(), StandardCopyOption.ATOMIC_MOVE) } catch (e: NoSuchFileException) { continue }
                if (System.currentTimeMillis() - Files.getLastModifiedTime(aside.toPath()).toMillis() <= staleLockMillis) {
                    try { Files.createLink(lockFile.toPath(), aside.toPath()) } catch (e: Exception) { /* a new holder exists */ }
                }
                aside.delete()
                continue
            }
            if (System.currentTimeMillis() > deadline) throw BacklogException("LOCK_TIMEOUT", "backlog is locked by another process: $lockFile")
            Thread.sleep(minOf(200L, 20L + round++ * 10L))
        }
    }

    private fun release(token: String) { if (holds(token)) lockFile.delete() }

    private fun cleanupInterrupted() {
        val dir = file.parentFile ?: return
        val base = file.name
        for (f in dir.listFiles().orEmpty()) {
            if (!f.name.startsWith("$base.tmp-") && !f.name.startsWith("$base.bak.tmp-")) continue
            if (System.currentTimeMillis() - f.lastModified() > staleLockMillis) f.delete()
        }
    }

    /**
     * Read-modify-write under the cross-process lock. CONFLICT when
     * [expectedRevision] differs or the file changed while the lock was held
     * (lock broken as stale); then nothing is written.
     */
    @Synchronized
    fun update(expectedRevision: Long? = null, mutate: (Map<String, Any?>) -> Map<String, Any?>): UpdateResult {
        file.parentFile?.mkdirs()
        val token = acquire()
        try {
            cleanupInterrupted()
            val current = load(repair = true)
            if (expectedRevision != null && current.doc["revision"] != expectedRevision) {
                throw BacklogException("CONFLICT", "expected revision $expectedRevision, found ${current.doc["revision"]}")
            }
            val next = mutate(current.doc)
            val bytes = DevelopmentBacklog.encode(next)
            // a migrated, recovered or new backlog is always written; otherwise only real changes
            val fresh = current.migratedFrom != null || current.recovered != null || !current.exists
            val same = current.bytes != null && bytes.contentEquals(current.bytes)
            if (same && !fresh) return UpdateResult(current.doc, false, null, null)
            if (!fresh && (next["revision"] as Long) <= (current.doc["revision"] as Long)) {
                throw BacklogException("CONFLICT", "a changed document must have a higher revision")
            }
            val tmp = File(file.path + ".tmp-" + hex(8))
            writeSynced(tmp, bytes)
            try {
                if (!holds(token)) throw BacklogException("LOCK_LOST", "the lock was taken over; nothing written")
                // compare-and-swap: the file must still be what was read
                val now = if (file.exists()) file.readBytes() else null
                if (current.bytes != null && (now == null || !now.contentEquals(current.bytes))) {
                    throw BacklogException("CONFLICT", "backlog changed concurrently; nothing written")
                }
                if (current.bytes == null && now != null && current.recovered == null && !current.exists) {
                    throw BacklogException("CONFLICT", "backlog was created concurrently; nothing written")
                }
                val v1Copy = File(file.path + ".v1.json")
                if (current.migratedFrom == 1 && now != null && !v1Copy.exists()) Files.copy(file.toPath(), v1Copy.toPath())
                if (now != null && current.recovered == null) {
                    val bakTmp = File(file.path + ".bak.tmp-" + hex(8))
                    writeSynced(bakTmp, now)
                    Files.move(bakTmp.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                file.parentFile?.let(::syncDirectory)
            } finally {
                tmp.delete()
            }
            val written = DevelopmentBacklog.decode(file.readBytes()).doc
            return UpdateResult(written, true, current.recovered, current.migratedFrom)
        } finally {
            release(token)
        }
    }

    /** Apply operations atomically ([stopOnError]) or leniently with per-operation reports. */
    fun apply(ops: List<Map<String, Any?>>, expectedRevision: Long? = null, stopOnError: Boolean = true): UpdateResult {
        var reports: List<DevelopmentBacklog.OpReport> = emptyList()
        val result = update(expectedRevision) { doc ->
            val (next, r) = DevelopmentBacklog.applyOps(doc, ops, stopOnError)
            reports = r
            next
        }
        return result.copy(results = reports)
    }
}

