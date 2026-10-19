package at.heinz.deviceai.cognitive

import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.util.concurrent.CancellationException
import javax.xml.parsers.SAXParserFactory

/** Public Bing RSS search, with Wikipedia only when search produces no safe result.
 * Every transport shares the research provider's DNS/TLS/redirect/byte bounds.
 * Endpoint availability is not guaranteed; fallback and failures stay visible.
 */
class GeneralWebDiscoveryProvider(
    private val wikipedia:ResearchDiscoveryProvider?=null,
    private val checkAccess:()->Unit={},
    private val fetchHtml:((String)->String)?=null
):ResearchDiscoveryProvider {
    @Volatile private var generalWorked=false
    @Volatile var lastIssue:String?=null
        private set
    override val isGeneralSearchAvailable:Boolean get()=generalWorked
    override fun discover(query:ResearchQuery):List<ResearchSourceCandidate> {
        checkAccess()
        val text=query.text.trim().take(512)
        if(text.isBlank())return emptyList()
        val focused=ResearchQuestionFocus.searchText(text)
        val primary=runCatching {
            checkAccess()
            parseSearchResults(fetchHtml?.invoke(searchUrl(focused))?:fetchSearchResponse(searchUrl(focused),checkAccess))
                .filter { ResearchQuestionFocus.titleMatches(text,it.title) }
        }.getOrElse {
            rethrowCancellation(it)
            lastIssue="Allgemeine Websuche fehlgeschlagen: "+(it.message?:it.javaClass.simpleName).take(160)
            emptyList()
        }
        checkAccess()
        if(primary.isNotEmpty()){generalWorked=true;lastIssue=null;return primary}
        lastIssue=lastIssue?:"Allgemeine Websuche lieferte keine thematisch passenden Quellen."
        checkAccess()
        val fallback=runCatching {
            (wikipedia?:WikipediaResearchDiscoveryProvider(checkAccess=checkAccess))
                .discover(query.copy(text=focused))
                .filter { ResearchQuestionFocus.titleMatches(text,it.title) }
        }
            .getOrElse {
                rethrowCancellation(it)
                lastIssue=lastIssue+" Wikipedia-Fallback ebenfalls fehlgeschlagen."
                emptyList()
            }
        checkAccess()
        return fallback.take(8)
    }
    private fun rethrowCancellation(error:Throwable) {
        if(error is InterruptedException)Thread.currentThread().interrupt()
        if(error is InterruptedException||error is CancellationException)throw error
    }
    companion object {
        fun searchUrl(query:String)="https://www.bing.com/search?format=rss&q="+URLEncoder.encode(query,"UTF-8")
        private fun candidate(url:String,title:String?):ResearchSourceCandidate? {
            val decoded=url.replace("&amp;","&").trim()
            val uri=runCatching { URI(decoded) }.getOrNull()?:return null
            if(runCatching { HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(uri) }.isFailure)return null
            val host=uri.host.lowercase(java.util.Locale.ROOT).trimEnd('.')
            if(host=="bing.com"||host.endsWith(".bing.com")||host=="duckduckgo.com"||host.endsWith(".duckduckgo.com")||host=="google.com"||host.endsWith(".google.com"))return null
            return ResearchSourceCandidate(decoded,title?.replace(Regex("\\s+")," ")?.take(256),"general-web-search")
        }
        fun parseSearchResults(raw:String):List<ResearchSourceCandidate> {
            require(raw.length<=150000)
            if(raw.contains(Regex("(?i)<rss\\b")))return parseRss(raw)
            val clean=raw.replace(Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>")," ")
            val href=Regex("""(?i)\bhref\s*=\s*["']([^"'<>\s]{1,2400})["']""")
            return Regex("""(?is)<a\b([^>]{0,3000})>(.*?)</a\s*>""").findAll(clean).mapNotNull { a->
                if(!Regex("""(?i)\bclass\s*=\s*["'][^"']*\bresult__a\b""").containsMatchIn(a.groupValues[1]))return@mapNotNull null
                var url=href.find(a.groupValues[1])?.groupValues?.get(1)?:return@mapNotNull null
                url=url.replace("&amp;","&")
                if(url.startsWith("//duckduckgo.com/l/"))url="https:"+url
                val wrapped=runCatching { URI(url) }.getOrNull()?:return@mapNotNull null
                if(wrapped.host=="duckduckgo.com"&&wrapped.path=="/l/") {
                    val value=wrapped.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("uddg=") }?.substringAfter('=')?:return@mapNotNull null
                    url=runCatching { URLDecoder.decode(value,"UTF-8") }.getOrNull()?:return@mapNotNull null
                }
                candidate(url,a.groupValues[2].replace(Regex("<[^>]*>")," "))
            }.distinctBy { it.url }.take(8).toList()
        }
        private fun parseRss(raw:String):List<ResearchSourceCandidate> {
            require(!Regex("(?is)<!\\s*(doctype|entity)\\b").containsMatchIn(raw)) { "Unzulässige Suchantwort-Deklaration" }
            val result=mutableListOf<ResearchSourceCandidate>()
            val factory=SAXParserFactory.newInstance().apply {
                isValidating=false
                for(f in listOf("http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"))runCatching { setFeature(f,false) }
            }
            var item=false;var field="";var title=StringBuilder();var url=StringBuilder()
            val handler=object:DefaultHandler() {
                override fun startElement(uri:String?,localName:String?,qName:String,attrs:Attributes) {
                    if(qName=="item"){item=true;title=StringBuilder();url=StringBuilder()}
                    field=if(item)qName else ""
                }
                override fun characters(ch:CharArray,start:Int,length:Int) {
                    if(item&&field=="title"&&title.length<1024)title.append(ch,start,minOf(length,1024-title.length))
                    if(item&&field=="link"&&url.length<4096)url.append(ch,start,minOf(length,4096-url.length))
                }
                override fun endElement(uri:String?,localName:String?,qName:String) {
                    if(qName=="item"){if(result.size<8)candidate(url.toString(),title.toString())?.let(result::add);item=false}
                    field=""
                }
            }
            val parser=factory.newSAXParser();parser.xmlReader.entityResolver=org.xml.sax.EntityResolver { _,_->InputSource(StringReader("")) }
            parser.parse(InputSource(StringReader(raw)),handler)
            return result.distinctBy { it.url }.take(8)
        }
        private fun fetchSearchResponse(url:String,check:()->Unit):String {
            check()
            return HttpReadOnlyResearchProvider(ResearchProviderPolicy(maxBytes=150000,maxContentCharacters=100000,totalTimeoutMillis=6500,maxRedirects=1),accessCheck=check)
                .retrieveMarkup(ResearchRequest("public web discovery",url,maxBytes=150000,timeoutMillis=6500,maxRedirects=1)).documents.first().content
        }
    }
}
