package at.heinz.deviceai.devicetools

import android.content.ContentUris
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import at.heinz.deviceai.OwnerLibraryAccess
import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.OwnerWorkToolsDeviceUiTest
import at.heinz.deviceai.ToolCallRequest
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The Android side of the device tools with Android's own classes (Robolectric, API 35): package manager, storage
 * grant and walk, PdfDocument and the Download folder (a test provider behind the real ContentResolver), viewer and
 * share intents, clipboard, preferences. Not covered here: the text layer of a PDF (PdfRenderer has no host
 * implementation), the media index look-up of a foreign file and everything a real Fold7 decides – NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceToolsAndroidUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val written = HashMap<Long, ByteArrayOutputStream>()

    @Before fun configure() {
        OwnerWorkToolsDeviceUiTest.DownloadsProvider.rows.clear()
        OwnerWorkToolsDeviceUiTest.DownloadsProvider.nextId = 1L
        Robolectric.setupContentProvider(OwnerWorkToolsDeviceUiTest.DownloadsProvider::class.java, MediaStore.AUTHORITY)
        (1L..4L).forEach { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            shadowOf(app.contentResolver).registerOutputStreamSupplier(uri) { ByteArrayOutputStream().also { written[id] = it } }
            shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(written.getValue(id).toByteArray()) }
        }
    }

    private fun install(label: String, packageName: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
            nonLocalizedLabel = label
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), info)
    }

    private fun call(tools: NativeDeviceTools, name: String, vararg arguments: Pair<String, Any?>) =
        tools.execute(ToolCallRequest("android-$name", name, mapOf(*arguments)))

    @Test fun installedAppsAndTheSharedStorageAreSearchedThroughAndroidsOwnApis() {
        install("Experimente Labor", "at.example.experimente")
        install("Kamera", "com.sec.android.app.camera")
        // The launcher list also holds this app itself (its manifest); the two installed ones are found by label.
        assertEquals(listOf(DeviceApp("Experimente Labor", "at.example.experimente"), DeviceApp("Kamera", "com.sec.android.app.camera")),
            NativeDeviceTools.launcherApps(app).filterNot { it.packageName.startsWith("at.heinz.") })
        // Android's package manager and document classes; the storage is a real directory with a switchable grant
        // (this Robolectric cannot switch Android's own „Zugriff auf alle Dateien“ – that check is NOT_RUN here).
        val storage = TestStorage(OwnerLibraryAccess.MISSING)
        val tools = NativeDeviceTools(storage, { NativeDeviceTools.launcherApps(app) }, TestPdf(emptyList()),
            DeviceDocumentWriter(AndroidDeviceDocumentPort(app)), AndroidDeviceFileHandover(app), AndroidDeviceClipboard(app))
        // Without the grant nothing of the storage is listed; the search says so and still finds the app.
        assertFalse(tools.handles("find_files"))
        assertTrue(tools.handles("search_device") && tools.handles("read_file") && tools.handles("create_document"))
        assertEquals(DeviceToolPolicy.FILES_MISSING, call(tools, "find_files", "query" to "experimente"))
        val appsOnly = call(tools, "search_device", "query" to "experimente")
        assertTrue(appsOnly, appsOnly.contains("1. [APP] Experimente Labor · at.example.experimente") && appsOnly.contains("Dateien habe ich nicht durchsucht"))
        // With the grant the same tools walk the storage directory.
        storage.granted = OwnerLibraryAccess.GRANTED
        storage.text("Download/Experimente.txt", "Argon bei 4 bar")
        storage.text("Android/data/com.other/Experimente-geheim.txt", "fremd")
        assertTrue(tools.handles("find_files"))
        assertEquals("/sdcard/Download/Experimente.txt", call(tools, "find_files", "query" to "experimente"))
        val both = call(tools, "search_device", "query" to "experimente")
        assertTrue(both, both.startsWith("Gerätesuche „experimente“ – 2 Treffer:\n1. [DATEI] /sdcard/Download/Experimente.txt · Text · 15 Byte · geändert "))
        assertTrue(both, both.contains("2. [APP] Experimente Labor · at.example.experimente"))
        assertFalse("another app's private folder is never walked", both.contains("geheim"))
        assertTrue(call(tools, "read_file", "path" to "/sdcard/Download/Experimente.txt").endsWith("Inhalt (Zeichen 1–15 von 15):\nArgon bei 4 bar"))
        assertTrue(call(tools, "list_files", "path" to "/sdcard/Download").lines().last().endsWith(" Experimente.txt"))
    }

    @Test fun aRealWordFileIsWrittenToTheDownloadFolderReadBackAndShownAndAPdfIsLaidOutByAndroid() {
        val tools = NativeDeviceTools.android(app)
        // Android's own text layout decides the pages of the PDF: a long document needs several.
        val blocks = DeviceDocumentMarkup.layout("Einkauf", java.time.LocalDate.of(2026, 11, 19), "# Markt\n\n- Milch\n- Brot\n\n" + "Ein längerer Absatz. ".repeat(400))
        assertTrue(at.heinz.deviceai.OwnerDocumentPdf.plan(blocks).pages.size >= 2)
        // The PdfDocument container itself: honest evidence of what this host can do. Where it is missing, creating
        // a PDF fails BEFORE anything is saved – no entry, no half-written file.
        val pdf = runCatching { call(tools, "create_document", "title" to "Einkauf", "content" to "# Markt\n\n- Milch\n- Brot") }
        println("DEVICE-TOOL-PDF-CONTAINER " + (pdf.getOrNull()?.lineSequence()?.first() ?: ("not available on this host: " + pdf.exceptionOrNull())))
        val first = if (pdf.isSuccess) 2L else 1L
        if (pdf.isSuccess) {
            assertTrue(pdf.getOrThrow(), pdf.getOrThrow().contains("Geprüft: zurückgelesen, Inhalt stimmt überein"))
            assertEquals("%PDF-", String(written.getValue(1L).toByteArray(), 0, 5, Charsets.ISO_8859_1))
        } else assertTrue(OwnerWorkToolsDeviceUiTest.DownloadsProvider.rows.isEmpty() && written.isEmpty())

        val word = call(tools, "create_document", "title" to "Notiz", "content" to "Ein Satz.", "format" to "docx", "name" to "Meine Notiz")
        assertTrue(word, word.startsWith("Gespeichert: /sdcard/Download/Meine Notiz.docx\nName: Meine Notiz.docx\nOrt: Interner Speicher › Download\n"))
        assertTrue(word, word.contains("Geprüft: zurückgelesen, Inhalt stimmt überein"))
        assertEquals("PK", String(written.getValue(first).toByteArray(), 0, 2, Charsets.ISO_8859_1))
        val row = OwnerWorkToolsDeviceUiTest.DownloadsProvider.rows.getValue(first)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", row[MediaStore.MediaColumns.MIME_TYPE])
        assertEquals(0, row[MediaStore.MediaColumns.IS_PENDING])
        // The file just created is shown with the reference Android gave for it – no all-files grant, no file:// path.
        val shown = call(tools, "open_file", "path" to "/sdcard/Download/Meine Notiz.docx")
        assertTrue(shown, shown.startsWith("Geöffnet: /sdcard/Download/Meine Notiz.docx"))
        val view = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, first), view.data)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", view.type)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun viewerAndShareSheetGetAContentReferenceAndNothingIsSentByTheApp() {
        val handover = AndroidDeviceFileHandover(app)
        val saved = OwnerSavedFile("Bericht.pdf", "Download-Ordner", "content://media/external/downloads/7", "application/pdf")
        assertNull(handover.share(File("/sdcard/Download/Bericht.pdf"), saved))
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        @Suppress("DEPRECATION") assertEquals(Uri.parse(saved.uri), send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertNull("the app names no recipient and no target app", send.component ?: send.`package`)
        assertEquals("application/pdf", AndroidDeviceFileHandover.mime("a.PDF"))
        assertTrue(AndroidDeviceFileHandover.viewable("jpg") && AndroidDeviceFileHandover.viewable("docx"))
        assertFalse(AndroidDeviceFileHandover.viewable("apk") || AndroidDeviceFileHandover.viewable("html") || AndroidDeviceFileHandover.viewable(""))
    }

    @Test fun clipboardAndTheOwnersSwitchesLiveInAndroidAndStartSwitchedOff() {
        val clipboard = AndroidDeviceClipboard(app)
        clipboard.write("Zeile eins\nZeile zwei")
        assertEquals("Zeile eins\nZeile zwei", clipboard.read())
        val settings = StoredDeviceToolSettings(app)
        assertFalse(settings.cloudNames || settings.cloudContent || settings.documentsWithoutAsking)
        settings.cloudNames = true
        settings.documentsWithoutAsking = true
        val again = StoredDeviceToolSettings(app)
        assertTrue(again.cloudNames && again.documentsWithoutAsking && !again.cloudContent)
        again.cloudNames = false
        assertFalse(StoredDeviceToolSettings(app).cloudNames)
    }
}
