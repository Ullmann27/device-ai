package at.heinz.deviceai.avatar

/** Bounded cosmetic event lifetime. */
class AvatarIconBurst {
    private var startedAt = Long.MIN_VALUE
    private var lastEvent = Long.MIN_VALUE
    private var clearedAt = Long.MIN_VALUE

    fun start(eventMillis: Long, wallMillis: Long, uptimeMillis: Long): Boolean {
        if (eventMillis < 0 || uptimeMillis < 0 || eventMillis > wallMillis ||
            wallMillis - eventMillis > 750 || eventMillis <= lastEvent || eventMillis <= clearedAt) return false
        lastEvent = eventMillis
        if (seconds(uptimeMillis) == null) startedAt = uptimeMillis
        return true
    }

    fun seconds(uptimeMillis: Long): Float? {
        if (startedAt == Long.MIN_VALUE) return null
        if (uptimeMillis < startedAt || uptimeMillis - startedAt >= DURATION_MILLIS) {
            startedAt = Long.MIN_VALUE
            return null
        }
        return (uptimeMillis - startedAt) / 1_000f
    }

    fun clear(wallMillis: Long) {
        startedAt = Long.MIN_VALUE
        clearedAt = maxOf(clearedAt, wallMillis)
    }

    companion object { const val DURATION_MILLIS = 1_950L }
}
