package at.heinz.deviceai.devicetools

import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.PriorityQueue
import java.util.concurrent.CancellationException

/**
 * The file tools answered from the file system itself (java.io under Android's „Zugriff auf alle Dateien“) instead of
 * a shell command through the Shizuku bridge. The existing tools keep the output shape of their shell commands,
 * because the task chain and the files stage parse it:
 *  - `list_files`: the long listing of `ls -lah` (optional `total` line, one entry per line, ISO date);
 *  - `find_files`, `find_latest_apk`: one path per line, `/sdcard/…`;
 *  - `get_large_files`: `du -ak` lines – size in KB, a tab, the path – largest first.
 * A bound that ended a walk early is said in a last line that starts with „Hinweis:“ (in the `total` line of a
 * listing); an empty result is a sentence, never an empty string. Nothing here writes except [move] and [delete],
 * which the ToolGate only reaches after the owner's approval.
 */
internal class DeviceFileTools(
    private val storage: DeviceStorage,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val limits: DeviceFileWalk.Limits = DeviceFileWalk.Limits(),
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val root: File get() = storage.root()

    private fun cancelledNow(cancelled: () -> Boolean) {
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Werkzeug gestoppt")
    }

    private fun shown(file: File): String = DevicePaths.shown(DevicePaths.relativeOf(file, root))

    private fun walk(start: File, cancelled: () -> Boolean, limits: DeviceFileWalk.Limits = this.limits,
                     visit: (DeviceFileWalk.Entry) -> Boolean): DeviceFileWalk.Outcome =
        DeviceFileWalk.walk(root, start, limits, cancelled, nanoTime, visit)

    /** The folders named in `roots`, or the whole storage; an entry outside the shared storage is an error. */
    private fun starts(roots: List<String>): List<File> {
        if (roots.isEmpty()) return listOf(root)
        return roots.map { raw -> DevicePaths.resolve(raw, root) ?: throw IllegalArgumentException(OUTSIDE) }.distinct()
    }

    // ── list_files ──────────────────────────────────────────────────────────────────────────────────────────

    fun listFiles(rawPath: String, cancelled: () -> Boolean = { false }): String {
        val target = DevicePaths.resolve(rawPath, root) ?: return OUTSIDE
        val path = shown(target)
        if (!target.exists()) return "Nicht gefunden: $path – diesen Ordner gibt es nicht."
        if (!target.isDirectory) return longLine(target)
        val entries = target.listFiles() ?: return "Nicht lesbar: Android lässt mich $path nicht auflisten."
        class Row(val file: File, val modified: Long)
        val rows = entries.map { cancelledNow(cancelled); Row(it, it.lastModified()) }
        // More entries than fit: the most recently changed ones stay (the newest file is never the one cut off).
        val kept = rows.sortedByDescending { it.modified }
        val lines = ArrayList<Pair<String, String>>()
        var used = 0
        var bytes = 0L
        for (row in kept) {
            val line = longLine(row.file)
            if (lines.size >= MAX_LISTED || used + line.length + 1 > LISTING_CHARACTERS) break
            lines += row.file.name to line
            used += line.length + 1
            if (row.file.isFile) bytes += row.file.length()
        }
        val cut = lines.size < rows.size
        val total = "total ${human(bytes)}" + if (cut) " · Hinweis: nur die ${lines.size} zuletzt geänderten von ${rows.size} Einträgen" else ""
        if (rows.isEmpty()) return "$total\nDer Ordner $path ist leer."
        return (listOf(total) + lines.sortedBy { it.first.lowercase(Locale.ROOT) }.map { it.second }).joinToString("\n")
    }

    /** One `ls -lah` entry. Owner and group are not readable through java.io; they are printed as `-`. */
    internal fun longLine(file: File): String {
        val directory = file.isDirectory
        val stamp = LISTING_TIME.format(Instant.ofEpochMilli(file.lastModified()).atZone(zone()))
        val name = file.name.map { if (it.isISOControl()) '?' else it }.joinToString("")
        return (if (directory) "drwxrwx--- 2" else "-rw-rw---- 1") + " - - " + human(file.length()) + " " + stamp + " " + name
    }

    // ── find_files ──────────────────────────────────────────────────────────────────────────────────────────

    fun findFiles(query: String, roots: List<String>, limit: Int, cancelled: () -> Boolean = { false }): String {
        val wanted = query.replace(Regex("[\\r\\n\\t]"), " ").trim().take(80)
        if (wanted.isEmpty()) return "Keine Suche: Der Suchtext ist leer."
        val needle = wanted.lowercase(Locale.ROOT)
        val matcher = DeviceSearch.Query(wanted, DeviceSearch.Scope.FILES)
        class Found(val file: DeviceFile, val rank: Int)
        val best = PriorityQueue(compareBy<Found>({ it.rank }, { -it.file.modifiedMillis }, { it.file.relative }).reversed())
        val bound = limit.coerceIn(1, 250)
        var total = 0
        var visited = 0
        var stopped: String? = null
        val places = starts(roots)
        for (start in places) {
            val outcome = walk(start, cancelled) { entry ->
                if (entry.directory) return@walk true
                // The shell tool matched `-iname '*query*'`; names that only differ in spelling match as well.
                val contains = entry.name.lowercase(Locale.ROOT).contains(needle)
                val tier = matcher.file(entry.name, entry.relative.substringBeforeLast('/', ""), false)
                if (contains || tier != null) {
                    total++
                    best += Found(entry.describe(), tier?.ordinal ?: DeviceSearch.Tier.CONTAINS.ordinal)
                    if (best.size > bound) best.poll()
                }
                true
            }
            visited += outcome.visited
            stopped = stopped ?: outcome.stoppedEarly
        }
        val hint = stopped?.let { "Hinweis: Suche $it." }
        if (best.isEmpty()) return listOfNotNull("Keine Datei gefunden, deren Name zu „$wanted“ passt. Durchsucht: $visited Einträge in " +
            places.joinToString(", ") { shown(it) } + ".", hint).joinToString("\n")
        val paths = best.sortedWith(compareBy<Found>({ it.rank }, { -it.file.modifiedMillis }, { it.file.relative })).map { it.file.path }
        return (paths + listOfNotNull(hint)).joinToString("\n")
    }

    // ── find_latest_apk ─────────────────────────────────────────────────────────────────────────────────────

    fun findLatestApk(filter: String?, cancelled: () -> Boolean = { false }): String {
        val wanted = filter?.replace(Regex("[^A-Za-z0-9._ -]"), "")?.trim().orEmpty().lowercase(Locale.ROOT)
        val download = File(root, "Download")
        if (!download.isDirectory) return "Keine passende APK-Datei: Den Download-Ordner gibt es nicht."
        val found = ArrayList<DeviceFile>()
        // As the shell tool did: the Download folder, at most three levels deep.
        val outcome = walk(download, cancelled, DeviceFileWalk.Limits(maxDepth = 3, maxEntries = limits.maxEntries, maxMillis = limits.maxMillis)) { entry ->
            val name = entry.name.lowercase(Locale.ROOT)
            if (!entry.directory && name.endsWith(".apk") && (wanted.isEmpty() || name.removeSuffix(".apk").contains(wanted))) found += entry.describe()
            true
        }
        val hint = outcome.stoppedEarly?.let { "Hinweis: Suche $it." }
        if (found.isEmpty()) return listOfNotNull("Keine passende APK-Datei im Download-Ordner gefunden" +
            (if (wanted.isEmpty()) "." else " (Filter „$wanted“)."), hint).joinToString("\n")
        return (found.sortedWith(compareBy<DeviceFile>({ -it.modifiedMillis }, { it.relative })).take(MAX_APKS).map { it.path } +
            listOfNotNull(hint)).joinToString("\n")
    }

    // ── get_large_files ─────────────────────────────────────────────────────────────────────────────────────

    fun largeFiles(roots: List<String>, limit: Int, cancelled: () -> Boolean = { false }): String {
        val bound = limit.coerceIn(1, 100)
        // Without `roots`: every top folder of the storage on its own, like `du` over the shared folders.
        val places = if (roots.isEmpty()) (root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }
            .sortedBy { it.name.lowercase(Locale.ROOT) }) else starts(roots)
        if (places.isEmpty()) return "Keine Ordner gefunden, die ich messen könnte."
        class Sized(val path: String, val kilobytes: Long)
        val files = PriorityQueue(compareBy<Sized>({ it.kilobytes }, { it.path }))
        val folders = HashMap<String, Long>()
        var stopped: String? = null
        var counted = 0
        for (start in places) {
            val top = DevicePaths.relativeOf(start, root)
            folders.putIfAbsent(top, 0L)
            val outcome = walk(start, cancelled) { entry ->
                if (entry.directory) { folders.putIfAbsent(entry.relative, 0L); return@walk true }
                val bytes = entry.file.length()
                counted++
                files += Sized(DevicePaths.shown(entry.relative), kilobytes(bytes))
                if (files.size > bound) files.poll()
                // `du` counts a file in every folder above it, up to the measured one.
                var folder = entry.relative.substringBeforeLast('/', "")
                while (true) {
                    folders[folder] = (folders[folder] ?: 0L) + bytes
                    if (folder == top || folder.isEmpty()) break
                    folder = folder.substringBeforeLast('/', "")
                }
                true
            }
            stopped = stopped ?: outcome.stoppedEarly
        }
        val all = files.toList() + folders.map { Sized(DevicePaths.shown(it.key), kilobytes(it.value)) }
        val lines = all.sortedWith(compareBy<Sized>({ -it.kilobytes }, { it.path })).take(bound).map { "${it.kilobytes}\t${it.path}" }
        val hint = stopped?.let { "Hinweis: Messung $it; die Größen sind Mindestwerte." }
        if (counted == 0) return listOfNotNull("Keine Dateien in " + places.joinToString(", ") { shown(it) } + " gefunden.", hint).joinToString("\n")
        return (lines + listOfNotNull(hint)).joinToString("\n")
    }

    // ── list_recent_files ───────────────────────────────────────────────────────────────────────────────────

    fun recentFiles(filter: String?, limit: Int, cancelled: () -> Boolean = { false }): String {
        val bound = limit.coerceIn(1, 100)
        val wanted = filter?.trim().orEmpty()
        val tokens = OwnerLibraryMatcher.tokens(wanted)
        val kind = tokens.firstNotNullOfOrNull { DeviceFileKind.ofWord(it) }
        val extension = wanted.lowercase(Locale.ROOT).removePrefix("*").removePrefix(".").takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
        val needle = wanted.lowercase(Locale.ROOT)
        val newest = PriorityQueue(compareBy<DeviceFile>({ it.modifiedMillis }, { it.relative }))
        var total = 0
        val outcome = walk(root, cancelled) { entry ->
            if (entry.directory) return@walk true
            val own = DevicePaths.extension(entry.name)
            val wantedFile = wanted.isEmpty() || (kind != null && own in kind.extensions) || (kind == null && extension != null && own == extension) ||
                (kind == null && entry.name.lowercase(Locale.ROOT).contains(needle))
            if (wantedFile) {
                total++
                newest += entry.describe()
                if (newest.size > bound) newest.poll()
            }
            true
        }
        val hint = outcome.stoppedEarly?.let { "Hinweis: Suche $it." }
        val what = if (wanted.isEmpty()) "" else " (Filter „${DeviceSearch.clean(wanted).take(40)}“)"
        if (newest.isEmpty()) return listOfNotNull("Keine Dateien gefunden$what. Durchsucht: ${outcome.visited} Einträge.", hint).joinToString("\n")
        val lines = newest.sortedWith(compareBy<DeviceFile>({ -it.modifiedMillis }, { it.relative })).mapIndexed { index, file ->
            "${index + 1}. ${file.path} · ${OwnerLibraryMatcher.sizeText(file.sizeBytes)} · geändert ${DeviceSearch.dateTime(file.modifiedMillis, zone())}"
        }
        return (listOf("Zuletzt geänderte Dateien$what (${lines.size} von $total):") + lines + listOfNotNull(hint)).joinToString("\n")
    }

    // ── get_folder_overview ─────────────────────────────────────────────────────────────────────────────────

    fun folderOverview(rawPath: String?, cancelled: () -> Boolean = { false }): String {
        val target = if (rawPath.isNullOrBlank()) root else DevicePaths.resolve(rawPath, root) ?: return OUTSIDE
        val path = shown(target)
        if (!target.exists()) return "Nicht gefunden: $path – diesen Ordner gibt es nicht."
        if (!target.isDirectory) return "Kein Ordner: $path ist eine Datei. " + longLine(target)
        val base = DevicePaths.relativeOf(target, root)
        class Sub(var files: Int = 0, var bytes: Long = 0L)
        val subs = LinkedHashMap<String, Sub>()
        val kinds = HashMap<String, Int>()
        var direct = 0
        var directBytes = 0L
        var files = 0
        var bytes = 0L
        var newest: DeviceFile? = null
        val outcome = walk(target, cancelled) { entry ->
            val below = if (base.isEmpty()) entry.relative else entry.relative.removePrefix("$base/")
            val top = below.substringBefore('/')
            if (entry.directory) { if (entry.depth == 1) subs.getOrPut(top) { Sub() }; return@walk true }
            val size = entry.file.length()
            files++
            bytes += size
            val label = DeviceFileKind.ofExtension(DevicePaths.extension(entry.name))?.label ?: "andere"
            kinds[label] = (kinds[label] ?: 0) + 1
            if (entry.depth == 1) { direct++; directBytes += size } else subs.getOrPut(top) { Sub() }.let { it.files++; it.bytes += size }
            val modified = entry.file.lastModified()
            if (newest == null || modified > newest!!.modifiedMillis) newest = DeviceFile(entry.name, entry.relative, false, size, modified)
            true
        }
        return buildString {
            append("Ordnerübersicht $path: ${subs.size} Unterordner, $direct Dateien direkt darin (${OwnerLibraryMatcher.sizeText(directBytes)}), ")
            append("$files Dateien mit allen Unterordnern (${OwnerLibraryMatcher.sizeText(bytes)}).")
            if (subs.isNotEmpty()) {
                append("\nUnterordner nach Größe:")
                subs.entries.sortedWith(compareBy({ -it.value.bytes }, { it.key.lowercase(Locale.ROOT) })).take(MAX_SUBFOLDERS).forEach { (name, sub) ->
                    append("\n- ").append(name).append("/ · ").append(sub.files).append(" Dateien · ").append(OwnerLibraryMatcher.sizeText(sub.bytes))
                }
                if (subs.size > MAX_SUBFOLDERS) append("\n- … und ${subs.size - MAX_SUBFOLDERS} weitere")
            }
            if (kinds.isNotEmpty()) append("\nDateitypen: " + kinds.entries.sortedWith(compareBy({ -it.value }, { it.key })).joinToString(" · ") { "${it.key} ${it.value}" })
            newest?.let { append("\nNeueste Datei: ${it.path} · geändert ${DeviceSearch.dateTime(it.modifiedMillis, zone())}") }
            outcome.stoppedEarly?.let { append("\nHinweis: Zählung $it; die Zahlen sind Mindestwerte.") }
        }
    }

    // ── search_file_text ────────────────────────────────────────────────────────────────────────────────────

    fun searchText(query: String, rawPath: String?, limit: Int, budget: Int, cancelled: () -> Boolean = { false }): String {
        val wanted = DeviceSearch.clean(query).take(120)
        if (wanted.length < 2) return "Keine Suche: Der Suchtext ist zu kurz."
        val target = if (rawPath.isNullOrBlank()) root else DevicePaths.resolve(rawPath, root) ?: return OUTSIDE
        if (!target.exists()) return "Nicht gefunden: ${shown(target)} – diesen Ordner gibt es nicht."
        val needle = wanted.lowercase(Locale.GERMAN)
        val bound = limit.coerceIn(1, 50)
        val hits = ArrayList<String>()
        var read = 0
        var skipped = 0
        var used = 0
        var more = false
        fun scan(file: File, relative: String): Boolean {
            if (DevicePaths.extension(file.name) !in DeviceFileKind.TEXT.extensions + TEXT_SEARCH_EXTRA) return true
            if (file.length() > MAX_SEARCH_FILE_BYTES || read >= MAX_SEARCH_FILES) { skipped++; return true }
            val text = runCatching { DeviceFileText.decode(file.readBytes()) }.getOrNull() ?: run { skipped++; return true }
            read++
            text.lineSequence().forEachIndexed { index, line ->
                cancelledNow(cancelled)
                if (!line.lowercase(Locale.GERMAN).contains(needle)) return@forEachIndexed
                if (hits.size >= bound) { more = true; return false }
                val at = line.lowercase(Locale.GERMAN).indexOf(needle)
                val from = (at - 60).coerceAtLeast(0)
                val excerpt = DeviceSearch.clean((if (from > 0) "…" else "") + line.substring(from, minOf(line.length, at + needle.length + 100)) +
                    (if (at + needle.length + 100 < line.length) "…" else ""))
                val entry = "${hits.size + 1}. ${DevicePaths.shown(relative)} · Zeile ${index + 1}: $excerpt"
                if (hits.isNotEmpty() && used + entry.length + 1 > budget - 300) { more = true; return false }
                hits += entry
                used += entry.length + 1
            }
            return true
        }
        val outcome = if (target.isFile) { scan(target, DevicePaths.relativeOf(target, root)); null }
            else walk(target, cancelled) { entry -> if (entry.directory) true else scan(entry.file, entry.relative) }
        val scope = "Durchsucht: $read Textdatei(en) in ${shown(target)}" + (if (skipped > 0) ", $skipped übersprungen (größer als " +
            "${OwnerLibraryMatcher.sizeText(MAX_SEARCH_FILE_BYTES.toLong())}, nicht lesbar oder über der Grenze von $MAX_SEARCH_FILES Dateien)" else "") +
            ". PDF- und Word-Dateien durchsucht dieses Werkzeug nicht."
        val hint = outcome?.stoppedEarly?.let { "Hinweis: Suche $it." }
        if (hits.isEmpty()) return listOfNotNull("Textsuche „$wanted“ – keine Fundstelle. $scope", hint).joinToString("\n")
        return (listOf("Textsuche „$wanted“ – ${hits.size} Fundstelle(n)" + (if (more) " (${DeviceFileText.CUT}: es gibt weitere)" else "") + ":") +
            hits + scope + listOfNotNull(hint)).joinToString("\n")
    }

    // ── move_file / delete_file ─────────────────────────────────────────────────────────────────────────────

    /** The same folders the shell tools were limited to; a changing tool does not reach beyond them. */
    private fun changeable(raw: String): File {
        val file = DevicePaths.resolve(raw, root) ?: throw IllegalArgumentException(OUTSIDE)
        val top = DevicePaths.relativeOf(file, root).substringBefore('/')
        require(top in CHANGE_ROOTS) { "Pfad liegt außerhalb der freigegebenen Ordner (${CHANGE_ROOTS.joinToString()})." }
        return file
    }

    /** Why [source] cannot be moved to [destination] right now, or null. Checked before the owner is asked. */
    fun moveProblem(source: String, destination: String): String? = runCatching {
        val from = changeable(source)
        val to = target(from, changeable(destination))
        when {
            !from.exists() -> "Nicht verschoben: ${shown(from)} gibt es nicht. Es wurde nichts verändert."
            DevicePaths.relativeOf(from, root) in CHANGE_ROOTS -> "Nicht verschoben: Ein Hauptordner wird nicht verschoben. Es wurde nichts verändert."
            to.exists() -> "Nicht verschoben: Am Ziel ${shown(to)} liegt schon etwas; ich überschreibe nichts. Es wurde nichts verändert."
            to.parentFile?.isDirectory != true -> "Nicht verschoben: Den Zielordner ${to.parentFile?.let(::shown)} gibt es nicht. Es wurde nichts verändert."
            else -> null
        }
    }.getOrElse { "Nicht verschoben: ${it.message ?: "der Pfad ist ungültig"} Es wurde nichts verändert." }

    private fun target(from: File, to: File): File = if (to.isDirectory) File(to, from.name) else to

    fun move(source: String, destination: String): String {
        val from = changeable(source)
        val to = target(from, changeable(destination))
        check(from.exists()) { "Quelle fehlt." }
        check(!to.exists()) { "Ziel existiert bereits." }
        check(from.renameTo(to)) { "Android hat das Verschieben abgelehnt." }
        check(to.exists() && !from.exists()) { "Verschieben nicht bestätigt." }
        return "Verschoben.\nVon: ${shown(from)}\nNach: ${shown(to)}"
    }

    /** Why [raw] cannot be deleted right now, or null. Checked before the owner is asked. */
    fun deleteProblem(raw: String): String? = runCatching {
        val file = changeable(raw)
        when {
            DevicePaths.relativeOf(file, root) in CHANGE_ROOTS -> "Nicht gelöscht: Ordnerwurzeln dürfen nicht gelöscht werden. Es wurde nichts verändert."
            !file.exists() -> "Nicht gelöscht: ${shown(file)} gibt es nicht. Es wurde nichts verändert."
            !file.isFile -> "Nicht gelöscht: ${shown(file)} ist ein Ordner; dieses Werkzeug löscht nur einzelne Dateien. Es wurde nichts verändert."
            else -> null
        }
    }.getOrElse { "Nicht gelöscht: ${it.message ?: "der Pfad ist ungültig"} Es wurde nichts verändert." }

    fun delete(raw: String): String {
        val file = changeable(raw)
        require(DevicePaths.relativeOf(file, root) !in CHANGE_ROOTS) { "Ordnerwurzeln dürfen nicht gelöscht werden." }
        check(file.isFile) { "Keine Datei." }
        check(file.delete() && !file.exists()) { "Android hat das Löschen abgelehnt." }
        return "Gelöscht.\nDatei: ${shown(file)}"
    }

    companion object {
        const val OUTSIDE = "Nicht möglich: Der Pfad liegt nicht im gemeinsamen Speicher (/sdcard/…) oder im gesperrten Ordner Android/."
        const val MAX_LISTED = 400
        const val LISTING_CHARACTERS = 7_000
        const val MAX_APKS = 20
        const val MAX_SUBFOLDERS = 12
        const val MAX_SEARCH_FILES = 2_000
        const val MAX_SEARCH_FILE_BYTES = 512 * 1024
        val CHANGE_ROOTS = setOf("Download", "Documents", "DCIM", "Pictures", "Movies")
        private val TEXT_SEARCH_EXTRA = setOf("csv", "tsv", "srt", "tex", "conf", "cfg", "properties", "toml", "sql", "vcf", "ics")
        private val LISTING_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

        fun kilobytes(bytes: Long): Long = (bytes + 1023) / 1024

        /** Sizes as `ls -h` prints them: 512, 3.4K, 498M, 1.1G. */
        fun human(bytes: Long): String {
            if (bytes < 1024) return bytes.coerceAtLeast(0).toString()
            var value = bytes.toDouble()
            var unit = 0
            val units = "KMGTP"
            do { value /= 1024.0; unit++ } while (value >= 1024.0 && unit < units.length)
            val text = if (value < 9.95) String.format(Locale.ROOT, "%.1f", value) else Math.round(value).toString()
            return text + units[unit - 1]
        }
    }
}
