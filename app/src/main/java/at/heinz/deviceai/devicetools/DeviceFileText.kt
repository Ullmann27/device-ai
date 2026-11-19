package at.heinz.deviceai.devicetools

import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** One page of a PDF as the platform's text layer gives it. */
internal sealed interface PdfPageText {
    data class Text(val text: String) : PdfPageText
    data object NoText : PdfPageText
    data object NeedsAndroid15 : PdfPageText
    data class Missing(val pageCount: Int) : PdfPageText
}

/** The PDF boundary of the read tool: the phone uses PdfDocumentIntake (PdfRenderer), host tests a fixed document. */
internal fun interface PdfPageReader {
    /** Page [page] (1-based) of [file]; [PdfPageText.Missing] carries the real page count. */
    fun read(file: File, page: Int): PdfPageText
}

/**
 * „Sich holen“: the text of one file for the brain – plain text formats, a PDF's own text layer (by page) and the
 * text of a Word file (.docx). Nothing is guessed: a binary file is refused, a scan without text layer says so, and
 * what does not fit the budget is cut with a marker that names how to read on. Pure java.io/zip plus [PdfPageReader].
 */
internal object DeviceFileText {
    const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    const val MAX_DOCX_XML_BYTES = 8 * 1024 * 1024
    const val MAX_PAGES_PER_CALL = 10
    /** Marker of a cut: the same word in every tool, so the brain and the owner can rely on it. */
    const val CUT = "gekürzt"

    private val textExtensions = DeviceFileKind.TEXT.extensions + setOf("csv", "tsv", "srt", "vtt", "tex", "rtf", "conf",
        "cfg", "properties", "gradle", "kts", "c", "h", "cpp", "rs", "go", "ts", "tsx", "jsx", "sql", "bat", "toml", "vcf", "ics")

    /** An inclusive page range, already clamped to the document and to [MAX_PAGES_PER_CALL]. */
    data class PageRange(val first: Int, val last: Int) {
        override fun toString(): String = "$first-$last"
    }

    /** „3“, „2-4“, „2–4“, „2 bis 4“; null when it is no range of this document. */
    fun pageRange(text: String, pageCount: Int): PageRange? {
        val match = Regex("^\\s*(\\d{1,5})(?:\\s*(?:-|–|—|bis)\\s*(\\d{1,5}))?\\s*$").matchEntire(text) ?: return null
        val first = match.groupValues[1].toInt()
        val last = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: first
        if (first < 1 || last < first || first > pageCount) return null
        return PageRange(first, minOf(last, pageCount, first + MAX_PAGES_PER_CALL - 1))
    }

    /**
     * The observation for [file] ([shownPath] as the tools show it), at most [budget] characters. [pages]: PDF only.
     * [offset]: characters of the text to skip (reading on after a cut).
     */
    fun read(file: File, shownPath: String, pages: String?, offset: Int, budget: Int, pdf: PdfPageReader,
             cancelled: () -> Boolean = { false }): String {
        if (!file.exists()) return "Nicht gefunden: $shownPath – die Datei gibt es nicht (mehr). Gelesen habe ich nichts."
        if (file.isDirectory) return "Kein Dateitext: $shownPath ist ein Ordner. Mit list_files siehst du, was darin liegt."
        if (!file.canRead()) return "Nicht lesbar: Android lässt mich $shownPath nicht öffnen. Gelesen habe ich nichts."
        val extension = DevicePaths.extension(file.name)
        val size = OwnerLibraryMatcher.sizeText(file.length())
        return when {
            extension == "pdf" -> readPdf(file, shownPath, size, pages, offset, budget, pdf, cancelled)
            extension == "docx" -> readDocx(file, shownPath, size, offset, budget)
            else -> readPlain(file, shownPath, size, extension, offset, budget)
        }
    }

    private fun header(shownPath: String, facts: String): String = "Datei: $shownPath\nTyp: $facts\n"

    /** Formats that are never text; they are refused by name instead of reading megabytes to find that out. */
    private val binaryExtensions = DeviceFileKind.IMAGE.extensions + DeviceFileKind.VIDEO.extensions + DeviceFileKind.AUDIO.extensions +
        DeviceFileKind.APK.extensions + DeviceFileKind.ARCHIVE.extensions + setOf("xlsx", "xls", "ods", "pptx", "ppt", "odp", "doc",
            "odt", "db", "sqlite", "bin", "exe", "so", "dex", "jar", "iso", "ttf", "otf", "epub", "task", "litertlm", "gguf")

    private fun readPlain(file: File, shownPath: String, size: String, extension: String, offset: Int, budget: Int): String {
        if (extension in binaryExtensions) return "Kein Text: $shownPath (${DeviceFileKind.label(extension)}, $size) ist keine " +
            "Textdatei; ihren Inhalt kann ich nicht als Text lesen. Mit open_file lässt sie sich in der passenden App anzeigen."
        val bytes = file.inputStream().use { readBounded(it, MAX_TEXT_BYTES + 1) }
        val whole = bytes.size <= MAX_TEXT_BYTES
        val text = decode(if (whole) bytes else bytes.copyOf(MAX_TEXT_BYTES))
        if (text == null || looksBinary(text)) return "Kein Text: $shownPath (${DeviceFileKind.label(extension)}, $size) ist keine " +
            "Textdatei; ihren Inhalt kann ich nicht als Text lesen. Mit open_file lässt sie sich in der passenden App anzeigen."
        if (extension !in textExtensions && extension.isNotEmpty() && text.isBlank())
            return "Kein Text: $shownPath ($size) enthält keinen lesbaren Text."
        val note = if (whole) null else "nur die ersten ${OwnerLibraryMatcher.sizeText(MAX_TEXT_BYTES.toLong())} der Datei sind lesbar"
        return header(shownPath, "Text · $size") + window(normalise(text), offset, budget - shownPath.length - 120, "Zeichen", note)
    }

    private fun readDocx(file: File, shownPath: String, size: String, offset: Int, budget: Int): String {
        val text = try { docxText(file) } catch (_: Exception) { null }
            ?: return "Kein Text: $shownPath ($size) ist keine lesbare Word-Datei (.docx). Gelesen habe ich nichts."
        if (text.isBlank()) return "Kein Text: Die Word-Datei $shownPath ($size) enthält keinen Text."
        return header(shownPath, "Word · $size") + window(text, offset, budget - shownPath.length - 120, "Zeichen", null)
    }

    private fun readPdf(file: File, shownPath: String, size: String, pages: String?, offset: Int, budget: Int,
                        pdf: PdfPageReader, cancelled: () -> Boolean): String {
        // Page 1 tells the page count (a missing page reports it) and whether this Android has a text layer API.
        val first = try { pdf.read(file, 1) } catch (error: Exception) {
            return "Nicht lesbar: $shownPath ($size) lässt sich nicht als PDF öffnen (${error.javaClass.simpleName}). Gelesen habe ich nichts."
        }
        if (first is PdfPageText.NeedsAndroid15) return "Kein Text: Den Text eines PDFs kann ich erst ab Android 15 lesen. " +
            "$shownPath ($size) lässt sich mit open_file anzeigen."
        val count = pageCount(file, pdf, first)
        if (count <= 0) return "Kein Text: Das PDF $shownPath ($size) hat keine Seite."
        val range = if (pages.isNullOrBlank()) PageRange(1, minOf(count, MAX_PAGES_PER_CALL)) else pageRange(pages, count)
            ?: return "Seitenangabe nicht verstanden: „${DeviceSearch.clean(pages).take(24)}“. Das PDF $shownPath hat $count " +
                "Seite(n); gib pages als Zahl oder Bereich an, zum Beispiel 2 oder 2-4. Gelesen habe ich nichts."
        val body = StringBuilder()
        var withText = 0
        for (page in range.first..range.last) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Lesen gestoppt")
            val read = if (page == 1) first else pdf.read(file, page)
            body.append("--- Seite ").append(page).append(" ---\n")
            when (read) {
                is PdfPageText.Text -> { withText++; body.append(normalise(read.text)).append('\n') }
                else -> body.append("(kein Text auf dieser Seite)\n")
            }
        }
        val facts = "PDF · " + (if (count == 1) "1 Seite" else "$count Seiten") + " · $size"
        if (withText == 0) return header(shownPath, facts) + "Kein Text: Auf " + (if (range.first == range.last) "Seite ${range.first}"
            else "den Seiten ${range.first}–${range.last}") + " gibt es keine Textebene (wahrscheinlich ein Scan). " +
            "Den Inhalt kann ich nicht lesen; open_file zeigt das PDF an."
        val label = if (range.first == range.last) "Seite ${range.first} von $count" else "Seiten ${range.first}–${range.last} von $count"
        // Only when the brain did not choose the pages itself: where the document goes on.
        val next = minOf(count, range.last + MAX_PAGES_PER_CALL)
        val further = if (pages.isNullOrBlank() && range.last < count)
            "weitere Seiten mit pages=\"" + (if (next == range.last + 1) "$next" else "${range.last + 1}-$next") + "\"" else null
        return header(shownPath, facts) + window(body.toString().trimEnd(), offset, budget - shownPath.length - 160, label, further, pagesLabel = true)
    }

    private fun pageCount(file: File, pdf: PdfPageReader, first: PdfPageText): Int {
        if (first is PdfPageText.Missing) return first.pageCount
        // Asking for a page far behind the end is the cheapest way to learn the count from the same boundary.
        return (pdf.read(file, Int.MAX_VALUE) as? PdfPageText.Missing)?.pageCount ?: 1
    }

    /**
     * `Inhalt (…):` and the part of [text] from [offset] that fits [room]; a cut is marked with [CUT] and names the
     * offset to continue with. [label]: „Zeichen“ for a running text, the page range for a PDF.
     */
    internal fun window(text: String, offset: Int, room: Int, label: String, note: String?, pagesLabel: Boolean = false): String {
        val total = text.length
        val from = offset.coerceIn(0, total)
        if (from >= total && total > 0) return "Inhalt ($label): ab Zeichen $from gibt es keinen weiteren Text; die Datei hat $total Zeichen."
        val limit = room.coerceAtLeast(200)
        var to = minOf(total, from + limit)
        if (to < total) text.lastIndexOf(' ', to).takeIf { it > from + limit * 3 / 4 }?.let { to = it }
        val cut = to < total
        val span = "Zeichen ${from + 1}–$to von $total"
        val head = buildString {
            append("Inhalt (")
            append(if (pagesLabel) label + (if (cut || from > 0) ", $span" else "") else span)
            if (cut) append(", $CUT – weiter mit offset=$to")
            note?.let { append("; ").append(it) }
            append("):\n")
        }
        return head + text.substring(from, to).trimEnd() + if (cut) "\n[$CUT]" else ""
    }

    private fun readBounded(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < max) {
            val read = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** UTF-8 (with or without BOM), UTF-16 with BOM, otherwise Windows-1252; null when a NUL byte says „binary“. */
    internal fun decode(bytes: ByteArray): String? {
        if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())))
            return String(bytes, Charsets.UTF_16)
        if (bytes.any { it == 0.toByte() }) return null
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        // A bounded read may end inside a character: up to three trailing bytes are dropped before giving up on UTF-8.
        for (drop in 0..3) {
            val length = bytes.size - start - drop
            if (length < 0) break
            try {
                return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, start, length)).toString()
            } catch (_: CharacterCodingException) { /* try the next shorter cut */ }
        }
        return String(bytes, start, bytes.size - start, Charset.forName("windows-1252"))
    }

    /** More than a few control characters that no text file carries. */
    private fun looksBinary(text: String): Boolean {
        val sample = text.take(4_000)
        val odd = sample.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' && it != '\u000C' }
        return sample.isNotEmpty() && odd * 50 > sample.length
    }

    /** Line ends unified, other control and invisible characters dropped, blank runs collapsed. */
    internal fun normalise(text: String): String = buildString(text.length) {
        text.replace("\r\n", "\n").replace('\r', '\n').forEach { char ->
            when {
                char == '\n' -> append('\n')
                char == '\t' -> append(' ')
                char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt() -> Unit
                else -> append(char)
            }
        }
    }.replace(Regex("[ \\u00A0]{2,}"), " ").replace(Regex(" *\\n *"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()

    /** The text of `word/document.xml`: one line per paragraph, tabs and line breaks kept, no markup. Null = no docx. */
    internal fun docxText(file: File): String? = ZipInputStream(file.inputStream().buffered()).use { zip ->
        var entries = 0
        var text: String? = null
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > 2_000) break
            if (entry.name != "word/document.xml") continue
            val xml = readBounded(zip, MAX_DOCX_XML_BYTES + 1)
            if (xml.size <= MAX_DOCX_XML_BYTES) text = paragraphs(String(xml, Charsets.UTF_8))
            break
        }
        text
    }

    private val docxToken = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab\\s*/>|<w:(?:br|cr)(?:\\s[^>]*)?/>|</w:p>")

    internal fun paragraphs(xml: String): String {
        val out = StringBuilder()
        docxToken.findAll(xml).forEach { match ->
            val value = match.value
            when {
                value.startsWith("<w:t") && !value.startsWith("<w:tab") -> out.append(unescape(match.groupValues[1]))
                value.startsWith("<w:tab") -> out.append(' ')
                value == "</w:p>" -> out.append('\n')
                else -> out.append('\n')
            }
        }
        return normalise(out.toString())
    }

    private fun unescape(text: String): String = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace(Regex("&#(\\d{1,7});")) { m -> m.groupValues[1].toIntOrNull()?.takeIf(Character::isValidCodePoint)
            ?.let { String(Character.toChars(it)) }.orEmpty() }
        .replace(Regex("&#x([0-9A-Fa-f]{1,6});")) { m -> m.groupValues[1].toIntOrNull(16)?.takeIf(Character::isValidCodePoint)
            ?.let { String(Character.toChars(it)) }.orEmpty() }
        .replace("&amp;", "&")
}
