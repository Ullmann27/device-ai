package at.heinz.deviceai.cognitive

import java.net.URLEncoder

/** Read-only multi-provider public web discovery with bounded fallback.
 * Public search endpoints may rate-limit or change; failure is explicit.
 * Result URLs are untrusted and must pass ResearchUrlValidator before fetching.
 */
class GeneralWebDiscoveryProvider(
    private val wikipedia: ResearchDiscoveryProvider = WikipediaResearchDiscoveryProvider(),
    private val fetchHtml: (String) -> String = ::fetchSearchHtml
) : ResearchDiscoveryProvider {
    override val isGeneralSearchAvailable: Boolean = true

    override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
        val text = query.text.trim().take(180)
        if (text.isBlank()) return emptyList()
        val primary = runCatching {
            parseSearchResults(fetchHtml(searchUrl(text)))
        }.getOrDefault(emptyList())
        if (primary.isNotEmpty()) return primary.take(8)
        return runCatching { wikipedia.discover(query) }.getOrDefault(emptyList()).take(6)
    }

    companion object {
        fun searchUrl(query: String): String =
            "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&num=8"

        /** Deliberately conservative: never trust arbitrary HTML links as search results. */
        fun parseSearchResults(html: String): List<ResearchSourceCandidate> {
            require(html.length <= 150_000)
            // Only links from the result markup are candidates, never arbitrary text,
            // analytics, scripts or embedded tracking URLs.
            val anchors = Regex("""(?is)<a\\b([^>]{0,2400})>""").findAll(html)
            val href = Regex("""(?i)\\bhref\\s*=\\s*["'](https://[^"'<>\\s]{1,1800})["']""")
            return anchors.mapNotNull { anchor ->
                val raw = href.find(anchor.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
                val url = raw.replace("&amp;", "&")
                val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
                    uri.port !in listOf(-1, 443)) return@mapNotNull null
                val host = uri.host.lowercase(java.util.Locale.ROOT).trimEnd('.')
                if (!host.contains('.') || host == "localhost" ||
                    host == "google.com" || host.endsWith(".google.com") ||
                    host == "google.at" || host.endsWith(".google.at") ||
                    host.endsWith(".local") || host.endsWith(".localhost") ||
                    host.endsWith(".internal") || host.endsWith(".lan") ||
                    host.endsWith(".test") || host.endsWith(".invalid") ||
                    host.endsWith(".example")) return@mapNotNull null
                ResearchSourceCandidate(url, host, "general-web-search")
            }.distinctBy { it.url }.take(8).toList()
        }

        private fun fetchSearchHtml(url: String): String {
            val result = HttpReadOnlyResearchProvider(
                ResearchProviderPolicy(maxBytes = 150_000, maxContentCharacters = 100_000,
                    totalTimeoutMillis = 10_000, maxRedirects = 1)
            ).retrieve(ResearchRequest("public web discovery", url,
                maxBytes = 150_000, timeoutMillis = 10_000, maxRedirects = 1))
            return result.documents.firstOrNull()?.content.orEmpty()
        }
    }
}
