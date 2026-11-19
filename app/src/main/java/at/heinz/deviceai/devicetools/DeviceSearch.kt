package at.heinz.deviceai.devicetools

import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.PriorityQueue

/** An installed app as Android's package manager names it. */
internal data class DeviceApp(val label: String, val packageName: String)

/** File kinds a query word can ask for („pdf“, „bilder“); [label] is what a hit is called. */
internal enum class DeviceFileKind(val label: String, val extensions: Set<String>, val words: Set<String>) {
    PDF("PDF", setOf("pdf"), setOf("pdf", "pdfs")),
    IMAGE("Bild", setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng"),
        setOf("bild", "bilder", "foto", "fotos", "photo", "photos")),
    VIDEO("Video", setOf("mp4", "mkv", "mov", "webm", "avi", "3gp"), setOf("video", "videos", "film", "filme")),
    AUDIO("Audio", setOf("mp3", "m4a", "ogg", "opus", "wav", "flac", "aac"), setOf("musik", "audio", "lied", "lieder")),
    APK("APK", setOf("apk"), setOf("apk", "apks")),
    WORD("Word", setOf("docx", "doc", "odt", "rtf"), setOf("word", "docx", "worddatei")),
    SHEET("Tabelle", setOf("xlsx", "xls", "ods", "csv"), setOf("excel", "tabelle", "tabellen", "xlsx")),
    SLIDES("Präsentation", setOf("pptx", "ppt", "odp"), setOf("powerpoint", "pptx", "prasentation", "prasentationen")),
    TEXT("Text", setOf("txt", "md", "log", "json", "xml", "html", "htm", "ini", "yaml", "yml", "kt", "java", "py", "js", "css", "sh"),
        setOf("textdatei", "textdateien", "txt")),
    ARCHIVE("Archiv", setOf("zip", "rar", "7z", "tar", "gz"), setOf("zip", "archiv", "archive"));

    companion object {
        fun ofExtension(extension: String): DeviceFileKind? = values().firstOrNull { extension in it.extensions }

        /** [token] in the folded form of [OwnerLibraryMatcher.tokens] („präsentation“ → „prasentation“). */
        fun ofWord(token: String): DeviceFileKind? = values().firstOrNull { token in it.words }

        /** „PDF“, „Bild“ … or the bare extension in capitals; „Datei“ when the name has none. */
        fun label(extension: String): String = ofExtension(extension)?.label ?: extension.uppercase(Locale.ROOT).ifEmpty { "Datei" }
    }
}

/**
 * The device-wide search („Apps, Dateien, egal was“): one query against installed apps, files and folders that
 * really exist. Names are compared in the form [OwnerLibraryMatcher] uses for spoken PDF names – case, spaces,
 * hyphens and underscores do not count, number words are digits, umlauts equal their transcriptions – for every
 * file type. A type word („pdf“, „bilder“) narrows files to that kind; „app“ or „datei“ in the query narrows the
 * search. Rank: exact name, name starts with it, name contains it (or all its words; a folder name may supply one),
 * and for a query that is only a type word the files of that type, newest first. A near miss (small edit distance)
 * is offered only when nothing matches and is marked as such. Pure: the same lists always give the same hits.
 */
internal object DeviceSearch {
    enum class Scope { ALL, APPS, FILES }
    enum class Kind { APP, FILE, FOLDER }
    enum class Tier { EXACT, PREFIX, CONTAINS, TYPE, NEAR }

    data class Hit(val kind: Kind, val tier: Tier, val name: String, val app: DeviceApp? = null, val file: DeviceFile? = null)

    /** [near]: nothing matched; [hits] merely look similar. [total] counts every match, [hits] at most the limit. */
    class Result(val hits: List<Hit>, val total: Int, val near: Boolean)

    const val DEFAULT_LIMIT = 20
    private const val MAX_NEAR = 5

    private val fillers = setOf("der", "die", "das", "den", "dem", "ein", "eine", "einen", "einem", "mein", "meine",
        "meinen", "meinem", "namens", "heisst", "mit", "von", "zu", "fur", "im", "in", "auf", "am", "alle", "allen",
        "bitte", "suche", "such", "finde", "find", "nach", "ordner")
    private val appWords = setOf("app", "apps")
    private val fileWords = setOf("datei", "dateien", "file", "files")

    fun scope(text: String?): Scope? = when (text?.trim()?.lowercase(Locale.ROOT)) {
        "apps", "app" -> Scope.APPS
        "dateien", "datei", "files", "file", "ordner" -> Scope.FILES
        "alles", "alle", "all" -> Scope.ALL
        else -> null
    }

    /** The comparable words of a file or folder name: its extension does not count. */
    fun nameTokens(name: String, directory: Boolean): List<String> =
        OwnerLibraryMatcher.tokens(if (directory || DevicePaths.extension(name).isEmpty()) name else name.substringBeforeLast('.'))

    private fun tier(wanted: List<String>, have: List<String>): Tier? {
        val wantedKey = wanted.joinToString("")
        val haveKey = have.joinToString("")
        if (wantedKey.isEmpty() || haveKey.isEmpty()) return null
        return when {
            haveKey == wantedKey -> Tier.EXACT
            wantedKey.length >= 2 && haveKey.startsWith(wantedKey) -> Tier.PREFIX
            wantedKey.length >= 2 && haveKey.contains(wantedKey) -> Tier.CONTAINS
            wanted.all { word -> have.any { it == word || (word.length >= 3 && it.startsWith(word)) } } -> Tier.CONTAINS
            else -> null
        }
    }

    private fun best(a: Tier?, b: Tier?): Tier? = when {
        a == null -> b
        b == null -> a
        else -> if (a.ordinal <= b.ordinal) a else b
    }

    /** One query, analysed once: its words, the kind and scope it names, and how a name matches it. */
    class Query(text: String, scope: Scope? = null) {
        private val tokens = OwnerLibraryMatcher.tokens(text)
        private val base = tokens.filterNot { it in fillers || it in appWords || it in fileWords }.ifEmpty { tokens }
        private val type = base.firstNotNullOfOrNull { DeviceFileKind.ofWord(it) }
        private val rest = base.filter { DeviceFileKind.ofWord(it) == null }
        private val raw = text.trim().lowercase(Locale.ROOT)
        val scope: Scope = scope ?: when {
            tokens.any { it in appWords } && tokens.none { it in fileWords } -> Scope.APPS
            tokens.any { it in fileWords } && tokens.none { it in appWords } -> Scope.FILES
            else -> Scope.ALL
        }
        val empty: Boolean get() = tokens.isEmpty()
        internal val nearKey: String = base.joinToString("")

        private fun match(have: List<String>): Tier? = best(tier(tokens, have), if (base != tokens) tier(base, have) else null)

        fun app(app: DeviceApp): Tier? {
            if (empty) return null
            val name = app.packageName.lowercase(Locale.ROOT)
            val byPackage = when {
                name == raw -> Tier.EXACT
                raw.length >= 3 && ' ' !in raw && name.contains(raw) -> Tier.CONTAINS
                else -> null
            }
            return best(match(OwnerLibraryMatcher.tokens(app.label)), byPackage)
        }

        /** [name] is a file or folder name, [folder] its folder below the storage root ("" = the root). */
        fun file(name: String, folder: String, directory: Boolean): Tier? {
            if (empty) return null
            val have = nameTokens(name, directory)
            if (directory) return match(have)
            match(have)?.let { return it }
            if (type != null && DevicePaths.extension(name) in type.extensions) {
                if (rest.isEmpty()) return Tier.TYPE
                tier(rest, have)?.let { return it }
            }
            // A word of the query may name the folder („argon labor“); the file name must carry the rest.
            val inFolder = OwnerLibraryMatcher.tokens(folder)
            if (inFolder.isEmpty()) return null
            val inName = base.filterNot { word -> inFolder.any { it == word || (word.length >= 3 && it.startsWith(word)) } }
            return if (inName.isNotEmpty() && inName.size < base.size && tier(inName, have) != null) Tier.CONTAINS else null
        }

        /** One edit for short names, two otherwise, against the name or its beginning; under four characters nothing is „similar“. */
        internal fun nearDistance(haveKey: String): Int? {
            val wanted = nearKey
            if (wanted.length < 4 || haveKey.isEmpty()) return null
            val allowed = if (wanted.length <= 5) 1 else 2
            val from = (wanted.length - allowed).coerceAtLeast(1)
            if (haveKey.length < from) return null
            var best = Int.MAX_VALUE
            for (length in from..(wanted.length + allowed)) {
                if (length > haveKey.length) break
                best = minOf(best, OwnerLibraryMatcher.editDistance(wanted, haveKey.substring(0, length), allowed))
            }
            return best.takeIf { it <= allowed }
        }
    }

    private val order = compareBy<Hit>({ it.tier.ordinal }, { kindOrder(it.kind) }, { -(it.file?.modifiedMillis ?: 0L) },
        { it.name.lowercase(Locale.GERMAN) }, { it.file?.relative ?: it.app?.packageName.orEmpty() })

    private fun kindOrder(kind: Kind): Int = when (kind) { Kind.APP -> 0; Kind.FILE -> 1; Kind.FOLDER -> 2 }

    /**
     * Takes candidates one by one (a storage walk can see a hundred thousand entries) and keeps only the best
     * [limit] hits and a handful of near misses. [describe] is asked for a file's size and date only when it counts.
     */
    class Collector(private val query: Query, limit: Int = DEFAULT_LIMIT) {
        private val limit = limit.coerceIn(1, 250)
        private val kept = PriorityQueue(order.reversed())
        private val near = PriorityQueue(compareBy<Pair<Hit, Int>> { it.second }.thenBy(order) { it.first }.reversed())
        var total = 0
            private set

        fun offer(app: DeviceApp) {
            if (query.scope == Scope.FILES) return
            val tier = query.app(app)
            if (tier != null) keep(Hit(Kind.APP, tier, app.label, app = app))
            else if (total == 0) query.nearDistance(OwnerLibraryMatcher.key(app.label))?.let { suggest(Hit(Kind.APP, Tier.NEAR, app.label, app = app), it) }
        }

        fun offer(name: String, folder: String, directory: Boolean, describe: () -> DeviceFile) {
            if (query.scope == Scope.APPS) return
            val kind = if (directory) Kind.FOLDER else Kind.FILE
            val tier = query.file(name, folder, directory)
            if (tier != null) keep(Hit(kind, tier, name, file = describe()))
            else if (total == 0) query.nearDistance(nameTokens(name, directory).joinToString(""))?.let { suggest(Hit(kind, Tier.NEAR, name, file = describe()), it) }
        }

        fun offer(file: DeviceFile) = offer(file.name, file.folder, file.directory) { file }

        private fun keep(hit: Hit) {
            total++
            kept += hit
            if (kept.size > limit) kept.poll()
        }

        private fun suggest(hit: Hit, distance: Int) {
            near += hit to distance
            if (near.size > MAX_NEAR) near.poll()
        }

        fun result(): Result {
            if (total > 0) return Result(kept.sortedWith(order), total, false)
            val suggested = near.sortedWith(compareBy<Pair<Hit, Int>> { it.second }.thenBy(order) { it.first }).map { it.first }
            return Result(suggested, 0, suggested.isNotEmpty())
        }
    }

    fun search(query: String, apps: List<DeviceApp>, files: List<DeviceFile>, scope: Scope? = null,
               limit: Int = DEFAULT_LIMIT): Result {
        val collector = Collector(Query(query, scope), limit)
        apps.forEach(collector::offer)
        files.forEach(collector::offer)
        return collector.result()
    }

    // ── Wording ─────────────────────────────────────────────────────────────────────────────────────────────

    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
    private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)

    fun date(millis: Long, zone: ZoneId): String = if (millis <= 0L) "unbekannt" else shortDate.format(Instant.ofEpochMilli(millis).atZone(zone))
    fun dateTime(millis: Long, zone: ZoneId): String = if (millis <= 0L) "unbekannt" else longDate.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** `1. [DATEI] /sdcard/Download/a.pdf · PDF · 1,2 MB · geändert 17.11.2026` – the line the chain contract parses. */
    fun line(number: Int, hit: Hit, zone: ZoneId): String = when (hit.kind) {
        Kind.APP -> "$number. [APP] ${clean(hit.app!!.label)} · ${hit.app.packageName}"
        Kind.FOLDER -> "$number. [ORDNER] ${hit.file!!.path}"
        Kind.FILE -> hit.file!!.let { file ->
            "$number. [DATEI] ${file.path} · ${DeviceFileKind.label(file.extension)} · ${OwnerLibraryMatcher.sizeText(file.sizeBytes)} · geändert ${date(file.modifiedMillis, zone)}"
        }
    }

    /**
     * The observation of a search, at most about [budget] characters: a counted header, one line per hit, what was
     * left out and what can be done with a hit. [searched]: what was really looked through; [notes]: bounds and
     * missing grants, said as they are.
     */
    fun render(query: String, result: Result, zone: ZoneId, searched: String, notes: List<String>, budget: Int): String {
        val shownQuery = clean(query).take(80)
        if (result.hits.isEmpty())
            return (listOf("Gerätesuche „$shownQuery“ – nichts gefunden. Durchsucht: $searched.") + notes).joinToString("\n")
        val header = if (result.near) "Gerätesuche „$shownQuery“ – kein Name passt genau. Ähnlich heißen:"
            else "Gerätesuche „$shownQuery“ – ${result.total} Treffer:"
        val kinds = result.hits.map { it.kind }.toSet()
        val next = buildString {
            append("Weiter:")
            if (Kind.FILE in kinds) append(" read_file liest eine Datei, open_file zeigt sie an.")
            if (Kind.APP in kinds) append(" get_app_info liest App-Details, launch_app öffnet sie nach Bestätigung.")
            if (Kind.FOLDER in kinds) append(" list_files zeigt einen Ordner.")
        }
        val tail = notes + next
        val room = budget - header.length - tail.sumOf { it.length + 1 } - 60
        val lines = ArrayList<String>()
        var used = 0
        for ((index, hit) in result.hits.withIndex()) {
            val text = line(index + 1, hit, zone)
            if (lines.isNotEmpty() && used + text.length + 1 > room) break
            lines += text
            used += text.length + 1
        }
        val known = if (result.near) result.hits.size else result.total
        val left = known - lines.size
        val more = if (left > 0) listOf("$left weitere Treffer nicht gezeigt – die Suche enger fassen.") else emptyList()
        return (listOf(header) + lines + more + tail).joinToString("\n")
    }

    /** No control or invisible characters in a line the brain and the chain read. */
    fun clean(text: String): String = text.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
        .replace(Regex("\\s+"), " ").trim()
}
