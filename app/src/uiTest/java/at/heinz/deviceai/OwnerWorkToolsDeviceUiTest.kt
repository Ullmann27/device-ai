package at.heinz.deviceai

import android.app.AlertDialog
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.cognitive.NewsSourceRef
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowPowerManager
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * The work tools in the real AgentActivity and with Android's own classes (Robolectric): chat routing and web
 * consent for the news order, PdfDocument/StaticLayout output, MediaStore download, viewer and mail hand-over.
 * No network is used; the Download folder is a test provider behind the real ContentResolver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerWorkToolsDeviceUiTest {
    @Implements(PowerManager::class)
    class ThermalPowerManager : ShadowPowerManager() {
        @Implementation(minSdk = 29)
        protected fun addThermalStatusListener(executor: Executor, listener: PowerManager.OnThermalStatusChangedListener) {
            super.addThermalStatusListener(listener)
        }
    }

    /** MediaStore.Downloads stand-in: rows as the real provider keeps them (path with trailing slash). */
    class DownloadsProvider : ContentProvider() {
        companion object {
            val rows = linkedMapOf<Long, MutableMap<String, Any?>>()
            var nextId = 1L
        }
        private fun id(uri: Uri): Long? = uri.lastPathSegment?.toLongOrNull()
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = rows[id(uri)]?.get(MediaStore.MediaColumns.MIME_TYPE) as String?
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val id = nextId++
            rows[id] = values!!.keySet().associateWith { values.get(it) }.toMutableMap().also {
                it[MediaStore.MediaColumns.RELATIVE_PATH] = it[MediaStore.MediaColumns.RELATIVE_PATH].toString().trimEnd('/') + "/"
            }
            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            val row = rows[id(uri)] ?: return 0
            values!!.keySet().forEach { row[it] = values.get(it) }
            return 1
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = if (rows.remove(id(uri)) != null) 1 else 0
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor {
            val columns = projection ?: arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val cursor = MatrixCursor(columns)
            val matching = id(uri)?.let { listOfNotNull(rows[it]) }
                ?: rows.values.filter { args == null || it[MediaStore.MediaColumns.DISPLAY_NAME] == args[0] }
            matching.forEach { row -> cursor.addRow(columns.map { row[it] }) }
            return cursor
        }
    }

    private val today = OwnerDeliverable.SHORT_DATE.format(LocalDate.now())
    private val written = HashMap<Long, ByteArrayOutputStream>()

    @Before fun configure() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        DownloadsProvider.rows.clear()
        DownloadsProvider.nextId = 1L
        Robolectric.setupContentProvider(DownloadsProvider::class.java, MediaStore.AUTHORITY)
        // Robolectric's resolver hands out registered streams: the next ids of the Download folder are captured.
        (1L..6L).forEach { id ->
            shadowOf(app.contentResolver).registerOutputStreamSupplier(
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)) {
                ByteArrayOutputStream().also { written[id] = it }
            }
        }
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

    private fun withActivity(internet: String, check: (AgentActivity) -> Unit) {
        RuntimeEnvironment.getApplication().getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit()
            .putString("mode", internet).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            idle()
            check(controller.get())
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    /** Types into the real input field and presses the real send button; waits for the busy lane to finish. */
    private fun say(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        assertTrue((field(activity, "sendButton") as Button).performClick())
        idle()
        repeat(200) {
            if (field(activity, "busy") == false) return
            Thread.sleep(25)
            idle()
        }
    }

    @Test fun newsOrderInTheChatRespectsTheInternetSettingAndAsksWithTheFeedExplanation() {
        withActivity("OFF") { activity ->
            say(activity, "Fass mir die Nachrichten von heute zusammen")
            val rows = chat(activity)
            assertTrue(rows.toString(), rows.contains("Fass mir die Nachrichten von heute zusammen"))
            assertTrue(rows.toString(), rows.any { it.startsWith("Internetzugang ist AUS, deshalb habe ich keine Nachrichten abgerufen.") })
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
            assertEquals(false, field(activity, "busy"))
        }
        withActivity("ASK") { activity ->
            say(activity, "Was gibt's Neues in der Welt?")
            val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            val message = shadowOf(dialog).message.toString()
            assertTrue(message, message.startsWith("Für die Nachrichten lese ich die öffentlichen Feeds von ORF, tagesschau, " +
                "Deutsche Welle, BBC News. Es wird nur gelesen:"))
            // The grant text is the fixed label, not the owner's sentence.
            assertTrue(message, message.endsWith("Nachrichten von heute (öffentliche Nachrichten-Feeds)"))
            assertFalse(message.contains("Was gibt's Neues"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertTrue(chat(activity).toString(), chat(activity).contains(
                "Nachrichten nicht abgerufen: Der Internetzugriff wurde für diese Anfrage nicht freigegeben."))
            assertEquals(false, field(activity, "busy"))
        }
    }

    /**
     * Robolectric 4.14.1 has no native PdfDocument (it reports „document is closed!“), so the chat flow is driven
     * with the Word format here; the same adapter, job lane, MediaStore code and wording serve the PDF order.
     * What the PDF order does when Android cannot produce the PDF is asserted first: the real reason, no file.
     */
    @Test fun theLastAnswerIsSavedInDownloadsOpenedAndHandedToTheMailApp() = withActivity("OFF") { activity ->
        val app = shadowOf(RuntimeEnvironment.getApplication())
        // A result in the chat: the honest offline reply of the news order (long enough to be worth keeping).
        say(activity, "Fass mir die Nachrichten von heute zusammen")
        say(activity, "Speichere das als PDF")
        assertTrue(chat(activity).toString(), chat(activity).any {
            it.startsWith("Das Speichern hat nicht geklappt:") && it.endsWith("Es wurde keine Datei angelegt.") })
        assertTrue(DownloadsProvider.rows.isEmpty())
        say(activity, "Speichere die Antwort als Word-Datei")
        val name = "Continuum Notiz $today.docx"
        val row = DownloadsProvider.rows.values.single()
        assertEquals(name, row[MediaStore.MediaColumns.DISPLAY_NAME])
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", row[MediaStore.MediaColumns.MIME_TYPE])
        assertEquals("Download/", row[MediaStore.MediaColumns.RELATIVE_PATH])
        assertEquals(0, row[MediaStore.MediaColumns.IS_PENDING])
        val bytes = written.getValue(1L).toByteArray()
        assertEquals("PK", String(bytes, 0, 2, Charsets.ISO_8859_1))
        val document = java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "word/document.xml" }
            String(zip.readBytes(), Charsets.UTF_8)
        }
        assertTrue(document.contains("Internetzugang ist AUS") && document.contains("Continuum Notiz"))
        val saved = chat(activity).last { it.startsWith("Erledigt.") }
        assertEquals("Erledigt. „$name“ liegt jetzt in deinem Download-Ordner (Word-Datei). Darin steht meine letzte Antwort " +
            "aus diesem Gespräch. Soll ich sie öffnen?", saved)
        // A second save never overwrites.
        say(activity, "Speichere das nochmal als Word-Datei")
        assertEquals(listOf(name, "Continuum Notiz $today (2).docx"),
            DownloadsProvider.rows.values.map { it[MediaStore.MediaColumns.DISPLAY_NAME] })
        // „Ja“ opens the file that was just saved in the owner's viewer, with a read grant.
        app.clearNextStartedActivities()
        say(activity, "Ja")
        val view = requireNotNull(app.nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 2L), view.data)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", view.type)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // The mail hand-over opens the composer with recipient, subject, text and the saved file; nothing is sent.
        say(activity, "Schick das per Mail an anna.muster@example.org")
        val mail = requireNotNull(app.nextStartedActivity)
        assertEquals(Intent.ACTION_SEND, mail.action)
        assertEquals("mailto:", mail.selector!!.dataString)
        assertEquals(listOf("anna.muster@example.org"), mail.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals("Continuum Notiz $today", mail.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(mail.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Internetzugang ist AUS"))
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, 2L),
            mail.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", mail.type)
        assertTrue(mail.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(mail.clipData)
        assertTrue(chat(activity).last { it.startsWith("Die E-Mail ist vorbereitet") }
            .endsWith("Gesendet habe ich nichts – du musst im Mailprogramm nur noch auf Senden tippen."))
    }

    /**
     * Measuring (StaticLayout with real fonts) and drawing of the PDF pages on a bitmap canvas. The PdfDocument
     * container itself cannot be produced by Robolectric 4.14.1 – writing the PDF bytes is NOT covered on the host.
     */
    @Test fun pdfPagesAreMeasuredPaginatedAndDrawnInsideTheA4Margins() {
        val body = (1..60).map { "Absatz $it: Die Nationalbank von Musterstadt hat den Leitzins gesenkt. Kredite könnten " +
            "damit günstiger werden, sagen Fachleute, und die Märkte reagierten am Vormittag gelassen auf den Schritt." }
        val blocks = OwnerDocumentLayout.blocks(OwnerDeliverable(OwnerDeliverable.Kind.NEWS_BRIEFING, "Nachrichten von heute",
            LocalDate.of(2026, 11, 18), body, (1..12).map { NewsSourceRef("ORF", "Meldung $it", "https://orf.at/stories/900000$it/") },
            note = "Zusammenfassung des lokalen Modells aus den unten genannten Meldungen"))
        val plan = OwnerDocumentPdf.plan(blocks)
        // Real line breaking: a 190-character paragraph needs several lines at 483 pt, the title one.
        assertEquals(1, plan.layouts[0].lineCount)
        assertTrue(plan.layouts[3].lineCount in 2..4)
        assertTrue(plan.layouts.all { it.width == OwnerDocumentLayout.CONTENT_WIDTH.toInt() })
        assertTrue("several pages: ${plan.pages.size}", plan.pages.size >= 4)
        // Every line of every block is on exactly one page, in order, and nothing reaches into the footer.
        val placed = plan.pages.flatMap { it.slices }.flatMap { slice -> (slice.firstLine..slice.lastLine).map { slice.block to it } }
        assertEquals(plan.layouts.flatMapIndexed { block, layout -> (0 until layout.lineCount).map { block to it } }, placed)
        plan.pages.forEach { page -> page.slices.forEach { slice ->
            val layout = plan.layouts[slice.block]
            assertTrue(slice.top + layout.getLineBottom(slice.lastLine) - layout.getLineTop(slice.firstLine) <= OwnerDocumentLayout.CONTENT_HEIGHT)
        } }
        // The source list starts with its heading on the same page as its first entry.
        val heading = blocks.indexOfFirst { it.style == OwnerDocumentLayout.Style.HEADING }
        val headingPage = plan.pages.indexOfFirst { page -> page.slices.any { it.block == heading } }
        assertTrue(plan.pages[headingPage].slices.any { it.block == heading + 1 })
        // Drawing: ink only inside the margins, text in the content area, the page number in the footer band.
        val bitmap = android.graphics.Bitmap.createBitmap(OwnerDocumentLayout.PAGE_WIDTH, OwnerDocumentLayout.PAGE_HEIGHT,
            android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap).apply { drawColor(android.graphics.Color.WHITE) }
        OwnerDocumentPdf.drawPage(canvas, plan, 0)
        fun ink(left: Int, top: Int, right: Int, bottom: Int): Int {
            var count = 0
            for (y in top until bottom) for (x in left until right) if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) count++
            return count
        }
        val margin = OwnerDocumentLayout.MARGIN.toInt()
        val contentBottom = OwnerDocumentLayout.PAGE_HEIGHT - margin - OwnerDocumentLayout.FOOTER_HEIGHT.toInt()
        println("PDF-PAGE-EVIDENCE pages=${plan.pages.size} contentInk=${ink(margin, margin, 595 - margin, contentBottom)} " +
            "footerInk=${ink(margin, contentBottom, 595 - margin, 842 - margin)} marginInk=${ink(0, 0, margin - 2, 842) + ink(0, 0, 595, margin - 2)}")
        assertTrue(ink(margin, margin, 595 - margin, contentBottom) > 2_000)
        assertTrue(ink(margin, contentBottom, 595 - margin, 842 - margin) in 20..2_000)
        assertEquals(0, ink(0, 0, margin - 2, 842) + ink(0, 0, 595, margin - 2) + ink(595 - margin + 2, 0, 595, 842) +
            ink(0, 842 - margin + 2, 595, 842))
        // A short document is one page.
        assertEquals(1, OwnerDocumentPdf.plan(OwnerDocumentLayout.blocks(OwnerDeliverable(
            OwnerDeliverable.Kind.ASSISTANT_REPLY, "Continuum Notiz", LocalDate.of(2026, 11, 18), listOf("Ein kurzer Absatz.")))).pages.size)
        // The PdfDocument container: honest evidence of what this host can and cannot do.
        val container = runCatching { OwnerDocumentPdf.render(blocks) }
        println("PDF-CONTAINER-EVIDENCE " + (container.getOrNull()?.let { "bytes=${it.bytes.size} pages=${it.pages}" }
            ?: ("not available on this host: " + container.exceptionOrNull())))
        container.getOrNull()?.let { rendered ->
            assertEquals("%PDF-", String(rendered.bytes, 0, 5, Charsets.ISO_8859_1))
            assertEquals(plan.pages.size, rendered.pages)
        }
    }
}
