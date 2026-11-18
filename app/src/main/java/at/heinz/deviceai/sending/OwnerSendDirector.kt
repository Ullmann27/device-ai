package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.OwnerWorkGeneration
import at.heinz.deviceai.WhatsAppDraftPolicy
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.sending.OwnerSendRecipientResolver.Candidate
import at.heinz.deviceai.sending.OwnerSendRecipientResolver.Resolution

/** The mail order as the work tools understood it („schick das per Mail an …“), handed over for autonomous sending. */
internal data class OwnerMailHandover(val request: String, val address: String?, val recipientName: String?,
                                      val subject: String, val body: String, val attachment: OwnerSavedFile?)

/** One busy job with real cockpit steps; [generate] is used only when the owner asks Continuum to word a text. */
internal interface OwnerSendJob {
    fun step(key: String, label: String, state: StepState)
    fun generate(prompt: String): OwnerWorkGeneration
}

/** Everything the send lane needs from the Activity. Fakes in host tests, [AgentActivity] on the phone. */
internal interface OwnerSendHost {
    val sessionId: String
    /** Changes with every owner turn any lane accepted; an open question only counts for the very next one. */
    val ownerTurn: Long
    val assistantName: String?
    fun acceptOwnerTurn(request: String)
    fun reply(request: String, text: String)
    fun activity(line: String)
    fun contactsReadable(): Boolean
    /** Shows [explanation], then asks Android for READ_CONTACTS; [done] on the UI thread. */
    fun requestContacts(explanation: String, done: (Boolean) -> Unit)
    fun contacts(): OwnerSendContactSource
    fun device(): OwnerSendDevice
    fun grants(): OwnerSendGrants
    fun modelReady(): Boolean
    /** [work] off the UI thread while the app is busy, [done] back on it. */
    fun <T> runJob(request: String, work: (OwnerSendJob) -> T, done: (Result<T>) -> Unit)
    fun nowMillis(): Long
    /** Shows the diagnosis locally (dialog and app-private file); it is not put into the conversation. */
    fun showDiagnosis(report: String)
    fun appVersion(channel: SendChannel): String?
}

/**
 * The send lane of one chat: understands the owner's order, resolves the recipient, enforces the two fixed rules
 * and runs [OwnerSendFlow]. Free of Android, so the whole chain runs in host tests.
 *
 * Entry points are only the owner's own turn ([handle]) and the mail order the work tools parsed from the owner's
 * own turn ([mail]). There is no tool-registry entry: a model, a web page, a file, a notification or text read from
 * a screen has no way to start a send or to change a grant.
 *
 * Fixed rule (a): [TextOrigin.ownerWords] decides. Text a model wrote (or a generated result used as mail text) is
 * shown once ([Pending.Confirm]) and sent only after a clear yes. Fixed rule (b): [OwnerSendRecipientResolver]
 * returns one contact or a question; groups, several recipients and negated orders are refused by the parser;
 * one [OwnerSendFlow] per order presses at most once; [OwnerSendGrants.reserveSend] holds the daily limit.
 */
internal class OwnerSendDirector(private val host: OwnerSendHost) {
    private sealed interface Work {
        val request: String
        data class Chat(override val request: String, val order: OwnerSendOrder, val recipient: Candidate?,
                        val text: String?, val origin: TextOrigin) : Work
        data class Mail(override val request: String, val handover: OwnerMailHandover, val recipient: Candidate?,
                        val origin: TextOrigin) : Work
    }

    private sealed interface Pending {
        data class Recipient(val work: Work.Chat) : Pending
        data class Pick(val candidates: List<Candidate>, val work: Work) : Pending
        data class Wording(val work: Work.Chat) : Pending
        data class Confirm(val work: Work) : Pending
    }

    private class Open(val session: String, val turn: Long, val at: Long, val pending: Pending)
    private class Next(val text: String, val pending: Pending? = null, val activity: String? = null)

    private var open: Open? = null

    /** True while a question of this lane waits for the owner's next turn. */
    val awaitingAnswer: Boolean get() = open != null

    /**
     * true = [request] was a send order (or the answer to this lane's open question) and is completely handled.
     * Call on the UI thread, for the owner's own turn only, while no other job runs.
     */
    fun handle(request: String): Boolean {
        val waiting = open?.takeIf { it.session == host.sessionId && it.turn == host.ownerTurn &&
            host.nowMillis() - it.at in 0..PENDING_MILLIS }
        open = null
        val parsed = OwnerSendIntentParser.parse(request, host.assistantName)
        if (waiting != null && answer(request, waiting.pending, parsed != null)) return true
        if (DIAGNOSIS.matches(request.trim())) { diagnose(request); return true }
        return when (parsed) {
            null -> false
            is OwnerSendIntentParser.Result.Refused -> {
                host.acceptOwnerTurn(request)
                host.reply(request, parsed.message)
                true
            }
            is OwnerSendIntentParser.Result.Order -> {
                host.acceptOwnerTurn(request)
                start(Work.Chat(request, parsed.order, null, parsed.order.text, parsed.order.origin))
                true
            }
        }
    }

    /**
     * The work tools call this for „schick das per Mail an …“ after they accepted the owner turn. false = no standing
     * Gmail grant (or no Gmail): the existing hand-over to the mail app stays exactly as it was.
     */
    fun mail(handover: OwnerMailHandover): Boolean {
        if (!host.grants().enabled(SendChannel.GMAIL) || !host.device().installed(SendChannel.GMAIL)) return false
        open = null
        start(Work.Mail(handover.request, handover, null, TextOrigin.DERIVED))
        return true
    }

    private fun answer(request: String, pending: Pending, newOrder: Boolean): Boolean {
        if (OwnerSendIntentParser.isCancel(request)) {
            host.acceptOwnerTurn(request)
            host.reply(request, "In Ordnung. Es wurde nichts gesendet.")
            return true
        }
        // A new complete order replaces the open question instead of being read as its answer.
        if (newOrder) return false
        when (pending) {
            is Pending.Recipient -> {
                val name = OwnerSendIntentParser.recipientAnswer(request) ?: return false
                host.acceptOwnerTurn(request)
                start(pending.work.copy(order = pending.work.order.copy(recipient = name, unsplit = null)))
            }
            is Pending.Pick -> {
                val chosen = OwnerSendRecipientResolver.pick(request, pending.candidates) ?: return false
                host.acceptOwnerTurn(request)
                start(when (val work = pending.work) {
                    is Work.Chat -> work.copy(recipient = chosen)
                    is Work.Mail -> work.copy(recipient = chosen)
                })
            }
            is Pending.Wording -> {
                host.acceptOwnerTurn(request)
                val text = OwnerSendIntentParser.dictated(request)
                if (text == null) host.reply(request, OwnerSendIntentParser.REFUSED_TEXT)
                // A reply that reads like a question or order to Continuum is not silently taken as the message.
                else if (OwnerSendIntentParser.addressesContinuum(request)) start(pending.work.copy(text = text, origin = TextOrigin.DERIVED))
                else start(pending.work.copy(text = text, origin = TextOrigin.DICTATED))
            }
            is Pending.Confirm -> {
                if (!OwnerSendIntentParser.isYes(request)) return false
                host.acceptOwnerTurn(request)
                start(when (val work = pending.work) {
                    is Work.Chat -> work.copy(origin = TextOrigin.OWNER_CONFIRMED)
                    is Work.Mail -> work.copy(origin = TextOrigin.OWNER_CONFIRMED)
                })
            }
        }
        return true
    }

    private fun start(work: Work) {
        val request = work.request
        val channel = if (work is Work.Chat) SendChannel.WHATSAPP else SendChannel.GMAIL
        if (!host.device().installed(channel)) {
            host.reply(request, "${channel.label} ist auf diesem Gerät nicht installiert. Es wurde nichts vorbereitet und nichts gesendet.")
            return
        }
        if (work is Work.Chat && work.recipient == null && work.order.recipient == null && work.order.unsplit == null) {
            ask(request, "An wen soll die WhatsApp gehen? Nenn mir den Namen, wie er in deinen Kontakten steht.",
                Pending.Recipient(work))
            return
        }
        val needsContacts = when (work) {
            is Work.Chat -> work.recipient == null
            is Work.Mail -> work.recipient == null && work.handover.address == null
        }
        if (needsContacts && !host.contactsReadable()) {
            host.requestContacts(CONTACTS_EXPLANATION) { granted ->
                if (granted) run(work) else host.reply(request, CONTACTS_DENIED)
            }
            return
        }
        run(work)
    }

    private fun run(work: Work) {
        val request = work.request
        host.runJob(request, { job -> when (work) { is Work.Chat -> chat(work, job); is Work.Mail -> mail(work, job) } }) { result ->
            result.fold({ next ->
                next.activity?.let(host::activity)
                if (next.pending != null) ask(request, next.text, next.pending) else host.reply(request, next.text)
            }, { error ->
                host.reply(request, "Der Sende-Auftrag wurde durch einen Fehler angehalten (${(error.message ?: error.javaClass.simpleName).take(120)}). " +
                    "Bitte prüfe in der App, ob etwas gesendet wurde; ich wiederhole nichts.")
            })
        }
    }

    private fun ask(request: String, question: String, pending: Pending) {
        host.reply(request, question)
        open = Open(host.sessionId, host.ownerTurn, host.nowMillis(), pending)
    }

    // ── WhatsApp ────────────────────────────────────────────────────────────────────────────────────────────

    private fun chat(work: Work.Chat, job: OwnerSendJob): Next {
        var recipient = work.recipient
        var text = work.text
        var origin = work.origin
        var brief = work.order.modelBrief
        if (recipient == null) {
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt suchen", StepState.ACTIVE)
            val rows = host.contacts().phones()
            var phrase = work.order.recipient
            if (phrase == null) {
                val unsplit = checkNotNull(work.order.unsplit)
                val parts = OwnerSendRecipientResolver.split(unsplit, rows.map { it.displayName }.distinct())
                    ?: return notFound(job, unsplit.substringBefore(' '))
                val content = OwnerSendIntentParser.body(parts.second) ?: return Next(OwnerSendIntentParser.REFUSED_TEXT)
                phrase = parts.first; text = content.text; origin = content.origin; brief = content.modelBrief
            }
            val settled = work.copy(order = work.order.copy(recipient = phrase, unsplit = null, modelBrief = brief),
                text = text, origin = origin)
            when (val found = OwnerSendRecipientResolver.whatsapp(phrase, rows)) {
                is Resolution.Resolved -> recipient = found.candidate
                is Resolution.Ambiguous -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: welcher Kontakt", StepState.WAITING)
                    return Next(which(found), Pending.Pick(found.candidates, settled))
                }
                is Resolution.NotFound -> return notFound(job, found.asked)
                is Resolution.Unusable -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt ohne nutzbare Nummer", StepState.BLOCKED)
                    return Next(found.reason)
                }
            }
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt gefunden", StepState.DONE)
        }
        if (recipient.value.isEmpty()) return Next("Die Nummer von ${recipient.displayName} ist ohne Ländervorwahl gespeichert. " +
            "Trag sie bitte mit +43 … im Kontakt ein. Es wurde nichts gesendet.")
        val resolved = work.copy(recipient = recipient, text = text, origin = origin,
            order = work.order.copy(modelBrief = brief))
        val wantSend = host.grants().enabled(SendChannel.WHATSAPP) && !work.order.draftOnly
        if (text == null) {
            val wording = Pending.Wording(resolved.copy(order = resolved.order.copy(modelBrief = null)))
            if (brief == null) return Next("Was soll ich ${recipient.displayName} schreiben? Sag mir den Wortlaut.", wording)
            if (!host.modelReady()) return Next("Den Text müsste ich selbst formulieren, das Sprachmodell ist aber gerade " +
                "nicht bereit. Sag mir den Wortlaut für ${recipient.displayName}, dann übernehme ich ihn.", wording)
            job.step(STEP_COMPOSE, "Text formulieren", StepState.ACTIVE)
            val generated = job.generate(COMPOSE_PROMPT.replace("{wish}", brief))
            val written = generated.text.trim().trim('"', '„', '“', '”').trim().takeIf { generated.failure == null && it.length <= 600 }
                ?.let(WhatsAppDraftPolicy::validated)
            if (written == null) {
                job.step(STEP_COMPOSE, "Kein Text formuliert", StepState.FAILED)
                return Next("Ich konnte keinen passenden Text formulieren. Sag mir den Wortlaut für ${recipient.displayName}, " +
                    "dann übernehme ich ihn.", wording)
            }
            job.step(STEP_COMPOSE, "Text formuliert", StepState.DONE)
            text = written; origin = TextOrigin.DERIVED
        }
        // Fixed rule (a): text that is not the owner's own wording is shown once and waits for a clear yes.
        if (wantSend && !origin.ownerWords) return Next(
            "Ich würde ${recipient.label} auf WhatsApp schreiben:\n\n„$text“\n\nSoll ich das so senden?",
            Pending.Confirm(resolved.copy(text = text, origin = TextOrigin.DERIVED)))
        val plan = SendPlan.WhatsApp(recipient.displayName, recipient.value, text, wantSend)
        val outcome = OwnerSendFlow(host.device(), host.grants(), job::step).run(plan)
        return Next(OwnerSendReport.text(outcome, draftWanted = work.order.draftOnly), activity = activityLine(outcome))
    }

    // ── Gmail ───────────────────────────────────────────────────────────────────────────────────────────────

    private fun mail(work: Work.Mail, job: OwnerSendJob): Next {
        val order = work.handover
        var recipient = work.recipient ?: order.address?.let { Candidate(it, it, it) }
        if (recipient == null) {
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt suchen", StepState.ACTIVE)
            when (val found = OwnerSendRecipientResolver.mail(order.recipientName.orEmpty(), host.contacts().mails())) {
                is Resolution.Resolved -> recipient = found.candidate
                is Resolution.Ambiguous -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: welcher Kontakt", StepState.WAITING)
                    return Next(which(found), Pending.Pick(found.candidates, work))
                }
                is Resolution.NotFound -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt nicht gefunden", StepState.BLOCKED)
                    return Next("Ich habe keinen Kontakt „${found.asked}“ mit E-Mail-Adresse gefunden. Nenn mir die Adresse " +
                        "oder den Namen, wie er in deinen Kontakten steht. Es wurde nichts vorbereitet und nichts gesendet.")
                }
                is Resolution.Unusable -> return Next(found.reason)
            }
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt gefunden", StepState.DONE)
        }
        // A file the owner explicitly ordered to send goes out as it is, named by its own file name and without any
        // model-written text. Without a saved file the generated text itself would be the mail: shown once first.
        val file = order.attachment
        val subject = if (file != null) file.displayName.substringBeforeLast('.').ifBlank { file.displayName } else order.subject.take(200)
        val body = if (file != null) "" else order.body.take(MAX_MAIL_BODY)
        val origin = if (file != null) TextOrigin.DICTATED else work.origin
        if (!origin.ownerWords) return Next(
            "Ich würde an ${recipient.label} diese E-Mail über Gmail senden:\n\nBetreff: $subject\n\n" +
                body.trim().let { if (it.length > 600) it.take(600) + " … (insgesamt ${it.length} Zeichen)" else it } +
                "\n\nDen Text habe nicht du diktiert, deshalb frage ich einmal: Soll ich das so senden? " +
                "(Als gespeicherte Datei – „speichere das als PDF“ – geht es künftig ohne Rückfrage als Anhang hinaus.)",
            Pending.Confirm(work.copy(recipient = recipient, origin = TextOrigin.DERIVED)))
        val plan = SendPlan.Mail(recipient.value, recipient.displayName.takeIf { it != recipient.value }, subject, body, file, send = true)
        val outcome = OwnerSendFlow(host.device(), host.grants(), job::step).run(plan)
        return Next(OwnerSendReport.text(outcome), activity = activityLine(outcome))
    }

    private fun notFound(job: OwnerSendJob, asked: String): Next {
        job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt nicht gefunden", StepState.BLOCKED)
        return Next("Ich habe keinen Kontakt „$asked“ gefunden. Nenn mir den Namen so, wie er in deinen Kontakten steht. " +
            "Es wurde nichts vorbereitet und nichts gesendet.")
    }

    private fun which(found: Resolution.Ambiguous): String {
        val listed = found.candidates.joinToString(", ") { it.label }
        return if (found.total > found.candidates.size)
            "Zu „${found.asked}“ gibt es ${found.total} passende Kontakte, zum Beispiel $listed. Nenn mir bitte den vollen Namen."
        else "Zu „${found.asked}“ gibt es mehrere passende Kontakte: $listed. Welchen meinst du?"
    }

    private fun activityLine(outcome: SendOutcome): String = "SENDEN · ${outcome.plan.channel.label} · " + when (outcome.state) {
        SendOutcome.State.SENT -> "geprüft und gesendet"
        SendOutcome.State.DRAFT_READY -> "Entwurf geprüft · nicht gesendet"
        SendOutcome.State.DRAFT_UNVERIFIED -> "Entwurf geöffnet · nicht geprüft · nicht gesendet"
        SendOutcome.State.NOT_SENT -> "nicht gesendet · ${outcome.problem?.name}"
        SendOutcome.State.SEND_UNCONFIRMED -> "Senden gedrückt · nicht bestätigt · ${outcome.problem?.name}"
        SendOutcome.State.NOT_PREPARED -> "App nicht geöffnet"
    }

    // ── „Sende-Diagnose“ ────────────────────────────────────────────────────────────────────────────────────

    private fun diagnose(request: String) {
        host.acceptOwnerTurn(request)
        if (!host.device().accessibilityOn()) {
            host.reply(request, "Für die Sende-Diagnose muss die Continuum-Bedienungshilfe in den Android-Einstellungen eingeschaltet sein.")
            return
        }
        host.reply(request, DIAGNOSIS_START)
        host.runJob(request, { job -> record(job) }) { result ->
            val report = result.getOrNull()
            if (report == null) host.reply(request, "Ich habe in ${DIAGNOSIS_WAIT_MILLIS / 1000} Sekunden weder WhatsApp noch Gmail im Vordergrund gesehen. Es wurde nichts aufgezeichnet.")
            else {
                host.showDiagnosis(report)
                host.reply(request, "Die Sende-Diagnose ist aufgezeichnet und bleibt auf dem Gerät. Sie enthält nur Kennungen und " +
                    "Beschriftungen von Bedienelementen, keinen Nachrichtentext und keine Namen. Du findest sie unter Einstellungen → Selbst senden.")
            }
        }
    }

    private fun record(job: OwnerSendJob): String? {
        val device = host.device()
        job.step(STEP_DIAGNOSIS, "Auf WhatsApp oder Gmail warten", StepState.ACTIVE)
        val deadline = device.nowMillis() + DIAGNOSIS_WAIT_MILLIS
        while (device.nowMillis() < deadline) {
            val channel = SendChannel.entries.firstOrNull { device.screen(it) != null }
            if (channel != null) {
                // The owner gets a moment to type one character, so the send control is on screen.
                device.pause(DIAGNOSIS_SETTLE_MILLIS)
                val screen = device.screen(channel) ?: continue
                job.step(STEP_DIAGNOSIS, "Bedienelemente aufgezeichnet", StepState.DONE)
                return OwnerSendDiagnostics.describe(screen, host.appVersion(channel),
                    java.time.Instant.ofEpochMilli(device.nowMillis()).toString())
            }
            device.pause(500)
        }
        job.step(STEP_DIAGNOSIS, "Keine App gesehen", StepState.BLOCKED)
        return null
    }

    companion object {
        const val PENDING_MILLIS = 2 * 60_000L
        const val MAX_MAIL_BODY = 8_000
        const val DIAGNOSIS_WAIT_MILLIS = 30_000L
        const val DIAGNOSIS_SETTLE_MILLIS = 4_000L
        const val STEP_COMPOSE = "send-compose"
        const val STEP_DIAGNOSIS = "send-diagnosis"
        val DIAGNOSIS = Regex("(?iu)^(?:(?:bitte\\s+)?(?:starte?|mach(?:e)?|zeichne)\\s+(?:(?:eine|die)\\s+)?)?sende[- ]?diagnose" +
            "(?:\\s+(?:starten|machen|aufzeichnen|auf|bitte))*[.!]?$")
        const val DIAGNOSIS_START = "Sende-Diagnose läuft: Öffne jetzt innerhalb von 30 Sekunden den WhatsApp-Chat oder den " +
            "Gmail-Entwurf und tippe ein Zeichen ins Nachrichtenfeld, damit der Senden-Knopf erscheint. Ich zeichne nur " +
            "Kennungen und Beschriftungen von Bedienelementen auf – keinen Nachrichtentext, keine Namen – und sende nichts."
        const val CONTACTS_EXPLANATION = "Damit ich einem Namen die richtige Nummer oder E-Mail-Adresse zuordnen kann, brauche " +
            "ich Lesezugriff auf deine Kontakte. Ich lese sie nur auf diesem Gerät und nur für deine eigenen Sende-Aufträge; " +
            "sie werden nicht gespeichert und nicht weitergegeben. Android fragt dich gleich einmal danach."
        const val CONTACTS_DENIED = "Ohne Zugriff auf deine Kontakte kann ich dem Namen keine Nummer oder Adresse zuordnen. " +
            "Es wurde nichts vorbereitet und nichts gesendet. Du kannst den Zugriff später in den Android-Einstellungen erlauben."
        const val COMPOSE_PROMPT = "Formuliere eine kurze WhatsApp-Nachricht auf Deutsch in der Ich-Form, höchstens drei Sätze. " +
            "Wunsch des Absenders: „{wish}“. Gib nur den Nachrichtentext aus – ohne Anführungszeichen, ohne Erklärung."
    }
}
