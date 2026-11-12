package at.heinz.deviceai.cognitive

import java.util.concurrent.atomic.AtomicInteger

/** Correlates responses, never grants execution. */
internal class ComputerCaptureConsent(
    private val codes: AtomicInteger = processCodes
) {
    data class Request(val requestCode: Int, val taskId: String)
    private var pending: Request? = null

    @Synchronized fun begin(taskId: String): Request {
        require(taskId.matches(TASK_ID)) { "INVALID_CAPTURE_TASK" }
        pending = null
        val request = Request(reserveCode(), taskId)
        pending = request
        return request
    }

    /** Caller checks result and visibility. */
    @Synchronized fun accept(requestCode: Int): String? {
        val current = pending ?: return null
        if (requestCode != current.requestCode) return null
        pending = null
        return current.taskId
    }

    @Synchronized fun cancel() { pending = null }

    private fun reserveCode(): Int {
        while (true) {
            val next = codes.get()
            check(next in FIRST_REQUEST..LAST_REQUEST) { "CAPTURE_REQUEST_CODES_EXHAUSTED" }
            if (codes.compareAndSet(next, next + 1)) return next
        }
    }

    companion object {
        // PDF owns codes through 60000.
        const val FIRST_REQUEST = 60_001
        const val LAST_REQUEST = 64_999
        private val processCodes = AtomicInteger(FIRST_REQUEST)
        private val TASK_ID = Regex("[A-Za-z0-9._:-]{1,128}")
        fun ownsRequestCode(code: Int): Boolean = code in FIRST_REQUEST..LAST_REQUEST
    }
}
