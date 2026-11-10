package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelRuntime

/** Explicit entry; never called by chat. */
object CognitiveBudgetLocalResearch {
    /** Caller owns the existing inference lane. */
    fun create(
        runtime: LocalModelRuntime,
        sourceRevision: String,
        config: CognitiveBudgetResearchConfig = CognitiveBudgetResearchConfig()
    ): CognitiveBudgetResearchRunner {
        val originNanos = System.nanoTime()
        val originMillis = System.currentTimeMillis()
        // Project epoch onto monotonic elapsed time.
        val clock = {
            val elapsed = (System.nanoTime() - originNanos).coerceAtLeast(0) / 1_000_000L
            if (elapsed > Long.MAX_VALUE - originMillis) Long.MAX_VALUE else originMillis + elapsed
        }
        return CognitiveBudgetResearchRunner(
            LocalModelReasoningAdapter(runtime, nowMillis = clock), sourceRevision, config,
            nowMillis = clock
        )
    }
}
