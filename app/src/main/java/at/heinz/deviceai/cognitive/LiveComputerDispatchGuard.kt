package at.heinz.deviceai.cognitive

/** Input requires a fresh monitor. */
internal object LiveComputerDispatchGuard {
    fun checkLive(feed: LiveMonitorFeed<*>, taskId: String, captureSession: Long, nowNanos: Long) {
        val status = feed.status(nowNanos)
        check(status.state == LiveMonitorState.LIVE && status.binding?.taskId == taskId &&
            status.binding.captureSession == captureSession) { "COMPUTER_LIVE_FRAME_REQUIRED" }
    }
}
