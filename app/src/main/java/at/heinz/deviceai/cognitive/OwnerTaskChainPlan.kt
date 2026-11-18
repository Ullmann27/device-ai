package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.learning.StrictJson

/**
 * An owner task as data: ordered steps, each a registered tool with its arguments, what counts as success and what
 * the next step needs from it. A plan grants nothing – [OwnerTaskChain] runs it through the unchanged
 * BoundedAgentOrchestrator, ToolGate and approval flow.
 */
data class ChainPlan(
    val goal: String,
    val origin: Origin,
    /** Recipe id for [Origin.RECIPE]; null for a model plan. */
    val recipe: String?,
    val steps: List<ChainStep>,
    /** The closing sentence(s) of a completed chain, built only from handed-over values and real outputs. */
    val summary: (ChainResults) -> String = { "" }
) {
    enum class Origin { RECIPE, MODEL }

    init {
        require(goal.isNotBlank() && steps.size in 1..MAX_STEPS)
        require(steps.map { it.id }.distinct().size == steps.size)
    }

    companion object {
        /** The ToolGate of one owner turn allows four calls (ToolPolicy.maxCalls); a chain stays inside that budget. */
        const val MAX_STEPS = 4
    }
}

data class ChainStep(
    val id: String,
    val tool: String,
    val arguments: Map<String, ChainArgument>,
    val success: ChainSuccess,
    val gives: List<ChainExtract>,
    /** What this step does, for the stage and the report („Neueste APK suchen“). */
    val label: String
)

/** Where a step argument comes from: fixed by the plan or handed over by an earlier step. */
sealed interface ChainArgument {
    data class Literal(val value: Any) : ChainArgument
    data class FromStep(val step: String, val key: String, val part: Part = Part.WHOLE) : ChainArgument
    enum class Part { WHOLE, PARENT, LEAF }
}

/** What counts as success of a step, decided on the real tool output. */
sealed interface ChainSuccess {
    /** Null when [output] proves success, otherwise the German reason. */
    fun problem(output: String, value: (String, String) -> String?): String?

    data object NonEmpty : ChainSuccess {
        override fun problem(output: String, value: (String, String) -> String?) =
            if (output.isBlank()) "das Werkzeug hat nichts zurückgegeben" else null
    }
    data class Contains(val text: String) : ChainSuccess {
        override fun problem(output: String, value: (String, String) -> String?) =
            if (output.contains(text, ignoreCase = true)) null else "im Ergebnis fehlt „$text“"
    }
    /** The output must name a value an earlier step handed over (the saved file shows up in the folder). */
    data class ContainsHandedOver(val step: String, val key: String, val part: ChainArgument.Part = ChainArgument.Part.WHOLE) : ChainSuccess {
        override fun problem(output: String, value: (String, String) -> String?): String? {
            val wanted = value(step, key)?.let { OwnerChainValues.part(it, part) } ?: return "der erwartete Wert aus Schritt $step fehlt"
            return if (output.contains(wanted)) null else "„$wanted“ kommt im Ergebnis nicht vor"
        }
    }
}

/** What a later step or the report needs from a step's real output. */
sealed interface ChainExtract {
    sealed interface Result {
        data class Values(val values: Map<String, String>) : Result
        /** Several candidates: the owner picks one; [options] are (shown text, value). */
        data class Choice(val key: String, val options: List<Pair<String, String>>) : Result
        data class Missing(val reason: String) : Result
    }

    fun extract(output: String, value: (String, String) -> String?): Result

    /** The first non-blank line (find_latest_apk prints the newest first). */
    data class FirstLine(val key: String) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result =
            output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?.let { Result.Values(mapOf(key to it)) } ?: Result.Missing("kein Treffer")
    }

    /** Exactly one line is handed over; several lines need the owner's choice. */
    data class OneLine(val key: String) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result {
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_OPTIONS + 1).toList()
            return when {
                lines.isEmpty() -> Result.Missing("kein Treffer")
                lines.size == 1 -> Result.Values(mapOf(key to lines.single()))
                else -> Result.Choice(key, lines.take(MAX_OPTIONS).map { it to it })
            }
        }
    }

    /** The first capture group of [pattern] („Gespeichert: (.+)“). */
    data class Pattern(val key: String, val pattern: Regex, val missing: String) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result =
            pattern.find(output)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { Result.Values(mapOf(key to it)) } ?: Result.Missing(missing)
    }

    /** A labelled line of a probe („Version: 1.2“); an absent optional field is simply not handed over. */
    data class Field(val key: String, val label: String, val required: Boolean = true) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result {
            val found = output.lineSequence().map { it.trim() }
                .firstOrNull { it.startsWith("$label:", ignoreCase = true) }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                found != null -> Result.Values(mapOf(key to found))
                required -> Result.Missing("die Angabe „$label“ fehlt")
                else -> Result.Values(emptyMap())
            }
        }
    }

    /** The unique installed app of a find_app result (`package`, `label`); several candidates need a choice. */
    data object AppPackage : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result {
            val root = runCatching { StrictJson.parse(output) as? Map<*, *> }.getOrNull()
                ?: return Result.Missing("das Suchergebnis ist nicht lesbar")
            if (root["schema"] != "CONTINUUM_APP_DISCOVERY_V1") return Result.Missing("das Suchergebnis ist nicht lesbar")
            val candidates = (root["candidates"] as? List<*>).orEmpty().mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val pkg = map["packageName"] as? String ?: return@mapNotNull null
                val label = map["label"] as? String ?: return@mapNotNull null
                if (ToolRegistry.isExplicitAppTarget(pkg)) label to pkg else null
            }
            return when {
                candidates.isEmpty() -> Result.Missing("keine installierte App mit diesem Namen gefunden")
                candidates.size == 1 -> Result.Values(mapOf("package" to candidates[0].second, "label" to candidates[0].first))
                else -> Result.Choice("package", candidates.take(MAX_OPTIONS).map { "${it.first} (${it.second})" to it.second })
            }
        }
    }

    /** Size and date of the file an earlier step handed over, read from a real `ls -lah` listing of its folder. */
    data class ListingEntry(val step: String, val key: String) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result {
            val name = value(step, key)?.let { OwnerChainValues.part(it, ChainArgument.Part.LEAF) }
                ?: return Result.Missing("der Dateiname aus Schritt $step fehlt")
            val entry = OwnerChainValues.listing(output).firstOrNull { it.name == name }
                ?: return Result.Missing("„$name“ steht nicht in der Ordnerliste")
            return Result.Values(mapOf("name" to entry.name, "size" to entry.size, "date" to entry.date, "time" to entry.time))
        }
    }

    /** The most recently changed file of a real `ls -lah` listing, optionally only with one of [extensions]. */
    data class NewestEntry(val extensions: Set<String>) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result {
            val files = OwnerChainValues.listing(output).filter { entry ->
                !entry.directory && (extensions.isEmpty() || extensions.any { entry.name.endsWith(".$it", ignoreCase = true) })
            }
            val newest = files.maxWithOrNull(compareBy({ it.date }, { it.time }))
                ?: return Result.Missing(if (extensions.isEmpty()) "der Ordner enthält keine Datei"
                    else "der Ordner enthält keine ${extensions.first().uppercase()}-Datei")
            return Result.Values(mapOf("name" to newest.name, "size" to newest.size, "date" to newest.date,
                "time" to newest.time, "count" to files.size.toString()))
        }
    }

    /** The first lines of a probe as one handed-over block, for the report. */
    data class Lines(val key: String, val max: Int) : ChainExtract {
        override fun extract(output: String, value: (String, String) -> String?): Result {
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(max).toList()
            return if (lines.isEmpty()) Result.Missing("kein Eintrag") else Result.Values(mapOf(key to lines.joinToString("\n")))
        }
    }

    companion object {
        const val MAX_OPTIONS = 8
    }
}

/** Handed-over values of the completed steps: `value("find", "path")`. */
fun interface ChainResults {
    fun value(step: String, key: String): String?
}

internal object OwnerChainValues {
    class Entry(val directory: Boolean, val size: String, val date: String, val time: String, val name: String)

    private val line = Regex("^([-dl])[-rwxsStT]{9}\\S*\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\S+)\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$")

    /** Entries of an `ls -lah` listing (toybox/GNU long format with ISO date); other lines are ignored. */
    fun listing(output: String): List<Entry> = output.lineSequence().mapNotNull { raw ->
        val match = line.matchEntire(raw.trimEnd()) ?: return@mapNotNull null
        val name = match.groupValues[5].substringBefore(" -> ")
        if (name == "." || name == "..") null
        else Entry(match.groupValues[1] == "d", match.groupValues[2], match.groupValues[3], match.groupValues[4], name)
    }.toList()

    fun part(value: String, part: ChainArgument.Part): String = when (part) {
        ChainArgument.Part.WHOLE -> value
        ChainArgument.Part.PARENT -> value.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
        ChainArgument.Part.LEAF -> value.trimEnd('/').substringAfterLast('/')
    }

    /** „2026-11-17“ → „17.11.2026“. */
    fun germanDate(iso: String): String = iso.split('-').takeIf { it.size == 3 }?.let { "${it[2]}.${it[1]}.${it[0]}" } ?: iso
}

/**
 * What the chain knows about a registered tool: how its real output proves success and what it hands on. A tool
 * without a contract cannot be a chain step. This is the plug-in point for further tools: a tool that is in
 * [ToolRegistry] and runs behind the ToolGate becomes chainable with one [register] call – the executor is untouched.
 */
object OwnerChainStepCatalog {
    data class Contract(val tool: String, val label: String, val success: ChainSuccess, val gives: List<ChainExtract>,
                        /** Names a model plan may bind to (`{{1.path}}`). */
                        val keys: Set<String>)

    private val contracts = LinkedHashMap<String, Contract>()

    @Synchronized fun register(contract: Contract) {
        require(ToolRegistry.get(contract.tool) != null) { "Only registered tools can be chain steps." }
        contracts[contract.tool] = contract
    }

    @Synchronized fun contract(tool: String): Contract? = contracts[tool]

    /** Tools DeviceToolExecutor answers from Android itself; every other chain tool needs the Shizuku bridge. */
    private val nativeTools = setOf("get_device_info", "list_installed_apps", "get_app_info", "find_app",
        "get_storage_info", "get_network_summary", "get_battery_info")

    fun needsDeviceBridge(tool: String): Boolean = tool !in nativeTools
    @Synchronized fun all(): List<Contract> = contracts.values.toList()

    init {
        fun add(tool: String, label: String, success: ChainSuccess, gives: List<ChainExtract>, vararg keys: String) =
            register(Contract(tool, label, success, gives, keys.toSet()))
        add("get_battery_info", "Akku prüfen", ChainSuccess.NonEmpty, listOf(ChainExtract.Lines("report", 6)), "report")
        add("get_storage_info", "Speicher prüfen", ChainSuccess.Contains("Gesamt:"), listOf(
            ChainExtract.Field("total", "Gesamt"), ChainExtract.Field("free", "Für die App nutzbar")), "total", "free")
        add("get_network_summary", "Netzwerk prüfen", ChainSuccess.NonEmpty, listOf(ChainExtract.Lines("report", 6)), "report")
        add("get_device_info", "Geräteinformationen lesen", ChainSuccess.Contains("Modell:"), listOf(
            ChainExtract.Field("model", "Modell"), ChainExtract.Field("android", "Android")), "model", "android")
        add("list_files", "Ordner lesen", ChainSuccess.NonEmpty, listOf(ChainExtract.Lines("listing", 12)), "listing")
        add("find_files", "Dateien suchen", ChainSuccess.NonEmpty, listOf(ChainExtract.OneLine("path")), "path")
        add("find_latest_apk", "Neueste APK suchen", ChainSuccess.Contains(".apk"), listOf(ChainExtract.FirstLine("path")), "path")
        add("get_large_files", "Große Dateien suchen", ChainSuccess.NonEmpty, listOf(ChainExtract.Lines("largest", 5)), "largest")
        add("find_app", "App suchen", ChainSuccess.Contains("CONTINUUM_APP_DISCOVERY_V1"), listOf(ChainExtract.AppPackage), "package", "label")
        add("get_app_info", "App-Info lesen", ChainSuccess.Contains("Paket:"), listOf(
            ChainExtract.Field("version", "Version"), ChainExtract.Field("code", "Versionscode", required = false)), "version", "code")
        add("list_installed_apps", "Apps auflisten", ChainSuccess.NonEmpty, listOf(ChainExtract.Lines("apps", 12)), "apps")
        add("take_screenshot", "Screenshot erstellen", ChainSuccess.Contains("Gespeichert:"), listOf(
            ChainExtract.Pattern("path", Regex("Gespeichert:\\s*(\\S.*)"), "der Speicherort wurde nicht gemeldet")), "path")
        add("restart_app", "App neu starten", ChainSuccess.Contains("Neu gestartet:"), emptyList())
        add("stop_app", "App stoppen", ChainSuccess.Contains("Gestoppt:"), emptyList())
    }
}

/**
 * Deterministic validation of a model-proposed plan. An invalid plan is rejected with its reason; it is never
 * repaired, reordered or completed. Rules:
 *  1. only tools of [ToolRegistry] that have a chain contract; no web tools (they own a separate consent session);
 *  2. no DESTRUCTIVE tool and no install/uninstall – those stay explicit single owner requests;
 *  3. every required argument present, no unknown argument, literals pass the registry's typed schema;
 *  4. a binding points to an earlier step and to a value that step really hands over;
 *  5. at most one changing step (CONFIRM), and after it only read-only steps (a change is verified, not chained);
 *  6. no step twice with the same tool and arguments; at most [ChainPlan.MAX_STEPS] steps;
 *  7. the owner's own request must not rule the action out.
 */
object OwnerChainPlanValidator {
    sealed interface Result {
        data class Valid(val plan: ChainPlan) : Result
        data class Rejected(val reason: String) : Result
    }

    data class DraftStep(val tool: String, val arguments: Map<String, Any?>)

    private val binding = Regex("^\\{\\{\\s*(\\d{1,2})\\.([a-z_]{1,32})(?:\\s*\\|\\s*(parent|leaf))?\\s*}}$")
    private val webTools = setOf("web_search", "web_read")

    fun validate(ownerTask: String, goal: String, draft: List<DraftStep>): Result {
        if (goal.isBlank() || goal.length > 300 || goal.any { it.isISOControl() }) return Result.Rejected("Das Ziel des Plans fehlt oder ist zu lang.")
        if (draft.isEmpty()) return Result.Rejected("Der Plan enthält keinen Schritt.")
        if (draft.size > ChainPlan.MAX_STEPS) return Result.Rejected("Der Plan hat ${draft.size} Schritte; erlaubt sind höchstens ${ChainPlan.MAX_STEPS}.")
        val steps = ArrayList<ChainStep>()
        var changingAt = -1
        draft.forEachIndexed { index, item ->
            val number = index + 1
            val spec = ToolRegistry.get(item.tool) ?: return Result.Rejected("Schritt $number: unbekanntes Werkzeug „${item.tool.take(40)}“.")
            if (item.tool in webTools) return Result.Rejected("Schritt $number: Internetwerkzeuge laufen nur über die eigene Internetfreigabe, nicht in einer Aufgabenkette.")
            if (spec.risk == Risk.DESTRUCTIVE || ExternalContentActionGuard.isHighImpact(item.tool, spec.risk))
                return Result.Rejected("Schritt $number: „${spec.label}“ ist löschend oder schwerwiegend und bleibt ein eigener, ausdrücklicher Auftrag.")
            val contract = OwnerChainStepCatalog.contract(item.tool)
                ?: return Result.Rejected("Schritt $number: für „${spec.label}“ gibt es keine Erfolgsprüfung; es kann kein Kettenschritt sein.")
            if (ExternalContentActionGuard.ownerNegated(ownerTask, item.tool))
                return Result.Rejected("Schritt $number: dein Auftrag schließt „${spec.label}“ ausdrücklich aus.")
            val missing = spec.required - item.arguments.keys
            if (missing.isNotEmpty()) return Result.Rejected("Schritt $number: für „${spec.label}“ fehlt das Argument ${missing.joinToString()}.")
            val unknown = item.arguments.keys - (spec.required + spec.optional)
            if (unknown.isNotEmpty()) return Result.Rejected("Schritt $number: „${spec.label}“ kennt das Argument ${unknown.joinToString()} nicht.")
            val arguments = LinkedHashMap<String, ChainArgument>()
            val literals = LinkedHashMap<String, Any?>()
            item.arguments.forEach { (key, raw) ->
                val bound = (raw as? String)?.let { binding.matchEntire(it.trim()) }
                if (bound == null) {
                    if (raw == null || (raw is String && ("{{" in raw || raw.isBlank())))
                        return Result.Rejected("Schritt $number: das Argument $key ist leer oder enthält eine unvollständige Übergabe.")
                    arguments[key] = ChainArgument.Literal(raw)
                    literals[key] = raw
                } else {
                    val source = bound.groupValues[1].toInt()
                    if (source !in 1 until number) return Result.Rejected(
                        "Schritt $number: das Argument $key verweist auf Schritt $source, der nicht davor liegt.")
                    val given = OwnerChainStepCatalog.contract(draft[source - 1].tool)!!.keys
                    if (bound.groupValues[2] !in given) return Result.Rejected(
                        "Schritt $number: Schritt $source übergibt keinen Wert „${bound.groupValues[2]}“.")
                    arguments[key] = ChainArgument.FromStep(source.toString(), bound.groupValues[2], when (bound.groupValues[3]) {
                        "parent" -> ChainArgument.Part.PARENT
                        "leaf" -> ChainArgument.Part.LEAF
                        else -> ChainArgument.Part.WHOLE
                    })
                }
            }
            // The registry's typed schema decides on literals; bound values are checked again when they exist.
            spec.schema.forEach { (key, schema) -> literals[key]?.let { value ->
                schema.check(value)?.let { return Result.Rejected("Schritt $number: Argument $key $it.") }
            } }
            if (arguments.values.none { it is ChainArgument.FromStep })
                ToolProposal(item.tool, literals).validate()?.let { return Result.Rejected("Schritt $number: $it") }
            if (spec.risk != Risk.READ_ONLY) {
                if (changingAt >= 0) return Result.Rejected(
                    "Schritt $number: ein Plan darf höchstens einen ändernden Schritt enthalten; danach wird nur noch geprüft.")
                changingAt = index
            }
            if (steps.any { it.tool == item.tool && it.arguments == arguments })
                return Result.Rejected("Schritt $number wiederholt Schritt ${steps.indexOfFirst { it.tool == item.tool && it.arguments == arguments } + 1} unverändert.")
            steps += ChainStep(number.toString(), item.tool, arguments, contract.success, contract.gives, contract.label)
        }
        return Result.Valid(ChainPlan(goal.trim(), ChainPlan.Origin.MODEL, null, steps))
    }
}

/**
 * The plan a model may propose: one strict JSON object, nothing else.
 * `{"schema":"continuum-owner-chain-v1","goal":"…","steps":[{"tool":"find_latest_apk","args":{}},
 *   {"tool":"list_files","args":{"path":"{{1.path|parent}}"}}]}`
 * The model names tools and arguments only. Success conditions and hand-over come from [OwnerChainStepCatalog], so a
 * model cannot weaken the verification of its own plan.
 */
object OwnerChainPlanCodec {
    const val SCHEMA = "continuum-owner-chain-v1"
    const val MAX_CHARS = 4_000

    fun parse(ownerTask: String, text: String): OwnerChainPlanValidator.Result {
        val body = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (body.isEmpty() || body.length > MAX_CHARS) return rejected("Der Modellplan ist leer oder zu lang.")
        val root = runCatching { StrictJson.parse(body, maxChars = MAX_CHARS) as? Map<*, *> }.getOrNull()
            ?: return rejected("Der Modellplan ist kein gültiges JSON-Objekt.")
        if (root.keys != setOf("schema", "goal", "steps") || root["schema"] != SCHEMA)
            return rejected("Der Modellplan hält das Planformat nicht ein.")
        val goal = root["goal"] as? String ?: return rejected("Dem Modellplan fehlt das Ziel.")
        val rawSteps = root["steps"] as? List<*> ?: return rejected("Dem Modellplan fehlt die Schrittliste.")
        val draft = rawSteps.mapIndexed { index, raw ->
            val map = raw as? Map<*, *> ?: return rejected("Schritt ${index + 1} ist kein Objekt.")
            if (map.keys != setOf("tool", "args")) return rejected("Schritt ${index + 1} hält das Planformat nicht ein.")
            val tool = map["tool"] as? String ?: return rejected("Schritt ${index + 1} nennt kein Werkzeug.")
            val args = map["args"] as? Map<*, *> ?: return rejected("Schritt ${index + 1} hat keine Argumentliste.")
            if (args.keys.any { it !is String }) return rejected("Schritt ${index + 1} hat ein ungültiges Argument.")
            OwnerChainPlanValidator.DraftStep(tool, args.entries.associate { (key, value) -> key as String to value })
        }
        return OwnerChainPlanValidator.validate(ownerTask, goal, draft)
    }

    private fun rejected(reason: String) = OwnerChainPlanValidator.Result.Rejected(reason)

    /** The bounded instruction for a text runtime (local model or Claude); the catalog is the only tool list. */
    fun prompt(ownerTask: String): String = buildString {
        append("Du planst einen Geräteauftrag als Schrittfolge. Antworte NUR mit einem JSON-Objekt in genau dieser Form:\n")
        append("{\"schema\":\"$SCHEMA\",\"goal\":\"<Ziel in einem Satz>\",\"steps\":[{\"tool\":\"<Name>\",\"args\":{}}]}\n")
        append("Regeln: höchstens ${ChainPlan.MAX_STEPS} Schritte, nur diese Werkzeuge, keine erfundenen Argumente. ")
        append("Ein Ergebnis eines früheren Schritts übergibst du als ganzen Argumentwert {{Schrittnummer.Wert}}, ")
        append("für den Ordner einer Datei {{Schrittnummer.Wert|parent}}.\nWerkzeuge:\n")
        OwnerChainStepCatalog.all().forEach { contract ->
            val spec = ToolRegistry.get(contract.tool) ?: return@forEach
            append("- ").append(spec.name).append('(')
            append((spec.required.map { it } + spec.optional.map { "$it?" }).joinToString(", "))
            append(") – ").append(spec.description.take(90))
            if (contract.keys.isNotEmpty()) append(" Übergibt: ").append(contract.keys.joinToString(", "))
            append('\n')
        }
        append("Auftrag: ").append(ownerTask.filterNot { it.isISOControl() }.take(400))
    }
}

/** Text source for a model plan. The app binds it to the loaded runtime; tests bind a fixed answer. */
fun interface OwnerChainPlanSource {
    /** The raw model text for [prompt], or null when no model answered. */
    fun propose(prompt: String): String?
}
