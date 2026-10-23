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

/**
 * Durable write of the public-study audit (issue #76). Android's SharedPreferences.commit() also returns false when
 * the calling thread merely carries an interrupt flag: it awaits a CountDownLatch, which throws for an interrupted
 * thread even after the write has finished. A flag that is not the job's own stop request must not turn that into
 * "not persisted": the SAME editor is committed once more with the flag cleared (Android has already moved its
 * changes into memory, so nothing is counted twice; the retry only makes sure that state is on disk) and the flag is
 * restored. A write that still fails gets a fixed cause code instead of an exception class name.
 */
internal object ContinuumStudyAuditWrite {
    const val MIN_FREE_BYTES = 8L * 1024 * 1024

    /** null when the audit reached the disk; otherwise STOPPED, STORAGE_FULL, NOT_WRITABLE or COMMIT_FALSE. */
    fun commit(commit: () -> Boolean, stopRequested: () -> Boolean, freeBytes: () -> Long, writable: () -> Boolean): String? {
        // Read before writing: the latch clears the flag when it throws, so afterwards it would be gone.
        val interrupted = Thread.currentThread().isInterrupted
        try {
            if (commit()) return null
            if (interrupted && stopRequested()) return "STOPPED"
            Thread.interrupted()
            if (commit()) return null
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
        return when {
            runCatching(freeBytes).getOrDefault(Long.MAX_VALUE) < MIN_FREE_BYTES -> "STORAGE_FULL"
            !runCatching(writable).getOrDefault(true) -> "NOT_WRITABLE"
            else -> "COMMIT_FALSE"
        }
    }
}

/** The audit of a public-study cycle did not reach the disk; [reason] is a fixed code of [ContinuumStudyAuditWrite]. */
internal class ContinuumStudyAuditNotPersisted(val reason: String) : IllegalStateException("Study audit not persisted: $reason")
