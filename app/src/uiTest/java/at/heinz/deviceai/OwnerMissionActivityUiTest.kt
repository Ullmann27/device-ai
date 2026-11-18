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
 * One owner sentence as a mission in the real AgentActivity (Robolectric): typed into the real input field and sent
 * with the real button, planned by the mission hook, every step handed to the real lane adapters – document desk on
 * the single busy lane, the existing document scene, the real generation path with a controlled runtime, MediaStore
 * download with read-back, the mail hand-over intent – with STOP, a correction while a step runs and the return from
 * Android's settings. Stand-ins: the storage listing and PDF reader ([OwnerDeskFiles]), the model runtime and the
 * Download provider. Nothing here proves the Fold7, the on-device model, the mail app or Gmail – that stays NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerMissionActivityUiTest {
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
        override val loadedModel = ImportedLocalModel("mission-test-model", "v1", "a".repeat(64), 1,
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
    private val anna = "anna.muster@example.org"
    private val order = "Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an $anna"
    private val word2 = "Zusammenfassung Argon4 Seite 2 $today.docx"
    private val word3 = "Zusammenfassung Argon4 Seite 3 $today.docx"
    private val plan = "Ich mache das in drei Schritten: 1. Das PDF „Argon4“ suchen und öffnen. 2. Seite 2 zusammenfassen und als " +
        "Word-Datei speichern. 3. Per E-Mail an $anna schicken."

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
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("timed out waiting for: $what\n${chat(activity).joinToString("\n")}", System.currentTimeMillis() < deadline)
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

    private fun said(activity: AgentActivity, start: String): String? = chat(activity).lastOrNull { it.startsWith(start) }

    private fun free(activity: AgentActivity) = field(activity, "busy") == false && field(activity, "botLookupPending") == false

    /** Every intent the app started since the last call, oldest first. */
    private fun started(): List<Intent> {
        val app = shadowOf(RuntimeEnvironment.getApplication())
        return generateSequence { app.nextStartedActivity }.toList().reversed()
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
            shadowOf(RuntimeEnvironment.getApplication()).clearNextStartedActivities()
            check(activity, controller)
        } finally {
            runtime.hold = false
            runtime.cancel()
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    @Test fun oneSentenceOpensThePdfSavesTheSummaryAndHandsTheMailOverInOrder() = withActivity { activity, _ ->
        type(activity, order)
        until(activity, "the mission reported its end") { said(activity, "Fertig – ") != null && free(activity) }
        val chat = chat(activity)
        // The owner's sentence is shown once; no sub-order appears as a turn of its own.
        assertEquals(chat.joinToString("\n"), 1, chat.count { it == order })
        assertFalse(chat.any { it.startsWith("fass Seite 2 zusammen") || it.startsWith("schick es per Mail") })
        // Plan, then the lanes' own messages in order, then the report.
        val planAt = chat.indexOf(plan)
        val foundAt = chat.indexOfFirst { it.startsWith("Gefunden: „Argon4.pdf“") && it.endsWith("Ich öffne es.") }
        val summaryAt = chat.indexOfFirst { it.startsWith("Seite 2 von „Argon4.pdf“ – Zusammenfassung:") }
        val mailAt = chat.indexOfFirst { it.startsWith("Die E-Mail ist vorbereitet: Betreff „Zusammenfassung Argon4 Seite 2 $today“") }
        val reportAt = chat.indexOfFirst { it.startsWith("Fertig – ") }
        assertTrue(chat.joinToString("\n"), planAt >= 0 && planAt < foundAt && foundAt < summaryAt && summaryAt < mailAt && mailAt < reportAt)
        assertEquals("Fertig – alle drei Schritte sind erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). 2. „$word2“ gespeichert " +
            "(Interner Speicher › Download). 3. E-Mail an $anna im Mailprogramm vorbereitet, diese Datei hängt an – gesendet habe ich nichts.",
            chat[reportAt])
        // The desk's own offer to open the file is not asked in between.
        assertTrue(chat[summaryAt], chat[summaryAt].contains(SUMMARY_2) && chat[summaryAt].contains("Gespeichert als Word-Datei: „$word2“") &&
            !chat[summaryAt].contains("Soll ich sie öffnen?"))
        // What really happened behind it: the PDF is bound to the chat, one file was written and read back, one model
        // generation on page 2, and the mail composer got the address and exactly that file. Nothing was sent.
        assertEquals("Argon4.pdf", openDocument(activity)!!.name)
        assertEquals(listOf<Any?>(word2), names())
        assertEquals(1, files.scans)
        assertTrue(runtime.prompts.single().contains(PAGES[1]))
        val mail = started().single { it.action == Intent.ACTION_SEND }
        assertEquals(listOf(anna), mail.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 1L), mail.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(mail.getStringExtra(Intent.EXTRA_TEXT)!!.contains(SUMMARY_2))
        assertEquals("", (field(activity, "input") as EditText).text.toString())
        assertFalse(activity.ownerMission.active)

        // The finished mission can still be corrected: the recipient changes, the file stays.
        type(activity, "An bernd.beispiel@example.org, nicht an $anna")
        until(activity, "the mail was prepared again") { chat(activity).count { it.startsWith("Fertig – ") } == 2 && free(activity) }
        val second = started().single { it.action == Intent.ACTION_SEND }
        assertEquals(listOf("bernd.beispiel@example.org"), second.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 1L), second.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(listOf<Any?>(word2), names())
        assertTrue(chat(activity).last { it.startsWith("Fertig – ") }.contains("Von vorher gibt es weiterhin: E-Mail an $anna im Mailprogramm vorbereitet"))
    }

    @Test fun stoppAndTheStopButtonEndTheMissionAndStartNoFurtherStep() = withActivity { activity, _ ->
        runtime.hold = true
        type(activity, order)
        until(activity, "the model is generating the summary") { runtime.running }
        assertTrue(activity.ownerMission.active)
        type(activity, "Stopp")
        until(activity, "the mission reported the stop") { said(activity, "Die Auftragsfolge ist gestoppt.") != null && free(activity) }
        val stopped = "Die Auftragsfolge ist gestoppt. Erledigt: 1. „Argon4.pdf“ geöffnet (Ordner Download). Abgebrochen: 2. Seite 2 " +
            "zusammenfassen und als Word-Datei speichern. Nicht begonnen: 3. Per E-Mail an $anna schicken."
        assertEquals(stopped, said(activity, "Die Auftragsfolge ist gestoppt."))
        assertTrue(chat(activity).joinToString("\n"), chat(activity).contains("Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert."))
        assertTrue(names().isEmpty())
        assertTrue(started().none { it.action == Intent.ACTION_SEND })
        assertFalse(activity.ownerMission.active)
        assertTrue(runtime.cancels >= 1)

        // The STOP button itself (no text) while the same step runs: the same end, said once more.
        type(activity, order)
        until(activity, "the model is generating again") { runtime.running }
        assertTrue((field(activity, "sendButton") as Button).performClick())
        until(activity, "the mission reported the second stop") { chat(activity).count { it == stopped } == 2 && free(activity) }
        assertTrue(names().isEmpty())
        assertTrue(started().none { it.action == Intent.ACTION_SEND })
    }

    @Test fun aCorrectionWhileTheSummaryRunsChangesTheSavedFileAndTheMail() = withActivity { activity, _ ->
        runtime.hold = true
        type(activity, order)
        until(activity, "the model is generating page 2") { runtime.running }
        type(activity, "Nein, Seite 3")
        until(activity, "the corrected page reached the model") { runtime.prompts.size == 2 && runtime.running }
        assertTrue(chat(activity).joinToString("\n"), chat(activity).contains("Verstanden – Seite 3 statt Seite 2. Den laufenden Auftrag breche ich dafür ab."))
        assertTrue(names().isEmpty())
        runtime.hold = false
        runtime.release()
        until(activity, "the mission reported its end") { said(activity, "Fertig – ") != null && free(activity) }
        assertEquals(listOf<Any?>(word3), names())
        assertEquals("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). " +
            "2. „$word3“ gespeichert (Interner Speicher › Download). 3. E-Mail an $anna im Mailprogramm vorbereitet, diese Datei hängt an – " +
            "gesendet habe ich nichts.", said(activity, "Fertig – "))
        val mail = started().single { it.action == Intent.ACTION_SEND }
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 1L), mail.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(mail.getStringExtra(Intent.EXTRA_TEXT)!!.contains(SUMMARY_3))
        assertFalse(chat(activity).any { it.startsWith("Gestoppt.") || it.startsWith("Die Auftragsfolge ist gestoppt") })
    }

    @Test fun withoutTheStorageGrantTheMissionWaitsAndContinuesAfterTheReturnFromAndroidsSettings() = withActivity { activity, controller ->
        files.granted = OwnerLibraryAccess.MISSING
        type(activity, order)
        until(activity, "the desk asked for the grant") { chat(activity).contains(OwnerDocumentDesk.GRANT_MISSING) && free(activity) }
        assertTrue(activity.ownerMission.active)
        assertEquals(0, files.scans)
        // An unrelated turn in between: the mission says once that it is waiting.
        type(activity, "Wie spät ist es?")
        until(activity, "the unrelated turn was answered") { free(activity) }
        assertEquals(1, chat(activity).count { it.startsWith("Die Auftragsfolge wartet noch bei Schritt 1 von 3 (Das PDF „Argon4“ suchen und öffnen).") })
        // „weiter“ hands the step to the desk again; it asks again, and „Ja“ opens Android's setting.
        type(activity, "weiter")
        until(activity, "the desk asked again") { chat(activity).count { it == OwnerDocumentDesk.GRANT_MISSING } == 2 && free(activity) }
        started()
        type(activity, "Ja")
        until(activity, "the settings page was started") { free(activity) }
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, started().single().action)
        assertNull(openDocument(activity))
        // The owner switches it on and comes back: the search continues, and with it the whole mission.
        files.granted = OwnerLibraryAccess.GRANTED
        controller.pause()
        controller.resume()
        until(activity, "the mission reported its end") { said(activity, "Fertig – ") != null && free(activity) }
        assertEquals(listOf<Any?>(word2), names())
        assertTrue(started().any { it.action == Intent.ACTION_SEND })
        assertTrue(said(activity, "Fertig – ")!!.startsWith("Fertig – alle drei Schritte sind erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download)."))
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
