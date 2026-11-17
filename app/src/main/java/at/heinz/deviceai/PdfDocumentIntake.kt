package at.heinz.deviceai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.roundToInt

/**
 * Reads the PDF the owner picked in Android's document picker: its real name and size, the page count, the first page
 * as an image and – on Android 15 and newer – the document's own text layer. Nothing is invented: without a text layer
 * (a scan) or on older Android the text stays empty and [Document.textState] says why. Bounded in pages, pixels and
 * characters; call it off the UI thread. Presentation and drafting decide nothing about the file itself.
 */
class PdfDocumentIntake(private val resolver: ContentResolver) {
    enum class TextState { EXTRACTED, NO_TEXT_LAYER, NEEDS_ANDROID_15 }

    class Document(
        val name: String,
        val sizeBytes: Long?,
        val pageCount: Int,
        val firstPage: Bitmap?,
        /** Text of pages 1..[textPages], in page order, at most [MAX_TEXT_CHARS] characters. */
        val text: String,
        val textState: TextState,
        val textPages: Int,
        /**
         * The same text kept per page: index i is page i+1 ("" = no text found on that page), at most [textPages]
         * entries. Empty when no page attribution exists (no text layer, older Android, or a caller that has none).
         */
        val pageTexts: List<String> = emptyList()
    )

    @JvmOverloads
    fun read(uri: Uri, pageWidthPx: Int, isCancelled: () -> Boolean = { false }): Document {
        fun checkCancelled() {
            if (isCancelled() || Thread.currentThread().isInterrupted) throw InterruptedIOException("PDF_READ_CANCELLED")
        }
        checkCancelled()
        val (name, size) = meta(uri)
        checkCancelled()
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw IOException("Die Datei lässt sich nicht öffnen.")
        descriptor.use { fd ->
            checkCancelled()
            PdfRenderer(fd).use { pdf ->
                val count = pdf.pageCount
                checkCancelled()
                val first = if (count > 0) pdf.openPage(0).use { render(it, pageWidthPx) } else null
                try {
                    checkCancelled()
                    if (Build.VERSION.SDK_INT < 35) return Document(name, size, count, first, "", TextState.NEEDS_ANDROID_15, 0)
                    val bounded = extract(pdf, ::checkCancelled)
                    checkCancelled()
                    return Document(name, size, count, first, bounded.text,
                        if (bounded.text.isBlank()) TextState.NO_TEXT_LAYER else TextState.EXTRACTED,
                        bounded.pages.size, bounded.pages)
                } catch (failure: Throwable) {
                    first?.recycle()
                    throw failure
                }
            }
        }
    }

    private fun meta(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        }
        return (name?.trim()?.takeIf { it.isNotEmpty() } ?: uri.lastPathSegment ?: "Dokument.pdf").take(120) to size
    }

    private fun render(page: PdfRenderer.Page, widthPx: Int): Bitmap {
        val w = widthPx.coerceIn(64, MAX_PAGE_WIDTH_PX)
        val h = (w.toFloat() * page.height / page.width.coerceAtLeast(1)).roundToInt().coerceIn(64, MAX_PAGE_HEIGHT_PX)
        // PDF pages are transparent where nothing is printed; paper is white.
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    @RequiresApi(35)
    private fun extract(pdf: PdfRenderer, checkCancelled: () -> Unit): BoundedText =
        boundedPages(sequence {
            for (index in 0 until minOf(pdf.pageCount, MAX_TEXT_PAGES)) {
                checkCancelled()
                yield(pdf.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } })
            }
        }, MAX_TEXT_CHARS)

    /** [text] exactly as [boundedText] gives it; [pages] the share of it on each page that was read (index = page - 1). */
    class BoundedText(val text: String, val pages: List<String>)

    companion object {
        const val MAX_TEXT_CHARS = 12_000
        const val MAX_TEXT_PAGES = 30
        const val MAX_PAGE_WIDTH_PX = 1_600
        const val MAX_PAGE_HEIGHT_PX = 2_400

        /**
         * Page texts in order, blank runs collapsed, cut at [maxChars] on a word boundary. Stops reading pages once
         * the budget is reached. Pure: the same pages always give the same text.
         */
        fun boundedText(pages: Sequence<String>, maxChars: Int): String = boundedPages(pages, maxChars).text

        /**
         * [boundedText] with its page attribution kept: every page that was read gets its own share of the bounded
         * text (blank pages and pages after the cut stay "" or absent). Pure, same budget, same cleaning.
         */
        fun boundedPages(pages: Sequence<String>, maxChars: Int): BoundedText {
            require(maxChars > 0)
            val out = StringBuilder()
            val ranges = mutableListOf<IntRange?>()
            for (page in pages) {
                val clean = page.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                    .replace(Regex(" *\\n[ \\n]*"), "\n").trim()
                if (clean.isEmpty()) { ranges.add(null); continue }
                if (out.isNotEmpty()) out.append("\n\n")
                val start = out.length
                out.append(clean)
                ranges.add(start until out.length)
                if (out.length >= maxChars) break
            }
            if (out.length <= maxChars) return BoundedText(out.toString(), ranges.map { r -> r?.let { out.substring(it.first, it.last + 1) }.orEmpty() })
            val cut = out.lastIndexOf(' ', maxChars).takeIf { it >= maxChars * 3 / 4 } ?: maxChars
            val kept = out.substring(0, cut).trimEnd()
            return BoundedText("$kept …", ranges.map { range ->
                if (range == null || range.first >= kept.length) "" else
                    kept.substring(range.first, minOf(range.last + 1, kept.length)).trimEnd() +
                        if (range.last + 1 > kept.length) " …" else ""
            })
        }
    }
}
