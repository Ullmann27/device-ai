package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.evaluation.HeatFrameAccumulator
import at.heinz.deviceai.evaluation.HeatLimits
import at.heinz.deviceai.evaluation.HeatMeasurement
import at.heinz.deviceai.evaluation.HeatMeasurementController
import at.heinz.deviceai.evaluation.HeatPlan
import at.heinz.deviceai.evaluation.HeatReport
import at.heinz.deviceai.evaluation.HeatStageFrames
import at.heinz.deviceai.evaluation.HeatTurnRecorder
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
import at.heinz.deviceai.ui.ContinuumCommand03OriginalStage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * The stage factors of the „Wärme- und Zeitmessung“ with the Command03 stage as the main design under Robolectric:
 * „Bühne aus“ really hides the page for its run, „volle Bewegung“ really sends the page the cloud-brain flag for
 * its run, „ohne Antwortentwurf“ (G, as in Code68) really sends the page no draft for its run, „alle
 * Werkzeugschemas“ (H, as in Code68) really takes the model route with tools for its run, all are back afterwards,
 * and the page's frame counter is started, read at the release of a turn and stopped. HOST: the page itself is a stand-in that records what Android sends and answers the counter with a
 * fixed synthetic reply – no Chromium, no frame and no heat is involved here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [HeatMeasurementStageUiTest.RecordingPage::class])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeatMeasurementStageUiTest {
    /** Records every script Android hands to the page and answers the frame counter like the bridge would. */
    @Implements(WebView::class)
    class RecordingPage : Command03HostWebView() {
        @Implementation
        protected override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
            scripts += script
            val probe = Regex("frameProbe\\('(start|read|stop)'\\)").find(script)?.groupValues?.get(1)
            callback?.onReceiveValue(when (probe) {
                "start" -> "true"
                "read", "stop" -> COUNTER
                else -> "null"
            })
        }

        companion object {
            val scripts: MutableList<String> = Collections.synchronizedList(ArrayList())
            /** A synthetic reply in the counter's format: seven delivered frames, six intervals of 140.2 ms. */
            val COUNTER: String = JSONObject().put("schema", HeatStageFrames.PROBE_SCHEMA).put("delivered", 7).put("ticks", 90)
                .put("limit", 5).put("observedMs", 1).put("visibility", "visible").put("perSecond", JSONArray(listOf(7)))
                .put("limitChanges", JSONArray()).put("histogram", JSONObject().put("bucketMs", 0.5).put("bucketCount", 300)
                    .put("counts", JSONArray().put(JSONArray().put(280).put(6))).put("overflow", 0).put("maxMs", 140.2).put("sumMs", 841.2))
                .toString()
        }
    }

    private class Runtime(private val observe: () -> String) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel(ContinuumSelectedModels.LOCAL_ID, ContinuumSelectedModels.LOCAL_VERSION,
            ContinuumSelectedModels.LOCAL_SHA256, 1, LocalModelCapabilities(true, false, false, false, true, 32_768, null,
                setOf(LocalModelBackend.CPU)))
        override val contextTokens: Int? = 4_096
        override val activeBackend: LocalModelBackend? = LocalModelBackend.CPU
        /** Route and what the stage looked like while the stand-in model „computed“, one entry per model call. */
        val seen: MutableList<String> = Collections.synchronizedList(ArrayList())
        /** How many scripts the page had received when each model call began. */
        val marks: MutableList<Int> = Collections.synchronizedList(ArrayList())
        // The entry with all tool schemas; the tool-free entry below is the one a plain text question takes.
        override fun stream(prompt: String, callback: LocalInferenceCallback) = answer("TOOLS", prompt, callback)
        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) = answer("TEXT", prompt, callback)
        private fun answer(route: String, prompt: String, callback: LocalInferenceCallback) {
            marks += RecordingPage.scripts.size
            seen += route + ":" + observe()
            val answer = if ("am Abend rot" in prompt) "Am Abend legt das Licht einen längeren Weg durch die Luft zurück, dabei bleibt vor allem das rote Licht übrig."
                else "Das Sonnenlicht wird in der Luft gestreut, und das blaue Licht am stärksten. Darum sieht der Himmel am Tag blau aus."
            callback.onEvent(LocalInferenceEvent.TextDelta(answer))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, answer.length, 1, null, null, 1, 2)))
        }
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
            callback.onEvent(LocalInferenceEvent.Failed("No isolated thinking in this fixture."))
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("no load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("no unload")
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = error("no tool")
        override fun cancel() = Unit
    }

    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun prepare() {
        RecordingPage.scripts.clear()
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit().putBoolean("original_html_main", true).commit()
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
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun chat(a: AgentActivity) = all(field(a, "chatContainer") as LinearLayout).filterIsInstance<TextView>().map { it.text.toString() }
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(message: String, seconds: Long = 120, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(2) }
        assertTrue(message, condition())
    }
    private fun measurement(a: AgentActivity) = field(a, "heatMeasurement") as HeatMeasurementController
    private fun stage(a: AgentActivity) = field(a, "command03MainStage") as ContinuumCommand03OriginalStage
    private fun objects(array: JSONArray) = (0 until array.length()).map(array::getJSONObject)
    private fun states() = synchronized(RecordingPage.scripts) { RecordingPage.scripts.toList() }
        .filter { it.startsWith("window.ContinuumNative?.receive(") }

    @Test fun stageOffHidesThePageAndFullMotionSendsTheCloudFlagOnlyForTheirOwnRunsAndTheCounterIsRead() {
        val controller: ActivityController<AgentActivity> = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            val a = controller.get()
            await("Activity is foreground") { field(a, "foregroundLearningActive") == true }
            val page = stage(a)
            page.webViewClient.onPageFinished(page, ContinuumCommand03OriginalStage.INDEX)
            advance()
            val runtime = Runtime { (if (page.visibility == View.VISIBLE) "VISIBLE" else "HIDDEN") + "/" + measurement(a).stageFullEffects }
            set(a, "localRuntime", runtime)
            measurement(a).planOf = { size, prompts -> HeatPlan(size, prompts, HeatMeasurement.CONFIGURATIONS, HeatLimits(
                turnTimeoutMs = 30_000L, cooldownMinMs = 0L, cooldownMaxMs = 0L, settleMs = 0L, sampleIntervalMs = 40L,
                pollIntervalMs = 10L, speechWaitMaxMs = 0L, freshChatWaitMs = 20_000L)) }
            assertTrue("Before the measurement the page is told that the device computes itself",
                states().isNotEmpty() && states().all { "\"cloudBrain\":false" in it })

            (field(a, "input") as EditText).setText("Continuum Wärmemessung kurz")
            AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(a, false)
            advance()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            await("the measurement finished") { !measurement(a).active && chat(a).any { it.startsWith("WÄRME- UND ZEITMESSUNG (kurz) · ") } }

            val report = HeatReport.read(reportDirectory().listFiles().orEmpty().single { it.name.endsWith(".json") }.readText(Charsets.UTF_8))
            val status = objects(report.getJSONArray("configurations")).associate { it.getString("id") to it.getString("status") }
            assertEquals(mapOf("A" to "RUN", "B" to "RUN", "C" to "RUN", "D" to "RUN", "E" to "NOT_RUN", "F" to "NOT_RUN",
                "G" to "RUN", "H" to "RUN"), status)
            val runs = objects(report.getJSONArray("runs"))
            assertEquals(listOf("A", "B", "C", "D", "G", "H"), runs.map { it.getString("configuration") })
            val turns = runs.flatMap { run -> objects(run.getJSONArray("turns")).map { run.getString("configuration") to it } }
            assertEquals(List(12) { HeatTurnRecorder.COMPLETE }, turns.map { it.second.getString("outcome") })

            assertEquals("While the model computed: A resting, B hidden, C with full motion, D and G resting – all as plain " +
                "text questions; H resting, on the route with all tool schemas", listOf(
                "TEXT:VISIBLE/false", "TEXT:VISIBLE/false", "TEXT:HIDDEN/false", "TEXT:HIDDEN/false", "TEXT:VISIBLE/true", "TEXT:VISIBLE/true",
                "TEXT:VISIBLE/false", "TEXT:VISIBLE/false", "TEXT:VISIBLE/false", "TEXT:VISIBLE/false",
                "TOOLS:VISIBLE/false", "TOOLS:VISIBLE/false"), runtime.seen.toList())
            // What the page received during each model call's turn: the answer draft – except in G (as in Code68).
            val received = synchronized(RecordingPage.scripts) { RecordingPage.scripts.toList() }
            val marks = runtime.marks.toList()
            val drafts = marks.indices.map { call ->
                received.subList(marks[call], marks.getOrElse(call + 1) { received.size }).count { it.startsWith("window.ContinuumNative?.draft(") }
            }
            turns.map { it.first }.forEachIndexed { call, configuration ->
                if (configuration == "G") assertEquals("G: the page gets no draft script at all", 0, drafts[call])
                else assertTrue("$configuration: the page gets the running answer as a draft", drafts[call] >= 1)
            }
            assertFalse(measurement(a).stageDraftOff)
            assertFalse(measurement(a).toolSchemasForced)
            val flagged = states().map { "\"cloudBrain\":true" in it }
            assertTrue("The page received the cloud-brain flag during C", flagged.any { it })
            assertFalse("and no longer after it", flagged.last())
            assertEquals("The flag was sent in one stretch only", 1,
                flagged.zipWithNext().count { (before, after) -> !before && after })
            assertEquals(View.VISIBLE, page.visibility)
            assertFalse(measurement(a).stageFullEffects)

            // The page's counter: started with every turn, read when the turn was released, stopped at its end.
            val commands = synchronized(RecordingPage.scripts) { RecordingPage.scripts.toList() }
                .mapNotNull { Regex("frameProbe\\('(start|read|stop)'\\)").find(it)?.groupValues?.get(1) }
            assertEquals(12, commands.count { it == "start" })
            assertEquals(12, commands.count { it == "stop" })
            assertTrue(commands.count { it == "read" } >= 12)
            turns.forEach { (configuration, turn) ->
                val frames = turn.getJSONObject("stageFrames")
                assertEquals(configuration, HeatFrameAccumulator.MEASURED, frames.getString("status"))
                assertEquals("What the page's counter answered, unchanged", 7, frames.getInt("delivered"))
                assertEquals(140.5, frames.getDouble("intervalP50Ms"), 0.0)
                assertEquals(140.2, frames.getDouble("intervalMaxMs"), 0.0)
                assertEquals(5, frames.getInt("frameLimit"))
                // Code69: a visible stage shows the running answer as its draft row – that is the first visible text.
                // With the stage off (B) nothing is on screen; the record stays the finished answer, marked not shown.
                // Without the draft (G, as in Code68) the first text on the visible stage is the finished answer.
                val answerOnly = configuration == "B" || configuration == "G"
                assertEquals("A visible stage shows the draft; a hidden one and one without drafts only get the answer",
                    if (answerOnly) HeatTurnRecorder.EVENT_STAGE_MESSAGE else HeatTurnRecorder.EVENT_STAGE_DRAFT,
                    turn.getString("firstVisibleTextEvent"))
                assertEquals("…and nobody can see it while the stage is off", configuration != "B", turn.getBoolean("firstVisibleTextShown"))
                if (answerOnly) assertEquals(turn.getLong("answerHandoverMs"), turn.getLong("firstVisibleTextMs"))
                else assertTrue("The draft is on screen before the checked answer",
                    turn.getLong("firstVisibleTextMs") <= turn.getLong("answerHandoverMs"))
                assertEquals("Only H carries the tool schemas", configuration != "H", turn.getBoolean("toolFreeRoute"))
                assertEquals(1, turn.getInt("modelCalls"))
            }
            assertEquals(true, report.getJSONObject("device").getBoolean("htmlStage"))
            assertTrue(report.getBoolean("restoreComplete"))
            assertEquals(listOf("Modellroute (Werkzeugschemas)", "Antwortentwurf der Bühne", "Gespräch im Modell behalten", "Chat", "Bühne",
                "Thermal-Beobachter", "Messanzeige", "Bildschirm anlassen"), objects(report.getJSONArray("restore")).map { it.getString("what") })
            assertTrue(objects(report.getJSONArray("restore")).all { it.getBoolean("ok") })
            val comparisons = objects(report.getJSONObject("summary").getJSONArray("comparisons")).associateBy { it.getString("configuration") }
            assertTrue("A page that still delivers frames while it is to be off makes „Bühne aus“ not comparable: the stand-in always answers 7",
                "FACTOR_NOT_EFFECTIVE" in comparisons.getValue("B").getJSONArray("reasons").toString())
            assertFalse("G took effect: no draft was the first visible text", "FACTOR_NOT_EFFECTIVE" in comparisons.getValue("G").getJSONArray("reasons").toString())
            assertFalse("H took effect: no turn went the tool-free route", "FACTOR_NOT_EFFECTIVE" in comparisons.getValue("H").getJSONArray("reasons").toString())
            assertFalse("H is not held against the prompt-size rule", "PROMPT_SIZE_DIFFERS" in comparisons.getValue("H").getJSONArray("reasons").toString())
        } finally {
            runCatching { measurement(controller.get()).requestStop() }
            runCatching { await("measurement ended", 20) { !measurement(controller.get()).active } }
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }
}
