package at.heinz.deviceai.learning

/**
 * Classifies the independently recorded result of the public-source cycle.
 *
 * An optional local curriculum stage can append a semicolon-suffixed audit
 * to the source phase; it never changes whether source research succeeded.
 * Do not turn a failed source request into ACCEPTED because of a later stage.
 */
internal object ContinuumPublicStudyCycleResult {
    fun failurePhase(operation:String,error:Throwable,cancelled:Boolean):String = when {
        cancelled || error is WikimediaFetchCancelled -> "INTERRUPTED"
        error is WikimediaRateLimited -> "PROVIDER_RATE_LIMIT"
        error is WikimediaSourceUnavailable -> "SOURCE_UNAVAILABLE_"+error.reason
        error is WikimediaFetchFailure -> "FAILED_"+error.code
        operation in setOf("FETCH_SOURCE","DISCOVER_TOPICS") && error is java.net.SocketTimeoutException ->
            "FAILED_FETCH_TIMEOUT"
        operation in setOf("FETCH_SOURCE","DISCOVER_TOPICS") && error is java.io.IOException ->
            "FAILED_FETCH_NETWORK"
        else -> ("FAILED_"+operation+"_"+error.javaClass.simpleName).take(65)
    }

    // Local rehearsal after source processing must not erase the source outcome.
    fun withDeferral(phase:String,reason:String?):String =
        if(reason!=null && phase.substringBefore(';') in setOf("INTERRUPTED","CANCELLED","TRAINING_DEFERRED"))
            "DEFERRED_"+reason else phase

    fun outcome(phase: String): String {
        val primary = phase.substringBefore(';')
        return when {
            primary.startsWith("FAILED_") -> "FAILED"
            primary == "TRAINING_ACCEPTED" -> "ACCEPTED"
            primary == "TRAINING_REJECTED" -> "REJECTED"
            primary == "TRAINING_DEFERRED" || primary == "PROVIDER_RATE_LIMIT" || primary.startsWith("DEFERRED_") ->
                "DEFERRED"
            primary == "INTERRUPTED" || primary == "CANCELLED" -> "INTERRUPTED"
            else -> "NO_CHANGE"
        }
    }

    fun markTopicCompleted(phase: String): Boolean {
        val primary = phase.substringBefore(';')
        return primary in setOf("TRAINING_ACCEPTED", "TRAINING_REJECTED", "SOURCE_UNCHANGED",
            "SOURCE_UNAVAILABLE", "SOURCE_UNAVAILABLE_MISSING_PAGE", "SOURCE_UNAVAILABLE_EMPTY_EXCERPT")
    }
}
