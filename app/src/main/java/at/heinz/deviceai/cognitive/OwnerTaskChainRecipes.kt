package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolRegistry
import java.util.Locale

/**
 * Deterministic recipes: common owner requests that need more than one step, recognised from the utterance and
 * turned into a [ChainPlan] of tools that exist in [ToolRegistry] today. Recognition is deliberately narrow – a
 * negated, quoted, question-about-the-past or otherwise unclear request returns null and keeps its present route.
 * A recipe grants nothing: every step runs through the ToolGate, a changing step through the owner's approval.
 */
object OwnerTaskChainRecipes {
    const val NEWEST_FILE = "newest_file_details"
    const val LATEST_APK = "latest_apk_info"
    const val FILE_SEARCH = "file_search_details"
    const val DEVICE_CHECK = "device_check"
    const val APP_VERSION = "app_version"
    const val STORAGE_ANALYSIS = "storage_analysis"
    const val SCREENSHOT = "screenshot_saved"
    const val APP_RESTART = "app_restart"

    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"
    private val negation = Regex("(?iu)$B(?:nicht|nichts|nie|niemals|ohne|weder|kein\\p{L}*)$E")
    private val quote = Regex("[\"„“”«»]")
    private val newestWord = "(?:neueste[nrs]?|neuste[nrs]?|letzte[nrs]?|jüngste[nrs]?|aktuellste[nrs]?)"
    private val folders = linkedMapOf(
        Regex("(?iu)${B}download(?:s|-ordner|ordner)?$E") to ("/sdcard/Download" to "Downloads"),
        Regex("(?iu)${B}dokumente(?:n|-ordner|ordner)?$E") to ("/sdcard/Documents" to "Dokumente"),
        Regex("(?iu)$B(?:bilder(?:n|-ordner|ordner)?|pictures)$E") to ("/sdcard/Pictures" to "Bilder"),
        Regex("(?iu)$B(?:dcim|kamera-ordner|kameraordner)$E") to ("/sdcard/DCIM" to "DCIM"),
        Regex("(?iu)$B(?:filme(?:n)?|movies)$E") to ("/sdcard/Movies" to "Filme"))
    private val fileKinds = linkedMapOf(
        Regex("(?iu)${B}pdf(?:-datei|s)?$E") to (setOf("pdf") to "PDF"),
        Regex("(?iu)$B(?:bild|foto|photo)$E") to (setOf("jpg", "jpeg", "png", "webp", "heic") to "Bilddatei"),
        Regex("(?iu)$B(?:video|film)$E") to (setOf("mp4", "mkv", "mov", "webm") to "Videodatei"),
        Regex("(?iu)$B(?:word-datei|docx|word-dokument)$E") to (setOf("docx", "doc") to "Word-Datei"),
        Regex("(?iu)${B}datei$E") to (emptySet<String>() to "Datei"))
    private val newestFile = Regex("(?iu)^(?:such(?:e)?|finde?|zeig(?:e)?|nenn(?:e)?|welche[sr]?|was)$E.*$B$newestWord$E.*")
    private val latestApk = Regex("(?iu)^(?:such(?:e)?|finde?|zeig(?:e)?|prüfe?|installiere?|downloade?|lade?|hol(?:e)?|welche[sr]?|was|wo)$E" +
        ".*(?:$B$newestWord$E.*${B}apk$E|${B}apk$E.*$B$newestWord$E).*")
    private val apkFilters = listOf(Regex("(?iu)${B}apk\\s+(?:von|vom|für|mit)\\s+([A-Za-z0-9._-]{2,40})$E"),
        Regex("(?iu)$B([A-Za-z0-9._-]{2,40})[- ]apk$E"))
    private val installWish = Regex("(?iu)$B(?:installier\\p{L}*|download\\p{L}*|herunterlad\\p{L}*|runterlad\\p{L}*|aufspiel\\p{L}*|einspiel\\p{L}*)$E")
    private val fileSearch = Regex("(?iu)^(?:such(?:e)?|finde?)\\s+(?:mir\\s+)?(?:bitte\\s+)?(?:nach\\s+)?(?:(?:der|die|den|einer|allen|alle)\\s+)?" +
        "datei(?:en)?\\s+(?:(?:mit\\s+dem\\s+namen|namens|mit|zu|über)\\s+)?([\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{1,59}?)" +
        "(?:\\s+und\\s+(?:sag|zeig|nenn)\\p{L}*\\s+.{0,60})?[.!?]*$")
    private val deviceCheck = Regex("(?iu)$B(?:geräte-?check|handy-?check|system-?check|status-?check|kurz-?check|gerätestatus)$E")
    private val probes = linkedMapOf(
        Regex("(?iu)$B(?:akku\\p{L}*|batterie\\p{L}*|ladezustand)$E") to "get_battery_info",
        Regex("(?iu)$B(?:speicher|speicherplatz|speicherstatus)$E") to "get_storage_info",
        Regex("(?iu)$B(?:netzwerk|wlan|internetverbindung)$E") to "get_network_summary",
        Regex("(?iu)$B(?:gerät|geräteinfo\\p{L}*|modell|android-version)$E") to "get_device_info")
    private val appVersion = Regex("(?iu)^(?:welche|was\\s+für\\s+eine)\\s+version\\s+(?:von|vom|der\\s+app)\\s+" +
        "([\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{1,39}?)\\s+(?:habe\\s+ich|hab\\s+ich|ist\\s+installiert|läuft|ist\\s+drauf)" +
        "(?:\\s+(?:installiert|drauf))?[.!?]*$|^ist\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{1,39}?)\\s+installiert\\s+und\\s+" +
        "(?:in\\s+)?welche[r]?\\s+version[.!?]*$")
    /** Only the device's own storage: „am meisten“, „so viel“, „meinen“ or an explicit place – never a comparison of things. */
    private val storageAnalysis = Regex("(?iu)^(?:was|wer|welche\\s+dateien)\\s+(?:braucht|brauchen|belegt|belegen|frisst|fressen|verbraucht|verbrauchen)\\s+" +
        "(?:(?:bei\\s+mir|am\\s+handy|auf\\s+(?:dem|meinem)\\s+(?:handy|gerät|telefon))\\s+)?" +
        "(?:am\\s+meisten|so\\s+viel|den\\s+meisten|den\\s+ganzen|meinen(?:\\s+ganzen)?)\\s+(?:platz|speicher|speicherplatz)" +
        "(?:\\s+(?:bei\\s+mir|am\\s+handy|auf\\s+(?:dem|meinem)\\s+(?:handy|gerät|telefon)))?[.!?]*$|" +
        "^(?:mach(?:e)?\\s+(?:mir\\s+)?(?:bitte\\s+)?eine\\s+)?speicheranalyse[.!?]*$")
    private val screenshot = Regex("(?iu)^(?:mach(?:e)?|erstelle?|nimm)\\s+(?:mir\\s+)?(?:bitte\\s+)?(?:mal\\s+)?(?:einen?\\s+)?" +
        "(?:screenshot|bildschirmfoto)(?:\\s+und\\s+(?:sag|zeig|nenn)\\p{L}*\\s+.{0,60})?[.!?]*$")
    private val appRestart = Regex("(?iu)^start(?:e)?\\s+(?:bitte\\s+)?(?:die\\s+app\\s+)?([\\p{L}\\p{N}][\\p{L}\\p{N}.+-]{1,39})\\s+neu[.!]*$")
    private val notAnApp = setOf("das", "den", "die", "der", "dich", "mich", "es", "alles", "mal", "bitte", "modell", "gespräch",
        "test", "system", "handy", "gerät", "continuum", "jarvis", "chat", "computer", "recherche", "suche", "diagnose")

    /** The plan for [utterance], or null when no recipe clearly applies. */
    fun recognise(utterance: String, assistantName: String? = null): ChainPlan? {
        if (utterance.length !in 6..220 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
            .replace(Regex("(?iu)^(?:bitte\\s+|kannst\\s+du\\s+(?:mir\\s+)?(?:bitte\\s+)?)"), "").trim()
        if (text.isEmpty() || ':' in text || quote.containsMatchIn(text) || negation.containsMatchIn(text)) return null
        if (OwnerDeviceRequestSafety.inspect(text).roleManipulation) return null
        return latestApk(text) ?: newestFile(text) ?: fileSearch(text) ?: appVersion(text) ?: storageAnalysis(text)
            ?: screenshot(text) ?: appRestart(text) ?: deviceCheck(text)
    }

    private fun literal(vararg pairs: Pair<String, Any>): Map<String, ChainArgument> =
        pairs.associate { it.first to ChainArgument.Literal(it.second) }

    private fun step(id: String, tool: String, arguments: Map<String, ChainArgument>, label: String,
                     success: ChainSuccess? = null, gives: List<ChainExtract>? = null): ChainStep {
        val contract = checkNotNull(OwnerChainStepCatalog.contract(tool)) { "No chain contract for $tool" }
        return ChainStep(id, tool, arguments, success ?: contract.success, gives ?: contract.gives, label)
    }

    private fun when_(results: ChainResults, step: String): String {
        val date = results.value(step, "date")?.let(OwnerChainValues::germanDate)
        val time = results.value(step, "time")
        return if (date == null) "" else "zuletzt geändert am $date" + (time?.let { " um $it Uhr" } ?: "")
    }

    private fun newestFile(text: String): ChainPlan? {
        if (!newestFile.matches(text)) return null
        val folder = folders.entries.firstOrNull { it.key.containsMatchIn(text) }?.value ?: return null
        val kind = fileKinds.entries.firstOrNull { it.key.containsMatchIn(text) }?.value ?: return null
        return ChainPlan(text, ChainPlan.Origin.RECIPE, NEWEST_FILE, listOf(
            step("list", "list_files", literal("path" to folder.first), "Ordner ${folder.second} lesen",
                gives = listOf(ChainExtract.NewestEntry(kind.first)))
        )) { r ->
            "Die neueste ${kind.second} in ${folder.second} ist „${r.value("list", "name")}“: ${r.value("list", "size")} groß, " +
                when_(r, "list") + ". Verglichen habe ich ${r.value("list", "count")} passende Datei(en) aus der Ordnerliste."
        }
    }

    private fun latestApk(text: String): ChainPlan? {
        if (!latestApk.matches(text)) return null
        val filter = apkFilters.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
            ?.takeIf { !Regex("(?iu)^(?:$newestWord|die|der|den|eine|einer|diese|meine|mir)$").matches(it) }
        val wantsInstall = installWish.containsMatchIn(text)
        return ChainPlan(text, ChainPlan.Origin.RECIPE, LATEST_APK, listOf(
            step("find", "find_latest_apk", if (filter == null) emptyMap() else literal("filter" to filter),
                "Neueste APK" + (filter?.let { " zu „$it“" } ?: "") + " suchen"),
            step("folder", "list_files", mapOf("path" to ChainArgument.FromStep("find", "path", ChainArgument.Part.PARENT)),
                "Ordner der APK lesen", success = ChainSuccess.ContainsHandedOver("find", "path", ChainArgument.Part.LEAF),
                gives = listOf(ChainExtract.ListingEntry("find", "path")))
        )) { r ->
            val path = r.value("find", "path").orEmpty()
            "Die neueste APK ist „${r.value("folder", "name")}“ (${r.value("folder", "size")}, ${when_(r, "folder")}) im Ordner " +
                "${OwnerChainValues.part(path, ChainArgument.Part.PARENT)}. Installiert habe ich nichts." +
                if (wantsInstall) " Eine Datei aus einem Suchergebnis installiere ich nicht von selbst – sag dafür ausdrücklich " +
                    "„Installiere ${r.value("folder", "name")}“, dann frage ich nach deiner Freigabe." else ""
        }
    }

    private fun fileSearch(text: String): ChainPlan? {
        val query = fileSearch.matchEntire(text)?.groupValues?.get(1)?.trim()?.trim('.', ' ') ?: return null
        if (query.length < 2 || Regex("(?iu)^(?:in|im|auf|von|aus)$E").containsMatchIn(query)) return null
        return ChainPlan(text, ChainPlan.Origin.RECIPE, FILE_SEARCH, listOf(
            step("find", "find_files", literal("query" to query, "limit" to 20), "Dateien mit „$query“ suchen"),
            step("folder", "list_files", mapOf("path" to ChainArgument.FromStep("find", "path", ChainArgument.Part.PARENT)),
                "Ordner der Datei lesen", success = ChainSuccess.ContainsHandedOver("find", "path", ChainArgument.Part.LEAF),
                gives = listOf(ChainExtract.ListingEntry("find", "path")))
        )) { r ->
            "Gefunden: „${r.value("folder", "name")}“ in ${OwnerChainValues.part(r.value("find", "path").orEmpty(), ChainArgument.Part.PARENT)} – " +
                "${r.value("folder", "size")}, ${when_(r, "folder")}. Geöffnet habe ich sie nicht; dafür gibt es noch kein freigegebenes Werkzeug."
        }
    }

    private fun appVersion(text: String): ChainPlan? {
        val match = appVersion.matchEntire(text) ?: return null
        val app = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (app.lowercase(Locale.ROOT) in notAnApp) return null
        return ChainPlan(text, ChainPlan.Origin.RECIPE, APP_VERSION, listOf(
            step("app", "find_app", literal("query" to app), "App „$app“ suchen"),
            step("info", "get_app_info", mapOf("packageOrName" to ChainArgument.FromStep("app", "package")), "Version lesen")
        )) { r ->
            "${r.value("app", "label") ?: app} (${r.value("app", "package")}) ist installiert: Version ${r.value("info", "version")}" +
                (r.value("info", "code")?.let { ", Versionscode $it" } ?: "") + "."
        }
    }

    private fun storageAnalysis(text: String): ChainPlan? {
        if (!storageAnalysis.matches(text)) return null
        return ChainPlan(text, ChainPlan.Origin.RECIPE, STORAGE_ANALYSIS, listOf(
            step("storage", "get_storage_info", emptyMap(), "Speicher messen"),
            step("large", "get_large_files", literal("limit" to 10), "Größte Dateien suchen")
        )) { r ->
            "Frei sind ${r.value("storage", "free")} von ${r.value("storage", "total")}. Die größten Einträge der freigegebenen Ordner " +
                "(Größe in KB, dann Pfad):\n${r.value("large", "largest")}\nGelöscht oder verschoben habe ich nichts."
        }
    }

    private fun screenshot(text: String): ChainPlan? {
        if (!screenshot.matches(text)) return null
        return ChainPlan(text, ChainPlan.Origin.RECIPE, SCREENSHOT, listOf(
            step("shot", "take_screenshot", emptyMap(), "Screenshot erstellen"),
            step("folder", "list_files", mapOf("path" to ChainArgument.FromStep("shot", "path", ChainArgument.Part.PARENT)),
                "Gespeicherte Datei prüfen", success = ChainSuccess.ContainsHandedOver("shot", "path", ChainArgument.Part.LEAF),
                gives = listOf(ChainExtract.ListingEntry("shot", "path")))
        )) { r ->
            "Der Screenshot ist gespeichert und im Ordner nachgeprüft: „${r.value("folder", "name")}“ (${r.value("folder", "size")}) in " +
                "${OwnerChainValues.part(r.value("shot", "path").orEmpty(), ChainArgument.Part.PARENT)}."
        }
    }

    private fun appRestart(text: String): ChainPlan? {
        val app = appRestart.matchEntire(text)?.groupValues?.get(1)?.trim() ?: return null
        if (app.lowercase(Locale.ROOT) in notAnApp) return null
        return ChainPlan(text, ChainPlan.Origin.RECIPE, APP_RESTART, listOf(
            step("app", "find_app", literal("query" to app), "App „$app“ suchen"),
            step("restart", "restart_app", mapOf("packageOrName" to ChainArgument.FromStep("app", "package")), "App neu starten",
                success = ChainSuccess.ContainsHandedOver("app", "package"))
        )) { r -> "${r.value("app", "label") ?: app} (${r.value("app", "package")}) wurde gestoppt und neu gestartet; das Werkzeug hat den Neustart gemeldet." }
    }

    private fun deviceCheck(text: String): ChainPlan? {
        val named = probes.entries.filter { it.key.containsMatchIn(text) }.map { it.value }
        // Only the explicit check word starts the chain. „Prüfe Akku und Speicher“ keeps its pinned route (the
        // start core asks which value first, REG-TOOL-006) until that case is changed on purpose.
        if (!deviceCheck.containsMatchIn(text)) return null
        val tools = (named + listOf("get_battery_info", "get_storage_info", "get_network_summary")).distinct().take(ChainPlan.MAX_STEPS)
        return ChainPlan(text, ChainPlan.Origin.RECIPE, DEVICE_CHECK, tools.map { tool ->
            step(tool, tool, emptyMap(), ToolRegistry.label(tool), gives = listOf(ChainExtract.Lines("report", 5)))
        }) { r -> tools.joinToString("\n") { tool -> "${ToolRegistry.label(tool)}: " + r.value(tool, "report").orEmpty().replace("\n", " · ") } }
    }
}

/** What the owner says to a paused chain. Anything else leaves the chain paused and is routed normally. */
object OwnerTaskChainContinuation {
    sealed interface Command {
        /** [choice]: 1-based pick for a chain that waits for a choice, else null. */
        data class Continue(val choice: Int?) : Command
        data object Drop : Command
    }

    private val proceed = Regex("(?iu)^(?:ja[,!]?\\s+)?(?:bitte\\s+)?(?:(?:mach(?:e)?\\s+)?weiter(?:machen)?|fortsetzen|setz(?:e)?\\s+fort|fahr(?:e)?\\s+fort)(?:\\s+bitte)?[.!]*$")
    private val drop = Regex("(?iu)^(?:abbrechen|abbruch|verwirf\\s+(?:das|die\\s+aufgabe|den\\s+auftrag)|lass\\s+(?:es|das)(?:\\s+(?:sein|bleiben))?|vergiss\\s+(?:es|das))[.!]*$")
    private val ordinals = listOf("erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte")
    private val pick = Regex("(?iu)^(?:(?:mach(?:e)?\\s+)?weiter\\s+mit\\s+|nimm\\s+|ich\\s+meine\\s+|ich\\s+nehme\\s+)?(?:(?:der|die|das|den|dem|nummer|nr\\.?|treffer)\\s+)?" +
        "(\\d|${ordinals.joinToString("|")})\\p{L}{0,2}(?:\\s+(?:datei|app|treffer|bitte))?[.!]*$")

    fun parse(utterance: String, waitsForChoice: Boolean, assistantName: String? = null): Command? {
        if (utterance.length !in 1..60) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trim()
        if (drop.matches(text)) return Command.Drop
        if (waitsForChoice) pick.matchEntire(text)?.let { match ->
            val raw = match.groupValues[1].lowercase(Locale.GERMAN)
            val number = raw.toIntOrNull() ?: (ordinals.indexOf(raw) + 1)
            if (number in 1..ChainExtract.MAX_OPTIONS) return Command.Continue(number)
        }
        // „weiter“ cannot answer „welchen meinst du?“; the question stays open.
        return if (!waitsForChoice && proceed.matches(text)) Command.Continue(null) else null
    }
}

/** The owner's switch for model-proposed plans („Aufgabenketten mit Modellplan an/aus“). Off by default. */
object OwnerTaskChainSwitch {
    private val command = Regex("(?iu)^(?:schalte?\\s+)?(?:die\\s+)?(?:aufgabenketten?|schrittfolgen?)\\s+mit\\s+modellplan\\s+(an|ein|aus|ab)[.!]*$")
    private val multiStep = Regex("(?iu)(?<![\\p{L}])(?:und\\s+dann|danach|anschließend|und\\s+(?:sag|zeig|nenn|prüf|schau)\\p{L}*\\s+(?:mir|ob|nach))(?![\\p{L}])")
    private val deviceObject = Regex("(?iu)(?<![\\p{L}])(?:datei\\p{L}*|ordner|apps?|apk|speicher\\p{L}*|akku\\p{L}*|downloads?|screenshot|netzwerk|wlan|gerät\\p{L}*)(?![\\p{L}])")

    /** true = on, false = off, null = not this command. */
    fun parse(utterance: String): Boolean? = command.matchEntire(utterance.trim())?.groupValues?.get(1)
        ?.lowercase(Locale.GERMAN)?.let { it == "an" || it == "ein" }

    /** A command with an explicit sequence about device things: the only requests a model is asked to plan. */
    fun wantsModelPlan(utterance: String, assistantName: String? = null): Boolean =
        utterance.length in 12..300 && ':' !in utterance && OwnerUnderstanding.commandShaped(utterance, assistantName) &&
            multiStep.containsMatchIn(utterance) && deviceObject.containsMatchIn(utterance) &&
            !OwnerDeviceRequestSafety.inspect(utterance).roleManipulation
}
