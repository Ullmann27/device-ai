package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumLearningCore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import java.nio.file.AtomicMoveNotSupportedException

/**
 * Android-first supervisor for the SAME shared development backlog that the
 * Node Teamleader consumes. It does actual local integrity checking and lease
 * recovery; it never claims that Android can run Node, Git or Gradle by itself.
 *
 * No paid API, network access, user content export or code write is performed.
 */
data class DeviceTeamleaderSnapshot(
    val checkedAt: Long,
    val state: String,
    val recoveredClaims: Int,
    val readyTasks: Int,
    val failedTasks: Int,
    val swb1Updates: Int,
    val cre1Steps: Int,
    val nextTaskId: String?,
    val reason: String
) {
    fun display(): String = buildString {
        append("Continuum Teamleiter · ").append(state)
        append("\nLetzte Prüfung: ").append(checkedAt)
        append("\nUnterbrochene Aufgaben wieder freigegeben: ").append(recoveredClaims)
        append("\nBereit im gemeinsamen Backlog: ").append(readyTasks)
        append(" · Fehlgeschlagen: ").append(failedTasks)
        append("\nSWB1-Trainingsschritte: ").append(swb1Updates)
        append(" · CRE1-Forschungsstand: ").append(cre1Steps)
        if (nextTaskId != null) {
            append("\nNächste Aufgabe: ").append(nextTaskId)
            append(" · Ausführung erfordert geeignete lokale Werkzeuge")
        }
        if (reason != "OK") append("\nHinweis: ").append(reason)
        append("\nAndroid prüft und verwaltet den Backlog; Git-Builds und Patches " +
            "werden nur von einem entsprechend ausgerüsteten Worker ausgeführt.")
    }
}

/** Private, checksummed, bounded status; no task titles or personal memories. */
class DeviceTeamleaderStatusStore(private val base: File) {
    private val path = File(base, "development/device-teamleader-v1.txt")
    fun save(s: DeviceTeamleaderSnapshot) {
        // Never overwrite a damaged previous status: it may contain the only
        // evidence of an interrupted run. Require an explicit recovery step.
        // The supervisor's process-wide CYCLE_LOCK serializes normal callers.
        if (path.exists()) read()
        require(s.state in setOf("READY", "DEFERRED", "ERROR"))
        require(s.reason in setOf("OK", "CANCELLED", "BATTERY_LOW", "THERMAL_LIMIT",
            "STORAGE_LOW", "CHECK_FAILED", "TIME_BUDGET", "WORKER_BUSY"))
        require(s.checkedAt >= 0 && s.recoveredClaims >= 0 && s.readyTasks >= 0 &&
            s.failedTasks >= 0 && s.swb1Updates >= 0 && s.cre1Steps >= 0)
        require(s.nextTaskId == null || Regex("[a-z][a-z0-9-]{2,63}").matches(s.nextTaskId))
        val body = listOf("device-teamleader-v1", s.checkedAt, s.state, s.recoveredClaims,
            s.readyTasks, s.failedTasks, s.swb1Updates, s.cre1Steps,
            s.nextTaskId ?: "-", s.reason).joinToString("\t")
        val bytes = (body + "\n" + sha(body) + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= 512)
        val folder = path.parentFile!!
        require(folder.isDirectory || folder.mkdirs())
        val pending = File.createTempFile("teamleader-pending-", ".tmp", folder)
        try {
            pending.writeBytes(bytes)
            // Never publish a corrupt status, even on an interrupted write.
            val checked = parse(pending.readBytes())
            require(checked == s)
            try {
                Files.move(pending.toPath(), path.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(pending.toPath(), path.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { pending.delete() }
    }

    fun read(): DeviceTeamleaderSnapshot? {
        if (!path.exists()) return null
        require(path.length() in 1..512) { "Invalid teamleader status size" }
        return parse(path.readBytes())
    }

    private fun parse(data: ByteArray): DeviceTeamleaderSnapshot {
        require(data.size in 1..512)
        val text = data.toString(Charsets.UTF_8)
        val lines = text.split('\n')
        require(lines.size == 3 && lines[2].isEmpty())
        require(MessageDigest.isEqual(
            sha(lines[0]).toByteArray(Charsets.US_ASCII),
            lines[1].toByteArray(Charsets.US_ASCII))) { "Teamleader status checksum mismatch" }
        val cells = lines[0].split('\t')
        require(cells.size == 10 && cells[0] == "device-teamleader-v1")
        val snapshot = DeviceTeamleaderSnapshot(
            cells[1].toLong(), cells[2], cells[3].toInt(),
            cells[4].toInt(), cells[5].toInt(), cells[6].toInt(),
            cells[7].toInt(), cells[8].takeUnless { it == "-" }, cells[9])
        require(snapshot.state in setOf("READY", "DEFERRED", "ERROR"))
        require(snapshot.reason in setOf("OK", "CANCELLED", "BATTERY_LOW", "THERMAL_LIMIT",
            "STORAGE_LOW", "CHECK_FAILED", "TIME_BUDGET", "WORKER_BUSY"))
        require(snapshot.checkedAt >= 0 && snapshot.recoveredClaims >= 0 &&
            snapshot.readyTasks >= 0 && snapshot.failedTasks >= 0 &&
            snapshot.swb1Updates >= 0 && snapshot.cre1Steps >= 0)
        require(snapshot.nextTaskId == null ||
            Regex("[a-z][a-z0-9-]{2,63}").matches(snapshot.nextTaskId))
        return snapshot
    }

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

class DeviceTeamleader(
    private val filesDir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val dnaAudit: (() -> DnaIntegrityAssessment)? = null
) {
    private val storage = DeviceTeamleaderStatusStore(filesDir)
    fun status(): DeviceTeamleaderSnapshot? = storage.read()

    /** The device acts autonomously on safe housekeeping; it never marks
     * development tasks DONE or executes unverified code.
     */
    fun runOnce(cancelled: () -> Boolean = { false }): DeviceTeamleaderSnapshot {
        // Coordinate both JobService and chat calls within this process.
        // BUSY must never overwrite the active worker's durable state.
        if (!CYCLE_LOCK.tryLock()) return DeviceTeamleaderSnapshot(now(), "DEFERRED",
            0, 0, 0, 0, 0, null, "WORKER_BUSY")
        fun check() { if (cancelled() || Thread.currentThread().isInterrupted)
            throw InterruptedException("Device teamleader stopped") }
        try {
            // A damaged signed status is forensic evidence. Preserve it instead
            // of hiding corruption by publishing a freshly minted clean report.
            try {
                storage.read()
            } catch (_: Exception) {
                return DeviceTeamleaderSnapshot(now(), "ERROR",
                    0, 0, 0, 0, 0, null, "CHECK_FAILED")
            }
            check()
            // The Android JobService supplies the signed-DNA auditor. If stored
            // identity data fail verification, never process or recover backlog
            // tasks in this cycle. Leave the damaged files untouched.
            if (dnaAudit?.invoke()?.state == DnaIntegrityState.INVALID) {
                val failed = DeviceTeamleaderSnapshot(now(), "ERROR",
                    0, 0, 0, 0, 0, null, "CHECK_FAILED")
                storage.save(failed)
                return failed
            }
            check()
            val backlog = DeviceDevelopmentBacklog(File(filesDir, "development"), now)
            val recovered = backlog.recover()
            check()
            val doc = backlog.read().doc
            val views = DevelopmentBacklog.views(doc)
            val next = DevelopmentBacklog.nextEligible(doc)?.get("id") as? String
            check()
            val core = ContinuumLearningCore(filesDir, now)
            val swb1 = core.status().modelUpdates
            check()
            val cre1 = core.cre1ResearchStatus().steps
            check()
            val snapshot = DeviceTeamleaderSnapshot(now(), "READY", recovered.size,
                views.count { it.state == "READY" },
                views.count { it.state == "FAILED" }, swb1, cre1, next, "OK")
            storage.save(snapshot)
            return snapshot
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            val snapshot = DeviceTeamleaderSnapshot(now(), "DEFERRED",
                0, 0, 0, 0, 0, null, "CANCELLED")
            storage.save(snapshot)
            return snapshot
        } catch (e: Exception) {
            // Fail closed: a corrupted backlog or model is never silently reset.
            val snapshot = DeviceTeamleaderSnapshot(now(), "ERROR",
                0, 0, 0, 0, 0, null, "CHECK_FAILED")
            storage.save(snapshot)
            return snapshot
        } finally { CYCLE_LOCK.unlock() }
    }

    fun defer(reason: String): DeviceTeamleaderSnapshot {
        require(reason in setOf("CANCELLED", "BATTERY_LOW", "THERMAL_LIMIT",
            "STORAGE_LOW", "TIME_BUDGET"))
        if (!CYCLE_LOCK.tryLock()) return DeviceTeamleaderSnapshot(now(), "DEFERRED",
            0, 0, 0, 0, 0, null, "WORKER_BUSY")
        try {
            try {
                storage.read()
            } catch (_: Exception) {
                return DeviceTeamleaderSnapshot(now(), "ERROR",
                    0, 0, 0, 0, 0, null, "CHECK_FAILED")
            }
            val snapshot = DeviceTeamleaderSnapshot(now(), "DEFERRED", 0, 0, 0, 0, 0, null, reason)
            storage.save(snapshot)
            return snapshot
        } finally { CYCLE_LOCK.unlock() }
    }

    companion object {
        private val CYCLE_LOCK = ReentrantLock()
    }
}
