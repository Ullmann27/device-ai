package at.heinz.deviceai

import java.util.concurrent.atomic.AtomicInteger

/**
 * Per invocation linearization point for the decision to start a backend.
 *
 * A caller may revoke a PENDING start, but may not retroactively declare a
 * COMMITTED dispatch unexecuted. The latter must be reported as potentially
 * dispatched if its result is lost. This is not an OS sandbox or a guarantee
 * that a non-cooperative backend can be rolled back.
 */
internal class ToolExecutionStartLatch {
    private val phase = AtomicInteger(PENDING)

    fun commitIfPending(): Boolean = phase.compareAndSet(PENDING, COMMITTED)

    /** True only if the backend was definitely not committed by this latch. */
    fun revokeIfPending(): Boolean = phase.compareAndSet(PENDING, REVOKED)

    val wasCommitted: Boolean get() = phase.get() == COMMITTED
    val wasRevokedBeforeCommit: Boolean get() = phase.get() == REVOKED

    private companion object {
        const val PENDING = 0
        const val COMMITTED = 1
        const val REVOKED = 2
    }
}
