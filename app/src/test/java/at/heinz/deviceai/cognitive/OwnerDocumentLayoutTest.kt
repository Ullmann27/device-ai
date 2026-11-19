package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerDocumentLayout.Style
import at.heinz.deviceai.cognitive.OwnerDocumentLayout.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** The page model and the Word writer: both are pure and run on the host exactly as on the device. */
class OwnerDocumentLayoutTest {
    private val deliverable = OwnerDeliverable(OwnerDeliverable.Kind.NEWS_BRIEFING, "Nachrichten von heute",
        LocalDate.of(2026, 11, 18),
        listOf("Die Nationalbank von Musterstadt hat den Leitzins gesenkt.\nKredite könnten günstiger werden.", "  ",
            "Außerdem gilt in Testland bald eine Solarpflicht für Neubauten – <b>wichtig</b> & \"neu\"."),
        listOf(NewsSourceRef("tagesschau", "Leitzins sinkt: Nationalbank Musterstadt senkt auf 2,5 Prozent",
                "https://www.tagesschau.de/wirtschaft/finanzen/leitzins-musterstadt-100.html"),
            NewsSourceRef("BBC News", "Musterstadt central bank cuts key rate to 2.5%",
                "https://www.bbc.co.uk/news/articles/synth0001?at_medium=RSS&at_campaign=rss"),
            NewsSourceRef("tagesschau", "Doppelt", "https://www.tagesschau.de/wirtschaft/finanzen/leitzins-musterstadt-100.html")),
        note = "Zusammenfassung des lokalen Modells aus den unten genannten Meldungen")

    /** Uniform lines of [height] points. */
    private fun lines(count: Int, height: Float = 10f) = List(count) { (it + 1) * height }
    private fun body(space: Float = 5f) = TextBlock("x", Style.BODY, space)

    @Test fun blocksCarryTitleDateNoteParagraphsAndEverySourceOnce() {
        val blocks = OwnerDocumentLayout.blocks(deliverable)
        assertEquals(listOf(Style.TITLE, Style.DATE, Style.NOTE, Style.BODY, Style.BODY, Style.HEADING,
            Style.SOURCE, Style.LINK, Style.SOURCE, Style.LINK), blocks.map { it.style })
        assertEquals("Nachrichten von heute", blocks[0].text)
        assertEquals("Mittwoch, 18. November 2026", blocks[1].text)
        // A line break inside a paragraph is a space; blank paragraphs vanish; markup characters stay literal text.
        assertEquals("Die Nationalbank von Musterstadt hat den Leitzins gesenkt. Kredite könnten günstiger werden.", blocks[3].text)
        assertTrue(blocks[4].text.contains("<b>wichtig</b> & \"neu\""))
        assertEquals("Quellen", blocks[5].text)
        assertEquals("tagesschau: Leitzins sinkt: Nationalbank Musterstadt senkt auf 2,5 Prozent", blocks[6].text)
        assertEquals("https://www.tagesschau.de/wirtschaft/finanzen/leitzins-musterstadt-100.html", blocks[7].link)
        assertEquals(blocks[7].link, blocks[7].text)
        assertTrue(blocks[0].keepWithNext && blocks[5].keepWithNext && blocks[6].keepWithNext)
        assertEquals("Seite 2 von 3", OwnerDocumentLayout.footer(2, 3))
    }

    @Test fun shortDocumentsStayOnOnePageWithSpacingOnlyBetweenBlocks() {
        val blocks = listOf(body(), body(), body())
        val page = OwnerDocumentLayout.paginate(blocks, listOf(lines(2), lines(3), lines(1)), pageHeight = 100f).single()
        assertEquals(listOf(0f, 25f, 60f), page.slices.map { it.top })
        assertEquals(listOf(0 to 1, 0 to 2, 0 to 0), page.slices.map { it.firstLine to it.lastLine })
    }

    @Test fun aLongParagraphBreaksBetweenLinesAndEveryLineIsPlacedExactlyOnce() {
        val blocks = listOf(body(), body(), body())
        val bottoms = listOf(lines(4), lines(23), lines(6))
        val pages = OwnerDocumentLayout.paginate(blocks, bottoms, pageHeight = 100f)
        assertEquals(4, pages.size)
        val placed = pages.flatMap { it.slices }.flatMap { slice -> (slice.firstLine..slice.lastLine).map { slice.block to it } }
        assertEquals(bottoms.flatMapIndexed { block, list -> list.indices.map { block to it } }, placed)
        // Nothing reaches below the page, and a new page starts at its top without the block spacing.
        pages.forEach { page ->
            assertEquals(0f, page.slices.first().top)
            page.slices.forEach { slice ->
                val height = bottoms[slice.block][slice.lastLine] - (if (slice.firstLine == 0) 0f else bottoms[slice.block][slice.firstLine - 1])
                assertTrue(slice.top + height <= 100f)
            }
        }
    }

    @Test fun headingsStayWithTheirTextAndAParagraphDoesNotLeaveOneLineBehind() {
        // 85 of 100 points are used; the heading (10) would fit, its first following line (5 + 10) would not.
        val heading = TextBlock("Quellen", Style.HEADING, 5f, keepWithNext = true)
        val pages = OwnerDocumentLayout.paginate(listOf(body(0f), heading, body()), listOf(lines(1, 85f), lines(1), lines(3)), 100f)
        assertEquals(2, pages.size)
        assertEquals(listOf(0), pages[0].slices.map { it.block })
        assertEquals(listOf(1, 2), pages[1].slices.map { it.block })
        // 80 used, spacing 5: only one line of the next paragraph would fit – it moves as a whole.
        val orphan = OwnerDocumentLayout.paginate(listOf(body(0f), body()), listOf(lines(1, 80f), lines(3)), 100f)
        assertEquals(listOf(listOf(0), listOf(1)), orphan.map { page -> page.slices.map { it.block } })
        // Two lines fit: the paragraph may break.
        val split = OwnerDocumentLayout.paginate(listOf(body(0f), body()), listOf(lines(1, 70f), lines(3)), 100f)
        assertEquals(listOf(0 to 1), split[0].slices.filter { it.block == 1 }.map { it.firstLine to it.lastLine })
        assertEquals(listOf(2 to 2), split[1].slices.map { it.firstLine to it.lastLine })
    }

    @Test fun degenerateInputNeverLoopsOrLosesText() {
        // A line taller than the page still gets a page of its own.
        val tall = OwnerDocumentLayout.paginate(listOf(body(), body()), listOf(lines(1, 300f), lines(2)), 100f)
        assertEquals(2, tall.size)
        assertEquals(1, OwnerDocumentLayout.paginate(emptyList(), emptyList(), 100f).size)
        assertTrue(OwnerDocumentLayout.paginate(listOf(body()), listOf(emptyList()), 100f).single().slices.isEmpty())
        // The real page geometry: 400 body lines of 14.4 pt on A4 content height.
        val many = OwnerDocumentLayout.paginate(listOf(body()), listOf(lines(400, 14.4f)))
        val perPage = (OwnerDocumentLayout.CONTENT_HEIGHT / 14.4f).toInt()
        assertEquals((400 + perPage - 1) / perPage, many.size)
        assertEquals(483f, OwnerDocumentLayout.CONTENT_WIDTH)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) }
        }
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    @Test fun wordFileIsAValidPackageWithTheSameTextAndRealLinks() {
        val blocks = OwnerDocumentLayout.blocks(deliverable)
        val bytes = OwnerDocumentDocx.write(blocks)
        assertEquals("PK", String(bytes, 0, 2, Charsets.ISO_8859_1))
        val parts = unzip(bytes)
        assertEquals(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels"), parts.keys.toList())
        parts.values.forEach { xml(it) } // every part is well-formed XML
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val document = xml(parts.getValue("word/document.xml"))
        val paragraphs = document.getElementsByTagNameNS(w, "p")
        assertEquals(blocks.size, paragraphs.length)
        val texts = (0 until paragraphs.length).map { (paragraphs.item(it) as Element).textContent }
        assertEquals(blocks.map { it.text }, texts)
        assertTrue(texts[4].contains("<b>wichtig</b> & \"neu\"")) // escaped in the file, literal for the reader
        // Title bold 20 pt (40 half-points); A4 page.
        val title = paragraphs.item(0) as Element
        assertEquals(1, title.getElementsByTagNameNS(w, "b").length)
        assertEquals("40", (title.getElementsByTagNameNS(w, "sz").item(0) as Element).getAttributeNS(w, "val"))
        val size = document.getElementsByTagNameNS(w, "pgSz").item(0) as Element
        assertEquals("11906" to "16838", size.getAttributeNS(w, "w") to size.getAttributeNS(w, "h"))
        // Every link paragraph is a hyperlink whose relationship is an external target with exactly that URL.
        val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val relations = xml(parts.getValue("word/_rels/document.xml.rels")).documentElement.getElementsByTagName("Relationship")
        val targets = (0 until relations.length).map { relations.item(it) as Element }
            .associate { it.getAttribute("Id") to (it.getAttribute("Target") to it.getAttribute("TargetMode")) }
        val links = document.getElementsByTagNameNS(w, "hyperlink")
        assertEquals(2, links.length)
        (0 until links.length).forEach { index ->
            val link = links.item(index) as Element
            val (target, mode) = targets.getValue(link.getAttributeNS(r, "id"))
            assertEquals(link.textContent, target)
            assertEquals("External", mode)
        }
        assertTrue(targets.values.any { it.first == "https://www.bbc.co.uk/news/articles/synth0001?at_medium=RSS&at_campaign=rss" })
        val types = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8)
        assertTrue(types.contains("wordprocessingml.document.main+xml"))
        assertFalse(parts.keys.any { it.contains("vba") || it.endsWith(".bin") })
        assertTrue(OwnerDocumentDocx.write(blocks).contentEquals(bytes)) // reproducible
    }

    @Test fun wordFileDropsCharactersXmlDoesNotAllow() {
        val bytes = OwnerDocumentDocx.write(listOf(TextBlock("A\u0000B\u0008C￾ ]]> &amp; 😀", Style.BODY, 0f)))
        val text = xml(unzip(bytes).getValue("word/document.xml")).documentElement.textContent
        assertEquals("ABC ]]> &amp; 😀", text)
    }
}
