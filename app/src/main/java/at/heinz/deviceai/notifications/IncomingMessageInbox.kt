package at.heinz.deviceai.notifications

import java.util.Locale

/** Monotonic milliseconds; a fake in host tests. */
internal fun interface IncomingClock { fun nowMs(): Long }

/** Runs [action] on the caller's thread after [delayMs]; the returned function cancels it. */
internal fun interface IncomingScheduler { fun after(delayMs: Long, action: () -> Unit): () -> Unit }

/** One message or mail of an item. UNTRUSTED content, held in memory only. */
internal class IncomingPart(val sender: String, val subject: String, val text: String, val atMs: Long)

internal enum class IncomingState {
    /** The burst is still open (more messages of the same conversation may follow). */
    COLLECTING,
    /** Ready, not offered yet (the owner is busy, it is quiet time, or the app is not in front). */
    WAITING,
    /** „Soll ich sie dir vorlesen?“ is open right now. */
    OFFERED,
    /** „Später“ or no answer: it stays in the list „Offene Nachrichten“. */
    OPEN,
    /** Being read or answered; a new message of the conversation starts a new item. */
    ACTIVE,
    DONE,
}

/** What kept a ready message from being offered at once. */
internal enum class IncomingHold { BUSY, AWAY, QUIET_HOURS, DO_NOT_DISTURB }

/**
 * The messages of one conversation that are offered to the owner as one announcement. Lives in memory only, is
 * bounded ([IncomingMessageInbox.MAX_PARTS]) and expires ([IncomingMessageInbox.RETENTION_MS]); `toString()` is redacted.
 */
internal class IncomingItem(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val kind: IncomingKind,
    val conversationKey: String,
    /** Sender as the notification named him; empty when Android showed no content. */
    val sender: String,
    /** Group name; empty for a direct message or a mail. */
    val conversation: String,
    val isGroup: Boolean,
    val decision: ImportanceDecision,
    /** Android handed out no sender and no text (locked device, or only the app's summary). Nothing can be read or answered. */
    val hidden: Boolean,
    val firstAtMs: Long,
) {
    val parts = ArrayList<IncomingPart>()
    /** All messages of the burst, also those beyond [IncomingMessageInbox.MAX_PARTS]. */
    var count = 0
    var state = IncomingState.COLLECTING
    var lastAtMs = firstAtMs
    var readyAtMs = 0L
    /** Why it could not be offered at once; decides the wording of the later offer. */
    var held: IncomingHold? = null
    /** Read to the owner already; only the answer is open („später“ after reading). */
    var read = false
    internal var cancelTimer: (() -> Unit)? = null

    val unclear: Boolean get() = decision.verdict == ImportanceVerdict.UNCLEAR
    /** The sender is a phone number: there is no name to say. */
    val unknownNumber: Boolean get() = IncomingNotificationParser.PHONE.matches(sender)

    override fun toString(): String = "IncomingItem(id=$id, package=$packageName, state=$state, content=redacted)"
}

/** Why a posted notification never became a message for the owner. */
internal enum class IncomingIgnore {
    DISABLED, APP_NOT_CHOSEN, OWN_PACKAGE, ONGOING, GROUP_SUMMARY, CATEGORY, EMPTY, OWN_MESSAGE, DUPLICATE, SILENT_NOTIFICATION,
}

internal sealed interface IncomingIntake {
    /** Not a new incoming message at all (or the feature / the app is off). */
    data class Ignored(val why: IncomingIgnore) : IncomingIntake
    /** A message, but the rules say it is not reported; [decision] says why. */
    data class NotReported(val decision: ImportanceDecision) : IncomingIntake
    /** Part of [item]; [startedItem] is false when it joined a burst or an item the owner already knows about. */
    data class Collected(val item: IncomingItem, val startedItem: Boolean) : IncomingIntake
}

/** One line of „Warum hast du das (nicht) gemeldet?“. Names the app and the sender, never the text. */
internal class IncomingDecisionRecord(val atMs: Long, val appLabel: String, val who: String, val reported: Boolean,
                                      val explanation: String)

internal interface IncomingInboxListener {
    /** The burst of [item] is closed: it may be offered now. */
    fun onReady(item: IncomingItem)
    /** [item] was dropped without the owner's doing (expired, read elsewhere, list cleared, feature off). */
    fun onGone(item: IncomingItem) {}
}

/** Maps a posted notification to the messages it carries. Pure Kotlin; formats of real apps are NOT verified here. */
internal object IncomingNotificationParser {
    val PHONE = Regex("""^\+?[\d\s()/.\-]{6,24}$""")
    private val COUNT_SUFFIX = Regex("""(?i)\s*\(\d+\s+(?:neue\s+|new\s+)?(?:nachricht(?:en)?|messages?)\)\s*$""")
    private val GROUP_LINE = Regex("""^([^:]{1,60}):\s+(.+)$""")

    class Message(val sender: String, val subject: String, val text: String, val timeMs: Long)

    class Parsed(val conversationKey: String, val sender: String, val conversation: String, val isGroup: Boolean,
                 val messages: List<Message>, val hidden: Boolean = false, val onlyOwn: Boolean = false)

    fun parse(notification: IncomingNotification, kind: IncomingKind): Parsed? {
        val details = notification.details
        val app = notification.packageName
        if (details?.contentHidden == true) return Parsed("$app|hidden", "", "", false, emptyList(), hidden = true)
        val title = COUNT_SUFFIX.replace(UntrustedNotificationText.clean(notification.title), "")
        val text = UntrustedNotificationText.clean(notification.text)
        val time = details?.whenMs ?: 0L
        if (kind == IncomingKind.MAIL) {
            if (title.isEmpty() && text.isEmpty()) return null
            val big = UntrustedNotificationText.clean(details?.bigText)
            // Gmail shows the subject as text and „subject, then the first lines“ as the expanded text.
            val body = if (text.isNotEmpty() && big.startsWith(text)) big.removePrefix(text).trim() else big
            return Parsed("$app|" + title.lowercase(Locale.ROOT), title, "", false, listOf(Message(title, text, body, time)))
        }
        var group = details?.isGroupConversation == true || details?.channelId?.contains("group", ignoreCase = true) == true
        var conversation = COUNT_SUFFIX.replace(UntrustedNotificationText.clean(details?.conversationTitle), "")
        var sender = title
        // Older WhatsApp wording for a group message: „Lisa @ Familie“. Not verified on a device.
        if (app.startsWith("com.whatsapp") && " @ " in title) {
            sender = title.substringBefore(" @ ").trim(); conversation = title.substringAfter(" @ ").trim(); group = true
        }
        if (group && conversation.isEmpty()) conversation = title
        if (!group) conversation = ""
        val self = UntrustedNotificationText.clean(details?.selfName)
        val history = details?.messages.orEmpty().map {
            Message(UntrustedNotificationText.clean(it.sender), "", UntrustedNotificationText.clean(it.text), it.timeMs)
        }.filter { it.text.isNotEmpty() }
        val named = history.any { it.sender.isNotEmpty() }
        // A message without a sender next to named ones, or one under the owner's own name, is his own reply.
        val others = history.filterNot { named && (it.sender.isEmpty() || (self.isNotEmpty() && it.sender.equals(self, true))) }
        val messages = when {
            history.isNotEmpty() -> others.map { Message(it.sender.ifEmpty { sender }, "", it.text, it.timeMs) }
            title.isEmpty() && text.isEmpty() -> return null
            else -> {
                val line = if (group && sender == conversation) GROUP_LINE.matchEntire(text) else null
                if (line != null) listOf(Message(line.groupValues[1].trim(), "", line.groupValues[2], time))
                else listOf(Message(sender, "", text, time))
            }
        }
        // With a messaging style the newest foreign message names its sender; Android's own title for it may read
        // „Gruppe: Absender“.
        if (group || history.isNotEmpty()) messages.lastOrNull()?.sender?.takeIf { it.isNotEmpty() }?.let { sender = it }
        val thread = details?.shortcutId?.takeIf { it.isNotBlank() } ?: (if (group) conversation else sender).lowercase(Locale.ROOT)
        return Parsed("$app|$thread", sender, conversation, group, messages, onlyOwn = history.isNotEmpty() && others.isEmpty())
    }
}

/**
 * The intake and the small list „Offene Nachrichten“ of the incoming-message feature. Pure Kotlin, one thread (the
 * caller's), no Android.
 *
 * It takes every notification the bus carries – from the notification listener of a build that has one, or from the
 * accessibility service of the normal build – and decides: is this a new incoming message of an app the owner chose,
 * is it important ([IncomingMessageClassifier]), and to which conversation's burst does it belong. Both sources are
 * de-duplicated by what the message is (conversation, sender, text, its own timestamp), not by a notification key,
 * so the same message is counted once whichever source saw it and however often the app re-posts its notification.
 *
 * Privacy: everything is held in memory only, bounded and expiring – at most [MAX_ITEMS] items with [MAX_PARTS]
 * messages each for [RETENTION_MS]; the duplicate memory keeps hashes, never text. Nothing is logged or written.
 */
internal class IncomingMessageInbox(
    private val clock: IncomingClock,
    private val scheduler: IncomingScheduler,
    private val settings: () -> IncomingMessageSettings,
    /** true = this display name is in the owner's contacts. Asked for the sender only, on the device. */
    private val inContacts: (String) -> Boolean = { false },
    /** First item id. The app starts from the wall clock, so a card left over from an ended process matches nothing. */
    firstId: Long = 1L,
) {
    var listener: IncomingInboxListener? = null

    /** „Stör mich jetzt nicht“ holds until this monotonic time; kept here so it outlives a recreated screen. */
    var doNotDisturbUntil = 0L

    private val items = ArrayList<IncomingItem>()
    private val seen = LinkedHashMap<String, LinkedHashMap<Long, Long>>()
    private val keys = LinkedHashMap<String, String>()
    private val decisions = ArrayDeque<IncomingDecisionRecord>()
    private val lastMessageEventAt = HashMap<String, Long>()
    private val pendingSummaries = HashSet<String>()
    private val hintClosedAt = HashMap<String, Long>()
    private var nextId = firstId

    /** true = the feature is on and [packageName] is one of the owner's apps: this inbox, not the plain announcer, handles it. */
    fun claims(packageName: String): Boolean = settings().let { it.enabled && packageName in it.apps }

    fun onNotification(notification: IncomingNotification): IncomingIntake {
        val current = settings()
        if (!current.enabled) return IncomingIntake.Ignored(IncomingIgnore.DISABLED)
        if (notification.isOwnPackage) return IncomingIntake.Ignored(IncomingIgnore.OWN_PACKAGE)
        if (notification.packageName !in current.apps) return IncomingIntake.Ignored(IncomingIgnore.APP_NOT_CHOSEN)
        // Calls, playback, uploads, backups and the app's own summary are never an incoming message.
        if (notification.isOngoing) return IncomingIntake.Ignored(IncomingIgnore.ONGOING)
        val category = notification.category?.trim()?.lowercase(Locale.ROOT)
        if (category != null && category in NOT_A_MESSAGE) return IncomingIntake.Ignored(IncomingIgnore.CATEGORY)
        val now = clock.nowMs()
        // The app's own summary carries no message of its own – unless it repeats the chat's history, which is then
        // taken like any other notification (the same message is still counted once).
        if (notification.isGroupSummary && notification.details?.messages.isNullOrEmpty()) {
            if (notification.details?.source == NotificationSource.ACCESSIBILITY) summaryOnly(notification, now)
            return IncomingIntake.Ignored(IncomingIgnore.GROUP_SUMMARY)
        }
        if (!notification.isGroupSummary) lastMessageEventAt[notification.packageName] = now
        val kind = IncomingMessageClassifier.kindOf(notification.packageName, category)
        val parsed = IncomingNotificationParser.parse(notification, kind) ?: return IncomingIntake.Ignored(IncomingIgnore.EMPTY)
        prune(now)
        if (notification.details?.source == NotificationSource.LISTENER) rememberKey(notification.key, parsed.conversationKey)
        val label = UntrustedNotificationText.label(notification.appLabel)
        // Remembered before any rule: an update of the same notification is never looked at twice.
        val fresh = parsed.messages.filter { remember(parsed.conversationKey, it, now) }
        if (!parsed.hidden && fresh.isEmpty())
            return IncomingIntake.Ignored(if (parsed.onlyOwn) IncomingIgnore.OWN_MESSAGE else IncomingIgnore.DUPLICATE)
        // The sender is spoken in the question; a title that itself carries a code is not a name.
        val who = if (parsed.hidden || UntrustedNotificationText.containsSecurityCode(parsed.sender, "")) "" else
            UntrustedNotificationText.bound(UntrustedNotificationText.replaceLinks(parsed.sender), UntrustedNotificationText.MAX_TITLE)
        if (notification.details?.silent == true && !current.silentNotifications) {
            record(now, label, who, false, "Die Benachrichtigung kam stumm, und stumme sind ausgeschaltet.")
            return IncomingIntake.Ignored(IncomingIgnore.SILENT_NOTIFICATION)
        }
        val decision = if (parsed.hidden) HIDDEN else fresh.last().let { newest ->
            IncomingMessageClassifier.classify(ImportanceInput(kind, if (parsed.isGroup) newest.sender else parsed.sender,
                newest.subject, newest.text, parsed.conversation, inContacts(parsed.sender), parsed.isGroup), current.rules)
        }
        val explanation = if (parsed.hidden) HIDDEN_EXPLANATION.format(label) else decision.explanation
        if (decision.verdict == ImportanceVerdict.SILENT ||
            (decision.verdict == ImportanceVerdict.UNCLEAR && current.unclear == UnclearHandling.SILENT)) {
            record(now, label, who, false, if (decision.verdict == ImportanceVerdict.UNCLEAR)
                "$explanation Unklare Nachrichten meldest du nicht." else explanation)
            return IncomingIntake.NotReported(decision)
        }
        val joined = items.lastOrNull { it.conversationKey == parsed.conversationKey && it.state in JOINABLE }
        val item = joined ?: IncomingItem(nextId++, notification.packageName, label, kind, parsed.conversationKey, who,
            UntrustedNotificationText.bound(parsed.conversation, UntrustedNotificationText.MAX_TITLE), parsed.isGroup,
            decision, parsed.hidden, now).also { created ->
            items += created
            while (items.size > MAX_ITEMS) drop(items.first())
            record(now, label, who, true, explanation)
        }
        if (parsed.hidden) item.count++ else fresh.forEach { message ->
            item.count++
            item.parts += IncomingPart(UntrustedNotificationText.bound(message.sender, UntrustedNotificationText.MAX_TITLE),
                UntrustedNotificationText.bound(message.subject, MAX_SUBJECT), UntrustedNotificationText.bound(message.text, MAX_READ), now)
            while (item.parts.size > MAX_PARTS) item.parts.removeAt(0)
        }
        item.lastAtMs = now
        if (item.state == IncomingState.COLLECTING) schedule(item, now)
        return IncomingIntake.Collected(item, joined == null)
    }

    /**
     * The notification left the status bar (only the listener source reports this): the owner read or dismissed it
     * in the app. An item he has not been told about yet is dropped; one he knows about stays in his list.
     */
    fun onRemoved(key: String) {
        val conversation = keys.remove(key.take(MAX_KEY)) ?: return
        items.filter { it.conversationKey == conversation && (it.state == IncomingState.COLLECTING || it.state == IncomingState.WAITING) }
            .forEach { drop(it) }
    }

    /** Everything the owner has not dealt with, oldest first: waiting, offered and „später“. */
    fun open(): List<IncomingItem> {
        prune(clock.nowMs())
        return items.filter { it.state == IncomingState.WAITING || it.state == IncomingState.OFFERED || it.state == IncomingState.OPEN }
    }

    fun waiting(): List<IncomingItem> = open().filter { it.state == IncomingState.WAITING }

    fun item(id: Long): IncomingItem? = items.firstOrNull { it.id == id && it.state != IncomingState.DONE }

    fun mark(item: IncomingItem, state: IncomingState) {
        if (item.state == IncomingState.DONE) return
        if (state == IncomingState.DONE) { close(item); return }
        item.cancelTimer?.invoke(); item.cancelTimer = null
        item.state = state
    }

    /**
     * A new Activity found what an earlier one left in the middle of a conversation: a question nobody can answer any
     * more returns to the list, a half-finished reading or answer ends (nothing was sent).
     */
    fun reclaim() {
        items.toList().forEach { item ->
            when (item.state) {
                IncomingState.OFFERED -> item.state = IncomingState.OPEN
                IncomingState.ACTIVE -> close(item)
                else -> Unit
            }
        }
    }

    /** The owner dealt with [item] (read, answered, declined): it leaves the list and its content is released. */
    fun close(item: IncomingItem) {
        if (item.hidden && item.state != IncomingState.DONE) hintClosedAt[item.packageName] = clock.nowMs()
        item.cancelTimer?.invoke(); item.cancelTimer = null
        item.state = IncomingState.DONE
        item.parts.clear()
        items.remove(item)
    }

    /** „Offene Nachrichten löschen“ / the feature was switched off: content, duplicate memory and reasons are forgotten. */
    fun clear() {
        items.toList().forEach { drop(it) }
        seen.clear(); keys.clear(); decisions.clear()
        lastMessageEventAt.clear(); pendingSummaries.clear(); hintClosedAt.clear()
    }

    /** The newest decision, optionally only a reported or only an unreported one, optionally about [about]. */
    fun lastDecision(reported: Boolean? = null, about: String? = null): IncomingDecisionRecord? {
        prune(clock.nowMs())
        val wanted = about?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        return decisions.lastOrNull { record ->
            (reported == null || record.reported == reported) &&
                (wanted == null || wanted in record.who.lowercase(Locale.ROOT) || wanted in record.appLabel.lowercase(Locale.ROOT))
        }
    }

    internal fun rememberedHashes(): Int = seen.values.sumOf { it.size }

    /**
     * The accessibility source saw only the app's summary. Android hands a service at most one notification event per
     * short interval, so the message's own event can be lost when the app posts it together with its summary. When
     * no message event of that app comes within [SUMMARY_GRACE_MS], the summary is taken as a hint without content.
     */
    private fun summaryOnly(notification: IncomingNotification, now: Long) {
        val app = notification.packageName
        if (now - (lastMessageEventAt[app] ?: NEVER) <= SUMMARY_GRACE_MS || !pendingSummaries.add(app)) return
        scheduler.after(SUMMARY_GRACE_MS) {
            pendingSummaries.remove(app)
            if (clock.nowMs() - (lastMessageEventAt[app] ?: NEVER) > 2 * SUMMARY_GRACE_MS) onNotification(notification.copy(
                title = "", text = "", isGroupSummary = false,
                details = NotificationDetails(NotificationSource.ACCESSIBILITY, contentHidden = true)))
        }
    }

    private fun schedule(item: IncomingItem, now: Long) {
        item.cancelTimer?.invoke()
        // A hint without content says little: at most one per app within the cool-down, the rest only counts up.
        val due = if (item.hidden) maxOf(now + DEBOUNCE_MS, (hintClosedAt[item.packageName] ?: NEVER) + HINT_COOLDOWN_MS)
            else minOf(now + DEBOUNCE_MS, item.firstAtMs + MAX_COLLECT_MS)
        item.cancelTimer = scheduler.after((due - now).coerceAtLeast(0L)) {
            item.cancelTimer = null
            if (item.state == IncomingState.COLLECTING) {
                item.state = IncomingState.WAITING
                item.readyAtMs = clock.nowMs()
                listener?.onReady(item)
            }
        }
    }

    private fun drop(item: IncomingItem) {
        if (item.state == IncomingState.DONE) return
        close(item)
        listener?.onGone(item)
    }

    private fun prune(now: Long) {
        items.filter { now - it.firstAtMs > RETENTION_MS && it.state != IncomingState.ACTIVE }.forEach { drop(it) }
        while (decisions.isNotEmpty() && now - decisions.first().atMs > RETENTION_MS) decisions.removeFirst()
        val expired = seen.entries.filter { entry -> entry.value.values.all { now - it > RETENTION_MS } }.map { it.key }
        expired.forEach { seen.remove(it) }
    }

    /** true = this message of [conversation] was not seen before. Only a 64-bit hash is kept. */
    private fun remember(conversation: String, message: IncomingNotificationParser.Message, now: Long): Boolean {
        val hash = UntrustedNotificationText.contentHash(message.sender + "\u0000" + message.subject,
            message.text + "\u0000" + message.timeMs)
        val known = seen.remove(conversation) ?: LinkedHashMap()
        seen[conversation] = known
        while (seen.size > MAX_CONVERSATIONS) seen.remove(seen.keys.first())
        if (known.containsKey(hash)) return false
        known[hash] = now
        while (known.size > MAX_SEEN_PER_CONVERSATION) known.remove(known.keys.first())
        return true
    }

    private fun rememberKey(key: String, conversation: String) {
        keys.remove(key.take(MAX_KEY)); keys[key.take(MAX_KEY)] = conversation
        while (keys.size > MAX_CONVERSATIONS) keys.remove(keys.keys.first())
    }

    private fun record(now: Long, app: String, who: String, reported: Boolean, explanation: String) {
        decisions.addLast(IncomingDecisionRecord(now, app, who, reported, explanation))
        while (decisions.size > MAX_DECISIONS) decisions.removeFirst()
    }

    companion object {
        /** A burst closes this long after its last message … */
        const val DEBOUNCE_MS = 4_000L
        /** … and never later than this after its first one. */
        const val MAX_COLLECT_MS = 15_000L
        /** How long a summary waits for the message event of its app before it counts as a hint without content. */
        const val SUMMARY_GRACE_MS = 1_500L
        /** Hints without content („auf WhatsApp ist etwas eingegangen“) of one app are at least this far apart. */
        const val HINT_COOLDOWN_MS = 10 * 60_000L
        private const val NEVER = Long.MIN_VALUE / 4
        const val RETENTION_MS = 12L * 60L * 60L * 1_000L
        const val MAX_ITEMS = 20
        const val MAX_PARTS = 5
        const val MAX_READ = 600
        const val MAX_SUBJECT = 160
        const val MAX_DECISIONS = 24
        const val MAX_CONVERSATIONS = 64
        const val MAX_SEEN_PER_CONVERSATION = 64
        private const val MAX_KEY = 256

        private val JOINABLE = setOf(IncomingState.COLLECTING, IncomingState.WAITING, IncomingState.OFFERED, IncomingState.OPEN)

        /** android.app.Notification categories that are a state, a call or the system – never an incoming message. */
        private val NOT_A_MESSAGE = setOf("call", "missed_call", "voicemail", "transport", "progress", "service", "sys",
            "status", "navigation", "stopwatch", "workout", "location_sharing", "recommendation", "alarm", "reminder",
            "event", "err")

        private val HIDDEN = ImportanceDecision(ImportanceVerdict.UNCLEAR, ImportanceReason.UNCLEAR)
        private const val HIDDEN_EXPLANATION = "Android hat mir nur gezeigt, dass in %s etwas eingegangen ist – ohne " +
            "Absender und ohne Inhalt (bei gesperrtem Gerät ist das immer so)."
    }
}
