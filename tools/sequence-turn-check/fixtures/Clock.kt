package android.os

/** Only the synchronous main-thread branch is available in this host fixture. */
class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun myLooper() = main
        fun getMainLooper() = main
    }
}

object SystemClock {
    fun uptimeMillis(): Long = error("HOST_FIXTURE_CLOCK_MUST_BE_INJECTED")
}
