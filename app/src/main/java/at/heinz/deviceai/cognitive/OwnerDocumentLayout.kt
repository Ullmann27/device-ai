package at.heinz.deviceai.cognitive

/**
 * The page model of an exported deliverable, free of Android: which text blocks a document consists of (title, date,
 * note, body paragraphs, source list with outlet, headline and link) and how measured lines are distributed over A4
 * pages. The PDF writer only measures the blocks (StaticLayout) and draws what [paginate] decided; the Word writer
 * uses the same blocks. All sizes are PostScript points (1/72 inch), the unit of PdfDocument.
 */
internal object OwnerDocumentLayout {
    /** [size] in points; [gray]: secondary text. */
    enum class Style(val size: Float, val bold: Boolean = false, val italic: Boolean = false, val gray: Boolean = false) {
        TITLE(20f, bold = true),
        DATE(11f, gray = true),
        NOTE(9.5f, italic = true, gray = true),
        BODY(11.5f),
        HEADING(13f, bold = true),
        SOURCE(10f),
        LINK(9f, gray = true)
    }

    /** [spaceBefore] is dropped at the top of a page. [keepWithNext]: never the last thing on a page. */
    data class TextBlock(val text: String, val style: Style, val spaceBefore: Float, val keepWithNext: Boolean = false,
                         val link: String? = null)

    /** Lines [firstLine]..[lastLine] of block [block], drawn with their top edge at [top] below the page's content top. */
    data class Slice(val block: Int, val firstLine: Int, val lastLine: Int, val top: Float)

    data class Page(val slices: List<Slice>)

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 56f
    const val FOOTER_HEIGHT = 28f
    const val LINE_SPACING = 1.25f
    const val MAX_SOURCES = 40
    const val MAX_BODY_CHARACTERS = 60_000
    val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN - FOOTER_HEIGHT

    fun footer(page: Int, pages: Int): String = "Seite $page von $pages"

    fun blocks(deliverable: OwnerDeliverable): List<TextBlock> = buildList {
        add(TextBlock(clean(deliverable.title), Style.TITLE, 0f, keepWithNext = true))
        add(TextBlock(deliverable.dateLabel, Style.DATE, 4f, keepWithNext = true))
        deliverable.note?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { add(TextBlock(it, Style.NOTE, 2f, keepWithNext = true)) }
        var budget = MAX_BODY_CHARACTERS
        var first = true
        deliverable.paragraphs.map(::clean).filter { it.isNotEmpty() }.forEach { paragraph ->
            if (budget <= 0) return@forEach
            val text = paragraph.take(budget)
            budget -= text.length
            add(TextBlock(text, Style.BODY, if (first) 18f else 9f))
            first = false
        }
        val sources = deliverable.sources.distinctBy { it.link }.take(MAX_SOURCES)
        if (sources.isNotEmpty()) {
            add(TextBlock("Quellen", Style.HEADING, 22f, keepWithNext = true))
            sources.forEachIndexed { index, source ->
                add(TextBlock(clean(source.outlet + ": " + source.title), Style.SOURCE, if (index == 0) 8f else 7f, keepWithNext = true))
                add(TextBlock(clean(source.link), Style.LINK, 1f, link = source.link))
            }
        }
    }

    /**
     * Distributes the blocks over pages of [pageHeight]. [lineBottoms] holds, for every block, the bottom edge of each
     * of its lines measured from the block's own top (what a text layout reports). A paragraph breaks between lines,
     * a heading stays with the first line that follows it, a paragraph does not leave a single first line behind, and
     * a line taller than a page still gets a page instead of an endless loop.
     */
    fun paginate(blocks: List<TextBlock>, lineBottoms: List<List<Float>>, pageHeight: Float = CONTENT_HEIGHT): List<Page> {
        require(blocks.size == lineBottoms.size && pageHeight > 0f)
        val pages = ArrayList<Page>()
        var current = ArrayList<Slice>()
        var y = 0f
        fun newPage() { pages += Page(current); current = ArrayList(); y = 0f }
        blocks.forEachIndexed { index, block ->
            val bottoms = lineBottoms[index]
            if (bottoms.isEmpty()) return@forEachIndexed
            var gap = if (y == 0f) 0f else block.spaceBefore
            if (block.keepWithNext && y > 0f) {
                val following = lineBottoms.getOrNull(index + 1)?.firstOrNull() ?: 0f
                val followingGap = blocks.getOrNull(index + 1)?.spaceBefore ?: 0f
                if (y + gap + bottoms.last() + followingGap + following > pageHeight) { newPage(); gap = 0f }
            }
            var first = 0
            while (first < bottoms.size) {
                val offset = if (first == 0) 0f else bottoms[first - 1]
                var last = first - 1
                while (last + 1 < bottoms.size && y + gap + (bottoms[last + 1] - offset) <= pageHeight) last++
                val nothingFits = last < first
                val lonelyFirstLine = first == 0 && last == 0 && bottoms.size >= 2
                if ((nothingFits || lonelyFirstLine) && y > 0f) { newPage(); gap = 0f; continue }
                if (nothingFits) last = first
                current += Slice(index, first, last, y + gap)
                y += gap + (bottoms[last] - offset)
                gap = 0f
                first = last + 1
                if (first < bottoms.size) newPage()
            }
        }
        if (current.isNotEmpty() || pages.isEmpty()) pages += Page(current)
        return pages
    }

    /** No control or invisible characters in a document; line breaks inside a paragraph become spaces. */
    private fun clean(text: String): String = buildString {
        text.forEach { char ->
            when {
                char == '\n' || char == '\t' || char == '\r' -> append(' ')
                char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt() -> Unit
                else -> append(char)
            }
        }
    }.replace(Regex(" {2,}"), " ").trim()
}
