package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
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
    /** The owner's confirmed relations („mein Vater“ = one contact). */
    fun relations(): OwnerRelations
    fun modelReady(): Boolean
    /** [work] off the UI thread while the app is busy, [done] back on it. */
    fun <T> runJob(request: String, work: (OwnerSendJob) -> T, done: (Result<T>) -> Unit)
    fun nowMillis(): Long
    /** Shows the diagnosis locally (dialog and app-private file); it is not put into the conversation. */
    fun showDiagnosis(report: String)
    fun appVersion(channel: SendChannel): String?
    /** How an order of this lane ended (see [OwnerLaneOutcome]); nobody has to listen. */
    fun outcome(outcome: OwnerLaneOutcome) {}
    /** A real step of the running order, for the stage. Called from the job's thread; nobody has to listen. */
    fun progress(event: OwnerSendProgress) {}
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
    /**
     * [confirm]: the owner sees recipient and text once and says yes before anything is sent, also with the standing
     * grant – the recipient came from a free answer after „not found“, from a relation that was just learnt or has
     * never been used to send, or the text was not separated from where a recipient could stand.
     * [learn]: the owner is naming the contact for this relation; after the resolution he is asked whether to remember it.
     * [via]: the confirmed relation this recipient was resolved through.
     */
    private sealed interface Work {
        val request: String
        val confirm: Boolean
        val learn: OwnerRelation?
        val via: OwnerRelation?
        /** [guard]: the order is a step of a sequence; it stays with the order through every question and answer. */
        val guard: OwnerSendGuard?
        data class Chat(override val request: String, val order: OwnerSendOrder, val recipient: Candidate?,
                        val text: String?, val origin: TextOrigin, override val confirm: Boolean = false,
                        override val learn: OwnerRelation? = null, override val via: OwnerRelation? = null,
                        override val guard: OwnerSendGuard? = null) : Work
        data class Mail(override val request: String, val handover: OwnerMailHandover, val recipient: Candidate?,
                        val origin: TextOrigin, override val confirm: Boolean = false,
                        override val learn: OwnerRelation? = null, override val via: OwnerRelation? = null,
                        override val guard: OwnerSendGuard? = null) : Work
    }

    private sealed interface Pending {
        val work: Work
        data class Recipient(override val work: Work.Chat) : Pending
        data class Pick(val candidates: List<Candidate>, override val work: Work) : Pending
        data class Wording(override val work: Work.Chat) : Pending
        data class Confirm(override val work: Work) : Pending
        /** „Wer ist dein Vater in den Kontakten?“ */
        data class Relation(override val work: Work, val relation: OwnerRelation) : Pending
        /** „Soll ich mir merken, dass Thomas Muster dein Vater ist?“ [work] already holds the resolved recipient. */
        data class Remember(override val work: Work, val relation: OwnerRelation, val candidate: Candidate) : Pending
    }

    private class Open(val session: String, val turn: Long, val at: Long, val pending: Pending, val question: String)
    /** [sent]: what the send flow really did, when it ran. */
    private class Next(val text: String, val pending: Pending? = null, val activity: String? = null, val sent: SendOutcome? = null,
                       val via: OwnerRelation? = null)

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
        if (waiting != null && answer(request, waiting, parsed != null)) return true
        if (DIAGNOSIS.matches(request.trim())) { diagnose(request); return true }
        OwnerRelation.forgetCommand(request)?.let { relation ->
            host.acceptOwnerTurn(request)
            host.reply(request, if (host.relations().forget(relation)) "In Ordnung, ich habe vergessen, wer ${relation.yours} ist. " +
                "Beim nächsten Mal frage ich wieder nach." else "Ich hatte mir nicht gemerkt, wer ${relation.yours} ist.")
            return true
        }
        return when (parsed) {
            null -> false
            is OwnerSendIntentParser.Result.Refused -> {
                host.acceptOwnerTurn(request)
                host.reply(request, parsed.message)
                host.outcome(OwnerLaneOutcome.Failed(parsed.message))
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
    fun mail(handover: OwnerMailHandover, guard: OwnerSendGuard? = null): Boolean {
        if (!host.grants().enabled(SendChannel.GMAIL) || !host.device().installed(SendChannel.GMAIL)) return false
        open = null
        start(Work.Mail(handover.request, handover, null, TextOrigin.DERIVED, guard = guard))
        return true
    }

    /**
     * The entry for a message that is a step of a sequence of owner orders (OwnerMissionDirector). [order] is the
     * owner's own clause; it is parsed here by this lane's parser and then takes exactly the way of an order he said
     * alone: the same recipient resolution (a confirmed relation or the question, the first send through a new
     * relation always confirmed), the same rule for wording that is not his own, the same standing grant, the same
     * verification on screen, the same daily limit and the same single press. Nothing of a sequence can widen that.
     *
     * [channelGiven]: the owner said „Öffne WhatsApp und …“ in front of the clause. [recipientOverride]: he corrected
     * the recipient of the planned step („nicht Papa, Mama“). [guard]: the sequence's journal, asked right before the
     * one press. true = taken (an outcome follows through the host); false = not a send order, nothing happened.
     */
    fun missionSend(order: String, channelGiven: Boolean = false, recipientOverride: String? = null,
                    guard: OwnerSendGuard? = null): Boolean {
        open = null
        when (val parsed = OwnerSendIntentParser.parse(order, host.assistantName, channelGiven)) {
            null -> return false
            is OwnerSendIntentParser.Result.Refused -> {
                host.acceptOwnerTurn(order)
                host.reply(order, parsed.message)
                host.outcome(OwnerLaneOutcome.Failed(parsed.message))
            }
            is OwnerSendIntentParser.Result.Order -> {
                var said = parsed.order
                if (recipientOverride != null) {
                    val unsplit = said.unsplit
                    said = if (unsplit == null) said.copy(recipient = recipientOverride) else {
                        // „meinem Vater guten Morgen“: the relation in front is replaced, the text behind it stays.
                        val rest = OwnerRelation.split(unsplit)?.third
                        val content = rest?.let(OwnerSendIntentParser::body)
                        if (content == null) {
                            val refused = "Bei dieser Nachricht kann ich den Empfänger nicht austauschen, weil Empfänger und Text " +
                                "nicht getrennt sind. Sag sie bitte noch einmal mit dem richtigen Empfänger. Es wurde nichts gesendet."
                            host.acceptOwnerTurn(order)
                            host.reply(order, refused)
                            host.outcome(OwnerLaneOutcome.Failed(refused))
                            return true
                        }
                        said.copy(recipient = recipientOverride, unsplit = null, text = content.text, origin = content.origin,
                            modelBrief = content.modelBrief, looseText = false)
                    }
                }
                host.acceptOwnerTurn(order)
                start(Work.Chat(order, said, null, said.text, said.origin, guard = guard))
            }
        }
        return true
    }

    /**
     * The answer the owner dictated and confirmed in the incoming-message conversation; [request] is his yes to the
     * repeated wording. Same lane and same rules as an order he spoke himself: [recipient] (the sender's name as the
     * notification showed it – data, never a command) is only ever matched against the contacts, so it resolves to
     * one address-book entry or to this lane's question; the standing grant decides between sending and a draft, the
     * screen is checked and the daily limit holds. false = not taken and nothing prepared (unusable text or name,
     * or Gmail without its grant – a mail is never handed to another app as a half-filled draft from here).
     */
    fun reply(request: String, channel: SendChannel, recipient: String, subject: String, text: String): Boolean {
        val words = OwnerSendIntentParser.dictated(text) ?: return false
        val name = recipient.replace(Regex("[^\\p{L}\\p{N}.'’ -]+"), " ").replace(Regex(" {2,}"), " ").trim()
        if (name.isEmpty() || name.length > 80 || name.none { it.isLetter() }) return false
        if (channel == SendChannel.GMAIL && (!host.grants().enabled(channel) || !host.device().installed(channel))) return false
        open = null
        host.acceptOwnerTurn(request)
        start(when (channel) {
            SendChannel.WHATSAPP -> Work.Chat(request, OwnerSendOrder(name, words, TextOrigin.OWNER_CONFIRMED, original = request),
                null, words, TextOrigin.OWNER_CONFIRMED)
            SendChannel.GMAIL -> Work.Mail(request, OwnerMailHandover(request, null, name, subject.take(200), words, null),
                null, TextOrigin.OWNER_CONFIRMED)
        })
        return true
    }

    private fun answer(request: String, waiting: Open, newOrder: Boolean): Boolean {
        val asked = waiting.pending
        // „nein“ to „Soll ich mir das merken?“ only means: do not remember it.
        if (asked is Pending.Remember) return remember(request, asked)
        if (OwnerSendIntentParser.isCancel(request)) {
            host.acceptOwnerTurn(request)
            host.reply(request, "In Ordnung. Es wurde nichts gesendet.")
            host.outcome(OwnerLaneOutcome.Stopped)
            return true
        }
        // A new complete order replaces the open question instead of being read as its answer.
        if (newOrder) return false
        val pending = waiting.pending
        if (pending !is Pending.Confirm && OwnerSendIntentParser.isChannelAnswer(request)) {
            host.acceptOwnerTurn(request)
            ask(request, waiting.question, pending)
            return true
        }
        // „Nicht Papa, Mama“: the owner names another recipient for the same message.
        if (pending !is Pending.Wording) OwnerSendIntentParser.correctedRecipient(request)?.let { name ->
            host.acceptOwnerTurn(request)
            start(retarget(pending.work, name))
            return true
        }
        when (pending) {
            is Pending.Recipient -> {
                val name = OwnerSendIntentParser.recipientAnswer(request) ?: return false
                host.acceptOwnerTurn(request)
                start(pending.work.copy(order = pending.work.order.copy(recipient = name, unsplit = null)))
            }
            is Pending.Relation -> {
                val name = OwnerSendIntentParser.recipientAnswer(request) ?: return false
                host.acceptOwnerTurn(request)
                // The owner's answer names a person; a relation named again is resolved as such, not learnt twice.
                val learn = pending.relation.takeIf { OwnerRelation.parse(name) == null }
                start(when (val work = pending.work) {
                    is Work.Chat -> work.copy(order = work.order.copy(recipient = name, unsplit = null), recipient = null,
                        learn = learn, via = null, confirm = true)
                    is Work.Mail -> work.copy(handover = work.handover.copy(recipientName = name, address = null), recipient = null,
                        learn = learn, via = null, confirm = true)
                })
            }
            is Pending.Remember -> return remember(request, pending)
            is Pending.Pick -> {
                val chosen = OwnerSendRecipientResolver.pick(request, pending.candidates)
                    ?: OwnerSendIntentParser.recipientAnswer(request)?.let {
                        OwnerSendRecipientResolver.pick(it, pending.candidates)
                    } ?: return false
                host.acceptOwnerTurn(request)
                start(when (val work = pending.work) {
                    is Work.Chat -> work.copy(recipient = chosen)
                    is Work.Mail -> work.copy(recipient = chosen)
                })
            }
            is Pending.Wording -> {
                host.acceptOwnerTurn(request)
                val text = OwnerSendIntentParser.dictated(request)
                if (text == null) {
                    host.reply(request, OwnerSendIntentParser.REFUSED_TEXT)
                    host.outcome(OwnerLaneOutcome.Failed(OwnerSendIntentParser.REFUSED_TEXT))
                }
                // A reply that reads like a question or order to Continuum is not silently taken as the message.
                else if (OwnerSendIntentParser.addressesContinuum(request)) start(pending.work.copy(text = text, origin = TextOrigin.DERIVED))
                else start(pending.work.copy(text = text, origin = TextOrigin.DICTATED))
            }
            is Pending.Confirm -> {
                if (!OwnerSendIntentParser.isYes(request)) return false
                host.acceptOwnerTurn(request)
                start(when (val work = pending.work) {
                    is Work.Chat -> work.copy(origin = TextOrigin.OWNER_CONFIRMED, confirm = false)
                    is Work.Mail -> work.copy(origin = TextOrigin.OWNER_CONFIRMED, confirm = false)
                })
            }
        }
        return true
    }

    /** The answer to „Soll ich mir merken, dass … ist?“: only a clear yes stores the relation. Either way nothing is sent yet. */
    private fun remember(request: String, pending: Pending.Remember): Boolean {
        val relation = pending.relation
        val candidate = pending.candidate
        val work = pending.work
        when {
            OwnerSendIntentParser.isYes(request) -> {
                host.acceptOwnerTurn(request)
                val id = candidate.contactId
                if (id == null) {
                    host.reply(request, "Diesen Empfänger kann ich mir nicht als ${relation.yours} merken, weil er nicht in deinen Kontakten steht.")
                    start(work)
                } else {
                    host.relations().remember(relation, id, candidate.displayName)
                    host.activity("BEZIEHUNG · ${relation.yours} gemerkt · vom Besitzer bestätigt")
                    host.reply(request, "Gut, ich merke mir: ${candidate.displayName} ist ${relation.yours}. Mit „Vergiss, wer " +
                        "${relation.mine} ist“ lösche ich das wieder.")
                    start(when (work) {
                        is Work.Chat -> work.copy(via = relation)
                        is Work.Mail -> work.copy(via = relation)
                    })
                }
            }
            REMEMBER_NO.matches(request.trim()) -> {
                host.acceptOwnerTurn(request)
                host.reply(request, "In Ordnung, das merke ich mir nicht.")
                start(work)
            }
            OwnerSendIntentParser.isCancel(request) -> {
                host.acceptOwnerTurn(request)
                host.reply(request, "In Ordnung. Es wurde nichts gesendet.")
                host.outcome(OwnerLaneOutcome.Stopped)
            }
            else -> return false
        }
        return true
    }

    /** The same message to the recipient the owner named instead; nothing learnt or resolved before carries over. */
    private fun retarget(work: Work, name: String): Work = when (work) {
        is Work.Chat -> work.copy(order = work.order.copy(recipient = name, unsplit = null), recipient = null, learn = null, via = null)
        is Work.Mail -> work.copy(handover = work.handover.copy(recipientName = name, address = null), recipient = null,
            learn = null, via = null)
    }

    private fun start(work: Work) {
        val request = work.request
        val channel = if (work is Work.Chat) SendChannel.WHATSAPP else SendChannel.GMAIL
        if (!host.device().installed(channel)) {
            host.reply(request, "${channel.label} ist auf diesem Gerät nicht installiert. Es wurde nichts vorbereitet und nichts gesendet.")
            host.outcome(OwnerLaneOutcome.Failed("${channel.label} ist auf diesem Gerät nicht installiert. Es wurde nichts gesendet."))
            return
        }
        if (work is Work.Chat && work.recipient == null && work.order.recipient == null && work.order.unsplit == null) {
            ask(request, ASK_RECIPIENT, Pending.Recipient(work))
            return
        }
        val needsContacts = when (work) {
            is Work.Chat -> work.recipient == null
            is Work.Mail -> work.recipient == null && work.handover.address == null
        }
        if (needsContacts && !host.contactsReadable()) {
            host.requestContacts(CONTACTS_EXPLANATION) { granted ->
                if (granted) run(work) else {
                    host.reply(request, CONTACTS_DENIED)
                    host.outcome(OwnerLaneOutcome.Failed(CONTACTS_DENIED))
                }
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
                // The first confirmed send through a newly learnt relation: from now on it counts like a name.
                if (next.sent?.state == SendOutcome.State.SENT) next.via?.let { host.relations().markSent(it) }
                if (next.pending != null) ask(request, next.text, next.pending) else {
                    host.reply(request, next.text)
                    host.outcome(settled(next))
                }
            }, { error ->
                val said = "Der Sende-Auftrag wurde durch einen Fehler angehalten (${(error.message ?: error.javaClass.simpleName).take(120)}). " +
                    "Bitte prüfe in der App, ob etwas gesendet wurde; ich wiederhole nichts."
                host.reply(request, said)
                host.outcome(OwnerLaneOutcome.Failed(said))
            })
        }
    }

    private fun ask(request: String, question: String, pending: Pending) {
        host.reply(request, question)
        open = Open(host.sessionId, host.ownerTurn, host.nowMillis(), pending, question)
        host.outcome(OwnerLaneOutcome.NeedsOwner(when (pending) {
            is Pending.Pick -> OwnerLaneOutcome.Need.CHOICE
            is Pending.Confirm -> OwnerLaneOutcome.Need.CONFIRMATION
            is Pending.Recipient, is Pending.Wording, is Pending.Relation -> OwnerLaneOutcome.Need.DETAIL
            is Pending.Remember -> OwnerLaneOutcome.Need.CONFIRMATION
        }, question))
    }

    /** The typed end of an order that asked nothing more: sent, a verified draft, or not done with the lane's reason. */
    private fun settled(next: Next): OwnerLaneOutcome {
        val sent = next.sent ?: return OwnerLaneOutcome.Failed(next.text)
        val state = when (sent.state) {
            SendOutcome.State.SENT -> OwnerLaneResult.Delivered.State.SENT
            SendOutcome.State.DRAFT_READY -> OwnerLaneResult.Delivered.State.DRAFT
            else -> return OwnerLaneOutcome.Failed(next.text)
        }
        val plan = sent.plan
        return OwnerLaneOutcome.Done(OwnerLaneResult.Delivered(plan.channel.label, plan.recipientLabel,
            (plan as? SendPlan.Mail)?.attachment, state))
    }

    // ── WhatsApp ────────────────────────────────────────────────────────────────────────────────────────────

    private fun chat(work: Work.Chat, job: OwnerSendJob): Next {
        var recipient = work.recipient
        var text = work.text
        var origin = work.origin
        var brief = work.order.modelBrief
        var confirm = work.confirm
        var via = work.via
        if (recipient == null) {
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt suchen", StepState.ACTIVE)
            val rows = host.contacts().phones()
            var phrase = work.order.recipient
            if (phrase == null) {
                val unsplit = checkNotNull(work.order.unsplit)
                // A relation in front („meinem Chef, ich komme später“) is never split by name tokens of contacts.
                val parts = OwnerRelation.split(unsplit)?.let { it.second to it.third }
                    ?: OwnerSendRecipientResolver.split(unsplit, rows.map { it.displayName }.distinct())
                    ?: OwnerSendIntentParser.splitFamily(unsplit)
                if (parts == null) {
                    if (!work.order.looseText) return notFound(job, unsplit.substringBefore(' '))
                    // Nobody is named in front: the whole tail is the text. Who gets it is asked, and recipient and
                    // wording are shown once before anything is sent – recipient words never slip into the message.
                    val content = OwnerSendIntentParser.body(unsplit) ?: return Next(OwnerSendIntentParser.REFUSED_TEXT)
                    job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: an wen", StepState.WAITING)
                    return Next(ASK_RECIPIENT, Pending.Recipient(work.copy(order = work.order.copy(unsplit = null, looseText = false,
                        modelBrief = content.modelBrief), text = content.text, origin = content.origin, confirm = true)))
                }
                if (OwnerSendIntentParser.startsWithNegation(parts.second)) return Next(OwnerSendIntentParser.REFUSED_NEGATED)
                if (OwnerSendIntentParser.hasAdditionalRecipient(parts.second)) {
                    return Next(OwnerSendIntentParser.REFUSED_SEVERAL)
                }
                val content = OwnerSendIntentParser.body(parts.second) ?: return Next(OwnerSendIntentParser.REFUSED_TEXT)
                phrase = parts.first; text = content.text; origin = content.origin; brief = content.modelBrief
            }
            val settled = work.copy(order = work.order.copy(recipient = phrase, unsplit = null, modelBrief = brief),
                text = text, origin = origin)
            val relation = OwnerRelation.parse(phrase).takeIf { work.learn == null }
            val stored = relation?.let { host.relations().get(it) }
            val found = if (stored != null) OwnerSendRecipientResolver.whatsappContact(phrase, stored.contactId, rows)
                else OwnerSendRecipientResolver.whatsapp(phrase, rows)
            when (found) {
                is Resolution.Resolved -> {
                    recipient = found.candidate
                    if (stored != null) { via = relation; if (!stored.firstSendDone) confirm = true }
                }
                is Resolution.Ambiguous -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: welcher Kontakt", StepState.WAITING)
                    return Next(which(found), Pending.Pick(found.candidates, settled))
                }
                is Resolution.NotFound -> return if (relation != null) relationQuestion(job, found.asked, relation, stored, settled, mail = false)
                    else notFound(job, found.asked, settled)
                is Resolution.Unusable -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt ohne nutzbare Nummer", StepState.BLOCKED)
                    return Next(found.reason)
                }
            }
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt gefunden", StepState.DONE)
        }
        host.progress(OwnerSendProgress.RecipientResolved(recipient.displayName))
        if (recipient.value.isEmpty()) return Next("Die Nummer von ${recipient.displayName} ist ohne Ländervorwahl gespeichert. " +
            "Trag sie bitte mit +43 … im Kontakt ein. Es wurde nichts gesendet.")
        val resolved = work.copy(recipient = recipient, text = text, origin = origin,
            order = work.order.copy(modelBrief = brief), confirm = confirm, via = via)
        work.learn?.let { learn -> rememberQuestion(job, learn, recipient, resolved.copy(learn = null))?.let { return it } }
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
        // Fixed rule (a): text that is not the owner's own wording is shown once and waits for a clear yes. The same
        // single question when the recipient or the separation of the text is not yet confirmed (see [Work.confirm]).
        if (wantSend && (!origin.ownerWords || confirm)) return Next(
            "Ich würde ${recipient.label} auf WhatsApp schreiben:\n\n„$text“\n\nSoll ich das so senden?",
            Pending.Confirm(resolved.copy(text = text, origin = if (origin.ownerWords) origin else TextOrigin.DERIVED, confirm = true)))
        val plan = SendPlan.WhatsApp(recipient.displayName, recipient.value, text, wantSend)
        host.progress(OwnerSendProgress.ChannelChosen(SendChannel.WHATSAPP.label))
        val outcome = OwnerSendFlow(host.device(), host.grants(), job::step, guard = work.guard, progress = host::progress).run(plan)
        return Next(OwnerSendReport.text(outcome, draftWanted = work.order.draftOnly), activity = activityLine(outcome), sent = outcome,
            via = via)
    }

    // ── Gmail ───────────────────────────────────────────────────────────────────────────────────────────────

    private fun mail(work: Work.Mail, job: OwnerSendJob): Next {
        val order = work.handover
        var recipient = work.recipient ?: order.address?.let { Candidate(it, it, it) }
        var confirm = work.confirm
        var via = work.via
        if (recipient == null) {
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt suchen", StepState.ACTIVE)
            val phrase = order.recipientName.orEmpty()
            val relation = OwnerRelation.parse(phrase).takeIf { work.learn == null }
            val stored = relation?.let { host.relations().get(it) }
            val rows = host.contacts().mails()
            val found = if (stored != null) OwnerSendRecipientResolver.mailContact(phrase, stored.contactId, rows)
                else OwnerSendRecipientResolver.mail(phrase, rows)
            when (found) {
                is Resolution.Resolved -> {
                    recipient = found.candidate
                    if (stored != null) { via = relation; if (!stored.firstSendDone) confirm = true }
                }
                is Resolution.Ambiguous -> {
                    job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: welcher Kontakt", StepState.WAITING)
                    return Next(which(found), Pending.Pick(found.candidates, work))
                }
                is Resolution.NotFound -> {
                    if (relation != null) return relationQuestion(job, found.asked, relation, stored, work, mail = true)
                    job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt nicht gefunden", StepState.BLOCKED)
                    return Next("Ich habe keinen Kontakt „${found.asked}“ mit E-Mail-Adresse gefunden. Nenn mir die Adresse " +
                        "oder den Namen, wie er in deinen Kontakten steht. Es wurde nichts vorbereitet und nichts gesendet.")
                }
                is Resolution.Unusable -> return Next(found.reason)
            }
            job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt gefunden", StepState.DONE)
        }
        // The stage shows who it goes to by the contact's name; a bare address is not shown there.
        recipient.displayName.takeIf { it != recipient.value }?.let { host.progress(OwnerSendProgress.RecipientResolved(it)) }
        // A file the owner explicitly ordered to send goes out as it is, named by its own file name and without any
        // model-written text. Without a saved file the generated text itself would be the mail: shown once first.
        val file = order.attachment
        val subject = if (file != null) file.displayName.substringBeforeLast('.').ifBlank { file.displayName } else order.subject.take(200)
        val body = if (file != null) "" else order.body.take(MAX_MAIL_BODY)
        val origin = if (file != null) TextOrigin.DICTATED else work.origin
        val settled = work.copy(recipient = recipient, confirm = confirm, via = via)
        work.learn?.let { learn -> rememberQuestion(job, learn, recipient, settled.copy(learn = null))?.let { return it } }
        if (origin.ownerWords && confirm) return Next(
            "Ich würde an ${recipient.label} über Gmail senden: Betreff „$subject“" +
                (if (file != null) ", Anhang „${file.displayName}“" else "") + ". Soll ich das so senden?",
            Pending.Confirm(settled))
        if (!origin.ownerWords) return Next(
            "Ich würde an ${recipient.label} diese E-Mail über Gmail senden:\n\nBetreff: $subject\n\n" +
                body.trim().let { if (it.length > 600) it.take(600) + " … (insgesamt ${it.length} Zeichen)" else it } +
                "\n\nDen Text habe nicht du diktiert, deshalb frage ich einmal: Soll ich das so senden? " +
                "(Als gespeicherte Datei – „speichere das als PDF“ – geht es künftig ohne Rückfrage als Anhang hinaus.)",
            Pending.Confirm(settled.copy(origin = TextOrigin.DERIVED, confirm = true)))
        val plan = SendPlan.Mail(recipient.value, recipient.displayName.takeIf { it != recipient.value }, subject, body, file, send = true)
        host.progress(OwnerSendProgress.ChannelChosen(SendChannel.GMAIL.label))
        val outcome = OwnerSendFlow(host.device(), host.grants(), job::step, guard = work.guard, progress = host::progress).run(plan)
        return Next(OwnerSendReport.text(outcome), activity = activityLine(outcome), sent = outcome, via = via)
    }

    /**
     * The name was not found. The owner may answer with the name as it stands in his contacts; because that answer is
     * only a few free words, the resolved contact and the text are shown once before anything is sent.
     */
    private fun notFound(job: OwnerSendJob, asked: String, work: Work.Chat? = null): Next {
        job.step(OwnerSendFlow.STEP_CONTACT, "Kontakt nicht gefunden", StepState.BLOCKED)
        return Next("Ich habe keinen Kontakt „$asked“ gefunden. Nenn mir den Namen so, wie er in deinen Kontakten steht. " +
            "Es wurde nichts vorbereitet und nichts gesendet.", work?.let { Pending.Recipient(it.copy(confirm = true)) })
    }

    /** „Wer ist dein Vater in den Kontakten?“ – a relation is never guessed from contact names. */
    private fun relationQuestion(job: OwnerSendJob, asked: String, relation: OwnerRelation, stored: OwnerRelationEntry?,
                                 work: Work, mail: Boolean): Next {
        job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: wer ist ${relation.yours}", StepState.WAITING)
        val lead = if (stored != null) "„${stored.displayName}“, den ich mir als ${relation.yours} gemerkt habe, finde ich " +
            (if (mail) "mit E-Mail-Adresse " else "") + "nicht mehr in deinen Kontakten."
            else "Ich habe keinen Kontakt „$asked“" + (if (mail) " mit E-Mail-Adresse" else "") + " gefunden."
        return Next("$lead ${relation.whoIs} in den Kontakten? Nenn mir den Namen, wie er dort steht. " +
            "Es wurde nichts vorbereitet und nichts gesendet.", Pending.Relation(work, relation))
    }

    /** After the owner named the contact for a relation: „Soll ich mir merken, dass … ist?“ Null when it cannot be stored. */
    private fun rememberQuestion(job: OwnerSendJob, relation: OwnerRelation, candidate: Candidate, work: Work): Next? {
        if (candidate.contactId == null) return null
        job.step(OwnerSendFlow.STEP_CONTACT, "Rückfrage: merken", StepState.WAITING)
        return Next("Soll ich mir merken, dass ${candidate.displayName} ${relation.yours} ist?",
            Pending.Remember(work, relation, candidate))
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
        const val ASK_RECIPIENT = "An wen soll die WhatsApp gehen? Nenn mir den Namen, wie er in deinen Kontakten steht."
        private val REMEMBER_NO = Regex("(?iu)^(?:nein|nö|nee|ne|nein danke|lieber nicht|nicht merken|merk dir das nicht|brauchst du nicht)[.!]?$")
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
