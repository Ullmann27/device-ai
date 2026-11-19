package at.heinz.deviceai.localmodel

/**
 * Texts of others the app has read out or shown on this device – notifications, incoming messages, what the
 * incoming-message conversation repeats of them. The consent text promises that such content is not sent to the
 * cloud brain; this is the runtime's last check for it, independent of how a prompt was assembled: a request that
 * contains one of these texts is refused before any network use (see [ClaudeModelRuntime]).
 *
 * Process memory only, bounded, nothing is written anywhere. Short texts are not kept: a common phrase must never
 * block an ordinary question.
 */
internal object DeviceLocalOnlyText {
    const val MIN_CHARS = 24
    const val MAX_KEPT = 64

    private val kept = ArrayDeque<String>()

    private fun normal(text: String): String = text.replace(Regex("\\s+"), " ").trim().lowercase()

    fun remember(text: String) {
        val value = normal(text)
        if (value.length < MIN_CHARS) return
        synchronized(kept) {
            kept.remove(value)
            kept.addLast(value)
            while (kept.size > MAX_KEPT) kept.removeFirst()
        }
    }

    /** true when [text] carries one of the kept texts (or a long part of one: a quoted notification is still one). */
    fun carriedIn(text: String): Boolean {
        val haystack = normal(text)
        return synchronized(kept) { kept.any { it in haystack } }
    }

    /** Host tests only. */
    internal fun clear() = synchronized(kept) { kept.clear() }
}
