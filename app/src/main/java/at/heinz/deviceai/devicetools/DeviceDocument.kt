package at.heinz.deviceai.devicetools

import android.content.Context
import at.heinz.deviceai.OwnerDocumentPdf
import at.heinz.deviceai.OwnerDocumentStore
import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerDocumentDocx
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import at.heinz.deviceai.cognitive.OwnerExportCommand
import at.heinz.deviceai.cognitive.OwnerLibraryMatcher
import java.time.LocalDate
import java.util.Locale

/**
 * The content a brain hands to „Dokument erstellen“, read as a small, forgiving markup: a line that starts with
 * `# ` is a heading, a line that starts with `- `, `* `, `• `, `– ` or `1. ` / `1) ` is a list item, a blank line
 * ends a paragraph, the lines of a paragraph are joined. `**`, `__` and back-ticks are dropped; nothing else is
 * interpreted, so no text of the owner is lost or restyled.
 */
internal object DeviceDocumentMarkup {
    sealed interface Block {
        val text: String
        data class Heading(override val text: String) : Block
        data class Paragraph(override val text: String) : Block
        data class Item(override val text: String, val numbered: Boolean = false) : Block
    }

    private val heading = Regex("^#{1,6}\\s+(.*\\S)\\s*$")
    private val bullet = Regex("^[-*•–·]\\s+(.*\\S)\\s*$")
    private val numbered = Regex("^(\\d{1,3}[.)])\\s+(.*\\S)\\s*$")

    fun parse(content: String): List<Block> {
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) { blocks += Block.Paragraph(paragraph.toString()); paragraph.setLength(0) }
        }
        clean(content).split('\n').forEach { raw ->
            val line = raw.trim()
            val head = heading.matchEntire(line)
            val item = bullet.matchEntire(line)
            val number = numbered.matchEntire(line)
            when {
                line.isEmpty() -> flush()
                head != null -> { flush(); blocks += Block.Heading(head.groupValues[1].trimEnd('#', ' ')) }
                item != null -> { flush(); blocks += Block.Item(item.groupValues[1]) }
                number != null -> { flush(); blocks += Block.Item(number.groupValues[1] + " " + number.groupValues[2], numbered = true) }
                else -> { if (paragraph.isNotEmpty()) paragraph.append(' '); paragraph.append(line) }
            }
        }
        flush()
        return blocks.filter { it.text.isNotBlank() }
    }

    /** Line ends unified; emphasis marks, control and invisible characters dropped. */
    private fun clean(text: String): String = buildString(text.length) {
        text.replace("\r\n", "\n").replace('\r', '\n').replace("**", "").replace("__", "").replace("`", "").forEach { char ->
            when {
                char == '\n' -> append('\n')
                char == '\t' -> append(' ')
                char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt() -> Unit
                else -> append(char)
            }
        }
    }

    private fun oneLine(text: String): String = clean(text).replace('\n', ' ').replace(Regex(" {2,}"), " ").trim()

    /**
     * The page blocks of the document: title, date, then the content. A first heading that only repeats the title is
     * dropped. Empty when there is nothing to print below the title.
     */
    fun layout(title: String, date: LocalDate, content: String): List<OwnerDocumentLayout.TextBlock> {
        val shownTitle = oneLine(title).take(160)
        var body = parse(content)
        if ((body.firstOrNull() as? Block.Heading)?.text?.equals(shownTitle, ignoreCase = true) == true) body = body.drop(1)
        if (shownTitle.isEmpty() || body.isEmpty()) return emptyList()
        return buildList {
            add(OwnerDocumentLayout.TextBlock(shownTitle, OwnerDocumentLayout.Style.TITLE, 0f, keepWithNext = true))
            add(OwnerDocumentLayout.TextBlock(OwnerDeliverable.LONG_DATE.format(date), OwnerDocumentLayout.Style.DATE, 4f, keepWithNext = true))
            var budget = OwnerDocumentLayout.MAX_BODY_CHARACTERS
            body.forEachIndexed { index, block ->
                if (budget <= 0) return@forEachIndexed
                val first = index == 0
                val text = when (block) {
                    is Block.Item -> if (block.numbered) block.text else "• " + block.text
                    else -> block.text
                }.take(budget)
                budget -= text.length
                add(when (block) {
                    is Block.Heading -> OwnerDocumentLayout.TextBlock(text, OwnerDocumentLayout.Style.HEADING, if (first) 18f else 16f, keepWithNext = true)
                    is Block.Item -> OwnerDocumentLayout.TextBlock(text, OwnerDocumentLayout.Style.BODY, if (first) 18f else 4f)
                    is Block.Paragraph -> OwnerDocumentLayout.TextBlock(text, OwnerDocumentLayout.Style.BODY, if (first) 18f else 9f)
                })
            }
        }
    }
}

/** The Android boundary of „Dokument erstellen“: the phone renders with PdfDocument and saves through MediaStore. */
internal interface DeviceDocumentPort {
    /** PDF bytes and page count. */
    fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int>
    fun nameTaken(displayName: String): Boolean
    fun save(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile
    fun readBack(file: OwnerSavedFile): ByteArray?
    fun location(file: OwnerSavedFile): String?
}

internal class AndroidDeviceDocumentPort(context: Context) : DeviceDocumentPort {
    private val store = OwnerDocumentStore(context.applicationContext)
    override fun renderPdf(blocks: List<OwnerDocumentLayout.TextBlock>): Pair<ByteArray, Int> =
        OwnerDocumentPdf.render(blocks).let { it.bytes to it.pages }
    override fun nameTaken(displayName: String): Boolean = store.nameTaken(displayName)
    override fun save(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile = store.save(displayName, mime, bytes)
    override fun readBack(file: OwnerSavedFile): ByteArray? = store.readBack(file)
    override fun location(file: OwnerSavedFile): String? = store.location(file)
}

/**
 * „Selbst eine PDF erstellen“: builds the document with the existing writers ([OwnerDocumentPdf], [OwnerDocumentDocx]),
 * saves it as a NEW file into the owner's Download folder (never over an existing one), reads it back and reports the
 * real name and place. Whether it may run at all is the ToolGate's decision (owner approval).
 */
internal class DeviceDocumentWriter(private val port: DeviceDocumentPort, private val today: () -> LocalDate = LocalDate::now) {
    /** A file this tool wrote, by the path the tools show for it. */
    class Created(val path: String, val file: OwnerSavedFile, val verified: Boolean)

    class Outcome(val observation: String, val created: Created?)

    fun create(title: String, content: String, format: String?, name: String?): Outcome {
        val kind = formatOf(format) ?: return Outcome(unknownFormat(format), null)
        val date = today()
        val blocks = DeviceDocumentMarkup.layout(title, date, content)
        if (blocks.isEmpty()) return Outcome(EMPTY, null)
        val (bytes, pages) = when (kind) {
            OwnerExportCommand.Format.PDF -> port.renderPdf(blocks).let { it.first to it.second as Int? }
            OwnerExportCommand.Format.DOCX -> OwnerDocumentDocx.write(blocks) to null
        }
        check(bytes.size <= MAX_BYTES) { "Das Dokument ist zu groß." }
        val displayName = OwnerExportCommand.fileName(name?.trim()?.takeIf { it.isNotEmpty() }, blocks.first().text, date, kind, port::nameTaken)
        val saved = port.save(displayName, kind.mime, bytes)
        val verified = runCatching { port.readBack(saved) }.getOrNull()?.contentEquals(bytes) == true
        val inDownloads = saved.uri != null
        val path = if (inDownloads) DevicePaths.shown("Download/" + saved.displayName) else saved.displayName
        val place = (if (inDownloads) runCatching { port.location(saved) }.getOrNull() else null) ?: saved.folder
        val shape = listOfNotNull(kind.label, pages?.let { if (it == 1) "1 Seite" else "$it Seiten" },
            OwnerLibraryMatcher.sizeText(bytes.size.toLong())).joinToString(" · ")
        val observation = buildString {
            append("Gespeichert: ").append(path).append('\n')
            append("Name: ").append(saved.displayName).append('\n')
            append("Ort: ").append(place).append('\n')
            append("Format: ").append(shape).append('\n')
            append(if (verified) "Geprüft: zurückgelesen, Inhalt stimmt überein" else
                "Geprüft: NEIN – die Datei ließ sich danach nicht zurücklesen; bitte prüf sie selbst")
            if (!inDownloads) append("\nHinweis: Auf dieser Android-Version liegt die Datei im App-Ordner, nicht im Download-Ordner.")
            append("\nEine neue Datei; nichts wurde überschrieben.")
        }
        return Outcome(observation, Created(path, saved, verified))
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val EMPTY = "Nicht angelegt: Titel oder Inhalt des Dokuments sind leer. Es wurde keine Datei geschrieben."

        /** „pdf“ (also when nothing is said), „docx“/„word“; null for anything else. */
        fun formatOf(text: String?): OwnerExportCommand.Format? = when (text?.trim()?.lowercase(Locale.ROOT)?.removePrefix(".")) {
            null, "", "pdf" -> OwnerExportCommand.Format.PDF
            "docx", "word", "doc" -> OwnerExportCommand.Format.DOCX
            else -> null
        }

        fun unknownFormat(text: String?): String = "Nicht angelegt: Das Format „${DeviceSearch.clean(text.orEmpty()).take(24)}“ " +
            "kenne ich nicht. Möglich sind pdf und docx (Word). Es wurde keine Datei geschrieben."
    }
}
