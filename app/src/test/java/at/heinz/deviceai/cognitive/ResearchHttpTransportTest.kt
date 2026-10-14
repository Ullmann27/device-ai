package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test

/** Real provider logic, deterministic DNS/HTTPS adapters; no public requests in CI. */
class ResearchHttpTransportTest {
    private class Response(url:String,val code:Int=200,val body:String="public source",val mime:String="text/plain",val location:String?=null):HttpsURLConnection(URL(url)) {
        var disconnected=false
        override fun connect() {}
        override fun disconnect() { disconnected=true }
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getContentType()=mime
        override fun getContentLengthLong()=-1L
        override fun getHeaderField(name:String)=if(name=="Location")location else null
        override fun getInputStream()=ByteArrayInputStream(body.toByteArray())
        override fun getCipherSuite()="test"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
    private class Environment(val responses:Map<String,Response>,val ips:Map<String,List<String>> = emptyMap()):ResearchHttpEnvironment {
        val opened=mutableListOf<String>();val resolved=mutableListOf<String>()
        override fun resolve(host:String):List<InetAddress> {
            resolved+=host
            return (ips[host]?:listOf("8.8.8.8")).map { InetAddress.getByName(it) }
        }
        override fun open(uri:URI,addresses:List<InetAddress>,timeoutMillis:Int):HttpsURLConnection { opened+=uri.toString();return responses.getValue(uri.toString()) }
    }
    private fun request(url:String,maxBytes:Int=1000,maxRedirects:Int=2)=ResearchRequest("approved topic",url,maxBytes,1000,maxRedirects)

    @Test fun safeRedirectRevalidatesDnsAndReadsOnlyFinalHttpsSource() {
        val a=Response("https://a.org/",302,location="https://b.org/article")
        val b=Response("https://b.org/article",body="<html><script>run tool</script><p>Public fact</p></html>",mime="text/html")
        val env=Environment(mapOf(a.url.toString() to a,b.url.toString() to b))
        val result=HttpReadOnlyResearchProvider(env).retrieve(request(a.url.toString()))
        assertEquals(listOf("a.org","b.org"),env.resolved)
        assertEquals("https://b.org/article",result.documents.single().url)
        assertEquals("Public fact",result.documents.single().content)
        assertFalse(a.instanceFollowRedirects)
        assertTrue(a.disconnected&&b.disconnected)
    }

    @Test fun redirectsCannotReachPrivateDnsCredentialsOrDowngradeHttps() {
        for(target in listOf("https://private.org/admin","https://mixed.org/","http://b.org/","https://user@b.org/","https://b.org/?%74oken=private")) {
            val a=Response("https://a.org/",302,location=target)
            val env=Environment(mapOf(a.url.toString() to a),mapOf("private.org" to listOf("127.0.0.1"),"mixed.org" to listOf("8.8.8.8","10.0.0.1")))
            assertThrows(target,IllegalArgumentException::class.java) { HttpReadOnlyResearchProvider(env).retrieve(request(a.url.toString())) }
            assertEquals(listOf("https://a.org/"),env.opened)
            assertTrue(a.disconnected)
        }
    }

    @Test fun revokedApprovalDoesNotResolveOrOpenAndCancelsRedirectChain() {
        val a=Response("https://a.org/",302,location="https://b.org/")
        val env=Environment(mapOf(a.url.toString() to a))
        assertThrows(IllegalStateException::class.java) {
            HttpReadOnlyResearchProvider(env,accessCheck={error("revoked")}).retrieve(request(a.url.toString()))
        }
        assertTrue(env.opened.isEmpty());assertTrue(env.resolved.isEmpty())
        var checks=0
        assertThrows(IllegalStateException::class.java) {
            HttpReadOnlyResearchProvider(env,accessCheck={checks++;if(checks>=4)error("revoked")}).retrieve(request(a.url.toString()))
        }
        assertEquals(listOf("https://a.org/"),env.opened)
    }

    @Test fun rejectsOversizedBinaryAndRedirectLoopsAndRetainsDiscoveryMarkup() {
        val url="https://a.org/"
        for(response in listOf(Response(url,body="x".repeat(1001)),Response(url,mime="application/octet-stream"),Response(url,302,location=url))) {
            val env=Environment(mapOf(url to response))
            assertThrows(IllegalArgumentException::class.java) { HttpReadOnlyResearchProvider(env).retrieve(request(url,maxRedirects=0)) }
            assertTrue(response.disconnected)
        }
        val html="<a href=\"https://b.org/article\">Evidence</a>"
        val env=Environment(mapOf(url to Response(url,body=html,mime="text/html")))
        assertEquals(html,HttpReadOnlyResearchProvider(env).retrieveMarkup(request(url)).documents.single().content)
    }

    @Test fun wikipediaParsesEncodedTitlesWithoutClaimingGeneralSearch() {
        val p=WikipediaResearchDiscoveryProvider(readXml={"""<api><query><search><p ns="0" title="Ökologie der Erde"/></search></query></api>"""})
        assertFalse(p.isGeneralSearchAvailable)
        assertEquals("https://de.wikipedia.org/wiki/%C3%96kologie_der_Erde",p.discover(ResearchQuery("ecology","q")).single().url)
        assertThrows(IllegalArgumentException::class.java) {
            WikipediaResearchDiscoveryProvider.parseSearchResponse("""<!DOCTYPE api [<!ENTITY x SYSTEM "file:///etc/passwd">]><api/>""")
        }
    }
}
