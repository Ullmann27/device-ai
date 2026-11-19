package at.heinz.deviceai.devicetools

import at.heinz.deviceai.OwnerLibraryAccess
import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A temporary shared storage for host tests: real files and folders, a switchable grant. */
internal class TestStorage(var granted: OwnerLibraryAccess = OwnerLibraryAccess.GRANTED) : DeviceStorage {
    val dir: File = Files.createTempDirectory("continuum-device-storage").toFile()
    override fun access(): OwnerLibraryAccess = granted
    override fun root(): File = dir

    fun file(relative: String, content: ByteArray, modified: String = "2026-11-18T09:12"): File {
        val target = File(dir, relative)
        target.parentFile!!.mkdirs()
        target.writeBytes(content)
        check(target.setLastModified(millis(modified)))
        return target
    }

    fun text(relative: String, content: String, modified: String = "2026-11-18T09:12"): File = file(relative, content.toByteArray(Charsets.UTF_8), modified)

    fun folder(relative: String): File = File(dir, relative).apply { mkdirs() }

    fun delete() { dir.deleteRecursively() }

    companion object {
        val ZONE: ZoneId = ZoneOffset.UTC
        fun millis(iso: String): Long = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}

/** The Download folder of the document writer on the host: files are really written below [storage]`/Download`. */
internal class TestDocumentPort(private val storage: TestStorage, var readBackBroken: Boolean = false) : DeviceDocumentPort {
    val saved = ArrayList<OwnerSavedFile>()
    var pdfRenders = 0
    override fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int> {
        pdfRenders++
        // Not a real PDF: Android's PdfDocument exists only on the device and in the Robolectric test.
        return ("%PDF-TEST\n" + blocks.joinToString("\n") { it.style.name + ":" + it.text }).toByteArray(Charsets.UTF_8) to 1
    }
    override fun nameTaken(displayName: String): Boolean = File(storage.dir, "Download/$displayName").exists()
    override fun save(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile {
        val target = File(storage.folder("Download"), displayName)
        check(target.createNewFile()) { "Die Datei existiert bereits." }
        target.writeBytes(bytes)
        return OwnerSavedFile(displayName, "Download-Ordner", "content://media/external/downloads/${saved.size + 1}", mime).also { saved += it }
    }
    override fun readBack(file: OwnerSavedFile): ByteArray? = if (readBackBroken) null else File(storage.dir, "Download/${file.displayName}").readBytes()
    override fun location(file: OwnerSavedFile): String? = "Interner Speicher › Download"
}

internal class TestHandover : DeviceFileHandover {
    val viewed = ArrayList<Pair<File, OwnerSavedFile?>>()
    val shared = ArrayList<File>()
    var problem: String? = null
    override fun view(file: File, known: OwnerSavedFile?): String? = problem.also { if (it == null) viewed += file to known }
    override fun share(file: File, known: OwnerSavedFile?): String? = problem.also { if (it == null) shared += file }
}

internal class TestClipboard(var content: String? = null) : DeviceClipboard {
    override fun read(): String? = content
    override fun write(text: String) { content = text }
}

/** A PDF whose pages are the given texts ("" = a page without text layer). */
internal class TestPdf(private val pages: List<String>, private val android15: Boolean = true) : PdfPageReader {
    var reads = 0
    override fun read(file: File, page: Int): PdfPageText {
        reads++
        return when {
            page !in 1..pages.size -> PdfPageText.Missing(pages.size)
            !android15 -> PdfPageText.NeedsAndroid15
            pages[page - 1].isEmpty() -> PdfPageText.NoText
            else -> PdfPageText.Text(pages[page - 1])
        }
    }
}

internal fun testTools(storage: TestStorage, apps: List<DeviceApp> = emptyList(), pdf: PdfPageReader = TestPdf(emptyList()),
                       port: TestDocumentPort = TestDocumentPort(storage), handover: TestHandover = TestHandover(),
                       clipboard: TestClipboard = TestClipboard(), limits: DeviceFileWalk.Limits = DeviceFileWalk.Limits()): NativeDeviceTools =
    NativeDeviceTools(storage, { apps }, pdf, DeviceDocumentWriter(port) { LocalDate.of(2026, 11, 19) }, handover, clipboard, { TestStorage.ZONE }, limits)
