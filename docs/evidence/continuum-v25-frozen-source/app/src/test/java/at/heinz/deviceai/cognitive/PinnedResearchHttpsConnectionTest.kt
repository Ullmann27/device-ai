package at.heinz.deviceai.cognitive

import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import okhttp3.Authenticator
import okhttp3.CookieJar
import org.junit.Assert.*
import org.junit.Test

class PinnedResearchHttpsConnectionTest {
    @Test fun routesUseOnlyPrevalidatedAddressesWithoutReresolvingOrSharingConnections() {
        val ip=InetAddress.getByName("8.8.8.8")
        val addresses=mutableListOf(ip)
        val connection=PinnedResearchHttpsConnection(URI("https://www.nasa.gov/"),addresses,1000)
        connection.connectTimeout=500;connection.readTimeout=500
        addresses.clear()
        val client=connection.client()
        assertEquals(listOf(ip),client.dns.lookup("www.nasa.gov"))
        assertThrows(IllegalStateException::class.java) { client.dns.lookup("private.example.org") }
        assertEquals(Proxy.NO_PROXY,client.proxy)
        assertFalse(client.followRedirects);assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertSame(CookieJar.NO_COOKIES,client.cookieJar)
        assertSame(Authenticator.NONE,client.authenticator)
        assertNull(client.cache)
        connection.disconnect()
        assertThrows(IllegalStateException::class.java) { client.dns.lookup("www.nasa.gov") }
        assertThrows(IllegalStateException::class.java) { connection.connect() }
    }
}
