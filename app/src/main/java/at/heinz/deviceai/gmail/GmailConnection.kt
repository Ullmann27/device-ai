package at.heinz.deviceai.gmail

import at.heinz.deviceai.connections.ContinuumConnectionState
import at.heinz.deviceai.connections.ContinuumConnectionStore
import at.heinz.deviceai.connections.GmailConnectionView

/**
 * What the app keeps about the Gmail connection – app-private, never a token: the account address Google reported,
 * the granted scopes, the last failure kind, and the owner's switch „Mailinhalte an die Cloud senden“ (default OFF).
 * Everything is removed on disconnect except that switch, which the owner set himself.
 */
internal class GmailConnection(private val store: ContinuumConnectionStore) {
    val connected: Boolean get() = store.get(CONNECTED) == "yes"
    val account: String? get() = store.get(ACCOUNT)?.takeIf { GmailMime.validAddress(it) }
    val grant: GmailGrant get() = GmailGrant(store.get(SCOPES).orEmpty().split(' ').filter { it.isNotBlank() })

    /** The owner's separate consent: mail text may go to the cloud brain. OFF until he switches it on in the Gmail page. */
    var cloudContent: Boolean
        get() = store.get(CLOUD) == "on"
        set(value) = store.put(CLOUD, if (value) "on" else null)

    val failure: GmailFailureKind? get() = store.get(FAILURE)?.let { name -> GmailFailureKind.entries.firstOrNull { it.name == name } }

    /** Google confirmed [grant] (a consent or a silent authorization); [address] when getProfile told it. */
    @Synchronized fun granted(grant: GmailGrant, address: String? = null) {
        if (grant.capabilities.isEmpty()) return
        store.put(CONNECTED, "yes")
        store.put(SCOPES, this.grant.plus(grant).scopes.joinToString(" "))
        address?.takeIf { GmailMime.validAddress(it) }?.let { store.put(ACCOUNT, it.lowercase()) }
        store.put(FAILURE, null)
    }

    /** A Gmail call ended; [kind] null = it worked. Only failures that say something about the connection are kept. */
    @Synchronized fun ended(kind: GmailFailureKind?) {
        when {
            kind == null -> store.put(FAILURE, null)
            kind.leaves != null -> store.put(FAILURE, kind.name)
        }
    }

    /** Disconnect: everything about the account goes; the cloud switch stays as the owner set it. */
    @Synchronized fun clear() {
        listOf(CONNECTED, ACCOUNT, SCOPES, FAILURE).forEach { store.put(it, null) }
    }

    /** The state of the „Verbindungen“ row. */
    fun view(): GmailConnectionView {
        val failure = failure
        val state = failure?.leaves
        return GmailConnectionView(connected, account, grant.capabilities.map { it.label },
            state?.takeIf { it != ContinuumConnectionState.CONNECTED }, failure?.let { GmailText.rowText(it) })
    }

    companion object {
        const val PREFERENCES = "continuum-gmail"
        private const val CONNECTED = "connected"
        private const val ACCOUNT = "account"
        private const val SCOPES = "scopes"
        private const val FAILURE = "failure"
        private const val CLOUD = "cloud_content_consent"
    }
}

/** The fixed sentences of the Gmail lane. None of them ever contains a token, a header or mail content. */
internal object GmailText {
    const val SETUP_HINT = "Die Anleitung steht in der App-Dokumentation unter „Gmail verbinden – Einrichtung“."

    fun failure(kind: GmailFailureKind, capability: GmailCapability?): String = when (kind) {
        GmailFailureKind.NOT_CONNECTED -> "Gmail ist nicht verbunden. Sag „Verbinde Gmail“ oder öffne Einstellungen → Verbindungen → Google Gmail."
        GmailFailureKind.PERMISSION_REQUIRED -> "Für " + (capability?.right ?: "diesen Schritt") + " fehlt die Google-Freigabe " +
            "oder sie muss neu bestätigt werden."
        GmailFailureKind.REVOKED -> "Die Google-Freigabe für Gmail gilt nicht mehr – sie wurde widerrufen oder ist abgelaufen. " +
            "Verbinde Gmail bitte neu."
        GmailFailureKind.INTERRUPTED -> "Die Verbindung zu Gmail ist unterbrochen – das Gerät ist offline oder die Verbindung brach ab."
        GmailFailureKind.QUOTA -> "Google nimmt gerade keine weiteren Gmail-Anfragen an (Anfragelimit). Versuch es in ein paar Minuten noch einmal."
        GmailFailureKind.SERVER -> "Die Gmail-Server haben mehrmals mit einem Fehler geantwortet."
        GmailFailureKind.CLIENT_NOT_REGISTERED -> "Google kennt diese App-Fassung nicht. Die Gmail-Verbindung funktioniert nur in der " +
            "original signierten Continuum-App und erst, nachdem im Google-Cloud-Projekt ein Android-Client mit Paketname " +
            "at.heinz.deviceai.continuumlocal und dem SHA-1-Fingerabdruck der Originalsignatur angelegt ist. Eine Test- oder " +
            "QA-Fassung hat eine andere Signatur und wird von Google abgelehnt. $SETUP_HINT"
        GmailFailureKind.API_DISABLED -> "Im Google-Cloud-Projekt ist die Gmail-API nicht aktiviert. Schalte sie unter " +
            "„APIs & Dienste → Bibliothek → Gmail API“ ein. $SETUP_HINT"
        GmailFailureKind.PLAY_SERVICES -> "Für die Google-Anmeldung fehlen auf diesem Gerät aktuelle Google-Play-Dienste."
        GmailFailureKind.CANCELLED -> "Die Google-Freigabe wurde abgebrochen. Es wurde nichts verbunden."
        GmailFailureKind.NOT_FOUND -> "Diese Mail gibt es in Gmail nicht mehr."
        GmailFailureKind.INVALID_RESPONSE -> "Gmail hat eine Antwort geschickt, die ich nicht verarbeiten kann. Ich übernehme davon nichts."
        GmailFailureKind.INTERNET_OFF -> "Der Internetzugang von Continuum ist ausgeschaltet, deshalb rufe ich auch Gmail nicht ab. " +
            "Stell ihn unter „Datenschutz & Freigaben“ auf „Nachfragen“ oder „Immer“."
        GmailFailureKind.UNKNOWN -> "Bei Gmail ist ein unerwarteter Fehler aufgetreten."
    }

    /** The row text for a remembered failure. */
    fun rowText(kind: GmailFailureKind): String = when (kind) {
        GmailFailureKind.PERMISSION_REQUIRED -> "Google möchte die Freigabe neu bestätigt haben. Tippe auf „Öffnen“ → „Erneut verbinden“."
        GmailFailureKind.REVOKED -> "Die Freigabe wurde widerrufen oder ist abgelaufen. Tippe auf „Öffnen“ → „Erneut verbinden“."
        GmailFailureKind.CLIENT_NOT_REGISTERED -> "Google kennt diese App-Fassung nicht – nur die original signierte App kann sich verbinden."
        else -> failure(kind, null)
    }

    const val CLOUD_SWITCH = "Mailinhalte an die Cloud senden"
    const val CLOUD_SWITCH_DETAIL = "Nur wenn Claude als Gehirn gewählt ist: Text deiner Mails darf zum Zusammenfassen an Claude " +
        "gehen. Aus: Mailtext bleibt auf dem Gerät – zusammengefasst wird dann nur mit dem Modell auf dem Gerät."
    const val COMPOSE_NOTE = "Hinweis: Googles Entwurfsrecht erlaubt technisch auch das Senden. Continuum sendet trotzdem nur mit " +
        "dem eigenen Senderecht und erst, nachdem du Empfänger, Betreff und Text bestätigt hast."
}
