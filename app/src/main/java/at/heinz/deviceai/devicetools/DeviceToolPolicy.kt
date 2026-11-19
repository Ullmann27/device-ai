package at.heinz.deviceai.devicetools

import at.heinz.deviceai.ToolRefusal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import java.util.Locale

/**
 * What the tool layer knows about every registered tool before it starts: where it runs (in this process through an
 * Android API, through the Shizuku shell bridge, only in the system centre) and what its result would reveal to the
 * active brain (nothing personal, names of files/apps, content). [decide] turns that and the device's present state
 * into „run it there“ or a refusal with a plain reason – before the owner is asked and before anything is dispatched.
 */
internal object DeviceToolPolicy {
    enum class Home {
        /** An Android API inside the app; needs neither Shizuku nor the all-files grant. */
        IN_PROCESS,
        /** A file tool: in-process under „Zugriff auf alle Dateien“, otherwise through the bridge. */
        FILES_NATIVE_OR_BRIDGE,
        /** A file tool that only exists in-process. */
        FILES_NATIVE_ONLY,
        /** Android gives an ordinary app no API for this; only the bridge's shell identity can do it. */
        BRIDGE_ONLY,
        /** Started from the system centre itself, never for a model. */
        SYSTEM_CENTRE,
        /** Not a device tool (web tools own their consent session). */
        OTHER
    }

    enum class Reveals { NOTHING, NAMES, CONTENT }
    enum class Route { NATIVE, BRIDGE, OTHER }

    sealed interface Decision {
        data class Run(val route: Route) : Decision
        data class Refused(val refusal: ToolRefusal) : Decision
    }

    /** The device and consent state at the moment of the call. [readable]: this very file can be read without the grant. */
    class State(
        val cloudBrain: Boolean,
        /** The result goes back to a model (an owner turn the brain drives), not to a fixed in-app sequence. */
        val modelTurn: Boolean,
        val namesConsent: Boolean,
        val contentConsent: Boolean,
        val bridgeReady: Boolean,
        val filesAccess: Boolean,
        val ownerTurn: String = "",
        val documentsCreated: Int = 0,
        val filesOpened: Int = 0,
        /** This call was started by the system centre itself. */
        val systemCall: Boolean = false,
        val readable: (String) -> Boolean = { false }
    )

    private val bridgeOnly = mapOf(
        "list_processes" to "Prozesse anderer Apps sind für eine App gesperrt",
        "read_logcat" to "die Logs anderer Apps sind für eine App gesperrt",
        "stop_app" to "eine fremde App beenden darf nur das System",
        "restart_app" to "eine fremde App beenden darf nur das System",
        "install_apk" to "ohne Androids eigenen Installationsdialog kann eine App nichts installieren",
        "uninstall_app" to "ohne Androids eigenen Dialog kann eine App nichts deinstallieren",
        "take_screenshot" to "den Bildschirm anderer Apps darf eine App nicht still aufnehmen",
        "record_screen" to "den Bildschirm anderer Apps darf eine App nicht still aufnehmen")
    private val filesEither = setOf("list_files", "find_files", "find_latest_apk", "get_large_files", "move_file", "delete_file")
    private val filesNative = setOf("read_file", "open_file", "share_file", "list_recent_files", "get_folder_overview", "search_file_text")
    private val inProcess = setOf("get_device_info", "get_battery_info", "get_storage_info", "get_network_summary",
        "list_installed_apps", "find_app", "get_app_info", "launch_app", "read_app_screen", "open_android_settings",
        "search_device", "create_document", "read_clipboard", "write_clipboard")
    private val other = setOf("web_search", "web_read")

    private val names = setOf("list_installed_apps", "find_app", "get_app_info", "list_processes", "list_files", "find_files",
        "find_latest_apk", "get_large_files", "search_device", "list_recent_files", "get_folder_overview")
    private val content = setOf("read_file", "search_file_text", "read_clipboard", "read_logcat", "read_app_screen")

    /** Files a single owner turn may create or show; a brain cannot litter the storage or pop up viewers in a row. */
    const val MAX_DOCUMENTS_PER_TURN = 2
    const val MAX_OPENS_PER_TURN = 1

    fun home(tool: String): Home = when (tool) {
        in SystemIntelligenceCommands.localTools -> Home.SYSTEM_CENTRE
        in bridgeOnly -> Home.BRIDGE_ONLY
        in filesEither -> Home.FILES_NATIVE_OR_BRIDGE
        in filesNative -> Home.FILES_NATIVE_ONLY
        in inProcess -> Home.IN_PROCESS
        else -> Home.OTHER
    }

    fun reveals(tool: String): Reveals = when (tool) {
        in content -> Reveals.CONTENT
        in names -> Reveals.NAMES
        else -> Reveals.NOTHING
    }

    /** True for the tools this table knows; every registered tool must be one of them (pinned by a test). */
    fun classified(tool: String): Boolean = tool in SystemIntelligenceCommands.localTools || tool in bridgeOnly ||
        tool in filesEither || tool in filesNative || tool in inProcess || tool in other

    /** Why a tool only works with the bridge, in the owner's words; null for every other tool. */
    fun bridgeReason(tool: String): String? = bridgeOnly[tool]

    fun needsBridge(tool: String): Boolean = tool in bridgeOnly

    fun decide(tool: String, arguments: Map<String, Any?>, state: State): Decision {
        val label = ToolRegistry.label(tool)
        fun refuse(code: String, message: String) = Decision.Refused(ToolRefusal(code, message))
        val home = home(tool)
        if (home == Home.OTHER) return Decision.Run(Route.OTHER)
        if (home == Home.SYSTEM_CENTRE) return if (state.systemCall) Decision.Run(Route.NATIVE) else refuse("SYSTEM_CENTRE_ONLY",
            "„$label“ läuft nur im Systemzentrum und wird dort von dir selbst gestartet. Öffne den Systembereich; " +
                "ausgeführt habe ich nichts.")
        // 1. Privacy before anything else: with the cloud brain the result would leave the device.
        if (state.cloudBrain && state.modelTurn) when (reveals(tool)) {
            Reveals.NAMES -> if (!state.namesConsent) return refuse("CLOUD_NAMES_CONSENT_MISSING",
                "„$label“ habe ich nicht ausgeführt: Als Gehirn ist gerade Claude (Cloud) gewählt, und das Ergebnis – Namen " +
                    "deiner Dateien oder Apps – würde dein Gerät verlassen. Dafür fehlt deine Freigabe. Erlauben: " +
                    "„${DeviceToolCommand.SAY_NAMES_ON}“. Oder wechsle im Modellzentrum auf das lokale Modell – das kann es, " +
                    "ohne etwas zu senden.")
            Reveals.CONTENT -> if (!state.contentConsent) return refuse("CLOUD_CONTENT_CONSENT_MISSING",
                "„$label“ habe ich nicht ausgeführt: Als Gehirn ist gerade Claude (Cloud) gewählt, und das Ergebnis – Inhalte " +
                    "von deinem Gerät – würde es verlassen. Dafür fehlt deine Freigabe. Erlauben: " +
                    "„${DeviceToolCommand.SAY_CONTENT_ON}“. Oder wechsle im Modellzentrum auf das lokale Modell – das kann " +
                    "es, ohne etwas zu senden.")
            Reveals.NOTHING -> Unit
        }
        // 2. What the owner's own words must carry.
        if (tool == "open_file") {
            if (!DeviceOwnerWish.wantsToSee(state.ownerTurn)) return refuse("OPEN_NOT_ORDERED",
                "„$label“ habe ich nicht ausgeführt: In deinem Auftrag steht nicht, dass ich eine Datei öffnen oder anzeigen " +
                    "soll. Sag zum Beispiel „Zeig mir die Datei …“, dann öffne ich sie.")
            if (state.filesOpened >= MAX_OPENS_PER_TURN) return refuse("OPEN_LIMIT",
                "In diesem Auftrag habe ich schon eine Datei angezeigt; eine weitere öffne ich erst auf deinen nächsten Auftrag.")
        }
        if (tool == "create_document") {
            if (state.documentsCreated >= MAX_DOCUMENTS_PER_TURN) return refuse("DOCUMENT_LIMIT",
                "In diesem Auftrag habe ich schon $MAX_DOCUMENTS_PER_TURN Dokumente angelegt; weitere lege ich erst auf " +
                    "deinen nächsten Auftrag an.")
            val format = arguments["format"]?.toString()
            if (DeviceDocumentWriter.formatOf(format) == null) return refuse("DOCUMENT_FORMAT_UNKNOWN", DeviceDocumentWriter.unknownFormat(format))
        }
        // 3. Where it can run right now.
        return when (home) {
            Home.BRIDGE_ONLY -> if (state.bridgeReady) Decision.Run(Route.BRIDGE) else refuse("BRIDGE_NOT_RUNNING",
                "Für „$label“ muss Shizuku laufen: Android gibt einer App dafür keinen eigenen Zugriff (${bridgeOnly.getValue(tool)}). " +
                    "Shizuku ist gerade nicht verbunden – nach einem Neustart des Geräts muss es neu gestartet werden. " +
                    "Es wurde nichts ausgeführt und nichts verändert.")
            Home.FILES_NATIVE_OR_BRIDGE -> when {
                state.filesAccess -> Decision.Run(Route.NATIVE)
                state.bridgeReady -> Decision.Run(Route.BRIDGE)
                else -> refuse("ALL_FILES_ACCESS_MISSING", FILES_MISSING + " Mit laufendem Shizuku ginge „$label“ auch ohne diese Erlaubnis.")
            }
            Home.FILES_NATIVE_ONLY -> {
                val path = arguments["path"]?.toString()
                if (state.filesAccess || (path != null && tool in setOf("read_file", "open_file", "share_file") && state.readable(path)))
                    Decision.Run(Route.NATIVE)
                else refuse("ALL_FILES_ACCESS_MISSING", FILES_MISSING)
            }
            else -> Decision.Run(Route.NATIVE)
        }
    }

    /** The document desk's wording for the missing grant, plus the way to give it. */
    const val FILES_MISSING = "Dafür fehlt mir der Zugriff auf deine Dateien: In Android ist „Zugriff auf alle Dateien“ für " +
        "Continuum nicht erlaubt. Ich habe nichts durchsucht und nichts verändert. Erlauben: Android-Einstellungen › Apps › " +
        "Spezieller Zugriff › Zugriff auf alle Dateien › Continuum – oder sag „${DeviceToolCommand.SAY_FILE_ACCESS}“, dann " +
        "öffne ich die Seite."
}

/**
 * What the owner's own sentence asks for – the only source of two small liberties: showing a file without a
 * confirmation dialog („zeig sie mir“) and, with his switch on, creating a document without one („erstelle eine PDF“).
 * Narrow on purpose: a negated wish is no wish, and a text that merely mentions a file asks for nothing.
 */
internal object DeviceOwnerWish {
    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"
    private const val NOT = "(?:nicht|nichts|nie|niemals|kein\\p{L}*|ohne)"
    private val see = "(?:zeig\\p{L}*|anzeig\\p{L}*|öffn\\p{L}*|oeffn\\p{L}*|aufmach\\p{L}*|anseh\\p{L}*|anschau\\p{L}*|vorschau|open|show|sehen)"
    private val seeWish = Regex("(?iu)$B(?:$see|mach\\p{L}*\\s+(?:[\\p{L}\\p{N}.-]+\\s+){0,5}auf)$E")
    private val seeNegated = Regex("(?iu)$B$see$E(?:\\s+[\\p{L}\\p{N}.-]+){0,3}?\\s+$NOT$E|$B$NOT$E(?:\\s+[\\p{L}\\p{N}.-]+){0,3}?\\s+$see$E")

    private val documentNoun = Regex("(?iu)$B(?:pdfs?|pdf-\\p{L}+|word|word-\\p{L}+|docx|dokument\\p{L}*|datei)$E")
    private val createVerb = Regex("(?iu)$B(?:erstell\\p{L}*|schreib\\p{L}*|erzeug\\p{L}*|verfass\\p{L}*|exportier\\p{L}*|speicher\\p{L}*|" +
        "abspeicher\\p{L}*|anleg\\p{L}*|mach\\p{L}*\\s+(?:[\\p{L}\\p{N}.-]+\\s+){0,3}?(?:ein|eine|einen|daraus)$E)")
    private val anyNegation = Regex("(?iu)$B$NOT$E")

    /** The owner asked to open or show something in this very sentence. */
    fun wantsToSee(ownerTurn: String): Boolean {
        val text = ownerTurn.lowercase(Locale.GERMAN)
        return text.length in 3..2_000 && seeWish.containsMatchIn(text) && !seeNegated.containsMatchIn(text)
    }

    /** The owner ordered a document (a creating verb and a document word, nothing negated). */
    fun ordersDocument(ownerTurn: String): Boolean {
        val text = ownerTurn.lowercase(Locale.GERMAN)
        return text.length in 3..2_000 && documentNoun.containsMatchIn(text) && createVerb.containsMatchIn(text) &&
            !anyNegation.containsMatchIn(text)
    }
}
