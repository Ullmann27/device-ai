package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.GeneralWebDiscoveryProvider
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.ResearchDiscoveryProvider
import at.heinz.deviceai.cognitive.ResearchProvider
import at.heinz.deviceai.cognitive.ResearchProviderPolicy
import at.heinz.deviceai.cognitive.ResearchQuery
import at.heinz.deviceai.cognitive.ResearchRequest
import at.heinz.deviceai.cognitive.ResearchSourceCandidate
import at.heinz.deviceai.cognitive.ResearchUrlCanonicalizer
import java.net.URI
import java.io.IOException

/**
 * One bounded public-web evidence acquisition. The topic is a public keyword,
 * never a private chat transcript. All network requests pass the same guarded,
 * DNS-validated, TLS-only transport as manually approved web research.
 * Discovered text remains an untrusted source observation, never instructions.
 */
internal object ContinuumOpenWebStudyFetch {
    private val titleSeparators = Regex("""\s+[-–—|•·/]\s+|\s*:\s+""")
    private val bracketAnnotations = Regex("""\s*\[[^\]]{1,32}]\s*""")
    private val transientFailureHints = Regex("""(?i)\b(timeout|time limit|resolved|HTTP (?:429|5\d\d))\b""")

    private fun isTransientFailure(error: Throwable): Boolean =
        error is java.net.SocketTimeoutException ||
            error is IOException ||
            transientFailureHints.containsMatchIn(error.message.orEmpty())

    private fun allowed(url: String): Boolean = runCatching {
        val canonical = ResearchUrlCanonicalizer.canonicalize(url)
        val uri = URI(canonical)
        val host = uri.host.lowercase()
        canonical.length in 15..420 &&
            (uri.rawQuery?.length ?: 0) <= 600 &&
            host != "wikipedia.org" && !host.endsWith(".wikipedia.org") &&
            host != "wikimedia.org" && !host.endsWith(".wikimedia.org")
    }.getOrDefault(false)

    data class Acquisition(
        val source: MentorSource,
        val discoveredTitles: List<String> = emptyList()
    )

    /** Candidates are untrusted public titles, not commands or owner facts. */
    internal fun extractCandidateTitles(rawTitle: String?, currentTopic: String): List<String> {
        val cleaned = rawTitle
            ?.replace(bracketAnnotations, " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (cleaned.isEmpty()) return emptyList()
        val segments = listOf(cleaned) + cleaned.split(titleSeparators).map { it.trim() }
        return segments.asSequence()
            .map { it.trim(' ', '-', '–', '—', '|', ':', '•', '·', '/', '„', '“', '"', '\'') }
            .filter { ContinuumPublicStudyPolicy.validArticleTitle(it) }
            .filterNot { it.equals(currentTopic, ignoreCase = true) }
            .distinct()
            .toList()
    }

    fun acquire(
        topic: String,
        cancelled: () -> Boolean,
        completedShas: Set<String> = emptySet(),
        discoveryFactory: (() -> Unit) -> ResearchDiscoveryProvider = { check ->
            GeneralWebDiscoveryProvider(checkAccess = check)
        },
        readerFactory: (() -> Unit) -> ResearchProvider = { check ->
            HttpReadOnlyResearchProvider(
                ResearchProviderPolicy(
                    maxBytes = 96 * 1024,
                    maxContentCharacters = 8_000,
                    totalTimeoutMillis = 10_000,
                    maxRedirects = 2
                ),
                accessCheck = check
            )
        }
    ): Acquisition {
        require(ContinuumPublicStudyPolicy.validArticleTitle(topic))
        fun checkActive() { check(!cancelled()) { "WEB_STUDY_CANCELLED" } }
        checkActive()
        val discovery = discoveryFactory(::checkActive)
        val rawCandidates = discovery.discover(ResearchQuery(topic, "q0"))
        val discoveredTitles = extractTopicsFromCandidates(rawCandidates, topic)
        val candidates = rawCandidates
            .asSequence()
            .filter { allowed(it.url) }
            .distinctBy { ResearchUrlCanonicalizer.canonicalize(it.url) }
            .take(12)
            .toList()
        if (candidates.isEmpty()) {
            // A provider outage must never permanently retire a research topic.
            // Genuine empty responses are still classified as no public results.
            if (discovery is GeneralWebDiscoveryProvider && discovery.allSearchEndpointsFailed) {
                throw IOException("WEB_GENERAL_SEARCH_UNAVAILABLE")
            }
            throw WikimediaSourceUnavailable("NO_PUBLIC_WEB_RESULTS")
        }
        val reader = readerFactory(::checkActive)
        var fallbackKnownSource: MentorSource? = null
        var fallbackTopics: List<String> = discoveredTitles
        var firstTransientFailure: Throwable? = null
        var lastFailure: Throwable? = null
        for (candidate in candidates) {
            checkActive()
            val attempt = runCatching {
                val result = reader.retrieve(
                    ResearchRequest(topic, candidate.url, 96 * 1024, 10_000, 2)
                )
                val doc = result.documents.firstOrNull() ?: error("EMPTY_RESPONSE")
                check(allowed(doc.url)) { "ENCYCLOPEDIA_NOT_SELECTED" }
                val excerpt = ContinuumPublicStudyPolicy.normalizeExcerpt(doc.content)
                val source = MentorSource(
                    topic,
                    ResearchUrlCanonicalizer.canonicalize(doc.url),
                    ContinuumPublicStudyPolicy.digest(excerpt),
                    excerpt
                ).also {
                    MentorSourceValidation.checkSource(it)
                }
                val mergedTopics = (extractCandidateTitles(doc.title, topic) + discoveredTitles)
                    .distinct()
                    .take(12)
                source to mergedTopics
            }
            // Cancellation can happen while the reader is running. Do not convert
            // that interruption into a permanent unavailable-source result.
            checkActive()
            if (attempt.isSuccess) {
                val (source, mergedTopics) = attempt.getOrThrow()
                if (source.sha.lowercase() !in completedShas) {
                    return Acquisition(source, mergedTopics)
                }
                if (fallbackKnownSource == null) {
                    fallbackKnownSource = source
                    fallbackTopics = mergedTopics
                }
            } else {
                val err = attempt.exceptionOrNull()
                if (err != null) {
                    lastFailure = err
                    if (firstTransientFailure == null && isTransientFailure(err)) {
                        firstTransientFailure = err
                    }
                }
            }
        }
        if (fallbackKnownSource != null) {
            return Acquisition(fallbackKnownSource, fallbackTopics)
        }
        val transient = firstTransientFailure
        if (transient is java.net.SocketTimeoutException) throw transient
        if (transient is IOException) throw transient
        if (transient != null) {
            if (transient.message.orEmpty().contains("time limit", ignoreCase = true)) {
                throw java.net.SocketTimeoutException("WEB_STUDY_TIMEOUT")
            }
            throw IOException("WEB_STUDY_TRANSIENT_" + transient.javaClass.simpleName.take(24))
        }
        val reason = lastFailure?.javaClass?.simpleName?.take(32) ?: "NO_USABLE_PUBLIC_SOURCE"
        throw WikimediaSourceUnavailable("WEB_" + reason)
    }

    fun fetch(
        topic: String,
        cancelled: () -> Boolean,
        completedShas: Set<String> = emptySet()
    ): MentorSource = acquire(topic, cancelled, completedShas).source

    private fun extractTopicsFromCandidates(
        candidates: List<ResearchSourceCandidate>,
        topic: String
    ): List<String> = candidates.asSequence()
        .filter { allowed(it.url) }
        .flatMap { extractCandidateTitles(it.title, topic).asSequence() }
        .distinct()
        .take(12)
        .toList()

    /** Only plain, bounded public titles enter the persistent topic queue. */
    fun discoverTitles(
        topic: String,
        cancelled: () -> Boolean,
        discoveryFactory: (() -> Unit) -> ResearchDiscoveryProvider = { check ->
            GeneralWebDiscoveryProvider(checkAccess = check)
        }
    ): List<String> {
        require(ContinuumPublicStudyPolicy.validArticleTitle(topic))
        fun checkActive() { check(!cancelled()) { "WEB_STUDY_CANCELLED" } }
        val candidates = discoveryFactory(::checkActive).discover(ResearchQuery(topic, "q0"))
        return extractTopicsFromCandidates(candidates, topic)
    }
}
