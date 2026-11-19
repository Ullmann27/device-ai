package at.heinz.deviceai.connections

import at.heinz.deviceai.cognitive.InternetAccessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every row of „Verbindungen“ from fake checks: state, account, the one action, and real use. */
class ContinuumConnectionsTest {
    private class Store : ContinuumConnectionStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private class Checks : ContinuumConnectionChecks {
        var gmail = GmailConnectionView(false, null, emptyList(), null, null)
        var calendar: Boolean? = false
        var contacts: Boolean? = true
        var files: Boolean? = false
        var waInstalled: Boolean? = true
        var waGrant: Boolean? = false
        var accessibility: Boolean? = false
        var internet: InternetAccessMode? = InternetAccessMode.ASK
        var incoming: Boolean? = false
        var canAsk = true
        override fun gmail() = gmail
        override fun calendarGranted() = calendar
        override fun contactsGranted() = contacts
        override fun filesAccess() = files
        override fun whatsappInstalled() = waInstalled
        override fun whatsappSelfSend() = waGrant
        override fun accessibilityOn() = accessibility
        override fun internetMode() = internet
        override fun incomingAnnouncements() = incoming
        override fun canAskPermission(permission: String) = canAsk
    }

    private val usage = ContinuumConnectionUsage(Store()) { 1_000L }
    private fun row(checks: Checks, kind: ContinuumConnectionKind) = ContinuumConnectionRegistry.row(kind, checks, usage)

    @Test fun sixStatesWithTheirGermanLabels() {
        assertEquals(listOf("Nicht verbunden", "Berechtigung erforderlich", "Verbunden", "Wird verwendet", "Verbindung unterbrochen",
            "Berechtigung widerrufen"), ContinuumConnectionState.entries.map { it.label })
        assertEquals(7, ContinuumConnectionRegistry.rows(Checks(), usage).size)
        assertTrue(ContinuumConnectionKind.entries.none { it.title.contains("Drive") })
    }

    @Test fun gmailRowsFollowTheConnectionAndAlwaysOpenTheGmailPage() {
        val checks = Checks()
        assertEquals(ContinuumConnectionState.NOT_CONNECTED, row(checks, ContinuumConnectionKind.GMAIL).state)
        checks.gmail = GmailConnectionView(true, "heinz@example.org", listOf("Lesen"), null, null)
        val connected = row(checks, ContinuumConnectionKind.GMAIL)
        assertEquals(ContinuumConnectionState.CONNECTED, connected.state)
        assertEquals("heinz@example.org", connected.account)
        assertEquals(ContinuumConnectionAction.OPEN_GMAIL, connected.action)
        usage.begin(ContinuumConnectionKind.GMAIL)
        assertEquals(ContinuumConnectionState.IN_USE, row(checks, ContinuumConnectionKind.GMAIL).state)
        usage.end(ContinuumConnectionKind.GMAIL, "Die Verbindung zu Gmail ist unterbrochen.")
        val after = row(checks, ContinuumConnectionKind.GMAIL)
        assertEquals(ContinuumConnectionState.CONNECTED, after.state)
        assertEquals(1_000L, after.lastUseMillis)
        assertEquals("Die Verbindung zu Gmail ist unterbrochen.", after.lastError)
        checks.gmail = GmailConnectionView(true, "heinz@example.org", listOf("Lesen"), ContinuumConnectionState.REVOKED, "widerrufen")
        assertEquals(ContinuumConnectionState.REVOKED, row(checks, ContinuumConnectionKind.GMAIL).state)
        checks.gmail = GmailConnectionView(false, null, emptyList(), ContinuumConnectionState.NOT_CONNECTED, "nur original signiert")
        assertEquals("nur original signiert", row(checks, ContinuumConnectionKind.GMAIL).detail)
    }

    @Test fun calendarAndContactsUseTheSystemPermissionScreens() {
        val checks = Checks()
        val calendar = row(checks, ContinuumConnectionKind.CALENDAR)
        assertEquals(ContinuumConnectionState.PERMISSION_REQUIRED, calendar.state)
        assertEquals(ContinuumConnectionAction.REQUEST_PERMISSION, calendar.action)
        checks.canAsk = false
        assertEquals(ContinuumConnectionAction.OPEN_APP_SETTINGS, row(checks, ContinuumConnectionKind.CALENDAR).action)
        val contacts = row(checks, ContinuumConnectionKind.CONTACTS)
        assertEquals(ContinuumConnectionState.CONNECTED, contacts.state)
        assertEquals(ContinuumConnectionAction.OPEN_APP_SETTINGS, contacts.action)
        checks.contacts = null
        assertEquals(ContinuumConnectionState.INTERRUPTED, row(checks, ContinuumConnectionKind.CONTACTS).state)
    }

    @Test fun filesWhatsappInternetAndNotificationsReflectTheExistingChecks() {
        val checks = Checks()
        assertEquals(ContinuumConnectionAction.OPEN_FILES_ACCESS, row(checks, ContinuumConnectionKind.FILES).action)
        assertEquals(ContinuumConnectionState.PERMISSION_REQUIRED, row(checks, ContinuumConnectionKind.FILES).state)
        checks.files = true
        assertEquals(ContinuumConnectionState.CONNECTED, row(checks, ContinuumConnectionKind.FILES).state)

        assertEquals(ContinuumConnectionState.NOT_CONNECTED, row(checks, ContinuumConnectionKind.WHATSAPP).state)
        assertEquals(ContinuumConnectionAction.OPEN_SELF_SEND, row(checks, ContinuumConnectionKind.WHATSAPP).action)
        checks.waGrant = true
        assertEquals(ContinuumConnectionAction.OPEN_ACCESSIBILITY, row(checks, ContinuumConnectionKind.WHATSAPP).action)
        checks.accessibility = true
        assertEquals(ContinuumConnectionState.CONNECTED, row(checks, ContinuumConnectionKind.WHATSAPP).state)
        checks.waInstalled = false
        assertNull("no button without effect", row(checks, ContinuumConnectionKind.WHATSAPP).action)

        assertEquals("Nachfragen", row(checks, ContinuumConnectionKind.INTERNET).account)
        checks.internet = InternetAccessMode.OFF
        assertEquals(ContinuumConnectionState.NOT_CONNECTED, row(checks, ContinuumConnectionKind.INTERNET).state)
        assertEquals(ContinuumConnectionAction.OPEN_INTERNET, row(checks, ContinuumConnectionKind.INTERNET).action)

        assertEquals(ContinuumConnectionState.NOT_CONNECTED, row(checks, ContinuumConnectionKind.NOTIFICATIONS).state)
        checks.incoming = true; checks.accessibility = false
        assertEquals(ContinuumConnectionState.PERMISSION_REQUIRED, row(checks, ContinuumConnectionKind.NOTIFICATIONS).state)
        checks.accessibility = true
        assertEquals(ContinuumConnectionState.CONNECTED, row(checks, ContinuumConnectionKind.NOTIFICATIONS).state)
    }

    @Test fun aFailedUseIsRecordedAndForgottenOnDisconnect() {
        val store = Store()
        val tracker = ContinuumConnectionUsage(store) { 5L }
        runCatching { tracker.use(ContinuumConnectionKind.CALENDAR, { "Kalender nicht lesbar" }) { error("x") } }
        assertEquals("Kalender nicht lesbar", tracker.lastError(ContinuumConnectionKind.CALENDAR))
        assertEquals(5L, tracker.lastUse(ContinuumConnectionKind.CALENDAR))
        tracker.forget(ContinuumConnectionKind.CALENDAR)
        assertNull(tracker.lastUse(ContinuumConnectionKind.CALENDAR))
    }
}
