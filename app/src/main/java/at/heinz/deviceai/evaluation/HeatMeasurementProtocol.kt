package at.heinz.deviceai.evaluation

import org.json.JSONArray
import org.json.JSONObject

/** One reading of Android's sensors; null where Android reported nothing. Never an estimate. */
data class HeatSample(
    /** PowerManager thermal status 0..6. */
    val thermalStatus: Int?,
    /** PowerManager.getThermalHeadroom(0): 1.0 is the threshold of „stark erhöht“. */
    val headroom: Double?,
    val batteryTempC: Double?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val powerSave: Boolean?
) {
    fun toJson(atMs: Long, phase: String? = null): JSONObject = JSONObject().put("tMs", atMs)
        .put("thermalStatus", thermalStatus ?: JSONObject.NULL).put("headroom", headroom ?: JSONObject.NULL)
        .put("batteryTempC", batteryTempC ?: JSONObject.NULL).put("batteryPercent", batteryPercent ?: JSONObject.NULL)
        .apply { if (phase != null) put("phase", phase) }
}

/** Why a measurement ended before its plan was complete. */
data class HeatStop(val reason: String, val message: String? = null) {
    companion object {
        const val OWNER_STOP = "OWNER_STOP"
        const val LEFT_FOREGROUND = "LEFT_FOREGROUND"
        const val THERMAL_CRITICAL = "THERMAL_CRITICAL"
        const val PRECONDITION = "PRECONDITION"
        const val MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE"
        /** The app was closed or ended in the middle; the report was saved later from the runs kept so far. */
        const val INTERRUPTED = "INTERRUPTED"
        const val ERROR = "ERROR"
    }
}

/** One step of loading or unloading a model, timed on its own; never part of an answer time. */
data class HeatLifecycleStep(val action: String, val ms: Long, val ok: Boolean, val failure: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("action", action).put("ms", ms).put("ok", ok)
        .put("failure", failure?.take(200) ?: JSONObject.NULL)
}

data class HeatRestoreStep(val what: String, val ok: Boolean, val detail: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("what", what).put("ok", ok).put("detail", detail?.take(200) ?: JSONObject.NULL)
}

/** The frames and thermal changes of one turn, collected by the device between hand-over and release. */
data class HeatTurnObservation(
    val result: HeatTurnResult,
    /** Device clock of the hand-over. */
    val handoverAtMs: Long,
    /** (device clock, status) of every change Android's thermal listener reported. */
    val thermalChanges: List<Pair<Long, Int>>,
    val windowFrames: JSONObject,
    val stageFrames: JSONObject
)

/**
 * Everything the protocol needs from the phone. The app implements it with Android's sensors, its own turn path
 * and its stage; host tests implement it with a scripted device and a clock that only moves when the protocol
 * waits. Every method may be called from the protocol's worker thread.
 */
interface HeatMeasurementDevice {
    fun nowMs(): Long
    fun sleep(ms: Long)
    /** Null while the measurement may go on. */
    fun stopReason(): HeatStop?
    /** Null when the measurement can start; otherwise the owner-facing reason (no model, cloud brain, …). */
    fun blocker(): String?
    /** Device, app and setting facts for the report, copied verbatim. */
    fun facts(): JSONObject
    fun sample(): HeatSample
    /** The loaded model as the runtime reports it right now (id, file, size, backend, threads), or null. */
    fun model(): JSONObject?
    /** Null when [configuration] can run here; otherwise why not. It is then recorded as NOT_RUN. */
    fun unavailable(configuration: HeatConfiguration): String?
    fun applyStage(mode: HeatStageMode): Boolean
    fun applyDialogueReuse(on: Boolean): Boolean
    /** False: the stage page gets no draft of the running answer for the following turns (as in Code68). */
    fun applyStageDraft(on: Boolean): Boolean
    /** True: the following turns go to the model with all tool schemas, whatever their wording. */
    fun applyToolSchemas(on: Boolean): Boolean
    /** Loads the model [choice] stands for; the steps are timed. The last step tells whether it is ready. */
    fun swapModel(choice: HeatModelChoice): List<HeatLifecycleStep>
    /** A new empty chat, so that every run starts without dialogue history. */
    fun freshChat(): Boolean
    /** Hands [text] to the app's send path and starts observing the turn. */
    fun beginTurn(text: String)
    fun turnEnded(): Boolean
    /** Uses the app's own stop control on the running turn. */
    fun abortTurn(timeout: Boolean)
    /** Stops observing and returns what the turn did. */
    fun endTurn(): HeatTurnObservation
    /** A spoken answer is still being played. */
    fun speaking(): Boolean
    /** Puts back everything the measurement changed. Idempotent; must work after a stop and after a failure. */
    fun restore(): List<HeatRestoreStep>
    fun progress(line: String)
    /** The report as far as it is; called after every run so that an interrupted measurement keeps its data. */
    fun checkpoint(report: JSONObject) {}
}

/**
 * The measurement protocol: baseline start condition → for every planned run: apply ONE configuration, wait until
 * the start condition is back (or flag the run), open a new chat, hand over the fixed orders, sample sensors while
 * the app answers → restore. It decides nothing about the app; it only orders the steps and writes down what the
 * device reported. A configuration that is unavailable is recorded as NOT_RUN and is not replaced.
 */
class HeatMeasurementProtocol(private val device: HeatMeasurementDevice, private val plan: HeatPlan) {
    private val limits = plan.limits
    private val runs = JSONArray()
    private val status = LinkedHashMap<String, String>()
    private val reasons = HashMap<String, String>()
    private val models = HashMap<String, JSONObject>()
    private val lifecycle = HashMap<String, JSONArray>()
    private var stop: HeatStop? = null
    private var baseline: HeatSample? = null
    private var currentModel = HeatModelChoice.BASELINE
    private var report = JSONObject()

    fun run(startedAtEpochMs: Long, endedAtEpochMs: () -> Long): JSONObject {
        var restore: List<HeatRestoreStep> = emptyList()
        try {
            report = header(startedAtEpochMs)
            plan.configurations.filter { it.id in plan.leftOut }.forEach { notRun(it, plan.leftOut.getValue(it.id)) }
            val blocker = device.blocker()
            if (blocker != null) stop = HeatStop(HeatStop.PRECONDITION, blocker)
            else {
                baseline = device.sample()
                execute()
            }
        } catch (failure: Throwable) {
            if (stop == null) stop = HeatStop(HeatStop.ERROR, failure.message ?: failure.javaClass.simpleName)
        } finally {
            restore = runCatching { device.restore() }.getOrElse {
                listOf(HeatRestoreStep("restore", false, it.message ?: it.javaClass.simpleName))
            }
        }
        return assemble().put("endedAtEpochMs", endedAtEpochMs())
            .put("restore", JSONArray(restore.map { it.toJson() }))
            .put("restoreComplete", restore.all { it.ok })
    }

    private fun header(startedAtEpochMs: Long): JSONObject = JSONObject()
        .put("schema", HeatMeasurement.SCHEMA)
        .put("startedAtEpochMs", startedAtEpochMs)
        .put("size", plan.size.name)
        .put("input", JSONObject().put("kind", HeatMeasurement.INPUT_KIND).put("microphone", false)
            .put("note", HeatMeasurement.INPUT_NOTE).put("promptSet", plan.promptSet.id)
            .put("promptSetVersion", plan.promptSet.version))
        .put("order", JSONArray(plan.runs.map { it.configuration.id + ":" + it.prompt.id + ":" + it.repetition }))
        .put("limits", limits.toJson())
        .put("device", device.facts())

    private fun assemble(): JSONObject {
        val configurations = JSONArray()
        for (configuration in plan.configurations) configurations.put(JSONObject()
            .put("id", configuration.id).put("label", configuration.label)
            .put("factor", configuration.factor ?: JSONObject.NULL)
            .put("settings", configuration.settingsJson())
            .put("status", status[configuration.id] ?: HeatMeasurement.NOT_RUN)
            .put("reason", reasons[configuration.id] ?: if (status[configuration.id] == null)
                "Die Messung endete, bevor diese Konfiguration an der Reihe war." else JSONObject.NULL)
            .put("model", models[configuration.id] ?: JSONObject.NULL)
            .put("modelLifecycle", lifecycle[configuration.id] ?: JSONArray()))
        val start = baseline
        return report
            .put("baselineStart", start?.toJson(0L)?.put("charging", start.charging ?: JSONObject.NULL)
                ?.put("powerSave", start.powerSave ?: JSONObject.NULL) ?: JSONObject.NULL)
            .put("configurations", configurations)
            .put("runs", runs)
            .put("complete", stop == null && plan.runs.all { planned ->
                status[planned.configuration.id] == HeatMeasurement.NOT_RUN || runRecorded(planned) })
            .put("stop", stop?.let { JSONObject().put("reason", it.reason).put("message", it.message ?: JSONObject.NULL) }
                ?: JSONObject.NULL)
    }

    private fun runRecorded(planned: HeatPlannedRun): Boolean = (0 until runs.length()).any {
        val run = runs.getJSONObject(it)
        run.getString("configuration") == planned.configuration.id && run.getString("prompt") == planned.prompt.id &&
            run.getInt("repetition") == planned.repetition
    }

    private fun execute() {
        for ((index, planned) in plan.runs.withIndex()) {
            if (halted()) return
            val configuration = planned.configuration
            if (status[configuration.id] == HeatMeasurement.NOT_RUN) continue
            if (configuration.id !in status) {
                val unavailable = device.unavailable(configuration)
                if (unavailable != null) { notRun(configuration, unavailable); continue }
            }
            if (!useModel(configuration)) { if (stop != null) return else continue }
            if (!device.applyStage(configuration.stage)) {
                notRun(configuration, "Die Bühne ließ sich nicht umschalten."); device.applyStage(HeatStageMode.RESTING); continue
            }
            if (!device.applyDialogueReuse(configuration.dialogueReuse)) {
                notRun(configuration, "Die Gesprächswiederverwendung ließ sich nicht umschalten."); continue
            }
            if (!device.applyStageDraft(configuration.stageDraft)) {
                notRun(configuration, "Der Antwortentwurf der Bühne ließ sich nicht umschalten."); device.applyStageDraft(true); continue
            }
            if (!device.applyToolSchemas(configuration.toolSchemas)) {
                notRun(configuration, "Die Modellroute ließ sich nicht umschalten."); device.applyToolSchemas(false); continue
            }
            if (configuration.id !in status) {
                status[configuration.id] = RUN
                device.model()?.let { models[configuration.id] = it }
            }
            device.progress("Wärmemessung · Lauf ${index + 1}/${plan.runs.size} · Konfiguration ${configuration.id} · Abkühlen")
            val startCondition = coolDown() ?: return
            if (!device.freshChat()) {
                stop = HeatStop(HeatStop.ERROR, "Ein neuer Chat für den Lauf ließ sich nicht öffnen.")
                return
            }
            pause(limits.settleMs)
            if (halted()) return
            device.progress("Wärmemessung · Lauf ${index + 1}/${plan.runs.size} · Konfiguration ${configuration.id} · " +
                planned.prompt.label)
            val turns = JSONArray()
            var answered = true
            for ((turnIndex, text) in planned.prompt.turns.withIndex()) {
                if (!answered || stop != null) {
                    turns.put(JSONObject().put("turn", turnIndex + 1).put("role", role(turnIndex))
                        .put("outcome", HeatMeasurement.NOT_RUN)
                        .put("message", "Die vorige Frage dieses Laufs wurde nicht vollständig beantwortet."))
                    continue
                }
                val turn = turn(turnIndex, text)
                turns.put(turn)
                answered = turn.getString("outcome") == HeatTurnRecorder.COMPLETE
            }
            runs.put(JSONObject().put("index", runs.length() + 1).put("configuration", configuration.id)
                .put("prompt", planned.prompt.id).put("repetition", planned.repetition)
                .put("startCondition", startCondition).put("turns", turns))
            runCatching { device.checkpoint(assemble()) }
        }
        // The last block may have left another model loaded; the timed way back belongs to that configuration.
        if (stop == null && currentModel != HeatModelChoice.BASELINE)
            backToBaselineModel(plan.configurations.first { it.model == currentModel })
    }

    /** True when a stop is known; a stop the device reports now is taken over. */
    private fun halted(): Boolean {
        if (stop == null) stop = device.stopReason()
        return stop != null
    }

    private fun notRun(configuration: HeatConfiguration, reason: String) {
        status[configuration.id] = HeatMeasurement.NOT_RUN
        reasons[configuration.id] = reason.take(300)
    }

    /** Loads the model [configuration] needs. False: this run cannot take place (see [stop] for a fatal case). */
    private fun useModel(configuration: HeatConfiguration): Boolean {
        if (configuration.model == currentModel) return true
        if (currentModel != HeatModelChoice.BASELINE && !backToBaselineModel(plan.configurations.first { it.model == currentModel }))
            return false
        if (configuration.model == HeatModelChoice.BASELINE) return true
        device.progress("Wärmemessung · Konfiguration ${configuration.id} · Modell wird gewechselt")
        val steps = device.swapModel(configuration.model)
        val log = lifecycle.getOrPut(configuration.id) { JSONArray() }
        steps.forEach { log.put(it.toJson()) }
        if (steps.isNotEmpty() && steps.all { it.ok }) { currentModel = configuration.model; return true }
        notRun(configuration, "Modellwechsel fehlgeschlagen: " +
            (steps.firstOrNull { !it.ok }?.let { it.action + " – " + (it.failure ?: "ohne Angabe") } ?: "kein Schritt ausgeführt"))
        // The failed swap may have unloaded the baseline model; without it nothing else can be measured.
        backToBaselineModel(configuration)
        return false
    }

    private fun backToBaselineModel(owner: HeatConfiguration): Boolean {
        device.progress("Wärmemessung · Ausgangsmodell wird wieder geladen")
        val steps = device.swapModel(HeatModelChoice.BASELINE)
        val log = lifecycle.getOrPut(owner.id) { JSONArray() }
        steps.forEach { log.put(it.toJson().put("restoresBaseline", true)) }
        currentModel = HeatModelChoice.BASELINE
        if (steps.all { it.ok }) return true
        stop = HeatStop(HeatStop.MODEL_UNAVAILABLE, "Das Ausgangsmodell ließ sich nicht wieder laden: " +
            (steps.firstOrNull { !it.ok }?.failure ?: "ohne Angabe"))
        return false
    }

    /**
     * Waits until Android's thermal status is back at the status of the measurement's start and the battery
     * temperature is within the band around the first start temperature, at least [HeatLimits.cooldownMinMs] and
     * at most [HeatLimits.cooldownMaxMs]. A start condition that was not reached is written down, never hidden.
     * Null: the measurement was stopped while waiting.
     */
    private fun coolDown(): JSONObject? {
        val start = baseline ?: device.sample().also { baseline = it }
        val began = device.nowMs()
        var announced = began
        while (true) {
            if (halted()) return null
            val now = device.sample()
            val waited = device.nowMs() - began
            if ((now.thermalStatus ?: 0) >= THERMAL_CRITICAL) {
                stop = HeatStop(HeatStop.THERMAL_CRITICAL, "Android meldet eine kritische Thermalstufe (${now.thermalStatus}).")
                return null
            }
            val thermalChecked = start.thermalStatus != null
            val batteryChecked = start.batteryTempC != null
            val thermalOk = !thermalChecked || (now.thermalStatus != null && now.thermalStatus <= start.thermalStatus)
            val batteryDelta = if (batteryChecked && now.batteryTempC != null) now.batteryTempC - start.batteryTempC else null
            val batteryOk = !batteryChecked || (batteryDelta != null && Math.abs(batteryDelta) <= limits.batteryBandC)
            val checkable = thermalChecked || batteryChecked
            val reached = checkable && thermalOk && batteryOk
            // A battery that is colder than the band cannot be brought back by waiting.
            val tooCold = batteryDelta != null && batteryDelta < -limits.batteryBandC
            if (waited >= limits.cooldownMinMs && (reached || tooCold || !checkable) || waited >= limits.cooldownMaxMs) {
                val reason = when {
                    reached -> null
                    !checkable -> "Android meldet weder Thermalstufe noch Akkutemperatur; die Startbedingung ist nicht prüfbar."
                    !thermalOk -> "Thermalstufe ${now.thermalStatus ?: "unbekannt"} statt höchstens ${start.thermalStatus} nach ${waited / 1_000} s."
                    tooCold -> "Akku kälter als das Startband (${delta(batteryDelta)} °C gegenüber dem ersten Start)."
                    else -> "Akkutemperatur außerhalb des Startbands (${delta(batteryDelta)} °C gegenüber dem ersten Start) nach ${waited / 1_000} s."
                }
                return now.toJson(0L).apply { remove("tMs") }
                    .put("reached", reached).put("waitedMs", waited).put("reason", reason ?: JSONObject.NULL)
                    .put("charging", now.charging ?: JSONObject.NULL).put("powerSave", now.powerSave ?: JSONObject.NULL)
            }
            if (device.nowMs() - announced >= 10_000L) {
                announced = device.nowMs()
                device.progress("Wärmemessung · Abkühlen ${waited / 1_000} s · Thermalstufe ${now.thermalStatus ?: "–"}" +
                    (now.batteryTempC?.let { " · Akku " + oneDecimal(it) + " °C" } ?: ""))
            }
            device.sleep(limits.sampleIntervalMs)
        }
    }

    private fun pause(ms: Long) {
        var left = ms
        while (left > 0 && !halted()) {
            val step = Math.min(left, limits.pollIntervalMs)
            device.sleep(step)
            left -= step
        }
    }

    private fun turn(turnIndex: Int, text: String): JSONObject {
        val samples = JSONArray()
        val readings = ArrayList<Pair<Long, HeatSample>>()
        val began = device.nowMs()
        fun read(phase: String) = device.sample().also {
            val at = device.nowMs() - began
            if (phase == PHASE_ANSWER) readings += at to it
            samples.put(it.toJson(at, phase))
        }
        val first = read(PHASE_ANSWER)
        device.beginTurn(text)
        var lastSample = device.nowMs()
        var abortedAt: Long? = null
        while (!device.turnEnded()) {
            val now = device.nowMs()
            if (abortedAt == null) {
                val reason = if (stop == null) device.stopReason() else null
                if (reason != null) { stop = reason; device.abortTurn(timeout = false); abortedAt = now }
                else if (now - began >= limits.turnTimeoutMs) { device.abortTurn(timeout = true); abortedAt = now }
            } else if (now - abortedAt >= ABORT_GRACE_MS) break
            if (now - lastSample >= limits.sampleIntervalMs) { read(PHASE_ANSWER); lastSample = now }
            device.sleep(limits.pollIntervalMs)
        }
        read(PHASE_ANSWER)
        val observation = device.endTurn()
        val result = observation.result
        // The end of a turn is noticed a moment after its release. The readings up to the release and the first
        // one after it describe the answer; later ones are kept in the samples, marked, and not evaluated.
        val released = result.totalMs?.let { observation.handoverAtMs - began + it }
        val endIndex = if (released == null) readings.lastIndex
            else readings.indexOfFirst { it.first >= released }.let { if (it < 0) readings.lastIndex else it }
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            if (sample.getString("phase") == PHASE_ANSWER && index > endIndex) sample.put("phase", PHASE_AFTER)
        }
        val answerReadings = readings.take(endIndex + 1).map { it.second }
        val last = answerReadings.last()
        // A spoken answer keeps the device busy after the text is there: it is awaited, sampled and kept apart.
        var speechMs: Long? = null
        if (stop == null && device.speaking()) {
            // The spoken answer starts with the answer text, i.e. at the release of the turn.
            val waitStart = device.nowMs()
            val speechStart = result.totalMs?.let { observation.handoverAtMs + it } ?: waitStart
            while (device.speaking() && device.nowMs() - waitStart < limits.speechWaitMaxMs && !halted()) {
                device.sleep(limits.pollIntervalMs)
                if (device.nowMs() - lastSample >= limits.sampleIntervalMs) { read(PHASE_SPEECH); lastSample = device.nowMs() }
            }
            speechMs = device.nowMs() - speechStart
            read(PHASE_SPEECH)
        }
        val changes = JSONArray()
        observation.thermalChanges.forEach { (at, value) ->
            changes.put(JSONObject().put("tMs", at - observation.handoverAtMs).put("status", value))
        }
        val statuses = answerReadings.mapNotNull { it.thermalStatus } + observation.thermalChanges.map { it.second }
        val temperatures = answerReadings.mapNotNull { it.batteryTempC }
        val headrooms = answerReadings.mapNotNull { it.headroom }
        if ((statuses.maxOrNull() ?: 0) >= THERMAL_CRITICAL && stop == null)
            stop = HeatStop(HeatStop.THERMAL_CRITICAL, "Android meldet eine kritische Thermalstufe (${statuses.max()}).")
        return JSONObject().put("turn", turnIndex + 1).put("role", role(turnIndex)).put("textCharacters", text.length)
            .put("outcome", result.outcome).put("message", result.message ?: JSONObject.NULL)
            .put("firstTokenMs", result.firstTokenMs ?: JSONObject.NULL)
            .put("firstVisibleTextMs", result.firstVisibleTextMs ?: JSONObject.NULL)
            .put("firstVisibleTextEvent", result.firstVisibleTextEvent ?: JSONObject.NULL)
            .put("firstVisibleTextShown", result.firstVisibleTextShown ?: JSONObject.NULL)
            .put("nativePreviewMs", result.nativePreviewMs ?: JSONObject.NULL)
            .put("nativePreviewShown", result.nativePreviewShown ?: JSONObject.NULL)
            .put("answerHandoverMs", result.answerHandoverMs ?: JSONObject.NULL)
            .put("totalMs", result.totalMs ?: JSONObject.NULL)
            .put("modelCalls", result.modelCalls).put("completions", JSONArray(result.completions))
            .put("promptCharacters", result.promptCharacters ?: JSONObject.NULL)
            .put("generatedCharacters", result.generatedCharacters)
            .put("answerCharacters", result.answerCharacters ?: JSONObject.NULL)
            .put("charsPerSecond", result.charsPerSecond?.let(HeatHistogram::round) ?: JSONObject.NULL)
            .put("inputTokens", result.inputTokens ?: JSONObject.NULL)
            .put("outputTokens", result.outputTokens ?: JSONObject.NULL)
            .put("runtimeFirstTokenMs", result.runtimeFirstTokenMs ?: JSONObject.NULL)
            .put("runtimeTotalMs", result.runtimeTotalMs ?: JSONObject.NULL)
            .put("dialogueReused", result.dialogueReused ?: JSONObject.NULL)
            .put("dialogueReuseReason", result.dialogueReuseReason ?: JSONObject.NULL)
            .put("toolFreeRoute", result.toolFreeRoute ?: JSONObject.NULL)
            .put("speechOutputMs", speechMs ?: JSONObject.NULL)
            .put("thermal", JSONObject().put("start", first.thermalStatus ?: JSONObject.NULL)
                .put("end", last.thermalStatus ?: JSONObject.NULL).put("max", statuses.maxOrNull() ?: JSONObject.NULL)
                .put("changes", changes))
            .put("batteryTempC", JSONObject().put("start", first.batteryTempC ?: JSONObject.NULL)
                .put("end", last.batteryTempC ?: JSONObject.NULL).put("max", temperatures.maxOrNull() ?: JSONObject.NULL))
            .put("headroom", JSONObject().put("start", first.headroom ?: JSONObject.NULL)
                .put("max", headrooms.maxOrNull() ?: JSONObject.NULL))
            .put("samples", samples)
            .put("windowFrames", observation.windowFrames)
            .put("stageFrames", observation.stageFrames)
    }

    private fun role(turnIndex: Int): String = if (turnIndex == 0) ROLE_FIRST else ROLE_FOLLOW_UP

    private fun delta(value: Double?): String = value?.let { (if (it >= 0) "+" else "−") + oneDecimal(Math.abs(it)) } ?: "unbekannt"

    companion object {
        const val RUN = "RUN"
        const val ROLE_FIRST = "ERSTFRAGE"
        const val ROLE_FOLLOW_UP = "FOLGEFRAGE"
        const val PHASE_ANSWER = "ANSWER"
        /** Read after the turn was released, while its end was not yet certain. */
        const val PHASE_AFTER = "AFTER_ANSWER"
        const val PHASE_SPEECH = "SPEECH"
        const val THERMAL_CRITICAL = 4
        /** After the stop control was used, the turn gets this long to end before its bookkeeping is closed. */
        const val ABORT_GRACE_MS = 15_000L

        internal fun oneDecimal(value: Double): String =
            (Math.round(value * 10.0) / 10.0).toString().replace('.', ',')
    }
}

/**
 * Remembers what a measurement changed and puts it back, newest change first. Each entry keeps the value that was
 * there before the FIRST change; restoring is idempotent, and a failing entry never keeps the others from being
 * restored. Nothing here is persisted: a measurement override lives only as long as this object.
 */
class HeatOverrideLedger {
    private class Entry(val name: String, val undo: () -> Unit)
    private val entries = ArrayList<Entry>()

    /** Registers [undo] once per [name]; a later change of the same thing keeps the first undo. */
    @Synchronized fun changed(name: String, undo: () -> Unit) {
        if (entries.none { it.name == name }) entries += Entry(name, undo)
    }

    @Synchronized fun pending(): List<String> = entries.map { it.name }

    @Synchronized fun restoreAll(): List<HeatRestoreStep> {
        val steps = ArrayList<HeatRestoreStep>()
        for (entry in entries.asReversed()) {
            val outcome = runCatching(entry.undo)
            steps += HeatRestoreStep(entry.name, outcome.isSuccess, outcome.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
        }
        entries.clear()
        return steps
    }
}
