package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import at.heinz.deviceai.cognitive.CockpitWorkJournal
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
import at.heinz.deviceai.ui.ContinuumHomeView
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The whole owner flow in one run (owner brief 06.11.2026): document window → the real `runLocal` path → JARVIS reads the
 * document while the model works → result surface, or an honest end on failure and STOP – the cockpit stays on START.
 * HOST + SIMULATED_DOCUMENT_INPUT + CONTROLLED_MODEL (the local runtime is replaced by a test runtime that records the
 * prompt and answers when the test says so). REAL_DEVICE_NOT_RUN; no model quality is measured here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumDocumentFlowUiTest {
    private class Runtime : LocalModelRuntime {
        @Volatile override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("flow-test-model", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        private val pending = AtomicReference<LocalInferenceCallback?>()
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("The draft must reuse the loaded model")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("The draft must not unload the model")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            check(pending.compareAndSet(null, callback)) { "Concurrent inference was admitted" }
            state = LocalModelState.GENERATING
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            error("A document draft calls no tools")
        val running: Boolean get() = pending.get() != null
        fun answer(text: String) {
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.TextDelta(text))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
        }
        fun fail(message: String) {
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.Failed(message))
        }
        override fun cancel() {
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.Cancelled)
        }
    }

    private var framePump: CockpitHostFramePump? = null

    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(owner: Any, name: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            runCatching { return type!!.getDeclaredField(name).apply { isAccessible = true }.get(owner) }
            type = type.superclass
        }
        error("no field $name")
    }
    private fun call(activity: AgentActivity, name: String, vararg arguments: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == arguments.size &&
            it.parameterTypes.zip(arguments).all { (type, value) -> value == null || type.isInstance(value) ||
                type.isPrimitive } }.apply { isAccessible = true }.invoke(activity, *arguments)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(16L, left)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            left -= step
        }
    }
    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }
    private fun draw(root: View) {
        val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }
    /** A fixture page that says what it is: no owner document, no invented content. */
    private fun page() = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        val c = Canvas(this)
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 28, 44); textSize = 14f }
        c.drawText("Beispiel · Host-Testseite", 16f, 34f, ink)
        ink.textSize = 9f
        listOf("Projektübersicht Continuum.", "Ziel: ein lokales Cockpit", "auf dem Fold7.").forEachIndexed { i, line ->
            c.drawText(line, 16f, 68f + i * 18f, ink)
        }
    }

    /** HOST evidence (-PcontinuumUiEvidenceDir): Robolectric software drawing, not a device or Filament frame. */
    private fun capture(root: View, name: String) {
        val dir = System.getProperty("continuum.ui.evidenceDir") ?: return
        val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            java.io.File(dir).apply { mkdirs() }.resolve("$name.png").outputStream()
                .use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    /** Lets the real background lanes (model executor, journal I/O) finish while the main looper keeps turning. */
    private fun until(root: View, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            assertTrue("timed out waiting for: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
            advance(16L)
        }
        measure(root); draw(root)
    }

    private fun <T> withFlow(block: (AgentActivity, View, Runtime) -> T): T {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val runtime = Runtime()
        try {
            controller.setup().visible(); advance(32L); controller.windowFocusChanged(true)
            val activity = controller.get()
            AgentActivity::class.java.getDeclaredField("localRuntime").apply { isAccessible = true }.set(activity, runtime)
            val root = activity.window.decorView
            framePump = CockpitHostFramePump { root }
            (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            return block(activity, root, runtime)
        } finally {
            runtime.cancel()
            framePump?.close(); framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    private class Scene(val doc: PdfDocumentIntake.Document, val episode: String, val journal: CockpitWorkJournal)

    /** The document window is on screen and the draft runs on the real local-model path. */
    private fun startDraft(activity: AgentActivity, root: View, runtime: Runtime, name: String, text: String): Scene {
        val doc = PdfDocumentIntake.Document(name, 4_096L, 2, page(), text, PdfDocumentIntake.TextState.EXTRACTED, 2)
        call(activity, "presentDocumentScene", doc)
        advance(1_600L); measure(root); draw(root)
        val episode = field(activity, "workEpisodeId") as String
        val journal = CockpitWorkJournal(activity.filesDir)
        until(root, "the document episode is recorded") { journal.all().any { it.id == episode } }
        call(activity, "startDocumentDraft", doc)
        until(root, "the draft reaches the model") { runtime.running }
        return Scene(doc, episode, journal)
    }

    private fun windowIntent(activity: AgentActivity): Any? = field(field(activity, "avatarDirector")!!, "windowIntent")
    private fun reading(activity: AgentActivity): Boolean = windowIntent(activity)?.let { field(it, "reading") as Boolean } ?: false
    private fun status(root: View) = (descendants(root).single { it.tag == "jarvis-document-draft" && it.isShown } as TextView).text.toString()
    private fun state(scene: Scene) = scene.journal.all().single { it.id == scene.episode }.state

    @Test fun jarvisReadsTheDocumentWhileTheModelWorksAndThenShowsTheDraft() = withFlow { activity, root, runtime ->
        val text = "Projektübersicht Continuum. Ziel: ein lokales Cockpit auf dem Fold7. Offen: Gerätetest."
        val scene = startDraft(activity, root, runtime, "Projektübersicht.pdf", text)
        val prompt = runtime.prompts.single()
        assertTrue("the model gets the document's own text", prompt.contains(text))
        assertEquals("Entwurf wird lokal erstellt …", status(root))
        until(root, "the journal records the running draft") { state(scene) == CockpitWorkJournal.State.DRAFTING }
        assertTrue("JARVIS reads the document while the model works", reading(activity))
        assertEquals(at.heinz.deviceai.avatar.AvatarState.THINKING,
            (field(activity, "avatarDirector") as at.heinz.deviceai.avatar.AvatarDirector).state)
        val again = descendants(root).single { it.tag == "jarvis-document-start-draft" }
        assertFalse("ENTWURF ERSTELLEN is off while the draft runs", again.isEnabled)
        call(activity, "startDocumentDraft", scene.doc)
        advance(64L)
        assertEquals("a second start changes nothing", 1, runtime.prompts.size)
        assertEquals("the window keeps saying what really happens", "Entwurf wird lokal erstellt …", status(root))
        advance(4_000L); measure(root); draw(root)
        assertTrue("4 s later – far past a glance – he still reads", reading(activity))
        capture(root, "flow-1-reading-while-the-model-works")

        val documentWindow = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        val documentBox = field(documentWindow, "destination") as android.graphics.RectF
        runtime.answer("Kernaussagen: lokales Cockpit auf dem Fold7. Offen: Gerätetest.")
        until(root, "the draft surface") { descendants(root).any { it.tag == "jarvis-draft" && it.isShown } }
        capture(root, "flow-2-draft-unfolds-out-of-the-document")
        assertFalse("the reading ends with the work", reading(activity))
        val draftWindow = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        val from = field(draftWindow, "origin") as android.graphics.RectF
        assertEquals("the draft grows out of the document window", documentBox.centerX(), from.centerX(), 1f)
        assertEquals(documentBox.centerY(), from.centerY(), 1f)
        assertTrue("…from inside it", documentBox.contains(from))
        val source = descendants(draftWindow).single { it.tag == "jarvis-draft-source-page" } as android.widget.ImageView
        assertTrue("the document stays behind the result as its own first page",
            (source.drawable as android.graphics.drawable.BitmapDrawable).bitmap === scene.doc.firstPage)
        val draft = descendants(root).single { it.tag == "jarvis-draft-text" } as TextView
        assertEquals("Kernaussagen: lokales Cockpit auf dem Fold7. Offen: Gerätetest.", draft.text.toString())
        until(root, "the journal records the draft") { state(scene) == CockpitWorkJournal.State.DRAFTED }
        assertEquals("the cockpit stays the stage", ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        assertFalse(field(activity, "busy") as Boolean)
        advance(1_200L); measure(root); draw(root)
        capture(root, "flow-3-draft-with-source-card")
    }

    @Test fun aFailedModelRunReadsAsFailedNeverAsRunning() = withFlow { activity, root, runtime ->
        val scene = startDraft(activity, root, runtime, "Bericht.pdf", "Ein kurzer Bericht über den Stand.")
        until(root, "the journal records the running draft") { state(scene) == CockpitWorkJournal.State.DRAFTING }
        runtime.fail(at.heinz.deviceai.localmodel.LocalModelThermalGuard.STOPPED_THERMAL)
        until(root, "the run is over") { !(field(activity, "busy") as Boolean) }
        assertEquals("Entwurf fehlgeschlagen – wegen Gerätewärme gestoppt. Nichts gespeichert.", status(root))
        until(root, "the journal records the failure") { state(scene) == CockpitWorkJournal.State.FAILED }
        assertFalse("no reading after the work failed", reading(activity))
        assertEquals("the document window stays with its honest state", ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        assertTrue(descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
        assertEquals("no stale draft turn is left", null, field(activity, "documentDraftTurn"))
        assertTrue("the owner can start it again", descendants(root).single { it.tag == "jarvis-document-start-draft" }.isEnabled)
        capture(root, "flow-4-failed-draft-says-so")
    }

    @Test fun stopDuringTheDraftEndsReadingWindowAndRunAsAborted() = withFlow { activity, root, runtime ->
        val scene = startDraft(activity, root, runtime, "Notizen.pdf", "Notizen zum Projekt.")
        until(root, "the journal records the running draft") { state(scene) == CockpitWorkJournal.State.DRAFTING }
        assertTrue(reading(activity))
        call(activity, "cancelLocalRequest")
        until(root, "STOP is recorded") { state(scene) == CockpitWorkJournal.State.ABORTED }
        assertFalse("STOP ends the reading", reading(activity))
        until(root, "the stopped run has ended") { !(field(activity, "busy") as Boolean) }
        advance(400L); measure(root); draw(root)
        assertFalse("STOP folds the window away at once", descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
        assertEquals("the cockpit stays the stage after STOP", ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        val chat = descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("STOP is named as the owner's STOP: $chat", chat.any { it == "Entwurf mit STOP abgebrochen – nichts gespeichert." })
        assertTrue("never an undetermined model error: $chat", chat.none { it.startsWith("Lokaler Modellfehler") })
        advance(1_200L); measure(root); draw(root)
        capture(root, "flow-5-after-stop-cockpit-restored")
    }
}
