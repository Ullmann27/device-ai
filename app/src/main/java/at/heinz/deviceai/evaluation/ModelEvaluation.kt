package at.heinz.deviceai.evaluation

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * On-device evaluation of the local language model with docs/evaluation/continuum-eval-v1
 * (`model-dev.jsonl`, `model-holdout.jsonl`, shipped as assets under `continuum-eval-v1/`).
 *
 * Scope, stated in every report: this measures the RAW model with the dialogue placed in one prompt –
 * no Continuum memory, no deterministic lanes, no tools. Tool-choice cases are therefore NOT scored here.
 * Content criteria are formal; abstention and ask-back criteria are lexical heuristics and are reported
 * separately so nobody mistakes them for a formal verdict. Nothing is estimated: every latency, heat and
 * memory figure in a report is a value sampled on the device during that run.
 */
data class ModelEvalCase(
    val id: String,
    val category: String,
    val turns: List<Turn>,
    val criterion: String,
    val values: List<String>,
    val tool: String?,
    val forbiddenTools: List<String>
) {
    data class Turn(val user: Boolean, val text: String)

    companion object {
        fun parse(line: String): ModelEvalCase {
            val json = JSONObject(line)
            val expect = json.getJSONObject("expect")
            val turns = json.getJSONArray("turns")
            return ModelEvalCase(
                id = json.getString("id"),
                category = json.getString("category"),
                turns = (0 until turns.length()).map { index ->
                    val turn = turns.getJSONObject(index)
                    Turn(user = turn.getString("role") == "user", text = turn.getString("text"))
                },
                criterion = expect.getString("type"),
                values = strings(expect.optJSONArray("values")),
                tool = if (expect.isNull("tool")) null else expect.getString("tool"),
                forbiddenTools = strings(expect.optJSONArray("forbidden_tools"))
            )
        }

        fun parseAll(lines: Sequence<String>): List<ModelEvalCase> =
            lines.filter { it.isNotBlank() }.map(::parse).toList()

        private fun strings(array: JSONArray?): List<String> =
            if (array == null) emptyList() else (0 until array.length()).map(array::getString)
    }
}

enum class ModelEvalVerdict { PASS, FAIL, NOT_SCORED }

/** [heuristic]: the verdict rests on wording, not on a formally checkable value. */
data class ModelEvalScore(val verdict: ModelEvalVerdict, val heuristic: Boolean, val reason: String)

object ModelEvalScoring {
    const val MAX_PROMPT_BYTES = 6_000

    /** The dialogue as one tool-free prompt, or null when it does not fit [maxBytes] (never truncated). */
    fun prompt(case: ModelEvalCase, maxBytes: Int = MAX_PROMPT_BYTES): String? {
        if (case.turns.isEmpty() || !case.turns.last().user) return null
        val text = buildString {
            append("Du bist ein hilfreicher Assistent. Antworte auf Deutsch, kurz und nur mit dem, was aus dem ")
            append("Gespräch hervorgeht oder was du sicher weißt. Wenn etwas fehlt oder unklar ist, sag das ")
            append("und frag nach.\n")
            if (case.turns.size > 1) {
                append("\nBisheriges Gespräch:\n")
                case.turns.dropLast(1).forEach { append(if (it.user) "Nutzer: " else "Assistent: ").append(it.text).append('\n') }
            }
            append("\nNutzer: ").append(case.turns.last().text).append("\nAssistent:")
        }
        return text.takeIf { it.toByteArray(Charsets.UTF_8).size <= maxBytes }
    }

    fun score(case: ModelEvalCase, answer: String): ModelEvalScore {
        val text = normalize(answer)
        if (text.isEmpty()) return ModelEvalScore(ModelEvalVerdict.FAIL, false, "keine Antwort")
        return when (case.criterion) {
            "contains_all" -> {
                val missing = case.values.filter { value -> variants(value).none { it in text } }
                if (missing.isEmpty()) ModelEvalScore(ModelEvalVerdict.PASS, false, "alle erwarteten Angaben enthalten")
                else ModelEvalScore(ModelEvalVerdict.FAIL, false, "fehlt: " + missing.joinToString(", "))
            }
            "contains_any" ->
                if (case.values.any { value -> variants(value).any { it in text } })
                    ModelEvalScore(ModelEvalVerdict.PASS, false, "erwartete Angabe enthalten")
                else ModelEvalScore(ModelEvalVerdict.FAIL, false, "keine der erwarteten Angaben enthalten")
            "numeric" -> {
                val wanted = case.values.mapNotNull(::number)
                val found = numbers(answer)
                if (wanted.isNotEmpty() && wanted.all { target -> found.any { close(it, target) } })
                    ModelEvalScore(ModelEvalVerdict.PASS, false, "Zahl stimmt")
                else ModelEvalScore(ModelEvalVerdict.FAIL, false,
                    "erwartet " + case.values.joinToString(", ") + ", gefunden " + found.take(6).joinToString(", "))
            }
            "abstain" ->
                if (abstention.containsMatchIn(text)) ModelEvalScore(ModelEvalVerdict.PASS, true, "Enthaltung formuliert")
                else ModelEvalScore(ModelEvalVerdict.FAIL, true, "keine Enthaltung erkennbar")
            "asks_back" ->
                if ('?' in answer && question.containsMatchIn(text)) ModelEvalScore(ModelEvalVerdict.PASS, true, "Rückfrage gestellt")
                else ModelEvalScore(ModelEvalVerdict.FAIL, true, "keine Rückfrage erkennbar")
            "tool_call", "no_tool" -> ModelEvalScore(ModelEvalVerdict.NOT_SCORED, false,
                "Werkzeugwahl ist im Rohmodell-Lauf ohne Werkzeuge nicht beobachtbar")
            else -> ModelEvalScore(ModelEvalVerdict.NOT_SCORED, false, "unbekanntes Kriterium " + case.criterion)
        }
    }

    internal fun normalize(text: String): String =
        text.lowercase(Locale.GERMAN).replace(Regex("[\\s\\u00a0]+"), " ").trim()

    /** A value and, for small whole numbers, its other spelling („fünf“ ↔ „5“). */
    internal fun variants(value: String): List<String> {
        val base = normalize(value)
        val index = numberWords.indexOf(base)
        return when {
            index >= 0 -> listOf(base, index.toString())
            base.toIntOrNull() in numberWords.indices -> listOf(base, numberWords[base.toInt()])
            else -> listOf(base)
        }
    }

    /** Numbers in German or plain notation: „1.250,50“, „1250.5“, „90“, „-3“. */
    internal fun numbers(text: String): List<Double> =
        Regex("(?<![\\p{L}\\d])-?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?|(?<![\\p{L}\\d])-?\\d+(?:[.,]\\d+)?").findAll(text)
            .mapNotNull { number(it.value) }.toList()

    internal fun number(raw: String): Double? {
        val text = raw.trim()
        val german = Regex("-?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?").matches(text) || (',' in text && '.' !in text)
        return (if (german) text.replace(".", "").replace(',', '.') else text.replace(",", "")).toDoubleOrNull()
    }

    private fun close(found: Double, wanted: Double): Boolean =
        found == wanted || Math.abs(found - wanted) <= Math.abs(wanted) * 0.005

    private val numberWords = listOf("null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun",
        "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn", "zwanzig")
    private val abstention = Regex("wei(?:ß|ss) ich nicht|nicht bekannt|keine (?:angabe|information|informationen|daten|auskunft)|" +
        "nicht (?:genannt|gesagt|erwähnt|angegeben|verfügbar|möglich|enthalten|hervor)|kann ich (?:dir |ihnen )?nicht|" +
        "habe (?:ich )?(?:dazu |darüber )?(?:keine|keinen)|liegt (?:mir )?nicht vor|steht nicht|geht nicht hervor|" +
        "ohne (?:internet|zugriff|sensor)|keinen zugriff|nicht sicher|unbekannt|hast du (?:mir )?(?:noch )?nicht")
    private val question = Regex("\\b(?:welche[nrsm]?|was|wen|wem|wer|wann|wo|wohin|wie|wieviel|meinst du|möchtest du|" +
        "soll ich|kannst du|könntest du|magst du|genauer|bitte nenn|sag mir)\\b")
}

/** One generation as observed on the device. */
data class ModelEvalGeneration(
    val text: String,
    val completed: Boolean,
    val outputLimit: Boolean,
    val failure: String?,
    val firstTextMs: Long?,
    val totalMs: Long,
    val textChunks: Int,
    /** Native token counts as the runtime reported them; null when it reported none. */
    val inputTokens: Int? = null,
    val outputTokens: Int? = null
)

/** Values Android reported at one moment; null where the platform gave none. */
data class ModelEvalDeviceSample(
    val thermalStatus: Int?,
    val batteryPercent: Int?,
    val availableRamBytes: Long?,
    val jvmHeapUsedBytes: Long
)

class ModelEvalRunner(
    private val generate: (String) -> ModelEvalGeneration,
    private val sample: () -> ModelEvalDeviceSample,
    private val cancelled: () -> Boolean,
    private val onProgress: (done: Int, total: Int, caseId: String) -> Unit = { _, _, _ -> }
) {
    /**
     * Runs [cases] one after another and returns the report. [meta] is copied verbatim (model id, model
     * SHA-256, context tokens, app version, device) – the runner adds nothing it did not observe.
     * Stops early on cancellation or when Android reports critical heat, and says so in the report.
     */
    fun run(setName: String, cases: List<ModelEvalCase>, meta: JSONObject): JSONObject {
        val results = JSONArray()
        val totals = ArrayList<Long>()
        val firsts = ArrayList<Long>()
        val charsPerSecond = ArrayList<Double>()
        val tokensPerSecond = ArrayList<Double>()
        var inputTokensMax: Int? = null
        val samples = ArrayList<ModelEvalDeviceSample>().apply { add(sample()) }
        var stopReason: String? = null
        var outputLimited = 0
        val counts = linkedMapOf<String, IntArray>() // category → pass, fail, notScored, heuristicPass, heuristicFail
        for ((index, case) in cases.withIndex()) {
            if (cancelled()) { stopReason = "CANCELLED"; break }
            val before = sample()
            if ((before.thermalStatus ?: 0) >= THERMAL_CRITICAL) { stopReason = "THERMAL_CRITICAL"; break }
            onProgress(index, cases.size, case.id)
            val prompt = ModelEvalScoring.prompt(case)
            val row = JSONObject().put("id", case.id).put("category", case.category).put("criterion", case.criterion)
            val bucket = counts.getOrPut(case.category) { IntArray(5) }
            if (prompt == null) {
                row.put("verdict", ModelEvalVerdict.NOT_SCORED.name).put("reason", "Prompt passt nicht in das Budget")
                bucket[2]++
                results.put(row)
                continue
            }
            val generation = generate(prompt)
            // Stopped while this case was running: its partial result is not a measurement of the model.
            if (cancelled()) { stopReason = "CANCELLED"; break }
            val after = sample()
            samples += after
            val score = if (generation.failure != null && generation.text.isBlank())
                ModelEvalScore(ModelEvalVerdict.FAIL, false, "Generierung fehlgeschlagen: " + generation.failure.take(120))
                else ModelEvalScoring.score(case, generation.text)
            when (score.verdict) {
                ModelEvalVerdict.PASS -> if (score.heuristic) bucket[3]++ else bucket[0]++
                ModelEvalVerdict.FAIL -> if (score.heuristic) bucket[4]++ else bucket[1]++
                ModelEvalVerdict.NOT_SCORED -> bucket[2]++
            }
            if (generation.outputLimit) outputLimited++
            if (generation.failure == null) {
                totals += generation.totalMs
                generation.firstTextMs?.let { firsts += it }
                val writing = generation.totalMs - (generation.firstTextMs ?: 0L)
                if (writing > 0 && generation.text.isNotEmpty())
                    charsPerSecond += generation.text.length * 1_000.0 / writing
                val produced = generation.outputTokens
                if (writing > 0 && produced != null && produced > 0) tokensPerSecond += produced * 1_000.0 / writing
            }
            generation.inputTokens?.let { tokens -> inputTokensMax = maxOf(inputTokensMax ?: 0, tokens) }
            results.put(row.put("verdict", score.verdict.name).put("heuristic", score.heuristic)
                .put("reason", score.reason).put("answer", generation.text.take(MAX_ANSWER_CHARACTERS))
                .put("completed", generation.completed).put("outputLimit", generation.outputLimit)
                .put("totalMs", generation.totalMs).put("firstTextMs", generation.firstTextMs ?: JSONObject.NULL)
                .put("textChunks", generation.textChunks)
                .put("inputTokens", generation.inputTokens ?: JSONObject.NULL)
                .put("outputTokens", generation.outputTokens ?: JSONObject.NULL)
                .put("thermalBefore", before.thermalStatus ?: JSONObject.NULL)
                .put("thermalAfter", after.thermalStatus ?: JSONObject.NULL))
        }
        onProgress(results.length(), cases.size, "")
        val categories = JSONObject()
        var pass = 0; var fail = 0; var notScored = 0; var heuristicPass = 0; var heuristicFail = 0
        counts.forEach { (category, c) ->
            categories.put(category, JSONObject().put("pass", c[0]).put("fail", c[1]).put("notScored", c[2])
                .put("heuristicPass", c[3]).put("heuristicFail", c[4]))
            pass += c[0]; fail += c[1]; notScored += c[2]; heuristicPass += c[3]; heuristicFail += c[4]
        }
        val thermal = samples.mapNotNull { it.thermalStatus }
        return JSONObject()
            .put("schema", "continuum-model-eval-report-v1")
            .put("scope", "RAW_MODEL_DIALOGUE_IN_PROMPT_NO_TOOLS_NO_MEMORY")
            .put("set", setName).put("meta", meta)
            .put("casesTotal", cases.size).put("casesRun", results.length())
            .put("stoppedEarly", stopReason ?: JSONObject.NULL)
            .put("formal", JSONObject().put("pass", pass).put("fail", fail))
            .put("heuristic", JSONObject().put("pass", heuristicPass).put("fail", heuristicFail)
                .put("note", "Enthaltung und Rückfrage werden am Wortlaut erkannt; bitte stichprobenartig gegenlesen."))
            .put("notScored", notScored).put("outputLimitReached", outputLimited)
            .put("latencyMs", JSONObject().put("totalP50", percentile(totals, 50) ?: JSONObject.NULL)
                .put("totalP95", percentile(totals, 95) ?: JSONObject.NULL)
                .put("firstTextP50", percentile(firsts, 50) ?: JSONObject.NULL)
                .put("firstTextP95", percentile(firsts, 95) ?: JSONObject.NULL))
            .put("charactersPerSecondMean", if (charsPerSecond.isEmpty()) JSONObject.NULL
                else Math.round(charsPerSecond.average() * 10.0) / 10.0)
            .put("tokensPerSecondMean", if (tokensPerSecond.isEmpty()) JSONObject.NULL
                else Math.round(tokensPerSecond.average() * 10.0) / 10.0)
            .put("tokensPerSecondNote", if (tokensPerSecond.isEmpty())
                "Die Laufzeit hat keine Tokenzahlen gemeldet; angegeben sind nur Zeichen pro Sekunde."
                else "Aus den von der Laufzeit gemeldeten Ausgabe-Tokens und der Zeit nach dem ersten Text.")
            .put("inputTokensMax", inputTokensMax ?: JSONObject.NULL)
            .put("thermalStatus", JSONObject().put("min", thermal.minOrNull() ?: JSONObject.NULL)
                .put("max", thermal.maxOrNull() ?: JSONObject.NULL))
            .put("batteryPercent", JSONObject().put("start", samples.first().batteryPercent ?: JSONObject.NULL)
                .put("end", samples.last().batteryPercent ?: JSONObject.NULL))
            .put("availableRamBytesMin", samples.mapNotNull { it.availableRamBytes }.minOrNull() ?: JSONObject.NULL)
            .put("jvmHeapUsedBytesMax", samples.maxOf { it.jvmHeapUsedBytes })
            .put("categories", categories).put("results", results)
    }

    companion object {
        const val THERMAL_CRITICAL = 4
        const val MAX_ANSWER_CHARACTERS = 1_200

        /** Nearest-rank percentile; null for an empty list. */
        fun percentile(values: List<Long>, percent: Int): Long? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val rank = Math.ceil(percent / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }

        /** Owner-facing summary of a report, numbers only as contained in it. */
        fun summary(report: JSONObject): String {
            val formal = report.getJSONObject("formal")
            val heuristic = report.getJSONObject("heuristic")
            val latency = report.getJSONObject("latencyMs")
            fun value(json: JSONObject, key: String) = if (json.isNull(key)) "–" else json.get(key).toString()
            return "MODELL-EVALUATION · " + report.getString("set") + "\n" +
                "Gelaufen: ${report.getInt("casesRun")} von ${report.getInt("casesTotal")}" +
                (if (report.isNull("stoppedEarly")) "" else " (vorzeitig beendet: ${report.getString("stoppedEarly")})") + "\n" +
                "Formal bewertet: ${formal.getInt("pass")} bestanden, ${formal.getInt("fail")} nicht bestanden\n" +
                "Am Wortlaut bewertet (Enthaltung/Rückfrage): ${heuristic.getInt("pass")} bestanden, ${heuristic.getInt("fail")} nicht\n" +
                "Nicht bewertbar (Werkzeugwahl): ${report.getInt("notScored")}\n" +
                "Antwortzeit gesamt: Median ${value(latency, "totalP50")} ms, 95 % ${value(latency, "totalP95")} ms\n" +
                "Bis zum ersten Text: Median ${value(latency, "firstTextP50")} ms\n" +
                "Tokens pro Sekunde (Mittel): ${value(report, "tokensPerSecondMean")}\n" +
                "Am Ausgabelimit abgeschnitten: ${report.getInt("outputLimitReached")}\n" +
                "Thermalstatus: ${value(report.getJSONObject("thermalStatus"), "min")} bis ${value(report.getJSONObject("thermalStatus"), "max")}\n" +
                "Umfang: Rohmodell mit Gespräch im Prompt, ohne Continuum-Gedächtnis und Werkzeuge."
        }
    }
}
