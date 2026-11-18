package at.heinz.deviceai.cognitive

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal, valid Word document (Office Open XML, .docx) written with java.util.zip – no library. It contains the
 * same blocks as the PDF ([OwnerDocumentLayout.blocks]): title, date, note, paragraphs and the source list, the
 * links as real external hyperlinks. A4, 2 cm margins, direct formatting only (no styles part, no macros, no
 * embedded objects). All text is XML-escaped; characters XML does not allow are dropped.
 */
internal object OwnerDocumentDocx {
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val FONT = "Calibri"

    fun write(blocks: List<OwnerDocumentLayout.TextBlock>): ByteArray {
        require(blocks.isNotEmpty())
        val links = blocks.mapNotNull { it.link }.distinct()
        val body = StringBuilder()
        blocks.forEach { block -> body.append(paragraph(block, block.link?.let { "rId" + (links.indexOf(it) + 1) })) }
        val document = HEADER + "<w:document xmlns:w=\"$W\" xmlns:r=\"$R\"><w:body>" + body +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" " +
            "w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
        val documentRels = HEADER + "<Relationships xmlns=\"$PACKAGE_RELS\">" + links.mapIndexed { index, link ->
            "<Relationship Id=\"rId${index + 1}\" Type=\"$R/hyperlink\" Target=\"${escape(link)}\" TargetMode=\"External\"/>"
        }.joinToString("") + "</Relationships>"
        val contentTypes = HEADER + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument." +
            "wordprocessingml.document.main+xml\"/></Types>"
        val rootRels = HEADER + "<Relationships xmlns=\"$PACKAGE_RELS\"><Relationship Id=\"rId1\" " +
            "Type=\"$R/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            // The content-types part must come first; fixed entry times keep the output reproducible.
            listOf("[Content_Types].xml" to contentTypes, "_rels/.rels" to rootRels, "word/document.xml" to document,
                "word/_rels/document.xml.rels" to documentRels).forEach { (name, xml) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 315_619_200_000L }) // 02.01.1980, no build time inside
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun paragraph(block: OwnerDocumentLayout.TextBlock, relationId: String?): String {
        val style = block.style
        val halfPoints = Math.round(style.size * 2)
        val run = "<w:rPr><w:rFonts w:ascii=\"$FONT\" w:hAnsi=\"$FONT\" w:cs=\"$FONT\"/>" +
            (if (style.bold) "<w:b/>" else "") + (if (style.italic) "<w:i/>" else "") +
            (when { relationId != null -> "<w:color w:val=\"1A56B0\"/><w:u w:val=\"single\"/>"; style.gray -> "<w:color w:val=\"5F6368\"/>"; else -> "" }) +
            "<w:sz w:val=\"$halfPoints\"/><w:szCs w:val=\"$halfPoints\"/></w:rPr>"
        val text = block.text.split('\n').joinToString("<w:br/>") { "<w:t xml:space=\"preserve\">${escape(it)}</w:t>" }
        val content = "<w:r>$run$text</w:r>"
        // Twentieths of a point; line spacing as in the PDF.
        return "<w:p><w:pPr>" + (if (block.keepWithNext) "<w:keepNext/>" else "") +
            "<w:spacing w:before=\"${Math.round(block.spaceBefore * 20)}\" w:after=\"0\" " +
            "w:line=\"${Math.round(240 * OwnerDocumentLayout.LINE_SPACING)}\" w:lineRule=\"auto\"/></w:pPr>" +
            (if (relationId != null) "<w:hyperlink r:id=\"$relationId\">$content</w:hyperlink>" else content) + "</w:p>"
    }

    /** XML 1.0 text and attribute escaping; characters outside the XML character range are dropped. */
    internal fun escape(text: String): String = buildString(text.length + 16) {
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            index += Character.charCount(cp)
            when {
                cp == '&'.code -> append("&amp;")
                cp == '<'.code -> append("&lt;")
                cp == '>'.code -> append("&gt;")
                cp == '"'.code -> append("&quot;")
                cp == '\''.code -> append("&apos;")
                cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD ||
                    cp in 0x10000..0x10FFFF -> appendCodePoint(cp)
            }
        }
    }
}
