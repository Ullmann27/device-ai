package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class ContinuumPublicStudyFetchTest {
    private val title = "Asilomar Conference on Beneficial AI"
    private val valid = """{"query":{"pages":{"123":{"extract":"Ein ausreichend langer, öffentlicher Wikipedia-Auszug für den Forschungstest."}}}}"""
    private class Connection(var body: String, var status: Int = 200,
                             var mime: String = "application/json") : HttpsURLConnection(URL("https://de.wikipedia.org/w/api.php")) {
        var disconnected = false
        var length = -1L
        var retry: String? = null
        var stream: (() -> InputStream)? = null
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String?) = if (name == "Retry-After") retry else null
        override fun getInputStream(): InputStream = stream?.invoke() ?: ByteArrayInputStream(body.toByteArray())
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private fun fetch(c: Connection, cancelled: () -> Boolean = { false }): MentorSource =
        ContinuumPublicStudyFetch.fetch(title, cancelled) { url ->
            assertEquals("https", url.protocol)
            assertEquals("de.wikipedia.org", url.host)
            c
        }
    private fun failure(c: Connection, cancelled: () -> Boolean = { false }): Exception {
        try { fetch(c, cancelled); fail("Expected fetch failure") } catch (e: Exception) { return e }
        error("unreachable")
    }
    @Test fun observedAsilomarMissingResponseIsUnavailableWithoutFalseLearning() {
        val body = javaClass.getResource("/research/asilomar-missing.json")!!.readText()
        val c = Connection(body)
        assertTrue(failure(c) is WikimediaSourceUnavailable)
        assertTrue(c.disconnected)
        val phase = "SOURCE_UNAVAILABLE;STAGE_1_ACCEPTED"
        assertEquals("NO_CHANGE", ContinuumPublicStudyCycleResult.outcome(phase))
        val next = ContinuumTopicFrontier.afterAttempt(listOf(title, "Neuronales Netz"), emptyList(), title,
            ContinuumPublicStudyCycleResult.markTopicCompleted(phase))
        assertEquals(listOf("Neuronales Netz"), next.first)
    }
    @Test fun validResponseIsBoundedHasDigestAndDisconnects() {
        val c = Connection(valid)
        val source = fetch(c)
        assertEquals(ContinuumPublicStudyPolicy.digest(source.excerpt), source.sha)
        assertTrue(c.disconnected)
        assertFalse(c.instanceFollowRedirects)
    }
    @Test fun emptyArticleIsUnavailableButMalformedApiDataIsFailure() {
        assertTrue(failure(Connection("""{"query":{"pages":{"1":{"extract":""}}}}""")) is WikimediaSourceUnavailable)
        assertEquals("FETCH_INVALID_RESPONSE", failure(Connection("""{"query":{}}""")).message)
        assertEquals("FETCH_INVALID_JSON", failure(Connection("<broken>")).message)
        assertEquals("FETCH_API_ERROR", failure(Connection("""{"error":{"code":"badvalue","info":"UNTRUSTED"}}""")).message)
    }
    @Test fun httpFailuresHaveSafeDistinctCodesAndNeverFollowRedirects() {
        for (status in listOf(302, 404, 500)) {
            val c = Connection(valid, status)
            assertEquals("FETCH_HTTP_$status", failure(c).message)
            assertTrue(c.disconnected)
            assertFalse(c.instanceFollowRedirects)
        }
        assertEquals("FETCH_UNEXPECTED_CONTENT", failure(Connection(valid, mime = "text/html")).message)
    }
    @Test fun rateLimitsRespectSecondsAndHttpDateAndHaveNoImmediateRetry() {
        for (status in listOf(429, 503)) {
            val c = Connection(valid, status).apply { retry = "7200" }
            val e = failure(c)
            assertTrue(e is WikimediaRateLimited)
            assertEquals(7200L, (e as WikimediaRateLimited).waitSeconds)
        }
        val c = Connection(valid, 429).apply { retry = "Thu, 01 Jan 1970 00:00:00 GMT" }
        assertEquals(3600L, (failure(c) as WikimediaRateLimited).waitSeconds)
    }
    @Test fun declaredAndStreamingByteLimitsBothFailClosed() {
        assertEquals("FETCH_TOO_LARGE", failure(Connection(valid).apply {
            length = ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES + 1L
        }).message)
        assertEquals("FETCH_TOO_LARGE", failure(Connection("x".repeat(ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES + 1))).message)
    }
    @Test fun transportAndTimeoutAreNotMissingArticles() {
        val timeout = SocketTimeoutException("private transport detail")
        assertSame(timeout, failure(Connection(valid).apply { stream = { throw timeout } }))
        val network = IOException("private transport detail")
        assertSame(network, failure(Connection(valid).apply { stream = { throw network } }))
    }
    @Test fun cancellationBeforeAndDuringReadStopsWithoutAnArticle() {
        var opened = false
        val e = runCatching {
            ContinuumPublicStudyFetch.fetch(title, { true }) { opened = true; Connection(valid) }
        }.exceptionOrNull()
        assertEquals("CANCELLED", e?.message)
        assertFalse(opened)
        var polls = 0
        val c = Connection(valid)
        assertEquals("CANCELLED", failure(c) { ++polls >= 2 }.message)
        assertTrue(c.disconnected)
    }
    @Test fun optionalDiscoveryCannotTreatInvalidResponseAsValidEmptyLinks() {
        val c = Connection("""{"error":{"code":"badvalue"}}""")
        val e = runCatching { ContinuumPublicStudyFetch.discover(title, { false }) { c } }.exceptionOrNull()
        assertEquals("FETCH_API_ERROR", e?.message)
        assertTrue(c.disconnected)
    }
}
