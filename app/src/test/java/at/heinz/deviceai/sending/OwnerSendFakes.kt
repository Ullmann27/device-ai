package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerWorkGeneration
import at.heinz.deviceai.cockpit.StepState
import java.time.LocalDate

/* Shared fakes for the send lane. All names, numbers and addresses are fictional. */

internal class FakeSendStore : OwnerSendKeyValue {
    val values = HashMap<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

internal fun fakeGrants(store: FakeSendStore = FakeSendStore(), day: () -> LocalDate = { LocalDate.of(2026, 11, 18) },
                        vararg on: SendChannel): OwnerSendGrants =
    OwnerSendGrants(store, day).apply { on.forEach { change(it, true, OwnerSendOwnerTap()) } }

/** Synthetic accessibility trees shaped like the WhatsApp chat and the Gmail compose screen. */
internal object SendScreens {
    private const val WA = "com.whatsapp:id/"
    private const val GM = "com.google.android.gm:id/"

    fun whatsAppChat(title: String, entry: String, bubbles: List<String> = emptyList(), sendId: String? = "send",
                     sendLabel: String = "Senden", complete: Boolean = true): SendScreen = SendScreen("com.whatsapp", buildList {
        add(SendNode(WA + "back", "android.widget.ImageView", description = "Zurück", clickable = true))
        add(SendNode(WA + "conversation_contact_name", "android.widget.TextView", text = title))
        add(SendNode(WA + "conversation_contact_status", "android.widget.TextView", text = "online"))
        bubbles.forEach { add(SendNode(WA + "message_text", "android.widget.TextView", text = it)) }
        add(SendNode(WA + "emoji_picker_btn", "android.widget.ImageButton", description = "Emoji", clickable = true))
        add(if (entry.isEmpty()) SendNode(WA + "entry", "android.widget.EditText", text = "Nachricht", hintShown = true,
            clickable = true, editable = true)
        else SendNode(WA + "entry", "android.widget.EditText", text = entry, clickable = true, editable = true))
        add(SendNode(WA + "input_attach_button", "android.widget.ImageButton", description = "Anhängen", clickable = true))
        if (entry.isEmpty()) add(SendNode(WA + "voice_note_btn", "android.widget.ImageButton", description = "Sprachnachricht", clickable = true))
        else add(SendNode(sendId?.let { WA + it }, "android.widget.ImageButton", description = sendLabel, clickable = true))
    }, complete)

    fun gmailCompose(to: List<String>, subject: String, body: String, attachment: String? = null,
                     from: String = "besitzer@example.org", chips: Boolean = true, sendId: String? = "send",
                     sendLabel: String = "Senden", signature: String = "", extra: List<SendNode> = emptyList()): SendScreen =
        SendScreen("com.google.android.gm", buildList {
            add(SendNode(GM + "from_account_name", "android.widget.TextView", text = from))
            if (chips) to.forEach { add(SendNode(GM + "peoplekit_chip", "android.view.ViewGroup", description = it, clickable = true)) }
            else add(SendNode(GM + "to", "android.widget.MultiAutoCompleteTextView", text = to.joinToString(", "), editable = true))
            add(SendNode(GM + "subject", "android.widget.EditText", text = subject.ifEmpty { "Betreff" },
                hintShown = subject.isEmpty(), editable = true))
            add(SendNode(GM + "wc_body", "android.widget.EditText", text = body + signature, editable = true))
            if (attachment != null) add(SendNode(GM + "attachment_tile_title", "android.widget.TextView", text = attachment))
            add(SendNode(GM + "add_attachment", "android.widget.TextView", description = "Datei anhängen", clickable = true))
            add(SendNode(sendId?.let { GM + it }, "android.widget.TextView", description = sendLabel, clickable = true))
            addAll(extra)
        })

    fun gmailInbox(): SendScreen = SendScreen("com.google.android.gm", listOf(
        SendNode(GM + "open_search", "android.widget.EditText", text = "In E-Mails suchen", hintShown = true, editable = true),
        SendNode(GM + "compose_button", "android.widget.Button", text = "Schreiben", clickable = true)))

    fun dialog(packageName: String, message: String): SendScreen = SendScreen(packageName, listOf(
        SendNode("android:id/message", "android.widget.TextView", text = message),
        SendNode("android:id/button1", "android.widget.Button", text = "OK", clickable = true)))
}

/**
 * A phone that shows [beforeClick] once the target was opened and [afterClick] once the send control was pressed.
 * Time only passes through [pause], so bounded waits run instantly and deterministically.
 */
internal class FakeSendDevice : OwnerSendDevice {
    var installedApps: Set<SendChannel> = SendChannel.entries.toSet()
    var accessibility = true
    var openProblem: String? = null
    var pressAccepted = true
    var beforeClick: (SendPlan, Long) -> SendScreen? = { _, _ -> null }
    var afterClick: (SendPlan, Long) -> SendScreen? = { _, _ -> null }
    /** Shown while nothing was opened by a plan (the „Sende-Diagnose“ reads whatever is in front). */
    var idle: Map<SendChannel, SendScreen> = emptyMap()
    /** What is in front when the target app is not (Continuum itself); null = the screen cannot be observed. */
    var frontWhenAway: String? = "at.heinz.deviceai.continuumlocal"
    val opened = ArrayList<SendPlan>()
    val pressed = ArrayList<SendLocator>()
    var returned = 0
    var clock = 0L
    private var pressedAt = 0L
    private var pressedCurrent = false

    override fun installed(channel: SendChannel) = channel in installedApps
    override fun accessibilityOn() = accessibility
    override fun open(plan: SendPlan): String? {
        openProblem?.let { return it }
        opened += plan
        pressedCurrent = false
        return null
    }
    override fun screen(channel: SendChannel): SendScreen? {
        val plan = opened.lastOrNull() ?: return idle[channel]
        if (plan.channel != channel) return null
        return if (!pressedCurrent) beforeClick(plan, clock) else afterClick(plan, clock - pressedAt)
    }
    override fun frontPackage(): String? =
        if (!accessibility) null else opened.lastOrNull()?.let { screen(it.channel)?.packageName } ?: frontWhenAway
    override fun press(channel: SendChannel, locator: SendLocator, stillVerified: (SendScreen) -> Boolean): Boolean {
        val shown = screen(channel) ?: return false
        if (!pressAccepted || shown.nodes.count(locator::matches) != 1 || !stillVerified(shown)) return false
        pressed += locator
        pressedCurrent = true
        pressedAt = clock
        return true
    }
    override fun returnToContinuum() { returned++ }
    override fun pause(millis: Long) { clock += millis }
    override fun nowMillis() = clock

    /** WhatsApp behaves: the chat shows the prepared draft, and the bubble after the click. */
    fun whatsAppWorks(title: (SendPlan.WhatsApp) -> String = { it.contactName }) {
        beforeClick = { plan, _ -> (plan as SendPlan.WhatsApp).let { SendScreens.whatsAppChat(title(it), it.text) } }
        afterClick = { plan, _ -> (plan as SendPlan.WhatsApp).let { SendScreens.whatsAppChat(title(it), "", listOf(it.text)) } }
    }

    /** Gmail behaves: the compose screen shows the prepared mail and closes after the click. */
    fun gmailWorks() {
        beforeClick = { plan, _ -> (plan as SendPlan.Mail).let {
            SendScreens.gmailCompose(listOf(it.address), it.subject, it.body, it.attachment?.displayName) } }
        afterClick = { _, _ -> null }
    }
}

internal class FakeContacts(var phoneRows: List<OwnerContactPhone> = emptyList(),
                            var mailRows: List<OwnerContactMail> = emptyList()) : OwnerSendContactSource {
    var reads = 0
    override fun phones(): List<OwnerContactPhone> { reads++; return phoneRows }
    override fun mails(): List<OwnerContactMail> { reads++; return mailRows }

    companion object {
        fun phone(id: Long, name: String, number: String, mobile: Boolean = true, e164: String? = number.replace(" ", "")) =
            OwnerContactPhone(id, name, number, e164, mobile)
    }
}

/** The Activity as the director sees it; jobs run inline. */
internal class FakeSendHost(
    val device: FakeSendDevice = FakeSendDevice(),
    val contacts: FakeContacts = FakeContacts(),
    val store: FakeSendStore = FakeSendStore(),
    var day: LocalDate = LocalDate.of(2026, 11, 18)
) : OwnerSendHost {
    val grants = OwnerSendGrants(store) { day }
    val relationStore = FakeSendStore()
    val relations = OwnerRelations(relationStore) { java.time.Instant.parse("2026-11-19T08:00:00Z") }
    val accepted = ArrayList<String>()
    val replies = ArrayList<String>()
    val activities = ArrayList<String>()
    val steps = ArrayList<Triple<String, String, StepState>>()
    val prompts = ArrayList<String>()
    val diagnoses = ArrayList<String>()
    var contactsGranted = true
    var grantOnRequest = true
    var contactRequests = 0
    var model = true
    var generated: OwnerWorkGeneration = OwnerWorkGeneration("Alles Liebe zum Geburtstag, ich denke an dich!", null, true)
    override var sessionId = "chat-1"
    override var ownerTurn = 0L
    override val assistantName: String? = null

    fun grant(vararg channels: SendChannel) = channels.forEach { grants.change(it, true, OwnerSendOwnerTap()) }
    val lastReply: String get() = replies.last()

    override fun acceptOwnerTurn(request: String) { accepted += request; ownerTurn++ }
    override fun reply(request: String, text: String) { replies += text }
    override fun activity(line: String) { activities += line }
    override fun contactsReadable() = contactsGranted
    override fun requestContacts(explanation: String, done: (Boolean) -> Unit) {
        contactRequests++
        contactsGranted = grantOnRequest
        done(grantOnRequest)
    }
    override fun contacts(): OwnerSendContactSource = contacts
    override fun device(): OwnerSendDevice = device
    override fun grants(): OwnerSendGrants = grants
    override fun relations(): OwnerRelations = relations
    override fun modelReady() = model
    override fun <T> runJob(request: String, work: (OwnerSendJob) -> T, done: (Result<T>) -> Unit) = done(runCatching {
        work(object : OwnerSendJob {
            override fun step(key: String, label: String, state: StepState) { steps += Triple(key, label, state) }
            override fun generate(prompt: String): OwnerWorkGeneration { prompts += prompt; return generated }
        })
    })
    override fun nowMillis() = device.clock
    override fun showDiagnosis(report: String) { diagnoses += report }
    override fun appVersion(channel: SendChannel): String? = "2.26.1 (fiktiv)"
}
