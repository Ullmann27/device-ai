package at.heinz.deviceai.cognitive

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Fail-closed text distillation for a single OFFICIAL Austrian RIS paragraph page.
 * Reuses already authorized HTTPS retrieval; never opens a socket or invents law.
 * An unsupported page is not a verified statute and yields no evidence.
 */
internal class LegalRisEvidenceProvider(private val upstream: ResearchProvider): ResearchProvider {
    override fun retrieve(request: ResearchRequest): ResearchResult {
        val actual = upstream.retrieve(request)
        val expectedParagraph = LegalRisParagraphExtractor.paragraphNumber(request.url)
            ?: return actual
        val cleaned = actual.documents.mapNotNull { doc ->
            if (LegalRisParagraphExtractor.paragraphNumber(doc.url) != expectedParagraph)
                return@mapNotNull null
            val text = LegalRisParagraphExtractor.extract(expectedParagraph, doc.content)
                ?: return@mapNotNull null
            val sha = MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            doc.copy(content=text, contentHash=sha, mimeType="text/plain")
        }
        return ResearchResult(cleaned, cleaned.map { it.citation() })
    }
}

internal object LegalRisParagraphExtractor {
    // URL host and paths must be exact RIS resources, not a lookalike or search engine.
    fun paragraphNumber(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.scheme != "https" || host !in setOf("www.ris.bka.gv.at","ris.bka.gv.at") ||
            uri.userInfo != null || uri.port !in setOf(-1,443)) return null
        val path = uri.path.orEmpty()
        val text = if (path.startsWith("/eli/", true)) path
            else if (path.equals("/NormDokument.wxe", true)) uri.rawQuery.orEmpty()
            else return null
        return Regex("""(?iu)(?:/P|(?:^|&)Paragraf=)([0-9]{1,5})(?:/|&|$)""")
            .find(text)?.groupValues?.get(1)
    }

    /** This is a partial-evidence extractor, not legal interpretation or current-law oracle. */
    fun extract(paragraph: String, raw: String): String? {
        if (!paragraph.matches(Regex("[0-9]{1,5}")) || raw.length !in 100..100_000)
            return null
        val heading = Regex("""(?iu)§\s*""" + Regex.escape(paragraph) + """(?:[.\s]|$)""")
        // Metadata can mention "Dokumentnummer" before the actual Text section.
        // Only treat section trailers *following* "Text" as delimiters.
        val textField = Regex("""(?iu)\bText\s*:?""").find(raw)
            ?.range?.last?.plus(1) ?: return null
        val firstTrailer = listOf("Schlagworte", "Zuletzt aktualisiert", "Dokumentnummer", "European Legislation Identifier")
            .map { raw.indexOf(it, startIndex = textField, ignoreCase = true) }
            .filter { it >= 0 }.minOrNull() ?: raw.length
        val candidates = heading.findAll(raw)
            .filter { it.range.first in textField until firstTrailer }.toList()
        val match = candidates.lastOrNull() ?: return null
        val segment = raw.substring(match.range.first, firstTrailer)
            .replace(Regex("""\s+"""), " ").trim().take(10_000)
        if (segment.length < 80 || !Regex("""(?iu)(?:\(\s*1\s*\)|\bAbsatz\s+(?:eins|1)\b|\b1\))""")
                .containsMatchIn(segment)) return null
        return segment
    }
}
