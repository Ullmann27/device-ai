package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.evaluation.HeatLimits
import at.heinz.deviceai.evaluation.HeatMeasurement
import at.heinz.deviceai.evaluation.HeatMeasurementController
import at.heinz.deviceai.evaluation.HeatPlan
import at.heinz.deviceai.evaluation.HeatReport
import at.heinz.deviceai.evaluation.HeatStop
import at.heinz.deviceai.evaluation.HeatTurnRecorder
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalDialogueReuse
import at.heinz.deviceai.localmodel.LocalDialogueReuseHost
import at.heinz.deviceai.localmodel.LocalDialogueRequest
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The owner-started „Wärme- und Zeitmessung“ through the real Activity under Robolectric: the chat command and the
 * model-centre entry, the confirmation, the fixed orders through the app's own send path and local turn lifecycle
 * with a stand-in runtime, the thin hooks, the report files, STOPP and the restore. HOST: no model ran, Robolectric
 * reports no thermal status and no battery temperature, and every time in the written report is a host time – this
 * test proves the wiring, never a device value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeatMeasurementActivityUiTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    /** The result message; the start message of the same measurement reads „… (kurz) gestartet“. */
    private val RESULT = "WÄRME- UND ZEITMESSUNG (kurz) · "

    /** Answers at once like a loaded Gemma 4 E4B would be addressed; keeps one dialogue like the LiteRT-LM runtime. */
    private class Runtime : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel(ContinuumSelectedModels.LOCAL_ID, ContinuumSelectedModels.LOCAL_VERSION,
            ContinuumSelectedModels.LOCAL_SHA256, 1, LocalModelCapabilities(true, false, false, false, true, 32_768, null,
                setOf(LocalModelBackend.CPU)))
        override val contextTokens: Int? = 4_096
        override val activeBackend: LocalModelBackend? = LocalModelBackend.CPU
        override val computeThreads: Int? = 4
        val entries = ArrayList<String>()
        var lifecycleCalls = 0
        var cancels = 0
        @Volatile var hold: java.util.concurrent.CountDownLatch? = null
        private var kept: String? = null

        private fun reply(sent: String, callback: LocalInferenceCallback) {
            hold?.await(30, TimeUnit.SECONDS)
            // A fitting answer per order, so that the app's own answer check has no reason to ask the model again.
            val answer = if ("am Abend rot" in sent) EVENING else ANSWER
            callback.onEvent(LocalInferenceEvent.TextDelta(answer.take(20)))
            callback.onEvent(LocalInferenceEvent.TextDelta(answer.drop(20)))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, answer.length, 2, null, null, 1, 2)))
        }

        override fun streamDialogue(request: LocalDialogueRequest, callback: LocalInferenceCallback) {
            val reuse = kept == request.conversationKey && request.followUp != null
            request.receipt.dispatched(reuse, if (reuse || request.followUp == null) null
                else LocalDialogueReuse.RUNTIME_NO_KEPT_CONVERSATION, if (reuse) 900 else null)
            entries += if (reuse) "FOLLOW_UP" else "DIALOGUE_FRESH"
            kept = request.conversationKey
            request.receipt.retained(1_000)
            reply(if (reuse) request.followUp.orEmpty() else request.freshPrompt, callback)
        }
        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) { kept = null; entries += "TEXT_ONLY"; reply(prompt, callback) }
        override fun stream(prompt: String, callback: LocalInferenceCallback) { kept = null; entries += "TOOL_PATH"; reply(prompt, callback) }
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
            callback.onEvent(LocalInferenceEvent.Failed("No isolated thinking in this fixture."))
        override fun releaseDialogue() { kept = null }
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) { lifecycleCalls++ }
        override fun unload(callback: (Result<Unit>) -> Unit) { lifecycleCalls++ }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = error("No tool in fixture")
        override fun cancel() { cancels++; hold?.countDown() }

        companion object {
            const val ANSWER = "Das Sonnenlicht wird in der Luft gestreut, und das blaue Licht am stärksten. Darum sieht der Himmel am Tag blau aus."
            const val EVENING = "Am Abend legt das Licht einen längeren Weg durch die Luft zurück, dabei bleibt vor allem das rote Licht übrig."
        }
    }

    @Before fun prepare() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit().putBoolean("original_html_main", false).commit()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        reportDirectory().listFiles()?.forEach { it.delete() }
    }

    private fun reportDirectory() = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(app.filesDir, "Download")
    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun texts(view: View) = all(view).filterIsInstance<TextView>().map { it.text.toString() }
    private fun chat(a: AgentActivity) = texts(field(a, "chatContainer") as LinearLayout)
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(message: String, seconds: Long = 60, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(2) }
        assertTrue(message, condition())
    }
    private fun measurement(a: AgentActivity) = field(a, "heatMeasurement") as HeatMeasurementController
    private fun overlay(a: AgentActivity): View? = all(a.window.decorView).firstOrNull { it.tag == "continuum-heat-measurement-overlay" }
    private fun send(a: AgentActivity, text: String) {
        (field(a, "input") as EditText).setText(text)
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(a, false)
        advance()
    }
    private fun objects(array: JSONArray) = (0 until array.length()).map(array::getJSONObject)

    private fun withActivity(block: (AgentActivity, Runtime) -> Unit) {
        val controller: ActivityController<AgentActivity> = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            val a = controller.get()
            await("Activity is foreground") { field(a, "foregroundLearningActive") == true }
            val runtime = Runtime()
            set(a, "localRuntime", runtime)
            // The waits of the protocol are shortened for the host; order, orders and configurations stay the app's.
            measurement(a).planOf = { size, prompts -> HeatPlan(size, prompts, HeatMeasurement.CONFIGURATIONS, HeatLimits(
                turnTimeoutMs = 30_000L, cooldownMinMs = 0L, cooldownMaxMs = 0L, settleMs = 0L, sampleIntervalMs = 40L,
                pollIntervalMs = 10L, speechWaitMaxMs = 0L, freshChatWaitMs = 20_000L)) }
            block(a, runtime)
        } finally {
            runCatching { measurement(controller.get()).requestStop() }
            runCatching { await("measurement ended", 20) { !measurement(controller.get()).active } }
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }

    private fun savedReport(): JSONObject {
        val files = reportDirectory().listFiles().orEmpty().sortedBy { it.name }
        assertEquals(files.map { it.name }.toString(), 2, files.size)
        assertTrue(files[0].name.matches(Regex("continuum-waermemessung-kurz-\\d{8}-\\d{6}\\.json")))
        assertEquals(files[0].name.removeSuffix(".json") + ".md", files[1].name)
        val report = HeatReport.read(files[0].readText(Charsets.UTF_8))
        assertEquals("The readable file is the report's own text", HeatReport.markdown(report), files[1].readText(Charsets.UTF_8))
        return report
    }

    @Test fun theChatCommandRunsTheFixedOrdersThroughTheRealTurnPathWritesBothFilesAndPutsEverythingBack() = withActivity { a, runtime ->
        val chatBefore = field(a, "sessionId") as String
        assertFalse(LocalDialogueReuseHost.enabled(app))
        assertEquals(0, a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        send(a, "Continuum Wärmemessung kurz")
        assertTrue("The command is consumed, not sent to the model", (field(a, "input") as EditText).text.isEmpty())
        assertTrue(runtime.entries.isEmpty())
        val confirm = ShadowAlertDialog.getLatestAlertDialog()
        val announced = texts(confirm.window!!.decorView).joinToString("\n")
        assertTrue(announced, "Wärme- und Zeitmessung (kurz): 6 Konfigurationen, 6 Läufe, bis zu 12 Modellantworten." in announced)
        assertTrue(announced, "Erwartete Dauer: unbekannt" in announced)
        assertTrue(announced, HeatMeasurement.INPUT_NOTE in announced)
        assertFalse("Nothing starts before the owner confirmed", measurement(a).active)
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advance()
        assertTrue(measurement(a).active)
        assertTrue("The start is written into the owner's chat", chat(a).any { it.startsWith("WÄRME- UND ZEITMESSUNG (kurz) gestartet: 6 Läufe") })
        assertNotNull("The line with STOPP is shown", overlay(a))
        assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)

        await("the measurement finished", 120) { !measurement(a).active && chat(a).any { it.startsWith(RESULT) } }
        val summary = chat(a).last { it.startsWith(RESULT) }
        val report = savedReport()
        val names = reportDirectory().listFiles().orEmpty().map { it.name }.sorted()
        assertTrue(summary, "abgeschlossen" in summary && HeatMeasurement.INPUT_NOTE in summary)
        assertTrue("The chat names the real files: $summary", names.all { it in summary })

        // A and D ran; the native view has no stage to switch, no E2B is imported, the GPU is never switched.
        val status = objects(report.getJSONArray("configurations")).associate { it.getString("id") to it.getString("status") }
        assertEquals(mapOf("A" to "RUN", "B" to "NOT_RUN", "C" to "NOT_RUN", "D" to "RUN", "E" to "NOT_RUN", "F" to "NOT_RUN"), status)
        objects(report.getJSONArray("configurations")).filter { it.getString("status") == "NOT_RUN" }
            .forEach { assertTrue(it.getString("id"), it.getString("reason").length > 20) }
        val runs = objects(report.getJSONArray("runs"))
        assertEquals(listOf("A", "D"), runs.map { it.getString("configuration") })
        val turns = runs.flatMap { objects(it.getJSONArray("turns")) }
        assertEquals("Both orders of both runs reached the model and were answered",
            List(4) { HeatTurnRecorder.COMPLETE }, turns.map { it.getString("outcome") })
        turns.forEach { turn ->
            assertEquals(turn.toString(), 1, turn.getInt("modelCalls"))
            assertFalse("first token", turn.isNull("firstTokenMs"))
            assertFalse("first visible text", turn.isNull("firstVisibleTextMs"))
            assertTrue(turn.getString("firstVisibleTextEvent"), turn.getString("firstVisibleTextEvent") in
                setOf(HeatTurnRecorder.EVENT_NATIVE_PREVIEW, HeatTurnRecorder.EVENT_NATIVE_BUBBLE))
            assertTrue(turn.getLong("totalMs") >= turn.getLong("firstTokenMs"))
            assertTrue(turn.getLong("answerHandoverMs") <= turn.getLong("totalMs"))
            assertTrue(turn.getInt("promptCharacters") > turn.getInt("textCharacters"))
            assertEquals(if (turn.getString("role") == "ERSTFRAGE") Runtime.ANSWER.length else Runtime.EVENING.length,
                turn.getInt("generatedCharacters"))
            assertEquals(HeatMeasurement.NOT_AVAILABLE, turn.getJSONObject("stageFrames").getString("status"))
            assertTrue(turn.getJSONObject("windowFrames").has("status"))
            assertTrue(turn.getJSONArray("samples").length() >= 2)
        }
        assertEquals("The baseline never uses the kept dialogue; D keeps it and continues the follow-up",
            listOf("TEXT_ONLY", "TEXT_ONLY", "DIALOGUE_FRESH", "FOLLOW_UP"), runtime.entries)
        assertEquals(listOf(false, true), objects(runs[1].getJSONArray("turns")).map { it.getBoolean("dialogueReused") })
        runs.forEach { run ->
            val start = run.getJSONObject("startCondition")
            assertFalse("Robolectric reports no thermal status and no battery temperature: not called reached", start.getBoolean("reached"))
            assertTrue(start.getString("reason"), "nicht prüfbar" in start.getString("reason"))
        }
        assertEquals(HeatMeasurement.INPUT_NOTE, report.getJSONObject("input").getString("note"))
        val device = report.getJSONObject("device")
        assertEquals(BuildConfig.VERSION_CODE, device.getInt("appVersionCode"))
        assertEquals(false, device.getBoolean("speechOutput"))
        assertEquals(false, device.getBoolean("htmlStage"))
        assertEquals(ContinuumSelectedModels.LOCAL_FILENAME, device.getJSONObject("loadedModel").getString("fileName"))
        assertEquals("CPU", device.getJSONObject("loadedModel").getString("backend"))
        assertTrue(report.getBoolean("complete"))
        assertTrue(report.isNull("stop"))
        assertTrue(report.getBoolean("restoreComplete"))
        assertEquals("NOT_COMPARABLE", report.getJSONObject("summary").getJSONObject("costDriver").getString("status"))

        // Everything is back: no stored switch, no override, no screen flag, no line, the owner's chat, the model untouched.
        assertFalse("The stored owner switch was never written", LocalDialogueReuseHost.enabled(app))
        assertEquals(false, call(a, "dialogueReuseOn"))
        assertFalse(measurement(a).stageFullEffects)
        assertEquals(0, a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assertNull(overlay(a))
        assertEquals(chatBefore, field(a, "sessionId"))
        assertEquals("The loaded model is never swapped without an imported E2B", 0, runtime.lifecycleCalls)
        assertEquals(false, field(a, "busy"))
        assertTrue("The app has no internet permission to use here", "ohne Internet" in announced)
    }

    @Test fun stoppKeepsTheMeasuredRunAndRestoresWhileAnAnswerIsRunning() = withActivity { a, runtime ->
        val chatBefore = field(a, "sessionId") as String
        send(a, "Continuum Wärmemessung kurz")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advance()
        // The first run completes; the first answer of the second run is held open in the runtime.
        await("first run answered") { runtime.entries.size == 2 }
        runtime.hold = java.util.concurrent.CountDownLatch(1)
        await("second run is generating") { runtime.entries.size == 3 }
        assertEquals(true, call(a, "dialogueReuseOn"))
        send(a, "Wie spät ist es?")
        assertTrue("Other orders are held back while the measurement owns the lane", runtime.entries.size == 3)
        val stop = all(overlay(a)!!).filterIsInstance<Button>().single { it.text.toString() == "STOPP" }
        stop.performClick()
        await("the measurement stopped", 60) { !measurement(a).active && chat(a).any { it.startsWith(RESULT) } }
        val report = savedReport()
        assertEquals(HeatStop.OWNER_STOP, report.getJSONObject("stop").getString("reason"))
        assertFalse(report.getBoolean("complete"))
        val runs = objects(report.getJSONArray("runs"))
        assertEquals(listOf("A", "D"), runs.map { it.getString("configuration") })
        assertEquals(listOf(HeatTurnRecorder.COMPLETE, HeatTurnRecorder.COMPLETE),
            objects(runs[0].getJSONArray("turns")).map { it.getString("outcome") })
        assertEquals(listOf(HeatTurnRecorder.CANCELLED_BY_OWNER, HeatMeasurement.NOT_RUN),
            objects(runs[1].getJSONArray("turns")).map { it.getString("outcome") })
        assertTrue(runtime.cancels > 0)
        assertTrue(chat(a).last { it.startsWith(RESULT) }.contains("vorzeitig beendet (OWNER_STOP)"))
        assertTrue(report.getBoolean("restoreComplete"))
        assertEquals(false, call(a, "dialogueReuseOn"))
        assertFalse(LocalDialogueReuseHost.enabled(app))
        assertNull(overlay(a))
        assertEquals(0, a.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assertEquals(chatBefore, field(a, "sessionId"))
        assertEquals(false, field(a, "busy"))
    }

    @Test fun theRunsOfAnInterruptedMeasurementAreSavedAsAnIncompleteReportOnRequest() = withActivity { a, runtime ->
        send(a, "Continuum Wärmemessung Teilbericht")
        assertTrue(chat(a).toString(), chat(a).any { it == "WÄRME- UND ZEITMESSUNG · Es liegt kein Teilstand einer abgebrochenen Messung vor." })
        // What a measurement keeps after every run (here: synthetic host data), left behind by a closed app.
        val kept = at.heinz.deviceai.evaluation.HeatSyntheticScenarios.before().apply {
            remove("restore"); remove("restoreComplete"); remove("endedAtEpochMs"); remove("summary"); put("stop", JSONObject.NULL)
        }
        val checkpoint = File(File(a.filesDir, "heat-measurement"), "letzte-messung-teilstand.json")
        checkpoint.parentFile!!.mkdirs()
        checkpoint.writeText(kept.toString(), Charsets.UTF_8)
        send(a, "Continuum Wärmemessung Teilbericht")
        await("the kept runs were saved") { chat(a).any { it.startsWith(RESULT) } }
        val summary = chat(a).last { it.startsWith(RESULT) }
        assertTrue(summary, "vorzeitig beendet (INTERRUPTED)" in summary)
        val report = savedReport()
        assertEquals(HeatStop.INTERRUPTED, report.getJSONObject("stop").getString("reason"))
        assertFalse(report.getBoolean("complete"))
        assertFalse(report.has("restoreComplete"))
        assertEquals(5, report.getJSONArray("runs").length())
        assertTrue(reportDirectory().listFiles().orEmpty().all { it.name in summary })
        assertFalse("The kept runs are handed over once", checkpoint.exists())
        assertTrue("Nothing was measured or sent to the model for this", runtime.entries.isEmpty())
        assertFalse(measurement(a).active)
    }

    @Test fun theModelCentreEntryAsksForTheSizeAndWithoutALoadedModelNothingStarts() {
        withActivity { a, _ ->
            call(a, "showModelCenter")
            advance()
            val centre = ShadowAlertDialog.getLatestAlertDialog()
            val entry = all(centre.window!!.decorView).filterIsInstance<TextView>()
                .single { it.text.toString() == "6 · WÄRME- UND ZEITMESSUNG · KURZ ODER VOLL" }
            entry.performClick()
            advance()
            val choose = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("KURZ STARTEN", choose.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertEquals("VOLL STARTEN", choose.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
            val announced = texts(choose.window!!.decorView).joinToString("\n")
            assertTrue(announced, "Wärme- und Zeitmessung (voll): 6 Konfigurationen, 36 Läufe, bis zu 72 Modellantworten." in announced)
            choose.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            advance()
            assertFalse(measurement(a).active)
        }
        withActivity { a, _ ->
            set(a, "localRuntime", object : LocalModelRuntime {
                override val state = LocalModelState.EMPTY
                override val loadedModel: ImportedLocalModel? = null
                override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("no load")
                override fun unload(callback: (Result<Unit>) -> Unit) = error("no unload")
                override fun stream(prompt: String, callback: LocalInferenceCallback) = error("no inference")
                override fun cancel() = Unit
            })
            send(a, "Continuum Wärmemessung")
            assertTrue(chat(a).toString(), chat(a).any { it.startsWith("WÄRME- UND ZEITMESSUNG · nicht gestartet. Kein geladenes Modell im Zustand BEREIT.") })
            assertFalse(measurement(a).active)
            assertNull(overlay(a))
            assertTrue(reportDirectory().listFiles().orEmpty().isEmpty())
        }
    }
}
