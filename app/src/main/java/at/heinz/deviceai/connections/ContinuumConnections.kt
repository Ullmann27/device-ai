package at.heinz.deviceai.connections

import at.heinz.deviceai.cognitive.InternetAccessMode

/** The one set of states every connection is shown in (owner order 19.11.2026). */
internal enum class ContinuumConnectionState(val label: String) {
    NOT_CONNECTED("Nicht verbunden"),
    PERMISSION_REQUIRED("Berechtigung erforderlich"),
    CONNECTED("Verbunden"),
    IN_USE("Wird verwendet"),
    INTERRUPTED("Verbindung unterbrochen"),
    REVOKED("Berechtigung widerrufen")
}

/** The connections of the app. Google Drive is not built and therefore not listed as a connection. */
internal enum class ContinuumConnectionKind(val title: String, val usedFor: String) {
    GMAIL("Google Gmail", "Mails suchen, lesen, zusammenfassen, Entwürfe anlegen und – nach deiner Bestätigung – senden"),
    CALENDAR("Kalender", "Termine im Briefing und auf Nachfrage vorlesen – nur lesen"),
    CONTACTS("Kontakte", "Empfänger und Nummern für Senden, Anrufen und Mail-Entwürfe finden"),
    FILES("Dateien", "PDFs und Dokumente auf dem Gerät suchen, öffnen und lesen"),
    WHATSAPP("WhatsApp", "WhatsApp-Nachrichten in deinem Wortlaut selbst senden"),
    INTERNET("Browser & Internet", "Webrecherche, Nachrichten und Wetter über deine Internetfreigabe"),
    NOTIFICATIONS("Benachrichtigungen", "Neue WhatsApp- und Gmail-Nachrichten melden und vorlesen")
}

/** The one real thing a row's button does. Each maps to an existing screen or flow of the app. */
internal enum class ContinuumConnectionAction(val label: String) {
    /** The Gmail page (connect, rights, cloud switch, disconnect). */
    OPEN_GMAIL("Öffnen"),
    /** Android's runtime permission request. */
    REQUEST_PERMISSION("Berechtigung erteilen"),
    /** Android's own settings page of this app (permissions are taken back there). */
    OPEN_APP_SETTINGS("In Android verwalten"),
    /** Android's „Zugriff auf alle Dateien“ page. */
    OPEN_FILES_ACCESS("Zugriff erlauben"),
    /** Android's accessibility settings. */
    OPEN_ACCESSIBILITY("Bedienungshilfe öffnen"),
    /** The app's own „Selbst senden“ settings. */
    OPEN_SELF_SEND("Freigabe verwalten"),
    /** The app's own internet setting. */
    OPEN_INTERNET("Internetfreigabe ändern"),
    /** The app's own „Eingehende Nachrichten melden“ settings. */
    OPEN_INCOMING("Meldungen einstellen")
}

/** One row of the „Verbindungen“ page. Every value is read at the moment the page is built. */
internal data class ContinuumConnection(
    val kind: ContinuumConnectionKind,
    val state: ContinuumConnectionState,
    /** Account or plain detail („anna@gmail.com“, „Nachfragen“); null when there is none. */
    val account: String?,
    val lastUseMillis: Long?,
    /** The last failure in plain words; null when the last use worked. */
    val lastError: String?,
    val action: ContinuumConnectionAction?,
    /** One plain sentence about the state, for the row. */
    val detail: String
)

/**
 * The real present state of every connection, through the app's existing checks (the Activity lends them):
 * null = the check could not be made right now.
 */
internal interface ContinuumConnectionChecks {
    fun gmail(): GmailConnectionView
    fun calendarGranted(): Boolean?
    fun contactsGranted(): Boolean?
    /** Android's „Zugriff auf alle Dateien“ (OwnerDeviceLibrary.access). */
    fun filesAccess(): Boolean?
    fun whatsappInstalled(): Boolean?
    /** The owner's standing „WhatsApp-Nachrichten selbst senden“ grant (OwnerSendGrants). */
    fun whatsappSelfSend(): Boolean?
    /** The Continuum accessibility service, which the WhatsApp send flow and the message announcements use. */
    fun accessibilityOn(): Boolean?
    fun internetMode(): InternetAccessMode?
    /** „Eingehende Nachrichten melden“ is switched on in the app. */
    fun incomingAnnouncements(): Boolean?
    /** Android may still show the permission dialog for [permission] (false: only the settings page can grant it). */
    fun canAskPermission(permission: String): Boolean = true
}

/** What the Gmail connection looks like from outside, without anything secret. */
internal data class GmailConnectionView(
    val connected: Boolean,
    val account: String?,
    val rights: List<String>,
    val failure: ContinuumConnectionState?,
    val failureText: String?
)

/** App-private key/value storage (SharedPreferences on the phone, a map in host tests). */
internal interface ContinuumConnectionStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/**
 * „Wird verwendet“ and „zuletzt verwendet“: set by real use only. [begin] is called when a lane step or API call
 * really starts, [end] when it ended – with the plain error when it failed. Timestamps and the plain error survive a
 * restart; IN_USE does not (nothing runs after a restart).
 */
internal class ContinuumConnectionUsage(private val store: ContinuumConnectionStore, private val now: () -> Long = System::currentTimeMillis) {
    private val running = HashMap<ContinuumConnectionKind, Int>()

    @Synchronized fun begin(kind: ContinuumConnectionKind) { running[kind] = (running[kind] ?: 0) + 1 }

    /** [error]: a fixed sentence of the lane, never a token or content. null = it worked. */
    @Synchronized fun end(kind: ContinuumConnectionKind, error: String?) {
        val left = (running[kind] ?: 0) - 1
        if (left <= 0) running.remove(kind) else running[kind] = left
        store.put(key(kind, "last_use"), now().toString())
        store.put(key(kind, "last_error"), error?.take(240))
    }

    /** Runs [work] as one use of [kind]; [failed] words a failure for the row. */
    fun <T> use(kind: ContinuumConnectionKind, failed: (Throwable) -> String?, work: () -> T): T {
        begin(kind)
        var error: String? = null
        try {
            return work()
        } catch (failure: Throwable) {
            error = failed(failure)
            throw failure
        } finally {
            end(kind, error)
        }
    }

    @Synchronized fun inUse(kind: ContinuumConnectionKind): Boolean = (running[kind] ?: 0) > 0
    fun lastUse(kind: ContinuumConnectionKind): Long? = store.get(key(kind, "last_use"))?.toLongOrNull()
    fun lastError(kind: ContinuumConnectionKind): String? = store.get(key(kind, "last_error"))?.takeIf { it.isNotBlank() }

    /** After a disconnect nothing of the old use is shown. */
    @Synchronized fun forget(kind: ContinuumConnectionKind) {
        store.put(key(kind, "last_use"), null)
        store.put(key(kind, "last_error"), null)
    }

    private fun key(kind: ContinuumConnectionKind, field: String) = "usage_" + kind.name.lowercase() + "_" + field
}

/**
 * The rows of the „Verbindungen“ page, built from [checks] and [usage]. Pure: the same checks give the same rows.
 * No row has a button without an effect: [ContinuumConnection.action] is null where nothing applies.
 */
internal object ContinuumConnectionRegistry {
    const val DRIVE_NOTE = "Google Drive: noch nicht gebaut"
    const val CALENDAR_PERMISSION = "android.permission.READ_CALENDAR"
    const val CONTACTS_PERMISSION = "android.permission.READ_CONTACTS"

    fun rows(checks: ContinuumConnectionChecks, usage: ContinuumConnectionUsage): List<ContinuumConnection> =
        ContinuumConnectionKind.entries.map { kind -> row(kind, checks, usage) }

    fun row(kind: ContinuumConnectionKind, checks: ContinuumConnectionChecks, usage: ContinuumConnectionUsage): ContinuumConnection {
        fun make(state: ContinuumConnectionState, account: String?, action: ContinuumConnectionAction?, detail: String) =
            ContinuumConnection(kind, if (state == ContinuumConnectionState.CONNECTED && usage.inUse(kind)) ContinuumConnectionState.IN_USE else state,
                account, usage.lastUse(kind), usage.lastError(kind), action, detail)
        fun unknown() = make(ContinuumConnectionState.INTERRUPTED, null, ContinuumConnectionAction.OPEN_APP_SETTINGS,
            "Der Zustand ließ sich gerade nicht prüfen.")
        return when (kind) {
            ContinuumConnectionKind.GMAIL -> {
                val gmail = checks.gmail()
                when {
                    !gmail.connected -> make(gmail.failure ?: ContinuumConnectionState.NOT_CONNECTED, null, ContinuumConnectionAction.OPEN_GMAIL,
                        gmail.failureText ?: "Noch kein Google-Konto verbunden. Tippe auf „Öffnen“ und dann auf „Konto verbinden“.")
                    gmail.failure != null && gmail.failure != ContinuumConnectionState.CONNECTED -> make(gmail.failure, gmail.account,
                        ContinuumConnectionAction.OPEN_GMAIL, gmail.failureText ?: gmail.failure.label)
                    else -> make(ContinuumConnectionState.CONNECTED, gmail.account, ContinuumConnectionAction.OPEN_GMAIL,
                        "Freigegeben: " + gmail.rights.joinToString(", ").ifEmpty { "nichts" } + ".")
                }
            }
            ContinuumConnectionKind.CALENDAR -> permissionRow(checks.calendarGranted(), checks.canAskPermission(CALENDAR_PERMISSION),
                "Kalender lesen ist erlaubt.", "Continuum darf den Kalender nicht lesen.", ::make) ?: unknown()
            ContinuumConnectionKind.CONTACTS -> permissionRow(checks.contactsGranted(), checks.canAskPermission(CONTACTS_PERMISSION),
                "Kontakte lesen ist erlaubt.", "Continuum darf die Kontakte nicht lesen.", ::make) ?: unknown()
            ContinuumConnectionKind.FILES -> when (checks.filesAccess()) {
                true -> make(ContinuumConnectionState.CONNECTED, null, ContinuumConnectionAction.OPEN_FILES_ACCESS,
                    "„Zugriff auf alle Dateien“ ist erlaubt. Ausschalten kannst du ihn auf derselben Android-Seite.")
                false -> make(ContinuumConnectionState.PERMISSION_REQUIRED, null, ContinuumConnectionAction.OPEN_FILES_ACCESS,
                    "Android „Zugriff auf alle Dateien“ ist für Continuum aus.")
                null -> unknown()
            }
            ContinuumConnectionKind.WHATSAPP -> {
                val installed = checks.whatsappInstalled()
                val grant = checks.whatsappSelfSend()
                val access = checks.accessibilityOn()
                when {
                    installed == false -> make(ContinuumConnectionState.NOT_CONNECTED, null, null, "WhatsApp ist auf diesem Gerät nicht installiert.")
                    installed == null || grant == null -> unknown()
                    !grant -> make(ContinuumConnectionState.NOT_CONNECTED, null, ContinuumConnectionAction.OPEN_SELF_SEND,
                        "Selbst senden ist aus: Continuum bereitet WhatsApp-Nachrichten nur vor.")
                    access != true -> make(ContinuumConnectionState.PERMISSION_REQUIRED, null, ContinuumConnectionAction.OPEN_ACCESSIBILITY,
                        "Selbst senden ist an, aber die Continuum-Bedienungshilfe ist aus.")
                    else -> make(ContinuumConnectionState.CONNECTED, null, ContinuumConnectionAction.OPEN_SELF_SEND,
                        "Selbst senden ist an, die Bedienungshilfe läuft.")
                }
            }
            ContinuumConnectionKind.INTERNET -> when (checks.internetMode()) {
                InternetAccessMode.OFF -> make(ContinuumConnectionState.NOT_CONNECTED, "Aus", ContinuumConnectionAction.OPEN_INTERNET,
                    "Der Internetzugang ist ausgeschaltet – auch Gmail ruft nichts ab.")
                InternetAccessMode.ASK -> make(ContinuumConnectionState.CONNECTED, "Nachfragen", ContinuumConnectionAction.OPEN_INTERNET,
                    "Jede Webanfrage wird einzeln freigegeben.")
                InternetAccessMode.ALWAYS_ON -> make(ContinuumConnectionState.CONNECTED, "Immer", ContinuumConnectionAction.OPEN_INTERNET,
                    "Webanfragen sind dauerhaft freigegeben.")
                null -> unknown()
            }
            ContinuumConnectionKind.NOTIFICATIONS -> {
                val on = checks.incomingAnnouncements()
                val access = checks.accessibilityOn()
                when {
                    on == null -> unknown()
                    !on -> make(ContinuumConnectionState.NOT_CONNECTED, null, ContinuumConnectionAction.OPEN_INCOMING,
                        "Das Melden neuer Nachrichten ist aus.")
                    access != true -> make(ContinuumConnectionState.PERMISSION_REQUIRED, null, ContinuumConnectionAction.OPEN_ACCESSIBILITY,
                        "Das Melden ist an, aber die Continuum-Bedienungshilfe ist aus.")
                    else -> make(ContinuumConnectionState.CONNECTED, null, ContinuumConnectionAction.OPEN_INCOMING,
                        "Neue Nachrichten werden gemeldet.")
                }
            }
        }
    }

    private fun permissionRow(granted: Boolean?, canAsk: Boolean, yes: String, no: String,
                              make: (ContinuumConnectionState, String?, ContinuumConnectionAction?, String) -> ContinuumConnection): ContinuumConnection? =
        when (granted) {
            true -> make(ContinuumConnectionState.CONNECTED, null, ContinuumConnectionAction.OPEN_APP_SETTINGS,
                "$yes Entziehen kannst du die Berechtigung in den Android-Einstellungen.")
            false -> make(ContinuumConnectionState.PERMISSION_REQUIRED, null,
                if (canAsk) ContinuumConnectionAction.REQUEST_PERMISSION else ContinuumConnectionAction.OPEN_APP_SETTINGS,
                no + if (canAsk) "" else " Android fragt nicht mehr nach; erlaube es in den Android-Einstellungen.")
            null -> null
        }

    /** The row as one line for chat or speech („Google Gmail: Verbunden · anna@gmail.com“). */
    fun line(row: ContinuumConnection): String = row.kind.title + ": " + row.state.label + (row.account?.let { " · $it" } ?: "")
}
