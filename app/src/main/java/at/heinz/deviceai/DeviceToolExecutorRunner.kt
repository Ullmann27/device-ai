package at.heinz.deviceai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges callback-based device tools into the existing synchronous ToolGate.
 *
 * The ToolGate timeout bounds the caller, but cancelling that outer Future
 * cannot make a missing callback complete. This inner deadline prevents a
 * permanently blocked waiter. A late callback cannot turn a timeout green.
 */
class DeviceToolExecutorRunner internal constructor(
    private val start: (ToolCallRequest, (Result<String>) -> Unit) -> Unit,
    private val callbackTimeoutMillis: Long
) : ToolRunner {
    constructor(executor: DeviceToolExecutor) : this(executor::execute, 18_000)

    init {
        require(callbackTimeoutMillis in 1..18_000)
    }

    override fun execute(request: ToolCallRequest): String {
        val completed = CountDownLatch(1)
        val outcome = AtomicReference<Result<String>?>(null)
        start(request) { result ->
            if (outcome.compareAndSet(null, result)) completed.countDown()
        }
        try {
            if (!completed.await(callbackTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw ToolBackendFailure(
                    ToolExecutionStatus.TIMED_OUT,
                    "Device tool callback did not complete within its deadline."
                )
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ToolBackendFailure(
                ToolExecutionStatus.CANCELLED,
                "Device tool callback wait interrupted."
            )
        }
        return checkNotNull(outcome.get()) { "Missing tool completion." }.getOrThrow()
    }
}
