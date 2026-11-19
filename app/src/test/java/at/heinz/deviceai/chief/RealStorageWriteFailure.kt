package at.heinz.deviceai.chief

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Real, unmocked ways to make the bot store's temp-file creation fail.
 *
 * Mode bits alone are not a host-independent precondition: the kernel ignores them for a
 * process holding CAP_DAC_OVERRIDE (root in a container). The path-length limit applies to
 * every process, so it carries the assertions that must hold on every host; the mode-bit
 * case is added wherever the kernel actually enforces it for this process.
 */
internal object RealStorageWriteFailure {
    private const val REGISTRY = "bot-profiles-v1.bin"
    private const val LOCK = ".bot-profiles.lock"
    // Linux PATH_MAX counts the terminating NUL: 4095 is the longest path a system call accepts.
    private const val LONGEST_PATH = 4095

    /**
     * Moves an already seeded store directory to a location where the registry and the lock
     * still resolve but every sibling temp file (`.bot-profiles-<random>.tmp`) exceeds the
     * kernel path limit. The store then fails at exactly the statement a read-only directory
     * fails at (`Files.createTempFile` in `atomicWrite`), for any uid.
     */
    fun moveToTempNameLimit(seeded: File, scratch: File): File {
        val target = LONGEST_PATH - 1 - REGISTRY.length
        var current = File(scratch, "path-limit").absoluteFile
        check(current.mkdir())
        while (current.path.length + 1 + 200 < target - 1) {
            current = File(current, "d".repeat(200))
            check(current.mkdir())
        }
        val deep = File(current, "s".repeat(target - current.path.length - 1))
        check(deep.path.length == target)
        Files.move(seeded.toPath(), deep.toPath())
        // Establish the precondition by observation, not by assumption about the host.
        check(File(deep, REGISTRY).isFile && File(deep, LOCK).isFile) {
            "Seeded registry and lock must stay reachable at the path limit"
        }
        try {
            val temp = Files.createTempFile(deep.toPath(), ".bot-profiles-", ".tmp")
            Files.delete(temp)
            throw AssertionError("This host accepts paths beyond $LONGEST_PATH bytes; no real temp-file failure")
        } catch (expected: FileSystemException) {
            check(expected !is AccessDeniedException)
        }
        return deep
    }

    /**
     * Makes [directory] read-only and reports whether the kernel then really denies this
     * process a new entry in it. The caller restores the returned original mode.
     */
    fun makeReadOnly(directory: File): ReadOnly {
        val original = Files.getPosixFilePermissions(directory.toPath())
        Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("r-x------"))
        val probe = File(directory, ".mode-probe").toPath()
        val enforced = try { Files.createFile(probe); Files.delete(probe); false }
            catch (_: AccessDeniedException) { true }
        return ReadOnly(enforced) { Files.setPosixFilePermissions(directory.toPath(), original) }
    }

    class ReadOnly(val enforcedForThisProcess: Boolean, private val restore: () -> Unit) : AutoCloseable {
        override fun close() = restore()
    }
}
