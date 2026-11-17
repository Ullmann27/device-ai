package at.heinz.deviceai.intelligence

import java.net.URI

/** Pure redirect policy for a GitHub release APK after the exact browser URL passed preflight. */
internal object ContinuumGitHubDownloadRedirectPolicy {
    const val MAX_REDIRECTS = 3
    private val assetHosts = setOf(
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    fun resolve(current: URI, location: String): URI? {
        if (location.isBlank() || location.length > 4_096) return null
        val target = runCatching { current.resolve(location) }.getOrNull() ?: return null
        if (!target.scheme.equals("https", ignoreCase = true) ||
            target.userInfo != null || target.rawFragment != null ||
            (target.port != -1 && target.port != 443)
        ) return null

        val from = current.host?.lowercase() ?: return null
        val to = target.host?.lowercase() ?: return null
        val allowed = when {
            from == ContinuumGitHubReleaseCatalog.DOWNLOAD_HOST ->
                to in assetHosts
            from in assetHosts ->
                to in assetHosts
            else -> false
        }
        if (!allowed) return null
        // Signed asset URLs require a query; the original github.com URL was already
        // checked separately and is never accepted here as a redirect target.
        if (target.rawPath.isNullOrBlank()) return null
        return target
    }
}
