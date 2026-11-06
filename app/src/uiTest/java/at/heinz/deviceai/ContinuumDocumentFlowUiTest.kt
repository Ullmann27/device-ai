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
        /** Like a native runtime whose cancel returns before its stream ended: the answer can still arrive late. */
        @Volatile var holdOnCancel = false
        override fun cancel() {
            if (holdOnCancel) return
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

    /**
     * Owner addendum 06.11.: no sequence card may lie over the avatar's eyes, face, neck or the orb while a work window
     * is open – the cards dock into the window's status strip. Checks the cards really drawn in the last frame.
     */
    private fun assertAvatarFree(activity: AgentActivity, root: View, moment: String) {
        measure(root); draw(root)
        val hologram = field(activity, "hologramView") as ContinuumHologramView
        val sequence = field(hologram, "cockpitSequence") as at.heinz.deviceai.ui.JarvisSequenceView
        val density = activity.resources.displayMetrics.density
        val protectedAreas = listOfNotNull(field(hologram, "cockpitHeadBounds") as? android.graphics.Rect,
            field(hologram, "cockpitOrbBounds") as? android.graphics.Rect)
        assertTrue("head and orb regions are known", protectedAreas.isNotEmpty())
        assertTrue("the turn's steps exist (they are docked, not lost)", sequence.effects.cardCount > 0)
        for (i in 0 until sequence.effects.drawnCardCount) {
            val b = sequence.effects.drawnCardBox(i)
            val card = android.graphics.Rect((b[0] * density).toInt(), (b[1] * density).toInt(),
                (b[2] * density).toInt(), (b[3] * density).toInt())
            protectedAreas.forEach { area ->
                assertFalse("$moment: a status card $card lies over the avatar/orb $area", android.graphics.Rect.intersects(card, area))
            }
        }
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
        val strip = descendants(root).single { it.tag == "jarvis-work-window:status" } as TextView
        assertTrue("the status strip shows the real running steps", strip.isShown)
        assertEquals("Auftrag angenommen · Bearbeitung läuft", strip.text.toString())
        val again = descendants(root).single { it.tag == "jarvis-document-start-draft" }
        assertFalse("ENTWURF ERSTELLEN is off while the draft runs", again.isEnabled)
        call(activity, "startDocumentDraft", scene.doc)
        advance(64L)
        assertEquals("a second start changes nothing", 1, runtime.prompts.size)
        assertEquals("the window keeps saying what really happens", "Entwurf wird lokal erstellt …", status(root))
        advance(4_000L); measure(root); draw(root)
        assertTrue("4 s later – far past a glance – he still reads", reading(activity))
        assertAvatarFree(activity, root, "4 s into the work, all cards flown in")
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
        val sequence = field(field(activity, "hologramView")!!, "cockpitSequence") as at.heinz.deviceai.ui.JarvisSequenceView
        assertFalse("with the window gone the cards are back at their cockpit place", sequence.effects.docked)
        val chat = descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("STOP is named as the owner's STOP: $chat", chat.any { it == "Entwurf mit STOP abgebrochen – nichts gespeichert." })
        assertTrue("never an undetermined model error: $chat", chat.none { it.startsWith("Lokaler Modellfehler") })
        advance(1_200L); measure(root); draw(root)
        capture(root, "flow-5-after-stop-cockpit-restored")
    }

    /**
     * Owner: „Spätantwort-Prüfung“ – STOP while the native stream has not ended yet; the model's answer still arrives
     * afterwards. Nothing may come back: no draft window, no late text, the journal stays ABORTED, START stays. A later
     * real failure of the same draft is reported with its real cause, never as the old STOP (#565 review, Gitar).
     */
    @Test fun aLateAnswerAfterStopChangesNothingAndALaterFailureKeepsItsCause() = withFlow { activity, root, runtime ->
        runtime.holdOnCancel = true
        val scene = startDraft(activity, root, runtime, "Protokoll.pdf", "Protokoll der Besprechung.")
        until(root, "the journal records the running draft") { state(scene) == CockpitWorkJournal.State.DRAFTING }
        call(activity, "cancelLocalRequest")
        until(root, "STOP is recorded") { state(scene) == CockpitWorkJournal.State.ABORTED }
        runtime.answer("Späte Antwort, die erst nach STOP kam.")
        until(root, "the late run has ended") { !(field(activity, "busy") as Boolean) && !runtime.running }
        advance(800L); measure(root); draw(root)
        fun chat() = descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("no late text: ${chat()}", chat().none { it.contains("Späte Antwort") })
        assertFalse("no late draft window", descendants(root).any { it.tag == "jarvis-draft" && it.isShown })
        assertEquals(CockpitWorkJournal.State.ABORTED, state(scene))
        assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))

        runtime.holdOnCancel = false
        call(activity, "startDocumentDraft", scene.doc)
        until(root, "the restarted draft reaches the model") { runtime.running }
        runtime.fail(at.heinz.deviceai.localmodel.LocalModelThermalGuard.STOPPED_THERMAL)
        until(root, "the restarted run is over") { !(field(activity, "busy") as Boolean) }
        advance(200L)
        val last = chat().last()
        assertFalse("a real failure is not reported as the old STOP: $last", last == "Entwurf mit STOP abgebrochen – nichts gespeichert.")
        assertEquals(at.heinz.deviceai.localmodel.LocalModelThermalGuard.ownerMessage(
            at.heinz.deviceai.localmodel.LocalModelThermalGuard.STOPPED_THERMAL), last)
    }

    /**
     * The race Gitar named (#565 review): the model has finished, its delivery to the UI is already queued, then the
     * owner presses STOP. The finished draft may not appear after STOP (no late text, no window, no CHAT switch, not
     * kept as conversation context); the STOP marker is used up, so a later real failure keeps its real cause.
     */
    @Test fun aReplyQueuedBeforeStopIsDiscardedAfterIt() = withFlow { activity, root, runtime ->
        val scene = startDraft(activity, root, runtime, "Angebot.pdf", "Angebot für das Projekt.")
        until(root, "the journal records the running draft") { state(scene) == CockpitWorkJournal.State.DRAFTING }
        runtime.answer("Fertiger Entwurf, der vor STOP fertig wurde.")
        // Let the model lane finish and queue its delivery on the (paused) main looper – without running it.
        Thread.sleep(400)
        call(activity, "cancelLocalRequest")
        until(root, "the queued delivery has run") { !(field(activity, "busy") as Boolean) }
        advance(800L); measure(root); draw(root)
        fun chat() = descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("the finished draft does not come back after STOP: ${chat()}", chat().none { it.contains("Fertiger Entwurf") })
        assertFalse(descendants(root).any { it.tag == "jarvis-draft" && it.isShown })
        assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        until(root, "STOP is recorded") { state(scene) == CockpitWorkJournal.State.ABORTED }
        // The owner restarts the same draft (same request text) and it fails for a real reason.
        call(activity, "startDocumentDraft", scene.doc)
        until(root, "the restarted draft reaches the model") { runtime.running }
        runtime.fail(at.heinz.deviceai.localmodel.LocalModelThermalGuard.STOPPED_THERMAL)
        until(root, "the restarted run is over") { !(field(activity, "busy") as Boolean) }
        advance(200L)
        assertEquals("a real failure keeps its real cause, not the old STOP", at.heinz.deviceai.localmodel.LocalModelThermalGuard
            .ownerMessage(at.heinz.deviceai.localmodel.LocalModelThermalGuard.STOPPED_THERMAL), chat().last())
    }

    /**
     * Owner brief E, counter path „fehlende Datei“: „Wo waren wir gestern? Nimm die letzte PDF und fasse sie zusammen.“
     * The record answers with the real hit (the orb gets the real count), the remembered file is gone. JARVIS names it,
     * changes nothing, records the failed attempt, offers Android's picker and stays on the cockpit – no model run.
     */
    @Test fun aRememberedPdfThatIsGoneIsNamedRecordedAndThePickerOffered() = withFlow { activity, root, runtime ->
        val uri = android.net.Uri.parse("content://at.heinz.test.missing/document/bericht")
        activity.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1); set(java.util.Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        val journal = CockpitWorkJournal(activity.filesDir) { yesterday }
        journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "Fasse den Bericht zusammen", CockpitWorkJournal.State.DRAFTED,
            "Bericht.pdf", 2, 40, uri.toString())
        val bus = field(activity, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus
        val events = CopyOnWriteArrayList<at.heinz.deviceai.cockpit.CockpitAnimationEvent>()
        bus.subscribe { events += it }
        val sentence = "Wo waren wir gestern? Nimm die letzte PDF und fasse sie zusammen."
        (field(activity, "input") as android.widget.EditText).setText(sentence)
        call(activity, "sendCurrentInput", false)
        fun chat() = descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        until(root, "the read attempt has answered") { chat().any { it.contains("PDF") && it.contains("nichts verändert") } }
        val recall = events.filterIsInstance<at.heinz.deviceai.cockpit.CockpitAnimationEvent.RecallHits>().single()
        assertEquals("the orb gets the real number of hits", 1, recall.count)
        assertTrue("answered from the record: ${chat()}", chat().any { it.startsWith("Gestern:") && it.contains("Bericht.pdf") })
        val gone = chat().single { it.contains("nichts verändert") }
        assertTrue("the remembered file is named: $gone", gone.contains("„Bericht.pdf“") && gone.contains("nicht mehr lesen"))
        assertEquals("the cockpit stays", ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        val picker = shadowOf(activity).nextStartedActivityForResult
        assertEquals("Android's picker is offered", android.content.Intent.ACTION_OPEN_DOCUMENT, picker?.intent?.action)
        until(root, "the failed attempt is recorded") {
            CockpitWorkJournal(activity.filesDir).all().any { it.request == sentence && it.state == CockpitWorkJournal.State.FAILED }
        }
        val failed = CockpitWorkJournal(activity.filesDir).all().single { it.request == sentence }
        assertEquals("Bericht.pdf", failed.source)
        assertTrue("no model run", runtime.prompts.isEmpty())
    }

    /**
     * Owner review P0 (06.11.): also without a work window, a running turn's cards were laid out at a fixed position in
     * the middle of the hologram – over the original head and the orb. Real events of the activity's own triggers; the
     * cards may only be drawn where they leave the head and orb regions free, otherwise the steps stay in the cockpit's
     * „Aktueller Ablauf“ panel (each step once).
     */
    @Test fun withoutAWindowTheStatusCardsLeaveTheAvatarFreeAndStayInTheProcessPanel() = withFlow { activity, root, _ ->
        val triggers = field(activity, "cockpitTriggers") as at.heinz.deviceai.cockpit.CockpitAnimationTriggers
        triggers.commandAcknowledged("owner-turn-1", "Was steht heute an?")
        advance(4_000L)
        assertFalse("no work window is open", descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
        measure(root); draw(root)
        capture(root, System.getProperty("continuum.ui.evidenceName") ?: "statusbar-nowindow-after")
        assertAvatarFree(activity, root, "a running turn without a window")
        val home = field(activity, "homeView") as ContinuumHomeView
        val rows = at.heinz.deviceai.ui.ContinuumCockpitPresentation.processRows(home.state.cockpit).map { it.label }
        assertEquals("the steps stay visible in the process panel, each once",
            listOf("Auftrag angenommen", "Bearbeitung"), rows)
    }

    /**
     * Owner review P1 (06.11.): the work journal was read on the UI thread while writes run on the serial document lane.
     * A recall right after STOP could therefore read the old state and claim the draft was still running. The STOP write
     * is held back here (the lane is busy); the answer may only come from the state STOP really recorded.
     */
    @Test fun aRecallRightAfterStopReadsTheStateStopRecorded() = withFlow { activity, root, runtime ->
        val scene = startDraft(activity, root, runtime, "Bericht.pdf", "Ein kurzer Bericht über den Stand.")
        until(root, "the journal records the running draft") { state(scene) == CockpitWorkJournal.State.DRAFTING }
        val lane = field(activity, "documentIoExecutor") as java.util.concurrent.ExecutorService
        val gate = java.util.concurrent.CountDownLatch(1)
        lane.execute { gate.await(10, TimeUnit.SECONDS) }
        call(activity, "cancelLocalRequest")
        until(root, "the stopped run has ended (the journal lane is still held)") { !(field(activity, "busy") as Boolean) }
        assertEquals("the STOP write is still queued", CockpitWorkJournal.State.DRAFTING, state(scene))
        (field(activity, "input") as android.widget.EditText).setText("Was haben wir heute gemacht?")
        call(activity, "sendCurrentInput", false)
        advance(64L)
        gate.countDown()
        fun chat() = descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        until(root, "the recall answer") { chat().any { it.startsWith("Heute:") } }
        val answer = chat().last { it.startsWith("Heute:") }
        assertFalse("never „still running“ after STOP: $answer", answer.contains("Entwurf in Arbeit"))
        assertTrue("the state STOP recorded: $answer", answer.contains("mit STOP abgebrochen, nicht erledigt"))
    }

    @Test fun stopRejectsAQueuedDecisionContinuation() = withFlow { activity, root, runtime ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val episode = journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "Entwurf fortsetzen",
            CockpitWorkJournal.State.DRAFTED, "Beispiel.pdf")
        journal.update(episode.id) { it.copy(draft = "Ungeprüfter Testentwurf") }
        val items = at.heinz.deviceai.cognitive.WorkDecisionRecall.items(journal.all(), emptyList(), System.currentTimeMillis())
        val focus = at.heinz.deviceai.cognitive.WorkDecisionRecall.Focus(items, items, 0)
        AgentActivity::class.java.getDeclaredField("decisionFocus").apply { isAccessible = true }
            .set(activity, field(activity, "sessionId") as String to focus)
        val lane = field(activity, "documentIoExecutor") as java.util.concurrent.ExecutorService
        val gate = java.util.concurrent.CountDownLatch(1)
        lane.execute { gate.await(10, TimeUnit.SECONDS) }
        try {
            call(activity, "presentDecisionRecall", at.heinz.deviceai.cognitive.WorkDecisionRecall.Ask.ContinueOpen)
            call(activity, "cancelLocalRequest")
        } finally { gate.countDown() }
        lane.submit {}.get(10, TimeUnit.SECONDS)
        advance(128L); measure(root); draw(root)
        assertFalse("STOP prevents a late draft window", descendants(root).any { it.tag == "jarvis-draft" && it.isShown })
        assertTrue("no restarted inference", runtime.prompts.isEmpty())
        assertEquals(CockpitWorkJournal.State.DRAFTED, journal.all().single { it.id == episode.id }.state)
    }

    @Test fun stopRejectsAQueuedPdfFailureAndItsPicker() = withFlow { activity, root, runtime ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val episode = journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "Letzte PDF öffnen",
            CockpitWorkJournal.State.READ, "Verschoben.pdf", documentUri = "content://missing/document/test")
        val lane = field(activity, "documentIoExecutor") as java.util.concurrent.ExecutorService
        val gate = java.util.concurrent.CountDownLatch(1)
        lane.execute { gate.await(10, TimeUnit.SECONDS) }
        try {
            call(activity, "readPdfDocument", android.net.Uri.parse(episode.documentUri), episode)
            call(activity, "cancelLocalRequest")
        } finally { gate.countDown() }
        lane.submit {}.get(10, TimeUnit.SECONDS)
        advance(128L); measure(root); draw(root)
        assertEquals("no picker after STOP", null, shadowOf(activity).nextStartedActivityForResult)
        assertTrue(runtime.prompts.isEmpty())
        assertEquals("cancelled reads add no failed task", 1, journal.all().size)
    }

    @Test fun anEmptyDecisionQueryClearsThePreviousFocus() = withFlow { activity, _, _ ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val episode = journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "Testauftrag",
            CockpitWorkJournal.State.DRAFTED, "Beispiel.pdf")
        val items = at.heinz.deviceai.cognitive.WorkDecisionRecall.items(listOf(episode), emptyList(), System.currentTimeMillis())
        val focus = at.heinz.deviceai.cognitive.WorkDecisionRecall.Focus(items, items, 0)
        AgentActivity::class.java.getDeclaredField("decisionFocus").apply { isAccessible = true }
            .set(activity, field(activity, "sessionId") as String to focus)
        call(activity, "presentDecisionRecall", at.heinz.deviceai.cognitive.WorkDecisionRecall.Ask.Decisions(
            at.heinz.deviceai.cognitive.WorkRecall.Period.TODAY, false), true,
            emptyList<CockpitWorkJournal.Episode>(), emptyList<at.heinz.deviceai.cognitive.WorkDecisionRecall.OwnerStatement>())
        assertEquals("no old focus after an empty answer", null, field(activity, "decisionFocus"))
    }
}
