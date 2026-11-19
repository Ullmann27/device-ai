package at.heinz.deviceai.devicetools

import android.content.Context
import android.os.Environment
import at.heinz.deviceai.OwnerDeviceLibrary
import at.heinz.deviceai.OwnerLibraryAccess
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * The owner's shared storage as the in-process device tools see it: Android's „Zugriff auf alle Dateien“
 * (MANAGE_EXTERNAL_STORAGE) decides whether anything below [root] may be listed or read by path. No Shizuku, no
 * network. Host tests replace it with a temporary directory tree.
 */
internal interface DeviceStorage {
    fun access(): OwnerLibraryAccess
    fun root(): File
}

internal class AndroidDeviceStorage(context: Context) : DeviceStorage {
    private val library = OwnerDeviceLibrary(context.applicationContext)
    override fun access(): OwnerLibraryAccess = library.access()
    @Suppress("DEPRECATION")
    override fun root(): File = Environment.getExternalStorageDirectory()
}

/**
 * One file or folder as the file system reports it. [relative] is the path below the storage root with `/`
 * separators („Download/Experimente.pdf“); nothing here is invented or completed.
 */
internal data class DeviceFile(val name: String, val relative: String, val directory: Boolean, val sizeBytes: Long,
                               val modifiedMillis: Long) {
    /** „Download“, „Documents/Labor“ – "" for an entry directly in the storage root. */
    val folder: String get() = relative.substringBeforeLast('/', "")
    val extension: String get() = if (directory) "" else DevicePaths.extension(name)
    val path: String get() = DevicePaths.shown(relative)
}

/**
 * Paths of the shared storage. Tools show and accept the short `/sdcard/…` form the existing file tools use (chains
 * and the files stage parse it); a relative path („Download/a.pdf“) and Android's long form are accepted as well.
 */
internal object DevicePaths {
    const val SHOWN_ROOT = "/sdcard"
    private val knownRoots = listOf("/storage/emulated/0", "/sdcard", "/storage/self/primary", "/mnt/sdcard")

    fun shown(relative: String): String = if (relative.isEmpty()) SHOWN_ROOT else "$SHOWN_ROOT/$relative"

    /** The lower-case extension of a file name („pdf“), or "" – a leading dot or an over-long tail is no extension. */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        val tail = name.substring(dot + 1)
        return if (tail.length <= 5 && tail.all { it.isLetterOrDigit() }) tail.lowercase(Locale.ROOT) else ""
    }

    /**
     * [raw] as a path below the storage root, or null when it leaves the shared storage: `..`, another absolute
     * location, control characters, or `Android/` at the root (other apps' private data).
     */
    fun relative(raw: String, root: File? = null): String? {
        var text = raw.trim().replace('\\', '/')
        if (text.isEmpty() || text.length > 1_024 || text.any { it.isISOControl() }) return null
        text = text.removePrefix("file://")
        val prefixes = knownRoots + listOfNotNull(root?.absolutePath?.trimEnd('/')?.takeIf { it.isNotEmpty() })
        val prefix = prefixes.sortedByDescending { it.length }.firstOrNull { text == it || text.startsWith("$it/") }
        if (prefix != null) text = text.removePrefix(prefix) else if (text.startsWith("/")) return null
        val parts = text.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." || it == "." }) return null
        if (parts.firstOrNull() == "Android") return null
        return parts.joinToString("/")
    }

    /** The real file for [raw], or null when [raw] is not a path of the shared storage. */
    fun resolve(raw: String, root: File): File? {
        val relative = relative(raw, root) ?: return null
        val file = if (relative.isEmpty()) root else File(root, relative)
        // A link must not lead out of the storage.
        val inside = runCatching {
            val base = root.canonicalPath
            val real = file.canonicalPath
            real == base || real.startsWith(base + File.separator)
        }.getOrDefault(false)
        return file.takeIf { inside }
    }

    fun relativeOf(file: File, root: File): String =
        file.absolutePath.removePrefix(root.absolutePath).trim('/').replace(File.separatorChar, '/')
}

/**
 * The bounded walk below a storage folder: breadth first, the usual document folders first, so the likely places are
 * done before a bound ends the walk. Skipped: `Android/` at the storage root and hidden entries. Bounded in depth,
 * visited entries and time; [cancelled] and the thread's interrupt flag are checked for every entry and end the walk
 * with a CancellationException. Plain java.io.
 */
internal object DeviceFileWalk {
    class Limits(val maxDepth: Int = 12, val maxEntries: Int = 150_000, val maxMillis: Long = 10_000L)

    /** [stoppedEarly]: the reason in the owner's language when a bound ended the walk before every folder was seen. */
    class Outcome(val visited: Int, val folders: Int, val stoppedEarly: String?)

    /** One seen entry. Size and time are read from the file system only when a tool asks for them. */
    class Entry(val file: File, val relative: String, val directory: Boolean, val depth: Int) {
        val name: String get() = file.name
        fun describe(): DeviceFile = DeviceFile(file.name, relative, directory, if (directory) 0L else file.length(), file.lastModified())
    }

    private val preferred = listOf("download", "downloads", "documents", "dokumente")

    /**
     * Names that end like this are files; asking the file system about each of them would only cost time in folders
     * with thousands of photos. A folder with such a name is listed as a file and not entered – none is known.
     */
    private val plainFiles = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "dng", "mp4", "mov", "mkv", "webm",
        "mp3", "m4a", "opus", "ogg", "wav", "aac", "flac", "zip", "apk", "txt", "pdf", "docx", "doc", "xlsx", "xls", "pptx",
        "json", "xml", "db", "log", "tmp", "nomedia", "vcf", "csv", "html", "md")

    /**
     * Walks [start] (a folder below [root]). [visit] gets every entry; returning false ends the walk at once (the
     * caller has what it needs – that is not „stopped early“). [maxDepth] 1 = only the entries of [start] itself.
     */
    fun walk(root: File, start: File = root, limits: Limits = Limits(), cancelled: () -> Boolean = { false },
             nanoTime: () -> Long = System::nanoTime, visit: (Entry) -> Boolean): Outcome {
        val started = nanoTime()
        val startRelative = DevicePaths.relativeOf(start, root)
        var visited = 0
        var folders = 0
        var stopped: String? = null
        val queue = ArrayDeque<Triple<File, String, Int>>()
        queue.addLast(Triple(start, startRelative, 0))
        walk@ while (queue.isNotEmpty()) {
            val (directory, relative, depth) = queue.removeFirst()
            if ((nanoTime() - started) / 1_000_000L > limits.maxMillis) {
                stopped = "nach ${limits.maxMillis / 1000} Sekunden beendet; nicht alle Ordner wurden geprüft"
                break
            }
            val entries = directory.listFiles() ?: continue
            folders++
            val top = relative.isEmpty()
            val ordered = if (top) entries.sortedWith(compareBy<File>({ file ->
                preferred.indexOf(file.name.lowercase(Locale.ROOT)).let { if (it < 0) preferred.size else it } },
                { it.name.lowercase(Locale.ROOT) })) else entries.sortedBy { it.name.lowercase(Locale.ROOT) }
            for (entry in ordered) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Suche gestoppt")
                val name = entry.name
                if (name.startsWith(".") || (top && name == "Android")) continue
                if (++visited > limits.maxEntries) {
                    stopped = "nach ${limits.maxEntries} Einträgen beendet; nicht alle Ordner wurden geprüft"
                    break@walk
                }
                val isDirectory = DevicePaths.extension(name) !in plainFiles && entry.isDirectory
                val child = if (relative.isEmpty()) name else "$relative/$name"
                if (!visit(Entry(entry, child, isDirectory, depth + 1))) break@walk
                if (isDirectory && depth + 1 < limits.maxDepth) queue.addLast(Triple(entry, child, depth + 1))
            }
        }
        return Outcome(visited, folders, stopped)
    }
}
