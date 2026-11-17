package android.os

/** Compile-only Android stand-in. Host tests cannot simulate or run a Handler queue. */
class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper) {
    fun post(@Suppress("UNUSED_PARAMETER") action: Runnable): Boolean =
        error("HOST_FIXTURE_MAIN_HANDLER_QUEUE_UNAVAILABLE")
}
