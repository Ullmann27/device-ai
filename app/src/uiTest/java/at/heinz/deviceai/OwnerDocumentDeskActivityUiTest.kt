package at.heinz.deviceai

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerLibraryFile
import at.heinz.deviceai.cognitive.OwnerPageSummary
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * The document desk in the real AgentActivity (Robolectric): the owner's turn typed into the real input field and
 * sent with the real button, the thin hooks, the single busy lane with STOP, the existing document scene and chat
 * binding, the real generation path with a controlled runtime, MediaStore download with read-back, and the return
 * from Android's settings. Stand-ins: the storage listing and PDF reader ([OwnerDeskFiles]), the model runtime and
 * the Download provider. Nothing here proves the Fold7, PdfRenderer or the on-device model – that stays NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerDocumentDeskActivityUiTest {
    private class Files : OwnerDeskFiles {
        @Volatile var granted = OwnerLibraryAccess.GRANTED
        val files = ArrayList<OwnerLibraryFile>()
        val pages = HashMap<String, List<String>>()
        @Volatile var scans = 0

        fun put(name: String, folder: String, texts: List<String>) {
            val path = "/storage/emulated/0/$folder/$name"
            files += OwnerLibraryFile(name, folder, path, 1_258_291,
                LocalDate.of(2026, 11, 17).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            pages[path] = texts
        }

        override fun access(): OwnerLibraryAccess = granted
        override fun accessSettings(): List<Intent> =
            listOf(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:at.heinz.deviceai")))
        override fun scan(cancelled: () -> Boolean, progress: (Int) -> Unit): OwnerLibraryScan {
            scans++
            progress(files.size)
            return OwnerLibraryScan(files.toList(), listOf("Download", "Documents"))
        }
        override fun readDocument(file: OwnerLibraryFile, pageWidthPx: Int, cancelled: () -> Boolean): Pair<String, PdfDocumentIntake.Document> {
            val texts = pages.getValue(file.path)
            return "file://${file.path}" to PdfDocumentIntake.Document(file.name, file.sizeBytes, texts.size, null,
                texts.filter { it.isNotBlank() }.joinToString("\n\n"), PdfDocumentIntake.TextState.EXTRACTED, texts.size, texts)
        }
        override fun readPage(uri: String, page: Int): OwnerDeskPage {
            val texts = pages[uri.removePrefix("file://")] ?: return OwnerDeskPage.Failed("nicht lesbar")
            return when {
                page !in 1..texts.size -> OwnerDeskPage.Missing(texts.size)
                texts[page - 1].isBlank() -> OwnerDeskPage.NoText
                else -> OwnerDeskPage.Text(texts[page - 1])
            }
        }
    }

    /** A loaded model that answers at once, or – with [hold] – keeps generating until released or cancelled. */
    private class Runtime : LocalModelRuntime {
        @Volatile override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("desk-test-model", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        @Volatile var hold = false
        @Volatile var cancels = 0
        private val pending = AtomicReference<LocalInferenceCallback?>()
        val running: Boolean get() = pending.get() != null

        private fun answer(prompt: String) = if (prompt.contains("Seite 3 des Dokuments")) SUMMARY_3 else SUMMARY_2
        private fun finish(callback: LocalInferenceCallback, prompt: String) {
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.TextDelta(answer(prompt)))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
        }
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("not loaded here")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("not unloaded here")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            if (hold) { check(pending.compareAndSet(null, callback)); state = LocalModelState.GENERATING } else finish(callback, prompt)
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = error("no tools")
        fun release() { pending.getAndSet(null)?.let { finish(it, prompts.last()) } }
        override fun cancel() {
            cancels++
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.Cancelled)
        }
    }

    private val today = OwnerDeliverable.SHORT_DATE.format(LocalDate.now())
    private val written = HashMap<Long, ByteArrayOutputStream>()
    private val files = Files()
    private val runtime = Runtime()
    private val argonLine = "„Argon4.pdf“ – Ordner Download · 1,2 MB · geändert am 17.11.2026"

    @Before fun configure() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        OwnerWorkToolsDeviceUiTest.DownloadsProvider.rows.clear()
        OwnerWorkToolsDeviceUiTest.DownloadsProvider.nextId = 1L
        Robolectric.setupContentProvider(OwnerWorkToolsDeviceUiTest.DownloadsProvider::class.java, MediaStore.AUTHORITY)
        (1L..6L).forEach { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            shadowOf(app.contentResolver).registerOutputStreamSupplier(uri) { ByteArrayOutputStream().also { written[id] = it } }
            // Reading back returns exactly what was written to that entry.
            shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(written.getValue(id).toByteArray()) }
        }
        files.put("Argon4.pdf", "Download", PAGES)
        files.put("Rechnung November.pdf", "Documents", listOf("Rechnung."))
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(activity: AgentActivity) = texts(field(activity, "chatContainer") as View)
    private fun names() = OwnerWorkToolsDeviceUiTest.DownloadsProvider.rows.values.map { it[MediaStore.MediaColumns.DISPLAY_NAME] }
    private fun openDocument(activity: AgentActivity) =
        (field(activity, "localConversationContext") as LocalConversationContext).document(field(activity, "sessionId") as String)

    private fun layout(activity: AgentActivity) {
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }

    private fun until(activity: AgentActivity, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            assertTrue("timed out waiting for: $what\n${chat(activity)}", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
            idle()
            layout(activity)
        }
        idle()
    }

    /** Types into the real input field and presses the real send button (which is STOP while a job runs). */
    private fun type(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        assertTrue((field(activity, "sendButton") as Button).performClick())
        idle()
    }

    private fun say(activity: AgentActivity, text: String) {
        type(activity, text)
        until(activity, "the lane is free after „$text“") { field(activity, "busy") == false && field(activity, "botLookupPending") == false }
    }

    private fun withActivity(check: (AgentActivity, ActivityController<AgentActivity>) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            idle()
            val activity = controller.get()
            activity.ownerDeskFilesOverride = files
            AgentActivity::class.java.getDeclaredField("localRuntime").apply { isAccessible = true }.set(activity, runtime)
            layout(activity)
            idle()
            check(activity, controller)
        } finally {
            runtime.hold = false
            runtime.cancel()
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    private fun open(activity: AgentActivity) {
        say(activity, "Öffne das PDF Argon4")
        until(activity, "the found PDF is bound to the chat") { openDocument(activity) != null }
    }

    @Test fun theSpokenOrderOpensTheNamedPdfAndThePageOrderSavesAReadableWordFile() = withActivity { activity, _ ->
        open(activity)
        assertTrue(chat(activity).toString(), chat(activity).contains("Gefunden: $argonLine. Ich öffne es."))
        // The existing reading path took over: scene message, journal and the document of this chat.
        assertTrue(chat(activity).toString(), chat(activity).any { it.startsWith("PDF gelesen: 4 Seiten · ") && it.contains("Argon4.pdf") })
        val document = openDocument(activity)!!
        assertEquals("Argon4.pdf", document.name)
        assertEquals(4, document.pageCount)
        assertEquals("file:///storage/emulated/0/Download/Argon4.pdf", document.documentUri)
        assertEquals(PAGES[1], document.pageText(2))
        assertEquals(1, files.scans)

        say(activity, "Fass Seite 2 zusammen und speichere es als Word")
        val name = "Zusammenfassung Argon4 Seite 2 $today.docx"
        assertEquals(chat(activity).toString(), listOf<Any?>(name), names())
        val row = OwnerWorkToolsDeviceUiTest.DownloadsProvider.rows.values.single()
        assertEquals("Download/", row[MediaStore.MediaColumns.RELATIVE_PATH])
        assertEquals(0, row[MediaStore.MediaColumns.IS_PENDING])
        val docx = written.getValue(1L).toByteArray()
        assertTrue(OwnerPageSummary.readableDocx(docx))
        val prompt = runtime.prompts.single()
        assertTrue(prompt.contains(PAGES[1]) && !prompt.contains(PAGES[0]) && !prompt.contains(PAGES[2]))
        val said = chat(activity).last { it.startsWith("Seite 2 von „Argon4.pdf“ – Zusammenfassung:") }
        assertTrue(said, said.contains(SUMMARY_2) && said.contains("Gespeichert als Word-Datei: „$name“ in deinem Download-Ordner " +
            "(Interner Speicher › Download)") && said.endsWith("Soll ich sie öffnen?"))
        assertFalse("read back and compared", said.contains("Zurücklesen konnte ich"))

        // „Ich meinte Seite 3“ changes the next action; the first file stays.
        say(activity, "Ich meinte Seite 3")
        val third = "Zusammenfassung Argon4 Seite 3 $today.docx"
        assertEquals(chat(activity).toString(), listOf<Any?>(name, third), names())
        assertTrue(runtime.prompts[1].contains(PAGES[2]) && !runtime.prompts[1].contains(PAGES[1]))
        val corrected = chat(activity).last { it.startsWith("Seite 3 von „Argon4.pdf“ – statt Seite 2 – Zusammenfassung:") }
        assertTrue(corrected, corrected.contains("Die Datei von vorhin (Seite 2, „$name“) gibt es weiterhin."))

        // The work tools know the saved file: „Ja“ opens it in the owner's viewer.
        val app = shadowOf(RuntimeEnvironment.getApplication())
        app.clearNextStartedActivities()
        say(activity, "Ja")
        val view = requireNotNull(app.nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 2L), view.data)
        assertTrue(chat(activity).toString(), chat(activity).contains("„$third“ ist geöffnet."))
        assertEquals(false, field(activity, "busy"))
    }

    @Test fun withoutTheGrantTheSettingIsOfferedAndAfterTheReturnTheSearchContinues() = withActivity { activity, controller ->
        val app = shadowOf(RuntimeEnvironment.getApplication())
        files.granted = OwnerLibraryAccess.MISSING
        say(activity, "Öffne das PDF Argon4")
        assertTrue(chat(activity).toString(), chat(activity).contains(OwnerDocumentDesk.GRANT_MISSING))
        assertEquals(0, files.scans)
        assertNull(openDocument(activity))
        app.clearNextStartedActivities()
        say(activity, "Ja")
        val settings = requireNotNull(app.nextStartedActivity)
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, settings.action)
        assertTrue(chat(activity).toString(), chat(activity).any { it.startsWith("Ich öffne die Android-Einstellung.") })
        assertEquals(0, files.scans)
        // The owner switches it on in Android's settings and comes back.
        files.granted = OwnerLibraryAccess.GRANTED
        controller.pause()
        controller.resume()
        until(activity, "the waiting search continued and opened the PDF") { openDocument(activity) != null }
        assertTrue(chat(activity).toString(), chat(activity).contains("Der Zugriff auf alle Dateien ist jetzt erlaubt. Ich suche „Argon4“."))
        assertTrue(chat(activity).contains("Gefunden: $argonLine. Ich öffne es."))
        assertEquals(1, files.scans)
        assertFalse(activity.getSharedPreferences("continuum-document-desk", Context.MODE_PRIVATE).contains("pending_query"))
    }

    @Test fun decliningTheSettingOpensNothingAndOffersAndroidsOwnPicker() = withActivity { activity, _ ->
        val app = shadowOf(RuntimeEnvironment.getApplication())
        files.granted = OwnerLibraryAccess.MISSING
        say(activity, "Öffne das PDF Argon4")
        app.clearNextStartedActivities()
        say(activity, "Nein")
        assertNull(app.nextStartedActivity)
        assertTrue(chat(activity).toString(), chat(activity).any { it.startsWith("In Ordnung – ich öffne nichts und habe nichts durchsucht.") })
        say(activity, "Ja")
        val picker = requireNotNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.action)
        assertEquals("application/pdf", picker.intent.type)
        assertEquals(0, files.scans)
        assertNull(openDocument(activity))
    }

    @Test fun stoppAndTheStopButtonEndTheRunningPageJobWithoutAFile() = withActivity { activity, _ ->
        open(activity)
        runtime.hold = true
        type(activity, "Fass Seite 2 zusammen und speichere es als Word")
        until(activity, "the model is generating") { runtime.running }
        assertEquals(true, field(activity, "busy"))
        // Typed (or dictated) „Stopp“ and the send button, which is STOP while the job runs.
        type(activity, "Stopp")
        until(activity, "the lane is free after Stopp") { field(activity, "busy") == false }
        assertTrue(chat(activity).toString(), chat(activity).contains("Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert."))
        assertTrue(names().isEmpty())
        assertTrue(runtime.cancels >= 1)
        assertEquals("", (field(activity, "input") as EditText).text.toString())

        // The STOP button itself (no text): the same end.
        type(activity, "Fass Seite 2 zusammen und speichere es als Word")
        until(activity, "the model is generating again") { runtime.running }
        assertTrue((field(activity, "sendButton") as Button).performClick())
        until(activity, "the lane is free after STOP") { field(activity, "busy") == false }
        assertEquals(2, chat(activity).count { it == "Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert." })
        assertTrue(names().isEmpty())
    }

    @Test fun aCorrectionWhileTheJobRunsReplacesItWithTheCorrectedPage() = withActivity { activity, _ ->
        open(activity)
        runtime.hold = true
        type(activity, "Fass Seite 2 zusammen und speichere es als Word")
        until(activity, "the model is generating page 2") { runtime.running }
        type(activity, "Nein, Seite 3")
        until(activity, "the corrected order reached the model") { runtime.prompts.size == 2 && runtime.running }
        assertTrue(chat(activity).toString(), chat(activity).contains("Verstanden – Seite 3 statt Seite 2. Den laufenden Auftrag breche ich dafür ab."))
        assertTrue(names().isEmpty())
        runtime.hold = false
        runtime.release()
        until(activity, "the lane is free") { field(activity, "busy") == false }
        assertEquals(listOf<Any?>("Zusammenfassung Argon4 Seite 3 $today.docx"), names())
        val said = chat(activity).last { it.startsWith("Seite 3 von „Argon4.pdf“ – statt Seite 2 – Zusammenfassung:") }
        assertTrue(said, said.contains("Der abgebrochene Auftrag (Seite 2) hat nichts gespeichert."))
        assertFalse(chat(activity).any { it.startsWith("Gestoppt.") })
        assertNotNull(openDocument(activity))
    }

    private companion object {
        val PAGES = listOf(
            "Argon4 – Überblick. Seite eins beschreibt den Aufbau des lokalen Gedächtnisses und seine Grenzen.",
            "Seite zwei erklärt den Abruf: Erinnerungen werden nach Zeit und Bedeutung geordnet. Der Abruf läuft vollständig " +
                "auf dem Gerät und braucht kein Netz.",
            "Seite drei behandelt Korrekturen: Eine spätere Aussage ersetzt die frühere nicht, sie wird daneben gelegt und markiert.",
            "")
        const val SUMMARY_2 = "Die zweite Seite beschreibt, wie Erinnerungen abgerufen werden. Sie werden nach Zeit und Bedeutung " +
            "geordnet. Der Abruf läuft vollständig auf dem Gerät und braucht kein Netz."
        const val SUMMARY_3 = "Die dritte Seite behandelt Korrekturen. Eine spätere Aussage ersetzt die frühere nicht, sondern " +
            "wird daneben gelegt und markiert."
    }
}
