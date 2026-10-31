package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.GeneralWebDiscoveryProvider
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.ResearchProviderPolicy
import at.heinz.deviceai.cognitive.ResearchQuery
import at.heinz.deviceai.cognitive.ResearchRequest
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
    private fun allowed(url: String): Boolean = runCatching {
        val host = URI(ResearchUrlCanonicalizer.canonicalize(url)).host.lowercase()
        host != "wikipedia.org" && !host.endsWith(".wikipedia.org") &&
            host != "wikimedia.org" && !host.endsWith(".wikimedia.org")
    }.getOrDefault(false)

    fun fetch(topic: String, cancelled: () -> Boolean): MentorSource {
        require(ContinuumPublicStudyPolicy.validArticleTitle(topic))
        fun checkActive() { check(!cancelled()) { "WEB_STUDY_CANCELLED" } }
        checkActive()
        val candidates = GeneralWebDiscoveryProvider(checkAccess = ::checkActive)
            .discover(ResearchQuery(topic, "q0"))
            .asSequence().filter { allowed(it.url) }.distinctBy { it.url }.take(4).toList()
        if (candidates.isEmpty()) throw IOException("NO_PUBLIC_WEB_RESULTS")
        val reader = HttpReadOnlyResearchProvider(
            ResearchProviderPolicy(
                maxBytes = 96 * 1024,
                maxContentCharacters = 8_000,
                totalTimeoutMillis = 10_000,
                maxRedirects = 2
            ),
            accessCheck = ::checkActive
        )
        var lastFailure: String = "NO_USABLE_PUBLIC_SOURCE"
        for (candidate in candidates) {
            checkActive()
            val attempt = runCatching {
                val result = reader.retrieve(
                    ResearchRequest(topic, candidate.url, 96 * 1024, 10_000, 2)
                )
                val doc = result.documents.firstOrNull() ?: error("EMPTY_RESPONSE")
                check(allowed(doc.url)) { "ENCYCLOPEDIA_NOT_SELECTED" }
                val excerpt = ContinuumPublicStudyPolicy.normalizeExcerpt(doc.content)
                MentorSource(topic, ResearchUrlCanonicalizer.canonicalize(doc.url),
                    ContinuumPublicStudyPolicy.digest(excerpt), excerpt).also {
                    MentorSourceValidation.checkSource(it)
                }
            }
            if (attempt.isSuccess) return attempt.getOrThrow()
            lastFailure = attempt.exceptionOrNull()?.javaClass?.simpleName ?: "FAILED"
        }
        throw IOException("PUBLIC_WEB_SOURCE_UNAVAILABLE_" + lastFailure.take(40))
    }

    /** Non-authoritative topic links. No URLs, instructions, or raw page text
     * enter the persistent topic queue. Only plain public titles are accepted.
     */
    fun discoverTitles(topic: String, cancelled: () -> Boolean): List<String> {
        require(ContinuumPublicStudyPolicy.validArticleTitle(topic))
        fun checkActive() { check(!cancelled()) { "WEB_STUDY_CANCELLED" } }
        return GeneralWebDiscoveryProvider(checkAccess = ::checkActive)
            .discover(ResearchQuery(topic, "q0")).asSequence()
            .filter { allowed(it.url) }
            .mapNotNull { it.title?.trim() }
            .filter { ContinuumPublicStudyPolicy.validArticleTitle(it) }
            .filter { it != topic }
            .distinct().take(8).toList()
    }
}
