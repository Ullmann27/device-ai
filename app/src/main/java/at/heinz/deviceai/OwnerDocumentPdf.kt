package at.heinz.deviceai

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import java.io.ByteArrayOutputStream

/**
 * Writes the blocks of [OwnerDocumentLayout] as an A4 PDF with Android's own PdfDocument. This class only measures
 * (one StaticLayout per block) and draws; which line goes on which page is decided by the pure
 * [OwnerDocumentLayout.paginate]. Text stays text in the file (selectable, searchable); nothing is rasterised.
 * [plan] and [drawPage] are separate from the PdfDocument so that measuring and drawing can be checked on any Canvas.
 */
internal object OwnerDocumentPdf {
    class Rendered(val bytes: ByteArray, val pages: Int)

    /** The measured blocks and their distribution over pages. */
    class Plan(val layouts: List<StaticLayout>, val pages: List<OwnerDocumentLayout.Page>)

    fun plan(blocks: List<OwnerDocumentLayout.TextBlock>): Plan {
        require(blocks.isNotEmpty())
        val layouts = blocks.map(::layout)
        return Plan(layouts, OwnerDocumentLayout.paginate(blocks, layouts.map { layout ->
            List(layout.lineCount) { line -> layout.getLineBottom(line).toFloat() }
        }))
    }

    /** Draws page [index] of [plan] in page coordinates (points): its slices inside the margins and the page number. */
    fun drawPage(canvas: Canvas, plan: Plan, index: Int) {
        plan.pages[index].slices.forEach { slice ->
            val layout = plan.layouts[slice.block]
            val top = layout.getLineTop(slice.firstLine).toFloat()
            val bottom = layout.getLineBottom(slice.lastLine).toFloat()
            canvas.save()
            // Only the lines of this slice: the layout is shifted so that they start at the slice's place.
            canvas.translate(OwnerDocumentLayout.MARGIN, OwnerDocumentLayout.MARGIN + slice.top - top)
            canvas.clipRect(0f, top, OwnerDocumentLayout.CONTENT_WIDTH, bottom)
            layout.draw(canvas)
            canvas.restore()
        }
        val footerPaint = paint(OwnerDocumentLayout.Style.LINK)
        val footer = OwnerDocumentLayout.footer(index + 1, plan.pages.size)
        canvas.drawText(footer, (OwnerDocumentLayout.PAGE_WIDTH - footerPaint.measureText(footer)) / 2f,
            OwnerDocumentLayout.PAGE_HEIGHT - OwnerDocumentLayout.MARGIN - 6f, footerPaint)
    }

    fun render(blocks: List<OwnerDocumentLayout.TextBlock>): Rendered {
        val plan = plan(blocks)
        val document = PdfDocument()
        try {
            plan.pages.indices.forEach { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(
                    OwnerDocumentLayout.PAGE_WIDTH, OwnerDocumentLayout.PAGE_HEIGHT, index + 1).create())
                drawPage(page.canvas, plan, index)
                document.finishPage(page)
            }
            val bytes = ByteArrayOutputStream()
            document.writeTo(bytes)
            check(bytes.size() > 0) { "Android hat kein PDF erzeugt" }
            return Rendered(bytes.toByteArray(), plan.pages.size)
        } finally {
            document.close()
        }
    }

    private fun paint(style: OwnerDocumentLayout.Style) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = style.size
        color = if (style.gray) Color.rgb(95, 99, 104) else Color.BLACK
        typeface = Typeface.create(Typeface.SANS_SERIF, when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        })
    }

    private fun layout(block: OwnerDocumentLayout.TextBlock): StaticLayout =
        StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint(block.style),
            OwnerDocumentLayout.CONTENT_WIDTH.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, OwnerDocumentLayout.LINE_SPACING)
            .setIncludePad(false)
            .build()
}
