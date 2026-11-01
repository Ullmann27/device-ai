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
        val ogdDocument = LegalRisParagraphExtractor.isOfficialDocumentUrl(request.url)
        val expectedParagraph = LegalRisParagraphExtractor.paragraphNumber(request.url)
            ?: if (ogdDocument) null else return actual
        val cleaned = actual.documents.mapNotNull { doc ->
            // Official OGD documents (ogd.ris.bka.gv.at/Dokumente/...) carry the paragraph in
            // their metadata, not in the URL; portal/ELI URLs must match the URL paragraph.
            val paragraph = if (ogdDocument) {
                if (!LegalRisParagraphExtractor.isOfficialDocumentUrl(doc.url)) return@mapNotNull null
                LegalRisParagraphExtractor.declaredParagraph(doc.content) ?: return@mapNotNull null
            } else {
                if (LegalRisParagraphExtractor.paragraphNumber(doc.url) != expectedParagraph) return@mapNotNull null
                expectedParagraph!!
            }
            val text = LegalRisParagraphExtractor.extract(paragraph, doc.content)
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

    /** Official RIS open-data document host (same authority, no portal chrome); paragraph comes from metadata. */
    fun isOfficialDocumentUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host?.lowercase(Locale.ROOT) == "ogd.ris.bka.gv.at" &&
            uri.userInfo == null && uri.port in setOf(-1, 443) && uri.rawQuery == null &&
            Regex("""/Dokumente/Bundesnormen/(NOR[0-9]{6,12})/\1\.html""").matches(uri.path.orEmpty())
    }

    /** Paragraph declared in the RIS metadata block ("§/Artikel/Anlage … § 1151"), null if absent. */
    fun declaredParagraph(raw: String): String? =
        Regex("""(?iu)§/Artikel/Anlage\s+(?:Paragraph/Artikel/Anlage\s+)?§\s*([0-9]{1,5})(?![0-9])""")
            .find(normalize(raw))?.groupValues?.get(1)

    /** "Inkrafttretensdatum"/"Außerkrafttretensdatum" from the RIS metadata; an expired version is marked. */
    fun validityNote(raw: String): String? {
        val text = normalize(raw)
        val date = Regex("""[0-3][0-9]\.[01][0-9]\.[12][0-9]{3}""")
        val inForce = Regex("""(?iu)\bInkrafttretensdatum\s+(""" + date.pattern + ")").find(text)?.groupValues?.get(1)
        val expired = Regex("""(?iu)\bAußerkrafttretensdatum\s+(""" + date.pattern + ")").find(text)?.groupValues?.get(1)
        return when {
            expired != null -> "RIS-Fassung AUSSER KRAFT seit $expired (historische Fassung, kein geltendes Recht)" +
                (inForce?.let { ", in Kraft gewesen ab $it" } ?: "")
            inForce != null -> "RIS-Fassung in Kraft seit $inForce (Geltung zum Abrufzeitpunkt nicht unabhängig bestätigt)"
            else -> null
        }
    }

    // RIS emits "§\u00A01151." with a no-break space; \s does not match it without UNICODE_CHARACTER_CLASS.
    private fun normalize(raw: String): String = raw.replace(Regex("[\u00A0\u2007\u202F]"), " ")

    /** This is a partial-evidence extractor, not legal interpretation or current-law oracle. */
    fun extract(paragraph: String, rawInput: String): String? {
        if (!paragraph.matches(Regex("[0-9]{1,5}")) || rawInput.length !in 100..100_000)
            return null
        val raw = normalize(rawInput)
        // A document whose metadata declares another paragraph can never serve as evidence for this one.
        val declared = declaredParagraph(raw)
        if (declared != null && declared != paragraph) return null
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
        // Without a metadata binding the text must at least show an "Absatz 1" structure;
        // single-sentence norms (e.g. § 678 ABGB) are accepted only from a bound RIS document.
        if (segment.length < 80 || declared == null && !Regex("""(?iu)(?:\(\s*1\s*\)|\bAbsatz\s+(?:eins|1)\b|\b1\))""")
                .containsMatchIn(segment)) return null
        return validityNote(raw)?.let { "[$it] $segment" } ?: segment
    }
}
