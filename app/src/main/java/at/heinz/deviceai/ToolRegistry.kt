package at.heinz.deviceai

data class ToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val risk: Risk,
    val required: Set<String> = emptySet(),
    val optional: Set<String> = emptySet()
)

object ToolRegistry {
    private val allSpecs = listOf(
        ToolSpec("web_search", "Websuche", "Sucht öffentliche Quellen nur nach Android-Eigentümerfreigabe (ASK oder ALWAYS_ON).", Risk.CONFIRM, required=setOf("query")),
        ToolSpec("web_read", "Webseite lesen", "Liest öffentliche HTTPS-Quellen nur nach Android-Eigentümerfreigabe (ASK oder ALWAYS_ON).", Risk.CONFIRM, required=setOf("url")),
        ToolSpec("get_device_info", "Geräteinformationen", "Liest Modell, Android-Version und Build-Daten.", Risk.READ_ONLY),
        ToolSpec("get_battery_info", "Akku prüfen", "Liest den aktuellen Batteriestatus.", Risk.READ_ONLY),
        ToolSpec("get_storage_info", "Speicher prüfen", "Liest freien und belegten Speicher.", Risk.READ_ONLY),
        ToolSpec("list_installed_apps", "Apps auflisten", "Listet installierte Apps.", Risk.READ_ONLY, optional = setOf("userOnly")),
        ToolSpec("get_app_info", "App-Info lesen", "Liest Paket- und Versionsinformationen.", Risk.READ_ONLY, required = setOf("packageOrName")),
        ToolSpec("list_processes", "Prozesse prüfen", "Liest aktive Prozesse und CPU-Nutzung.", Risk.READ_ONLY),
        ToolSpec("read_logcat", "Logcat analysieren", "Liest aktuelle Android-Logs.", Risk.READ_ONLY, optional = setOf("packageOrName", "lines")),
        ToolSpec("launch_app", "App starten", "Startet eine installierte App.", Risk.CONFIRM, required = setOf("packageOrName")),
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

    fun validate(call: ToolCallRequest): String? {
        val spec = byName[call.name] ?: return "Unbekanntes Tool: ${call.name}"
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
        return null
    }
}

