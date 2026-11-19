package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import at.heinz.deviceai.cognitive.OwnerLibraryFile
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException

/** The storage and PDF boundary of the document desk. Host tests replace it; the phone uses [OwnerDeviceLibrary]. */
internal interface OwnerDeskFiles {
    /** May be called off the UI thread. */
    fun access(): OwnerLibraryAccess
    /** Android's pages for the grant, the most specific first. Empty when this Android has no such grant. */
    fun accessSettings(): List<Intent>
    fun scan(cancelled: () -> Boolean, progress: (Int) -> Unit): OwnerLibraryScan
    /** Reads [file] like a picked PDF: its content URI for later reading and the read document. */
    fun readDocument(file: OwnerLibraryFile, pageWidthPx: Int, cancelled: () -> Boolean): Pair<String, PdfDocumentIntake.Document>
    /** The text of one page of the PDF at [uri] (1-based), read from the file itself. */
    fun readPage(uri: String, page: Int): OwnerDeskPage
}

/**
 * The owner's shared storage as the document desk sees it. Reading by path needs Android's „Zugriff auf alle
 * Dateien“ (MANAGE_EXTERNAL_STORAGE, Android 11+): a one-time grant the owner gives in Android's own settings and can
 * take back there at any time. Without it nothing is listed and nothing is read – [access] says so. No Shizuku, no
 * network, no write access is used here: the walk only lists names, sizes and dates, a PDF is opened read-only.
 */
internal class OwnerDeviceLibrary(private val context: Context) : OwnerDeskFiles {
    override fun access(): OwnerLibraryAccess {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return OwnerLibraryAccess.UNSUPPORTED
        return if (Environment.isExternalStorageManager()) OwnerLibraryAccess.GRANTED else OwnerLibraryAccess.MISSING
    }

    override fun accessSettings(): List<Intent> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    override fun scan(cancelled: () -> Boolean, progress: (Int) -> Unit): OwnerLibraryScan {
        check(access() == OwnerLibraryAccess.GRANTED) { "Zugriff auf alle Dateien ist nicht erlaubt" }
        @Suppress("DEPRECATION") val root = Environment.getExternalStorageDirectory()
        return OwnerLibraryWalker.scan(root, cancelled = cancelled, progress = progress)
    }

    override fun readDocument(file: OwnerLibraryFile, pageWidthPx: Int, cancelled: () -> Boolean): Pair<String, PdfDocumentIntake.Document> {
        val uri = Uri.fromFile(File(file.path))
        return uri.toString() to PdfDocumentIntake(context.contentResolver).read(uri, pageWidthPx, cancelled)
    }

    override fun readPage(uri: String, page: Int): OwnerDeskPage =
        when (val read = PdfDocumentIntake(context.contentResolver).readPage(Uri.parse(uri), page)) {
            is PdfDocumentIntake.PageRead.Text -> OwnerDeskPage.Text(read.text)
            PdfDocumentIntake.PageRead.NoText -> OwnerDeskPage.NoText
            PdfDocumentIntake.PageRead.NeedsAndroid15 -> OwnerDeskPage.NeedsAndroid15
            is PdfDocumentIntake.PageRead.Missing -> OwnerDeskPage.Missing(read.pageCount)
        }
}

/**
 * The bounded walk that lists the PDFs below a storage root. Breadth first, the usual document folders first, so the
 * likely places are done before a bound can end the walk. Skipped: `Android/` at the root (other apps' private data,
 * not readable anyway) and hidden folders. Bounded in depth, visited entries, listed PDFs and time; [cancelled] is
 * checked for every entry and ends the walk with a CancellationException. Plain java.io – no Android.
 */
internal object OwnerLibraryWalker {
    class Limits(val maxDepth: Int = 8, val maxEntries: Int = 120_000, val maxPdfs: Int = 5_000, val maxMillis: Long = 12_000L)

    private val preferred = listOf("download", "downloads", "documents", "dokumente")
    /**
     * Names that end like this are media or data files; asking the file system about each of them would only cost
     * time in folders with thousands of photos. A folder with such a name would not be entered – none is known.
     */
    private val plainFiles = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "dng", "mp4", "mov", "mkv", "webm",
        "mp3", "m4a", "opus", "ogg", "wav", "aac", "zip", "apk", "txt", "docx", "xlsx", "pptx", "json", "xml", "db", "log",
        "tmp", "nomedia", "thumb", "vcf", "csv", "html")

    fun scan(root: File, limits: Limits = Limits(), cancelled: () -> Boolean = { false }, progress: (Int) -> Unit = {},
             nanoTime: () -> Long = System::nanoTime): OwnerLibraryScan {
        val started = nanoTime()
        val rootPath = root.absolutePath.trimEnd('/')
        val files = ArrayList<OwnerLibraryFile>()
        val folders = ArrayList<String>()
        var visited = 0
        var reported = 0
        var stopped: String? = null
        // (directory, depth); depth 0 is the root itself.
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(root to 0)
        walk@ while (queue.isNotEmpty()) {
            val (directory, depth) = queue.removeFirst()
            if ((nanoTime() - started) / 1_000_000L > limits.maxMillis) {
                stopped = "Nach ${limits.maxMillis / 1000} Sekunden habe ich die Suche beendet; nicht alle Ordner wurden geprüft."
                break
            }
            val entries = directory.listFiles() ?: continue
            if (depth == 1) folders += directory.name
            val ordered = if (depth == 0) entries.sortedWith(compareBy<File>({ file ->
                preferred.indexOf(file.name.lowercase(Locale.ROOT)).let { if (it < 0) preferred.size else it } },
                { it.name.lowercase(Locale.ROOT) })) else entries.sortedBy { it.name.lowercase(Locale.ROOT) }
            for (entry in ordered) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Suche gestoppt")
                if (++visited > limits.maxEntries) {
                    stopped = "Nach ${limits.maxEntries} Einträgen habe ich die Suche beendet; nicht alle Ordner wurden geprüft."
                    break@walk
                }
                val name = entry.name
                if (name.startsWith(".")) continue
                if (name.endsWith(".pdf", ignoreCase = true) && entry.isFile) {
                    val folder = directory.absolutePath.trimEnd('/').removePrefix(rootPath).trim('/')
                    files += OwnerLibraryFile(name, folder, entry.absolutePath, entry.length(), entry.lastModified())
                    if (files.size - reported >= 25) { reported = files.size; progress(reported) }
                    if (files.size >= limits.maxPdfs) {
                        stopped = "Nach ${limits.maxPdfs} PDFs habe ich die Suche beendet; nicht alle Ordner wurden geprüft."
                        break@walk
                    }
                } else if (depth < limits.maxDepth && !(depth == 0 && name == "Android") &&
                    name.substringAfterLast('.', "").lowercase(Locale.ROOT) !in plainFiles && entry.isDirectory) {
                    queue.addLast(entry to depth + 1)
                }
            }
        }
        if (files.size != reported) progress(files.size)
        return OwnerLibraryScan(files, folders, stopped)
    }
}
