package at.heinz.deviceai.sending

import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.cockpit.StepState
import java.util.concurrent.atomic.AtomicBoolean

/** Exactly one outgoing message. [send] false = prepare the draft only (no grant, or the owner asked for a draft). */
internal sealed interface SendPlan {
    val channel: SendChannel
    val send: Boolean
    /** How the recipient is named in the report. */
    val recipientLabel: String

    data class WhatsApp(val contactName: String, val waDigits: String, val text: String,
                        override val send: Boolean) : SendPlan {
        override val channel get() = SendChannel.WHATSAPP
        override val recipientLabel get() = contactName
    }

    data class Mail(val address: String, val recipientName: String?, val subject: String, val body: String,
                    val attachment: OwnerSavedFile?, override val send: Boolean) : SendPlan {
        override val channel get() = SendChannel.GMAIL
        override val recipientLabel get() = recipientName?.let { "$it ($address)" } ?: address
    }
}

/**
 * The device boundary of the send flow: an intent that opens the prefilled chat/compose screen, the app's existing
 * accessibility service for reading the front window and pressing one located control, and the way back.
 * A fake in host tests; [AndroidOwnerSendDevice] on the phone.
 */
internal interface OwnerSendDevice {
    fun installed(channel: SendChannel): Boolean
    fun accessibilityOn(): Boolean
    /** Opens the target with everything prefilled. Null = started, otherwise an owner-facing reason. */
    fun open(plan: SendPlan): String?
    /** The front application window when it belongs to [channel]'s app, else null. */
    fun screen(channel: SendChannel): SendScreen?
    /** Package of the active application window; null when the screen cannot be observed. */
    fun frontPackage(): String?
    /**
     * Reads the window of [channel]'s app once, requires [stillVerified] on exactly that reading and exactly one
     * control matching [locator] in it, and clicks that control once. False = nothing was clicked.
     */
    fun press(channel: SendChannel, locator: SendLocator, stillVerified: (SendScreen) -> Boolean): Boolean
    fun returnToContinuum()
    fun pause(millis: Long)
    fun nowMillis(): Long
}

internal data class SendOutcome(val plan: SendPlan, val state: State, val problem: SendProblem? = null,
                                val detail: String? = null) {
    enum class State {
        /** Pressed once and the effect was confirmed on screen. */
        SENT,
        /** Draft prepared and verified on screen; by design not sent. */
        DRAFT_READY,
        /** Target opened with the draft, but it could not be verified; not sent. */
        DRAFT_UNVERIFIED,
        /** A send was wanted, but nothing was pressed. The draft stays. */
        NOT_SENT,
        /** Pressed once, effect not confirmed. Never pressed again. */
        SEND_UNCONFIRMED,
        /** The target could not be opened at all. */
        NOT_PREPARED
    }
}

/**
 * „Prepare by intent, press send through the accessibility service, verify, report“ – one instance, one message.
 *
 * Nothing is pressed unless the front window of the right app shows the prepared recipient and text (twice: when it
 * becomes ready and again on a fresh observation right before the click), the standing grant is still on and the
 * daily limit has room. The send control is pressed at most once; an unclear effect is reported, never repeated.
 * No model is involved anywhere in this class.
 */
internal class OwnerSendFlow(
    private val device: OwnerSendDevice,
    private val grants: OwnerSendGrants,
    private val step: (key: String, label: String, state: StepState) -> Unit = { _, _, _ -> },
    private val cancelled: () -> Boolean = { false }
) {
    private val used = AtomicBoolean(false)

    fun run(plan: SendPlan): SendOutcome {
        check(used.compareAndSet(false, true)) { "ONE_MESSAGE_PER_ORDER" }
        val app = plan.channel.label
        step(STEP_OPEN, "$app öffnen", StepState.ACTIVE)
        device.open(plan)?.let { problem ->
            step(STEP_OPEN, "$app nicht geöffnet", StepState.FAILED)
            return SendOutcome(plan, SendOutcome.State.NOT_PREPARED, detail = problem)
        }
        step(STEP_OPEN, "$app geöffnet", StepState.DONE)
        fun unsent(problem: SendProblem) = SendOutcome(plan,
            if (plan.send) SendOutcome.State.NOT_SENT else SendOutcome.State.DRAFT_UNVERIFIED, problem)
        if (!device.accessibilityOn()) {
            step(STEP_VERIFY, "Entwurf nicht geprüft", StepState.UNVERIFIED)
            return unsent(SendProblem.ACCESSIBILITY_OFF)
        }
        step(STEP_VERIFY, "Entwurf prüfen", StepState.ACTIVE)
        val ready = awaitVerified(plan)
        if (ready !is SendVerification.Verified) {
            step(STEP_VERIFY, "Entwurf nicht bestätigt", StepState.UNVERIFIED)
            return unsent((ready as SendVerification.Mismatch).reason)
        }
        step(STEP_VERIFY, "Entwurf geprüft", StepState.DONE)
        if (!plan.send) return SendOutcome(plan, SendOutcome.State.DRAFT_READY)
        fun notSent(problem: SendProblem): SendOutcome {
            step(STEP_PRESS, "Nicht gesendet", StepState.BLOCKED)
            return SendOutcome(plan, SendOutcome.State.NOT_SENT, problem)
        }
        if (cancelled()) return notSent(SendProblem.STOPPED)
        // A fresh observation right before the click must show the same verified draft and the same control.
        val again = verify(plan, device.screen(plan.channel))
        if (again !is SendVerification.Verified || again.send != ready.send)
            return notSent((again as? SendVerification.Mismatch)?.reason ?: SendProblem.SEND_AMBIGUOUS)
        if (!grants.reserveSend(plan.channel))
            return notSent(if (grants.enabled(plan.channel)) SendProblem.DAILY_CAP else SendProblem.GRANT_REVOKED)
        step(STEP_PRESS, "Senden", StepState.ACTIVE)
        // The click itself is bound to one more reading: same draft, same control, in the tree that is clicked.
        val pressed = device.press(plan.channel, again.send) { shown ->
            verify(plan, shown).let { it is SendVerification.Verified && it.send == again.send }
        }
        if (!pressed) return notSent(SendProblem.CLICK_REFUSED)
        val effect = awaitEffect(plan)
        if (effect == null) {
            step(STEP_PRESS, "Gesendet", StepState.DONE)
            device.returnToContinuum()
            // Continuum needs a moment to be in front again before the report is shown and spoken.
            device.pause(RETURN_SETTLE_MILLIS)
            return SendOutcome(plan, SendOutcome.State.SENT)
        }
        step(STEP_PRESS, "Senden nicht bestätigt", StepState.UNVERIFIED)
        return SendOutcome(plan, SendOutcome.State.SEND_UNCONFIRMED, effect)
    }

    private fun verify(plan: SendPlan, screen: SendScreen?): SendVerification = when (plan) {
        is SendPlan.WhatsApp -> OwnerSendScreens.verifyWhatsApp(screen, plan.contactName, plan.waDigits, plan.text)
        is SendPlan.Mail -> OwnerSendScreens.verifyGmail(screen, plan.address, plan.subject, plan.body,
            plan.attachment?.displayName)
    }

    /** Bounded wait for the compose screen. A final mismatch (wrong chat, dialog) must be seen twice in a row. */
    private fun awaitVerified(plan: SendPlan): SendVerification {
        val deadline = device.nowMillis() + VERIFY_MILLIS
        var previous: SendProblem? = null
        while (true) {
            val seen = verify(plan, device.screen(plan.channel))
            if (seen is SendVerification.Verified) return seen
            seen as SendVerification.Mismatch
            if (!seen.waiting && previous == seen.reason) return seen
            previous = seen.reason.takeIf { !seen.waiting }
            if (cancelled() || device.nowMillis() >= deadline) return seen
            device.pause(POLL_MILLIS)
        }
    }

    /** Null = confirmed. Otherwise why the effect of the one click could not be confirmed within the bound. */
    private fun awaitEffect(plan: SendPlan): SendProblem? {
        val deadline = device.nowMillis() + EFFECT_MILLIS
        var previous: SendEffect? = null
        while (true) {
            device.pause(POLL_MILLIS)
            val screen = device.screen(plan.channel)
            val effect = when (plan) {
                is SendPlan.WhatsApp -> OwnerSendScreens.whatsAppEffect(screen, plan.text)
                is SendPlan.Mail -> OwnerSendScreens.gmailEffect(screen, device.frontPackage())
            }
            // WhatsApp shows the bubble itself; a closed Gmail compose screen and an error must be seen twice.
            if (effect == SendEffect.CONFIRMED && (plan is SendPlan.WhatsApp || previous == SendEffect.CONFIRMED)) return null
            if (effect == SendEffect.ERROR && previous == SendEffect.ERROR)
                return if (plan is SendPlan.Mail) SendProblem.ERROR_SHOWN else SendProblem.DIALOG
            if (device.nowMillis() >= deadline) return when (effect) {
                SendEffect.STILL_DRAFT -> SendProblem.STILL_DRAFT
                SendEffect.PENDING -> if (plan is SendPlan.Mail) SendProblem.STILL_DRAFT else SendProblem.NOT_CONFIRMED
                SendEffect.UNCLEAR -> if (plan is SendPlan.Mail) SendProblem.UNREADABLE else SendProblem.LEFT_APP
                SendEffect.ERROR -> if (plan is SendPlan.Mail) SendProblem.ERROR_SHOWN else SendProblem.DIALOG
                SendEffect.CONFIRMED -> SendProblem.NOT_CONFIRMED
            }
            previous = effect
        }
    }

    companion object {
        const val VERIFY_MILLIS = 8_000L
        const val EFFECT_MILLIS = 6_000L
        const val POLL_MILLIS = 300L
        const val RETURN_SETTLE_MILLIS = 700L
        const val STEP_CONTACT = "send-contact"
        const val STEP_OPEN = "send-open"
        const val STEP_VERIFY = "send-verify"
        const val STEP_PRESS = "send-press"
    }
}

/** The report, worded like a colleague. States exactly what was confirmed and never more. */
internal object OwnerSendReport {
    private fun quote(text: String): String = "„" + text.trim().let { if (it.length > 300) it.take(300) + " …" else it } + "“"

    private fun subject(plan: SendPlan): String = when (plan) {
        is SendPlan.WhatsApp -> quote(plan.text)
        is SendPlan.Mail -> "Betreff ${quote(plan.subject)}" +
            (plan.attachment?.let { ", Anhang „${it.displayName}“" } ?: ", ohne Anhang") +
            if (plan.body.isBlank()) ", ohne Begleittext" else ""
    }

    private val locatorProblems = setOf(SendProblem.TITLE_NOT_FOUND, SendProblem.FIELD_NOT_FOUND, SendProblem.SEND_NOT_FOUND,
        SendProblem.SEND_AMBIGUOUS, SendProblem.RECIPIENT_MISSING, SendProblem.NOT_COMPOSE, SendProblem.CLICK_REFUSED)

    fun text(outcome: SendOutcome, draftWanted: Boolean = false): String {
        val plan = outcome.plan
        val app = plan.channel.label
        val to = plan.recipientLabel
        val why = outcome.problem?.text
        val diagnosis = if (outcome.problem in locatorProblems)
            " Wenn das wieder passiert, sag „Sende-Diagnose“ – dann zeichne ich die Kennungen der Bedienelemente für die Fehlersuche auf." else ""
        return when (outcome.state) {
            SendOutcome.State.SENT -> "Gesendet an $to ($app): ${subject(plan)}"
            SendOutcome.State.DRAFT_READY -> "Der Entwurf an $to steht in $app bereit: ${subject(plan)}. Gesendet habe ich nichts" +
                if (draftWanted) " – du wolltest einen Entwurf."
                else ", weil „${plan.channel.settingLabel}“ ausgeschaltet ist. Einschalten kannst du es unter Einstellungen → Selbst senden."
            SendOutcome.State.DRAFT_UNVERIFIED -> "Ich habe $app mit dem Entwurf an $to geöffnet, konnte ihn aber nicht prüfen, " +
                "weil $why. Gesendet habe ich nichts."
            SendOutcome.State.NOT_SENT -> "Der Entwurf an $to steht in $app bereit, gesendet habe ich nichts, weil $why.$diagnosis"
            SendOutcome.State.SEND_UNCONFIRMED ->
                if (outcome.problem == SendProblem.STILL_DRAFT)
                    "Der Entwurf steht in $app bereit, das Senden konnte ich nicht bestätigen, weil $why. Ich drücke nicht noch einmal.$diagnosis"
                else "Ich habe in $app auf Senden gedrückt, konnte das Senden an $to aber nicht bestätigen, weil $why. " +
                    "Bitte schau selbst nach; ich sende nicht noch einmal."
            SendOutcome.State.NOT_PREPARED -> "Ich konnte $app nicht öffnen: ${outcome.detail}. Es wurde nichts gesendet."
        }
    }
}
