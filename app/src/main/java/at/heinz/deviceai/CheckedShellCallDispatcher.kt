package at.heinz.deviceai

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounds checked Binder calls to one actual in-flight transaction per bridge.
 *
 * Cancelling a ToolGate Future does not abort an already running Binder call.
 * The slot therefore remains owned until the callable really returns, even
 * after an outer timeout. It is deliberately not released by bridge.stop().
 * This prevents repeated timeouts from creating an unbounded thread pileup.
 */
internal class CheckedShellCallDispatcher(
    private val threadFactory: (Runnable) -> Thread = { task ->
        Thread(task, "device-ai-checked-shell").apply { isDaemon = true }
    }
) {
    private val inFlight = AtomicBoolean(false)

    fun execute(task: () -> String, callback: (Result<String>) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) {
            callback(Result.failure(IllegalStateException(
                "A checked Shizuku operation is still in flight; operator review required."
            )))
            return
        }
        try {
            threadFactory(Runnable {
                try {
                    callback(runCatching { task() })
                } finally {
                    // Only the actual end of this thread's operation releases it.
                    inFlight.set(false)
                }
            }).start()
        } catch (error: RuntimeException) {
            inFlight.set(false)
            callback(Result.failure(error))
        }
    }
}
