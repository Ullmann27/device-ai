package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisActionScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
import java.util.concurrent.TimeUnit

/**
 * Owner flow „Lies diese PDF …“ (06.11.2026). HOST + SIMULATED_DOCUMENT_INPUT, REAL_DEVICE_NOT_RUN: the picker wiring
 * runs for real; the document scene gets a [PdfDocumentIntake.Document] built here, because Android's PDF renderer is
 * native and does not run on the host. The Fold7 run with a real PDF is still open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumDocumentSceneUiTest {
    private var framePump: CockpitHostFramePump? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            remaining -= step
        }
    }
    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }
    private fun draw(root: View) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }

    /** Labelled host evidence (HOST + SIMULATED inputs) when -PcontinuumUiEvidenceDir is set; never a device claim. */
    private fun capture(root: View, name: String) {
        val dir = System.getProperty("continuum.ui.evidenceDir") ?: return
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            java.io.File(dir).apply { mkdirs() }.resolve("$name.png").outputStream()
                .use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    /** A fixture page that says what it is: no owner document, no invented content. */
    private fun examplePage(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE)
        val c = Canvas(this)
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 28, 44); textSize = w / 14f }
        c.drawText("Beispiel · Host-Testseite", w * .08f, h * .12f, ink)
        ink.textSize = w / 22f
        listOf("Projektübersicht Continuum.", "Ziel: ein lokales Cockpit", "auf dem Fold7.").forEachIndexed { i, line ->
            c.drawText(line, w * .08f, h * (.24f + i * .07f), ink)
        }
    }

    private fun <T> withCockpit(startInChat: Boolean = false, block: (AgentActivity, View) -> T): T {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            if (startInChat) controller.setup(android.os.Bundle().apply {
                putString("continuum_work_area_state", "CHAT")
            }) else controller.setup()
            controller.visible(); advance(32L); controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            framePump = CockpitHostFramePump { root }
            (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            return block(activity, root)
        } finally {
            framePump?.close(); framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    private fun chatTexts(activity: AgentActivity) =
        descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }

    @Test fun theOwnersPdfRequestOpensOnlyThePickerAndACancelReadsNothing() = withCockpit { activity, root ->
        (field(activity, "input") as EditText).setText("Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.")
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, false)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("Android's document picker is the only thing that opens", started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        assertEquals("application/pdf", started.intent.type)
        assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals(true, field(activity, "pendingDocumentDraft"))
        assertEquals("no model turn starts for the request itself", false, field(activity, "busy"))
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        advance(64L); measure(root); draw(root)
        assertTrue("a cancel is said honestly: ${chatTexts(activity)}", chatTexts(activity).any { it.contains("Keine PDF gewählt – es wurde nichts gelesen.") })
        assertEquals(false, field(activity, "pendingDocumentDraft"))
        assertTrue("nothing opens without a file", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
    }

    @Test fun theDocumentArrivesAsItsOwnPageOnTheTrailAndJarvisLooksAtIt() = withCockpit { activity, root ->
        val originalDeck = JarvisHomeTestRoutes.deck(root)
        assertEquals("The document fixture starts with the full deck", 14, originalDeck.size)
        assertTrue("The original deck is shown", originalDeck.all { it.isShown })
        val page = examplePage(360, 480)
        val text = "Projektübersicht Continuum. Ziel: ein lokales Cockpit auf dem Fold7."
        capture(root, "document-0-ready")
        val doc = PdfDocumentIntake.Document("Projektübersicht.pdf", 12_288L, 3, page, text,
            PdfDocumentIntake.TextState.EXTRACTED, 3)
        val director = field(activity, "avatarDirector")!!
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        assertNotNull("JARVIS looks at the arriving document", field(director, "windowIntent"))
        advance(120L); measure(root); draw(root)
        capture(root, "document-1-page-on-trail")
        val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        val emblem = field(window, "emblem") as? BitmapDrawable
        assertSame("the trail carries the document's own first page", page, emblem?.bitmap)
        advance(640L); measure(root); draw(root)
        println("PANEL_ALPHA_AT_760MS=${(field(window, "panel") as View).alpha} PHASE=${field(window, "phase")}")
        advance(900L); measure(root); draw(root)
        println("PANEL_ALPHA_SETTLED=${(field(window, "panel") as View).alpha} PHASE=${field(window, "phase")}")
        capture(root, "document-2-window")
        val texts = descendants(window).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("titled with the file: $texts", texts.contains("DOKUMENT · Projektübersicht.pdf"))
        val image = descendants(window).filterIsInstance<ImageView>().single { it.tag == "jarvis-document-page" }
        assertSame("the page image is the document's own page", page, (image.drawable as BitmapDrawable).bitmap)
        val meta = descendants(window).single { it.tag == "jarvis-document-meta" } as TextView
        assertTrue(meta.text.toString(), meta.text.startsWith("3 Seiten · ") && meta.text.endsWith(" · Projektübersicht.pdf"))
        assertEquals("the excerpt is the document's text", text, (descendants(window).single { it.tag == "jarvis-document-excerpt" } as TextView).text.toString())
        val clearedDeck = JarvisHomeTestRoutes.deck(root)
        assertEquals("The same deck yields its space", originalDeck.map { it.id }, clearedDeck.map { it.id })
        val footprint = JarvisActionScenes.footprintFor(JarvisActionScenes.Kind.FILES)
        assertTrue("The cards made room for the document", JarvisHomeTestRoutes.clearedBy(root, footprint).none { it.isShown })
        assertTrue("The mission rows stay on the deck beside the medium document window",
            JarvisHomeTestRoutes.keptBy(root, footprint).all { it.isShown })
        assertTrue("chat names the same facts", chatTexts(activity).any { it.startsWith("PDF gelesen: 3 Seiten") && it.contains("${text.length} Zeichen von Seite 1–3") })
    }

    @Test fun typedPdfRequestReturnsFromChatIntoAnimatedDocumentWork() = withCockpit { activity, root ->
        AgentActivity::class.java.getDeclaredMethod("focusChatComposer")
            .apply { isAccessible = true }.invoke(activity)
        measure(root); advance(32L)
        assertFalse((field(activity, "homeView") as ContinuumHomeView).isShown)
        (field(activity, "input") as EditText).setText("Öffne bitte die PDF.")
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, false)
        val picker = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.action)
        val page = examplePage(200, 280)
        val doc = PdfDocumentIntake.Document("Chat-Auftrag.pdf", 4096L, 1, page,
            "Der gelesene Inhalt bleibt gleich.", PdfDocumentIntake.TextState.EXTRACTED, 1)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        measure(root); advance(80L); draw(root)
        val window = descendants(root).firstOrNull { it.tag == "jarvis-work-window" && it.isShown }
        assertNotNull("A typed PDF task must open its animated work window", window)
        assertEquals("OPENING", field(window!!, "phase").toString())
        capture(root, "code48-pdf-chat-opening")
        advance(1700L); measure(root); draw(root)
        assertEquals("OPEN", field(window, "phase").toString())
        val image = descendants(window).filterIsInstance<ImageView>().single { it.tag == "jarvis-document-page" }
        assertSame(page, (image.drawable as BitmapDrawable).bitmap)
        assertTrue(chatTexts(activity).any { it.contains("Chat-Auftrag.pdf") })
        assertEquals(false, field(activity, "busy"))
        capture(root, "code48-pdf-chat-open")
    }

    @Test fun restoredChatOpensPdfBeforeTheCockpitsFirstLayout() = withCockpit(startInChat = true) { activity, root ->
        val home = field(activity, "homeView") as ContinuumHomeView
        assertFalse(home.isShown)
        assertEquals("The restored CHAT has never laid out its cockpit", 0, home.jarvisShell.width)
        val page = examplePage(200, 280)
        val doc = PdfDocumentIntake.Document("Wiederaufnahme.pdf", 4096L, 1, page,
            "Synthetischer PDF-Inhalt.", PdfDocumentIntake.TextState.EXTRACTED, 1)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        measure(root); advance(1900L); measure(root); draw(root)
        val window = descendants(root).firstOrNull { it.tag == "jarvis-work-window" && it.isShown }
        assertNotNull("A newly visible cockpit must retain the pending document scene", window)
        val image = descendants(window!!).filterIsInstance<ImageView>().single { it.tag == "jarvis-document-page" }
        assertSame(page, (image.drawable as BitmapDrawable).bitmap)
        assertTrue(chatTexts(activity).any { it.contains("Wiederaufnahme.pdf") })
    }

    @Test fun documentWorkWaitsForFreshShellGeometry() = withCockpit { activity, root ->
        val home = field(activity, "homeView") as ContinuumHomeView
        home.jarvisShell.compactPresentation = !home.jarvisShell.compactPresentation
        val doc = PdfDocumentIntake.Document("Neue-Ansicht.pdf", 4096L, 1, examplePage(200, 280),
            "Geometrie vor dem ersten neuen Bild.", PdfDocumentIntake.TextState.EXTRACTED, 1)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        measure(root); advance(80L); draw(root)
        assertTrue("A pending layout must not discard the document scene",
            descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
    }

    @Test fun stopBeforeFirstCockpitLayoutDiscardsThePendingDocument() = withCockpit(startInChat = true) { activity, root ->
        val page = examplePage(200, 280)
        val doc = PdfDocumentIntake.Document("Verworfene-PDF.pdf", 4096L, 1, page,
            "Synthetischer PDF-Inhalt.", PdfDocumentIntake.TextState.EXTRACTED, 1)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        AgentActivity::class.java.getDeclaredMethod("cancelLocalRequest")
            .apply { isAccessible = true }.invoke(activity)
        measure(root); advance(160L); draw(root)
        assertTrue(page.isRecycled)
        assertFalse(descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
        assertFalse(chatTexts(activity).any { it.contains("Verworfene-PDF.pdf") })
    }

    @Test fun withoutTextTheSceneSaysWhyAndShowsNoInventedExcerpt() = withCockpit { activity, root ->
        val page = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        AgentActivity::class.java.getDeclaredField("pendingDocumentDraft").apply { isAccessible = true }.set(activity, true)
        val doc = PdfDocumentIntake.Document("Scan.pdf", null, 1, page, "", PdfDocumentIntake.TextState.NO_TEXT_LAYER, 1)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        advance(760L); measure(root); draw(root)
        val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        assertTrue(descendants(window).none { it.tag == "jarvis-document-excerpt" })
        val status = (descendants(window).single { it.tag == "jarvis-document-status" } as TextView).text.toString()
        assertTrue(status, status.startsWith("Kein auslesbarer Text"))
        assertEquals("Für einen Entwurf fehlt der Dokumenttext.",
            (descendants(window).single { it.tag == "jarvis-document-draft" } as TextView).text.toString())
        assertFalse("a missing size is not invented", (descendants(window).single { it.tag == "jarvis-document-meta" } as TextView).text.contains("B"))
    }

    private fun draftTurn(prompt: at.heinz.deviceai.cognitive.DocumentDraftPrompt, name: String, episodeId: String? = null): Any =
        AgentActivity::class.java.declaredClasses.single { it.simpleName == "DocumentDraftTurn" }
            .getDeclaredConstructor(at.heinz.deviceai.cognitive.DocumentDraftPrompt::class.java, String::class.java, String::class.java)
            .apply { isAccessible = true }.newInstance(prompt, name, episodeId)

    /**
     * Result surface (owner flow): the draft text a local model returned (SIMULATED_MODEL_REPLY – no model on the host)
     * appears as its own labelled window, is edited in a dialog and written through Android's save dialog.
     */
    @Test fun theDraftIsItsOwnSurfaceIsEditedAndSavedThroughAndroid() = withCockpit { activity, root ->
        val text = "Projektübersicht Continuum. Ziel: ein lokales Cockpit auf dem Fold7."
        val prompt = requireNotNull(at.heinz.deviceai.cognitive.DocumentDraftPrompt.build("Projektübersicht.pdf", text))
        val reply = "Kernaussagen:\n- Ziel ist ein lokales Cockpit auf dem Fold7."
        val turnClass = AgentActivity::class.java.declaredClasses.single { it.simpleName == "DocumentDraftTurn" }
        val window = AgentActivity::class.java.getDeclaredMethod("presentDraftScene", turnClass, String::class.java)
            .apply { isAccessible = true }.invoke(activity, draftTurn(prompt, "Projektübersicht.pdf"), reply)
        assertNotNull("the draft opens as a cockpit surface", window)
        advance(1_660L); measure(root); draw(root)
        capture(root, "document-3-draft")
        val shown = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        val texts = descendants(shown).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), texts.contains("ENTWURF · Projektübersicht.pdf"))
        val draft = descendants(shown).single { it.tag == "jarvis-draft-text" } as TextView
        assertEquals(reply, draft.text.toString())
        assertTrue("labelled as a local draft", texts.any { it.startsWith("Lokal erzeugter Entwurf aus „Projektübersicht.pdf“") })
        assertEquals("Grundlage: der vollständige gelesene Text (${text.length} Zeichen).",
            (descendants(shown).single { it.tag == "jarvis-draft-basis" } as TextView).text.toString())
        descendants(shown).single { it.tag == "jarvis-draft-edit" }.performClick()
        advance(32L)
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as android.app.AlertDialog
        val editor = descendants(dialog.window!!.decorView).single { it.tag == "jarvis-draft-editor" } as EditText
        assertEquals(reply, editor.text.toString())
        editor.setText("$reply\n- Ergänzt vom Besitzer.")
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        advance(32L)
        assertEquals("$reply\n- Ergänzt vom Besitzer.", draft.text.toString())
        descendants(shown).single { it.tag == "jarvis-draft-save" }.performClick()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("text/markdown", started.intent.type)
        assertEquals("Entwurf – Projektübersicht.md", started.intent.getStringExtra(Intent.EXTRA_TITLE))
        val target = android.net.Uri.parse("content://test.documents/entwurf.md")
        val written = java.io.ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStream(target, written)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(target))
        val deadline = System.currentTimeMillis() + 5_000
        while (chatTexts(activity).none { it.startsWith("Entwurf gespeichert") || it.startsWith("Entwurf nicht gespeichert") } &&
            System.currentTimeMillis() < deadline) { Thread.sleep(20); advance(16L) }
        val expected = "$reply\n- Ergänzt vom Besitzer."
        assertTrue(chatTexts(activity).toString(), chatTexts(activity).any { it == "Entwurf gespeichert (${expected.length} Zeichen)." })
        assertEquals("exactly the edited draft is written", expected, written.toString(Charsets.UTF_8.name()))
    }

    @Test fun aDraftRequestWithoutALoadedModelSaysSoAndStartsNoModelTurn() = withCockpit { activity, root ->
        AgentActivity::class.java.getDeclaredField("pendingDocumentDraft").apply { isAccessible = true }.set(activity, true)
        val page = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val doc = PdfDocumentIntake.Document("Bericht.pdf", 2_048L, 2, page, "Ein kurzer Bericht.",
            PdfDocumentIntake.TextState.EXTRACTED, 2)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        advance(760L); measure(root); draw(root)
        val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        assertEquals("Entwurf: Lokales Modell nicht geladen · in der Modellverwaltung laden.",
            (descendants(window).single { it.tag == "jarvis-document-draft" } as TextView).text.toString())
        assertEquals("no model turn", false, field(activity, "busy"))
        assertEquals(null, field(activity, "documentDraftTurn"))
    }

    /**
     * Owner brief 06.11.2026 (memory × cockpit), HOST + SIMULATED_DOCUMENT_INPUT: the state a document task really
     * reached survives an app restart and answers „Wo waren wir heute?“ in a later conversation – from the work record,
     * shown as the VERLAUF surface and labelled as remembering (not model learning). No model is loaded on the host, so
     * the honest state is „kein lokales Modell“ with that as the open step.
     */
    @Test fun theWorkRecordSurvivesARestartAndAnswersWhereWeWere() {
        val request = "Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf."
        withCockpit { activity, root ->
            fun set(name: String, value: Any?) = AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
            set("pendingDocumentDraft", true)
            set("pendingDocumentRequest", request)
            val doc = PdfDocumentIntake.Document("Projektübersicht.pdf", 12_288L, 3, examplePage(200, 280),
                "Projektübersicht Continuum.", PdfDocumentIntake.TextState.EXTRACTED, 3)
            AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
                .apply { isAccessible = true }.invoke(activity, doc)
            val journal = java.io.File(activity.filesDir, "cockpit-work/journal-v1.jsonl")
            val deadline = System.currentTimeMillis() + 5_000
            while (!(journal.exists() && journal.readText().contains("NO_MODEL")) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20); advance(16L)
            }
            assertTrue("the reached state is written: ${journal.takeIf { it.exists() }?.readText()}", journal.readText().contains("NO_MODEL"))
        }
        // A new Activity instance – the app was closed in between – and a later conversation.
        withCockpit { activity, root ->
            (field(activity, "input") as EditText).setText("Wo waren wir heute?")
            AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, false)
            advance(760L); measure(root); draw(root)
            val answer = chatTexts(activity).lastOrNull { it.startsWith("Heute:") }
            assertNotNull("answered from the record: ${chatTexts(activity)}", answer)
            assertTrue(answer!!, answer.contains("„$request“ – Quelle: Projektübersicht.pdf, 3 Seiten – " +
                "Stand: Entwurf nicht erstellt – kein lokales Modell geladen – offen: lokales Modell laden, dann den Entwurf erstellen"))
            assertTrue(answer, answer.endsWith("(Aus dem Arbeitsverlauf dieses Geräts – Erinnerung, kein Modelllernen.)"))
            val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
            val texts = descendants(window).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("the VERLAUF surface: $texts", texts.contains("VERLAUF"))
            assertTrue(texts.toString(), texts.containsAll(listOf("Quelle: Projektübersicht.pdf, 3 Seiten",
                "Stand: Entwurf nicht erstellt – kein lokales Modell geladen",
                "Offen: lokales Modell laden, dann den Entwurf erstellen")))
            assertEquals("the cockpit stays for the scene", "START", field(activity, "activeWorkArea").toString())
            advance(900L); measure(root); draw(root)
            capture(root, "memory-1-verlauf-after-restart")
        }
    }

    /**
     * „Nein, ich meinte den letzten Entwurf.“ (owner brief 06.11.2026), HOST: while a draft for the wrong PDF is running,
     * the correction ends it under the STOP rules (recorded as aborted, not done), shows the draft the owner meant from
     * the work journal and keeps the correction on the corrected task.
     */
    @Test fun aCorrectionEndsTheWrongDraftAndShowsTheMeantOne() = withCockpit { activity, root ->
        val journal = at.heinz.deviceai.cognitive.CockpitWorkJournal(activity.filesDir)
        val meant = journal.begin(at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind.DOCUMENT,
            "Fasse die Projektübersicht zusammen", at.heinz.deviceai.cognitive.CockpitWorkJournal.State.DRAFTED,
            "Projektübersicht.pdf", 3, 68)
        journal.update(meant.id) { it.copy(draft = "Kernaussagen:\n- Ziel ist ein lokales Cockpit auf dem Fold7.", coveredChars = 68) }
        val wrong = journal.begin(at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind.DOCUMENT,
            "Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.",
            at.heinz.deviceai.cognitive.CockpitWorkJournal.State.DRAFTING, "Rechnung.pdf", 1, 400)
        val prompt = requireNotNull(at.heinz.deviceai.cognitive.DocumentDraftPrompt.build("Rechnung.pdf", "Rechnung 42"))
        AgentActivity::class.java.getDeclaredField("documentDraftTurn").apply { isAccessible = true }
            .set(activity, draftTurn(prompt, "Rechnung.pdf", wrong.id))
        AgentActivity::class.java.getDeclaredField("workEpisodeId").apply { isAccessible = true }.set(activity, wrong.id)
        (field(activity, "input") as EditText).setText("Nein, ich meinte den letzten Entwurf")
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, false)
        assertEquals("the wrong draft no longer waits for a reply", null, field(activity, "documentDraftTurn"))
        advance(760L); measure(root); draw(root)
        val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        val texts = descendants(window).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.toString(), texts.contains("ENTWURF · Projektübersicht.pdf"))
        assertEquals("Kernaussagen:\n- Ziel ist ein lokales Cockpit auf dem Fold7.",
            (descendants(window).single { it.tag == "jarvis-draft-text" } as TextView).text.toString())
        assertTrue(chatTexts(activity).toString(), chatTexts(activity).any { it.startsWith("Verstanden – du meinst den Entwurf von heute") &&
            it.endsWith("zu „Projektübersicht.pdf“. Hier ist er.") })
        val deadline = System.currentTimeMillis() + 5_000
        fun stored() = journal.all().single { it.id == wrong.id }
        while (stored().correction == null && System.currentTimeMillis() < deadline) { Thread.sleep(20); advance(16L) }
        assertEquals("aborted, not done", at.heinz.deviceai.cognitive.CockpitWorkJournal.State.ABORTED, stored().state)
        assertEquals("the owner's words stay with the corrected task", "Nein, ich meinte den letzten Entwurf", stored().correction)
        capture(root, "memory-2-correction-last-draft")
    }

    /**
     * Owner brief 06.11.2026 („Animationen sind fester Bestandteil“), HOST: remembering lights the cockpit's existing
     * recall channel with the real number of tasks found, an understood correction gives the avatar's confirming nod,
     * and a draft that failed never keeps reading as running.
     */
    @Test fun rememberingAndCorrectingAreVisibleAndAFailedDraftNeverReadsAsRunning() = withCockpit { activity, root ->
        val bus = field(activity, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus
        val events = java.util.concurrent.CopyOnWriteArrayList<at.heinz.deviceai.cockpit.CockpitAnimationEvent>()
        bus.subscribe { events += it }
        val journal = at.heinz.deviceai.cognitive.CockpitWorkJournal(activity.filesDir)
        val earlier = journal.begin(at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind.DOCUMENT, "Fasse die Projektübersicht zusammen",
            at.heinz.deviceai.cognitive.CockpitWorkJournal.State.DRAFTED, "Projektübersicht.pdf", 3, 68)
        journal.update(earlier.id) { it.copy(draft = "Kernaussagen: Cockpit auf dem Fold7.", coveredChars = 68) }
        fun say(text: String) {
            (field(activity, "input") as EditText).setText(text)
            AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, false)
            advance(400L); measure(root); draw(root)
        }
        say("Wo waren wir heute?")
        val recall = events.filterIsInstance<at.heinz.deviceai.cockpit.CockpitAnimationEvent.RecallHits>().single()
        assertEquals("the real number of tasks found", 1, recall.count)
        assertEquals(1f, recall.topScore)
        val director = field(activity, "avatarDirector")!!
        assertEquals(Long.MIN_VALUE, field(director, "lastConfirmationAtMillis"))
        say("Nein, ich meinte den letzten Entwurf")
        assertTrue("an understood correction nods", field(director, "lastConfirmationAtMillis") != Long.MIN_VALUE)

        val doc = PdfDocumentIntake.Document("Bericht.pdf", 2_048L, 2, examplePage(200, 280), "Ein kurzer Bericht.",
            PdfDocumentIntake.TextState.EXTRACTED, 2)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        advance(760L); measure(root); draw(root)
        val prompt = requireNotNull(at.heinz.deviceai.cognitive.DocumentDraftPrompt.build("Bericht.pdf", "Ein kurzer Bericht."))
        val episodeId = field(activity, "workEpisodeId") as String
        val turnClass = AgentActivity::class.java.declaredClasses.single { it.simpleName == "DocumentDraftTurn" }
        val window = AgentActivity::class.java.getDeclaredMethod("onDocumentDraftReply", turnClass, String::class.java)
            .apply { isAccessible = true }.invoke(activity, draftTurn(prompt, "Bericht.pdf", episodeId), "")
        assertEquals("no draft surface without a draft", null, window)
        val status = descendants(root).single { it.tag == "jarvis-document-draft" && it.isShown } as TextView
        assertEquals("Entwurf fehlgeschlagen – das lokale Modell hat keinen Entwurf geliefert. Nichts gespeichert.", status.text.toString())
        val deadline = System.currentTimeMillis() + 5_000
        while (journal.all().single { it.id == episodeId }.state != at.heinz.deviceai.cognitive.CockpitWorkJournal.State.FAILED &&
            System.currentTimeMillis() < deadline) { Thread.sleep(20); advance(16L) }
        assertEquals(at.heinz.deviceai.cognitive.CockpitWorkJournal.State.FAILED, journal.all().single { it.id == episodeId }.state)
    }
}
