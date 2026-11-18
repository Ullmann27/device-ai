package at.heinz.deviceai.evaluation

import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import org.json.JSONObject

/**
 * A scripted phone for host tests of the heat measurement. EVERY number in here is synthetic and chosen only to
 * exercise the protocol (ordering, waiting, flags, statistics); none of them is a measurement or a typical value
 * of any device. Time moves only when the protocol waits. The turn itself is recorded by the real
 * [HeatTurnRecorder], driven through the same calls the app's turn path makes.
 */
internal class HeatMeasurementFakeDevice : HeatMeasurementDevice {
    /** How one synthetic turn behaves. */
    data class Turn(
        val durationMs: Long = 4_000L,
        val firstTokenMs: Long = 1_000L,
        val characters: Int = 120,
        val completion: String = "COMPLETE",
        val failure: String? = null,
        /** False: the app answers without the model (a deterministic reply). */
        val modelCall: Boolean = true,
        val accepted: Boolean = true,
        val heatsBatteryC: Double = 0.4,
        /** Thermal status Android „reports“ from the middle of the turn on. */
        val thermalStatus: Int = 0,
        val speechMs: Long = 0L,
        val reused: Boolean? = null,
        val windowFrameMs: List<Double> = listOf(8.2, 8.4, 16.9, 9.0),
        val stageDelivered: Int = 20
    )

    var now = 5_000_000L
    val log = ArrayList<String>()

    // What a measurement may change – and must put back.
    var stage = HeatStageMode.RESTING
    var dialogueReuse = false
    var stageDraft = true
    var toolSchemas = false
    var model = "gemma4-e4b-it"
    var chat = 0
    private val ledger = HeatOverrideLedger()
    var restores = 0
    var failRestoreOf: String? = null

    // The synthetic environment.
    var thermal = 0
    var batteryC = 30.0
    var coolingCPerSecond = 0.05
    var reportsThermal = true
    var reportsBattery = true
    var e2bImported = true
    var stageAvailable = true
    var blocked: String? = null
    var swapFailure: String? = null
    var freshChatWorks = true
    var stopWhen: (HeatMeasurementFakeDevice) -> HeatStop? = { null }
    /** The script: which turn the device produces for the configuration that is applied right now. */
    var script: (HeatMeasurementFakeDevice, String) -> Turn = { _, _ -> Turn() }
    val checkpoints = ArrayList<Int>()
    val turnsStarted = ArrayList<String>()

    private val recorder = HeatTurnRecorder { recorderClock ?: now }
    private var recorderClock: Long? = null
    private var turn: Turn? = null
    private var turnStart = 0L
    private var probe: HeatTurnRecorder.GenerationProbe? = null
    private var callback: LocalInferenceCallback? = null
    private var firstTokenSent = false
    private var finished = false
    private var aborted = false
    private var speechUntil = 0L
    private val changes = ArrayList<Pair<Long, Int>>()

    /** The settings a restore must bring back; compared before and after a measurement. */
    fun settings(): String = "$stage/$dialogueReuse/$model" + (if (stageDraft) "" else "/no-draft") + (if (toolSchemas) "/tools" else "")

    override fun nowMs(): Long = now

    override fun sleep(ms: Long) {
        val target = now + ms
        val running = turn
        if (running != null && !finished) {
            if (!firstTokenSent && running.modelCall && target >= turnStart + running.firstTokenMs) at(turnStart + running.firstTokenMs) {
                callback?.onEvent(LocalInferenceEvent.TextDelta("x".repeat(10)))
                // Like the app: the first text piece goes to the stage page as a draft, when the page gets drafts.
                if (stageAvailable && stageDraft) recorder.stageDraftSet(shown = stage != HeatStageMode.OFF)
                firstTokenSent = true
            }
            val half = turnStart + running.durationMs / 2
            if (running.thermalStatus != thermal && target >= half && now < half) {
                thermal = running.thermalStatus
                changes += half to thermal
            }
            if (target >= turnStart + running.durationMs) at(turnStart + running.durationMs) { finish(running) }
        } else if (running == null) {
            batteryC = Math.max(batteryFloor, batteryC - coolingCPerSecond * ms / 1_000.0)
            idleMs += ms
            if (idleMs >= thermalRecoversAfterMs) thermal = 0
        }
        now = target
    }

    /** The battery never cools below this synthetic ambient value. */
    var batteryFloor = 30.0
    /** The synthetic thermal status falls back to 0 after this much time without a turn. */
    var thermalRecoversAfterMs = 30_000L
    private var idleMs = 0L

    private fun at(time: Long, block: () -> Unit) {
        recorderClock = time
        try { block() } finally { recorderClock = null }
    }

    private fun finish(running: Turn) {
        if (running.modelCall) {
            if (running.completion == "COMPLETE") callback?.onEvent(LocalInferenceEvent.Completed(
                LocalInferenceMetrics(900, running.characters, 12, null, null, running.firstTokenMs, running.durationMs)))
            probe?.finished(running.completion, running.failure, running.characters)
            running.reused?.let { recorder.dialogueReuse(it, if (it) null else "SYNTHETIC_NOT_REUSED") }
        }
        if (running.completion == "COMPLETE" || LocalModelThermalGuard.isThermal(running.failure))
            recorder.bubble(false, running.characters, HeatTurnRecorder.EVENT_STAGE_MESSAGE, stage != HeatStageMode.OFF)
        if (running.modelCall) recorder.busy(false)
        batteryC += running.heatsBatteryC
        if (running.speechMs > 0) speechUntil = turnStart + running.durationMs + running.speechMs
        finished = true
    }

    override fun stopReason(): HeatStop? = stopWhen(this)
    override fun blocker(): String? = blocked

    override fun facts(): JSONObject = JSONObject().put("manufacturer", "SYNTHETIC").put("model", "HOST-TEST-FIXTURE")
        .put("androidRelease", "0").put("androidSdk", 0).put("appVersion", "synthetic").put("appVersionCode", 0)
        .put("speechOutput", false).put("htmlStage", stageAvailable).put("stageProfile", "AUTO").put("dialogueReuseSwitch", false)

    override fun sample(): HeatSample = HeatSample(
        thermalStatus = if (reportsThermal) thermal else null,
        headroom = if (reportsThermal) HeatHistogram.round(0.3 + (batteryC - 30.0) * 0.1) else null,
        batteryTempC = if (reportsBattery) HeatHistogram.round(batteryC) else null,
        batteryPercent = 80, charging = false, powerSave = false)

    override fun model(): JSONObject? = JSONObject().put("id", model).put("backend", "CPU").put("threads", 4)
        .put("bytes", 1).put("fileName", "$model.synthetic")

    override fun unavailable(configuration: HeatConfiguration): String? = when {
        configuration.backend == HeatBackendChoice.GPU -> "Synthetisch: GPU wird nicht geschaltet."
        configuration.model == HeatModelChoice.GEMMA_E2B && !e2bImported -> "Synthetisch: Gemma 4 E2B ist nicht importiert."
        configuration.needsStage && !stageAvailable -> "Synthetisch: keine Bühne."
        else -> null
    }

    override fun applyStage(mode: HeatStageMode): Boolean {
        val before = stage
        ledger.changed("Bühne") { if (failRestoreOf == "Bühne") error("synthetic restore failure"); stage = before }
        stage = mode
        log += "stage:$mode"
        return true
    }

    override fun applyDialogueReuse(on: Boolean): Boolean {
        val before = dialogueReuse
        if (before != on) ledger.changed("Gespräch im Modell behalten") { dialogueReuse = before }
        dialogueReuse = on
        return true
    }

    override fun applyStageDraft(on: Boolean): Boolean {
        if (on != stageDraft) ledger.changed("Antwortentwurf der Bühne") { stageDraft = true }
        stageDraft = on
        return true
    }

    override fun applyToolSchemas(on: Boolean): Boolean {
        if (on != toolSchemas) ledger.changed("Modellroute (Werkzeugschemas)") { toolSchemas = false }
        toolSchemas = on
        return true
    }

    override fun swapModel(choice: HeatModelChoice): List<HeatLifecycleStep> {
        val target = if (choice == HeatModelChoice.BASELINE) "gemma4-e4b-it" else "gemma4-e2b-it"
        if (model == target) return emptyList()
        val before = model
        ledger.changed("Geladenes Modell") { model = before }
        log += "swap:$target"
        now += 700
        swapFailure?.let { if (choice != HeatModelChoice.BASELINE) { model = "none"; return listOf(
            HeatLifecycleStep("UNLOAD", 200, true), HeatLifecycleStep("LOAD", 500, false, it)) } }
        model = target
        return listOf(HeatLifecycleStep("UNLOAD", 200, true), HeatLifecycleStep("LOAD", 500, true))
    }

    override fun freshChat(): Boolean {
        if (!freshChatWorks) return false
        val before = chat
        if (before == 0) ledger.changed("Chat") { chat = 0 }
        chat++
        log += "chat:$chat"
        return true
    }

    override fun beginTurn(text: String) {
        val running = script(this, text)
        turn = running; turnStart = now; firstTokenSent = false; finished = false; aborted = false; idleMs = 0L
        changes.clear()
        turnsStarted += settings() + "|" + text
        log += "turn:${settings()}"
        recorder.begin()
        if (!running.accepted) return
        recorder.bubble(true, text.length, HeatTurnRecorder.EVENT_STAGE_MESSAGE, true)
        if (running.modelCall) {
            recorder.busy(true)
            recorder.route(withoutTools = !toolSchemas)
            // Synthetic prompt sizes: the route with tool schemas has another prompt text.
            probe = recorder.probe(if (toolSchemas) 1_040 else 900)
            callback = probe?.observe(LocalInferenceCallback { })
        } else recorder.bubble(false, 40, HeatTurnRecorder.EVENT_STAGE_MESSAGE, true)
    }

    override fun turnEnded(): Boolean = recorder.poll(appBusy = turn?.modelCall == true && !finished && !aborted) == HeatTurnRecorder.State.ENDED

    override fun abortTurn(timeout: Boolean) {
        if (timeout) recorder.timeout() else recorder.ownerStop()
        log += if (timeout) "abort:timeout" else "abort:stop"
        aborted = true
        probe?.finished("CANCELLED", "synthetic stop", 30)
        recorder.busy(false)
    }

    override fun endTurn(): HeatTurnObservation {
        recorder.forceEnd()
        val running = turn!!
        // Like the app: the frame counters are read at the release of the turn, not when its end is noticed.
        val observedMs = recorder.result().totalMs ?: (now - turnStart)
        val frames = HeatFrameAccumulator(16.667)
        running.windowFrameMs.forEachIndexed { index, value -> frames.frame(turnStart + index * 100L, value) }
        val stageFrames = if (!stageAvailable) HeatFrameAccumulator.notAvailable("Synthetisch: keine Bühne.")
            else HeatStageFrames.parse(JSONObject().put("schema", HeatStageFrames.PROBE_SCHEMA)
                .put("delivered", if (stage == HeatStageMode.OFF) 0 else if (stage == HeatStageMode.FULL_EFFECTS) running.stageDelivered * 3 else running.stageDelivered)
                .put("ticks", 240).put("limit", if (stage == HeatStageMode.FULL_EFFECTS) 15 else 5).put("visibility", if (stage == HeatStageMode.OFF) "hidden" else "visible")
                .put("histogram", HeatHistogram().apply { if (stage != HeatStageMode.OFF) repeat(running.stageDelivered - 1) { add(200.0) } }.toJson())
                .toString(), observedMs)
        turn = null
        return HeatTurnObservation(recorder.result(), turnStart, changes.toList(), frames.toJson(observedMs), stageFrames)
    }

    override fun speaking(): Boolean = now < speechUntil

    override fun restore(): List<HeatRestoreStep> {
        restores++
        log += "restore"
        return ledger.restoreAll()
    }

    override fun progress(line: String) { log += "progress" }
    override fun checkpoint(report: JSONObject) { checkpoints += report.getJSONArray("runs").length() }
}

/**
 * Two complete synthetic measurements for tests of the summary, the readable report and the host tool
 * (tools/heat-report/fixtures). They are produced by the real protocol and report code from a scripted phone.
 * The numbers are invented test data and say nothing about any device.
 */
internal object HeatSyntheticScenarios {
    const val NOTE = "SYNTHETISCHE TESTDATEN aus HeatSyntheticScenarios (Host-Test). Keine Messung, kein Gerät, keine typischen Werte."
    private fun file(path: String) = java.io.File(path).takeIf(java.io.File::isFile) ?: java.io.File("../$path")
    val prompts: HeatPromptSet get() = HeatPromptSet.parse(
        file("app/src/main/assets/${HeatMeasurement.PROMPT_ASSET}").readText(Charsets.UTF_8))

    private fun measure(startedAt: Long, script: (HeatMeasurementFakeDevice, Boolean) -> HeatMeasurementFakeDevice.Turn): JSONObject {
        val orders = prompts
        val followUps = orders.prompts.map { it.turns.last() }.toSet()
        val device = HeatMeasurementFakeDevice()
        device.script = { phone, text -> script(phone, text in followUps) }
        val report = HeatMeasurementProtocol(device, HeatMeasurement.plan(HeatSize.KURZ, orders)).run(startedAt) { startedAt + 480_000L }
        return HeatReport.withSummary(report).put("synthetic", true).put("syntheticNote", NOTE)
    }

    /**
     * The order with all tool schemas is stopped by the (scripted) thermal guard; the small model and the kept
     * dialogue are faster; without the stage draft the first visible text is the finished answer.
     */
    fun before(): JSONObject = measure(1_794_000_000_000L) { phone, followUp ->
        when {
            phone.model == "gemma4-e2b-it" -> HeatMeasurementFakeDevice.Turn(3_200L, 800L, heatsBatteryC = 0.3, thermalStatus = 0)
            phone.stage == HeatStageMode.OFF -> HeatMeasurementFakeDevice.Turn(5_600L, 1_500L, heatsBatteryC = 0.4, thermalStatus = 0)
            phone.stage == HeatStageMode.FULL_EFFECTS -> HeatMeasurementFakeDevice.Turn(6_400L, 1_500L, heatsBatteryC = 0.9, thermalStatus = 2)
            phone.toolSchemas -> HeatMeasurementFakeDevice.Turn(3_000L, 2_400L, characters = 60,
                completion = "FAILED", failure = at.heinz.deviceai.localmodel.LocalModelThermalGuard.STOPPED_THERMAL,
                heatsBatteryC = 1.3, thermalStatus = 3, windowFrameMs = listOf(8.2, 34.0, 41.5, 9.0))
            phone.dialogueReuse && followUp -> HeatMeasurementFakeDevice.Turn(3_000L, 400L, heatsBatteryC = 0.3, thermalStatus = 1, reused = true)
            phone.dialogueReuse -> HeatMeasurementFakeDevice.Turn(6_000L, 1_500L, heatsBatteryC = 0.6, thermalStatus = 1, reused = false)
            else -> HeatMeasurementFakeDevice.Turn(6_000L, 1_500L, heatsBatteryC = 0.6, thermalStatus = 1)
        }
    }

    /** A second synthetic measurement with other numbers, as the „after“ side of a comparison. */
    fun after(): JSONObject = measure(1_794_086_400_000L) { phone, followUp ->
        when {
            phone.model == "gemma4-e2b-it" -> HeatMeasurementFakeDevice.Turn(3_000L, 700L, heatsBatteryC = 0.2, thermalStatus = 1)
            phone.stage == HeatStageMode.OFF -> HeatMeasurementFakeDevice.Turn(4_800L, 1_200L, heatsBatteryC = 0.3, thermalStatus = 1)
            phone.stage == HeatStageMode.FULL_EFFECTS -> HeatMeasurementFakeDevice.Turn(5_600L, 1_300L, heatsBatteryC = 0.9, thermalStatus = 2)
            phone.toolSchemas -> HeatMeasurementFakeDevice.Turn(7_400L, 3_200L, heatsBatteryC = 0.9,
                thermalStatus = 2, windowFrameMs = listOf(8.2, 17.0, 9.0, 9.1))
            phone.dialogueReuse && followUp -> HeatMeasurementFakeDevice.Turn(2_600L, 300L, heatsBatteryC = 0.2, thermalStatus = 1, reused = true)
            phone.dialogueReuse -> HeatMeasurementFakeDevice.Turn(5_000L, 1_200L, heatsBatteryC = 0.4, thermalStatus = 1, reused = false)
            else -> HeatMeasurementFakeDevice.Turn(5_000L, 1_200L, heatsBatteryC = 0.4, thermalStatus = 1)
        }
    }
}
