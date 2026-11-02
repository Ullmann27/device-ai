package at.heinz.deviceai.cognitive

import java.text.Normalizer
import java.util.Locale

/** Read-only app discovery. A label is never an authorization to launch an app. */
internal data class NativeAppCandidate(val packageName: String, val label: String)

internal data class NativeAppMatches(
    val exactLabel: Boolean,
    val total: Int,
    val candidates: List<NativeAppCandidate>
)

internal object NativeAppLookup {
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    fun search(query: String, available: List<NativeAppCandidate>, limit: Int = 12): NativeAppMatches {
        require(query.length in 2..80 && query.none { it.isISOControl() }) { "INVALID_APP_QUERY" }
        require(limit in 1..20) { "INVALID_RESULT_LIMIT" }
        val needle = normalized(query)
        require(needle.length >= 2) { "INVALID_APP_QUERY" }
        val apps = available.asSequence()
            .filter { packagePattern.matches(it.packageName) && it.label.isNotBlank() }
            .distinctBy { it.packageName }
            .toList()
        val exact = apps.filter {
            normalized(it.label) == needle || it.packageName.lowercase(Locale.ROOT) == needle
        }
        // Fuzzy matching yields suggestions, never an executable package selection.
        val matches = if (exact.isNotEmpty()) exact else apps.filter {
            normalized(it.label).contains(needle) || it.packageName.lowercase(Locale.ROOT).contains(needle)
        }
        val sorted = matches.sortedWith(compareBy<NativeAppCandidate> { normalized(it.label) }.thenBy { it.packageName })
        return NativeAppMatches(exactLabel = exact.isNotEmpty(), total = sorted.size,
            candidates = sorted.take(limit).map { it.copy(label = it.label.take(100)) })
    }
}
