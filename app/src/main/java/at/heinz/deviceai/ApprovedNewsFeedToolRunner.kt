package at.heinz.deviceai

import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.NewsBriefing
import at.heinz.deviceai.cognitive.NewsFeed
import at.heinz.deviceai.cognitive.NewsFeedCatalog
import at.heinz.deviceai.cognitive.NewsFeedParser
import at.heinz.deviceai.cognitive.NewsFeedResult
import at.heinz.deviceai.cognitive.ResearchProvider
import at.heinz.deviceai.cognitive.ResearchProviderPolicy
import at.heinz.deviceai.cognitive.ResearchRequest
import at.heinz.deviceai.cognitive.WebToolRequest
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Reads the fixed news feeds of [NewsFeedCatalog] under ONE consumed owner web grant – the same
 * [ApprovedWebResearchSession] that web research uses (ASK ticket or ALWAYS_ON grant; OFF never gets here), run
 * through the common ToolGate. The grant is narrowed, not widened: only the catalogue's feed hosts are reachable
 * (redirects included), GET only, bounded bytes and time, through the pinned research transport. Nothing the owner
 * said is transmitted; topic selection happens on the device afterwards.
 *
 * A feed that fails is reported with its reason and never replaced by invented content; the tool itself only
 * fails when the grant was revoked.
 */
internal class ApprovedNewsFeedToolRunner(
    private val session: ApprovedWebResearchSession,
    private val feeds: List<NewsFeed>,
    provider: ResearchProvider,
    private val progress: (done: Int, total: Int) -> Unit = { _, _ -> }
) : ToolRunner {
    private val guarded = session.provider(provider)
    @Volatile private var completed: List<NewsFeedResult>? = null

    /** The feeds as read; null until the tool finished or once the grant is no longer current. */
    val results: List<NewsFeedResult>? get() = completed?.takeIf { session.isAuthorized() }

    init { require(feeds.isNotEmpty() && feeds.size <= MAX_FEEDS && feeds.all { it.host in NewsFeedCatalog.hosts }) }

    override fun execute(request: ToolCallRequest): String {
        val call = WebToolRequest.fromTool(request.name, request.arguments)
        val read = session.execute(call) { readAll() }
        session.checkAuthorized()
        completed = read
        return read.joinToString("\n") { result ->
            result.feed.outlet + ": " + (result.failure?.let { "nicht gelesen (" + NewsBriefing.clean(it) + ")" }
                ?: "${result.items.size} Einträge")
        }.also { session.checkAuthorized() }
    }

    private fun readAll(): List<NewsFeedResult> {
        val pool = Executors.newFixedThreadPool(minOf(PARALLEL_READS, feeds.size)) { runnable ->
            Thread(runnable, "device-ai-news-feed").apply { isDaemon = true }
        }
        try {
            var done = 0
            val futures = feeds.map { feed -> pool.submit(Callable { read(feed).also {
                progress(synchronized(this) { ++done }, feeds.size)
            } }) }
            return futures.mapIndexed { index, future ->
                try { future.get(FEED_TIMEOUT_MILLIS + 2_000L, TimeUnit.MILLISECONDS) }
                catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); throw interrupted }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    future.cancel(true)
                    NewsFeedResult(feeds[index], emptyList(), reason(error.cause ?: error))
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun read(feed: NewsFeed): NewsFeedResult = try {
        session.checkActive()
        val document = guarded.retrieve(ResearchRequest("Nachrichten-Feed", feed.url, maxBytes = MAX_FEED_BYTES,
            timeoutMillis = FEED_TIMEOUT_MILLIS, maxRedirects = MAX_REDIRECTS)).documents.firstOrNull()
        val items = document?.let { NewsFeedParser.parse(it.content) }.orEmpty()
        NewsFeedResult(feed, items, if (items.isEmpty()) "keine lesbaren Einträge" else null)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        NewsFeedResult(feed, emptyList(), "abgebrochen")
    } catch (error: Exception) {
        NewsFeedResult(feed, emptyList(), reason(error))
    }

    private fun reason(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is java.net.UnknownHostException || message.contains("could not be resolved") -> "keine Verbindung (Name nicht auflösbar)"
            error is java.net.SocketTimeoutException || error is java.util.concurrent.TimeoutException ||
                message.contains("time limit") || message.contains("timeout", ignoreCase = true) -> "Zeitüberschreitung"
            message.contains("HTTP ") -> "Antwort " + message.substringAfter("HTTP ").take(3).let { "HTTP $it" }
            message.contains("too large") -> "Antwort zu groß"
            message.contains("MIME") -> "kein Feed (unerwarteter Inhaltstyp)"
            message.contains("allowlisted") || message.contains("redirect", ignoreCase = true) -> "unerwartete Weiterleitung"
            message.contains("widerrufen") -> "Internetfreigabe widerrufen"
            error is java.io.IOException -> "keine Verbindung"
            else -> message.ifBlank { error.javaClass.simpleName }
        }
    }

    companion object {
        const val MAX_FEEDS = 8
        const val MAX_FEED_BYTES = 512_000
        const val FEED_TIMEOUT_MILLIS = 8_000
        const val MAX_REDIRECTS = 2
        private const val PARALLEL_READS = 4

        /** Only the catalogue's feed hosts, bounded bytes, time and redirects. */
        val policy = ResearchProviderPolicy(allowedHosts = NewsFeedCatalog.hosts, totalTimeoutMillis = FEED_TIMEOUT_MILLIS,
            maxBytes = MAX_FEED_BYTES, maxRedirects = MAX_REDIRECTS, maxContentCharacters = 100_000)

        /** The research transport restricted by [policy]; [accessCheck] is the live grant check. */
        fun transport(accessCheck: () -> Unit): HttpReadOnlyResearchProvider =
            HttpReadOnlyResearchProvider(policy, accessCheck = accessCheck)
    }
}
