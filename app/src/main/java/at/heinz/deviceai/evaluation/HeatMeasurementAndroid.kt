package at.heinz.deviceai.evaluation

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.FrameMetrics
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import at.heinz.deviceai.BuildConfig
import at.heinz.deviceai.ContinuumSelectedModels
import at.heinz.deviceai.OwnerDocumentStore
import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelLoadAdmission
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import at.heinz.deviceai.localmodel.localModelResourceSnapshot
import at.heinz.deviceai.ui.ContinuumCommand03OriginalStage
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * What the measurement needs from the Activity that owns the owner's turn path. The Activity implements it with
 * one-line delegations to what it already has; nothing here adds behaviour to a normal turn. Unless stated, the
 * methods are called on the UI thread.
 */
internal interface HeatMeasurementHost {
    val activity: Activity
    /** Thread-safe: the app is in front and its Activity is alive. */
    fun foreground(): Boolean
    fun busy(): Boolean
    /** Another lifecycle step (import, unload, bot lookup) is running; a measurement must not start. */
    fun otherWorkPending(): Boolean
    fun cloudBrainSelected(): Boolean
    fun runtime(): LocalModelRuntime
    /** Worker thread: reads the model catalog. */
    fun importedModels(): List<ImportedLocalModel>
    /** The loaded model changed through the measurement: drop what was bound to the old one and refresh the UI. */
    fun modelChanged()
    /** The Command03 stage while it is the visible main design, else null. */
    fun stage(): ContinuumCommand03OriginalStage?
    fun pushStageState()
    fun dialogueReuseOn(): Boolean
    /** In memory only – the stored owner switch is never written by a measurement. */
    fun overrideDialogueReuse(on: Boolean)
    fun speechOutputOn(): Boolean
    fun speaking(): Boolean
    fun handsFreeActive(): Boolean
    fun endHandsFree()
    fun sessionId(): String
    fun newChat()
    fun selectChat(id: String)
    /** The hand-over of a spoken turn: the recognised text goes into the composer and through the send path. */
    fun submit(text: String)
    fun clearInput()
    /** The owner's normal stop control. */
    fun stopTurn()
    fun say(text: String)
    fun status(line: String)
    /** Answer times the app measured in this session (ms). */
    fun recentAnswerMs(): List<Long>
    fun stageProfile(): String
    fun coolingMode(): String
}

/**
 * Android side of the owner-started „Wärme- und Zeitmessung“ (protocol: [HeatMeasurementProtocol]). It reads
 * Android's own sensors, counts the frames of the app window and of the stage, hands the fixed orders to the
 * app's normal send path and writes the report into the owner's Download folder. It works offline, needs no
 * permission and stores no setting: every override lives in memory and is put back by [HeatOverrideLedger].
 *
 * The hook methods ([generationProbe], [onBusy], …) are called by the turn path at all times; while no measurement
 * is running each of them is a single null check.
 */
internal class HeatMeasurementController(private val host: HeatMeasurementHost) {
    private class Session(val plan: HeatPlan, val originalModel: ImportedLocalModel, val originalChat: String) {
        @Volatile var ownerStop = false
        @Volatile var injecting = false
        /** The order that is on its way through the send path; the path may come back to it after a lookup. */
        @Volatile var pendingText: String? = null
        @Volatile var selfAbort = false
        val ledger = HeatOverrideLedger()
    }

    private val recorder = HeatTurnRecorder(SystemClock::elapsedRealtime)
    /** The plan of a size. Host tests replace it to shorten the waits; the app always uses [HeatMeasurement.plan]. */
    @Volatile var planOf: (HeatSize, HeatPromptSet) -> HeatPlan = HeatMeasurement::plan
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null
    @Volatile private var device: Device? = null
    private var overlay: Overlay? = null

    /** Read by the stage snapshot: true only inside configuration C of a running measurement. */
    @Volatile var stageFullEffects = false
        private set

    val active: Boolean get() = session != null

    // ── hooks of the turn path ───────────────────────────────────────────────────────────────────────────────

    fun generationProbe(promptCharacters: Int?): HeatTurnRecorder.GenerationProbe? =
        if (session == null) null else recorder.probe(promptCharacters)

    fun onPreviewText(view: View) { if (session != null) recorder.previewTextSet(view.isShown) }

    fun onBubble(user: Boolean, characters: Int) {
        if (session == null) return
        val stage = host.stage()
        recorder.bubble(user, characters,
            if (stage != null) HeatTurnRecorder.EVENT_STAGE_MESSAGE else HeatTurnRecorder.EVENT_NATIVE_BUBBLE,
            shown = stage?.isShown ?: true)
    }

    fun onBusy(value: Boolean) {
        if (session == null) return
        recorder.busy(value)
        device?.busyChanged(value)
    }

    fun onDialogueReuse(reused: Boolean, reason: String?) { if (session != null) recorder.dialogueReuse(reused, reason) }

    fun onTurnRoute(withoutTools: Boolean) { if (session != null) recorder.route(withoutTools) }

    /** The app's stop control was used. During a measurement that ends the measurement, whoever pressed it. */
    fun onStopControl() {
        val running = session ?: return
        if (running.selfAbort) return
        running.ownerStop = true
        recorder.ownerStop()
    }

    /**
     * First step of the send path. True: the text was a measurement command (or the measurement is running and
     * owns the lane) and nothing else may happen with it.
     */
    fun consumeInput(text: String): Boolean {
        val running = session
        if (running != null) {
            if (running.injecting || text.trim() == running.pendingText) return false
            if (HeatMeasurementCommand.stopsRunningMeasurement(text)) { host.clearInput(); requestStop() }
            else Toast.makeText(host.activity, "Die Wärmemessung läuft. STOPP beendet sie.", Toast.LENGTH_SHORT).show()
            return true
        }
        when (val command = HeatMeasurementCommand.parse(text)) {
            is HeatCommand.Start -> { host.clearInput(); open(command.size) }
            HeatCommand.SavePartial -> { host.clearInput(); savePartial() }
            // „Stopp“ without a running measurement is none of this feature's business.
            HeatCommand.Stop, null -> return false
        }
        return true
    }

    private fun checkpointFile(): File = File(File(host.activity.filesDir, "heat-measurement"), "letzte-messung-teilstand.json")

    /**
     * „Continuum Wärmemessung Teilbericht“: a measurement that could not write its report (the app was closed or
     * ended by Android in the middle) left its runs so far in the app's storage. They are saved as a report that
     * says that it is incomplete; nothing is measured or added here.
     */
    private fun savePartial() {
        val file = checkpointFile()
        if (!file.isFile) { host.say("WÄRME- UND ZEITMESSUNG · Es liegt kein Teilstand einer abgebrochenen Messung vor."); return }
        Thread({
            val saved = runCatching {
                val report = HeatReport.read(file.readText(Charsets.UTF_8))
                if (report.isNull("stop")) report.put("stop", JSONObject().put("reason", HeatStop.INTERRUPTED)
                    .put("message", "Die Messung wurde unterbrochen, bevor sie ihren Bericht schreiben konnte; ob alle Einstellungen " +
                        "zurückgestellt wurden, ist nicht aufgezeichnet."))
                report.put("complete", false)
                HeatReport.withSummary(report)
                (report to export(report)).also { file.delete() }
            }
            ui.post {
                if (host.activity.isFinishing || host.activity.isDestroyed) return@post
                saved.onSuccess { (report, files) ->
                    host.say(HeatReport.chatSummary(report, files.map { it.displayName }, files.firstOrNull()?.folder))
                }.onFailure { host.say("WÄRME- UND ZEITMESSUNG · Der Teilstand ließ sich nicht als Bericht speichern: " +
                    (it.message ?: it.javaClass.simpleName).take(160)) }
            }
        }, "continuum-heat-partial").start()
    }

    fun requestStop() {
        val running = session ?: return
        running.ownerStop = true
        recorder.ownerStop()
        overlay?.show("Wärmemessung · wird gestoppt, Einstellungen werden zurückgestellt …")
        if (host.busy()) { running.selfAbort = true; runCatching { host.stopTurn() }; running.selfAbort = false }
    }

    // ── start ────────────────────────────────────────────────────────────────────────────────────────────────

    /** Shows what will be measured and how long it may take; nothing starts without the owner's confirmation. */
    fun open(size: HeatSize? = null) {
        val activity = host.activity
        if (session != null) { Toast.makeText(activity, "Die Wärmemessung läuft bereits.", Toast.LENGTH_SHORT).show(); return }
        blocker()?.let { host.say("WÄRME- UND ZEITMESSUNG · nicht gestartet. $it"); return }
        val prompts = runCatching {
            activity.assets.open(HeatMeasurement.PROMPT_ASSET).bufferedReader(Charsets.UTF_8).use { HeatPromptSet.parse(it.readText()) }
        }.getOrElse { host.say("WÄRME- UND ZEITMESSUNG · Die festen Aufträge konnten nicht gelesen werden."); return }
        val recent = host.recentAnswerMs()
        val plans = (if (size == null) HeatSize.entries else listOf(size)).map { planOf(it, prompts) }
        val message = plans.joinToString("\n\n") { it.announcement(recent) } + "\n\n" + HeatMeasurement.INPUT_NOTE +
            ". Die Messung läuft ohne Internet. Sie legt für jeden Lauf einen neuen Chat an und stellt danach alles zurück."
        val dialog = AlertDialog.Builder(activity).setTitle("Wärme- und Zeitmessung").setMessage(message)
            .setNegativeButton("Abbrechen", null)
        if (plans.size == 1) dialog.setPositiveButton("STARTEN") { _, _ -> start(plans[0]) }
        else dialog.setPositiveButton("KURZ STARTEN") { _, _ -> start(plans[0]) }
            .setNeutralButton("VOLL STARTEN") { _, _ -> start(plans[1]) }
        dialog.show()
    }

    /** Null when a measurement can start now. */
    private fun blocker(): String? {
        val runtime = host.runtime()
        val model = runtime.loadedModel
        return when {
            host.cloudBrainSelected() -> "Als Gehirn ist Claude (Cloud) gewählt; gemessen wird nur das Modell auf dem Gerät."
            host.busy() || host.otherWorkPending() -> "Ein anderer Auftrag oder ein Bot-Dialog ist noch aktiv."
            model == null || runtime.state != LocalModelState.READY ->
                "Kein geladenes Modell im Zustand BEREIT. Bitte zuerst Gemma 4 E4B laden."
            model.id != ContinuumSelectedModels.LOCAL_ID ->
                "Die Basis der Messung ist Gemma 4 E4B (${ContinuumSelectedModels.LOCAL_ID}); geladen ist ${model.id}. Bitte zuerst Gemma 4 E4B laden."
            runtime.activeBackend == LocalModelBackend.GPU ->
                "Die Basis der Messung ist das CPU-Backend; das Modell rechnet gerade auf der GPU. Bitte den Beschleuniger auf CPU stellen und das Modell neu laden."
            (sensors.thermalStatus() ?: 0) >= LocalModelThermalGuard.SEVERE ->
                "Android meldet starke Erwärmung. Bitte das Gerät abkühlen lassen und die Messung dann starten."
            else -> null
        }
    }

    // Created with the first measurement: the Activity has no context yet while its fields are initialised.
    private val sensors by lazy { HeatSensors(host.activity) }

    private fun start(plan: HeatPlan) {
        val activity = host.activity
        if (session != null) return
        blocker()?.let { host.say("WÄRME- UND ZEITMESSUNG · nicht gestartet. $it"); return }
        val model = host.runtime().loadedModel ?: return
        val running = Session(plan, model, host.sessionId())
        session = running
        if (host.handsFreeActive()) host.endHandsFree()
        // The screen stays on for the run; the flag is cleared again only when the measurement set it.
        val window = activity.window
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            running.ledger.changed("Bildschirm anlassen") { onUi { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
        }
        overlay = Overlay(activity, ::requestStop).also { it.show("Wärmemessung · startet") }
        running.ledger.changed("Messanzeige") { onUi { overlay?.remove(); overlay = null } }
        sensors.listen()
        running.ledger.changed("Thermal-Beobachter") { sensors.close() }
        // The start is written into the owner's chat; the result is added there when the measurement has ended.
        host.say("WÄRME- UND ZEITMESSUNG (" + plan.size.label + ") gestartet: " + plan.runs.size + " Läufe mit festen Aufträgen, " +
            "jeder in einem eigenen neuen Chat. " + HeatMeasurement.INPUT_NOTE + ". Das Gerät bitte liegen lassen; STOPP beendet die Messung.")
        val startedAt = System.currentTimeMillis()
        Thread({
            val report = runCatching {
                HeatReport.withSummary(HeatMeasurementProtocol(Device(running).also { device = it }, plan)
                    .run(startedAt, System::currentTimeMillis))
            }
            device = null
            // The protocol restores in its own finally; this covers a failure outside of it.
            runCatching { running.ledger.restoreAll() }
            stageFullEffects = false
            val saved = report.mapCatching { export(it) }
            // The report is in the owner's Download folder; the working copy of an interrupted run is not needed.
            if (saved.isSuccess) runCatching { checkpointFile().delete() }
            // The owner's chat is selected again asynchronously; the summary belongs there, not in a probe chat.
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (SystemClock.elapsedRealtime() < deadline &&
                runCatching { onUi(1_000L) { host.sessionId() == running.originalChat && !host.busy() } }.getOrNull() != true)
                Thread.sleep(100L)
            session = null
            ui.post { finished(report, saved.getOrNull()) }
        }, "continuum-heat-measurement").start()
    }

    private fun finished(report: Result<JSONObject>, saved: List<OwnerSavedFile>?) {
        val activity = host.activity
        if (activity.isFinishing || activity.isDestroyed) return
        val json = report.getOrNull()
        if (json == null) {
            host.say("WÄRME- UND ZEITMESSUNG FEHLGESCHLAGEN · " +
                (report.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "ohne Angabe").take(200))
            return
        }
        val files = saved.orEmpty()
        host.status("Wärmemessung · beendet")
        host.say(HeatReport.chatSummary(json, files.map { it.displayName }, files.firstOrNull()?.folder))
        val shareable = files.mapNotNull { it.uri }
        if (shareable.isEmpty()) return
        AlertDialog.Builder(activity).setTitle("Bericht der Wärmemessung")
            .setMessage("Gespeichert: " + files.joinToString(" und ") { it.displayName } + " (" + files.first().folder +
                "). Möchtest du die beiden Dateien jetzt teilen?")
            .setNegativeButton("Später", null)
            .setPositiveButton("TEILEN") { _, _ -> share(shareable) }
            .show()
    }

    /** JSON with every raw value and the readable text, side by side in the owner's Download folder. */
    private fun export(report: JSONObject): List<OwnerSavedFile> {
        val store = OwnerDocumentStore(host.activity)
        val stem = HeatReport.fileStem(report)
        return listOf(
            store.save("$stem.json", "application/json", report.toString(1).toByteArray(Charsets.UTF_8)),
            store.save("$stem.md", "text/markdown", HeatReport.markdown(report).toByteArray(Charsets.UTF_8)))
    }

    private fun share(uris: List<String>) {
        val streams = ArrayList(uris.map(Uri::parse))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_SUBJECT, "Continuum Wärme- und Zeitmessung")
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
            clipData = ClipData.newRawUri("Wärmemessung", streams[0]).apply { streams.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { host.activity.startActivity(Intent.createChooser(intent, "Bericht der Wärmemessung teilen")) }
            .onFailure { Toast.makeText(host.activity, "Die Dateien liegen im Download-Ordner; Teilen ließ sich nicht öffnen.", Toast.LENGTH_LONG).show() }
    }

    /** Runs [block] on the UI thread and waits for it; null when the UI thread did not answer in time. */
    private fun <T> onUi(timeoutMs: Long = 10_000L, block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = AtomicReference<Result<T>?>(null)
        val done = CountDownLatch(1)
        ui.post { result.set(runCatching(block)); done.countDown() }
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        return result.get()?.getOrThrow()
    }

    // ── the phone as the protocol sees it ────────────────────────────────────────────────────────────────────

    private inner class Device(private val running: Session) : HeatMeasurementDevice {
        private val activity = host.activity
        private val windowFrames = HeatWindowFrames(activity)
        private var handoverAt = 0L
        private var stageProbeStarted = false
        @Volatile private var observing = false
        // What the frame counters showed at the moment the app last released the turn (UI thread).
        private var releasedAtMs = -1L
        private var windowAtRelease: JSONObject? = null
        private var stageAtRelease: String? = null

        /**
         * UI thread. The turn's end is known only a moment after its last release, so the frame counters are read
         * at every release; a later busy phase discards the reading. Frames after the answer are not counted.
         */
        fun busyChanged(busy: Boolean) {
            if (!observing) return
            windowAtRelease = null; stageAtRelease = null; releasedAtMs = -1L
            if (busy) return
            val observedMs = nowMs() - handoverAt
            releasedAtMs = observedMs
            windowAtRelease = windowFrames.snapshot(observedMs)
            if (stageProbeStarted) host.stage()?.frameProbe("read") { if (releasedAtMs == observedMs) stageAtRelease = it }
        }

        override fun nowMs(): Long = SystemClock.elapsedRealtime()
        override fun sleep(ms: Long) = Thread.sleep(ms)

        override fun stopReason(): HeatStop? = when {
            running.ownerStop -> HeatStop(HeatStop.OWNER_STOP)
            !host.foreground() -> HeatStop(HeatStop.LEFT_FOREGROUND, "Continuum war nicht mehr im Vordergrund.")
            else -> null
        }

        override fun blocker(): String? = onUi { this@HeatMeasurementController.blocker() }

        override fun facts(): JSONObject = onUi {
            val metrics = activity.resources.displayMetrics
            val configuration = activity.resources.configuration
            val resolver = activity.contentResolver
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("androidRelease", Build.VERSION.RELEASE).put("androidSdk", Build.VERSION.SDK_INT)
                .put("appId", BuildConfig.APPLICATION_ID).put("appVersion", BuildConfig.VERSION_NAME)
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("displayWidthPx", metrics.widthPixels).put("displayHeightPx", metrics.heightPixels)
                .put("densityDpi", metrics.densityDpi).put("smallestWidthDp", configuration.smallestScreenWidthDp)
                .put("refreshRateHz", refreshRate()?.let { HeatHistogram.round(it.toDouble()) } ?: JSONObject.NULL)
                // The hinge posture would need the window library; the display size above tells inner from cover screen.
                .put("foldState", HeatMeasurement.NOT_AVAILABLE)
                .put("brightnessMode", when (runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) }.getOrNull()) {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> "automatisch"
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> "manuell"
                    else -> JSONObject.NULL
                })
                .put("brightness", runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull() ?: JSONObject.NULL)
                .put("speechOutput", host.speechOutputOn())
                .put("htmlStage", host.stage() != null)
                .put("stageProfile", host.stageProfile())
                .put("dialogueReuseSwitch", host.dialogueReuseOn())
                .put("coolingMode", host.coolingMode())
                .put("loadedModel", model() ?: JSONObject.NULL)
        } ?: JSONObject()

        override fun sample(): HeatSample = sensors.sample()

        override fun model(): JSONObject? {
            val runtime = host.runtime()
            val loaded = runtime.loadedModel ?: return null
            val published = when {
                loaded.id == ContinuumSelectedModels.LOCAL_ID && loaded.sha256 == ContinuumSelectedModels.LOCAL_SHA256 ->
                    ContinuumSelectedModels.LOCAL_FILENAME
                loaded.id == ContinuumSelectedModels.FAST_ID && loaded.sha256 == ContinuumSelectedModels.FAST_SHA256 ->
                    ContinuumSelectedModels.FAST_FILENAME
                else -> null
            }
            return JSONObject().put("id", loaded.id).put("version", loaded.version).put("sha256", loaded.sha256)
                .put("bytes", loaded.byteCount)
                // The import keeps no file name; it is stated only when the SHA-256 is the published file's.
                .put("fileName", published ?: JSONObject.NULL)
                .put("backend", runtime.activeBackend?.name ?: JSONObject.NULL)
                .put("threads", runtime.computeThreads ?: JSONObject.NULL)
                .put("contextTokens", runtime.contextTokens ?: JSONObject.NULL)
                .put("backendNotice", runtime.backendNotice?.take(200) ?: JSONObject.NULL)
        }

        override fun unavailable(configuration: HeatConfiguration): String? = when {
            configuration.backend == HeatBackendChoice.GPU ->
                "Der GPU-Beschleuniger ist experimentell und auf diesem Gerät von der App nie gemessen worden. " +
                    "Diese Messung schaltet ihn nicht um, weil sie dafür eine gespeicherte Einstellung ändern müsste."
            configuration.stage != HeatStageMode.RESTING && onUi { host.stage() } == null ->
                "Die Command03-Bühne ist nicht die sichtbare Ansicht; der Bühnenfaktor lässt sich hier nicht schalten."
            configuration.model == HeatModelChoice.GEMMA_E2B && fastModel() == null ->
                "Gemma 4 E2B ist nicht importiert (Modellzentrum → KLEINER · GEMMA-4-E2B-IMPORT VORAUSFÜLLEN)."
            else -> null
        }

        private fun fastModel(): ImportedLocalModel? =
            runCatching { host.importedModels() }.getOrDefault(emptyList()).firstOrNull { it.id == ContinuumSelectedModels.FAST_ID }

        override fun applyStage(mode: HeatStageMode): Boolean = onUi {
            val stage = host.stage()
            if (stage == null) mode == HeatStageMode.RESTING
            else {
                running.ledger.changed("Bühne") {
                    onUi { stageFullEffects = false; host.stage()?.visibility = View.VISIBLE; stage.visibility = View.VISIBLE; host.pushStageState() }
                }
                // „Aus“: the page is invisible, so the WebView stops producing frames. „Volle Bewegung“: the stage
                // gets the flag a cloud brain sends and keeps moving while the device computes.
                stage.visibility = if (mode == HeatStageMode.OFF) View.INVISIBLE else View.VISIBLE
                stageFullEffects = mode == HeatStageMode.FULL_EFFECTS
                host.pushStageState()
                true
            }
        } ?: false

        override fun applyDialogueReuse(on: Boolean): Boolean = onUi {
            val before = host.dialogueReuseOn()
            if (before != on) {
                running.ledger.changed("Gespräch im Modell behalten") { onUi { host.overrideDialogueReuse(before) } }
                host.overrideDialogueReuse(on)
            }
            true
        } ?: false

        override fun swapModel(choice: HeatModelChoice): List<HeatLifecycleStep> {
            val target = if (choice == HeatModelChoice.BASELINE) running.originalModel
                else fastModel() ?: return listOf(HeatLifecycleStep("LOAD", 0, false, "Gemma 4 E2B ist nicht importiert."))
            val runtime = host.runtime()
            if (runtime.loadedModel == target && runtime.state == LocalModelState.READY) return emptyList()
            if (choice != HeatModelChoice.BASELINE)
                running.ledger.changed("Geladenes Modell") { check(swapModel(HeatModelChoice.BASELINE).all { it.ok }) {
                    "${running.originalModel.id} ließ sich nicht wieder laden; bitte im Modellzentrum laden." } }
            val steps = ArrayList<HeatLifecycleStep>()
            if (runtime.loadedModel != null || runtime.state == LocalModelState.READY)
                steps += timed("UNLOAD") { done -> runtime.cancel(); runtime.unload { done(it.exceptionOrNull()?.let(::message)) } }
            if (steps.all { it.ok }) {
                // The load admission may refuse a warm device; waiting for it is its own step, not load time.
                val waitStart = nowMs()
                var refusal = LocalModelLoadAdmission.refusal(target, activity.localModelResourceSnapshot())
                while (refusal == LocalModelLoadAdmission.REFUSED_THERMAL && nowMs() - waitStart < running.plan.limits.cooldownMaxMs) {
                    Thread.sleep(5_000L)
                    refusal = LocalModelLoadAdmission.refusal(target, activity.localModelResourceSnapshot())
                }
                if (nowMs() - waitStart >= 5_000L) steps += HeatLifecycleStep("WAIT_FOR_LOAD_ADMISSION", nowMs() - waitStart, refusal == null, refusal)
                steps += if (refusal != null) HeatLifecycleStep("LOAD", 0, false, refusal)
                    else timed("LOAD") { done -> runtime.load(target.id, target.version) { done(it.exceptionOrNull()?.let(::message)) } }
            }
            onUi { host.modelChanged() }
            return steps
        }

        private fun message(failure: Throwable): String = failure.message ?: failure.javaClass.simpleName

        /** Runs one lifecycle call and waits for its callback; [call] reports null for success or the failure. */
        private fun timed(action: String, call: ((String?) -> Unit) -> Unit): HeatLifecycleStep {
            val started = nowMs()
            val failure = AtomicReference<String?>(null)
            val done = CountDownLatch(1)
            val begun = runCatching { call { problem -> failure.set(problem); done.countDown() } }
            if (begun.isFailure) return HeatLifecycleStep(action, nowMs() - started, false, message(begun.exceptionOrNull()!!))
            if (!done.await(MODEL_LIFECYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                return HeatLifecycleStep(action, nowMs() - started, false, "Keine Rückmeldung nach ${MODEL_LIFECYCLE_TIMEOUT_MS / 1_000} s.")
            return HeatLifecycleStep(action, nowMs() - started, failure.get() == null, failure.get())
        }

        override fun freshChat(): Boolean {
            val before = onUi {
                running.ledger.changed("Chat") { onUi { if (host.sessionId() != running.originalChat) host.selectChat(running.originalChat) } }
                host.sessionId().also { host.newChat() }
            } ?: return false
            val deadline = nowMs() + running.plan.limits.freshChatWaitMs
            while (nowMs() < deadline) {
                if (onUi { host.sessionId() != before && !host.busy() } == true) return true
                Thread.sleep(100L)
            }
            return false
        }

        override fun beginTurn(text: String) {
            sensors.collectChanges()
            windowFrames.start(refreshRate()?.takeIf { it > 0f }?.let { 1_000.0 / it })
            stageProbeStarted = stageProbe("start") != null
            onUi {
                recorder.begin()
                handoverAt = nowMs()
                windowAtRelease = null; stageAtRelease = null; releasedAtMs = -1L
                observing = true
                running.injecting = true
                running.pendingText = text.trim()
                try { host.submit(text) } finally { running.injecting = false }
            }
        }

        // The busy flag is read on the thread its changes arrive on, so flag and reported changes always agree.
        override fun turnEnded(): Boolean =
            onUi(2_000L) { recorder.poll(host.busy()) == HeatTurnRecorder.State.ENDED } ?: false

        override fun abortTurn(timeout: Boolean) {
            if (timeout) recorder.timeout() else recorder.ownerStop()
            onUi { if (host.busy()) { running.selfAbort = true; try { host.stopTurn() } finally { running.selfAbort = false } } }
        }

        override fun endTurn(): HeatTurnObservation {
            val observedMs = nowMs() - handoverAt
            val stopped = if (stageProbeStarted) stageProbe("stop") else null
            val released = onUi { observing = false; Triple(releasedAtMs, windowAtRelease, stageAtRelease) }
            val stoppedWindow = windowFrames.stop(observedMs)
            val releasedMs = released?.first?.takeIf { it >= 0 }
            val window = released?.second ?: stoppedWindow
            val stageRaw = released?.third
            val stage = when {
                !stageProbeStarted -> HeatFrameAccumulator.notAvailable(if (onUi { host.stage() } == null)
                    "Die Command03-Bühne ist nicht die sichtbare Ansicht." else "Die Bühne hat keinen Bildzähler gemeldet.")
                releasedMs != null && stageRaw != null -> HeatStageFrames.parse(stageRaw, releasedMs)
                else -> HeatStageFrames.parse(stopped, observedMs)
            }
            recorder.forceEnd()
            running.pendingText = null
            val result = recorder.result()
            if (result.outcome == HeatTurnRecorder.NOT_ACCEPTED) onUi { host.clearInput() }
            // Status changes after the turn was released belong to the cool-down, not to this answer.
            val until = handoverAt + (result.totalMs ?: observedMs)
            return HeatTurnObservation(result, handoverAt, sensors.takeChanges().filter { it.first <= until }, window, stage)
        }

        /** Asks the page's frame counter; null when there is no stage or it did not answer. */
        private fun stageProbe(command: String): String? {
            val answer = AtomicReference<String?>(null)
            val done = CountDownLatch(1)
            val asked = onUi {
                val stage = host.stage()
                if (stage == null) false else { stage.frameProbe(command) { answer.set(it); done.countDown() }; true }
            } ?: false
            if (!asked || !done.await(3_000L, TimeUnit.MILLISECONDS)) return null
            return answer.get()
        }

        override fun speaking(): Boolean = onUi(2_000L) { host.speaking() } ?: false

        override fun restore(): List<HeatRestoreStep> {
            // A measurement that ended inside a turn (failure, stop) must not leave its observers behind.
            if (observing) {
                observing = false
                runCatching { windowFrames.stop(0L) }
                if (stageProbeStarted) runCatching { stageProbe("stop") }
                runCatching { sensors.takeChanges() }
                recorder.forceEnd()
                running.pendingText = null
            }
            val steps = running.ledger.restoreAll()
            stageFullEffects = false
            return steps
        }

        override fun progress(line: String) {
            ui.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                overlay?.show(line)
                host.status(line)
            }
        }

        /** The report so far stays in the app's own storage, so that a measurement that was cut off keeps its runs. */
        override fun checkpoint(report: JSONObject) {
            val file = checkpointFile()
            file.parentFile?.mkdirs()
            file.writeText(report.toString(), Charsets.UTF_8)
        }

        private fun refreshRate(): Float? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display?.refreshRate
            else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.refreshRate
        }.getOrNull()
    }

    companion object {
        const val MODEL_LIFECYCLE_TIMEOUT_MS = 180_000L
    }
}

/** Android's thermal status, thermal headroom and battery values. Reads only; needs no permission. */
internal class HeatSensors(context: Context) {
    private val app = context.applicationContext
    private val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val changes = ArrayList<Pair<Long, Int>>()
    private var collecting = false
    private var listener: Any? = null

    fun thermalStatus(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { power?.currentThermalStatus }.getOrNull() else null

    fun sample(): HeatSample {
        val battery = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val tenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        // Android throttles headroom queries; a NaN answer is „not reported“, never a value.
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            runCatching { power?.getThermalHeadroom(0) }.getOrNull()?.takeIf { it.isFinite() }?.toDouble() else null
        return HeatSample(
            thermalStatus = thermalStatus(),
            headroom = headroom?.let(HeatHistogram::round),
            batteryTempC = if (tenths == Int.MIN_VALUE) null else tenths / 10.0,
            batteryPercent = if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt() else null,
            charging = if (plugged < 0) null else plugged != 0,
            powerSave = runCatching { power?.isPowerSaveMode }.getOrNull())
    }

    /** Registers Android's thermal status listener for the measurement; every change is kept with its time. */
    fun listen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || listener != null) return
        val added = PowerManager.OnThermalStatusChangedListener { status ->
            synchronized(changes) { if (collecting && changes.size < 512) changes += SystemClock.elapsedRealtime() to status }
        }
        if (runCatching { power?.addThermalStatusListener({ it.run() }, added) }.isSuccess) listener = added
    }

    fun collectChanges() = synchronized(changes) { changes.clear(); collecting = true }

    fun takeChanges(): List<Pair<Long, Int>> = synchronized(changes) { collecting = false; changes.toList().also { changes.clear() } }

    fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            (listener as? PowerManager.OnThermalStatusChangedListener)?.let { runCatching { power?.removeThermalStatusListener(it) } }
        listener = null
    }
}

/** Frame times of the app window as Android reports them (FrameMetrics.TOTAL_DURATION), collected off the UI thread. */
internal class HeatWindowFrames(private val activity: Activity) {
    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var accumulator: HeatFrameAccumulator? = null
    private var failure: String? = null

    fun start(vsyncMs: Double?) {
        failure = null
        val collected = HeatFrameAccumulator(vsyncMs)
        val worker = HandlerThread("continuum-heat-frames").apply { start() }
        val added = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            collected.frame(SystemClock.elapsedRealtime(), metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0, dropped)
        }
        val registered = runCatching {
            val ready = CountDownLatch(1)
            activity.runOnUiThread {
                // Android reports frame times only for a hardware-accelerated window; without one nothing would
                // ever arrive, and that must not look like a measured zero.
                if (!activity.window.decorView.isHardwareAccelerated) failure = "Das Fenster ist nicht hardwarebeschleunigt; Android liefert dafür keine Bildzeiten."
                else runCatching { activity.window.addOnFrameMetricsAvailableListener(added, Handler(worker.looper)) }
                    .onFailure { failure = it.message ?: it.javaClass.simpleName }
                ready.countDown()
            }
            ready.await(5, TimeUnit.SECONDS) && failure == null
        }.getOrDefault(false)
        if (!registered) { worker.quitSafely(); failure = failure ?: "Android hat die Bildzeitmessung nicht angenommen."; return }
        thread = worker; listener = added; accumulator = collected
    }

    /** The frames so far without stopping; null when nothing is being collected. */
    fun snapshot(observedMs: Long): JSONObject? = accumulator?.toJson(observedMs)

    fun stop(observedMs: Long): JSONObject {
        val added = listener
        val collected = accumulator
        if (added != null) activity.runOnUiThread { runCatching { activity.window.removeOnFrameMetricsAvailableListener(added) } }
        thread?.quitSafely()
        thread = null; listener = null; accumulator = null
        return collected?.toJson(observedMs)
            ?: HeatFrameAccumulator.notAvailable(failure ?: "Die Bildzeitmessung des Fensters lief nicht.")
    }
}

/** One static line and a STOPP button above the stage. It draws nothing by itself while a turn runs. */
private class Overlay(private val activity: Activity, private val onStop: () -> Unit) {
    private val label = TextView(activity).apply {
        setTextColor(Color.rgb(226, 236, 255)); textSize = 12f
        setPadding(dp(12), dp(8), dp(8), dp(8))
    }
    private val view = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.argb(232, 6, 16, 32))
        fitsSystemWindows = true
        tag = "continuum-heat-measurement-overlay"
        addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(activity).apply {
            text = "STOPP"
            contentDescription = "Wärmemessung stoppen"
            setOnClickListener { onStop() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    /** Also re-attaches the line after the Activity rebuilt its layout (fold, design switch). */
    fun show(line: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (view.parent == null) activity.addContentView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        if (label.text.toString() != line) label.text = line
    }

    fun remove() { (view.parent as? android.view.ViewGroup)?.removeView(view) }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
