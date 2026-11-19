package at.heinz.deviceai.deepclean

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Size and modification time of a file as the file system reports them right now. */
internal data class DeepCleanStamp(val sizeBytes: Long, val modifiedMillis: Long)

/**
 * The file system the lane works on. Every read never follows a symbolic link, every path is resolved below its
 * canonical root, and [delete] is the single call through which this package removes a file directly. Host tests
 * use [NioDeepCleanFiles] on a temporary tree (or a counting wrapper); the phone uses the same class on its real
 * roots.
 */
internal interface DeepCleanFiles {
    /** The roots that can be scanned now; a root without access is absent. */
    fun roots(): Set<DeepCleanRoot>
    /**
     * Visits every file and link below [root], depth first, bounded. [visit] returns false to end the walk. Folders
     * that belong to other apps or the system are not entered ([skipped]).
     */
    fun walk(root: DeepCleanRoot, cancelled: () -> Boolean, visit: (DeepCleanEntry) -> Boolean): WalkResult
    /** The file as it is now; null when it is gone, is not a regular file, is or lies behind a link, or left its root. */
    fun stat(root: DeepCleanRoot, relative: String): DeepCleanStamp?
    /** Full SHA-256 of the file's content (hex); null when it cannot be read or [cancelled] ends it. */
    fun sha256(root: DeepCleanRoot, relative: String, cancelled: () -> Boolean): String?
    /** Removes exactly this regular file. false = it was not removed. */
    fun delete(root: DeepCleanRoot, relative: String): Boolean

    data class WalkResult(val complete: Boolean, val visited: Int, val skipped: List<String>)
}

/**
 * [DeepCleanFiles] over java.nio – free of Android. [roots] maps each root kind to its directory; the canonical form
 * of that directory is the boundary nothing may leave. Folders in [skipTop] directly below a root are not entered.
 */
internal class NioDeepCleanFiles(
    roots: Map<DeepCleanRoot, File>,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxDepth: Int = MAX_DEPTH,
    private val skipTop: Map<DeepCleanRoot, Set<String>> = mapOf(DeepCleanRoot.SHARED to setOf("android"))
) : DeepCleanFiles {
    private val canonical: Map<DeepCleanRoot, Path> = roots.mapNotNull { (kind, dir) ->
        runCatching { dir.toPath().toRealPath() }.getOrNull()?.takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }?.let { kind to it }
    }.toMap()

    override fun roots(): Set<DeepCleanRoot> = canonical.keys

    /** The path of [relative] below [root], or null when it would leave the root or passes a link. */
    private fun resolve(root: DeepCleanRoot, relative: String): Path? {
        val base = canonical[root] ?: return null
        if (relative.isEmpty() || relative.startsWith('/') || relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
        val path = base.resolve(relative).normalize()
        if (!path.startsWith(base) || path == base) return null
        // Every component from the root down must be a real directory or the file itself – never a link.
        var at: Path = path
        while (at != base) {
            if (Files.isSymbolicLink(at)) return null
            at = at.parent ?: return null
        }
        return path
    }

    override fun walk(root: DeepCleanRoot, cancelled: () -> Boolean, visit: (DeepCleanEntry) -> Boolean): DeepCleanFiles.WalkResult {
        val base = canonical[root] ?: return DeepCleanFiles.WalkResult(false, 0, emptyList())
        val skipped = ArrayList<String>()
        val stack = ArrayDeque<Pair<Path, Int>>()
        stack.addLast(base to 0)
        var visited = 0
        var complete = true
        while (stack.isNotEmpty()) {
            if (cancelled()) throw CancellationException("Suche gestoppt")
            val (dir, depth) = stack.removeLast()
            val children = runCatching { Files.newDirectoryStream(dir).use { it.toList() } }.getOrElse {
                skipped += base.relativize(dir).toString().ifEmpty { "." }
                continue
            }
            for (child in children.sortedBy { it.fileName.toString() }) {
                if (visited >= maxEntries) return DeepCleanFiles.WalkResult(false, visited, skipped)
                val relative = base.relativize(child).toString().replace(File.separatorChar, '/')
                val attributes = runCatching {
                    Files.readAttributes(child, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                }.getOrNull() ?: continue
                visited++
                when {
                    attributes.isSymbolicLink -> if (!visit(DeepCleanEntry(root, relative, 0L, attributes.lastModifiedTime().toMillis(),
                            symlink = true))) return DeepCleanFiles.WalkResult(false, visited, skipped)
                    attributes.isDirectory -> {
                        if (depth == 0 && child.fileName.toString().lowercase() in skipTop[root].orEmpty()) { skipped += relative; continue }
                        if (depth + 1 >= maxDepth) { skipped += relative; complete = false; continue }
                        stack.addLast(child to depth + 1)
                    }
                    attributes.isRegularFile -> if (!visit(DeepCleanEntry(root, relative, attributes.size(),
                            attributes.lastModifiedTime().toMillis(), insideRoot = child.normalize().startsWith(base))))
                        return DeepCleanFiles.WalkResult(false, visited, skipped)
                }
            }
        }
        return DeepCleanFiles.WalkResult(complete, visited, skipped)
    }

    override fun stat(root: DeepCleanRoot, relative: String): DeepCleanStamp? {
        val path = resolve(root, relative) ?: return null
        val attributes = runCatching { Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS) }
            .getOrNull() ?: return null
        if (!attributes.isRegularFile || attributes.isSymbolicLink) return null
        // The canonical path is exactly the path we built: no link anywhere in between, no unexpected change of place.
        if (runCatching { path.toRealPath() }.getOrNull() != path) return null
        return DeepCleanStamp(attributes.size(), attributes.lastModifiedTime().toMillis())
    }

    override fun sha256(root: DeepCleanRoot, relative: String, cancelled: () -> Boolean): String? {
        val path = resolve(root, relative) ?: return null
        if (stat(root, relative) == null) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    if (cancelled()) return null
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    override fun delete(root: DeepCleanRoot, relative: String): Boolean {
        val path = resolve(root, relative) ?: return false
        if (stat(root, relative) == null) return false
        return runCatching { Files.delete(path); true }.getOrDefault(false)
    }

    companion object {
        const val MAX_ENTRIES = 120_000
        const val MAX_DEPTH = 14
        private const val BUFFER = 256 * 1024
    }
}

/** What one scan really saw. Every number here is counted or measured by the scan itself. */
internal data class DeepCleanScan(
    val findings: List<DeepCleanFinding>,
    /** Roots that were wanted but could not be read (no permission). */
    val missingRoots: Set<DeepCleanRoot>,
    val visited: Int,
    val complete: Boolean,
    /** Folders that were not entered (other apps' data, depth limit). */
    val skipped: List<String>,
    val duplicates: DeepCleanDuplicates.Result?
) {
    fun level(level: DeepCleanLevel): List<DeepCleanFinding> = findings.filter { it.level == level }
    /** Per category: (count, bytes) of every file seen – measured sizes, nothing estimated. */
    fun byCategory(): Map<DeepCleanCategory, Pair<Int, Long>> = findings.groupBy { it.category }
        .mapValues { (_, list) -> list.size to list.sumOf { it.entry.sizeBytes } }
}

/** The scan: walk the roots, classify every entry, find duplicates among shared files. Bounded and cancellable. */
internal object DeepCleanScanner {
    fun scan(
        files: DeepCleanFiles,
        wanted: Set<DeepCleanRoot>,
        nowMillis: Long,
        active: Set<String> = emptySet(),
        withDuplicates: Boolean = false,
        cancelled: () -> Boolean = { false },
        throttled: () -> Boolean = { false },
        progress: (Int) -> Unit = {}
    ): DeepCleanScan {
        val available = files.roots()
        val findings = ArrayList<DeepCleanFinding>()
        val shared = ArrayList<DeepCleanEntry>()
        val skipped = ArrayList<String>()
        var visited = 0
        var complete = true
        for (root in wanted.filter { it in available }.sortedBy { it.ordinal }) {
            val result = files.walk(root, cancelled) { entry ->
                findings += DeepCleanClassifier.classify(entry, nowMillis, active)
                if (entry.root == DeepCleanRoot.SHARED && !entry.symlink) shared += entry
                if (findings.size % PROGRESS_EVERY == 0) progress(findings.size)
                true
            }
            visited += result.visited
            complete = complete && result.complete
            skipped += result.skipped.map { root.name + ":" + it }
        }
        progress(findings.size)
        val duplicates = if (withDuplicates && shared.isNotEmpty())
            DeepCleanDuplicates.find(shared, files, cancelled = cancelled, throttled = throttled) else null
        val merged = if (duplicates == null) findings else merge(findings, duplicates, nowMillis)
        return DeepCleanScan(merged, wanted - available, visited, complete && (duplicates?.complete ?: true), skipped, duplicates)
    }

    /** A YELLOW duplicate verdict replaces the single-file verdict, except for files that are excluded or kept. */
    private fun merge(findings: List<DeepCleanFinding>, duplicates: DeepCleanDuplicates.Result, nowMillis: Long): List<DeepCleanFinding> {
        val verdicts = DeepCleanClassifier.duplicates(duplicates.groups, { duplicates.hashes.getValue(it.key) }, nowMillis).associateBy { it.entry.key }
        return findings.map { finding ->
            val duplicate = verdicts[finding.entry.key] ?: return@map finding
            when {
                finding.level == DeepCleanLevel.EXCLUDED -> finding
                duplicate.level == DeepCleanLevel.YELLOW -> duplicate
                finding.level == DeepCleanLevel.RED -> duplicate
                else -> finding
            }
        }
    }

    private const val PROGRESS_EVERY = 250
}

/**
 * Byte-identical files: candidates are grouped by exact size first, then every member of a group of two or more is
 * hashed in full with SHA-256. Similar photos are not duplicates and are not looked for. Bounded by [maxBytes] and
 * [maxFiles]; [throttled] (the device is hot) ends the search early, and the result says it is incomplete.
 */
internal object DeepCleanDuplicates {
    const val MIN_BYTES = 256L * 1024
    const val MAX_BYTES = 8L * 1024 * 1024 * 1024
    const val MAX_FILES = 3_000

    data class Result(val groups: List<List<DeepCleanEntry>>, val hashes: Map<String, String>, val hashedFiles: Int,
                      val hashedBytes: Long, val complete: Boolean, val stoppedByHeat: Boolean)

    fun find(entries: List<DeepCleanEntry>, files: DeepCleanFiles, minBytes: Long = MIN_BYTES, maxBytes: Long = MAX_BYTES,
             maxFiles: Int = MAX_FILES, cancelled: () -> Boolean = { false }, throttled: () -> Boolean = { false }): Result {
        val bySize = entries.filter { !it.symlink && it.sizeBytes >= minBytes }.groupBy { it.sizeBytes }.filterValues { it.size >= 2 }
        val hashes = HashMap<String, String>()
        var hashedBytes = 0L
        var hashedFiles = 0
        var complete = true
        var heat = false
        loop@ for ((size, group) in bySize.entries.sortedByDescending { it.key }) {
            for (entry in group) {
                if (cancelled()) throw CancellationException("Suche gestoppt")
                if (throttled()) { heat = true; complete = false; break@loop }
                if (hashedFiles >= maxFiles || hashedBytes + size > maxBytes) { complete = false; break@loop }
                val hash = files.sha256(entry.root, entry.relative, cancelled) ?: continue
                hashes[entry.key] = hash
                hashedBytes += size
                hashedFiles++
            }
        }
        val groups = bySize.values.flatMap { group ->
            group.filter { it.key in hashes }.groupBy { hashes.getValue(it.key) }.values.filter { it.size >= 2 }
        }
        return Result(groups, hashes, hashedFiles, hashedBytes, complete, heat)
    }
}
