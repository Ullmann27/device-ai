package at.heinz.deviceai.cognitive

import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Private GET-only adapter for the existing bounded research reader. Pinning
 * happens at route selection, BEFORE TCP connects, rather than after a platform
 * URLConnection may have resolved the host again. TLS and hostname verification
 * use OkHttp's normal certificate validation. No global cookies/auth/cache/proxy.
 */
internal class PinnedResearchHttpsConnection(
    uri:URI, addresses:List<InetAddress>, private val deadlineMillis:Int
):HttpsURLConnection(uri.toURL()) {
    private val approvedHost=uri.host.removePrefix("[").removeSuffix("]")
    private val pinned=addresses.toList()
    @Volatile private var call:Call?=null
    @Volatile private var response:Response?=null
    @Volatile private var stopped=false

    internal fun client():OkHttpClient = OkHttpClient.Builder()
        .dns(object:Dns {
            override fun lookup(hostname:String):List<InetAddress> {
                check(hostname.equals(approvedHost,true)&&!stopped) { "Unapproved research DNS route" }
                return pinned
            }
        })
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectionPool(ConnectionPool(0,1,TimeUnit.MILLISECONDS))
        .connectTimeout(connectTimeout.coerceAtLeast(1).toLong(),TimeUnit.MILLISECONDS)
        .readTimeout(readTimeout.coerceAtLeast(1).toLong(),TimeUnit.MILLISECONDS)
        .callTimeout(deadlineMillis.toLong(),TimeUnit.MILLISECONDS)
        .build()

    @Synchronized override fun connect() {
        if(response!=null)return
        check(!stopped) { "Research connection cancelled" }
        require(requestMethod=="GET"&&!doOutput) { "Research transport is GET-only" }
        val builder=Request.Builder().url(url).get()
        requestProperties.forEach { (name,values)->values.forEach { builder.addHeader(name,it) } }
        val pending=client().newCall(builder.build())
        call=pending
        if(stopped)pending.cancel()
        val received=pending.execute()
        if(stopped){received.close();error("Research connection cancelled")}
        response=received
        connected=true
    }
    override fun disconnect() { stopped=true;call?.cancel();response?.close() }
    override fun usingProxy()=false
    private fun result():Response { connect();return checkNotNull(response) }
    override fun getResponseCode()=result().code
    override fun getContentType()=result().header("Content-Type")
    override fun getContentLengthLong()=result().body?.contentLength()?:-1L
    override fun getHeaderField(name:String)=result().header(name)
    override fun getInputStream()=checkNotNull(result().body).byteStream()
    override fun getCipherSuite()=result().handshake?.cipherSuite?.javaName?:""
    override fun getLocalCertificates():Array<Certificate>?=result().handshake?.localCertificates?.toTypedArray()
    override fun getServerCertificates():Array<Certificate> = result().handshake?.peerCertificates?.toTypedArray()?:emptyArray()
}
