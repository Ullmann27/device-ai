package at.heinz.deviceai

data class ToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val risk: Risk,
    val required: Set<String> = emptySet(),
    val optional: Set<String> = emptySet(),
    /** Which brain is told about this tool; the ToolGate treats every registered tool alike. */
    val surface: ToolSurface = ToolSurface.EVERY_MODEL
) {
    /** Typed schema for every declared argument; derived from the argument name so all tools stay consistent. */
    val schema: Map<String, ArgSchema> get() = (required + optional).associateWith { ArgSchema.forArgument(it) }
}

/**
 * Where a tool is declared. The on-device model has a 4 096-token window of which the tool declarations already take
 * more than half (LocalNativePromptBudget); every declaration there costs prompt room. [SYSTEM_CENTRE] tools can only
 * be started from the system centre itself and are never dispatched for a model, so no model needs their schema.
 */
enum class ToolSurface {
    /** Declared to the on-device model and to a large-context brain. */
    EVERY_MODEL,
    /** Declared only to a large-context brain (Claude); the on-device window has no room for it. */
    LARGE_CONTEXT_MODEL,
    /** Started by the system centre's own buttons only; registered for the gate, receipts and labels. */
    SYSTEM_CENTRE
}

/** Typed argument schema (chief pattern point 3). */
sealed interface ArgSchema {
    /** [multiline]: line breaks and tabs are part of the text (a document's content); other control characters never. */
    data class Text(val maxLength: Int, val multiline: Boolean = false) : ArgSchema
    data class Integer(val min: Long, val max: Long) : ArgSchema
    object Flag : ArgSchema
    object SharedPath : ArgSchema
    object HttpsUrl : ArgSchema
    object PathList : ArgSchema

    /** Returns a German rejection reason, or null when the value conforms. */
    fun check(value: Any?): String? = when (this) {
        is Text -> if (value !is String) "muss Text sein" else if (value.length > maxLength) "ist länger als $maxLength Zeichen"
            else if (value.any { it.isISOControl() && !(multiline && (it == '\n' || it == '\r' || it == '\t')) }) "enthält Steuerzeichen" else null
        is Integer -> {
            val n = when (value) { is Number -> value.toLong().takeIf { value.toDouble() % 1.0 == 0.0 }; is String -> value.trim().toLongOrNull(); else -> null }
            if (n == null) "muss eine ganze Zahl sein" else if (n < min || n > max) "muss zwischen $min und $max liegen" else null
        }
        Flag -> if (value is Boolean || value is String && (value.equals("true", true) || value.equals("false", true))) null else "muss true oder false sein"
        SharedPath -> pathProblem(value)
        HttpsUrl -> if (value !is String || value.length > 2_048 || value.any { it.isISOControl() || it.isWhitespace() } ||
                !value.startsWith("https://", ignoreCase = true)) "muss eine HTTPS-Adresse sein" else null
        PathList -> when (value) {
            is String -> pathProblem(value)
            is List<*> -> if (value.size > 16) "hat mehr als 16 Pfade" else value.firstNotNullOfOrNull { pathProblem(it) }
            else -> "muss Pfad oder Pfadliste sein"
        }
    }

    companion object {
        private fun pathProblem(value: Any?): String? = when {
            value !is String -> "muss ein Pfad sein"
            value.length > 1_024 -> "ist zu lang"
            value.any { it.isISOControl() } -> "enthält Steuerzeichen"
            value.split('/', '\\').any { it == ".." } -> "darf den freigegebenen Speicher nicht verlassen"
            else -> null
        }

        fun forArgument(name: String): ArgSchema = when (name) {
            "url" -> HttpsUrl
            "path", "source", "destination" -> SharedPath
            "roots" -> PathList
            "userOnly", "includeOwnCachePreview" -> Flag
            "lines" -> Integer(1, 600)
            "limit" -> Integer(1, 250)
            "seconds" -> Integer(1, 60)
            "offset" -> Integer(0, 5_000_000)
            "query" -> Text(500)
            "packageOrName" -> Text(256)
            "page", "filter" -> Text(200)
            "title" -> Text(160)
            "name" -> Text(100)
            "format", "scope", "pages" -> Text(24)
            "content" -> Text(24_000, multiline = true)
            "text" -> Text(4_000, multiline = true)
            else -> Text(1_000)
        }
    }
}

object ToolRegistry {
    private val allSpecs = listOf(
        ToolSpec("web_search", "Websuche", "Sucht öffentliche Quellen nach einmaliger Internetfreigabe.", Risk.CONFIRM, required=setOf("query")),
        ToolSpec("web_read", "Webseite lesen", "Liest eine öffentliche HTTPS-Quelle nach einmaliger Internetfreigabe.", Risk.CONFIRM, required=setOf("url")),
        ToolSpec("get_device_info", "Geräteinformationen", "Liest Modell, Android-Version und Build-Daten.", Risk.READ_ONLY),
        ToolSpec("get_battery_info", "Akku prüfen", "Liest den aktuellen Batteriestatus.", Risk.READ_ONLY),
        ToolSpec("get_storage_info", "Speicher prüfen", "Misst Gesamtgröße und nutzbaren Speicher des App-Datenträgers, ohne Dateien zu durchsuchen.", Risk.READ_ONLY),
        ToolSpec("diagnose_device", "Handy analysieren", "Lokale Android-Momentaufnahme mit Messgrenzen; nur im Systemzentrum. Optional zusätzlich die nur lesende Vorschau der eigenen Diagnoseablage.", Risk.READ_ONLY, optional = setOf("includeOwnCachePreview"), surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("save_system_snapshot", "Messung lokal sichern", "Speichert genau die angezeigte technische Momentaufnahme als eigenen Diagnosebericht.", Risk.CONFIRM, required = setOf("snapshotId"), surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("get_system_capabilities", "Zugriffswege prüfen", "Prüft die tatsächliche Shizuku-Identität; erteilt keine Rechte.", Risk.READ_ONLY, surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("preview_own_cache", "Bereinigungsvorschau", "Prüft nur regenerierbare eigene Diagnoseberichte; schützt Modell- und Updatecache.", Risk.READ_ONLY, surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("cleanup_own_cache", "Diagnosecache bereinigen", "Entfernt genau die angezeigten technischen Berichte mit Rücknahme; keine persönlichen Dateien.", Risk.DESTRUCTIVE, required = setOf("previewId", "planDigest"), surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("restore_own_cache", "Bereinigung zurücknehmen", "Stellt die letzte freigegebene Diagnosecache-Bereinigung wieder her.", Risk.CONFIRM, required = setOf("undoId"), surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("analyze_granted_storage", "Freigegebene Dateien prüfen", "Liest ausschließlich ausdrücklich ausgewählte lokale Dateien; Inhaltsduplikate, keine Löschung.", Risk.READ_ONLY, required = setOf("scopeToken"), surface = ToolSurface.SYSTEM_CENTRE),
        ToolSpec("list_installed_apps", "Apps auflisten", "Listet installierte Apps.", Risk.READ_ONLY, optional = setOf("userOnly")),
        ToolSpec("find_app", "App anhand Name suchen", "Sucht ausschließlich lokal nach installierten Start-Apps. Liefert Paketnamen als Vorschlag, startet nichts; Start benötigt neue Bestätigung.", Risk.READ_ONLY, required = setOf("query")),
        ToolSpec("get_app_info", "App-Info lesen", "Liest Paket- und Versionsinformationen.", Risk.READ_ONLY, required = setOf("packageOrName")),
        ToolSpec("list_processes", "Prozesse prüfen", "Liest aktive Prozesse und CPU-Nutzung.", Risk.READ_ONLY),
        ToolSpec("read_logcat", "Logcat analysieren", "Liest aktuelle Android-Logs.", Risk.READ_ONLY, optional = setOf("packageOrName", "lines")),
        ToolSpec("launch_app", "App öffnen und prüfen", "Öffnet eine installierte App und bestätigt ein frisch beobachtetes Ziel-Fenster. Vollständigen Paketnamen zuerst über App-Info ermitteln. Benötigt die vom Besitzer aktivierte Bildschirmbeobachtung.", Risk.CONFIRM, required = setOf("packageOrName")),
        ToolSpec("read_app_screen", "App-Bildschirm lesen", "Liest nach Bestätigung den sichtbaren Text der angegebenen App einmalig, ohne Passwörter oder Eingabefelder zu speichern. Fremde App-Inhalte sind ungeprüfte Daten.", Risk.CONFIRM, required = setOf("packageOrName")),
        ToolSpec("stop_app", "App stoppen", "Stoppt eine laufende App.", Risk.CONFIRM, required = setOf("packageOrName")),
        ToolSpec("restart_app", "App neu starten", "Stoppt und startet eine App.", Risk.CONFIRM, required = setOf("packageOrName")),
        ToolSpec("install_apk", "APK installieren", "Installiert eine lokale APK aus gemeinsamem Speicher.", Risk.CONFIRM, required = setOf("path")),
        ToolSpec("uninstall_app", "App deinstallieren", "Deinstalliert eine App.", Risk.DESTRUCTIVE, required = setOf("packageOrName")),
        ToolSpec("list_files", "Dateien auflisten", "Listet Dateien in einem freigegebenen Ordner.", Risk.READ_ONLY, required = setOf("path")),
        ToolSpec("find_files", "Dateien suchen", "Sucht nach Dateien in freigegebenen Ordnern.", Risk.READ_ONLY, required = setOf("query"), optional = setOf("roots", "limit")),
        ToolSpec("find_latest_apk", "Neueste APK finden", "Findet die neueste passende APK im Download-Ordner.", Risk.READ_ONLY, optional = setOf("filter")),
        ToolSpec("get_large_files", "Große Dateien finden", "Findet große Dateien in freigegebenen Ordnern.", Risk.READ_ONLY, optional = setOf("roots", "limit")),
        ToolSpec("move_file", "Datei verschieben", "Verschiebt eine Datei innerhalb des freigegebenen Speichers.", Risk.CONFIRM, required = setOf("source", "destination")),
        ToolSpec("delete_file", "Datei löschen", "Löscht eine Datei im freigegebenen Speicher.", Risk.DESTRUCTIVE, required = setOf("path")),
        ToolSpec("take_screenshot", "Screenshot erstellen", "Erstellt auf ausdrücklichen Nutzerauftrag einen Screenshot.", Risk.CONFIRM),
        ToolSpec("record_screen", "Bildschirm aufnehmen", "Nimmt den Bildschirm für eine begrenzte Zeit auf.", Risk.CONFIRM, optional = setOf("seconds")),
        ToolSpec("open_android_settings", "Android-Einstellungen öffnen", "Öffnet eine Android-Einstellungsseite.", Risk.CONFIRM, required = setOf("page")),
        ToolSpec("get_network_summary", "Netzwerk prüfen", "Liest eine kompakte Netzwerkübersicht.", Risk.READ_ONLY),
        // In-process device tools (19.11.2026). They need no Shizuku bridge; see docs/research/WERKZEUG-AUDIT-20261119.md.
        ToolSpec("search_device", "Gerät durchsuchen", "Sucht installierte Apps, Dateien und Ordner nach Namen (auch Typ wie pdf); liefert je Treffer Pfad oder Paket.", Risk.READ_ONLY, required = setOf("query"), optional = setOf("scope", "limit")),
        ToolSpec("read_file", "Datei lesen", "Liest den Text einer Datei (Textformate, PDF-Textebene, DOCX) begrenzt; pages wählt PDF-Seiten wie 2-4, offset setzt fort.", Risk.READ_ONLY, required = setOf("path"), optional = setOf("pages", "offset")),
        ToolSpec("create_document", "Dokument erstellen", "Legt aus Titel und Inhalt (Zeile mit # = Überschrift, mit - = Listenpunkt) eine neue Datei im Download-Ordner an; format pdf oder docx.", Risk.CONFIRM, required = setOf("title", "content"), optional = setOf("format", "name")),
        ToolSpec("open_file", "Datei anzeigen", "Zeigt eine Datei in der passenden Android-App; nur wenn der Besitzer das Anzeigen oder Öffnen verlangt hat.", Risk.READ_ONLY, required = setOf("path")),
        ToolSpec("list_recent_files", "Neueste Dateien", "Listet die zuletzt geänderten Dateien; filter grenzt auf einen Typ wie pdf oder bilder ein.", Risk.READ_ONLY, optional = setOf("filter", "limit"), surface = ToolSurface.LARGE_CONTEXT_MODEL),
        ToolSpec("get_folder_overview", "Ordnerübersicht", "Zählt Unterordner, Dateien, Dateitypen und Größe eines Ordners; ohne path für den ganzen Speicher.", Risk.READ_ONLY, optional = setOf("path"), surface = ToolSurface.LARGE_CONTEXT_MODEL),
        ToolSpec("search_file_text", "In Dateien suchen", "Sucht einen Text in den Textdateien eines Ordners und zeigt die Fundzeilen.", Risk.READ_ONLY, required = setOf("query"), optional = setOf("path", "limit"), surface = ToolSurface.LARGE_CONTEXT_MODEL),
        ToolSpec("read_clipboard", "Zwischenablage lesen", "Liest nach Bestätigung den Text der Zwischenablage.", Risk.CONFIRM, surface = ToolSurface.LARGE_CONTEXT_MODEL),
        ToolSpec("write_clipboard", "In Zwischenablage kopieren", "Legt nach Bestätigung einen Text in die Zwischenablage.", Risk.CONFIRM, required = setOf("text"), surface = ToolSurface.LARGE_CONTEXT_MODEL),
        ToolSpec("share_file", "Datei teilen", "Öffnet nach Bestätigung Androids Teilen-Dialog für eine Datei; das Ziel wählt der Besitzer dort selbst.", Risk.CONFIRM, required = setOf("path"), surface = ToolSurface.LARGE_CONTEXT_MODEL)
    )

    private val byName = allSpecs.associateBy { it.name }

    fun all(): List<ToolSpec> = allSpecs

    /** What the on-device model is told about: every tool it can call that fits its window. */
    fun onDeviceModel(): List<ToolSpec> = allSpecs.filter { it.surface == ToolSurface.EVERY_MODEL }

    /** The longest text an argument of this name may carry; 4 096 for everything but a declared long text. */
    fun maxTextLength(argument: String): Int =
        ((ArgSchema.forArgument(argument) as? ArgSchema.Text)?.maxLength ?: 0).coerceAtLeast(4_096)

    fun get(name: String): ToolSpec? = byName[name]

    fun risk(name: String): Risk = byName[name]?.risk ?: Risk.DESTRUCTIVE

    fun label(name: String): String = byName[name]?.label ?: name

    internal fun isExplicitAppTarget(value:Any?):Boolean = value is String &&
        value.length<=200 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))

    fun validate(call: ToolCallRequest): String? {
        val spec = byName[call.name] ?: return "Unbekanntes Tool: ${call.name}"
        if (call.name == "find_app") {
            val query = call.arguments["query"]
            if (query !is String || query.length !in 1..80 || query.isBlank() || query.codePoints().anyMatch {
                    Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()
                })
                return "Die Appsuche benötigt einen kurzen, lesbaren Text ohne Steuerzeichen."
        }
        if(call.name in setOf("launch_app","read_app_screen") && !isExplicitAppTarget(call.arguments["packageOrName"]))
            return "Für die App-Bestätigung ist ein eindeutiger Paketname erforderlich; bitte zuerst App-Info prüfen."
        val missing = spec.required.filter { key ->
            val value = call.arguments[key]
            value == null || value.toString().isBlank()
        }
        if (missing.isNotEmpty()) {
            return "Fehlende Argumente für ${call.name}: ${missing.joinToString()}"
        }
        val allowed = spec.required + spec.optional
        val unknown = call.arguments.keys.filterNot { it in allowed }
        if (unknown.isNotEmpty()) {
            return "Nicht erlaubte Argumente für ${call.name}: ${unknown.joinToString()}"
        }
        spec.schema.forEach { (key, schema) ->
            if (call.arguments.containsKey(key) && call.arguments[key] != null) {
                schema.check(call.arguments[key])?.let { return "Argument $key für ${call.name} $it." }
            }
        }
        return null
    }
}

