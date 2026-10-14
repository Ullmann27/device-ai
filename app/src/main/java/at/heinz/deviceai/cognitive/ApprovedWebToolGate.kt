package at.heinz.deviceai.cognitive

import java.net.URI

/** Typed, read-only web capabilities. The owner approves the exact request,
 * not an unrestricted browsing session. Nothing here can call device tools.
 */
sealed class WebToolRequest {
    data class Search(val query: String) : WebToolRequest()
    data class Read(val url: String) : WebToolRequest()
}

data class WebToolResult(\n    val operation: String,\n    val sources: List<ResearchSourceCandidate>,\n    val documents: List<ResearchDocument> = emptyList()\n)

class ApprovedWebToolGate(
    private val consent: InternetConsentPolicy,
    private val discovery: ResearchDiscoveryProvider,
    private val reader: (String) -> ResearchDocument
) {
    private var pending: WebToolRequest? = null

    @Synchronized fun request(call: WebToolRequest): Boolean {
        pending = null
        if (consent.mode != InternetAccessMode.ASK) return false
        when (call) {
            is WebToolRequest.Search -> {
                if (call.query.isBlank() || call.query.length > 512) return false
            }
            is WebToolRequest.Read -> {
                if (call.url.length !in 10..2048) return false
                val uri = runCatching { URI(call.url) }.getOrNull() ?: return false
                if (uri.scheme != "https" || uri.host.isNullOrBlank() ||
                    uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
            }
        }
        if (!consent.request(key(call))) return false
        pending = call
        return true
    }

    /** Denies replay and mismatched requests. All network activity remains inside
     * the existing bounded discovery/provider implementations supplied by caller.
     */
    @Synchronized fun execute(call: WebToolRequest): WebToolResult? {
        val matched = pending == call
        pending = null
        if (!matched || !InternetResearchGate(consent).authorize(key(call))) return null
        return when (call) {
            is WebToolRequest.Search -> WebToolResult(
                "web_search", discovery.discover(ResearchQuery(call.query, "web-tool")).take(8)
            )
            is WebToolRequest.Read -> {
                val document = reader(call.url)
                WebToolResult("web_read", listOf(ResearchSourceCandidate(document.url, document.title)), listOf(document))
            }
        }
    }

    @Synchronized fun cancel() {
        pending = null
        consent.cancel()
    }

    private fun key(call: WebToolRequest): String = when (call) {
        is WebToolRequest.Search -> "search:" + call.query
        is WebToolRequest.Read -> "read:" + call.url
    }
}
