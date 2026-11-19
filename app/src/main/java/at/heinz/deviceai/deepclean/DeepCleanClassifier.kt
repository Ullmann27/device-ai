package at.heinz.deviceai.deepclean

import java.util.Locale

/** Protection levels (owner order 19.11.2026). */
internal enum class DeepCleanLevel {
    /** Removed automatically after an explicit general clean-up order. Only Continuum's own regenerable data. */
    GREEN,
    /** Only with a preview and the owner's approval of exactly these files. */
    YELLOW,
    /** Protected in principle: never proposed. */
    RED,
    /** Not touched in this run at all: in use, too new, a link, outside its root. */
    EXCLUDED
}

/** Where an entry was found. GREEN is only possible below the app's own roots. */
internal enum class DeepCleanRoot { OWN_CACHE, OWN_EXTERNAL_CACHE, OWN_FILES, SHARED }

/** What a finding is, for the report by category. */
internal enum class DeepCleanCategory(val label: String) {
    OWN_CACHE("Eigener Zwischenspeicher von Continuum"),
    OWN_TEMP("Liegengebliebene eigene Arbeitsdateien"),
    OWN_PROTECTED("Continuums Modelle, Gedächtnis und Einstellungen"),
    PARTIAL_DOWNLOAD("Abgebrochene Downloads"),
    APK("Installationspakete (APK)"),
    DUPLICATE("Identische Dateien (Duplikate)"),
    LARGE_DOWNLOAD("Große Dateien im Download-Ordner"),
    PREVIEW("Vorschaubilder, die Android neu erzeugt"),
    PHOTO("Fotos"),
    VIDEO("Videos"),
    AUDIO("Musik und Aufnahmen"),
    DOCUMENT("Dokumente"),
    WHATSAPP("WhatsApp-Medien"),
    DOWNLOAD_OTHER("Weitere Downloads"),
    BACKUP("Sicherungen und Wiederherstellungsdateien"),
    PROJECT("Projekte und Quellcode"),
    APP_DATA("Daten anderer Apps"),
    SYSTEM("Systemdateien"),
    LINK("Verknüpfungen"),
    OTHER("Sonstige Dateien")
}

/** How a removal could be undone. */
internal enum class DeepCleanRecovery(val label: String) {
    /** Continuum or Android rebuild it when needed. */
    REGENERABLE("wird bei Bedarf neu erzeugt"),
    /** Android's trash keeps it until the trash is emptied; restorable. */
    TRASH("im Android-Papierkorb wiederherstellbar"),
    /** Gone for good. */
    PERMANENT("endgültig")
}

/** One file as the scan saw it – only measured facts. [relative] uses `/` and is below the scan root. */
internal data class DeepCleanEntry(
    val root: DeepCleanRoot,
    val relative: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val symlink: Boolean = false,
    /** The canonical path stayed under the canonical root. */
    val insideRoot: Boolean = true
) {
    val name: String get() = relative.substringAfterLast('/')
    val folder: String get() = relative.substringBeforeLast('/', "")
    val extension: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it != name.lowercase(Locale.ROOT) }.orEmpty()
    val key: String get() = root.name + ":" + relative
}

/** The classifier's verdict for one entry. [origin]: where it comes from, as the owner reads it. */
internal data class DeepCleanFinding(
    val entry: DeepCleanEntry,
    val level: DeepCleanLevel,
    val category: DeepCleanCategory,
    val origin: String,
    val reason: String,
    val recovery: DeepCleanRecovery,
    /** For a duplicate: the copy that stays, and the shared SHA-256. */
    val keeper: DeepCleanEntry? = null,
    val sha256: String? = null
)

/**
 * The deterministic rules that decide GREEN / YELLOW / RED. No model output, no guess: an entry that no rule names
 * explicitly is RED. GREEN is only reachable for the app's own roots, and there only for (a) the app's cache
 * directories, which Android itself may empty at any time (Context.getCacheDir contract), outside the folders the
 * app needs to run, and (b) temporary files of the app's own atomic writes (File.createTempFile pattern) that are
 * older than [TEMP_MIN_AGE_MILLIS]. Nothing in shared storage is ever GREEN.
 */
internal object DeepCleanClassifier {
    /** Own cache folders the app needs to run or to resume (model kernels, update staging, own diagnosis ledger). */
    val OWN_CACHE_PROTECTED = setOf("litertlm", "owner-update", "system-diagnostics", "deepclean")
    /** An own file changed this recently may belong to running work. */
    const val ACTIVE_MILLIS = 15 * 60_000L
    /** An orphaned temp file of an own atomic write is older than this. */
    const val TEMP_MIN_AGE_MILLIS = 24 * 3_600_000L
    /** A partial download changed within this time may still be running. */
    const val PARTIAL_MIN_AGE_MILLIS = 24 * 3_600_000L
    const val LARGE_BYTES = 100L * 1024 * 1024

    /** createTempFile(prefix, ".tmp"): prefix of at least three characters, then digits. */
    private val ownTemp = Regex("^\\.?[a-z][a-z0-9-]{1,40}-?[0-9]{5,20}\\.tmp$")
    private val partial = setOf("crdownload", "part", "partial", "download", "tmp")
    private val apk = setOf("apk", "apks", "xapk", "apkm")
    private val photo = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "dng", "raw", "gif", "bmp")
    private val video = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v")
    private val audio = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "awb")
    private val document = setOf("pdf", "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "txt", "rtf", "csv", "md", "epub")
    private val source = setOf("kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go", "gradle", "swift", "sh", "json", "xml", "yml", "yaml")
    private val backup = setOf("bak", "backup", "ab", "crypt14", "crypt15", "nbu", "sbu")
    private val model = setOf("litertlm", "task", "tflite", "gguf", "bin", "onnx", "safetensors", "ckpt")
    private val protectedTop = setOf("dcim", "pictures", "movies", "music", "documents", "recordings", "podcasts", "audiobooks", "ringtones",
        "alarms", "notifications", "audio", "camera", "screenshots", "whatsapp", "telegram", "signal")
    private val projectMarkers = setOf("androidstudioprojects", "projects", "projekte", "git", "repos", "source", "src", "termux", "code")
    private val backupFolders = setOf("backup", "backups", "sicherung", "sicherungen", "smart switch", "smartswitch", "titanium backup")

    private fun lower(text: String) = text.lowercase(Locale.ROOT)

    fun classify(entry: DeepCleanEntry, nowMillis: Long, active: Set<String> = emptySet()): DeepCleanFinding {
        val age = nowMillis - entry.modifiedMillis
        if (entry.symlink) return red(entry, DeepCleanCategory.LINK, "Verknüpfung", "Verknüpfungen werden nie verfolgt und nie gelöscht.")
        if (!entry.insideRoot || entry.relative.isEmpty() || entry.relative.split('/').any { it == ".." || it.isEmpty() })
            return excluded(entry, DeepCleanCategory.OTHER, "Der Pfad führt aus dem geprüften Bereich hinaus.")
        if (entry.key in active) return excluded(entry, categoryOf(entry), "Diese Datei wird gerade benutzt.")
        return when (entry.root) {
            DeepCleanRoot.OWN_CACHE, DeepCleanRoot.OWN_EXTERNAL_CACHE -> ownCache(entry, age)
            DeepCleanRoot.OWN_FILES -> ownFiles(entry, age)
            DeepCleanRoot.SHARED -> shared(entry, age)
        }
    }

    private fun ownCache(entry: DeepCleanEntry, age: Long): DeepCleanFinding {
        val top = lower(entry.relative.substringBefore('/'))
        if (top in OWN_CACHE_PROTECTED)
            return red(entry, DeepCleanCategory.OWN_PROTECTED, "Continuum", "Diesen Zwischenspeicher braucht Continuum zum Arbeiten (Modell, Update oder Diagnose).")
        if (age < ACTIVE_MILLIS) return excluded(entry, DeepCleanCategory.OWN_CACHE, "Vor weniger als 15 Minuten geändert – könnte gerade in Arbeit sein.")
        return DeepCleanFinding(entry, DeepCleanLevel.GREEN, DeepCleanCategory.OWN_CACHE, "Continuum (eigener Zwischenspeicher)",
            "Zwischenspeicher, den Android selbst jederzeit leeren darf; Continuum erzeugt ihn bei Bedarf neu.", DeepCleanRecovery.REGENERABLE)
    }

    private fun ownFiles(entry: DeepCleanEntry, age: Long): DeepCleanFinding {
        if (ownTemp.matches(lower(entry.name)) && age >= TEMP_MIN_AGE_MILLIS)
            return DeepCleanFinding(entry, DeepCleanLevel.GREEN, DeepCleanCategory.OWN_TEMP, "Continuum (abgebrochener Speichervorgang)",
                "Übrig gebliebene Zwischendatei eines eigenen Speichervorgangs, älter als einen Tag; sie wird nie wieder gelesen.",
                DeepCleanRecovery.REGENERABLE)
        return red(entry, DeepCleanCategory.OWN_PROTECTED, "Continuum",
            if (entry.extension in model) "KI-Modelle bleiben immer." else "Gedächtnis, Gespräche, Aufgaben und Einstellungen bleiben immer.")
    }

    /** The category of a shared file by its folder and type, before any rule about what may happen to it. */
    fun categoryOf(entry: DeepCleanEntry): DeepCleanCategory {
        if (entry.root != DeepCleanRoot.SHARED) return if (entry.root == DeepCleanRoot.OWN_FILES) DeepCleanCategory.OWN_PROTECTED else DeepCleanCategory.OWN_CACHE
        val parts = entry.relative.split('/').map(::lower)
        val top = parts.first()
        val ext = entry.extension
        return when {
            top == "android" -> if (parts.getOrNull(1) == "media" && parts.any { it.contains("whatsapp") }) DeepCleanCategory.WHATSAPP else DeepCleanCategory.APP_DATA
            parts.any { it.contains("whatsapp") } -> DeepCleanCategory.WHATSAPP
            parts.any { it in backupFolders } || ext in backup -> DeepCleanCategory.BACKUP
            parts.any { it in projectMarkers } || ext in source -> DeepCleanCategory.PROJECT
            parts.any { it == ".thumbnails" } -> DeepCleanCategory.PREVIEW
            top == "download" || top == "downloads" -> when {
                ext in partial -> DeepCleanCategory.PARTIAL_DOWNLOAD
                ext in apk -> DeepCleanCategory.APK
                else -> mediaCategory(ext) ?: DeepCleanCategory.DOWNLOAD_OTHER
            }
            ext in apk -> DeepCleanCategory.APK
            else -> mediaCategory(ext) ?: if (top.startsWith(".") || top == "lost.dir") DeepCleanCategory.SYSTEM else DeepCleanCategory.OTHER
        }
    }

    private fun mediaCategory(ext: String): DeepCleanCategory? = when (ext) {
        in photo -> DeepCleanCategory.PHOTO
        in video -> DeepCleanCategory.VIDEO
        in audio -> DeepCleanCategory.AUDIO
        in document -> DeepCleanCategory.DOCUMENT
        else -> null
    }

    /** true = the file sits in a folder of the owner's personal media or documents (never proposed there). */
    fun personalFolder(entry: DeepCleanEntry): Boolean {
        if (entry.root != DeepCleanRoot.SHARED) return false
        val parts = entry.relative.split('/').map(::lower)
        return parts.first() in protectedTop || parts.any { it.contains("whatsapp") || it in backupFolders || it in projectMarkers }
    }

    private fun shared(entry: DeepCleanEntry, age: Long): DeepCleanFinding {
        val category = categoryOf(entry)
        val parts = entry.relative.split('/').map(::lower)
        return when (category) {
            DeepCleanCategory.APP_DATA -> red(entry, category, "andere App", "Daten anderer Apps verändert Continuum nicht.")
            DeepCleanCategory.SYSTEM -> red(entry, category, "Android", "Systemdateien bleiben unangetastet.")
            DeepCleanCategory.BACKUP -> red(entry, category, "Sicherung", "Sicherungen und Wiederherstellungsdateien bleiben immer.")
            DeepCleanCategory.PROJECT -> red(entry, category, "Projekt", "Projektdateien und Quellcode bleiben immer.")
            DeepCleanCategory.WHATSAPP -> red(entry, category, "WhatsApp", "WhatsApp-Medien sind persönliche Daten.")
            DeepCleanCategory.PREVIEW -> if (age < ACTIVE_MILLIS) excluded(entry, category, "Gerade erst erzeugt.")
                else yellow(entry, category, "Android-Galerie (Vorschaubilder)", "Vorschaubilder erzeugt Android bei Bedarf neu; die Fotos selbst bleiben.")
            DeepCleanCategory.PARTIAL_DOWNLOAD -> if (age < PARTIAL_MIN_AGE_MILLIS)
                excluded(entry, category, "In den letzten 24 Stunden geändert – der Download läuft vielleicht noch.")
                else yellow(entry, category, "abgebrochener Download", "Unvollständige Datei, seit über einem Tag unverändert.")
            DeepCleanCategory.APK -> if (age < ACTIVE_MILLIS) excluded(entry, category, "Gerade erst geändert.")
                else yellow(entry, category, if (parts.first() == "download" || parts.first() == "downloads") "Download" else "Installationspaket",
                    "Installationsdatei einer App; nach der Installation wird sie meist nicht mehr gebraucht.")
            DeepCleanCategory.PHOTO, DeepCleanCategory.VIDEO, DeepCleanCategory.AUDIO, DeepCleanCategory.DOCUMENT, DeepCleanCategory.DOWNLOAD_OTHER ->
                if (!personalFolder(entry) && (parts.first() == "download" || parts.first() == "downloads") && entry.sizeBytes >= LARGE_BYTES && age >= ACTIVE_MILLIS)
                    yellow(entry, DeepCleanCategory.LARGE_DOWNLOAD, "Download", "Große Datei im Download-Ordner – nur du entscheidest, ob sie weg kann.")
                else red(entry, category, if (personalFolder(entry)) "deine Mediathek" else "deine Dateien",
                    "Persönliche Datei: Sie wird nur gelöscht, wenn du genau sie auswählst und bestätigst.")
            else -> red(entry, category, "unbekannt", "Herkunft und Entbehrlichkeit sind nicht nachweisbar – sie bleibt.")
        }
    }

    /**
     * Groups of byte-identical files (equal SHA-256): one copy stays (the keeper), the others are YELLOW – unless they
     * sit in a protected personal folder too, then they stay RED and are only mentioned.
     */
    fun duplicates(groups: List<List<DeepCleanEntry>>, hashOf: (DeepCleanEntry) -> String, nowMillis: Long): List<DeepCleanFinding> =
        groups.filter { it.size >= 2 }.flatMap { group ->
            val keeper = keeperOf(group)
            group.filter { it != keeper }.map { copy ->
                val repeated = repeatedDownload.containsMatchIn(copy.name)
                val reason = (if (repeated) "Wiederholter Download: " else "") + "Inhalt identisch mit „" + keeper.relative + "“ (SHA-256 geprüft)."
                when {
                    personalFolder(copy) -> DeepCleanFinding(copy, DeepCleanLevel.RED, DeepCleanCategory.DUPLICATE, "deine Mediathek",
                        "$reason Sie liegt in deinen persönlichen Ordnern und wird deshalb nicht vorgeschlagen.", DeepCleanRecovery.TRASH, keeper, hashOf(copy))
                    nowMillis - copy.modifiedMillis < ACTIVE_MILLIS -> DeepCleanFinding(copy, DeepCleanLevel.EXCLUDED, DeepCleanCategory.DUPLICATE,
                        "Download", "Gerade erst geändert.", DeepCleanRecovery.TRASH, keeper, hashOf(copy))
                    else -> DeepCleanFinding(copy, DeepCleanLevel.YELLOW, DeepCleanCategory.DUPLICATE,
                        if (repeated) "wiederholter Download" else "Kopie", reason, DeepCleanRecovery.TRASH, keeper, hashOf(copy))
                }
            }
        }

    private val repeatedDownload = Regex("\\s\\(\\d{1,3}\\)\\.[A-Za-z0-9]{1,5}$|-\\d{1,2}\\.[A-Za-z0-9]{1,5}$")

    /** The copy that stays: in a personal folder first, then without „ (1)“, then the oldest, then the shortest path. */
    fun keeperOf(group: List<DeepCleanEntry>): DeepCleanEntry = group.sortedWith(compareBy<DeepCleanEntry>(
        { if (personalFolder(it)) 0 else 1 }, { if (repeatedDownload.containsMatchIn(it.name)) 1 else 0 }, { it.modifiedMillis },
        { it.relative.length }, { it.relative })).first()

    private fun red(entry: DeepCleanEntry, category: DeepCleanCategory, origin: String, reason: String) =
        DeepCleanFinding(entry, DeepCleanLevel.RED, category, origin, reason, DeepCleanRecovery.PERMANENT)

    private fun excluded(entry: DeepCleanEntry, category: DeepCleanCategory, reason: String) =
        DeepCleanFinding(entry, DeepCleanLevel.EXCLUDED, category, "—", reason, DeepCleanRecovery.PERMANENT)

    private fun yellow(entry: DeepCleanEntry, category: DeepCleanCategory, origin: String, reason: String) =
        DeepCleanFinding(entry, DeepCleanLevel.YELLOW, category, origin, reason, DeepCleanRecovery.TRASH)

    /** true when [finding] belongs to one of the groups the owner protected. */
    fun protectedBy(finding: DeepCleanFinding, keep: Set<DeepCleanGroup>): Boolean {
        if (keep.isEmpty()) return false
        val entry = finding.entry
        val top = lower(entry.relative.substringBefore('/'))
        val ext = entry.extension
        return keep.any { group ->
            when (group) {
                DeepCleanGroup.PHOTOS -> ext in photo
                DeepCleanGroup.VIDEOS -> ext in video
                DeepCleanGroup.AUDIO -> ext in audio
                DeepCleanGroup.DOCUMENTS -> ext in document
                DeepCleanGroup.DOWNLOADS -> entry.root == DeepCleanRoot.SHARED && (top == "download" || top == "downloads")
                DeepCleanGroup.WHATSAPP -> lower(entry.relative).contains("whatsapp")
                DeepCleanGroup.APKS -> ext in apk
                DeepCleanGroup.DUPLICATES -> finding.category == DeepCleanCategory.DUPLICATE
                DeepCleanGroup.PARTIAL -> finding.category == DeepCleanCategory.PARTIAL_DOWNLOAD
                DeepCleanGroup.LARGE -> finding.category == DeepCleanCategory.LARGE_DOWNLOAD
                DeepCleanGroup.PERSONAL -> entry.root == DeepCleanRoot.SHARED &&
                    (ext in photo || ext in video || ext in audio || ext in document || personalFolder(entry))
            }
        }
    }

    /** The folder key the learning store counts refusals for („Download“, „DCIM/Camera“). */
    fun folderKey(entry: DeepCleanEntry): String = entry.root.name + ":" + entry.folder.split('/').take(2).joinToString("/")
}
