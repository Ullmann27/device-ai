package at.heinz.deviceai

data class ToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val risk: Risk,
    val required: Set<String> = emptySet(),
    val optional: Set<String> = emptySet()
) {
    /** Typed schema for every declared argument; derived from the argument name so all tools stay consistent. */
    val schema: Map<String, ArgSchema> get() = (required + optional).associateWith { ArgSchema.forArgument(it) }
}

/** Typed argument schema (chief pattern point 3). */
sealed interface ArgSchema {
    data class Text(val maxLength: Int) : ArgSchema
    data class Integer(val min: Long, val max: Long) : ArgSchema
    object Flag : ArgSchema
    object SharedPath : ArgSchema
    object HttpsUrl : ArgSchema
    object PathList : ArgSchema

    /** Returns a German rejection reason, or null when the value conforms. */
    fun check(value: Any?): String? = when (this) {
        is Text -> if (value !is String) "muss Text sein" else if (value.length > maxLength) "ist länger als $maxLength Zeichen"
            else if (value.any { it.isISOControl() }) "enthält Steuerzeichen" else null
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
            "userOnly" -> Flag
            "lines" -> Integer(1, 600)
            "limit" -> Integer(1, 250)
            "seconds" -> Integer(1, 60)
            "query" -> Text(500)
            "packageOrName" -> Text(256)
            "page", "filter" -> Text(200)
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
        ToolSpec("get_network_summary", "Netzwerk prüfen", "Liest eine kompakte Netzwerkübersicht.", Risk.READ_ONLY)
    )

    private val byName = allSpecs.associateBy { it.name }

    fun all(): List<ToolSpec> = allSpecs

    fun get(name: String): ToolSpec? = byName[name]

    fun risk(name: String): Risk = byName[name]?.risk ?: Risk.DESTRUCTIVE

    fun label(name: String): String = byName[name]?.label ?: name

    internal fun isExplicitAppTarget(value:Any?):Boolean = value is String &&
        value.length<=200 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))

    fun validate(call: ToolCallRequest): String? {
        val spec = byName[call.name] ?: return "Unbekanntes Tool: ${call.name}"
        if (call.name == "find_app") {
            val query = call.arguments["query"]
            if (query !is String || query.length !in 2..80 || query.any { it.isISOControl() })
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

