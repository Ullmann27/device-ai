package at.heinz.deviceai

/**
 * Truncates human-readable telemetry only; never changes the underlying
 * neural result, checkpoint or owner-approved learning record.
 */
internal object ContinuumStatusExcerpt {
    fun readable(raw: String, maxChars: Int): String {
        require(maxChars >= 8)
        val compact = raw.trim().replace(Regex("\\s+"), " ")
        if (compact.length <= maxChars) return compact
        val segment = compact.substring(0, maxChars - 1).trimEnd()
        val boundary = segment.lastIndexOf(' ')
        val prefix = if (boundary >= maxChars / 2) segment.substring(0, boundary) else segment
        return prefix.trimEnd() + "…"
    }
}
