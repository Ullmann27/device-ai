package at.heinz.deviceai.cognitive

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.URLEncoder
import java.net.URL
import javax.xml.parsers.SAXParserFactory

/** User-authorized, German-Wikipedia-only discovery, never autonomous general-web crawling.
 * Results remain untrusted research sources, not training facts or tool instructions.
 */
class WikipediaResearchDiscoveryProvider(
    private val checkAccess: () -> Unit = {},
    private val readXml: ((String) -> String)? = null
) : ResearchDiscoveryProvider {
    override val isGeneralSearchAvailable = false

    override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
        val text = query.text.trim().replace(Regex("\\s+"), " ").take(180)
        if (text.isBlank()) return emptyList()
        checkAccess()
        return parseSearchResponse(readXml?.invoke(searchUrl(text)) ?: fetchSearchResponse(searchUrl(text),checkAccess)).take(6)
    }

    companion object {
        fun searchUrl(topic: String): String {
            require(topic.length in 1..180)
            return "https://de.wikipedia.org/w/api.php?action=query&list=search" +
                "&srnamespace=0&srlimit=6&format=xml&srsearch=" +
                URLEncoder.encode(topic, "UTF-8")
        }

        /**
         * Hardened bounded XML parsing.
         *
         * Android devices ship different SAX implementations. Some reject Xerces-specific
         * feature names. Security therefore does not depend on one optional feature flag:
         * reject DTD/entity declarations before parsing, disable supported external-entity
         * features best-effort, and install an EntityResolver that never resolves resources.
         */
        fun parseSearchResponse(xml: String): List<ResearchSourceCandidate> {
            require(xml.length <= 80_000)
            require(!Regex("""(?is)<!\s*(doctype|entity)\b""").containsMatchIn(xml)) {
                "Wikipedia-Suchantwort enthält unzulässige DTD/Entity-Deklaration"
            }
            val results = mutableListOf<ResearchSourceCandidate>()
            var inSearch = false
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                fun optionalFeature(name:String,value:Boolean) {
                    runCatching { setFeature(name,value) }
                }
                optionalFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                optionalFeature("http://xml.org/sax/features/external-general-entities", false)
                optionalFeature("http://xml.org/sax/features/external-parameter-entities", false)
                optionalFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            val handler = object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                    if (qName == "search") inSearch = true
                    if (qName != "p" || !inSearch || results.size >= 6 || attrs.getValue("ns") != "0") return
                    val title = attrs.getValue("title")?.trim() ?: return
                    if (title.length !in 2..140 || !title.matches(Regex("[\\p{L}\\p{N} _().,:\\-]+"))) return
                    results += ResearchSourceCandidate(
                        "https://de.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'),"UTF-8").replace("+","%20"),
                        title, "wikipedia-search"
                    )
                }
                override fun endElement(uri: String?, localName: String?, qName: String) {
                    if (qName == "search") inSearch = false
                }
            }
            val parser=factory.newSAXParser()
            parser.xmlReader.entityResolver = org.xml.sax.EntityResolver { _, _ ->
                InputSource(StringReader(""))
            }
            parser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), handler)
            return results.distinctBy { it.url }
        }

        private fun fetchSearchResponse(endpoint:String,check:()->Unit):String {
            val url=URL(endpoint)
            require(url.protocol=="https"&&url.host=="de.wikipedia.org"&&url.path=="/w/api.php")
            check()
            return HttpReadOnlyResearchProvider(ResearchProviderPolicy(allowedHosts=setOf("de.wikipedia.org"),maxBytes=64000,maxContentCharacters=80000,totalTimeoutMillis=6500,maxRedirects=0),accessCheck=check)
                .retrieveMarkup(ResearchRequest("Wikipedia fallback",endpoint,maxBytes=64000,timeoutMillis=6500,maxRedirects=0)).documents.first().content
        }
    }
}
