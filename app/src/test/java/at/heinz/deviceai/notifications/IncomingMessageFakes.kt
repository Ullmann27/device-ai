package at.heinz.deviceai.notifications

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep

/* Shared fakes for the incoming-message feature. All names, numbers and texts are invented. */

internal const val WA = "com.whatsapp"
internal const val GM = "com.google.android.gm"

/** One clock for the inbox and the conversation; time only passes through [advance], so timers run deterministically. */
internal class FakeIncomingTime : IncomingClock, IncomingScheduler {
    var now = 1_000_000L
    private class Task(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
    private val tasks = ArrayList<Task>()

    override fun nowMs(): Long = now

    override fun after(delayMs: Long, action: () -> Unit): () -> Unit {
        val task = Task(now + delayMs, action)
        tasks += task
        return { task.cancelled = true }
    }

    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
            tasks.remove(next)
            now = maxOf(now, next.at)
            next.action()
        }
        now = end
    }
}

internal fun posted(app: String = WA, title: String, text: String, key: String = "0|$app|1|$title|10", category: String? = "msg",
                    ongoing: Boolean = false, summary: Boolean = false, details: NotificationDetails? = null, label: String? = null) =
    IncomingNotification(key, app, label ?: when (app) { WA -> "WhatsApp"; GM -> "Gmail"; else -> "Signal" }, title, text, category,
        ongoing, summary, postedAtMs = 0L, isOwnPackage = false, details = details)

internal fun mail(sender: String, subject: String, body: String = "", whenMs: Long = 1L, source: NotificationSource = NotificationSource.LISTENER) =
    posted(GM, sender, subject, key = "0|$GM|7|$sender$subject|10", category = "email",
        details = NotificationDetails(source, whenMs = whenMs, bigText = if (body.isEmpty()) "" else "$subject $body"))

/** A messaging-style notification: the whole unread history of one chat, as the app re-posts it with every message. */
internal fun chat(sender: String, vararg texts: Pair<String, Long>, source: NotificationSource = NotificationSource.LISTENER,
                  group: String? = null, app: String = WA, silent: Boolean? = null, self: String = "", key: String? = null,
                  senders: List<String>? = null) =
    posted(app, group ?: sender, texts.last().first, key = key ?: "0|$app|1|${group ?: sender}|10", details = NotificationDetails(source,
        whenMs = texts.last().second, conversationTitle = group.orEmpty(), isGroupConversation = group != null,
        messages = texts.mapIndexed { index, (text, time) -> NotificationMessage(senders?.get(index) ?: sender, text, time) },
        selfName = self, silent = silent))

internal class NamedProfile(override val ownerName: String?, override val personalTone: Boolean = true) : IncomingOwnerProfile

internal class FakeIncomingHost(private val time: FakeIncomingTime) : IncomingMessageHost {
    data class Sent(val request: String, val channel: IncomingReplyChannel, val recipient: String, val subject: String, val text: String)

    var turn = 0L
    var current = IncomingMessageSettings(enabled = true, apps = listOf(WA, GM), quietHours = false)
    var minute = 12 * 60
    var quietNow = true
    var canAnnounce = true
    var sendTakes = true
    override var profile: IncomingOwnerProfile = NamedProfile("Heinz")
    val announced = ArrayList<String>()
    val replies = ArrayList<String>()
    val turns = ArrayList<String>()
    val activity = ArrayList<String>()
    val staged = ArrayList<CockpitAnimationEvent>()
    val cards = LinkedHashSet<Long>()
    val sent = ArrayList<Sent>()

    override val ownerTurn: Long get() = turn
    override val assistantName: String? = "Jarvis"
    override fun nowMillis(): Long = time.now
    override fun minuteOfDay(): Int = minute
    override fun settings(): IncomingMessageSettings = current
    override fun updateSettings(change: (IncomingMessageSettings) -> IncomingMessageSettings) { current = IncomingMessageSettingsCodec.normalize(change(current)) }
    override fun quiet(): Boolean = quietNow
    override fun announce(title: String, detail: String, spoken: String): Boolean {
        if (!canAnnounce) return false
        announced += spoken
        return true
    }
    override fun acceptOwnerTurn(request: String) { turn++; turns += request }
    override fun reply(text: String) { replies += text }
    override fun activity(line: String) { activity += line }
    override fun stage(event: CockpitAnimationEvent) { staged += event }
    override fun later(delayMs: Long, action: () -> Unit) { time.after(delayMs, action) }
    override fun sendReply(request: String, channel: IncomingReplyChannel, recipient: String, subject: String, text: String): Boolean {
        if (!sendTakes) return false
        acceptOwnerTurn(request)
        sent += Sent(request, channel, recipient, subject, text)
        return true
    }
    override fun card(item: IncomingItem) { cards += item.id }
    override fun clearCard(item: IncomingItem) { cards -= item.id }

    /** The newest step list of the work window, as „label:STATE“. */
    fun steps(): List<String> = (staged.lastOrNull { it is CockpitAnimationEvent.ProcessTimeline } as? CockpitAnimationEvent.ProcessTimeline)
        ?.steps.orEmpty().map { step: ProcessStep -> step.label + ":" + step.state }
}

/** Inbox, conversation and host wired the way the app wires them (the screen is in front unless [attached] is false). */
internal class IncomingRig(attached: Boolean = true, contacts: Set<String> = setOf("Anna", "Anna Berger")) {
    val time = FakeIncomingTime()
    val host = FakeIncomingHost(time)
    val inbox = IncomingMessageInbox(time, time, { host.current }, { it in contacts })
    val dialogue = IncomingMessageDialogue(host, inbox)
    val background = ArrayList<IncomingItem>()
    var inFront = attached

    init {
        inbox.listener = object : IncomingInboxListener {
            override fun onReady(item: IncomingItem) { if (inFront) dialogue.ready(item) else background += item }
            override fun onGone(item: IncomingItem) = dialogue.gone(item)
        }
        if (attached) { dialogue.attach(); time.advance(IncomingMessageDialogue.ATTACH_DELAY_MS + 1) }
    }

    /** Posts and lets the burst close. */
    fun arrive(notification: IncomingNotification): IncomingIntake =
        inbox.onNotification(notification).also { time.advance(IncomingMessageInbox.DEBOUNCE_MS + 1) }

    fun say(text: String): Boolean = dialogue.handle(text)
}
