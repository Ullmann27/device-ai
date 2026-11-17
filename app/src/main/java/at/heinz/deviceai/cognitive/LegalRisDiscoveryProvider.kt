package at.heinz.deviceai.cognitive

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * M3 (Genesis): bounded discovery of ONE Austrian federal norm ("§ 364a ABGB") through the
 * official RIS open-data API (data.bka.gv.at/ris/api/v2.6, Applikation=BrKons). Only this
 * documented interface is used; the portal (www.ris.bka.gv.at, bot wall) is never touched.
 *
 * Returned candidates are official OGD document pages (ogd.ris.bka.gv.at/Dokumente/Bundesnormen/
 * NORxxx/NORxxx.html) that [LegalRisEvidenceProvider] then distils fail-closed. Paragraphs with
 * letter suffixes, documented validity ("in Kraft seit"/"AUSSER KRAFT seit") and historical
 * versions are kept visible in the candidate title so nothing historical is passed off as current.
 *
 * Any question that is not a norm reference goes untouched to the delegate (general web search).
 * API failure or zero hits also fall back to the delegate; [lastIssue] keeps that visible.
 */
internal class LegalRisDiscoveryProvider(
    private val delegate: ResearchDiscoveryProvider,
    private val checkAccess: () -> Unit = {},
    private val fetchJson: ((String) -> String)? = null
) : ResearchDiscoveryProvider {
    @Volatile var lastIssue: String? = null
        private set
    override val isGeneralSearchAvailable: Boolean get() = delegate.isGeneralSearchAvailable

    override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
        lastIssue = null
        val ref = LegalNormReference.parse(query.text) ?: return delegate.discover(query)
        checkAccess()
        val url = apiUrl(ref)
        val candidates = runCatching {
            parseSearchResponse(fetchJson?.invoke(url) ?: fetchApi(url, checkAccess), ref)
        }.getOrElse {
            if (it is InterruptedException) Thread.currentThread().interrupt()
            if (it is InterruptedException || it is CancellationException) throw it
            lastIssue = "RIS-API nicht verfügbar: " + (it.message ?: it.javaClass.simpleName).take(160)
            emptyList()
        }
        checkAccess()
        if (candidates.isNotEmpty()) return candidates
        lastIssue = lastIssue ?: ("RIS-API: keine Fassung zu ${ref.display} gefunden; allgemeine Websuche als Fallback.")
        return delegate.discover(query)
    }

    companion object {
        private const val API = "https://data.bka.gv.at/ris/api/v2.6/Bundesrecht"

        /** Official, documented search: full-text words bind paragraph AND abbreviation; 10 results per page. */
        fun apiUrl(ref: LegalNormReference): String =
            API + "?Applikation=BrKons&DokumenteProSeite=Ten&Suchworte=" +
                URLEncoder.encode("§ ${ref.paragraph} ${ref.abbreviation}", "UTF-8")

        /** Parses the OGD JSON; keeps only documents whose metadata declares exactly this norm. */
        fun parseSearchResponse(raw: String, ref: LegalNormReference): List<ResearchSourceCandidate> {
            require(raw.length <= 400_000) { "RIS-API-Antwort zu groß" }
            val results = JSONObject(raw).optJSONObject("OgdSearchResult")
                ?.optJSONObject("OgdDocumentResults") ?: return emptyList()
            val refs: List<JSONObject> = when (val node = results.opt("OgdDocumentReference")) {
                is JSONArray -> (0 until node.length()).mapNotNull { node.optJSONObject(it) }
                is JSONObject -> listOf(node)
                else -> emptyList()
            }
            data class Hit(val candidate: ResearchSourceCandidate, val inForce: Boolean, val from: String)
            val hits = refs.mapNotNull { doc ->
                val data = doc.optJSONObject("Data") ?: return@mapNotNull null
                val meta = data.optJSONObject("Metadaten") ?: return@mapNotNull null
                val federal = meta.optJSONObject("Bundesrecht") ?: return@mapNotNull null
                val kons = federal.optJSONObject("BrKons") ?: return@mapNotNull null
                val abbreviation = kons.optString("Abkuerzung").trim()
                if (!abbreviation.equals(ref.abbreviation, ignoreCase = true)) return@mapNotNull null
                val declared = (kons.optString("Paragraphnummer").trim() + kons.optString("Paragraphbuchstabe").trim())
                    .lowercase(Locale.ROOT)
                if (declared != ref.paragraph) return@mapNotNull null
                if (!kons.optString("Dokumenttyp").equals("Paragraph", ignoreCase = true)) return@mapNotNull null
                val from = kons.optString("Inkrafttretensdatum").trim()
                val until = kons.optString("Ausserkrafttretensdatum").trim()
                val html = contentUrls(data).firstOrNull { LegalRisParagraphExtractor.isOfficialDocumentUrl(it) }
                    ?: return@mapNotNull null
                val title = kons.optString("Kurztitel").ifBlank { federal.optString("Kurztitel") }.trim()
                val validity = if (until.isNotBlank()) "AUSSER KRAFT seit $until, historische Fassung ab $from"
                    else "RIS-Fassung in Kraft seit $from"
                Hit(ResearchSourceCandidate(html, ("§ ${ref.paragraph} ${ref.abbreviation} · $validity" +
                    (if (title.isNotBlank()) " · $title" else "")).take(256), "ris-ogd-api"), until.isBlank(), from)
            }
            // Current version first, then historical versions newest first; at most 4 documents.
            return hits.sortedWith(compareByDescending<Hit> { it.inForce }.thenByDescending { it.from })
                .map { it.candidate }.distinctBy { it.url }.take(4)
        }

        private fun contentUrls(data: JSONObject): List<String> {
            val list = data.optJSONObject("Dokumentliste")?.opt("ContentReference") ?: return emptyList()
            val refs = when (list) {
                is JSONArray -> (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                is JSONObject -> listOf(list)
                else -> emptyList()
            }
            return refs.flatMap { ref ->
                val urls = ref.optJSONObject("Urls")?.opt("ContentUrl")
                val entries = when (urls) {
                    is JSONArray -> (0 until urls.length()).mapNotNull { urls.optJSONObject(it) }
                    is JSONObject -> listOf(urls)
                    else -> emptyList()
                }
                entries.filter { it.optString("DataType").equals("Html", ignoreCase = true) }
                    .map { it.optString("Url").trim() }.filter { it.isNotBlank() }
            }
        }

        private fun fetchApi(url: String, check: () -> Unit): String {
            check()
            return HttpReadOnlyResearchProvider(
                ResearchProviderPolicy(maxBytes = 400_000, maxContentCharacters = 100_000, totalTimeoutMillis = 8_000, maxRedirects = 0),
                accessCheck = check
            ).retrieveMarkup(ResearchRequest("ris ogd api", url, maxBytes = 400_000, timeoutMillis = 8_000, maxRedirects = 0))
                .documents.first().content
        }
    }
}

/** "§ 364a ABGB", "Paragraph 1151 ABGB", "§ 1151 Abs 2 ABGB" → paragraph "364a"/"1151", abbreviation as written. */
internal data class LegalNormReference(val paragraph: String, val abbreviation: String) {
    val display: String get() = "§ $paragraph $abbreviation"

    companion object {
        private val pattern = Regex(
            """(?iu)(?:§|\bParagraph|\bPar\.)\s*([0-9]{1,5})\s?([a-z])?(?![0-9a-z])(?:\s*Abs\.?\s*[0-9]{1,2}\s*)?(?:\s*(?:Z|Ziffer)\s*[0-9]{1,3}\s*)?\s+(?:des\s+|der\s+)?([A-ZÄÖÜ][A-Za-zÄÖÜäöüß]{0,12}(?:-[A-Za-zÄÖÜäöüß]{1,6})?)\b"""
        )
        /** Only well-formed references to a federal statute abbreviation (contains at least two upper-case letters). */
        fun parse(text: String): LegalNormReference? {
            val m = pattern.find(text.take(600)) ?: return null
            val abbreviation = m.groupValues[3]
            if (abbreviation.count { it.isUpperCase() } < 2) return null
            val paragraph = m.groupValues[1].trimStart('0').ifBlank { return null } + m.groupValues[2].lowercase(Locale.ROOT)
            return LegalNormReference(paragraph, abbreviation)
        }
    }
}
