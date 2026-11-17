package at.heinz.deviceai.notifications

import java.util.Locale

/** How much of a notification the owner allows to be spoken for one app. */
enum class AnnouncementMode {
    /** Never announced (same as no rule at all). */
    OFF,
    /** Only the app and the kind: "WhatsApp: neue Nachricht." */
    APP_ONLY,
    /** App and title/sender: "WhatsApp: Nachricht von Anna." */
    SENDER,
    /** App, title/sender and the bounded text. */
    FULL,
}

data class AppRule(val mode: AnnouncementMode)

/**
 * Owner settings. Privacy by default: [enabled] is false and [apps] is empty, so nothing is ever
 * announced until the owner opts in per package.
 */
data class AnnouncementSettings(
    val enabled: Boolean = false,
    val apps: Map<String, AppRule> = emptyMap(),
    /** When true, announcements are spoken only during an active conversation or in drive mode. */
    val quietWhenNotInConversation: Boolean = true,
    val maxPerMinute: Int = NotificationAnnouncementPolicy.DEFAULT_MAX_PER_MINUTE,
)

/**
 * One posted notification as seen by the listener. [title] and [text] are UNTRUSTED third-party
 * data. `toString()` is redacted on purpose so that content cannot leak through a log statement.
 */
data class IncomingNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val category: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val postedAtMs: Long,
    val isOwnPackage: Boolean,
) {
    override fun toString(): String = "IncomingNotification(package=$packageName, content=redacted)"
}

/**
 * Caller situation for one decision.
 *
 * [listening] must be true only while an owner utterance is actually being captured. A microphone
 * that merely idles open in continuous conversation is not "listening" here; otherwise every
 * announcement would be deferred until it expires.
 * [nowMs] is one monotonic or wall clock of the caller's choice, used consistently for all calls.
 */
data class AnnouncementState(
    val conversationActive: Boolean,
    val driveModeActive: Boolean,
    val assistantSpeaking: Boolean,
    val listening: Boolean,
    val nowMs: Long,
)

sealed class AnnouncementDecision {
    /**
     * Speak [spokenText] and optionally show [displayTitle] / [displayDetail].
     *
     * [spokenText] and [displayDetail] contain UNTRUSTED notification content behind a fixed German
     * lead-in; [displayTitle] is the sanitised name of the app. They are output only: hand them to
     * text-to-speech or a text view, never to the command interpreter, the tool layer or a model
     * prompt as an instruction.
     */
    data class Announce(
        val spokenText: String,
        val displayTitle: String,
        val displayDetail: String,
    ) : AnnouncementDecision()

    data class Suppress(val reason: SuppressReason) : AnnouncementDecision()

    /** The assistant speaks or the owner is talking: queue [notification] and decide again later. */
    data class Defer(val notification: IncomingNotification) : AnnouncementDecision()
}

enum class SuppressReason {
    DISABLED,
    APP_NOT_ENABLED,
    OWN_PACKAGE,
    ONGOING,
    GROUP_SUMMARY,
    CATEGORY,
    EMPTY,
    DUPLICATE,
    NOT_IN_CONVERSATION,
    RATE_LIMITED,
    /** Folded into the announcement of an earlier notification of the same batch. */
    COALESCED,
}

/**
 * Decides whether and how a third-party notification is announced by voice.
 *
 * Pure Kotlin, no Android dependency. The instance keeps only bounded in-memory state: the last
 * content hash per notification key (at most [MAX_REMEMBERED_KEYS]) and the timestamps of the
 * announcements of the last minute. Notification text itself is never stored here.
 *
 * Security model: notification content is data. It is sanitised, bounded and only ever placed
 * behind a fixed German frame that starts with the app label. One-time codes are never returned.
 */
class NotificationAnnouncementPolicy {
    private val lock = Any()
    private val seen = LinkedHashMap<String, Long>()
    private val spokenAt = ArrayDeque<Long>()
    private var overflowCount = 0
    private var lastOverflowAtMs = 0L
    private var lastCollapsedAtMs: Long? = null

    fun decide(
        notification: IncomingNotification,
        settings: AnnouncementSettings,
        state: AnnouncementState,
    ): AnnouncementDecision = decideAll(listOf(notification), settings, state).first()

    /**
     * Decides a batch (for example a drained [AnnouncementQueue]). The result is index-aligned with
     * [notifications]. Several messages or e-mails of one app in APP_ONLY or SENDER mode are folded
     * into one short line ("Signal: zwei neue Nachrichten."); the folded entries are returned as
     * [SuppressReason.COALESCED]. FULL mode and security codes are never folded.
     */
    fun decideAll(
        notifications: List<IncomingNotification>,
        settings: AnnouncementSettings,
        state: AnnouncementState,
    ): List<AnnouncementDecision> = synchronized(lock) {
        val results = arrayOfNulls<AnnouncementDecision>(notifications.size)
        val candidates = ArrayList<Candidate>()
        val batchSeen = HashMap<String, Long>()
        notifications.forEachIndexed { index, notification ->
            val reason = ineligible(notification, settings)
            if (reason != null) {
                results[index] = AnnouncementDecision.Suppress(reason)
                return@forEachIndexed
            }
            val candidate = candidate(index, notification, settings)
            val key = memoryKey(notification.key)
            when {
                candidate == null -> results[index] = AnnouncementDecision.Suppress(SuppressReason.EMPTY)
                seen[key] == candidate.contentHash || batchSeen[key] == candidate.contentHash ->
                    results[index] = AnnouncementDecision.Suppress(SuppressReason.DUPLICATE)
                settings.quietWhenNotInConversation && !state.conversationActive && !state.driveModeActive ->
                    results[index] = AnnouncementDecision.Suppress(SuppressReason.NOT_IN_CONVERSATION)
                else -> {
                    batchSeen[key] = candidate.contentHash
                    candidates += candidate
                }
            }
        }
        if (candidates.isEmpty()) return@synchronized results.map { it!! }
        if (state.assistantSpeaking || state.listening) {
            // Not remembered as seen: the caller queues it and asks again.
            candidates.forEach { results[it.index] = AnnouncementDecision.Defer(it.notification) }
            return@synchronized results.map { it!! }
        }

        val now = state.nowMs
        val limit = settings.maxPerMinute.coerceIn(1, MAX_PER_MINUTE_CEILING)
        pruneWindow(now)
        var lastSpokenIndex = -1
        var firstOverflowIndex = -1
        var overflowed = 0
        for (group in coalesce(candidates)) {
            group.members.forEach { remember(memoryKey(it.notification.key), it.contentHash) }
            val first = group.members.first().index
            if (spokenAt.size < limit) {
                spokenAt.addLast(now)
                results[first] = group.announcement
                group.members.drop(1).forEach {
                    results[it.index] = AnnouncementDecision.Suppress(SuppressReason.COALESCED)
                }
                lastSpokenIndex = first
            } else {
                group.members.forEach {
                    results[it.index] = AnnouncementDecision.Suppress(SuppressReason.RATE_LIMITED)
                }
                if (firstOverflowIndex < 0) firstOverflowIndex = first
                overflowed += group.members.size
            }
        }
        if (overflowed > 0) {
            overflowCount = (overflowCount + overflowed).coerceAtMost(MAX_OVERFLOW_COUNT)
            lastOverflowAtMs = now
        }
        if (overflowCount > 0 && collapsedLineAllowed(now)) {
            if (firstOverflowIndex >= 0) {
                results[firstOverflowIndex] = collapsedAnnouncement(overflowCount)
            } else if (lastSpokenIndex >= 0) {
                val spoken = results[lastSpokenIndex] as AnnouncementDecision.Announce
                results[lastSpokenIndex] = spoken.copy(
                    spokenText = spoken.spokenText + " Außerdem " + furtherNotifications(overflowCount) + ".",
                )
            }
            overflowCount = 0
            lastCollapsedAtMs = now
        }
        results.map { it!! }
    }

    /**
     * The collapsed line for notifications that were held back by the rate limit and not yet
     * mentioned, or null when there is nothing to say, the line was already spoken within the last
     * minute, or the situation does not allow speaking. Optional: [decideAll] reports the same count
     * together with the next notification that arrives.
     */
    fun pendingSummary(settings: AnnouncementSettings, state: AnnouncementState): AnnouncementDecision.Announce? =
        synchronized(lock) {
            if (!settings.enabled) {
                overflowCount = 0
                return@synchronized null
            }
            if (state.assistantSpeaking || state.listening) return@synchronized null
            if (settings.quietWhenNotInConversation && !state.conversationActive && !state.driveModeActive) {
                return@synchronized null
            }
            if (overflowCount == 0 || !collapsedLineAllowed(state.nowMs)) return@synchronized null
            val line = collapsedAnnouncement(overflowCount)
            overflowCount = 0
            lastCollapsedAtMs = state.nowMs
            line
        }

    /** The notification was removed (for example read on the phone): allow the same content again. */
    fun forget(key: String) {
        synchronized(lock) { seen.remove(memoryKey(key)) }
    }

    /** Drops all remembered keys and counters, for example when the owner disables announcements. */
    fun reset() {
        synchronized(lock) {
            seen.clear()
            spokenAt.clear()
            overflowCount = 0
            lastOverflowAtMs = 0L
            lastCollapsedAtMs = null
        }
    }

    internal fun rememberedKeyCount(): Int = synchronized(lock) { seen.size }

    private class Candidate(
        val index: Int,
        val notification: IncomingNotification,
        val mode: AnnouncementMode,
        val kind: Kind,
        val label: String,
        val title: String,
        val text: String,
        val securityCode: Boolean,
        val contentHash: Long,
    )

    private class Group(val members: List<Candidate>, val announcement: AnnouncementDecision.Announce)

    private enum class Kind(val singular: String, val plural: String) {
        MESSAGE("Nachricht", "Nachrichten"),
        EMAIL("E-Mail", "E-Mails"),
        OTHER("Benachrichtigung", "Benachrichtigungen"),
    }

    /** Content-independent reasons first, so content of excluded notifications is not even parsed. */
    private fun ineligible(notification: IncomingNotification, settings: AnnouncementSettings): SuppressReason? {
        if (!settings.enabled) return SuppressReason.DISABLED
        if (notification.isOwnPackage) return SuppressReason.OWN_PACKAGE
        val mode = settings.apps[notification.packageName]?.mode ?: AnnouncementMode.OFF
        if (mode == AnnouncementMode.OFF) return SuppressReason.APP_NOT_ENABLED
        if (notification.isOngoing) return SuppressReason.ONGOING
        if (notification.isGroupSummary) return SuppressReason.GROUP_SUMMARY
        val category = notification.category?.trim()?.lowercase(Locale.ROOT)
        if (category != null && category in SILENT_CATEGORIES) return SuppressReason.CATEGORY
        return null
    }

    private fun candidate(index: Int, notification: IncomingNotification, settings: AnnouncementSettings): Candidate? {
        val rawTitle = UntrustedNotificationText.clean(notification.title)
        val rawText = UntrustedNotificationText.clean(notification.text)
        if (rawTitle.isEmpty() && rawText.isEmpty()) return null
        val mode = settings.apps.getValue(notification.packageName).mode
        val label = UntrustedNotificationText.label(notification.appLabel)
        val securityCode = UntrustedNotificationText.containsSecurityCode(rawTitle, rawText)
        val category = notification.category?.trim()?.lowercase(Locale.ROOT)
        val kind = when {
            category == CATEGORY_MESSAGE -> Kind.MESSAGE
            // A messenger without a category: a message. With another one (a missed call) it is not.
            category.isNullOrEmpty() && notification.packageName in KNOWN_MESSENGER_PACKAGES -> Kind.MESSAGE
            category == CATEGORY_EMAIL -> Kind.EMAIL
            else -> Kind.OTHER
        }
        var title = ""
        var text = ""
        if (!securityCode && mode != AnnouncementMode.APP_ONLY) {
            val withoutLinks = UntrustedNotificationText.replaceLinks(rawTitle)
            val allowed = if (mode == AnnouncementMode.FULL) withoutLinks else UntrustedNotificationText.hideAmounts(withoutLinks)
            title = UntrustedNotificationText.bound(allowed, UntrustedNotificationText.MAX_TITLE)
                .trimEnd(' ', ':', ',', ';', '.')
            // A title that only repeats the app name carries no sender.
            if (title.equals(label, ignoreCase = true)) title = ""
            if (mode == AnnouncementMode.FULL) {
                text = UntrustedNotificationText.bound(
                    UntrustedNotificationText.replaceLinks(rawText),
                    UntrustedNotificationText.MAX_TEXT,
                )
            }
        }
        return Candidate(
            index, notification, mode, kind, label, title, text, securityCode,
            UntrustedNotificationText.contentHash(rawTitle, rawText),
        )
    }

    private fun coalesce(candidates: List<Candidate>): List<Group> {
        val foldable = candidates
            .filter { !it.securityCode && it.kind != Kind.OTHER && it.mode != AnnouncementMode.FULL }
            .groupBy { it.notification.packageName }
            .filterValues { it.size > 1 }
        val groups = ArrayList<Group>()
        val done = HashSet<String>()
        for (candidate in candidates) {
            val members = foldable[candidate.notification.packageName]?.takeIf { candidate in it }
            if (members == null) {
                groups += Group(listOf(candidate), single(candidate))
            } else if (done.add(candidate.notification.packageName)) {
                groups += Group(members, folded(members))
            }
        }
        return groups
    }

    private fun single(c: Candidate): AnnouncementDecision.Announce {
        if (c.securityCode) return announce(c.label, SECURITY_CODE_SENTENCE, reported = false)
        val noun = c.kind.singular
        if (c.title.isEmpty() && c.text.isEmpty()) return announce(c.label, "neue $noun.", reported = false)
        if (c.kind == Kind.OTHER) {
            val detail = when {
                c.text.isEmpty() -> sentence(c.title)
                c.title.isEmpty() -> sentence(c.text)
                else -> sentence(c.title) + " " + sentence(c.text)
            }
            return announce(c.label, detail, reported = true)
        }
        val detail = when {
            c.text.isEmpty() -> "$noun von ${c.title}."
            c.title.isEmpty() -> "neue $noun: ${sentence(c.text)}"
            else -> "$noun von ${c.title}: ${sentence(c.text)}"
        }
        return announce(c.label, detail, reported = false)
    }

    private fun folded(members: List<Candidate>): AnnouncementDecision.Announce {
        val first = members.first()
        val sender = first.title.takeIf { title ->
            title.isNotEmpty() && members.all { it.title.equals(title, ignoreCase = true) }
        }
        val count = "${countWord(members.size)} neue ${first.kind.plural}"
        return announce(first.label, if (sender == null) "$count." else "$count von $sender.", reported = false)
    }

    /**
     * The only place where announcement text is assembled. [reported] marks a detail that starts
     * with untrusted content; it gets a fixed lead-in in both the spoken and the displayed form,
     * so no returned sentence ever consists of third-party text alone.
     */
    private fun announce(label: String, detail: String, reported: Boolean): AnnouncementDecision.Announce =
        AnnouncementDecision.Announce(
            spokenText = if (reported) "$label meldet: $detail" else "$label: $detail",
            displayTitle = label,
            displayDetail = if (reported) "Neu: $detail" else detail.replaceFirstChar { it.titlecase(Locale.GERMAN) },
        )

    private fun collapsedAnnouncement(count: Int): AnnouncementDecision.Announce {
        val line = furtherNotifications(count).replaceFirstChar { it.titlecase(Locale.GERMAN) } + "."
        return AnnouncementDecision.Announce(line, "Benachrichtigungen", line)
    }

    private fun furtherNotifications(count: Int): String =
        if (count == 1) "eine weitere Benachrichtigung" else "${countWord(count)} weitere Benachrichtigungen"

    private fun sentence(text: String): String =
        if (text.isNotEmpty() && text.last() in SENTENCE_END) text else "$text."

    private fun collapsedLineAllowed(now: Long): Boolean {
        // Held-back notifications that nobody was told about for two minutes are no longer news.
        if (overflowCount > 0 && now - lastOverflowAtMs > OVERFLOW_MAX_AGE_MS) overflowCount = 0
        val last = lastCollapsedAtMs ?: return overflowCount > 0
        return overflowCount > 0 && (now - last >= WINDOW_MS || now < last)
    }

    private fun pruneWindow(now: Long) {
        // Entries from the future can only come from a clock change; they must not block forever.
        spokenAt.removeAll { it > now || now - it >= WINDOW_MS }
    }

    private fun remember(key: String, hash: Long) {
        seen.remove(key)
        seen[key] = hash
        while (seen.size > MAX_REMEMBERED_KEYS) seen.remove(seen.keys.first())
    }

    private fun memoryKey(key: String): String = if (key.length <= MAX_KEY_LENGTH) key else key.substring(0, MAX_KEY_LENGTH)

    companion object {
        const val DEFAULT_MAX_PER_MINUTE = 4
        const val MAX_PER_MINUTE_CEILING = 20
        const val MAX_REMEMBERED_KEYS = 128
        const val SECURITY_CODE_SENTENCE = "Ein Sicherheitscode ist eingegangen."

        /**
         * Well-known messenger packages. Used to say "Nachricht" instead of "Benachrichtigung" and
         * as owner-facing suggestions; never enabled automatically.
         */
        val KNOWN_MESSENGER_PACKAGES: List<String> = listOf(
            "com.whatsapp",
            "org.thoughtcrime.securesms",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )

        private const val WINDOW_MS = 60_000L
        private const val OVERFLOW_MAX_AGE_MS = 120_000L
        private const val MAX_OVERFLOW_COUNT = 99
        private const val MAX_KEY_LENGTH = 256
        private const val CATEGORY_MESSAGE = "msg"
        private const val CATEGORY_EMAIL = "email"
        private const val SENTENCE_END = ".!?…"

        /** android.app.Notification category values that describe state, not an incoming event. */
        private val SILENT_CATEGORIES = setOf(
            "transport", "progress", "service", "sys", "status", "navigation",
            "stopwatch", "workout", "location_sharing", "recommendation",
        )

        private val COUNT_WORDS = listOf(
            "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf",
        )

        private fun countWord(count: Int): String = COUNT_WORDS.getOrNull(count - 2) ?: count.toString()
    }
}

/**
 * Sanitising of untrusted notification strings. Everything here is bounded: at most [MAX_RAW]
 * UTF-16 units of any input are ever inspected.
 */
internal object UntrustedNotificationText {
    const val MAX_TITLE = 80
    const val MAX_TEXT = 240
    const val MAX_LABEL = 40
    const val MAX_RAW = 2_000
    const val LINK_WORD = "ein Link"
    const val MAIL_ADDRESS_WORD = "eine E-Mail-Adresse"
    const val AMOUNT_WORD = "ein Betrag"
    const val UNKNOWN_APP = "Eine App"

    private val MAIL_ADDRESS = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val LINK = Regex(
        """(?i)\b(?:(?:https?|ftp)://|www\.)[^\s<>"]*[^\s<>".,;:!?)\]]""" +
            """|\b[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|de|at|ch|net|org|io|eu|info|app|ly|gl)\b(?:/[^\s<>"]*[^\s<>".,;:!?)\]])?""",
    )
    private const val NUMBER = """\d+(?:[.,']\d+)*(?:,-)?"""
    private val AMOUNT = Regex(
        """(?i)(?:€|\$|£|\b(?:EUR|USD|CHF|GBP)\b)\s?[+-]?$NUMBER""" +
            """|[+-]?$NUMBER\s?(?:€|\$|£|(?:EUR|USD|CHF|GBP|Euro|Dollar|Franken|Cent)\b)""",
    )

    // Matched against lower-cased text.
    private val CODE_KEYWORD = Regex(
        """code|\b(?:m|sms|push|chip|photo|app|i|e)?-?tans?\b|\bpin\b|\bpuk\b|passwor[dt]|kennwort|\botp\b""" +
            """|best(?:ä|ae)tig|verifi|verify|sicherheits|security|authenti|einmal|one[- ]?time|\b2fa\b""" +
            """|freigabe|zugangs|log-?in|anmeld""",
    )
    private val CODE_DIGITS = Regex(
        """(?<![\p{L}\d.,/])(?:[A-Za-z]{1,2}-)?(?:\d{4,10}|\d{3}[ -]\d{3}|\d{2}[ -]\d{2}[ -]\d{2})(?![.,:/]?\d)(?!\p{L})""",
    )

    /** Bounded copy without control, format, private-use or broken surrogate characters; single spaces. */
    fun clean(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val end = minOf(raw.length, MAX_RAW)
        val out = StringBuilder(minOf(end, 256))
        var pendingSpace = false
        var i = 0
        while (i < end) {
            val codePoint = raw.codePointAt(i)
            val width = Character.charCount(codePoint)
            if (i + width > end) break // do not keep half of a pair at the inspection bound
            i += width
            val type = Character.getType(codePoint).toByte()
            val drop = type == Character.FORMAT || type == Character.SURROGATE ||
                type == Character.PRIVATE_USE || type == Character.UNASSIGNED
            if (drop) continue
            val space = Character.isISOControl(codePoint) || Character.isWhitespace(codePoint) ||
                Character.isSpaceChar(codePoint)
            if (space) {
                pendingSpace = out.isNotEmpty()
                continue
            }
            if (pendingSpace) out.append(' ')
            pendingSpace = false
            out.appendCodePoint(codePoint)
        }
        return out.toString()
    }

    /** App label for the fixed frame. A colon would blur the frame, so it is removed. */
    fun label(raw: String?): String {
        val cleaned = bound(clean(raw).replace(':', ' ').replace(Regex(" {2,}"), " ").trim(), MAX_LABEL)
            .trimEnd('.', ',', ';', '!', '?', ' ')
        return cleaned.ifEmpty { UNKNOWN_APP }
    }

    fun replaceLinks(cleaned: String): String =
        LINK.replace(MAIL_ADDRESS.replace(cleaned, MAIL_ADDRESS_WORD), LINK_WORD)

    fun hideAmounts(cleaned: String): String = AMOUNT.replace(cleaned, AMOUNT_WORD)

    /**
     * True when the notification looks like a one-time code: a 4–10 digit group (also "123 456",
     * "123-456", "G-123456") together with a code/TAN/PIN/password/confirmation word anywhere in
     * title or text. Amounts and dates are not digit groups. Deliberately errs towards silence.
     */
    fun containsSecurityCode(cleanedTitle: String, cleanedText: String): Boolean {
        val combined = "$cleanedTitle \n $cleanedText"
        if (!CODE_KEYWORD.containsMatchIn(combined.lowercase(Locale.ROOT))) return false
        return CODE_DIGITS.containsMatchIn(AMOUNT.replace(combined, " "))
    }

    /**
     * At most [max] UTF-16 units. A longer text is cut after the last complete sentence if that
     * keeps at least half of the budget, otherwise at the last word; "…" marks the cut. A surrogate
     * pair is never split.
     */
    fun bound(cleaned: String, max: Int): String {
        if (cleaned.length <= max) return cleaned
        var cut = max - 1
        if (Character.isHighSurrogate(cleaned[cut - 1])) cut--
        val head = cleaned.substring(0, cut)
        for (i in minOf(head.length - 1, max - 3) downTo max / 2) {
            if (head[i] in ".!?" && cleaned[i + 1] == ' ') return head.substring(0, i + 1) + " …"
        }
        val space = head.lastIndexOf(' ')
        val words = if (space >= max / 2) head.substring(0, space) else head
        return words.trimEnd(' ', ',', ';', ':', '-', '.') + "…"
    }

    /** 64-bit FNV-1a over title and text; only this hash is remembered for duplicate detection. */
    fun contentHash(cleanedTitle: String, cleanedText: String): Long {
        var hash = -0x340d631b7bdddcdbL
        fun mix(value: Int) {
            hash = hash xor (value and 0xff).toLong()
            hash *= 0x100000001b3L
            hash = hash xor ((value ushr 8) and 0xff).toLong()
            hash *= 0x100000001b3L
        }
        cleanedTitle.forEach { mix(it.code) }
        mix(0xffff)
        cleanedText.forEach { mix(it.code) }
        return hash
    }
}

/**
 * Small FIFO for notifications that arrived while the assistant was speaking or the owner was
 * talking. In-memory only, at most [MAX_ENTRIES]; when full the oldest entry is dropped so the
 * newest are kept. A newer notification with the same key replaces the queued one.
 */
class AnnouncementQueue {
    private class Entry(val notification: IncomingNotification, val queuedAtMs: Long)

    private val entries = ArrayDeque<Entry>()

    val size: Int get() = synchronized(entries) { entries.size }

    fun offer(notification: IncomingNotification, nowMs: Long) {
        synchronized(entries) {
            entries.removeAll { it.notification.key == notification.key }
            entries.addLast(Entry(notification, nowMs))
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    /** The notification was dismissed or read elsewhere: nothing left to announce. */
    fun remove(key: String): Boolean = synchronized(entries) { entries.removeAll { it.notification.key == key } }

    /** Empties the queue and returns, oldest first, the entries queued within the last two minutes. */
    fun drain(nowMs: Long): List<IncomingNotification> = synchronized(entries) {
        val fresh = entries.filter { nowMs - it.queuedAtMs <= MAX_AGE_MS }.map { it.notification }
        entries.clear()
        fresh
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    companion object {
        const val MAX_ENTRIES = 8
        const val MAX_AGE_MS = 120_000L
    }
}
