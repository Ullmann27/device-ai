package at.heinz.deviceai.cognitive

import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** Fetch a bounded, authentic Wikipedia article extract rather than its unbounded HTML page.
 * Only exact German Wikipedia article URLs qualify. No private network, auto-learning,
 * tool authorization or unsupported text generation is performed here.
 */
class WikipediaExtractResearchProvider(
    private val downstream: ResearchProvider,
    private val readMarkup: (ResearchRequest) -> ResearchResult
) : ResearchProvider {
    constructor(downstream: HttpReadOnlyResearchProvider) : this(downstream, downstream::retrieveMarkup)

    override fun retrieve(request: ResearchRequest): ResearchResult {
        val title = articleTitle(request.url) ?: return downstream.retrieve(request)
        val endpoint = extractUrl(title)
        val xmlRequest = ResearchRequest(
            request.query, endpoint, minOf(request.maxBytes, 24_000), request.timeoutMillis, 0
        )
        val source = readMarkup(xmlRequest).documents.singleOrNull()
            ?: throw IllegalArgumentException("Wikipedia extract response is missing")
        require(source.url == endpoint && source.source.domain == "de.wikipedia.org") {
            "Wikipedia extract source mismatch"
        }
        val body = parseExtract(source.content, title)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val document = ResearchDocument(request.url, title.replace('_', ' '), source.retrievedAt,
            digest, "text/plain", source.source, body)
        return ResearchResult(listOf(document), listOf(document.citation()))
    }

    companion object {
        private val articlePattern = Regex("[\\p{L}\\p{N} _().,:\\-]{2,140}")
        private val unsafeXml = Regex("(?is)<!\\s*(doctype|entity)\\b")

        fun articleTitle(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host != "de.wikipedia.org" || uri.port != -1 ||
                uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val title = uri.path.removePrefix("/wiki/")
            if (uri.path == title || !articlePattern.matches(title) || title == ".." ||
                title.contains('/') || title.contains('\\')) return null
            return title
        }

        fun extractUrl(title: String): String {
            require(articlePattern.matches(title) && title != "..")
            return "https://de.wikipedia.org/w/api.php?action=query&prop=extracts" +
                "&explaintext=1&exchars=1200&format=xml&titles=" +
                URLEncoder.encode(title.replace('_', ' '), "UTF-8")
        }

        fun parseExtract(xml: String, expectedTitle: String): String {
            require(xml.length <= 24_000 && !unsafeXml.containsMatchIn(xml)) {
                "Unsafe or oversized Wikipedia extract"
            }
            val text = StringBuilder()
            var pageMatches = false
            var inExtract = false
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                for (feature in listOf(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    "http://xml.org/sax/features/external-general-entities",
                    "http://xml.org/sax/features/external-parameter-entities",
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd"
                )) runCatching {
                    setFeature(feature, feature.endsWith("disallow-doctype-decl"))
                }
            }
            val handler = object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                    if (qName == "page") {
                        pageMatches = attrs.getValue("title")?.replace('_', ' ')
                            ?.equals(expectedTitle.replace('_', ' '), ignoreCase = true) == true &&
                            attrs.getValue("missing") == null && attrs.getValue("invalid") == null
                    }
                    if (qName == "extract" && pageMatches) inExtract = true
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (!inExtract) return
                    require(text.length + length <= 2_000) { "Wikipedia extract exceeded text limit" }
                    text.append(ch, start, length)
                }
                override fun endElement(uri: String?, localName: String?, qName: String) {
                    if (qName == "extract") inExtract = false
                    if (qName == "page") pageMatches = false
                }
            }
            val parser = factory.newSAXParser()
            parser.xmlReader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
            parser.parse(InputSource(StringReader(xml)), handler)
            val result = text.toString().trim()
            require(result.isNotEmpty()) { "Wikipedia article has no readable extract" }
            return result
        }
    }
}
