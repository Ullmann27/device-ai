package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.NetworkTurnConstraint
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import com.anthropic.models.beta.messages.BetaCitationsConfigParam
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20260521
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaServerToolUseBlock
import com.anthropic.models.beta.messages.BetaWebFetchTool20260209
import com.anthropic.models.beta.messages.BetaWebFetchUrlSourceNone
import com.anthropic.models.beta.messages.BetaWebFetchUrlSources
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209
import com.anthropic.models.beta.messages.MessageCreateParams
import java.util.Locale

/**
 * Anthropic's server tools for the cloud brain (owner order 19.11.2026: „Ja bau das ein und mach alles fertig“):
 * web search, web fetch and code execution run on Anthropic's side, cost extra API credit and are therefore two
 * owner switches that are off by default. Everything about them lives in this file: which tools one request
 * declares, with which bounds, how the app's internet consent still rules, what a response says about them and the
 * owner texts. API facts are from Anthropic's documentation as read on 19.11.2026 (platform.claude.com/docs →
 * „Server tools“, „Web search tool“, „Web fetch tool“, „Code execution tool“); no real request was ever sent by a
 * test or by this implementation work.
 */

/** The server tools one request declares. */
data class ClaudeServerToolPlan(val web: Boolean = false, val code: Boolean = false) {
    val any: Boolean get() = web || code
}

/** What the app's own internet consent says about web access in the running owner turn. */
enum class ClaudeWebGrant {
    /** Internet mode „dauerhaft freigegeben“. */
    ALWAYS,
    /** Internet mode „auf Nachfrage“, and the owner approved web access for exactly this turn. */
    APPROVED_ONCE,
    /** Internet mode „auf Nachfrage“ without an approval for this turn. */
    NOT_APPROVED,
    INTERNET_OFF,
    /** The owner's sentence excludes research, or concerns this device or this assistant only. */
    OWNER_VETO
}

/**
 * The web side of one owner turn. The app's internet consent stays the rule for the cloud's web search: nothing here
 * can widen it. [internetMode] is the owner's setting at the moment of a request; an approval is the capability the
 * existing consent dialog minted for one request, handed over for exactly the turn that follows.
 */
class ClaudeWebTurn {
    /** Still valid? Read before every request of the turn, so a revoked consent ends the web use at once. */
    fun interface Approval { fun valid(): Boolean }

    /** Fail closed until the app connects its setting. */
    @Volatile var internetMode: () -> InternetAccessMode = { InternetAccessMode.OFF }

    private var pending: Pair<String, Approval>? = null
    private var active: Approval? = null
    private var vetoed = false

    /** The owner approved web access for [request] in the app's consent dialog; the turn for it starts next. */
    @Synchronized fun approve(request: String, approval: Approval) { pending = request to approval }

    /** A model turn for the owner's sentence [request] starts: an approval counts only for that same sentence. */
    @Synchronized fun begin(request: String) {
        active = pending?.takeIf { it.first == request }?.second
        pending = null
        vetoed = NetworkTurnConstraint.forbidsExternalResearch(request)
    }

    @Synchronized fun grant(): ClaudeWebGrant = when {
        vetoed -> ClaudeWebGrant.OWNER_VETO
        else -> when (runCatching { internetMode() }.getOrDefault(InternetAccessMode.OFF)) {
            InternetAccessMode.OFF -> ClaudeWebGrant.INTERNET_OFF
            InternetAccessMode.ALWAYS_ON -> ClaudeWebGrant.ALWAYS
            InternetAccessMode.ASK ->
                if (active?.let { approval -> runCatching { approval.valid() }.getOrDefault(false) } == true)
                    ClaudeWebGrant.APPROVED_ONCE else ClaudeWebGrant.NOT_APPROVED
        }
    }
}

/**
 * Which server tools a request declares, and their bounds.
 *
 * Declaration rules (documentation, „Server tools“ → „ZDR and allowed_callers“ and „Dynamic filtering with code
 * execution“; „Code execution tool“ → „Usage and pricing“):
 *  - web switch only: `web_search_20260209` and `web_fetch_20260209`, both with `allowed_callers: ["direct"]`. The
 *    default of these versions runs every search inside code execution („dynamic filtering“); „direct“ switches
 *    that off, so no code runs in the cloud while the owner's code switch is off, and `max_uses` counts across the
 *    whole turn („Searches from earlier requests in that turn count against the limit, unless Claude ran them from
 *    code execution“).
 *  - code switch only: `code_execution_20260521` („The latest tool version“).
 *  - both: the two web tools (direct) and `code_execution_20260521`. The standalone tool is declared because the
 *    app needs code execution for its own purposes; with the web tools on „direct“ there is exactly one execution
 *    environment, the declared one – not the second, automatically provisioned one the documentation warns about
 *    („Including it alongside _20260209 web tools creates a second execution environment“).
 * The app's own `web_search` and `web_read` are left out of a request that declares the cloud web tools: a request
 * cannot hold two tools named `web_search`.
 */
internal object ClaudeServerToolPolicy {
    /** Per owner turn; the API enforces them (`max_uses`). */
    const val WEB_SEARCH_MAX_USES = 3L
    const val WEB_FETCH_MAX_USES = 3L
    /** A fetched page is cut to about this many tokens before it enters the conversation (`max_content_tokens`). */
    const val WEB_FETCH_MAX_CONTENT_TOKENS = 20_000L
    /**
     * Steps in the code sandbox per owner turn (a command or a file operation is one step). The API has no such
     * parameter for code execution, so the runtime counts the calls while they stream and ends the turn at the cap.
     */
    const val CODE_MAX_STEPS = 10

    val CLIENT_WEB_TOOLS: Set<String> = setOf("web_search", "web_read")

    /**
     * [grant] is null when the cloud brain is not selected or the consent is missing: then no switch has any effect.
     */
    data class Decision(val plan: ClaudeServerToolPlan = ClaudeServerToolPlan(), val webSwitch: Boolean = false,
                        val codeSwitch: Boolean = false, val grant: ClaudeWebGrant? = null,
                        /** The turn writes a document draft: no server tool, whatever the switches say. */
                        val documentDraft: Boolean = false) {
        companion object { val NONE = Decision() }
    }

    /**
     * [documentDraft]: the answer of this turn becomes the text of a document. Nothing is declared then – the app's
     * own lines about a search or a computation must never end up inside the owner's document.
     */
    fun decide(settings: ClaudeBrainSettings, documentDraft: Boolean = false, grant: () -> ClaudeWebGrant): Decision {
        if (!settings.claudeSelected || !settings.consentGiven) return Decision.NONE
        if (documentDraft) return Decision(ClaudeServerToolPlan(), settings.cloudWebSearch, settings.cloudCodeExecution,
            null, documentDraft = true)
        val web = if (settings.cloudWebSearch) grant() else null
        return Decision(ClaudeServerToolPlan(
            web = web == ClaudeWebGrant.ALWAYS || web == ClaudeWebGrant.APPROVED_ONCE,
            code = settings.cloudCodeExecution), settings.cloudWebSearch, settings.cloudCodeExecution, web)
    }

    /** Appended after the app's own tools, in a fixed order, so the request prefix is byte-stable per switch state. */
    fun declare(builder: MessageCreateParams.Builder, plan: ClaudeServerToolPlan) {
        if (plan.web) {
            builder.addTool(BetaWebSearchTool20260209.builder()
                .maxUses(WEB_SEARCH_MAX_USES)
                .addAllowedCaller(BetaWebSearchTool20260209.AllowedCaller.DIRECT)
                .build())
            builder.addTool(BetaWebFetchTool20260209.builder()
                .maxUses(WEB_FETCH_MAX_USES)
                .maxContentTokens(WEB_FETCH_MAX_CONTENT_TOKENS)
                .addAllowedCaller(BetaWebFetchTool20260209.AllowedCaller.DIRECT)
                // Fetch cites only on request („citations are optional for web fetch and disabled by default“).
                .citations(BetaCitationsConfigParam.builder().enabled(true).build())
                // Addresses that only appear in a result of one of the app's device tools (a link in a message, a
                // file) are not fetched: only what the owner wrote or what a search of this turn returned.
                .urlSources(BetaWebFetchUrlSources.builder()
                    .clientToolResults(BetaWebFetchUrlSourceNone.builder().build()).build())
                .build())
        }
        if (plan.code) builder.addTool(BetaCodeExecutionTool20260521.builder().build())
    }

    /** A step of the code sandbox: `bash_code_execution`, `text_editor_code_execution` or `code_execution`. */
    fun isCodeStep(name: BetaServerToolUseBlock.Name): Boolean = name == BetaServerToolUseBlock.Name.BASH_CODE_EXECUTION ||
        name == BetaServerToolUseBlock.Name.TEXT_EDITOR_CODE_EXECUTION || name == BetaServerToolUseBlock.Name.CODE_EXECUTION
}

/**
 * What the server tools did in one owner turn, read from the response blocks only – nothing here is made up. Sources
 * are the web pages a text block cites (`web_search_result_location`) and the pages a fetch really returned.
 */
data class ClaudeServerToolOutcome(
    val sources: List<Source> = emptyList(),
    val webSearches: Int = 0,
    val webFetches: Int = 0,
    val codeSteps: Int = 0,
    val searchErrors: List<String> = emptyList(),
    val fetchErrors: List<String> = emptyList(),
    val codeErrors: List<String> = emptyList(),
    /** Files the sandbox handed out (`file_id` entries). The app does not download them. */
    val files: Int = 0
) {
    data class Source(val title: String, val url: String)

    val none: Boolean get() = webSearches == 0 && webFetches == 0 && codeSteps == 0 && sources.isEmpty() &&
        searchErrors.isEmpty() && fetchErrors.isEmpty() && codeErrors.isEmpty() && files == 0

    operator fun plus(other: ClaudeServerToolOutcome) = ClaudeServerToolOutcome(
        (sources + other.sources).distinctBy { it.url }.take(MAX_SOURCES),
        webSearches + other.webSearches, webFetches + other.webFetches, codeSteps + other.codeSteps,
        searchErrors + other.searchErrors, fetchErrors + other.fetchErrors, codeErrors + other.codeErrors,
        files + other.files)

    /**
     * The app's own lines under the answer, or null. They are not the model's words: where the answer comes from
     * (searched or computed in the cloud) and, plainly, every search, fetch or code step that returned an error.
     */
    fun note(): String? {
        val lines = mutableListOf<String>()
        if (webSearches > 0 || webFetches > 0) lines += "Über die Cloud im Internet nachgesehen: " +
            listOfNotNull(count(webSearches, "Suche", "Suchen"), count(webFetches, "Seitenabruf", "Seitenabrufe"))
                .joinToString(", ") + "."
        failures(searchErrors, "Websuche über die Cloud ist", "Websuchen über die Cloud sind",
            ClaudeServerToolTexts::searchError)?.let { lines += it }
        failures(fetchErrors, "Seitenabruf über die Cloud ist", "Seitenabrufe über die Cloud sind",
            ClaudeServerToolTexts::fetchError)?.let { lines += it }
        if (codeSteps > 0) lines += "Berechnet in der Cloud (Code-Ausführung, " + count(codeSteps, "Schritt", "Schritte") + ")."
        failures(codeErrors, "Schritt der Code-Ausführung ist", "Schritte der Code-Ausführung sind",
            ClaudeServerToolTexts::codeError)?.let { lines += it }
        if (files > 0) lines += "In der Cloud-Umgebung " + (if (files == 1) "wurde 1 Datei" else "wurden $files Dateien") +
            " erzeugt. Das Abholen auf dein Gerät ist noch nicht eingebaut; die Datei liegt nicht auf dem Gerät."
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun count(n: Int, one: String, many: String): String? = when (n) { 0 -> null; 1 -> "1 $one"; else -> "$n $many" }

    private fun failures(codes: List<String>, one: String, many: String, word: (String) -> String): String? {
        if (codes.isEmpty()) return null
        val reasons = codes.distinct().joinToString("; ", transform = word)
        return "Hinweis der App: " + (if (codes.size == 1) "1 $one" else "${codes.size} $many") + " fehlgeschlagen ($reasons)."
    }

    companion object {
        /** As many as the sources card shows. */
        const val MAX_SOURCES = 8
        const val MAX_TITLE = 160
    }
}

/** Reads the server-tool blocks of one response. Shapes: documentation, „Response“ and „Errors“ of each tool page. */
internal object ClaudeServerToolReading {
    fun outcome(message: BetaMessage): ClaudeServerToolOutcome {
        val sources = mutableListOf<ClaudeServerToolOutcome.Source>()
        val searchErrors = mutableListOf<String>()
        val fetchErrors = mutableListOf<String>()
        val codeErrors = mutableListOf<String>()
        var searches = 0
        var fetches = 0
        var steps = 0
        var files = 0
        message.content().forEach { block ->
            block.serverToolUse().ifPresent { use ->
                when {
                    use.name() == BetaServerToolUseBlock.Name.WEB_SEARCH -> searches++
                    use.name() == BetaServerToolUseBlock.Name.WEB_FETCH -> fetches++
                    ClaudeServerToolPolicy.isCodeStep(use.name()) -> steps++
                }
            }
            block.text().ifPresent { text ->
                text.citations().orElse(emptyList()).forEach { citation ->
                    citation.webSearchResultLocation().ifPresent { cited ->
                        source(cited.title().orElse(null), cited.url())?.let(sources::add)
                    }
                }
            }
            block.webSearchToolResult().ifPresent { result ->
                result.content().error().ifPresent { searchErrors += code(it.errorCode().asString()) }
            }
            block.webFetchToolResult().ifPresent { result ->
                val content = result.content()
                content.betaWebFetchToolResultErrorBlock().ifPresent { fetchErrors += code(it.errorCode().asString()) }
                content.betaWebFetchBlock().ifPresent { page ->
                    source(page.content().title().orElse(null), page.url())?.let(sources::add)
                }
            }
            block.bashCodeExecutionToolResult().ifPresent { result ->
                val content = result.content()
                content.betaBashCodeExecutionToolResultError().ifPresent { codeErrors += code(it.errorCode().asString()) }
                content.betaBashCodeExecutionResultBlock().ifPresent { files += it.content().size }
            }
            block.textEditorCodeExecutionToolResult().ifPresent { result ->
                result.content().betaTextEditorCodeExecutionToolResultError()
                    .ifPresent { codeErrors += code(it.errorCode().asString()) }
            }
            block.codeExecutionToolResult().ifPresent { result ->
                val content = result.content()
                content.error().ifPresent { codeErrors += code(it.errorCode().asString()) }
                content.resultBlock().ifPresent { files += it.content().size }
            }
        }
        return ClaudeServerToolOutcome(sources.distinctBy { it.url }.take(ClaudeServerToolOutcome.MAX_SOURCES),
            searches, fetches, steps, searchErrors, fetchErrors, codeErrors, files)
    }

    /**
     * `usage.server_tool_use` of one response. `code_execution_requests` has no typed accessor in the SDK version
     * in use; when the response does not report it, the code steps seen in the response are counted instead.
     */
    fun usage(message: BetaMessage, seen: ClaudeServerToolOutcome): ClaudeServerToolUsage {
        val reported = message.usage().serverToolUse().orElse(null)
        fun long(read: () -> Long): Long? = runCatching(read).getOrNull()?.takeIf { it >= 0 }
        val code = reported?.let { tools ->
            runCatching { tools._additionalProperties()["code_execution_requests"]?.asNumber()?.orElse(null)?.toLong() }
                .getOrNull()?.takeIf { it >= 0 }
        }
        return ClaudeServerToolUsage(
            webSearches = reported?.let { tools -> long { tools.webSearchRequests() } } ?: seen.webSearches.toLong(),
            webFetches = reported?.let { tools -> long { tools.webFetchRequests() } } ?: seen.webFetches.toLong(),
            codeExecutions = code ?: seen.codeSteps.toLong())
    }

    /** Only a real web address with a readable title becomes a source; nothing is completed or guessed. */
    private fun source(title: String?, url: String): ClaudeServerToolOutcome.Source? {
        val address = url.trim()
        if (address.length !in 10..2_000 || address.any { it.isWhitespace() || it.isISOControl() } ||
            !(address.startsWith("https://") || address.startsWith("http://"))) return null
        val shown = title.orEmpty().replace(Regex("\\s+"), " ").trim().take(ClaudeServerToolOutcome.MAX_TITLE)
        return ClaudeServerToolOutcome.Source(shown.ifEmpty { address.take(ClaudeServerToolOutcome.MAX_TITLE) }, address)
    }

    /** An error code is a short lowercase word of the API; anything else is not shown. */
    private fun code(raw: String): String = raw.takeIf { Regex("[a-z_]{1,48}").matches(it) } ?: "unknown"
}

/** Owner texts of the two switches. Pure, so they are checked without a device. */
object ClaudeServerToolTexts {
    const val WEB_LABEL = "Websuche und Seitenabruf über die Cloud"
    const val CODE_LABEL = "Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen)"

    const val SAY_WEB_ON = "Cloud-Websuche an"
    const val SAY_WEB_OFF = "Cloud-Websuche aus"
    const val SAY_CODE_ON = "Cloud-Code-Ausführung an"
    const val SAY_CODE_OFF = "Cloud-Code-Ausführung aus"
    const val SAY_STATUS = "Cloud-Werkzeuge zeigen"

    /** What leaves the device and what it costs; shown before the switch is turned on and in its status. */
    val WEB_EXPLANATION: String =
        "Ist der Schalter an, darf mein Cloud-Gehirn selbst im Internet suchen und Seiten abrufen. Die Suche und der " +
        "Abruf laufen bei Anthropic, nicht auf deinem Gerät.\n" +
        "Was das Gerät verlässt: Die Suchanfragen formuliert das Cloud-Modell selbst aus unserem Gespräch – die App " +
        "sieht sie erst im Ergebnis und kann sie vorher nicht prüfen. Abgerufen werden nur Adressen, die du selbst " +
        "genannt hast oder die aus einer Suche stammen; Adressen aus deinen Nachrichten oder Dateien ruft es nicht ab.\n" +
        "Kosten: Jede Suche kostet zusätzliches API-Guthaben (laut Anthropic-Dokumentation 10 USD je 1.000 Suchen, " +
        ClaudePricing.SERVER_TOOLS_AS_OF + "), dazu die Token der Treffer und Seiten. Die Suchen rechne ich in dein " +
        "Monatslimit ein. Ein Seitenabruf hat keinen Aufpreis, nur seine Token.\n" +
        "Grenzen: höchstens ${ClaudeServerToolPolicy.WEB_SEARCH_MAX_USES} Suchen und " +
        "${ClaudeServerToolPolicy.WEB_FETCH_MAX_USES} Seitenabrufe je Auftrag.\n" +
        "Dein Internetmodus gilt weiter: bei „Aus“ sucht die Cloud nie; bei „Auf Nachfrage“ frage ich dich vorher für " +
        "genau diesen Auftrag; bei „Dauerhaft freigegeben“ entscheidet das Modell selbst, ob es sucht. Der Schalter wirkt " +
        "nur, solange die Cloud als Gehirn gewählt ist."

    val CODE_EXPLANATION: String =
        "Ist der Schalter an, darf mein Cloud-Gehirn Programmcode in einer abgeschotteten Umgebung bei Anthropic " +
        "ausführen – für genaue Rechnungen und Datenauswertung. Die Umgebung hat kein Internet und keinen Zugriff auf " +
        "dein Gerät.\n" +
        "Was das Gerät verlässt: Das Modell kann Inhalte aus unserem Gespräch in diese Umgebung schreiben. Dateien von " +
        "deinem Gerät kommen dort nur an, wenn deine Freigabe für Dateiinhalte an ist und du es in dem Auftrag " +
        "verlangst. Bildschirminhalte anderer Apps gehen nie dorthin. Anthropic bewahrt die Daten der Umgebung bis " +
        "zu 30 Tage auf.\n" +
        "Kosten: zusätzliches API-Guthaben. Anthropic rechnet die Code-Ausführung nach Laufzeit ab (laut " +
        "Dokumentation mindestens 5 Minuten je Nutzung; 1.550 Stunden im Monat je Organisation frei, danach 0,05 USD " +
        "je Stunde; " + ClaudePricing.SERVER_TOOLS_AS_OF + "). Die Laufzeit erfährt diese App nicht: Ich zähle die " +
        "Ausführungen und zeige sie dir, aber ihr Preis ist NICHT in deinem Monatslimit enthalten. Die Token zählen " +
        "wie immer mit.\n" +
        "Grenze: höchstens ${ClaudeServerToolPolicy.CODE_MAX_STEPS} Schritte je Auftrag, dann breche ich ab.\n" +
        "Noch nicht eingebaut: Dateien, die dort entstehen, kann ich nicht auf dein Gerät holen. Ergebnisse bekommst du " +
        "als Text; für PDF und Word gibt es weiter den Ablauf der App. Der Schalter wirkt nur, solange die Cloud als " +
        "Gehirn gewählt ist."

    fun switchLabel(label: String, on: Boolean): String = label + ": " + if (on) "EIN" else "AUS"

    /** The state of both switches and whether they take effect right now. */
    fun status(settings: ClaudeBrainSettings, internetMode: InternetAccessMode?, keyPresent: Boolean): String = buildString {
        append(switchLabel(WEB_LABEL, settings.cloudWebSearch)).append('\n')
        append(switchLabel(CODE_LABEL, settings.cloudCodeExecution)).append('\n')
        val usable = settings.claudeSelected && settings.consentGiven
        when {
            !settings.cloudWebSearch && !settings.cloudCodeExecution ->
                append("Beide sind aus: Über die Cloud wird weder gesucht noch Code ausgeführt.")
            !usable -> append("Sie wirken gerade nicht: Die Cloud ist nicht als Gehirn gewählt" +
                (if (settings.consentGiven) "." else " und die Zustimmung zur Datenübertragung fehlt."))
            !keyPresent -> append("Sie wirken gerade nicht: Es ist kein API-Schlüssel gespeichert.")
            else -> {
                if (settings.cloudWebSearch) append(when (internetMode) {
                    InternetAccessMode.OFF -> "Websuche: wirkt gerade nicht, weil dein Internetmodus „Aus“ ist."
                    InternetAccessMode.ASK -> "Websuche: Internetmodus „Auf Nachfrage“ – ich frage dich vor einer " +
                        "Suche für genau diesen Auftrag."
                    InternetAccessMode.ALWAYS_ON -> "Websuche: aktiv – das Modell entscheidet selbst, ob es sucht " +
                        "(höchstens ${ClaudeServerToolPolicy.WEB_SEARCH_MAX_USES} Suchen und " +
                        "${ClaudeServerToolPolicy.WEB_FETCH_MAX_USES} Seitenabrufe je Auftrag)."
                    null -> "Websuche: richtet sich nach deinem Internetmodus."
                })
                if (settings.cloudWebSearch && settings.cloudCodeExecution) append('\n')
                if (settings.cloudCodeExecution) append("Code-Ausführung: aktiv (höchstens " +
                    "${ClaudeServerToolPolicy.CODE_MAX_STEPS} Schritte je Auftrag).")
            }
        }
    }

    /** The first sentences of the app's one-time web consent dialog when the cloud does the searching. */
    val CONSENT_DIALOG_NOTE: String =
        "Die Suche läuft über die Cloud (Anthropic): Das Cloud-Modell formuliert die Suchanfragen selbst und ruft " +
        "höchstens ${ClaudeServerToolPolicy.WEB_SEARCH_MAX_USES} Suchen und ${ClaudeServerToolPolicy.WEB_FETCH_MAX_USES} " +
        "Seiten ab. Jede Suche kostet zusätzliches API-Guthaben. "

    fun searchError(code: String): String = when (code) {
        "too_many_requests" -> "zu viele Anfragen, Ratenbegrenzung"
        "invalid_tool_input" -> "ungültige Suchanfrage"
        "max_uses_exceeded" -> "Grenze von ${ClaudeServerToolPolicy.WEB_SEARCH_MAX_USES} Suchen je Auftrag erreicht"
        "query_too_long" -> "Suchanfrage zu lang"
        "request_too_large" -> "Suchanfrage zu groß"
        "unavailable" -> "Suchdienst nicht verfügbar"
        else -> "Fehlercode $code"
    }

    fun fetchError(code: String): String = when (code) {
        "invalid_tool_input" -> "ungültige Adresse"
        "url_too_long" -> "Adresse zu lang"
        "url_not_allowed" -> "Adresse nicht erlaubt"
        "url_not_in_prior_context" -> "Adresse kam im Gespräch nicht vor"
        "url_not_accessible" -> "Seite nicht erreichbar"
        "too_many_requests" -> "zu viele Anfragen, Ratenbegrenzung"
        "unsupported_content_type" -> "Inhaltstyp nicht unterstützt (nur Text, HTML und PDF)"
        "content_too_large" -> "Inhalt zu groß"
        "max_uses_exceeded" -> "Grenze von ${ClaudeServerToolPolicy.WEB_FETCH_MAX_USES} Seitenabrufen je Auftrag erreicht"
        "unavailable" -> "Abrufdienst nicht verfügbar"
        else -> "Fehlercode $code"
    }

    fun codeError(code: String): String = when (code) {
        "unavailable" -> "Code-Umgebung nicht verfügbar"
        "execution_time_exceeded" -> "Zeitlimit der Ausführung überschritten"
        "invalid_tool_input" -> "ungültige Eingabe"
        "too_many_requests" -> "zu viele Anfragen, Ratenbegrenzung"
        "output_file_too_large" -> "Ausgabe zu groß"
        "file_not_found" -> "Datei in der Code-Umgebung nicht gefunden"
        else -> "Fehlercode $code"
    }
}

/**
 * The typed or spoken commands for the two switches. Fail closed like the device-tool commands: only the exact
 * shapes below switch anything; a question (other than the status question), a negation or a longer sentence is no
 * command and keeps its ordinary route.
 */
object ClaudeServerToolCommand {
    enum class Command { WEB_ON, WEB_OFF, CODE_ON, CODE_OFF, STATUS }

    private const val WEB = "cloud-?\\s?(?:web-?\\s?suche|internet-?\\s?suche|suche)"
    private const val CODE = "cloud-?\\s?(?:code-?\\s?ausführung|code|berechnung(?:en)?)"
    private const val ON = "(?:an|ein|einschalten|anschalten|aktivieren)"
    private const val OFF = "(?:aus|ab|ausschalten|abschalten|deaktivieren)"
    private const val LEAD = "(?:schalte\\s+(?:die\\s+)?)?(?:die\\s+)?"

    private val webOn = Regex("^$LEAD$WEB\\s+$ON$")
    private val webOff = Regex("^$LEAD$WEB\\s+$OFF$")
    private val codeOn = Regex("^$LEAD$CODE\\s+$ON$")
    private val codeOff = Regex("^$LEAD$CODE\\s+$OFF$")
    private val status = listOf(
        Regex("^(?:zeig(?:e)?\\s+(?:mir\\s+)?(?:die\\s+)?)?cloud-?\\s?werkzeuge(?:\\s+(?:zeigen|anzeigen))?\\??$"),
        Regex("^(?:ist|sind)\\s+(?:die\\s+)?(?:$WEB|$CODE|cloud-?\\s?werkzeuge)\\s+(?:an|aus|ein|aktiv|eingeschaltet)\\??$"),
        Regex("^status\\s+(?:der\\s+)?(?:$WEB|$CODE|cloud-?\\s?werkzeuge)\\??$"))

    fun parse(utterance: String, assistantName: String? = null): Command? {
        if (utterance.length !in 8..80 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("^bitte\\s+"), "").replace(Regex("\\s+bitte(?=[.!]*$)"), "").replace(Regex("\\s+"), " ").trim()
        val plain = text.trimEnd('.', '!')
        if (status.any { it.matches(plain) }) return Command.STATUS
        // A question mark never switches anything.
        if ('?' in text) return null
        return when {
            webOn.matches(plain) -> Command.WEB_ON
            webOff.matches(plain) -> Command.WEB_OFF
            codeOn.matches(plain) -> Command.CODE_ON
            codeOff.matches(plain) -> Command.CODE_OFF
            else -> null
        }
    }

    /**
     * Applies [utterance] to [brain] and returns the reply, or null when it is no such command. Nothing is sent.
     * Switching on states in full what leaves the device and what it costs.
     */
    fun reply(brain: ClaudeBrain?, utterance: String, assistantName: String?, internetMode: InternetAccessMode?): String? {
        val command = parse(utterance, assistantName) ?: return null
        if (brain == null) return "Das Cloud-Gehirn ist in dieser App nicht eingerichtet; es wurde nichts geändert."
        fun state() = ClaudeServerToolTexts.status(brain.settings, internetMode, brain.hasKey())
        fun switch(on: Boolean, set: (Boolean) -> Boolean, label: String, explanation: String): String = when {
            on && !brain.settings.consentGiven -> "Das geht erst nach deiner Zustimmung zur Datenübertragung unter " +
                "„Modelle → Gehirn“. Es wurde nichts geändert."
            !set(on) -> "Die Einstellung konnte nicht gespeichert werden; es bleibt beim bisherigen Stand."
            on -> "$label ist jetzt EIN.\n\n$explanation\n\n${state()}\n\nAusschalten mit „" +
                (if (label == ClaudeServerToolTexts.WEB_LABEL) ClaudeServerToolTexts.SAY_WEB_OFF
                    else ClaudeServerToolTexts.SAY_CODE_OFF) + "“."
            else -> "$label ist jetzt AUS. Ab der nächsten Anfrage ist das nicht mehr freigeschaltet."
        }
        return when (command) {
            Command.WEB_ON -> switch(true, brain::setCloudWebSearch, ClaudeServerToolTexts.WEB_LABEL, ClaudeServerToolTexts.WEB_EXPLANATION)
            Command.WEB_OFF -> switch(false, brain::setCloudWebSearch, ClaudeServerToolTexts.WEB_LABEL, "")
            Command.CODE_ON -> switch(true, brain::setCloudCodeExecution, ClaudeServerToolTexts.CODE_LABEL, ClaudeServerToolTexts.CODE_EXPLANATION)
            Command.CODE_OFF -> switch(false, brain::setCloudCodeExecution, ClaudeServerToolTexts.CODE_LABEL, "")
            Command.STATUS -> state() + "\n\n" + brain.ledger.describeServerTools() + "\n\nUmschalten: „" +
                ClaudeServerToolTexts.SAY_WEB_ON + "“ / „" + ClaudeServerToolTexts.SAY_WEB_OFF + "“, „" +
                ClaudeServerToolTexts.SAY_CODE_ON + "“ / „" + ClaudeServerToolTexts.SAY_CODE_OFF + "“."
        }
    }
}
