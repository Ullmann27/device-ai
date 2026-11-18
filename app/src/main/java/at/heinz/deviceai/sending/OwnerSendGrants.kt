package at.heinz.deviceai.sending

import java.time.LocalDate

/** Small persistent key/value port (SharedPreferences on the device, a map in host tests). */
internal interface OwnerSendKeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/**
 * Proof that a grant change comes from the owner's own tap in the settings dialog. The only production place that
 * creates one is [OwnerSendSettingsDialog]; no parser, tool, model output, screen text or file content can.
 */
internal class OwnerSendOwnerTap internal constructor()

/**
 * The standing owner permission per channel (default OFF) and the hard daily limit.
 *
 * - Without the grant Continuum only prepares drafts, exactly as before.
 * - The grant is switched only with an [OwnerSendOwnerTap]; it stays until the owner revokes it in the same place.
 * - At most [DAILY_CAP] messages per calendar day over both channels. Every pressed send control counts, also one
 *   whose effect could not be confirmed, so the limit can never be exceeded by unclear results.
 */
internal class OwnerSendGrants(private val store: OwnerSendKeyValue, private val today: () -> LocalDate) {
    fun enabled(channel: SendChannel): Boolean = store.get(key(channel)) == ON

    /** Day the owner switched the channel on (ISO date), null while it is off. */
    fun grantedOn(channel: SendChannel): String? = store.get(key(channel) + SINCE).takeIf { enabled(channel) }

    @Synchronized fun change(channel: SendChannel, enabled: Boolean, @Suppress("UNUSED_PARAMETER") tap: OwnerSendOwnerTap) {
        store.put(key(channel), if (enabled) ON else null)
        store.put(key(channel) + SINCE, if (enabled) today().toString() else null)
    }

    @Synchronized fun sentToday(): Int {
        val (day, count) = (store.get(LEDGER) ?: "").split('|').let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
        return if (day == today().toString() && count != null && count >= 0) count else 0
    }

    fun remainingToday(): Int = (DAILY_CAP - sentToday()).coerceAtLeast(0)

    /**
     * Reserves one send right before the send control is pressed. False = grant revoked meanwhile or the daily limit
     * is reached; then nothing may be pressed.
     */
    @Synchronized fun reserveSend(channel: SendChannel): Boolean {
        if (!enabled(channel)) return false
        val sent = sentToday()
        if (sent >= DAILY_CAP) return false
        store.put(LEDGER, today().toString() + "|" + (sent + 1))
        return true
    }

    private fun key(channel: SendChannel) = "self_send_" + channel.name.lowercase()

    companion object {
        const val DAILY_CAP = 30
        const val PREFERENCES = "continuum-owner-send"
        private const val ON = "on"
        private const val SINCE = "_since"
        private const val LEDGER = "sent_ledger"

        /** What the owner reads before switching a channel on. States exactly what then happens without asking. */
        fun grantDialogText(channel: SendChannel): String = when (channel) {
            SendChannel.WHATSAPP -> "Wenn du das einschaltest, sendet Continuum WhatsApp-Nachrichten in deinem Namen, " +
                "ohne jedes Mal nachzufragen:\n\n" +
                "• Nur wenn du es ausdrücklich beauftragst („Schreib Anna auf WhatsApp, ich komme später“).\n" +
                "• Nur Text, den du diktiert oder bestätigt hast. Selbst formulierten Text zeigt Continuum dir einmal " +
                "und sendet erst nach deinem Ja.\n" +
                "• Nur an genau einen eindeutigen Kontakt. Bei mehreren oder keinem Treffer fragt Continuum nach.\n" +
                "• Nie an Gruppen oder Broadcast-Listen, nie mehr als eine Nachricht pro Auftrag, höchstens $DAILY_CAP am Tag.\n" +
                "• Continuum öffnet den Chat, prüft Empfänger und Text auf dem Bildschirm, drückt Senden und meldet das Ergebnis.\n\n" +
                "Dazu braucht Continuum Lesezugriff auf deine Kontakte und die eingeschaltete Continuum-Bedienungshilfe. " +
                "Du kannst die Freigabe hier jederzeit wieder ausschalten."
            SendChannel.GMAIL -> "Wenn du das einschaltest, sendet Continuum E-Mails über Gmail in deinem Namen, " +
                "ohne jedes Mal nachzufragen:\n\n" +
                "• Nur wenn du es ausdrücklich beauftragst („Schick die Zusammenfassung per Mail an anna@example.org“).\n" +
                "• Eine Datei, die du ausdrücklich verschicken lässt, geht als Anhang hinaus. Selbst formulierten Text " +
                "(z. B. eine Zusammenfassung im Mailtext) zeigt Continuum dir einmal und sendet erst nach deinem Ja.\n" +
                "• Nur an eine Adresse, die du nennst, oder an genau einen eindeutigen Kontakt mit E-Mail-Adresse.\n" +
                "• Nie mehr als eine E-Mail pro Auftrag, höchstens $DAILY_CAP Nachrichten am Tag.\n" +
                "• Continuum öffnet den Gmail-Entwurf, prüft Empfänger, Betreff, Text und Anhang auf dem Bildschirm, " +
                "drückt Senden und meldet das Ergebnis.\n\n" +
                "Dazu braucht Continuum die eingeschaltete Continuum-Bedienungshilfe. " +
                "Du kannst die Freigabe hier jederzeit wieder ausschalten."
        }
    }
}
