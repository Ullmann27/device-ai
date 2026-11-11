package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import at.heinz.deviceai.cognitive.InMemoryMemoryStore
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Activity; controlled inference boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerObjectStateConversationUiTest {
    private class Runtime(ready: Boolean = true, modelPresent: Boolean = ready) : LocalModelRuntime {
        @Volatile override var state = if (ready) LocalModelState.READY else LocalModelState.EMPTY
        override val loadedModel = if (!modelPresent) null else ImportedLocalModel(
            "planning-context-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        private val pending = AtomicReference<LocalInferenceCallback?>()
        val running: Boolean get() = pending.get() != null
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            check(pending.compareAndSet(null, callback)) { "Concurrent inference" }
            state = LocalModelState.GENERATING
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            error("Unexpected tool continuation")
        fun answer(text: String) {
            val callback = checkNotNull(pending.getAndSet(null))
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.TextDelta(text))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
        }
        override fun cancel() {
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.Cancelled)
        }
    }

    @Before fun configure() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        LocalDialogueRetention.context.clear()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit()
            .putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun context(activity: AgentActivity) = field(activity, "localConversationContext") as LocalConversationContext
    private fun session(activity: AgentActivity) = field(activity, "sessionId") as String
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun until(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(label, condition())
    }
    private fun withActivity(runtime: Runtime = Runtime(), body: (AgentActivity, Runtime) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            repeat(2) { advance() }
            controller.windowFocusChanged(true)
            val activity = controller.get()
            AgentActivity::class.java.getDeclaredField("localRuntime").apply { isAccessible = true }.set(activity, runtime)
            repeat(2) { advance() }
            assertNotNull("Activity recorder connected", context(activity).onTurnRecorded)
            body(activity, runtime)
        } finally {
            runtime.cancel()
            runCatching { until("request released") { !(field(controller.get(), "busy") as Boolean) } }
            runCatching { controller.close() }
            AgentActivity.chatTranscriptIo.submit {}.get(2, TimeUnit.SECONDS)
            LocalDialogueRetention.context.clear()
        }
    }
    private fun submit(activity: AgentActivity, request: String) {
        val chat = JarvisHomeTestRoutes.destination(activity.window.decorView, "chat")
        assertNotNull("Chat entry exists", chat)
        JarvisHomeTestRoutes.touch(activity.window.decorView, chat)
        (field(activity, "input") as EditText).setText(request)
        assertTrue((field(activity, "sendButton") as Button).performClick())
    }
    private fun stored(activity: AgentActivity): List<LocalChatTranscriptStore.ContextTurn> {
        AgentActivity.chatTranscriptIo.submit {}.get(2, TimeUnit.SECONDS)
        return LocalChatTranscriptStore(File(activity.filesDir, "chat-history")).loadContextTurns(session(activity))
    }
    private fun visibleReply(activity: AgentActivity): String {
        AgentActivity.chatTranscriptIo.submit {}.get(2, TimeUnit.SECONDS)
        return LocalChatTranscriptStore(File(activity.filesDir, "chat-history")).load(session(activity))
            .last { !it.user }.text
    }
    private fun modelTurn(activity: AgentActivity, runtime: Runtime, request: String, answer: String): String {
        submit(activity, request)
        until("model received: $request; visible=${visibleReply(activity)}") { runtime.running }
        val prompt = runtime.prompts.last()
        runtime.answer(answer)
        until("model turn completed") { !(field(activity, "busy") as Boolean) }
        return prompt
    }
    private fun recall(activity: AgentActivity, runtime: Runtime, question: String, expected: String) {
        val calls = runtime.prompts.size
        submit(activity, question)
        until("recall delivered") { !(field(activity, "busy") as Boolean) }
        assertEquals("Recall requires no inference", calls, runtime.prompts.size)
        assertTrue(visibleReply(activity), visibleReply(activity).contains(expected))
    }

    @Test fun fiveStepCorrectionUsesActualOfflineEntryAndOwnerPerspective() = withActivity { activity, runtime ->
        assertEquals(InternetAccessMode.OFF, (field(activity, "internetConsent") as InternetConsentPolicy).mode)
        val memory = field(activity, "memoryStore") as InMemoryMemoryStore
        val priorMemories = memory.all()
        modelTurn(activity, runtime, FIRST, "Deine Angaben sind im Gespräch verfügbar.")
        recall(activity, runtime, "Welche Farbe hat meine Werkzeugkiste?", "Farbe: blau")
        recall(activity, runtime, "Wie viele Schrauben sind darin?", "Anzahl Schrauben: 12")
        recall(activity, runtime, "Wo steht sie?", "Ort: in der Garage")
        val firstCorrection = modelTurn(activity, runtime, CORRECTION, "Die neue Anzahl und der Standort sind berücksichtigt.")
        assertTrue(firstCorrection, firstCorrection.contains("Anzahl Schrauben 17"))
        assertTrue(firstCorrection, firstCorrection.contains("Ort im Keller"))
        val secondCorrection = modelTurn(activity, runtime, FINAL_CORRECTION, "Die letzte Korrektur ist berücksichtigt.")
        assertTrue(secondCorrection, secondCorrection.contains("Farbe blau"))
        assertTrue(secondCorrection, secondCorrection.contains("Anzahl Schrauben 21"))
        assertTrue(secondCorrection, secondCorrection.contains("Ort im Keller"))
        assertTrue(secondCorrection, secondCorrection.contains("Internetmodus: OFF"))
        assertFalse(context(activity).sessionEvidence(session(activity))!!.contains(FIRST))
        runtime.state = LocalModelState.EMPTY
        recall(activity, runtime, FINAL_QUESTION, "Anzahl Schrauben: 21")
        val answer = visibleReply(activity)
        assertTrue(answer, answer.startsWith("Laut deinen Angaben in diesem Gespräch"))
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
        assertFalse(answer, answer.contains("Zugriff"))
        assertFalse(answer, answer.contains("keine dauerhaft gespeicherte Erinnerung"))
        assertEquals(3, runtime.prompts.size)
        assertEquals(priorMemories, memory.all())
        val recorded = stored(activity).last()
        assertEquals(FINAL_QUESTION, recorded.request)
        assertFalse(recorded.modelResponse)
        assertTrue(recorded.response.contains("eigene Angaben aus diesem Gespräch"))
    }

    @Test fun ordinaryRelatedTaskReachesModelWithBoundedCurrentState() = withActivity { activity, runtime ->
        val context = context(activity)
        val session = session(activity)
        val objects = listOf("Werkzeugkiste", "Reisetasche", "Vorratsdose", "Einkaufskorb", "Schublade", "Bücherregal")
        for (noun in objects) context.record(session,
            "Ich habe eine blaue $noun mit 10 Schrauben, 20 Muttern, 30 Ringen und 40 Kabeln im Keller.",
            "Gesprächsdaten erhalten.")
        for (turn in 1..24) for (noun in objects) {
            val color = if (turn % 2 == 0) "dunkelblau" else "dunkelgrün"
            val place = "Elektroinstallationsmateriallager" + ('A'.code + turn).toChar()
            context.record(session, "Die $noun ist jetzt $color. Die $noun steht jetzt im $place. " +
                "Die $noun hat jetzt ${100 + turn} Schrauben, ${200 + turn} Muttern, " +
                "${300 + turn} Ringe und ${400 + turn} Kabel.", "Gesprächsdaten erhalten.")
        }
        context.record(session, "Die Werkzeugkiste enthält vielleicht 125 Schrauben.", "Die Zahl bleibt unsicher.")
        val request = "Nenne drei Ideen für meine Werkzeugkiste."
        val prompt = modelTurn(activity, runtime, request,
            "Drei Ideen: Schrauben sortieren, Fächer beschriften und eine Bestandsliste führen.")
        assertEquals(1, runtime.prompts.size)
        assertTrue("Complete owner request", prompt.contains(request))
        assertTrue(prompt, prompt.contains("Farbe dunkelblau"))
        assertTrue(prompt, prompt.contains("Anzahl Schrauben 125 (unsicher: unsicher formuliert)"))
        assertTrue(prompt, prompt.contains("Anzahl Muttern 224"))
        assertTrue(prompt, prompt.contains("Ort im ElektroinstallationsmateriallagerY"))
        assertTrue(prompt, prompt.contains("Änderungsverläufe für diese Anfrage ausgelassen"))
        assertFalse(prompt.contains("früherer Verlauf nicht mehr vollständig →"))
        assertTrue("UTF-8 budget", prompt.toByteArray(Charsets.UTF_8).size <=
            at.heinz.deviceai.cognitive.LocalChatPromptBudget.FULL_CHARACTERS)
    }

    @Test fun actualNewSessionClearsThePreviousOwnerObjectState() = withActivity { activity, runtime ->
        modelTurn(activity, runtime, FIRST, "Deine Angaben sind im Gespräch verfügbar.")
        recall(activity, runtime, "Welche Farbe hat meine Werkzeugkiste?", "Farbe: blau")
        val oldSession = session(activity)
        AgentActivity::class.java.getDeclaredMethod("newSession").apply { isAccessible = true }.invoke(activity)
        until("new session created") { session(activity) != oldSession && !(field(activity, "busy") as Boolean) }
        assertNull(context(activity).objectStateAnswer(oldSession, "Welche Farbe hat die Werkzeugkiste?"))
        assertNull(context(activity).objectStateAnswer(session(activity), "Welche Farbe hat die Werkzeugkiste?"))
        assertEquals("", context(activity).objectStateBlock(session(activity), FINAL_QUESTION))
    }

    private companion object {
        const val FIRST = "Ich habe eine blaue Werkzeugkiste mit zwölf Schrauben in der Garage. " +
            "Merk dir diese Angaben für unser Gespräch, ich frage dich später danach."
        const val CORRECTION = "Nicht zwölf, sondern siebzehn; im Keller statt Garage; Farbe unverändert."
        const val FINAL_CORRECTION = "Nicht siebzehn, sondern neunzehn; nein, neunzehn auch nicht, es sind einundzwanzig; " +
            "Farbe und Standort bleiben gleich."
        const val FINAL_QUESTION = "Ich brauche keine externe Überprüfung. Beziehe dich ausschließlich auf unser bisheriges " +
            "Gespräch. Welche Farbe hat meine Werkzeugkiste, wie viele Schrauben sind nach allen Korrekturen darin " +
            "und wo steht sie?"
    }
}
