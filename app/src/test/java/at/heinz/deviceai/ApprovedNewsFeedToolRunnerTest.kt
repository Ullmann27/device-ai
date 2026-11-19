package at.heinz.deviceai

import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.NewsFeed
import at.heinz.deviceai.cognitive.NewsFeedCatalog
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.ResearchHttpEnvironment
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.WebToolRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import java.util.Collections
import javax.net.ssl.HttpsURLConnection

/** The real news tool runner, grant, ToolGate and research transport; only DNS and HTTPS are deterministic fakes. */
class ApprovedNewsFeedToolRunnerTest {
    private class Response(url: String, val code: Int = 200, val body: String = "", val mime: String = "application/xml",
                           val location: String? = null, val failure: Exception? = null) : HttpsURLConnection(URL(url)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode(): Int { failure?.let { throw it }; return code }
        override fun getContentType() = mime
        override fun getContentLengthLong() = -1L
        override fun getHeaderField(name: String) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private class Environment(val responses: Map<String, Response>) : ResearchHttpEnvironment {
        val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val resolved: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun resolve(host: String): List<InetAddress> { resolved += host; return listOf(InetAddress.getByName("8.8.8.8")) }
        override fun open(uri: URI, addresses: List<InetAddress>, timeoutMillis: Int): HttpsURLConnection {
            opened += uri.toString()
            return responses.getValue(uri.toString())
        }
    }

    private val general = NewsFeedCatalog.feeds.filter { it.general }
    private val mimes = mapOf("orf" to "application/xml", "tagesschau" to "application/xml", "dw" to "application/rss+xml",
        "bbc" to "text/xml", "heise" to "application/atom+xml")

    private fun serving(feeds: List<NewsFeed>, override: Map<String, Response> = emptyMap()) = Environment(
        feeds.associate { it.url to Response(it.url, body = NewsTestFeeds.raw.getValue(it.id), mime = mimes.getValue(it.id)) } + override)

    private fun granted(policy: InternetConsentPolicy = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)): ApprovedWebResearchSession =
        ApprovedWebToolGate(policy).authorizeAlwaysOn(WebToolRequest.Search(OwnerNewsCommand.GRANT_LABEL), ResearchMode.STANDARD)!!

    private fun run(session: ApprovedWebResearchSession, feeds: List<NewsFeed>, environment: Environment): Pair<ToolExecutionResult, ApprovedNewsFeedToolRunner> {
        val runner = ApprovedNewsFeedToolRunner(session, feeds,
            HttpReadOnlyResearchProvider(environment, ApprovedNewsFeedToolRunner.policy, accessCheck = { session.checkActive() }))
        val gate = ToolGate(ToolPolicy(timeoutMillis = 30_000), runner)
        return gate.execute(session.call.proposal().invocation("news-test"), userConfirmed = true) to runner
    }

    @Test fun readsEveryFeedFormatUnderOneGrantThroughTheToolGate() {
        val feeds = general + NewsFeedCatalog.feeds.first { it.id == "heise" }
        val environment = serving(feeds)
        val (execution, runner) = run(granted(), feeds, environment)
        assertEquals(ToolExecutionStatus.SUCCEEDED, execution.record.status)
        val results = runner.results!!
        assertEquals(feeds.map { it.id }, results.map { it.feed.id })
        assertEquals(listOf(6, 4, 3, 2, 2), results.map { it.items.size })
        assertTrue(results.all { it.failure == null })
        // Exactly the catalogue URLs were contacted, nothing else; the grant label carries no owner wording.
        assertEquals(feeds.map { it.url }.toSet(), environment.opened.toSet())
        assertEquals(feeds.size, environment.opened.size)
        assertTrue(execution.output!!.contains("ORF: 6 Einträge"))
        assertEquals("web_search", execution.record.toolName)
    }

    @Test fun aFeedThatFailsIsNamedAndTheOthersAreStillRead() {
        val feeds = general
        val environment = serving(feeds, mapOf(
            feeds[1].url to Response(feeds[1].url, code = 503),
            feeds[2].url to Response(feeds[2].url, failure = java.net.SocketTimeoutException("timeout")),
            feeds[3].url to Response(feeds[3].url, body = "<html><body>Wartung</body></html>", mime = "text/html")))
        val (execution, runner) = run(granted(), feeds, environment)
        assertEquals(ToolExecutionStatus.SUCCEEDED, execution.record.status)
        val results = runner.results!!
        assertNull(results[0].failure)
        assertEquals("Antwort HTTP 503", results[1].failure)
        assertEquals("Zeitüberschreitung", results[2].failure)
        assertEquals("keine lesbaren Einträge", results[3].failure)
        assertTrue(results.drop(1).all { it.items.isEmpty() })
    }

    @Test fun onlyCatalogueHostsAreReachableEvenThroughARedirect() {
        val feed = general.first()
        val environment = serving(general, mapOf(
            feed.url to Response(feed.url, code = 302, location = "https://evil.example.net/feed.xml"),
            "https://evil.example.net/feed.xml" to Response("https://evil.example.net/feed.xml", body = NewsTestFeeds.raw.getValue("orf"))))
        val (_, runner) = run(granted(), general, environment)
        assertEquals("unerwartete Weiterleitung", runner.results!!.first().failure)
        assertFalse(environment.opened.any { it.contains("evil.example") })
        assertFalse(environment.resolved.contains("evil.example.net"))
        // A feed outside the catalogue cannot even be handed to the runner.
        val foreign = NewsFeed("x", "Fremd", "https://evil.example.net/feed.xml", "de", setOf("example.net"))
        assertThrows(IllegalArgumentException::class.java) {
            ApprovedNewsFeedToolRunner(granted(), listOf(foreign), HttpReadOnlyResearchProvider(environment, ApprovedNewsFeedToolRunner.policy))
        }
    }

    @Test fun withoutACurrentGrantNothingIsReadOrPublished() {
        val policy = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val session = granted(policy)
        policy.setMode(InternetAccessMode.OFF) // the owner switched the internet off before the job ran
        val environment = serving(general)
        val (execution, runner) = run(session, general, environment)
        assertFalse(execution.record.status == ToolExecutionStatus.SUCCEEDED)
        assertNull(runner.results)
        assertTrue(environment.opened.isEmpty())
        // OFF never mints a grant; ASK needs the owner's explicit approval of exactly this request.
        assertNull(ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.OFF))
            .authorizeAlwaysOn(WebToolRequest.Search(OwnerNewsCommand.GRANT_LABEL), ResearchMode.STANDARD))
        val ask = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ASK))
        assertNull(ask.authorizeAlwaysOn(WebToolRequest.Search(OwnerNewsCommand.GRANT_LABEL), ResearchMode.STANDARD))
        val approval = ask.prepare(WebToolRequest.Search(OwnerNewsCommand.GRANT_LABEL))!!
        val approved = ask.approve(approval, ResearchMode.STANDARD)!!
        val (first, firstRunner) = run(approved, general, serving(general))
        assertEquals(ToolExecutionStatus.SUCCEEDED, first.record.status)
        assertNotNull(firstRunner.results)
        // One grant, one use.
        val (second, _) = run(approved, general, serving(general))
        assertFalse(second.record.status == ToolExecutionStatus.SUCCEEDED)
    }
}
