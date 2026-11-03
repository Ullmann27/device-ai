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
    private fun visibleLabel(value: String): String = value
        .filterNot { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }
        .replace(Regex("\\s+"), " ").trim().take(100)

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    fun search(query: String, available: List<NativeAppCandidate>, limit: Int = 12): NativeAppMatches {
        require(query.length in 2..80 && query.none { it.isISOControl() }) { "INVALID_APP_QUERY" }
        require(limit in 1..20) { "INVALID_RESULT_LIMIT" }
        val normalizedQuery = normalized(query)
        // This is a read-only lookup, never an instruction interpreter.
        // A negative command must not be rewritten into a positive app launch.
        if (Regex("(?iu)\\b(nicht|kein|keine|niemals)\\b").containsMatchIn(normalizedQuery))
            return NativeAppMatches(false, 0, emptyList())
        val needle = normalizedQuery
            .replace(Regex("(?iu)^(?:bitte\\s+)?(?:öffne|starte|zeige(?: mir)?|suche|finde|öffnen|starten)\\s+(?:mir\\s+)?(?:(?:die|das|den)\\s+)?(?:app\\s+)?"), "")
            .replace(Regex("(?iu)\\s+bitte$"), "")
            .replace(Regex("(?iu)\\s+app$"), "").trim()
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
            candidates = sorted.take(limit).map { it.copy(label = visibleLabel(it.label)) })
    }
}
