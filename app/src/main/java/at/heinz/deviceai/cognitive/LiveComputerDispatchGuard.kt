package at.heinz.deviceai.cognitive

/** Input requires a fresh monitor. */
internal object LiveComputerDispatchGuard {
    fun isReady(feed: LiveMonitorFeed<*>, taskId: String, captureSession: Long, nowNanos: Long): Boolean {
        val status = feed.status(nowNanos)
        return status.state == LiveMonitorState.LIVE && status.binding?.taskId == taskId &&
            status.binding.captureSession == captureSession && feed.monitorSubmission.isReady(status, nowNanos)
    }

    fun checkLive(feed: LiveMonitorFeed<*>, taskId: String, captureSession: Long, nowNanos: Long) {
        val status = feed.status(nowNanos)
        check(status.state == LiveMonitorState.LIVE && status.binding?.taskId == taskId &&
            status.binding.captureSession == captureSession) { "COMPUTER_LIVE_FRAME_REQUIRED" }
        check(feed.monitorSubmission.isReady(status, nowNanos)) { "COMPUTER_MONITOR_FRAME_REQUIRED" }
    }
}
