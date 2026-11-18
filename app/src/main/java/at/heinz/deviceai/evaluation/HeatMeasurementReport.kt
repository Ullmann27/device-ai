package at.heinz.deviceai.evaluation

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Report of the „Wärme- und Zeitmessung“: the summary computed from the raw runs, the readable German text and the
 * short chat message. Everything is derived from the report's own JSON, so a report that was written, read back or
 * cut short by STOPP gives the same statements. Where a value was not measured the text shows „–“; nothing is
 * filled in. tools/heat-report recomputes the same summary on a host from the same definitions.
 *
 * Definitions:
 *  - median: middle value, mean of the two middle values for an even count;
 *  - Abbruch: a turn that ended as STOPPED_BY_THERMAL_GUARD, PAUSED_BY_THERMAL_GUARD, TIMEOUT, FAILED, STALLED,
 *    OUTPUT_LIMIT or CANCELLED_BY_APP (a STOPP by the owner and a turn without model call are counted apart);
 *  - per run: highest thermal status and headroom of its turns, battery temperature rise = highest battery
 *    temperature of its turns minus the run's start temperature, total time = sum of its turns when all are complete;
 *  - a configuration is comparable with the baseline only when [comparison] found no reason against it.
 */
object HeatReport {
    const val COST_RULE = "Regel: Jede Konfiguration, die sich in genau einem Faktor von der Basis A unterscheidet und " +
        "mit ihr vergleichbar ist, wird über die Mediane je Lauf mit A verglichen. Die Kennzahlen werden in dieser " +
        "Reihenfolge geprüft: 1. Abbrüche (Anzahl), 2. höchste Thermalstufe, 3. höchster Thermal-Headroom, " +
        "4. Anstieg der Akkutemperatur, 5. Gesamtzeit je Lauf. Kostentreiber ist der Faktor mit der betragsmäßig " +
        "größten Änderung in der ersten Kennzahl, in der überhaupt eine Konfiguration die Mindeständerung erreicht " +
        "(1 Abbruch, 1 Thermalstufe, 0,05 Headroom, 0,5 °C, 10 % Zeit). Erreicht keine Konfiguration eine " +
        "Mindeständerung, wird kein Kostentreiber genannt."

    private val METRICS: List<Triple<String, Double, String>> = listOf(
        Triple("aborts", 1.0, "Abbrüche"),
        Triple("thermalMaxMedian", 1.0, "höchste Thermalstufe (Median je Lauf)"),
        Triple("headroomMaxMedian", 0.05, "höchster Thermal-Headroom (Median je Lauf)"),
        Triple("batteryRiseCMedian", 0.5, "Anstieg der Akkutemperatur in °C (Median je Lauf)"),
        Triple("runTotalMsMedianPercent", 10.0, "Gesamtzeit je Lauf in % (Median)"))

    private val ROLES = listOf(HeatMeasurementProtocol.ROLE_FIRST, HeatMeasurementProtocol.ROLE_FOLLOW_UP)

    /** Parses a stored report; throws when it is not a report of this schema. */
    fun read(text: String): JSONObject = JSONObject(text).also {
        require(it.optString("schema") == HeatMeasurement.SCHEMA) { "Kein Bericht des Schemas ${HeatMeasurement.SCHEMA}." }
        require(it.optJSONArray("runs") != null && it.optJSONArray("configurations") != null) { "Bericht ohne Läufe oder Konfigurationen." }
    }

    fun withSummary(report: JSONObject): JSONObject = report.put("summary", summarize(report))

    fun fileStem(report: JSONObject): String = "continuum-waermemessung-" + report.optString("size", "").lowercase(Locale.ROOT) +
        "-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(report.optLong("startedAtEpochMs")))

    // ── summary ──────────────────────────────────────────────────────────────────────────────────────────────

    fun summarize(report: JSONObject): JSONObject {
        val configurations = objects(report.optJSONArray("configurations"))
        val runs = objects(report.optJSONArray("runs"))
        val rows = JSONArray()
        val byId = LinkedHashMap<String, JSONObject>()
        for (configuration in configurations) {
            val id = configuration.getString("id")
            val row = row(id, runs.filter { it.optString("configuration") == id })
            byId[id] = row
            rows.put(row)
        }
        val baselineId = configurations.firstOrNull { it.isNull("factor") }?.optString("id")
        val baseline = baselineId?.let(byId::get)
        val comparisons = JSONArray()
        for (configuration in configurations) {
            val id = configuration.getString("id")
            if (id == baselineId) continue
            comparisons.put(comparison(configuration, byId.getValue(id), baseline, runs))
        }
        return JSONObject().put("rule", COST_RULE).put("baseline", baselineId ?: JSONObject.NULL)
            .put("rows", rows).put("comparisons", comparisons).put("costDriver", costDriver(comparisons, byId, baselineId))
    }

    private fun row(id: String, runs: List<JSONObject>): JSONObject {
        val turns = runs.flatMap { run -> objects(run.optJSONArray("turns")) }.filter { it.optString("outcome") != HeatMeasurement.NOT_RUN }
        val kinds = JSONObject()
        turns.map { it.optString("outcome") }.filter { it in HeatTurnRecorder.ABORTS }.sorted()
            .forEach { kinds.put(it, kinds.optInt(it) + 1) }
        val conditions = runs.mapNotNull { it.optJSONObject("startCondition") }
        val runThermal = runs.mapNotNull { run -> turnValues(run, "thermal", "max").maxOrNull() }
        val runHeadroom = runs.mapNotNull { run -> turnValues(run, "headroom", "max").maxOrNull() }
        val runRise = runs.mapNotNull { run ->
            val start = number(run.optJSONObject("startCondition"), "batteryTempC")
            val max = turnValues(run, "batteryTempC", "max").maxOrNull()
            if (start != null && max != null) max - start else null
        }
        val runTotal = runs.mapNotNull { run ->
            val all = objects(run.optJSONArray("turns"))
            if (all.isNotEmpty() && all.all { it.optString("outcome") == HeatTurnRecorder.COMPLETE && !it.isNull("totalMs") })
                all.sumOf { it.getDouble("totalMs") } else null
        }
        val roles = JSONObject()
        for (role in ROLES) roles.put(role, roleRow(turns.filter { it.optString("role") == role }))
        return JSONObject().put("configuration", id).put("runs", runs.size)
            .put("startConditionMissed", conditions.count { !it.optBoolean("reached") } + (runs.size - conditions.size))
            .put("turns", turns.size)
            .put("complete", turns.count { it.optString("outcome") == HeatTurnRecorder.COMPLETE })
            .put("aborts", turns.count { it.optString("outcome") in HeatTurnRecorder.ABORTS })
            .put("abortKinds", kinds)
            .put("cancelledByOwner", turns.count { it.optString("outcome") == HeatTurnRecorder.CANCELLED_BY_OWNER })
            .put("withoutModelCall", turns.count {
                it.optString("outcome") == HeatTurnRecorder.NO_MODEL_CALL || it.optString("outcome") == HeatTurnRecorder.NOT_ACCEPTED })
            .put("thermalMax", runThermal.maxOrNull() ?: JSONObject.NULL)
            .put("thermalMaxMedian", rounded(HeatStatistics.median(runThermal)))
            .put("headroomMaxMedian", rounded(HeatStatistics.median(runHeadroom)))
            .put("batteryRiseCMedian", rounded(HeatStatistics.median(runRise)))
            .put("runTotalMsMedian", rounded(HeatStatistics.median(runTotal)))
            .put("chargingStates", JSONArray(conditions.map { state(it, "charging") }.distinct().sorted()))
            .put("powerSaveStates", JSONArray(conditions.map { state(it, "powerSave") }.distinct().sorted()))
            .put("roles", roles)
    }

    private fun roleRow(turns: List<JSONObject>): JSONObject {
        val complete = turns.filter { it.optString("outcome") == HeatTurnRecorder.COMPLETE }
        fun median(key: String) = rounded(HeatStatistics.median(complete.mapNotNull { number(it, key) }))
        fun frames(block: String, key: String) = rounded(HeatStatistics.median(complete.mapNotNull { turn ->
            turn.optJSONObject(block)?.takeIf { it.optString("status") == HeatFrameAccumulator.MEASURED }?.let { number(it, key) }
        }))
        return JSONObject().put("turns", turns.size).put("complete", complete.size)
            .put("firstTokenMsMedian", median("firstTokenMs"))
            .put("firstVisibleTextMsMedian", median("firstVisibleTextMs"))
            .put("totalMsMedian", median("totalMs"))
            .put("charsPerSecondMedian", median("charsPerSecond"))
            .put("promptCharactersMedian", median("promptCharacters"))
            .put("firstVisibleTextEvents", JSONArray(complete.mapNotNull { text(it, "firstVisibleTextEvent") }.distinct().sorted()))
            .put("firstVisibleTextHidden", complete.count { !it.isNull("firstVisibleTextShown") && !it.optBoolean("firstVisibleTextShown") })
            .put("dialogueReusedTurns", complete.count { !it.isNull("dialogueReused") && it.optBoolean("dialogueReused") })
            .put("toolFreeTurns", complete.count { !it.isNull("toolFreeRoute") && it.optBoolean("toolFreeRoute") })
            .put("windowFramesMeasured", complete.count { it.optJSONObject("windowFrames")?.optString("status") == HeatFrameAccumulator.MEASURED })
            .put("windowP50MsMedian", frames("windowFrames", "p50Ms"))
            .put("windowP90MsMedian", frames("windowFrames", "p90Ms"))
            .put("windowP99MsMedian", frames("windowFrames", "p99Ms"))
            .put("windowOver2VsyncShareMedian", frames("windowFrames", "over2VsyncShare"))
            .put("windowFramesPerSecondMedian", frames("windowFrames", "framesPerSecond"))
            .put("stageFramesMeasured", complete.count { it.optJSONObject("stageFrames")?.optString("status") == HeatFrameAccumulator.MEASURED })
            .put("stageFramesPerSecondMedian", frames("stageFrames", "deliveredPerSecond"))
            .put("stageIntervalP50MsMedian", frames("stageFrames", "intervalP50Ms"))
            .put("stageIntervalP90MsMedian", frames("stageFrames", "intervalP90Ms"))
            .put("stageIntervalP99MsMedian", frames("stageFrames", "intervalP99Ms"))
    }

    /** One configuration against the baseline: the reasons against a comparison and the differences (X minus A). */
    private fun comparison(configuration: JSONObject, row: JSONObject, baseline: JSONObject?, runs: List<JSONObject>): JSONObject {
        val id = configuration.getString("id")
        val factor = text(configuration, "factor")
        val reasons = ArrayList<String>()
        if (configuration.optString("status") != HeatMeasurementProtocol.RUN || row.optInt("runs") == 0) reasons += "NOT_RUN"
        if (baseline == null || baseline.optInt("runs") == 0) reasons += "BASELINE_MISSING"
        if (reasons.isEmpty() && baseline != null) {
            if (row.optInt("startConditionMissed") > 0 || baseline.optInt("startConditionMissed") > 0) reasons += "START_CONDITION"
            if (row.optInt("runs") != baseline.optInt("runs")) reasons += "RUNS_DIFFER"
            if (row.optInt("withoutModelCall") > 0 || baseline.optInt("withoutModelCall") > 0) reasons += "TURN_WITHOUT_MODEL"
            if (row.optInt("cancelledByOwner") > 0 || baseline.optInt("cancelledByOwner") > 0) reasons += "STOPPED_BY_OWNER"
            if (row.getJSONArray("chargingStates").toString() != baseline.getJSONArray("chargingStates").toString() ||
                row.getJSONArray("powerSaveStates").toString() != baseline.getJSONArray("powerSaveStates").toString())
                reasons += "CONDITIONS_CHANGED"
            val own = number(row.getJSONObject("roles").getJSONObject(ROLES[0]), "promptCharactersMedian")
            val base = number(baseline.getJSONObject("roles").getJSONObject(ROLES[0]), "promptCharactersMedian")
            if (own != null && base != null && base > 0 && Math.abs(own - base) / base > 0.10) reasons += "PROMPT_SIZE_DIFFERS"
            if (!factorEffective(factor, runs.filter { it.optString("configuration") == id })) reasons += "FACTOR_NOT_EFFECTIVE"
        }
        val delta = JSONObject()
        if (baseline != null && "NOT_RUN" !in reasons && "BASELINE_MISSING" !in reasons) {
            delta.put("aborts", row.optInt("aborts") - baseline.optInt("aborts"))
            for (key in listOf("thermalMaxMedian", "headroomMaxMedian", "batteryRiseCMedian"))
                delta.put(key, rounded(difference(number(row, key), number(baseline, key))))
            delta.put("runTotalMsMedianPercent", rounded(percent(number(row, "runTotalMsMedian"), number(baseline, "runTotalMsMedian"))))
            for (role in ROLES) {
                val own = row.getJSONObject("roles").getJSONObject(role)
                val base = baseline.getJSONObject("roles").getJSONObject(role)
                val part = JSONObject()
                for (key in listOf("firstTokenMsMedian", "firstVisibleTextMsMedian", "totalMsMedian"))
                    part.put(key + "Percent", rounded(percent(number(own, key), number(base, key))))
                delta.put(role, part)
            }
        }
        return JSONObject().put("configuration", id).put("factor", factor ?: JSONObject.NULL)
            .put("comparable", reasons.isEmpty()).put("reasons", JSONArray(reasons)).put("delta", delta)
    }

    /**
     * Whether the changed factor really took effect in the runs: „Gespräch behalten“ needs at least one follow-up
     * the runtime really continued, „Bühne aus“ must not have delivered stage frames. Unknown counts as effective.
     */
    private fun factorEffective(factor: String?, runs: List<JSONObject>): Boolean {
        val turns = runs.flatMap { objects(it.optJSONArray("turns")) }.filter { it.optString("outcome") == HeatTurnRecorder.COMPLETE }
        return when (factor) {
            HeatMeasurement.FACTOR_DIALOGUE_REUSE -> turns.any {
                it.optString("role") == HeatMeasurementProtocol.ROLE_FOLLOW_UP && !it.isNull("dialogueReused") && it.optBoolean("dialogueReused") }
            HeatMeasurement.FACTOR_STAGE_OFF -> turns.none { turn ->
                turn.optJSONObject("stageFrames")?.let { it.optString("status") == HeatFrameAccumulator.MEASURED && it.optInt("delivered") > 0 } == true }
            else -> true
        }
    }

    private fun costDriver(comparisons: JSONArray, rows: Map<String, JSONObject>, baselineId: String?): JSONObject {
        val candidates = objects(comparisons).filter { it.optBoolean("comparable") }
        if (candidates.isEmpty()) return JSONObject().put("status", "NOT_COMPARABLE")
        val single = candidates.any { rows[it.getString("configuration")]?.optInt("runs") == 1 } || rows[baselineId]?.optInt("runs") == 1
        for ((metric, threshold, label) in METRICS) {
            val measured = candidates.mapNotNull { candidate -> number(candidate.getJSONObject("delta"), metric)?.let { candidate to it } }
            val best = measured.maxByOrNull { Math.abs(it.second) } ?: continue
            if (Math.abs(best.second) < threshold) continue
            return JSONObject().put("status", "FOUND").put("configuration", best.first.getString("configuration"))
                .put("factor", best.first.opt("factor") ?: JSONObject.NULL).put("metric", metric).put("metricLabel", label)
                .put("delta", best.second).put("singleRuns", single)
                .put("tied", JSONArray(measured.filter { Math.abs(it.second) == Math.abs(best.second) }
                    .map { it.first.getString("configuration") }))
        }
        return JSONObject().put("status", "NONE").put("singleRuns", single)
    }

    // ── readable text ────────────────────────────────────────────────────────────────────────────────────────

    /** The readable German report. [report] must carry its summary ([withSummary]). */
    fun markdown(report: JSONObject): String = buildString {
        val summary = report.getJSONObject("summary")
        val device = report.optJSONObject("device") ?: JSONObject()
        val rows = objects(summary.getJSONArray("rows")).associateBy { it.getString("configuration") }
        val configurations = objects(report.getJSONArray("configurations"))
        val size = if (report.optString("size") == HeatSize.VOLL.name) HeatSize.VOLL.label else HeatSize.KURZ.label
        append("# Continuum · Wärme- und Zeitmessung (").append(size).append(")\n\n")
        append("- Gestartet: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(report.optLong("startedAtEpochMs"))))
            .append(" (Gerätezeit)")
        if (report.has("endedAtEpochMs")) append(" · Dauer ").append(Math.round((report.optLong("endedAtEpochMs") -
            report.optLong("startedAtEpochMs")) / 60_000.0)).append(" min")
        append("\n- Gerät: ").append(shown(device, "manufacturer")).append(' ').append(shown(device, "model"))
            .append(" · Android ").append(shown(device, "androidRelease")).append(" (SDK ").append(shown(device, "androidSdk"))
            .append(") · App ").append(shown(device, "appVersion")).append(" (Code ").append(shown(device, "appVersionCode")).append(")\n")
        val model = configurations.firstOrNull { it.isNull("factor") }?.optJSONObject("model") ?: device.optJSONObject("loadedModel")
        append("- Modell der Basis: ").append(shown(model, "id")).append(" · Datei ").append(shown(model, "fileName"))
            .append(" · ").append(shown(model, "bytes")).append(" Bytes · Backend ").append(shown(model, "backend"))
            .append(" · CPU-Threads ").append(shown(model, "threads")).append('\n')
        val input = report.optJSONObject("input") ?: JSONObject()
        append("- ").append(input.optString("note", HeatMeasurement.INPUT_NOTE)).append(" · Auftragssatz ")
            .append(shown(input, "promptSet")).append(" (Version ").append(shown(input, "promptSetVersion")).append(")\n")
        append("- Sprachausgabe: ").append(onOff(device, "speechOutput")).append(" · Bühne (Command03): ").append(onOff(device, "htmlStage"))
            .append(" · Leistungsprofil der Bühne: ").append(shown(device, "stageProfile"))
            .append(" · Schalter „Gespräch im Modell behalten“ vor der Messung: ").append(onOff(device, "dialogueReuseSwitch")).append('\n')
        append("- Helligkeit: Modus ").append(shown(device, "brightnessMode")).append(", Wert ").append(shown(device, "brightness"))
            .append(" · Bildwiederholrate ").append(shown(device, "refreshRateHz")).append(" Hz · Anzeige ")
            .append(shown(device, "displayWidthPx")).append("×").append(shown(device, "displayHeightPx"))
            .append(" px · Faltzustand: ").append(shown(device, "foldState")).append('\n')
        val start = report.optJSONObject("baselineStart")
        append("- Startbedingung (erster Start): Thermalstufe ").append(shown(start, "thermalStatus")).append(" · Akku ")
            .append(shown(start, "batteryTempC")).append(" °C · Headroom ").append(shown(start, "headroom"))
            .append(" · Akkustand ").append(shown(start, "batteryPercent")).append(" % · lädt: ").append(yesNo(start, "charging"))
            .append(" · Energiesparen: ").append(yesNo(start, "powerSave")).append('\n')
        val stop = report.optJSONObject("stop")
        append("- Status: ").append(if (report.optBoolean("complete")) "vollständig" else "unvollständig")
        if (stop != null) append(" · vorzeitig beendet: ").append(stop.optString("reason"))
            .append(text(stop, "message")?.let { " – $it" } ?: "")
        append("\n- Wiederherstellung nach der Messung: ").append(when {
            !report.has("restoreComplete") -> "nicht aufgezeichnet (Messung unterbrochen)"
            report.optBoolean("restoreComplete") -> "vollständig"
            else -> "NICHT vollständig – siehe Abschnitt „Wiederherstellung“"
        }).append("\n\n")

        append("## Wärme und Abbrüche je Konfiguration\n\n")
        append("| Konfiguration | Läufe | Start nicht erreicht | Antworten vollständig | Abbrüche | Thermalstufe max | ")
            .append("Thermalstufe (Median je Lauf) | Headroom max (Median) | Akku-Anstieg °C (Median) | Gesamtzeit je Lauf ms (Median) |\n")
        append("|---|---|---|---|---|---|---|---|---|---|\n")
        for (configuration in configurations) {
            val row = rows.getValue(configuration.getString("id"))
            append("| ").append(name(configuration)).append(" | ")
            if (row.optInt("runs") == 0) { append(HeatMeasurement.NOT_RUN).append(" | – | – | – | – | – | – | – | – |\n"); continue }
            append(row.optInt("runs")).append(" | ").append(row.optInt("startConditionMissed")).append(" | ")
                .append(row.optInt("complete")).append(" von ").append(row.optInt("turns")).append(" | ").append(aborts(row)).append(" | ")
                .append(shown(row, "thermalMax")).append(" | ").append(shown(row, "thermalMaxMedian")).append(" | ")
                .append(shown(row, "headroomMaxMedian")).append(" | ").append(shown(row, "batteryRiseCMedian")).append(" | ")
                .append(shown(row, "runTotalMsMedian")).append(" |\n")
        }
        for (role in ROLES) {
            append("\n## Zeiten und Bildzeit · ").append(if (role == ROLES[0]) "Erstfrage" else "Folgefrage").append("\n\n")
            append("| Konfiguration | Antworten vollständig | Erstes Token ms | Erster sichtbarer Text ms | Gesamtzeit ms | Zeichen/s | ")
                .append("Fensterbild ms p50 / p90 / p99 | Bilder > 2 Vsync | Fensterbilder/s | Bühnenbilder/s | Bühnenabstand ms p50 / p90 / p99 |\n")
            append("|---|---|---|---|---|---|---|---|---|---|---|\n")
            for (configuration in configurations) {
                val part = rows.getValue(configuration.getString("id")).getJSONObject("roles").getJSONObject(role)
                append("| ").append(configuration.getString("id")).append(" | ")
                if (part.optInt("turns") == 0) { append(HeatMeasurement.NOT_RUN).append(" | – | – | – | – | – | – | – | – | – |\n"); continue }
                append(part.optInt("complete")).append(" von ").append(part.optInt("turns")).append(" | ")
                    .append(shown(part, "firstTokenMsMedian")).append(" | ").append(shown(part, "firstVisibleTextMsMedian")).append(" | ")
                    .append(shown(part, "totalMsMedian")).append(" | ").append(shown(part, "charsPerSecondMedian")).append(" | ")
                    .append(shown(part, "windowP50MsMedian")).append(" / ").append(shown(part, "windowP90MsMedian")).append(" / ")
                    .append(shown(part, "windowP99MsMedian")).append(" | ").append(share(part, "windowOver2VsyncShareMedian")).append(" | ")
                    .append(shown(part, "windowFramesPerSecondMedian")).append(" | ").append(shown(part, "stageFramesPerSecondMedian")).append(" | ")
                    .append(shown(part, "stageIntervalP50MsMedian")).append(" / ").append(shown(part, "stageIntervalP90MsMedian")).append(" / ")
                    .append(shown(part, "stageIntervalP99MsMedian")).append(" |\n")
            }
        }
        append("\nMediane über die vollständig beantworteten Fragen. Bei einem Lauf je Konfiguration ist jeder Median ein Einzelwert.\n")
        append("Als reine Textfrage ohne Werkzeugschemata an das Modell gegeben (vollständige Antworten): ")
            .append(configurations.filter { rows.getValue(it.getString("id")).optInt("runs") > 0 }.joinToString(" · ") { configuration ->
                val roles = rows.getValue(configuration.getString("id")).getJSONObject("roles")
                configuration.getString("id") + " " + ROLES.sumOf { roles.getJSONObject(it).optInt("toolFreeTurns") } + " von " +
                    ROLES.sumOf { roles.getJSONObject(it).optInt("complete") }
            }).append(".\n")

        val baselineId = text(summary, "baseline") ?: "A"
        append("\n## Vergleich mit der Basis ").append(baselineId).append(" (jeweils ein Faktor)\n\n")
        append("| Konfiguration | Vergleichbar | Abbrüche Δ | Thermalstufe Δ | Headroom Δ | Akku-Anstieg Δ °C | Gesamtzeit je Lauf Δ % | ")
            .append("Erstfrage: erster sichtbarer Text Δ % | Hinweis |\n|---|---|---|---|---|---|---|---|---|\n")
        for (entry in objects(summary.getJSONArray("comparisons"))) {
            val configuration = configurations.first { it.getString("id") == entry.getString("configuration") }
            val delta = entry.getJSONObject("delta")
            val reasons = objects0(entry.getJSONArray("reasons"))
            append("| ").append(name(configuration)).append(" | ").append(if (entry.optBoolean("comparable")) "ja" else "nicht vergleichbar")
                .append(" | ").append(signed(delta, "aborts")).append(" | ").append(signed(delta, "thermalMaxMedian")).append(" | ")
                .append(signed(delta, "headroomMaxMedian")).append(" | ").append(signed(delta, "batteryRiseCMedian")).append(" | ")
                .append(signed(delta, "runTotalMsMedianPercent")).append(" | ")
                .append(signed(delta.optJSONObject(ROLES[0]), "firstVisibleTextMsMedianPercent")).append(" | ")
                .append(reasons.joinToString("; ") { reasonText(it, configuration) }.ifEmpty { "–" }).append(" |\n")
        }
        append("\nΔ = Konfiguration minus Basis. Ein Minus heißt: in dieser Konfiguration war der Wert kleiner als in der Basis.\n")

        append("\n## Größter gemessener Kostentreiber\n\n").append(COST_RULE).append("\n\n").append(costDriverText(report)).append('\n')

        val notRun = configurations.filter { it.optString("status") != HeatMeasurementProtocol.RUN }
        if (notRun.isNotEmpty()) {
            append("\n## Nicht gelaufen (NOT_RUN)\n\n")
            notRun.forEach { append("- ").append(name(it)).append(": ").append(text(it, "reason") ?: "ohne Angabe").append('\n') }
        }
        val runs = objects(report.getJSONArray("runs"))
        val flagged = runs.filter { it.optJSONObject("startCondition")?.optBoolean("reached") != true }
        if (flagged.isNotEmpty()) {
            append("\n## Läufe mit nicht erreichter Startbedingung\n\n")
            flagged.forEach { run -> append("- Lauf ").append(run.optInt("index")).append(" · ").append(run.optString("configuration"))
                .append(" · ").append(run.optString("prompt")).append(": ")
                .append(text(run.optJSONObject("startCondition"), "reason") ?: "ohne Angabe").append('\n') }
        }
        val interrupted = runs.flatMap { run -> objects(run.optJSONArray("turns")).map { run to it } }
            .filter { it.second.optString("outcome") != HeatTurnRecorder.COMPLETE && it.second.optString("outcome") != HeatMeasurement.NOT_RUN }
        if (interrupted.isNotEmpty()) {
            append("\n## Nicht vollständig beantwortete Fragen im Einzelnen\n\n")
            interrupted.forEach { (run, turn) -> append("- Lauf ").append(run.optInt("index")).append(" · ")
                .append(run.optString("configuration")).append(" · ").append(run.optString("prompt")).append(" · ")
                .append(if (turn.optString("role") == ROLES[0]) "Erstfrage" else "Folgefrage").append(": ")
                .append(outcomeText(turn.optString("outcome"))).append(" nach ").append(shown(turn, "totalMs")).append(" ms, ")
                .append(turn.optInt("generatedCharacters")).append(" Zeichen erzeugt, Thermalstufe max ")
                .append(shown(turn.optJSONObject("thermal"), "max")).append(text(turn, "message")?.let { " (" + it.take(120) + ")" } ?: "").append('\n') }
        }
        val lifecycle = configurations.flatMap { configuration -> objects(configuration.optJSONArray("modelLifecycle")).map { configuration to it } }
        if (lifecycle.isNotEmpty()) {
            append("\n## Modell laden und entladen (nicht in den Antwortzeiten enthalten)\n\n")
            lifecycle.forEach { (configuration, step) -> append("- ").append(configuration.getString("id")).append(" · ")
                .append(step.optString("action")).append(": ").append(step.optLong("ms")).append(" ms · ")
                .append(if (step.optBoolean("ok")) "in Ordnung" else "FEHLGESCHLAGEN – " + (text(step, "failure") ?: "ohne Angabe")).append('\n') }
        }
        val restore = objects(report.optJSONArray("restore"))
        append("\n## Wiederherstellung\n\n")
        if (!report.has("restoreComplete")) append("- Nicht aufgezeichnet: Die Messung wurde unterbrochen, bevor sie ihren Bericht " +
            "schreiben konnte. Ihre Änderungen an Bühne und Gesprächsschalter galten nur im laufenden Programm und sind mit ihm " +
            "beendet; ein für die Messung geladenes anderes Modell ist nach einem Neustart nicht mehr geladen.\n")
        else if (restore.isEmpty()) append("- Die Messung hat nichts verändert, das zurückzustellen war.\n")
        restore.forEach { append("- ").append(it.optString("what")).append(": ").append(if (it.optBoolean("ok")) "zurückgestellt"
            else "NICHT zurückgestellt – " + (text(it, "detail") ?: "ohne Angabe")).append('\n') }

        append("\n## Was die Zahlen bedeuten\n\n")
        append("- Thermalstufe: Androids eigene Stufe 0 bis 6 (0 normal, 1 leicht, 2 mäßig, 3 stark erhöht – ab hier hält der " +
            "Hitzeschutz der App das Modell an). Kein Temperaturwert.\n")
        append("- Headroom: Androids Abstand zur Stufe „stark erhöht“; 1,0 ist diese Schwelle. „–“: vom Gerät nicht gemeldet.\n")
        append("- Akkutemperatur: Temperatur des Akkus laut Android, nicht des Prozessors.\n")
        append("- Erstes Token: erstes Textstück des Modells nach der Übergabe des Auftrags.\n")
        append("- Erster sichtbarer Text: der Moment, in dem zum ersten Mal Antworttext an die sichtbare Oberfläche übergeben " +
            "wurde – entweder der Antwortentwurf in der nativen Ansicht (${HeatTurnRecorder.EVENT_NATIVE_PREVIEW}) oder die fertige " +
            "Antwort (${HeatTurnRecorder.EVENT_STAGE_MESSAGE} bzw. ${HeatTurnRecorder.EVENT_NATIVE_BUBBLE}). In dieser Messung: ")
            .append(objects(summary.getJSONArray("rows")).flatMap { row -> ROLES.flatMap { role ->
                objects0(row.getJSONObject("roles").getJSONObject(role).getJSONArray("firstVisibleTextEvents")) } }
                .distinct().sorted().joinToString(", ").ifEmpty { "kein Ereignis aufgezeichnet" }).append(".\n")
        append("- Gesamtzeit: Übergabe des Auftrags bis zur Freigabe durch die App. Modell laden und entladen zählt nicht dazu.\n")
        append("- Fensterbild: Gesamtdauer der Bilder des App-Fensters laut Android (FrameMetrics TOTAL_DURATION), in Klassen von " +
            "0,5 ms; angegeben ist die obere Klassengrenze. „Bilder > 2 Vsync“: Anteil der Bilder, die länger als zwei Bildperioden dauerten.\n")
        append("- Bühnenbilder: Bilder, die der Bildbegrenzer der Bühne wirklich an Szene, Kugel und Avatar weitergegeben hat, " +
            "und der Abstand zwischen ihnen.\n")
        append("- „–“ oder NOT_AVAILABLE: nicht gemessen. NOT_RUN: nicht gelaufen. Es steht nirgends ein Schätzwert.\n")
    }

    /** The statement about the largest measured cost driver, from the summary only. */
    fun costDriverText(report: JSONObject): String {
        val summary = report.getJSONObject("summary")
        val driver = summary.getJSONObject("costDriver")
        val configurations = objects(report.getJSONArray("configurations")).associateBy { it.getString("id") }
        return when (driver.optString("status")) {
            "FOUND" -> {
                val configuration = configurations[driver.getString("configuration")]
                val tied = objects0(driver.getJSONArray("tied"))
                "Ergebnis: " + (configuration?.let(::name) ?: driver.getString("configuration")) + " – " + driver.getString("metricLabel") +
                    " " + signedValue(driver.getDouble("delta")) + " gegenüber der Basis." +
                    (if (tied.size > 1) " Gleich große Änderung auch bei: " + tied.filter { it != driver.getString("configuration") }.joinToString(", ") + "." else "") +
                    (if (driver.optBoolean("singleRuns")) " Einzelmessung je Konfiguration: Die Streuung ist unbekannt; für eine belastbare " +
                        "Aussage die vollständige Messung wiederholen." else "")
            }
            "NONE" -> "Ergebnis: Keine vergleichbare Konfiguration erreicht eine Mindeständerung gegenüber der Basis; " +
                "aus dieser Messung lässt sich kein Kostentreiber benennen."
            else -> "Ergebnis: nicht vergleichbar – keine Konfiguration ist mit der Basis vergleichbar (siehe Hinweise im Vergleich). " +
                "Es wird kein Kostentreiber genannt."
        }
    }

    /** The short message for the chat; [savedFiles] are the real names of the written files (empty: not saved). */
    fun chatSummary(report: JSONObject, savedFiles: List<String>, location: String?): String = buildString {
        val summary = report.getJSONObject("summary")
        val rows = objects(summary.getJSONArray("rows"))
        val size = if (report.optString("size") == HeatSize.VOLL.name) HeatSize.VOLL.label else HeatSize.KURZ.label
        append("WÄRME- UND ZEITMESSUNG (").append(size).append(") · ")
        val stop = report.optJSONObject("stop")
        append(if (stop == null) "abgeschlossen" else "vorzeitig beendet (" + stop.optString("reason") + ")")
        append("\n").append(HeatMeasurement.INPUT_NOTE).append('.')
        for (row in rows) {
            append("\n").append(row.getString("configuration")).append(": ")
            if (row.optInt("runs") == 0) { append(HeatMeasurement.NOT_RUN); continue }
            val first = row.getJSONObject("roles").getJSONObject(ROLES[0])
            append(row.optInt("runs")).append(if (row.optInt("runs") == 1) " Lauf" else " Läufe").append(" · Abbrüche ").append(row.optInt("aborts"))
                .append(" · Thermalstufe max ").append(shown(row, "thermalMax")).append(" · Akku +").append(shown(row, "batteryRiseCMedian"))
                .append(" °C · Erstfrage gesamt ").append(shown(first, "totalMsMedian")).append(" ms, erster sichtbarer Text ")
                .append(shown(first, "firstVisibleTextMsMedian")).append(" ms")
        }
        append("\n\n").append(costDriverText(report))
        if (report.has("restoreComplete") && !report.optBoolean("restoreComplete"))
            append("\n\nACHTUNG: Nicht alles ließ sich zurückstellen – Einzelheiten im Bericht.")
        append("\n\n")
        if (savedFiles.isEmpty()) append("Der Bericht konnte nicht als Datei gespeichert werden.")
        else append("Bericht gespeichert (").append(location ?: "Download-Ordner").append("): ").append(savedFiles.joinToString(" und "))
            .append(". Bitte beide Dateien zurückschicken.")
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun name(configuration: JSONObject): String = configuration.getString("id") + " · " + configuration.optString("label")

    private fun aborts(row: JSONObject): String {
        val kinds = row.getJSONObject("abortKinds")
        val detail = kinds.keys().asSequence().toList().sorted().joinToString(", ") { kinds.getInt(it).toString() + "× " + outcomeText(it) }
        return row.optInt("aborts").toString() + if (detail.isEmpty()) "" else " ($detail)"
    }

    fun outcomeText(outcome: String): String = when (outcome) {
        HeatTurnRecorder.COMPLETE -> "vollständig"
        HeatTurnRecorder.STOPPED_BY_THERMAL_GUARD -> "vom Hitzeschutz angehalten"
        HeatTurnRecorder.PAUSED_BY_THERMAL_GUARD -> "wegen Hitze nicht gestartet"
        HeatTurnRecorder.TIMEOUT -> "Zeitlimit der Messung"
        HeatTurnRecorder.STALLED -> "Modell lieferte keinen Text mehr"
        HeatTurnRecorder.OUTPUT_LIMIT -> "Ausgabelimit erreicht"
        HeatTurnRecorder.CANCELLED_BY_OWNER -> "vom Besitzer gestoppt"
        HeatTurnRecorder.CANCELLED_BY_APP -> "von der App abgebrochen"
        HeatTurnRecorder.NO_MODEL_CALL -> "ohne Modellaufruf beantwortet"
        HeatTurnRecorder.NOT_ACCEPTED -> "Auftrag nicht angenommen"
        HeatTurnRecorder.FAILED -> "fehlgeschlagen"
        else -> outcome
    }

    private fun reasonText(reason: String, configuration: JSONObject): String = when (reason) {
        "NOT_RUN" -> "nicht gelaufen" + (text(configuration, "reason")?.let { ": $it" } ?: "")
        "BASELINE_MISSING" -> "die Basis ist nicht gelaufen"
        "START_CONDITION" -> "Startbedingung in mindestens einem Lauf nicht erreicht"
        "RUNS_DIFFER" -> "andere Anzahl Läufe als die Basis"
        "TURN_WITHOUT_MODEL" -> "eine Frage wurde ohne Modellaufruf beantwortet"
        "STOPPED_BY_OWNER" -> "ein Lauf wurde vom Besitzer gestoppt"
        "CONDITIONS_CHANGED" -> "Laden oder Energiesparen war anders als in der Basis"
        "PROMPT_SIZE_DIFFERS" -> "die Eingabe an das Modell war mehr als 10 % anders groß"
        "FACTOR_NOT_EFFECTIVE" -> "der geänderte Faktor hat in den Läufen nicht gewirkt"
        else -> reason
    }

    private fun objects(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull(array::optJSONObject)

    private fun objects0(array: JSONArray): List<String> = (0 until array.length()).map { array.get(it).toString() }

    private fun turnValues(run: JSONObject, block: String, key: String): List<Double> =
        objects(run.optJSONArray("turns")).mapNotNull { number(it.optJSONObject(block), key) }

    private fun number(json: JSONObject?, key: String): Double? =
        if (json == null || json.isNull(key)) null else json.optDouble(key).takeIf { it.isFinite() }

    private fun text(json: JSONObject?, key: String): String? =
        if (json == null || json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }

    private fun state(json: JSONObject, key: String): String = if (json.isNull(key)) "unknown" else json.optBoolean(key).toString()

    private fun difference(own: Double?, base: Double?): Double? = if (own == null || base == null) null else own - base

    private fun percent(own: Double?, base: Double?): Double? =
        if (own == null || base == null || base == 0.0) null else (own - base) / base * 100.0

    private fun rounded(value: Double?): Any = if (value == null) JSONObject.NULL else HeatHistogram.round(value)

    /** A number as the owner reads it: whole numbers without decimals, others with up to two, decimal comma. */
    internal fun plain(value: Double): String {
        val scaled = Math.round(value * 100.0) / 100.0
        return if (scaled == Math.rint(scaled)) String.format(Locale.ROOT, "%.0f", scaled)
            else scaled.toString().replace('.', ',')
    }

    private fun shown(json: JSONObject?, key: String): String {
        if (json == null || json.isNull(key)) return "–"
        val value = json.get(key)
        return if (value is Number) plain(value.toDouble()) else value.toString()
    }

    private fun signedValue(value: Double): String = (if (value > 0) "+" else if (value < 0) "−" else "±") + plain(Math.abs(value))

    private fun signed(json: JSONObject?, key: String): String = number(json, key)?.let(::signedValue) ?: "–"

    private fun share(json: JSONObject, key: String): String = number(json, key)?.let { plain(it * 100.0) + " %" } ?: "–"

    private fun onOff(json: JSONObject?, key: String): String = if (json == null || json.isNull(key)) "–" else if (json.optBoolean(key)) "an" else "aus"

    private fun yesNo(json: JSONObject?, key: String): String = if (json == null || json.isNull(key)) "–" else if (json.optBoolean(key)) "ja" else "nein"
}
