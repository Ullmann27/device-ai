package at.heinz.deviceai.cognitive

/** Identity before asynchronous pixel capture. */
internal data class LiveWindowCapture(
    val binding: LiveFrameBinding,
    val epoch: Long,
    val revision: Long,
    val window: LiveWindowRecord,
    val requestedAtUptimeMillis: Long
) {
    fun ageIfCurrent(currentBinding: LiveFrameBinding, currentEpoch: Long, currentRevision: Long,
                     currentWindow: LiveWindowRecord, capturedAtUptimeMillis: Long, nowUptimeMillis: Long): Long? {
        if (binding.origin != LiveFrameOrigin.ACCESSIBILITY_WINDOW || currentBinding != binding ||
            epoch != currentEpoch || revision != currentRevision || currentWindow != window ||
            window.id < 0 || !window.bounds.valid || window.kind != LiveWindowKind.APPLICATION ||
            window.packageName != binding.targetPackage || requestedAtUptimeMillis < 0 ||
            capturedAtUptimeMillis < requestedAtUptimeMillis || capturedAtUptimeMillis <= 0 ||
            capturedAtUptimeMillis > nowUptimeMillis || capturedAtUptimeMillis > Long.MAX_VALUE / 1_000_000) return null
        return (nowUptimeMillis - capturedAtUptimeMillis).takeIf { it <= binding.staleAfterMillis }
    }
}
