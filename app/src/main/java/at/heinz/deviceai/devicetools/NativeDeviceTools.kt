package at.heinz.deviceai.devicetools

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.webkit.MimeTypeMap
import at.heinz.deviceai.OwnerLibraryAccess
import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.PdfDocumentIntake
import at.heinz.deviceai.ToolCallRequest
import java.io.File
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Hands one file to another app of the owner. Nothing is sent by this app; null = started, otherwise the reason. */
internal interface DeviceFileHandover {
    /** Android's viewer for the file's type (ACTION_VIEW, read-only grant for exactly this file). */
    fun view(file: File, known: OwnerSavedFile?): String?
    /** Android's share sheet (ACTION_SEND chooser); the owner picks the target and sends there himself. */
    fun share(file: File, known: OwnerSavedFile?): String?
}

internal interface DeviceClipboard {
    /** The clipboard's text, or null when it is empty or Android does not hand it out right now. */
    fun read(): String?
    fun write(text: String)
}

internal class AndroidDeviceFileHandover(context: Context) : DeviceFileHandover {
    private val appContext = context.applicationContext

    override fun view(file: File, known: OwnerSavedFile?): String? {
        val uri = contentUri(file, known) ?: return NO_REFERENCE
        return start(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            "Ich habe keine App gefunden, die diesen Dateityp anzeigen kann.")
    }

    override fun share(file: File, known: OwnerSavedFile?): String? {
        val uri = contentUri(file, known) ?: return NO_REFERENCE
        val send = Intent(Intent.ACTION_SEND).setType(mime(file.name)).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        return start(Intent.createChooser(send, "Datei teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Android hat den Teilen-Dialog nicht geöffnet.")
    }

    /**
     * A content URI another app may read: the one this app got when it saved the file, otherwise the media index's
     * entry for the path (Android indexes the file and answers with its URI). A file:// path is never handed out.
     */
    private fun contentUri(file: File, known: OwnerSavedFile?): Uri? {
        known?.uri?.let { return Uri.parse(it) }
        val found = AtomicReference<Uri?>(null)
        val done = CountDownLatch(1)
        runCatching {
            MediaScannerConnection.scanFile(appContext, arrayOf(file.absolutePath), null) { _, uri -> found.set(uri); done.countDown() }
        }.onFailure { return null }
        done.await(INDEX_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        return found.get()
    }

    private fun start(intent: Intent, missing: String): String? = try {
        appContext.startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        missing
    } catch (error: Exception) {
        "Android hat den Start abgelehnt (${error.javaClass.simpleName})."
    }

    companion object {
        const val INDEX_WAIT_MILLIS = 4_000L
        const val NO_REFERENCE = "Android hat mir für diese Datei keinen teilbaren Verweis gegeben."
        private val known = mapOf("pdf" to "application/pdf", "txt" to "text/plain", "md" to "text/plain", "log" to "text/plain",
            "csv" to "text/csv", "json" to "application/json", "xml" to "text/xml",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation")

        fun mime(name: String): String {
            val extension = DevicePaths.extension(name)
            return known[extension] ?: runCatching { MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) }.getOrNull() ?: "*/*"
        }

        /**
         * Types a viewer only shows. An APK would start Android's installer and a web page can run scripts – both stay
         * with their own, confirmed tools; unknown types are not handed to whatever app claims them.
         */
        fun viewable(extension: String): Boolean = extension in VIEWABLE

        private val VIEWABLE = DeviceFileKind.PDF.extensions + DeviceFileKind.IMAGE.extensions + DeviceFileKind.VIDEO.extensions +
            DeviceFileKind.AUDIO.extensions + DeviceFileKind.WORD.extensions + DeviceFileKind.SHEET.extensions +
            DeviceFileKind.SLIDES.extensions + setOf("txt", "md", "log", "json", "xml", "epub")
    }
}

/** Created on the main thread (the clipboard service is bound there); read and written from the tool thread. */
internal class AndroidDeviceClipboard(context: Context) : DeviceClipboard {
    private val manager = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val appContext = context.applicationContext

    override fun read(): String? {
        val clipboard = manager ?: return null
        // Only the focused app may read. Right after the confirmation dialog the focus can still be on its way back.
        repeat(4) { attempt ->
            val text = runCatching { clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(appContext)?.toString() }.getOrNull()
            if (!text.isNullOrEmpty()) return text
            if (attempt < 3) Thread.sleep(150)
        }
        return null
    }

    override fun write(text: String) {
        checkNotNull(manager) { "Zwischenablage nicht verfügbar." }.setPrimaryClip(ClipData.newPlainText("Continuum", text))
    }
}

/**
 * The device tools that are answered inside the app: the file tools under Android's all-files grant, the device-wide
 * search, reading a file's text, creating a document, showing or sharing a file, the clipboard. One synchronous
 * [execute] per call on the ToolGate's worker thread; a stop interrupts that thread and every walk checks for it.
 * Whether a call may start at all (approval, consent, grant) is decided before, by the gate and [DeviceToolPolicy].
 */
internal class NativeDeviceTools(
    private val storage: DeviceStorage,
    private val apps: () -> List<DeviceApp>,
    private val pdf: PdfPageReader,
    private val documents: DeviceDocumentWriter,
    private val handover: DeviceFileHandover,
    private val clipboard: DeviceClipboard,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    walkLimits: DeviceFileWalk.Limits = DeviceFileWalk.Limits()
) {
    private val limits = walkLimits
    val files = DeviceFileTools(storage, zone, walkLimits)

    /** Set by the host: true while the result goes to a large-context brain; the on-device model gets short results. */
    @Volatile var largeContext: () -> Boolean = { false }

    /** Documents this app created in this run, by shown path: they can be shown or shared without the all-files grant. */
    private val created = object : LinkedHashMap<String, OwnerSavedFile>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OwnerSavedFile>?): Boolean = size > 32
    }

    /** Android's all-files grant right now; a check that breaks counts as „not granted“. */
    fun filesGranted(): Boolean = runCatching { storage.access() == OwnerLibraryAccess.GRANTED }.getOrDefault(false)

    /** [path] can be read right now even without the grant (a file this app wrote itself). */
    fun readable(path: String): Boolean = (shown(path)?.let { key -> synchronized(created) { created.containsKey(key) } } == true) ||
        runCatching { DevicePaths.resolve(path, storage.root())?.let { it.isFile && it.canRead() } == true }.getOrDefault(false)

    private fun shown(raw: String): String? = DevicePaths.relative(raw, storage.root())?.let(DevicePaths::shown)

    /** True when this call is answered here; false leaves it to the shell bridge. */
    fun handles(tool: String): Boolean = when (DeviceToolPolicy.home(tool)) {
        DeviceToolPolicy.Home.FILES_NATIVE_ONLY -> true
        DeviceToolPolicy.Home.FILES_NATIVE_OR_BRIDGE -> filesGranted()
        else -> tool in OWN
    }

    private fun budget(): Int = if (largeContext()) LARGE_RESULT else SMALL_RESULT

    fun execute(call: ToolCallRequest): String {
        val cancelled = { Thread.currentThread().isInterrupted }
        fun text(key: String): String? = call.arguments[key]?.toString()?.takeIf { it.isNotBlank() }
        fun number(key: String, fallback: Int): Int = when (val value = call.arguments[key]) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: fallback
            else -> fallback
        }
        fun roots(): List<String> = when (val raw = call.arguments["roots"]) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            is String -> raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
        // The tools that walk the storage need the grant; the precheck normally refuses earlier with the same words.
        val grantNeeded = setOf("list_files", "find_files", "find_latest_apk", "get_large_files", "move_file", "delete_file",
            "list_recent_files", "get_folder_overview", "search_file_text")
        if (call.name in grantNeeded && !filesGranted()) return DeviceToolPolicy.FILES_MISSING
        return when (call.name) {
            "list_files" -> files.listFiles(text("path").orEmpty(), cancelled)
            "find_files" -> files.findFiles(text("query").orEmpty(), roots(), number("limit", if (largeContext()) 100 else 25), cancelled)
            "find_latest_apk" -> files.findLatestApk(text("filter"), cancelled)
            "get_large_files" -> files.largeFiles(roots(), number("limit", if (largeContext()) 40 else 15), cancelled)
            "move_file" -> files.move(text("source").orEmpty(), text("destination").orEmpty())
            "delete_file" -> files.delete(text("path").orEmpty())
            "list_recent_files" -> files.recentFiles(text("filter"), number("limit", if (largeContext()) 20 else 8), cancelled)
            "get_folder_overview" -> files.folderOverview(text("path"), cancelled)
            "search_file_text" -> files.searchText(text("query").orEmpty(), text("path"), number("limit", 20), budget(), cancelled)
            "search_device" -> search(text("query").orEmpty(), text("scope"), number("limit", if (largeContext()) DeviceSearch.DEFAULT_LIMIT else 8), cancelled)
            "read_file" -> readFile(text("path").orEmpty(), text("pages"), number("offset", 0), cancelled)
            "open_file" -> handOver(text("path").orEmpty(), share = false)
            "share_file" -> handOver(text("path").orEmpty(), share = true)
            "create_document" -> documents.create(text("title").orEmpty(), call.arguments["content"]?.toString().orEmpty(), text("format"), text("name")).let { outcome ->
                outcome.created?.let { synchronized(created) { created[it.path] = it.file } }
                outcome.observation
            }
            "read_clipboard" -> clipboard.read()?.let { value ->
                val text = DeviceFileText.normalise(value)
                val room = budget() - 200
                if (text.length <= room) "Zwischenablage (${text.length} Zeichen):\n$text"
                else "Zwischenablage (Zeichen 1–$room von ${text.length}, ${DeviceFileText.CUT}):\n${text.take(room)}\n[${DeviceFileText.CUT}]"
            } ?: "Zwischenablage: leer – oder Android gibt sie mir gerade nicht (nur die App im Vordergrund darf sie lesen)."
            "write_clipboard" -> (call.arguments["text"]?.toString().orEmpty()).let { value ->
                clipboard.write(value)
                "In die Zwischenablage gelegt: ${value.length} Zeichen. Der vorherige Inhalt der Zwischenablage ist ersetzt."
            }
            else -> error("Tool nicht implementiert: ${call.name}")
        }
    }

    private fun search(query: String, scopeText: String?, limit: Int, cancelled: () -> Boolean): String {
        val analysed = DeviceSearch.Query(query, DeviceSearch.scope(scopeText))
        if (analysed.empty) return "Keine Suche: Der Suchtext enthält kein Wort."
        val collector = DeviceSearch.Collector(analysed, limit)
        val notes = ArrayList<String>()
        val searched = ArrayList<String>()
        if (analysed.scope != DeviceSearch.Scope.FILES) {
            val installed = runCatching(apps).getOrElse { notes += "Hinweis: Die App-Liste ließ sich nicht lesen."; emptyList() }
            installed.forEach(collector::offer)
            searched += "${installed.size} Apps"
        }
        if (analysed.scope != DeviceSearch.Scope.APPS) {
            if (filesGranted()) {
                var seen = 0
                val outcome = DeviceFileWalk.walk(storage.root(), storage.root(), limits, cancelled) { entry ->
                    seen++
                    collector.offer(entry.name, entry.relative.substringBeforeLast('/', ""), entry.directory) { entry.describe() }
                    true
                }
                searched += "$seen Dateien und Ordner"
                outcome.stoppedEarly?.let { notes += "Hinweis: Dateisuche $it." }
            } else notes += "Hinweis: Dateien habe ich nicht durchsucht – in Android ist „Zugriff auf alle Dateien“ für Continuum " +
                "nicht erlaubt („${DeviceToolCommand.SAY_FILE_ACCESS}“ öffnet die Seite)."
        }
        return DeviceSearch.render(query, collector.result(), zone(), searched.joinToString(", ").ifEmpty { "nichts" }, notes, budget())
    }

    private fun readFile(raw: String, pages: String?, offset: Int, cancelled: () -> Boolean): String {
        val file = DevicePaths.resolve(raw, storage.root()) ?: return DeviceFileTools.OUTSIDE
        if (!filesGranted() && !(file.isFile && file.canRead())) return DeviceToolPolicy.FILES_MISSING
        return DeviceFileText.read(file, DevicePaths.shown(DevicePaths.relativeOf(file, storage.root())), pages, offset.coerceAtLeast(0),
            budget(), pdf, cancelled)
    }

    private fun handOver(raw: String, share: Boolean): String {
        val file = DevicePaths.resolve(raw, storage.root()) ?: return DeviceFileTools.OUTSIDE
        val path = DevicePaths.shown(DevicePaths.relativeOf(file, storage.root()))
        val known = synchronized(created) { created[path] }
        val no = if (share) "Nicht geteilt" else "Nicht angezeigt"
        if (known == null && !filesGranted() && !(file.isFile && file.canRead())) return DeviceToolPolicy.FILES_MISSING
        if (known == null && !file.exists()) return "$no: $path gibt es nicht (mehr)."
        if (file.isDirectory) return "$no: $path ist ein Ordner; list_files zeigt, was darin liegt."
        val extension = DevicePaths.extension(file.name)
        if (!share && !AndroidDeviceFileHandover.viewable(extension)) return "$no: Dateien vom Typ „${DeviceFileKind.label(extension)}“ " +
            "gebe ich nicht von mir aus an eine Anzeige-App" + (if (extension == "apk") " – eine APK würde Androids Installation starten; dafür gibt es das Werkzeug mit eigener Freigabe." else ".")
        val problem = if (share) handover.share(file, known) else handover.view(file, known)
        if (problem != null) return "$no: $path – $problem"
        return if (share) "Teilen-Dialog gestartet: $path\nDas Ziel wählst du dort selbst; gesendet habe ich nichts."
            else "Geöffnet: $path\nAndroid hat die Anzeige-App gestartet; was sie zeigt, sehe ich nicht."
    }

    companion object {
        /** About what fits a cloud brain's turn next to the gate's 8 000-character limit. */
        const val LARGE_RESULT = 6_000
        /** The on-device model's tool-response channel is counted in bytes against a 4 096-token window. */
        const val SMALL_RESULT = 1_400
        private val OWN = setOf("search_device", "create_document", "read_clipboard", "write_clipboard")

        fun android(context: Context): NativeDeviceTools {
            val appContext = context.applicationContext
            return NativeDeviceTools(
                storage = AndroidDeviceStorage(appContext),
                apps = { launcherApps(appContext) },
                pdf = PdfPageReader { file, page ->
                    when (val read = PdfDocumentIntake(appContext.contentResolver).readPage(Uri.fromFile(file), page)) {
                        is PdfDocumentIntake.PageRead.Text -> PdfPageText.Text(read.text)
                        PdfDocumentIntake.PageRead.NoText -> PdfPageText.NoText
                        PdfDocumentIntake.PageRead.NeedsAndroid15 -> PdfPageText.NeedsAndroid15
                        is PdfDocumentIntake.PageRead.Missing -> PdfPageText.Missing(read.pageCount)
                    }
                },
                documents = DeviceDocumentWriter(AndroidDeviceDocumentPort(appContext)),
                handover = AndroidDeviceFileHandover(appContext),
                clipboard = AndroidDeviceClipboard(appContext))
        }

        /** The apps the owner sees in his launcher, with the names Android shows for them. */
        fun launcherApps(context: Context): List<DeviceApp> {
            val manager = context.packageManager
            @Suppress("DEPRECATION")
            val resolved = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            return resolved.mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(manager).toString() }.getOrNull()?.filterNot { it.isISOControl() }?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= 200 } ?: return@mapNotNull null
                DeviceApp(label, packageName)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.GERMAN) }.take(MAX_APPS)
        }

        const val MAX_APPS = 600
    }
}
