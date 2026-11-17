package at.heinz.deviceai.localmodel

import java.util.concurrent.atomic.AtomicInteger

/**
 * One tool proposal maximum per streamed turn, including across independent
 * callback chunks. An ambiguous turn must be rejected in full by its caller.
 */
internal class LocalToolTurnGate {
    private val seen = AtomicInteger(0)

    fun accept(chunkToolCalls: Int): Boolean {
        require(chunkToolCalls >= 0)
        if (chunkToolCalls == 0) return seen.get() <= 1
        return seen.addAndGet(chunkToolCalls) == 1
    }

    fun isAmbiguous(): Boolean = seen.get() > 1
}
