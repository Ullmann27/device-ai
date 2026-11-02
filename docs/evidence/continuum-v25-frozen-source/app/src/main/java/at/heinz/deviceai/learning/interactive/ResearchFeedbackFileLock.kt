package at.heinz.deviceai.learning.interactive

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/**
 * Serializes the complete research-feedback read/modify/write transaction across
 * JVM instances and operating-system processes. This grants no consent and does
 * not change the existing learning-experience format or status policy.
 *
 * A process crash releases the kernel lock. A lock failure aborts rather than
 * falling back to an unprotected ledger access.
 */
internal class ResearchFeedbackFileLock(root: File) {
    private val rootDirectory = root.toPath().toAbsolutePath().normalize().toFile()
    val directory: File = File(rootDirectory, "interactive-feedback-v1")
    private val lockFile = File(directory, ".review.lock")

    fun <T> guard(operation: () -> T): T = synchronized(PROCESS_MONITOR) {
        ensureDirectory()
        FileChannel.open(
            lockFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        ).use { channel ->
            channel.lock().use { operation() }
        }
    }

    private fun ensureDirectory() {
        val rootPath = rootDirectory.toPath()
        val directoryPath = directory.toPath()
        if (Files.isSymbolicLink(rootPath))
            throw IOException("Research feedback root cannot be a symbolic link")
        if (!Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS) &&
            !rootDirectory.mkdirs() && !Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS))
            throw IOException("Cannot create research feedback root")
        if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS))
            throw IOException("Research feedback root is not a directory")
        if (!Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS) &&
            !directory.mkdir() && !Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS))
            throw IOException("Cannot create research feedback directory")
        if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(directoryPath)
        ) throw IOException("Research feedback directory is unsafe")
        // The existing ledger codec uses ordinary File APIs. Refuse externally
        // redirected ledger paths before allowing any read or write transaction.
        for (name in listOf("experiences-v2.bin", "experiences-v2.lkg")) {
            if (Files.isSymbolicLink(File(directory, name).toPath())) {
                throw IOException("Research feedback ledger path is a symbolic link")
            }
        }
    }

    companion object {
        private val PROCESS_MONITOR = Any()
    }
}
