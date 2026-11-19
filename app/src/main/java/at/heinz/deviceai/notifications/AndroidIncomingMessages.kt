package at.heinz.deviceai.notifications

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.accessibility.AccessibilityEvent
import at.heinz.deviceai.AgentActivity
import at.heinz.deviceai.R
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Reads from a posted [Notification] what the incoming-message intake needs beyond title and text. Shared by both
 * sources, so they describe the same message identically. Bounded; nothing is logged or stored.
 *
 * NOT verified on a device: which of these extras WhatsApp, Gmail and other apps really fill in their current
 * versions (messaging style, group flag, channel ids).
 */
internal object NotificationDetailsReader {
    private const val MAX_MESSAGES = 8
    // Literal keys: the constants for some of them are newer than minSdk.
    private const val KEY_GROUP = "android.isGroupConversation"
    private const val KEY_MESSAGES = "android.messages"
    private const val KEY_SELF = "android.selfDisplayName"
    private const val KEY_SELF_PERSON = "android.messagingUser"

    fun title(notification: Notification): String = bounded(notification.extras?.getCharSequence(Notification.EXTRA_TITLE)
        ?: notification.extras?.getCharSequence(Notification.EXTRA_TITLE_BIG))

    fun text(notification: Notification): String = bounded(
        notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))

    fun details(notification: Notification, source: NotificationSource, silent: Boolean?): NotificationDetails {
        val extras = notification.extras ?: Bundle.EMPTY
        val messages = try {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(KEY_MESSAGES)?.mapNotNull { it as? Bundle }?.takeLast(MAX_MESSAGES)?.map { message ->
                NotificationMessage(bounded(message.getCharSequence("sender") ?: person(message, "sender_person")),
                    bounded(message.getCharSequence("text")), message.getLong("time"))
            }.orEmpty()
        } catch (_: RuntimeException) {
            emptyList() // An unreadable history falls back to title and text.
        }
        return NotificationDetails(
            source = source,
            whenMs = notification.`when`,
            conversationTitle = bounded(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)),
            isGroupConversation = extras.getBoolean(KEY_GROUP, false),
            messages = messages,
            selfName = bounded(extras.getCharSequence(KEY_SELF) ?: person(extras, KEY_SELF_PERSON)),
            subText = bounded(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
            bigText = bounded(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
            channelId = notification.channelId.orEmpty().take(120),
            shortcutId = notification.shortcutId.orEmpty().take(120),
            silent = silent,
        )
    }

    private fun person(bundle: Bundle, key: String): CharSequence? {
        if (Build.VERSION.SDK_INT < 28) return null
        @Suppress("DEPRECATION")
        return (bundle.getParcelable<android.os.Parcelable>(key) as? android.app.Person)?.name
    }

    /** Keeps no more than the rules will ever inspect, so large payloads are not held in memory. */
    fun bounded(value: CharSequence?): String {
        if (value == null) return ""
        return value.subSequence(0, minOf(value.length, UntrustedNotificationText.MAX_RAW)).toString()
    }
}

/**
 * The incoming-message signal of the normal build: notification events of the accessibility service the owner
 * enabled himself ([at.heinz.deviceai.ContinuumScreenObservationService]). No notification-listener permission.
 *
 * The service asks Android for `TYPE_NOTIFICATION_STATE_CHANGED` only while the owner's switch „Eingehende
 * Nachrichten melden“ is on ([apply]); the static service configuration stays as it was. An event is mapped only
 * for an app on the owner's list; for every other app not even the extras are read. While a build's notification
 * listener is connected it is the richer source and this one stays silent – one bus, one message.
 *
 * What Android delivers here (from the platform sources as known; NOT verified on the Fold7): an event for a newly
 * posted notification above minimum importance that „do not disturb“ does not suppress; for an update only when it
 * alerts again. On a locked device a notification that is not public arrives as its public version – usually
 * nothing – so sender and text are then unknown ([NotificationDetails.contentHidden]). No event tells when a
 * notification is removed.
 */
internal object AccessibilityNotificationSource {
    @Volatile
    private var service: AccessibilityService? = null

    /** true while the owner's accessibility service is connected in this process. */
    val connected: Boolean get() = service != null

    fun connected(service: AccessibilityService) {
        this.service = service
        IncomingMessages.start(service)
        apply(service)
    }

    fun disconnected(service: AccessibilityService) {
        if (this.service === service) this.service = null
    }

    /** The owner changed the switch: start or stop receiving notification events right away. */
    fun refresh() {
        service?.let(::apply)
    }

    /** true when the connected service currently asks Android for notification events. */
    fun listening(): Boolean = try {
        (service?.serviceInfo?.eventTypes ?: 0) and AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED != 0
    } catch (_: RuntimeException) {
        false
    }

    private fun apply(service: AccessibilityService) {
        try {
            val info = service.serviceInfo ?: return
            val wanted = IncomingMessagePreferences.load(service).enabled
            val has = info.eventTypes and AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED != 0
            if (wanted == has) return
            info.eventTypes = if (wanted) info.eventTypes or AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                else info.eventTypes and AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED.inv()
            service.serviceInfo = info
        } catch (_: RuntimeException) {
            // The screen observation of the service must not depend on this optional signal.
        }
    }

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        try {
            val settings = IncomingMessagePreferences.load(service)
            if (!settings.enabled || ContinuumNotificationBus.listenerConnected) return
            val source = event.packageName?.toString() ?: return
            if (source == service.packageName || source !in settings.apps) return
            ContinuumNotificationBus.publish(map(service, source, event) ?: return)
        } catch (_: RuntimeException) {
            // A malformed third-party notification must not take the service down. Not logged.
        }
    }

    internal fun map(context: Context, source: String, event: AccessibilityEvent): IncomingNotification? {
        val label = IncomingMessages.label(context, source)
        val now = System.currentTimeMillis()
        fun hidden() = IncomingNotification("a11y|$source|hidden", source, label, "", "", null, isOngoing = false,
            isGroupSummary = false, postedAtMs = now, isOwnPackage = false,
            details = NotificationDetails(NotificationSource.ACCESSIBILITY, contentHidden = true))
        val data = event.parcelableData
        if (data == null) {
            // A toast uses the same event type; only a notification that came without its content counts.
            return if (event.className?.toString() == Notification::class.java.name) hidden() else null
        }
        val notification = data as? Notification ?: return null
        val title = NotificationDetailsReader.title(notification)
        val text = NotificationDetailsReader.text(notification)
        val flags = notification.flags
        val channel = notification.channelId.orEmpty()
        val details = NotificationDetailsReader.details(notification, NotificationSource.ACCESSIBILITY,
            // This source cannot read the channel's importance; a channel that calls itself silent is taken at its word.
            silent = if (channel.contains("silent", ignoreCase = true)) true else null)
        val ongoing = flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        val summary = flags and Notification.FLAG_GROUP_SUMMARY != 0
        // A public version made for the lock screen names only the app („WhatsApp“ / „1 neue Nachricht“).
        if (!ongoing && !summary && details.messages.isEmpty() &&
            (title.isBlank() || title.trim().equals(label, ignoreCase = true))) return hidden()
        val thread = UntrustedNotificationText.contentHash(details.shortcutId, details.conversationTitle.ifEmpty { title })
        return IncomingNotification("a11y|$source|" + java.lang.Long.toHexString(thread), source, label, title, text,
            notification.category, ongoing, summary, now, isOwnPackage = false, details = details)
    }
}

/**
 * The process-wide home of the incoming-message feature: one [IncomingMessageInbox] fed by the one notification bus,
 * and the hand-over to whoever can talk to the owner right now – the conversation in the open app
 * ([IncomingMessageDialogue]) or, while the app is not in front, Continuum's own card and an optional short spoken
 * sentence. It keeps the inbox alive across a recreated screen; everything is memory only.
 *
 * Android limits, respected and not worked around: from the background no screen is opened and no microphone is
 * used. The conversation continues when the owner opens the app or taps „Vorlesen“ on the card.
 */
internal object IncomingMessages : ContinuumNotificationBus.Subscriber, IncomingInboxListener {
    const val EXTRA_ITEM = "at.heinz.deviceai.incoming.ITEM"
    const val EXTRA_READ = "at.heinz.deviceai.incoming.READ"
    const val ACTION_LATER = "at.heinz.deviceai.incoming.LATER"

    private val handler = Handler(Looper.getMainLooper())
    private val labels = HashMap<String, String>()

    @Volatile
    private var app: Context? = null
    private var started = false
    private var contacts: IncomingContactNames? = null
    private var notifier: IncomingMessageNotifier? = null
    private var voice: BackgroundGermanVoice? = null

    /** Host tests replace the address book; null = the device's contacts. */
    @Volatile
    internal var contactsOverride: ((String) -> Boolean)? = null

    /** The owner's name and tone for the spoken background sentence; the open app keeps it current. */
    @Volatile
    var profile: IncomingOwnerProfile = IncomingOwnerProfile.DEFAULT

    /** The conversation of the resumed screen; null while the app is not in front. Main thread only. */
    var foreground: IncomingMessageDialogue? = null

    val inbox: IncomingMessageInbox = IncomingMessageInbox(
        clock = { SystemClock.elapsedRealtime() },
        scheduler = { delayMs, action ->
            val task = Runnable(action)
            handler.postDelayed(task, delayMs)
            val cancel: () -> Unit = { handler.removeCallbacks(task) }
            cancel
        },
        settings = { app?.let(IncomingMessagePreferences::load) ?: IncomingMessageSettings() },
        inContacts = { name -> contactsOverride?.invoke(name) ?: (contacts?.known(name) == true) },
        firstId = System.currentTimeMillis(),
    ).also { it.listener = this }

    /** Idempotent. Called by the screen, the accessibility service and (in a build that has it) the listener. */
    @Synchronized
    fun start(context: Context) {
        val application = context.applicationContext ?: context
        val sameApplication = app === application
        app = application
        ContinuumNotificationBus.subscribe(this)
        if (started && sameApplication) return
        started = true
        contacts = IncomingContactNames(application).also { it.refresh() }
        // The list lives in memory only: a card that survived an ended process has nothing behind it any more.
        notifier = IncomingMessageNotifier(application).also { it.cancelAll() }
    }

    override fun onNotificationPosted(notification: IncomingNotification) = onMain { inbox.onNotification(notification) }

    override fun onNotificationRemoved(key: String) = onMain { inbox.onRemoved(key) }

    override fun onReady(item: IncomingItem) {
        val dialogue = foreground
        if (dialogue != null) dialogue.ready(item) else background(item)
    }

    override fun onGone(item: IncomingItem) {
        foreground?.gone(item)
        notifier?.cancel(item)
    }

    /** The owner changed a setting: (un)subscribe the accessibility events and forget everything when switched off. */
    fun settingsChanged(context: Context) {
        start(context)
        AccessibilityNotificationSource.refresh()
        if (!IncomingMessagePreferences.load(context).enabled) {
            inbox.clear()
            notifier?.cancelAll()
        } else contacts?.refresh()
    }

    /** „Offene Nachrichten löschen“ from the settings. */
    fun clear() {
        inbox.clear()
        notifier?.cancelAll()
    }

    /** „Später“ on the card. */
    fun later(itemId: Long) = onMain {
        val dialogue = foreground
        if (dialogue != null) dialogue.action(itemId, read = false)
        else inbox.item(itemId)?.let { item ->
            if (item.state != IncomingState.ACTIVE) inbox.mark(item, IncomingState.OPEN)
            notifier?.cancel(item)
        }
    }

    fun card(item: IncomingItem, quiet: Boolean = false) {
        notifier?.post(item, quiet)
    }

    fun clearCard(item: IncomingItem) {
        notifier?.cancel(item)
    }

    fun notificationsAllowed(): Boolean = notifier?.allowed() == true

    /** true = the short background sentence is on for [packageName]; the drive announcer then leaves it to this feature. */
    fun speaksInBackground(context: Context, packageName: String): Boolean =
        IncomingMessagePreferences.load(context).let { it.enabled && it.backgroundVoice && packageName in it.apps }

    fun minuteOfDay(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }

    /** App labels are not notification content; a tiny cache avoids a PackageManager call per message. */
    fun label(context: Context, packageName: String): String = synchronized(labels) {
        labels[packageName]?.let { return it }
        val label = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "" // The wording then says „Eine App“; no name is guessed from the package id.
        }
        if (label.isNotEmpty() && labels.size < 64) labels[packageName] = label
        label
    }

    /** The app is not in front: no question, no microphone – the card, and one sentence if the owner switched it on. */
    private fun background(item: IncomingItem) {
        val context = app ?: return
        val settings = IncomingMessagePreferences.load(context)
        val quiet = when {
            SystemClock.elapsedRealtime() < inbox.doNotDisturbUntil -> IncomingHold.DO_NOT_DISTURB
            settings.quietAt(minuteOfDay()) -> IncomingHold.QUIET_HOURS
            else -> null
        }
        if (item.held == null) item.held = quiet ?: IncomingHold.AWAY
        if (settings.backgroundCard) notifier?.post(item, quiet != null)
        if (settings.backgroundVoice && quiet == null && soundWanted(context)) {
            val speaker = voice ?: BackgroundGermanVoice(context, AudioAttributes.USAGE_ASSISTANT, "continuum-incoming-").also { voice = it }
            speaker.say(IncomingMessageWording.background(item, profile))
        }
    }

    /** Not while the phone is set to silent or vibrate, and not during a call. */
    private fun soundWanted(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && audio.mode == AudioManager.MODE_NORMAL
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    internal fun resetForTest() {
        handler.removeCallbacksAndMessages(null)
        inbox.clear()
        inbox.doNotDisturbUntil = 0L
        foreground = null
        contactsOverride = null
        profile = IncomingOwnerProfile.DEFAULT
        notifier?.cancelAll()
        voice?.release()
        voice = null
    }
}

/**
 * Display names of the owner's contacts, read on the device when READ_CONTACTS is already granted (this feature never
 * asks for it). Only used to answer „is this sender one of my contacts?“; names are held in memory, lower-cased.
 */
internal class IncomingContactNames(private val context: Context) {
    private val io = Executors.newSingleThreadExecutor { task -> Thread(task, "incoming-contacts").apply { isDaemon = true } }
    @Volatile private var names: Set<String> = emptySet()
    @Volatile private var loadedAt = 0L

    fun known(name: String): Boolean {
        if (SystemClock.elapsedRealtime() - loadedAt > REFRESH_MS) refresh()
        return normal(name).let { it.isNotEmpty() && it in names }
    }

    fun refresh() {
        loadedAt = SystemClock.elapsedRealtime()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        io.execute {
            runCatching {
                val found = HashSet<String>()
                context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && found.size < MAX_NAMES) normal(cursor.getString(0).orEmpty()).takeIf { it.isNotEmpty() }?.let(found::add)
                }
                names = found
            }
        }
    }

    private fun normal(name: String): String = name.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val REFRESH_MS = 10 * 60_000L
        const val MAX_NAMES = 5_000
    }
}

/**
 * Continuum's own notification for a waiting message: „Vorlesen“ opens the app and reads it, „Später“ keeps it in
 * the open list. It names sender and app only – never the message text – and shows no sender on a locked screen.
 * It makes no sound of its own (the message's app already did).
 */
internal class IncomingMessageNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun allowed(): Boolean = manager?.areNotificationsEnabled() == true

    fun post(item: IncomingItem, quiet: Boolean) {
        val manager = manager ?: return
        try {
            channels(manager)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(context, id(item) * 2, openIntent(context, item.id, read = !item.hidden), flags)
            val later = PendingIntent.getBroadcast(context, id(item) * 2 + 1,
                Intent(context, IncomingMessageActionReceiver::class.java).setAction(IncomingMessages.ACTION_LATER)
                    .putExtra(IncomingMessages.EXTRA_ITEM, item.id), flags)
            val channel = if (quiet) CHANNEL_QUIET else CHANNEL
            val public = Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_device_ai)
                .setContentTitle("Continuum").setContentText("Eine Nachricht wartet.").build()
            val builder = Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_device_ai)
                .setContentTitle("Continuum · Nachricht").setContentText(IncomingMessageWording.cardLine(item))
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(open)
            if (!item.hidden) builder.addAction(Notification.Action.Builder(null, "Vorlesen", open).build())
            builder.addAction(Notification.Action.Builder(null, "Später", later).build())
            manager.notify(TAG, id(item), builder.build())
        } catch (_: RuntimeException) {
            // Without the notification permission the card is simply not shown; the open list still has the message.
        }
    }

    fun cancel(item: IncomingItem) {
        runCatching { manager?.cancel(TAG, id(item)) }
    }

    fun cancelAll() {
        runCatching { manager?.activeNotifications?.filter { it.tag == TAG }?.forEach { manager.cancel(TAG, it.id) } }
    }

    private fun channels(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Eingehende Nachrichten", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Continuum meldet eine neue Nachricht der von dir gewählten Apps."
                setSound(null, null)
                enableVibration(false)
            })
        if (manager.getNotificationChannel(CHANNEL_QUIET) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "Eingehende Nachrichten (Ruhezeit)", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val TAG = "continuum-incoming"
        const val CHANNEL = "continuum-incoming-messages"
        const val CHANNEL_QUIET = "continuum-incoming-messages-quiet"

        fun id(item: IncomingItem): Int = 4_100 + (item.id % 100_000L).toInt()

        /** Brings the existing screen to the front (or starts it) with the wish to read [itemId]. */
        fun openIntent(context: Context, itemId: Long, read: Boolean): Intent = Intent(context, AgentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(IncomingMessages.EXTRA_ITEM, itemId).putExtra(IncomingMessages.EXTRA_READ, read)
    }
}

/** „Später“ on Continuum's own card. Not exported; it starts nothing and only marks the message as open. */
class IncomingMessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != IncomingMessages.ACTION_LATER) return
        val id = intent.getLongExtra(IncomingMessages.EXTRA_ITEM, -1L)
        if (id >= 0) IncomingMessages.later(id)
    }
}
