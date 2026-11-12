package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
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
import org.junit.Assert.assertNotEquals
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Field P0 Code54 („Fehler B“, Fold7, offline): „Öffne eine PDF.“ → Android picker → „PDF gelesen: 16 Seiten · 2,0 MB ·
 * Continuum_Zwischenbericht_2026-11-11.pdf … Seite 1–6.“ → „Was steht in der Überschrift auf Seite 1?“ was answered as
 * if no PDF existed, and „Wie heißt die PDF, die du gerade geöffnet hast, und wie viele Seiten hat sie?“ got the old
 * heading answer again. HOST + SIMULATED_DOCUMENT_INPUT (Android's PdfRenderer is native; the scene gets the read
 * [PdfDocumentIntake.Document] built here) + CONTROLLED_MODEL + a network guard (a web authorization is counted and its
 * time budget expires before any connection). The real routing, dialogue context and runLocal path run. Fold7: NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerPdfDialogueFollowUpUiTest {
    private class Runtime : LocalModelRuntime {
        @Volatile override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("pdf-dialog-model", "v1", "b".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        private val pending = AtomicReference<LocalInferenceCallback?>()
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("A follow-up question must reuse the loaded model")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("A follow-up question must not unload the model")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            check(pending.compareAndSet(null, callback)) { "Concurrent inference was admitted" }
            state = LocalModelState.GENERATING
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            error("A document question calls no tools")
        val running: Boolean get() = pending.get() != null
        fun answer(text: String) {
            val callback = pending.getAndSet(null) ?: return
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

    /** A reply as the owner saw it and the model prompt it took (null: answered without the model). */
    private class Turn(val reply: String, val prompt: String?)

    private class Fixture(val activity: AgentActivity, val root: View, val runtime: Runtime, val webAuthorizations: AtomicLong)

    private val fileName = "Continuum_Zwischenbericht_2026-11-11.pdf"
    private val heading = "Continuum Zwischenbericht 11. November 2026"
    /** Synthetic fixture text, page by page as the reader keeps it (S. 1–6 read of 16). No owner document. */
    private val pageTexts = listOf(
        "$heading\nStand der lokalen Arbeit auf dem Fold7",
        "Kapitel 2: Cockpit und Sprachsteuerung laufen lokal.",
        "Kapitel 3: Gedächtnis mit Zeitbezug.",
        "Kapitel 4: Werkzeuge nur mit Freigabe.",
        "Kapitel 5: Messwerte des Gerätetests stehen noch aus.",
        "Kapitel 6: Offene Punkte."
    )

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
    private fun set(owner: Any, name: String, value: Any?) =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
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
    private fun until(root: View, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            assertTrue("timed out waiting for: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
            advance(16L)
        }
        measure(root)
    }
    private fun chat(activity: AgentActivity) = descendants(field(activity, "chatContainer") as View)
        .filterIsInstance<TextView>().map { it.text.toString() }
    private fun busy(activity: AgentActivity) = field(activity, "busy") as Boolean

    private fun prepare(mode: InternetAccessMode) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", mode.name).commit()
    }

    private fun <T> withFlow(mode: InternetAccessMode, block: (Fixture) -> T): T {
        prepare(mode)
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val runtime = Runtime()
        try {
            controller.setup().visible(); advance(32L); controller.windowFocusChanged(true)
            val activity = controller.get()
            AgentActivity::class.java.getDeclaredField("localRuntime").apply { isAccessible = true }.set(activity, runtime)
            // Network guard: every web authorization reads this clock, and each read jumps far past any research
            // budget – a granted session expires before its first connection. The count is the evidence.
            val webAuthorizations = AtomicLong()
            val consent = InternetConsentPolicy(mode) { webAuthorizations.incrementAndGet() * 1_000_000_000L }
            set(activity, "internetConsent", consent)
            set(activity, "webToolGate", ApprovedWebToolGate(consent))
            val root = activity.window.decorView
            framePump = CockpitHostFramePump { root }
            (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(true)
            measure(root); advance(320L); measure(root)
            return block(Fixture(activity, root, runtime, webAuthorizations))
        } finally {
            runtime.cancel()
            framePump?.close(); framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    /** One Activity instance of a multi-instance flow: the same runtime and network guard are injected each time. */
    private fun instance(mode: InternetAccessMode, saved: Bundle?, runtime: Runtime, web: AtomicLong,
                         controller: org.robolectric.android.controller.ActivityController<AgentActivity> =
                             Robolectric.buildActivity(AgentActivity::class.java)):
        Pair<org.robolectric.android.controller.ActivityController<AgentActivity>, Fixture> {
        framePump?.close(); framePump = null
        if (saved != null) controller.setup(saved) else controller.setup()
        controller.visible(); advance(32L); controller.windowFocusChanged(true)
        return controller to adopt(controller.get(), mode, runtime, web)
    }

    private fun adopt(activity: AgentActivity, mode: InternetAccessMode, runtime: Runtime, web: AtomicLong): Fixture {
        framePump?.close(); framePump = null
        AgentActivity::class.java.getDeclaredField("localRuntime").apply { isAccessible = true }.set(activity, runtime)
        val consent = InternetConsentPolicy(mode) { web.incrementAndGet() * 1_000_000_000L }
        set(activity, "internetConsent", consent)
        set(activity, "webToolGate", ApprovedWebToolGate(consent))
        val root = activity.window.decorView
        framePump = CockpitHostFramePump { root }
        (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(true)
        measure(root); advance(320L); measure(root)
        return Fixture(activity, root, runtime, web)
    }

    /** Android destroys the activity behind another app (not a configuration change) and later recreates it. */
    private fun destroyedBehindAnotherApp(controller: org.robolectric.android.controller.ActivityController<AgentActivity>): Bundle {
        val saved = Bundle()
        controller.pause().saveInstanceState(saved).stop().destroy()
        return saved
    }

    private fun page() = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    /** The read result of the field PDF (16 pages, text of pages 1–6) arrives in the real document scene. */
    private fun openReadPdf(f: Fixture) {
        val text = PdfDocumentIntake.boundedText(pageTexts.asSequence(), PdfDocumentIntake.MAX_TEXT_CHARS)
        val doc = PdfDocumentIntake.Document(fileName, 2_097_152L, 16, page(), text,
            PdfDocumentIntake.TextState.EXTRACTED, 6, pageTexts)
        call(f.activity, "presentDocumentScene", doc, "content://com.android.providers.downloads.documents/document/42")
        advance(1_600L); measure(f.root)
        assertTrue("the scene reports the read PDF", chat(f.activity).any { it.startsWith("PDF gelesen: 16 Seiten") })
        drainStartedActivities(f.activity)
    }

    private fun drainStartedActivities(activity: AgentActivity) {
        while (shadowOf(activity).nextStartedActivityForResult != null) Unit
        while (shadowOf(activity).nextStartedActivity != null) Unit
    }

    private fun assertNoPicker(activity: AgentActivity) {
        val forResult = shadowOf(activity).nextStartedActivityForResult
        assertNull("no new document picker: ${forResult?.intent}", forResult)
        assertNull("no other started activity", shadowOf(activity).nextStartedActivity)
    }

    /** The owner sends [question]; a model call (if any) gets [modelReply]. */
    private fun ask(f: Fixture, question: String, modelReply: String = "Ich habe keine PDF vor mir."): Turn {
        val before = chat(f.activity).size
        (field(f.activity, "input") as android.widget.EditText).setText(question)
        assertTrue((field(f.activity, "sendButton") as android.widget.Button).performClick())
        advance(64L)
        until(f.root, "a reply or a model call for: $question") {
            f.runtime.running || !busy(f.activity) && chat(f.activity).size > before + 1
        }
        var prompt: String? = null
        if (f.runtime.running) {
            prompt = f.runtime.prompts.last()
            f.runtime.answer(modelReply)
            until(f.root, "the model reply is delivered: $question") { !busy(f.activity) && !f.runtime.running }
        }
        advance(400L)
        val after = chat(f.activity).drop(before)
        assertEquals("the owner's question is shown", question, after.firstOrNull())
        return Turn(after.drop(1).joinToString("\n"), prompt)
    }

    @Test fun offlineFieldSequenceIsAnsweredFromTheReadPdfAndNeverReplaysTheOldAnswer() = withFlow(InternetAccessMode.OFF) { f ->
        openReadPdf(f)
        val headingTurn = ask(f, "Was steht in der Überschrift auf Seite 1?")
        assertNull("the heading needs no model: ${headingTurn.prompt}", headingTurn.prompt)
        assertTrue(headingTurn.reply, headingTurn.reply.contains("„$heading“"))
        assertFalse("only page 1 is used", headingTurn.reply.contains("Kapitel 2") || headingTurn.reply.contains("Stand der lokalen"))

        val nameTurn = ask(f, "Wie heißt die PDF, die du gerade geöffnet hast, und wie viele Seiten hat sie?",
            modelReply = "Die Überschrift auf Seite 1 kann ich nicht sehen.")
        assertTrue(nameTurn.reply, nameTurn.reply.contains("„$fileName“"))
        assertTrue(nameTurn.reply, nameTurn.reply.contains("16 Seiten"))
        assertNotEquals("the new question gets its own answer", headingTurn.reply, nameTurn.reply)
        assertFalse("never the old heading answer again: ${nameTurn.reply}", nameTurn.reply.contains(heading))

        assertTrue(ask(f, "Welche PDF hast du gerade geöffnet?").reply.contains("„$fileName“"))
        assertTrue(ask(f, "Wie viele Seiten hat sie?").reply.contains("16 Seiten"))
        assertTrue("no model call for the file's own facts: ${f.runtime.prompts}", f.runtime.prompts.isEmpty())
        assertEquals("no web authorization", 0L, f.webAuthorizations.get())
        assertNoPicker(f.activity)
        assertEquals("the cockpit stays the stage", ContinuumWorkArea.START, field(f.activity, "activeWorkArea"))
    }

    @Test fun withAlwaysOnInternetADocumentQuestionNeverBecomesWebResearch() = withFlow(InternetAccessMode.ALWAYS_ON) { f ->
        openReadPdf(f)
        val headingTurn = ask(f, "Was steht in der Überschrift auf Seite 1?")
        assertEquals("no web research for the open PDF: ${headingTurn.reply}", 0L, f.webAuthorizations.get())
        assertTrue(headingTurn.reply, headingTurn.reply.contains("„$heading“"))
        val nameTurn = ask(f, "Wie heißt die PDF, die du gerade geöffnet hast, und wie viele Seiten hat sie?",
            modelReply = "Die Überschrift auf Seite 1 kann ich nicht sehen.")
        assertTrue(nameTurn.reply, nameTurn.reply.contains("„$fileName“") && nameTurn.reply.contains("16 Seiten"))
        assertFalse(nameTurn.reply, nameTurn.reply.contains(heading))
        assertEquals(0L, f.webAuthorizations.get())
        assertNoPicker(f.activity)
    }

    @Test fun aQuestionForTheModelGetsThePagesReadAndANewQuestionGetsItsOwnAnswer() =
        withFlow(InternetAccessMode.ALWAYS_ON) { f ->
            openReadPdf(f)
            ask(f, "Was steht in der Überschrift auf Seite 1?")
            assertEquals("no web research for the open PDF", 0L, f.webAuthorizations.get())
            val modelAnswer = "Laut Seite 1 geht es um den Stand der lokalen Arbeit auf dem Fold7."
            val about = ask(f, "Worum geht es in der PDF?", modelReply = modelAnswer)
            assertNotNull("this question needs the model", about.prompt)
            val prompt = about.prompt!!
            assertTrue(prompt, prompt.contains("DOKUMENTDATEN —"))
            assertTrue(prompt, prompt.contains("Datei: „$fileName“ · 16 Seiten"))
            assertTrue(prompt, prompt.contains("Text gelesen: Seite 1–6"))
            assertTrue(prompt, prompt.contains("[Seite 1]\n$heading"))
            assertTrue("the current question is the task", prompt.contains("Worum geht es in der PDF?"))
            assertFalse("no web data framed as current", prompt.contains("EXTERNE RECHERCHEDATEN"))
            assertTrue(about.reply, about.reply.contains(modelAnswer))
            assertEquals("no web research", 0L, f.webAuthorizations.get())

            val pages = ask(f, "Wie viele Seiten hat sie?")
            assertEquals("answered without another model call", 1, f.runtime.prompts.size)
            assertTrue(pages.reply, pages.reply.contains("16 Seiten"))
            assertFalse("the previous answer is not replayed", pages.reply.contains(modelAnswer))
            assertNoPicker(f.activity)
        }

    /**
     * Field evidence Code54 (Fold7 screenshot): after „Öffne eine PDF.“ the start greeting appeared again on the way
     * back from the picker – Android recreated the activity. The read PDF must stay bound to the chat across that
     * recreation, another non-configuration recreation after it (background, next picker trip, process death with
     * saved state) and a configuration change (fold/unfold).
     */
    @Test fun theReadPdfStaysBoundAcrossThePickerReturnRecreationAndLaterRecreations() {
        val mode = InternetAccessMode.OFF
        prepare(mode)
        val runtime = Runtime()
        val web = AtomicLong()
        val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<AgentActivity>>()
        try {
            val (first, beforePicker) = instance(mode, null, runtime, web).also { controllers += it.first }
            val chat = field(beforePicker.activity, "sessionId") as String
            (field(beforePicker.activity, "input") as android.widget.EditText).setText("Öffne eine PDF.")
            assertTrue((field(beforePicker.activity, "sendButton") as android.widget.Button).performClick())
            advance(64L)
            val picker = shadowOf(beforePicker.activity).nextStartedActivityForResult
            assertNotNull("the real Android picker opens", picker)
            assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, picker.intent.action)

            // The picker is in front; Android destroys JARVIS behind it and recreates it on the way back.
            val (second, afterPicker) = instance(mode, destroyedBehindAnotherApp(first), runtime, web)
                .also { controllers += it.first }
            assertEquals("the same chat continues", chat, field(afterPicker.activity, "sessionId"))
            openReadPdf(afterPicker)
            assertTrue(ask(afterPicker, "Was steht in der Überschrift auf Seite 1?").reply.contains("„$heading“"))

            // Recreated once more without a configuration change: the RAM dialogue is forgotten, the read PDF is not.
            val (third, recreated) = instance(mode, destroyedBehindAnotherApp(second), runtime, web)
                .also { controllers += it.first }
            assertEquals(chat, field(recreated.activity, "sessionId"))
            val nameTurn = ask(recreated, "Wie heißt die PDF, die du gerade geöffnet hast, und wie viele Seiten hat sie?",
                modelReply = "Die Überschrift auf Seite 1 kann ich nicht sehen.")
            assertTrue(nameTurn.reply, nameTurn.reply.contains("„$fileName“") && nameTurn.reply.contains("16 Seiten"))
            assertFalse(nameTurn.reply, nameTurn.reply.contains(heading))

            // A configuration change (fold/unfold) keeps it as well. Robolectric 4.14.1's recreate() fails in
            // visible() (see ContinuumPdfSceneAcceptanceUiTest), so the same lifecycle runs by hand with the old
            // instance marked as changing configurations – the RAM dialogue context is then retained, as on the device.
            val foldState = Bundle()
            third.pause().saveInstanceState(foldState).stop()
            android.app.Activity::class.java.getDeclaredField("mChangingConfigurations").apply { isAccessible = true }
                .setBoolean(third.get(), true)
            assertTrue(third.get().isChangingConfigurations)
            third.destroy()
            val (_, folded) = instance(mode, foldState, runtime, web).also { controllers += it.first }
            assertEquals(chat, field(folded.activity, "sessionId"))
            assertTrue(ask(folded, "Wie viele Seiten hat sie?").reply.contains("16 Seiten"))
            assertTrue(ask(folded, "Was steht auf Seite 2?").reply.contains("Kapitel 2: Cockpit und Sprachsteuerung"))

            assertTrue("no model call for the file's own facts: ${runtime.prompts}", runtime.prompts.isEmpty())
            assertEquals("no web authorization", 0L, web.get())
            assertNoPicker(folded.activity)
        } finally {
            runtime.cancel()
            framePump?.close(); framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            controllers.forEach { runCatching { it.close() } }
        }
    }

    @Test fun withoutALoadedModelAPdfQuestionSaysWhatIsPossibleAndGuessesNothing() = withFlow(InternetAccessMode.OFF) { f ->
        f.runtime.state = LocalModelState.EMPTY
        openReadPdf(f)
        val turn = ask(f, "Worum geht es in der PDF?")
        assertNull(turn.prompt)
        assertTrue(turn.reply, turn.reply.contains("„$fileName“") && turn.reply.contains("lokale Modell"))
        assertTrue(turn.reply, turn.reply.contains("Seite 1–6"))
        assertTrue(ask(f, "Was steht in der Überschrift auf Seite 9?").reply.contains("Seite 9 habe ich nicht ausgelesen"))
        assertEquals(0L, f.webAuthorizations.get())
        assertNoPicker(f.activity)
    }
}
