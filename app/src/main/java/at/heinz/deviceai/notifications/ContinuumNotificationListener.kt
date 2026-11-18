package at.heinz.deviceai.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Receives posted notifications after the owner granted notification access in Android settings.
 *
 * The service decides nothing and speaks nothing. It maps a notification of an app the owner
 * enabled to an [IncomingNotification] and hands it to [ContinuumNotificationBus]. For everything
 * else (announcements off, app without a rule, own package) the extras are not even read.
 * Content is never logged and never written to disk.
 */
class ContinuumNotificationListener : NotificationListenerService() {
    // App labels are not notification content; a tiny cache avoids a PackageManager call per message.
    private val labels = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_CACHED_LABELS
    }

    /** Speaks during a drive while another app is in front; silent whenever the app's own screen handles it. */
    private var driveAnnouncer: DriveBackgroundAnnouncer? = null

    override fun onListenerConnected() {
        ContinuumNotificationBus.listenerConnected = true
        if (driveAnnouncer == null) driveAnnouncer = DriveBackgroundAnnouncer(this).also { it.start() }
    }

    override fun onListenerDisconnected() {
        ContinuumNotificationBus.listenerConnected = false
        driveAnnouncer?.stop()
        driveAnnouncer = null
    }

    override fun onDestroy() {
        ContinuumNotificationBus.listenerConnected = false
        driveAnnouncer?.stop()
        driveAnnouncer = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val incoming = map(sbn) ?: return
            ContinuumNotificationBus.publish(incoming)
        } catch (_: RuntimeException) {
            // A malformed third-party notification must not take the listener down. Not logged.
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        ContinuumNotificationBus.publishRemoved(key)
    }

    private fun map(sbn: StatusBarNotification): IncomingNotification? {
        val settings = NotificationAnnouncementSettings.load(this)
        if (!settings.enabled) return null
        val sourcePackage = sbn.packageName ?: return null
        if (sourcePackage == packageName) return null
        val mode = settings.apps[sourcePackage]?.mode ?: AnnouncementMode.OFF
        if (mode == AnnouncementMode.OFF) return null

        val notification = sbn.notification ?: return null
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val flags = notification.flags
        return IncomingNotification(
            key = sbn.key ?: return null,
            packageName = sourcePackage,
            appLabel = appLabel(sourcePackage),
            title = bounded(title),
            text = bounded(text),
            category = notification.category,
            isOngoing = sbn.isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            postedAtMs = sbn.postTime,
            isOwnPackage = false,
        )
    }

    /** Keeps no more than the policy will ever inspect, so large payloads are not held in memory. */
    private fun bounded(value: CharSequence?): String {
        if (value == null) return ""
        val end = minOf(value.length, UntrustedNotificationText.MAX_RAW)
        return value.subSequence(0, end).toString()
    }

    private fun appLabel(sourcePackage: String): String {
        labels[sourcePackage]?.let { return it }
        val label = try {
            val info = packageManager.getApplicationInfo(sourcePackage, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "" // The policy then says "Eine App"; no name is guessed from the package id.
        }
        if (label.isNotEmpty()) labels[sourcePackage] = label
        return label
    }

    private companion object {
        const val MAX_CACHED_LABELS = 32
    }
}
