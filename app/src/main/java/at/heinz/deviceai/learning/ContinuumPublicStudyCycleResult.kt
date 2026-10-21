package at.heinz.deviceai.learning

/**
 * Classifies the independently recorded result of the public-source cycle.
 *
 * An optional local curriculum stage can append a semicolon-suffixed audit
 * to the source phase; it never changes whether source research succeeded.
 * Do not turn a failed source request into ACCEPTED because of a later stage.
 */
internal object ContinuumPublicStudyCycleResult {
    fun outcome(phase: String): String {
        val primary = phase.substringBefore(';')
        return when {
            primary.startsWith("FAILED_") -> "FAILED"
            primary == "TRAINING_ACCEPTED" -> "ACCEPTED"
            primary == "TRAINING_REJECTED" -> "REJECTED"
            primary == "TRAINING_DEFERRED" || primary == "PROVIDER_RATE_LIMIT" ->
                "DEFERRED"
            primary == "INTERRUPTED" || primary == "CANCELLED" -> "INTERRUPTED"
            else -> "NO_CHANGE"
        }
    }

    fun markTopicCompleted(phase: String): Boolean {
        val primary = phase.substringBefore(';')
        return primary != "INTERRUPTED" && primary != "CANCELLED" &&
            primary != "PROVIDER_RATE_LIMIT" && !primary.startsWith("FAILED_")
    }
}
