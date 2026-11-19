package at.heinz.deviceai.sending

import java.util.Locale

/**
 * One accessibility node of the target app's front window, as far as the send flow needs it. Held in memory for the
 * comparison only: never stored, never logged, never given to a model (the flow is deterministic).
 */
internal data class SendNode(
    val viewId: String? = null,
    val className: String? = null,
    val text: String = "",
    val description: String = "",
    /** [text] is the field's hint, not content. */
    val hintShown: Boolean = false,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false
) {
    /** „send“ of „com.whatsapp:id/send“. */
    val idName: String? get() = viewId?.substringAfter(":id/", "")?.takeIf { it.isNotEmpty() }
    val content: String get() = if (hintShown || password) "" else text
}

/** The front application window of [packageName]. [complete] = the bounded walk saw the whole tree. */
internal data class SendScreen(val packageName: String, val nodes: List<SendNode>, val complete: Boolean = true)

/** How the device finds the send control again right before the click: by view id when it has one, else by label. */
internal data class SendLocator(val viewId: String?, val label: String?) {
    init { require(viewId != null || label != null) }
    fun matches(node: SendNode): Boolean = node.visible && node.enabled && node.clickable && !node.editable &&
        !node.password && (if (viewId != null) node.viewId == viewId else OwnerSendScreens.sendLabel(node) == label)
}

internal sealed interface SendVerification {
    data class Verified(val send: SendLocator) : SendVerification
    /** [waiting]: the screen may simply not be ready yet (keep looking within the bound). */
    data class Mismatch(val reason: SendProblem, val waiting: Boolean = false) : SendVerification
}

/** Every reason the flow does not press send, or cannot confirm it – worded for the owner. */
internal enum class SendProblem(val text: String) {
    NOT_IN_FRONT("die App nicht rechtzeitig im Vordergrund war"),
    SCREEN_INCOMPLETE("ich den Bildschirm nicht vollständig lesen konnte"),
    NOT_ON_WHATSAPP("WhatsApp meldet, dass diese Nummer nicht bei WhatsApp ist"),
    DIALOG("die App eine Rückfrage anzeigt, die du selbst beantworten musst"),
    TITLE_NOT_FOUND("ich den Chat-Titel nicht gefunden habe"),
    TITLE_MISMATCH("der geöffnete Chat nicht den erwarteten Empfänger zeigt"),
    FIELD_NOT_FOUND("ich das Nachrichtenfeld nicht eindeutig gefunden habe"),
    TEXT_MISMATCH("im Nachrichtenfeld nicht genau der vorbereitete Text steht"),
    NOT_COMPOSE("der Gmail-Entwurf nicht sichtbar wurde"),
    RECIPIENT_MISSING("ich die Empfängeradresse im Feld „An“ nicht gefunden habe"),
    RECIPIENT_EXTRA("im Entwurf weitere Empfänger stehen"),
    SUBJECT_MISMATCH("der Betreff nicht dem vorbereiteten entspricht"),
    BODY_MISMATCH("der Mailtext nicht dem vorbereiteten entspricht"),
    ATTACHMENT_MISSING("der Anhang im Entwurf nicht zu sehen ist"),
    SEND_NOT_FOUND("ich den Senden-Knopf nicht gefunden habe"),
    SEND_AMBIGUOUS("mehrere mögliche Senden-Knöpfe sichtbar sind"),
    CLICK_REFUSED("sich der Senden-Knopf im geprüften Entwurf nicht drücken ließ"),
    STILL_DRAFT("der Text danach noch im Entwurf stand"),
    NOT_CONFIRMED("ich danach keine gesendete Nachricht gesehen habe"),
    LEFT_APP("die App danach nicht mehr im Vordergrund war"),
    UNREADABLE("ich den Bildschirm danach nicht mehr lesen konnte"),
    ERROR_SHOWN("die App nach dem Senden einen Hinweis oder Fehler anzeigt"),
    ACCESSIBILITY_OFF("die Continuum-Bedienungshilfe in den Android-Einstellungen ausgeschaltet ist"),
    GRANT_REVOKED("die Freigabe zum selbstständigen Senden inzwischen ausgeschaltet wurde"),
    DAILY_CAP("die feste Tagesgrenze von ${OwnerSendGrants.DAILY_CAP} selbst gesendeten Nachrichten erreicht ist"),
    STOPPED("der Auftrag gestoppt wurde"),
    /** A step of a sequence: the journal already holds a send for it, or the intent could not be written down. */
    JOURNAL_REFUSED("das Senden für diese Auftragsfolge schon vermerkt ist oder sich nicht sicher vermerken ließ – ohne diesen Vermerk drücke ich nicht")
}

internal enum class SendEffect { CONFIRMED, PENDING, STILL_DRAFT, UNCLEAR, ERROR }

/**
 * On-screen verification before the click and effect check after it, for the WhatsApp chat and the Gmail compose
 * screen. Controls are located by view id where the apps have had stable ones, else by German/English label; never
 * by coordinates. Anything unexpected is a [SendVerification.Mismatch]: then nothing is pressed.
 */
internal object OwnerSendScreens {
    private val sendLabels = setOf("senden", "send")
    private val invisible = Regex("[\\u200b-\\u200f\\u202a-\\u202e\\u2066-\\u2069\\ufeff]")
    private val mailAddress = Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63})+")
    private val notOnWhatsApp = Regex("(?iu)nicht (?:bei|auf) whatsapp|isn['’]?t on whatsapp|not on whatsapp")
    private val gmailErrors = Regex("(?iu)empfänger hinzu|mindestens einen empfänger|ungültig|nicht gesendet|" +
        "fehler|add at least one recipient|invalid|not sent|error|couldn['’]?t send")

    fun norm(value: String): String = invisible.replace(value, "").replace(' ', ' ')
        .replace(Regex("\\s+"), " ").trim()

    private fun same(a: String, b: String) = norm(a).lowercase(Locale.GERMAN) == norm(b).lowercase(Locale.GERMAN)

    /** „senden“ / „send“ when the node's description or text is exactly a send label, else null. */
    fun sendLabel(node: SendNode): String? = listOf(node.description, node.text)
        .map { norm(it).lowercase(Locale.GERMAN) }.firstOrNull { it in sendLabels }

    private fun dialog(nodes: List<SendNode>) = nodes.any { it.viewId?.startsWith("android:id/button") == true ||
        it.viewId == "android:id/alertTitle" || it.viewId == "android:id/message" }

    fun locateSend(nodes: List<SendNode>): SendVerification {
        fun pressable(node: SendNode) = node.enabled && node.clickable && !node.editable && !node.password
        val byId = nodes.filter { it.idName == "send" && pressable(it) }
        if (byId.size == 1) return SendVerification.Verified(SendLocator(byId.single().viewId, null))
        if (byId.size > 1) return SendVerification.Mismatch(SendProblem.SEND_AMBIGUOUS)
        val byLabel = nodes.filter { pressable(it) && sendLabel(it) != null }
        return when (byLabel.size) {
            1 -> byLabel.single().let { node ->
                // A label hit with a view id that occurs once is pinned to that id; otherwise to the label itself.
                val id = node.viewId?.takeIf { id -> nodes.count { it.viewId == id } == 1 }
                SendVerification.Verified(SendLocator(id, if (id == null) sendLabel(node) else null))
            }
            0 -> SendVerification.Mismatch(SendProblem.SEND_NOT_FOUND, waiting = true)
            else -> SendVerification.Mismatch(SendProblem.SEND_AMBIGUOUS)
        }
    }

    // ── WhatsApp chat ───────────────────────────────────────────────────────────────────────────────────────

    private fun whatsAppEntry(nodes: List<SendNode>): SendNode? {
        val fields = nodes.filter { it.editable && !it.password }
        return fields.filter { it.idName == "entry" }.singleOrNull() ?: fields.singleOrNull()
    }

    /**
     * The 1:1 chat that was opened by number: header shows [contactName] (or the number itself when WhatsApp does
     * not know the contact), the entry field holds exactly [text], and one send control is present.
     */
    fun verifyWhatsApp(screen: SendScreen?, contactName: String, waDigits: String, text: String): SendVerification {
        if (screen == null || screen.packageName != SendChannel.WHATSAPP.packageName)
            return SendVerification.Mismatch(SendProblem.NOT_IN_FRONT, waiting = true)
        if (!screen.complete) return SendVerification.Mismatch(SendProblem.SCREEN_INCOMPLETE, waiting = true)
        val nodes = screen.nodes.filter { it.visible }
        if (nodes.any { notOnWhatsApp.containsMatchIn(it.text) }) return SendVerification.Mismatch(SendProblem.NOT_ON_WHATSAPP)
        if (dialog(nodes)) return SendVerification.Mismatch(SendProblem.DIALOG)
        val titles = nodes.filter { it.idName == "conversation_contact_name" && !it.editable }
        val title = titles.singleOrNull()?.let { it.text.ifBlank { it.description } }
            ?: return SendVerification.Mismatch(SendProblem.TITLE_NOT_FOUND, waiting = true)
        val titleIsNumber = norm(title).all { it.isDigit() || it in "+ ()-/" } && title.filter(Char::isDigit) == waDigits
        if (!same(title, contactName) && !titleIsNumber) return SendVerification.Mismatch(SendProblem.TITLE_MISMATCH)
        val entry = whatsAppEntry(nodes) ?: return SendVerification.Mismatch(SendProblem.FIELD_NOT_FOUND, waiting = true)
        if (norm(entry.content) != norm(text)) return SendVerification.Mismatch(SendProblem.TEXT_MISMATCH, waiting = true)
        return locateSend(nodes)
    }

    /** After the click: the entry field no longer holds [text] and a message bubble with it is visible. */
    fun whatsAppEffect(screen: SendScreen?, text: String): SendEffect {
        if (screen == null || screen.packageName != SendChannel.WHATSAPP.packageName || !screen.complete) return SendEffect.UNCLEAR
        val nodes = screen.nodes.filter { it.visible }
        if (dialog(nodes)) return SendEffect.ERROR
        val entry = whatsAppEntry(nodes) ?: return SendEffect.UNCLEAR
        val wanted = norm(text)
        if (norm(entry.content) == wanted) return SendEffect.STILL_DRAFT
        val head = wanted.take(80)
        val bubble = nodes.any { !it.editable && norm(it.text).let { shown ->
            shown == wanted || (wanted.length > 80 && shown.startsWith(head)) } }
        return if (bubble) SendEffect.CONFIRMED else SendEffect.PENDING
    }

    // ── Gmail compose ───────────────────────────────────────────────────────────────────────────────────────

    private fun gmailCompose(nodes: List<SendNode>): Boolean =
        nodes.any { it.idName == "subject" && it.editable } || nodes.any { it.idName == "send" }

    /** Addresses outside the sender line, the subject and the body (a signature may contain the owner's own). */
    private fun recipientAddresses(nodes: List<SendNode>): Set<String> = nodes.filter { node ->
        val id = node.idName?.lowercase(Locale.ROOT).orEmpty()
        listOf("from", "body", "subject", "compose_area", "composearea", "wc_", "signature").none { it in id }
    }.flatMap { node -> mailAddress.findAll(node.content + " " + node.description).map { it.value.lowercase(Locale.ROOT) } }.toSet()

    private fun bodyShown(nodes: List<SendNode>, body: String): Boolean {
        val texts = nodes.filter { it.idName != "subject" }.map { norm(it.content) }.filter { it.isNotEmpty() }
        val whole = norm(body)
        if (texts.any { it.contains(whole) }) return true
        // A rich-text editor may expose every paragraph as its own node.
        val paragraphs = body.split('\n').map(::norm).filter { it.isNotEmpty() }
        return paragraphs.size > 1 && paragraphs.all { paragraph -> texts.any { it.contains(paragraph) } }
    }

    fun verifyGmail(screen: SendScreen?, address: String, subject: String, body: String,
                    attachmentName: String?): SendVerification {
        if (screen == null || screen.packageName != SendChannel.GMAIL.packageName)
            return SendVerification.Mismatch(SendProblem.NOT_IN_FRONT, waiting = true)
        if (!screen.complete) return SendVerification.Mismatch(SendProblem.SCREEN_INCOMPLETE, waiting = true)
        val nodes = screen.nodes.filter { it.visible }
        if (dialog(nodes)) return SendVerification.Mismatch(SendProblem.DIALOG)
        if (!gmailCompose(nodes)) return SendVerification.Mismatch(SendProblem.NOT_COMPOSE, waiting = true)
        val recipients = recipientAddresses(nodes)
        val wanted = address.trim().lowercase(Locale.ROOT)
        if (wanted !in recipients) return SendVerification.Mismatch(SendProblem.RECIPIENT_MISSING, waiting = true)
        if (recipients.size != 1) return SendVerification.Mismatch(SendProblem.RECIPIENT_EXTRA)
        val subjectField = nodes.filter { it.idName == "subject" && it.editable }.singleOrNull()
            ?: nodes.filter { it.editable && norm(it.content) == norm(subject) }.singleOrNull()
        if (subjectField == null || norm(subjectField.content) != norm(subject))
            return SendVerification.Mismatch(SendProblem.SUBJECT_MISMATCH, waiting = true)
        if (body.isNotBlank() && !bodyShown(nodes, body)) return SendVerification.Mismatch(SendProblem.BODY_MISMATCH, waiting = true)
        if (attachmentName != null) {
            val name = norm(attachmentName).lowercase(Locale.GERMAN)
            if (nodes.none { norm(it.content + " " + it.description).lowercase(Locale.GERMAN).contains(name) })
                return SendVerification.Mismatch(SendProblem.ATTACHMENT_MISSING, waiting = true)
        }
        return locateSend(nodes)
    }

    /**
     * After the click: Gmail closes the compose screen. [screen] null = no Gmail window is on screen; that only counts
     * when [frontPackage] proves that another app is in front (an unreadable screen proves nothing).
     * A dialog or an error text on a still open compose screen is never counted as sent.
     */
    fun gmailEffect(screen: SendScreen?, frontPackage: String?): SendEffect {
        if (screen == null) return if (frontPackage != null && frontPackage != SendChannel.GMAIL.packageName)
            SendEffect.CONFIRMED else SendEffect.UNCLEAR
        if (screen.packageName != SendChannel.GMAIL.packageName || !screen.complete) return SendEffect.UNCLEAR
        val nodes = screen.nodes.filter { it.visible }
        if (dialog(nodes)) return SendEffect.ERROR
        if (!gmailCompose(nodes)) return SendEffect.CONFIRMED
        return if (nodes.any { !it.editable && gmailErrors.containsMatchIn(it.text) }) SendEffect.ERROR else SendEffect.PENDING
    }
}

/**
 * „Sende-Diagnose“: what a failed locator needs to be fixed from the owner's report – view ids, classes and the
 * labels of controls of the open WhatsApp/Gmail screen. No message text and no names: field contents are never
 * included, and a label is printed only when it is plain control vocabulary; everything else appears as its length.
 */
internal object OwnerSendDiagnostics {
    private val vocabulary = Regex("(?iu)^(?:senden|send|absenden|nachricht senden|send message|anhängen|anhang|attach\\w*|" +
        "kamera|camera|emoji\\w*|sticker|gif|sprachnachricht|voice message|sprachanruf|videoanruf|voice call|video call|" +
        "mehr optionen|more options|zurück|back|nach oben|navigate up|schließen|close|an|to|von|from|cc|bcc|betreff|subject|" +
        "nachricht|message|e-mail schreiben|compose email|entwurf|draft|datei anhängen|attach file|löschen|delete|" +
        "suchen|search|menü|menu|optionen|options|planen|schedule send|senden planen)$")
    const val MAX_LINES = 80

    private fun label(value: String): String {
        val plain = OwnerSendScreens.norm(value)
        return when {
            plain.isEmpty() -> "-"
            vocabulary.matches(plain) -> "„$plain“"
            else -> "(${plain.length} Zeichen, nicht aufgezeichnet)"
        }
    }

    fun describe(screen: SendScreen, appVersion: String?, takenAt: String): String = buildString {
        append("CONTINUUM SENDE-DIAGNOSE\n")
        append("App: ${screen.packageName} · Version: ${appVersion ?: "unbekannt"} · $takenAt\n")
        append("Vollständig gelesen: ${if (screen.complete) "ja" else "nein"} · Knoten: ${screen.nodes.size}\n")
        val visible = screen.nodes.filter { it.visible }
        val send = when (val found = OwnerSendScreens.locateSend(visible)) {
            is SendVerification.Verified -> "gefunden über " + (found.send.viewId?.let { "View-ID $it" } ?: "Beschriftung „${found.send.label}“")
            is SendVerification.Mismatch -> "NICHT gefunden (${found.reason.name})"
        }
        append("Senden-Knopf: $send\n")
        if (screen.packageName == SendChannel.WHATSAPP.packageName) {
            append("Chat-Titel (conversation_contact_name): ${if (visible.any { it.idName == "conversation_contact_name" }) "vorhanden" else "FEHLT"}\n")
            append("Nachrichtenfeld (entry): ${if (visible.any { it.idName == "entry" && it.editable }) "vorhanden" else "FEHLT"}\n")
        } else {
            append("Betreff-Feld (subject): ${if (visible.any { it.idName == "subject" && it.editable }) "vorhanden" else "FEHLT"}\n")
        }
        append("Bedienelemente (ID | Klasse | Beschriftung | Eigenschaften):\n")
        visible.filter { it.clickable || it.editable }.take(MAX_LINES).forEach { node ->
            val shown = if (node.editable || node.password) "(Eingabefeld, Inhalt nicht aufgezeichnet)"
                else label(node.description.ifBlank { node.text })
            append("${node.viewId ?: "-"} | ${node.className?.substringAfterLast('.') ?: "-"} | $shown | " +
                listOfNotNull("klickbar".takeIf { node.clickable }, "Eingabe".takeIf { node.editable },
                    "deaktiviert".takeIf { !node.enabled }).joinToString(",") + "\n")
        }
    }
}
