package at.heinz.deviceai.notifications

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process hand-over from [ContinuumNotificationListener] to whoever announces notifications.
 *
 * Memory only: nothing is written to disk or logged, and no history is kept beyond the last
 * [MAX_RECENT] entries, which exist so a subscriber that registers a moment late can catch up.
 * Subscribers are called on the publishing thread (the main thread for the Android listener); a
 * subscriber that throws is isolated and does not affect the others or the listener service.
 */
object ContinuumNotificationBus {
    const val MAX_RECENT = 16

    interface Subscriber {
        /** [notification] carries untrusted third-party content: decide with the policy, never execute. */
        fun onNotificationPosted(notification: IncomingNotification)

        /** The notification with [key] left the status bar (dismissed, read or replaced). */
        fun onNotificationRemoved(key: String) {}
    }

    private val subscribers = CopyOnWriteArrayList<Subscriber>()
    private val recentEntries = ArrayDeque<IncomingNotification>()

    /** True while Android has the notification listener bound to this process. */
    @Volatile
    var listenerConnected: Boolean = false
        internal set

    fun subscribe(subscriber: Subscriber) {
        subscribers.addIfAbsent(subscriber)
    }

    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }

    /** The newest entries, oldest first. */
    fun recent(): List<IncomingNotification> = synchronized(recentEntries) { recentEntries.toList() }

    /** Forgets the buffered entries, for example when the owner turns announcements off. */
    fun clear() {
        synchronized(recentEntries) { recentEntries.clear() }
    }

    internal fun publish(notification: IncomingNotification) {
        synchronized(recentEntries) {
            recentEntries.removeAll { it.key == notification.key }
            recentEntries.addLast(notification)
            while (recentEntries.size > MAX_RECENT) recentEntries.removeFirst()
        }
        for (subscriber in subscribers) {
            try {
                subscriber.onNotificationPosted(notification)
            } catch (_: Throwable) {
                // Isolated on purpose; not logged because the stack could carry content.
            }
        }
    }

    internal fun publishRemoved(key: String) {
        synchronized(recentEntries) { recentEntries.removeAll { it.key == key } }
        for (subscriber in subscribers) {
            try {
                subscriber.onNotificationRemoved(key)
            } catch (_: Throwable) {
                // Isolated on purpose.
            }
        }
    }

    internal fun resetForTest() {
        subscribers.clear()
        clear()
        listenerConnected = false
    }
}
