package at.heinz.deviceai.notifications

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import at.heinz.deviceai.AgentActivity
import at.heinz.deviceai.ContinuumScreenObservationService
import at.heinz.deviceai.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAccessibilityService
import java.io.File
import java.time.Duration

/**
 * The incoming-message signal without the notification listener: notification events of the real
 * [ContinuumScreenObservationService] (Robolectric) become bus notifications, the inbox takes them, and while the
 * app is not in front Continuum's own card appears. The notifications are built with Android's real builder; which
 * extras WhatsApp and Gmail really fill, and what Android delivers on the Fold7, is NOT verified here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [IncomingMessageSourceUiTest.ServiceInfoShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class IncomingMessageSourceUiTest {
    /** Robolectric has no connection behind an accessibility service; this keeps the info the service sets. */
    @Implements(AccessibilityService::class)
    class ServiceInfoShadow : ShadowAccessibilityService() {
        @Implementation fun getServiceInfo(): AccessibilityServiceInfo? = info
        @Implementation fun setServiceInfo(value: AccessibilityServiceInfo) { info = value }
        companion object { var info: AccessibilityServiceInfo? = null }
    }

    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val received = ArrayList<IncomingNotification>()
    private val subscriber = object : ContinuumNotificationBus.Subscriber {
        override fun onNotificationPosted(notification: IncomingNotification) { received += notification }
    }
    private lateinit var service: ContinuumScreenObservationService
    private val windowEvents = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED

    private fun idle(seconds: Long = 0) = shadowOf(Looper.getMainLooper()).let { if (seconds == 0L) it.idle() else it.idleFor(Duration.ofSeconds(seconds)) }
    private fun settings(change: (IncomingMessageSettings) -> IncomingMessageSettings) {
        IncomingMessagePreferences.update(app, change)
        IncomingMessages.settingsChanged(app)
    }
    private fun install(packageName: String, label: String) = shadowOf(app.packageManager).installPackage(PackageInfo().apply {
        this.packageName = packageName
        applicationInfo = ApplicationInfo().apply { this.packageName = packageName; nonLocalizedLabel = label }
    })

    @Before fun prepare() {
        ContinuumNotificationBus.resetForTest()
        IncomingMessagePreferences.save(app, IncomingMessageSettings())
        IncomingMessages.start(app)
        IncomingMessages.resetForTest()
        ContinuumNotificationBus.subscribe(subscriber)
        install("com.whatsapp", "WhatsApp")
        install("com.google.android.gm", "Gmail")
        ServiceInfoShadow.info = AccessibilityServiceInfo().apply { eventTypes = windowEvents }
        service = Robolectric.buildService(ContinuumScreenObservationService::class.java).create().get()
        AccessibilityNotificationSource.connected(service)
    }

    @After fun release() {
        AccessibilityNotificationSource.disconnected(service)
        IncomingMessages.resetForTest()
        ContinuumNotificationBus.resetForTest()
        IncomingMessagePreferences.save(app, IncomingMessageSettings())
    }

    private fun event(packageName: String, notification: Notification?, className: String = Notification::class.java.name) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED).apply {
            this.packageName = packageName
            this.className = className
            parcelableData = notification
        }

    private fun builder() = Notification.Builder(app, "chat").setSmallIcon(R.drawable.ic_device_ai)

    private fun whatsApp(sender: String, vararg texts: Pair<String, Long>, group: String? = null): Notification {
        val style = Notification.MessagingStyle(Person.Builder().setName("Du").build())
        if (group != null) style.setConversationTitle(group).setGroupConversation(true)
        texts.forEach { (text, time) -> style.addMessage(text, time, Person.Builder().setName(sender).build()) }
        return builder().setStyle(style).setCategory(Notification.CATEGORY_MESSAGE).setWhen(texts.last().second).build()
    }

    private fun gmail(sender: String, subject: String, snippet: String, time: Long = 5_000L): Notification =
        builder().setContentTitle(sender).setContentText(subject).setSubText("besitzer@example.org")
            .setStyle(Notification.BigTextStyle().bigText("$subject\n$snippet")).setCategory(Notification.CATEGORY_EMAIL).setWhen(time).build()

    @Test fun theServiceAsksForNotificationEventsOnlyWhileTheOwnersSwitchIsOn() {
        // The static declaration of the service is unchanged: no notification events, no listener.
        val declared = listOf("src/main/res/xml/continuum_screen_observation.xml", "app/src/main/res/xml/continuum_screen_observation.xml")
            .map(::File).first { it.isFile }.readText()
        assertFalse(declared.contains("typeNotificationStateChanged"))
        assertEquals("off by default: nothing is asked for", windowEvents, ServiceInfoShadow.info!!.eventTypes)
        assertFalse(AccessibilityNotificationSource.listening())
        settings { it.copy(enabled = true, apps = listOf("com.whatsapp")) }
        assertEquals(windowEvents or AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED, ServiceInfoShadow.info!!.eventTypes)
        assertTrue(AccessibilityNotificationSource.listening())
        settings { it.copy(enabled = false) }
        assertEquals("switched off: the screen observation keeps exactly its own events", windowEvents, ServiceInfoShadow.info!!.eventTypes)
        // A service that connects later applies the stored switch by itself.
        settings { it.copy(enabled = true) }
        ServiceInfoShadow.info = AccessibilityServiceInfo().apply { eventTypes = windowEvents }
        AccessibilityNotificationSource.connected(service)
        assertTrue(AccessibilityNotificationSource.listening())
    }

    @Test fun aMessageOfAChosenAppReachesTheBusWithItsStructure() {
        settings { it.copy(enabled = true, apps = listOf("com.whatsapp", "com.google.android.gm")) }
        service.onAccessibilityEvent(event("com.whatsapp", whatsApp("Anna", "Bist du da?" to 1_000L, "Ich warte unten" to 2_000L)))
        val chat = received.single()
        assertEquals("com.whatsapp", chat.packageName)
        assertEquals("WhatsApp", chat.appLabel)
        assertEquals("Anna", chat.title)
        assertEquals("Ich warte unten", chat.text)
        assertEquals("msg", chat.category)
        assertTrue(chat.key.startsWith("a11y|com.whatsapp|"))
        assertFalse(chat.isOngoing || chat.isGroupSummary || chat.isOwnPackage)
        val details = requireNotNull(chat.details)
        assertEquals(NotificationSource.ACCESSIBILITY, details.source)
        assertEquals(listOf(NotificationMessage("Anna", "Bist du da?", 1_000L), NotificationMessage("Anna", "Ich warte unten", 2_000L)), details.messages)
        assertEquals("Du", details.selfName)
        assertFalse(details.isGroupConversation || details.contentHidden)
        assertEquals(2_000L, details.whenMs)
        assertFalse("content never appears in a log line", chat.toString().contains("warte") || details.toString().contains("warte"))

        received.clear()
        service.onAccessibilityEvent(event("com.whatsapp", whatsApp("Lisa", "Wer holt Oma ab?" to 3_000L, group = "Familie")))
        val group = requireNotNull(received.single().details)
        assertTrue(group.isGroupConversation)
        assertEquals("Familie", group.conversationTitle)
        assertEquals("Lisa", group.messages.single().sender)

        received.clear()
        service.onAccessibilityEvent(event("com.google.android.gm", gmail("Bezirksgericht Linz", "Ladung als Zeuge, GZ 12 C 345/26", "Sie werden geladen.")))
        val mail = received.single()
        assertEquals("Bezirksgericht Linz" to "Ladung als Zeuge, GZ 12 C 345/26", mail.title to mail.text)
        assertEquals("email", mail.category)
        assertEquals("Ladung als Zeuge, GZ 12 C 345/26\nSie werden geladen.", mail.details!!.bigText)
        assertEquals("besitzer@example.org", mail.details!!.subText)

        // The inbox behind the bus took all three: one item each, the group is off by default.
        idle(5)
        assertEquals(listOf("Anna", "Bezirksgericht Linz"), IncomingMessages.inbox.open().map { it.sender })
        assertEquals(listOf(2, 1), IncomingMessages.inbox.open().map { it.count })
        assertEquals("Sie werden geladen.", IncomingMessages.inbox.open().last().parts.single().text)
        assertEquals(ImportanceReason.AUTHORITY, IncomingMessages.inbox.open().last().decision.reason)
    }

    @Test fun nothingIsReadUnlessTheOwnerSwitchedItOnForThatApp() {
        val anna = whatsApp("Anna", "Hallo" to 1_000L)
        service.onAccessibilityEvent(event("com.whatsapp", anna))
        assertTrue("feature off", received.isEmpty())
        settings { it.copy(enabled = true, apps = listOf("com.google.android.gm")) }
        service.onAccessibilityEvent(event("com.whatsapp", anna))
        assertTrue("app not chosen", received.isEmpty())
        settings { it.copy(apps = listOf("com.whatsapp", app.packageName)) }
        service.onAccessibilityEvent(event(app.packageName, anna))
        assertTrue("Continuum's own card is never taken as a message", received.isEmpty())
        // A toast uses the same event type.
        service.onAccessibilityEvent(event("com.whatsapp", null, className = "android.widget.Toast\$TN"))
        assertTrue(received.isEmpty())
        // While a build's notification listener is connected it is the one source.
        ContinuumNotificationBus.listenerConnected = true
        service.onAccessibilityEvent(event("com.whatsapp", anna))
        assertTrue(received.isEmpty())
        ContinuumNotificationBus.listenerConnected = false
        service.onAccessibilityEvent(event("com.whatsapp", anna))
        assertEquals(1, received.size)
        // The same message once more from the listener of such a build: one message.
        idle(5)
        ContinuumNotificationBus.publish(IncomingNotification("0|com.whatsapp|1|null|10", "com.whatsapp", "WhatsApp",
            NotificationDetailsReader.title(anna), NotificationDetailsReader.text(anna), anna.category, false, false, 9L, false,
            NotificationDetailsReader.details(anna, NotificationSource.LISTENER, silent = false)))
        idle(5)
        assertEquals(1, IncomingMessages.inbox.open().single().count)
    }

    @Test fun aLockedDeviceGivesOnlyTheHintThatSomethingArrived() {
        settings { it.copy(enabled = true, apps = listOf("com.whatsapp")) }
        // Android hands out the public version of a private notification – usually nothing at all.
        service.onAccessibilityEvent(event("com.whatsapp", null))
        val hidden = received.single()
        assertTrue(hidden.details!!.contentHidden)
        assertEquals("" to "", hidden.title to hidden.text)
        // A public version that only names the app says nothing about the sender either.
        received.clear()
        service.onAccessibilityEvent(event("com.whatsapp", builder().setContentTitle("WhatsApp").setContentText("1 neue Nachricht").build()))
        assertTrue(received.single().details!!.contentHidden)
        // Ongoing work of the app (backup, call) is passed on as such and dropped by the inbox.
        received.clear()
        service.onAccessibilityEvent(event("com.whatsapp", builder().setContentTitle("WhatsApp").setContentText("Backup läuft").setOngoing(true).build()))
        assertTrue(received.single().isOngoing)
        idle(5)
        val item = IncomingMessages.inbox.open().single()
        assertTrue(item.hidden && item.count == 2 && item.parts.isEmpty())
    }

    @Test fun whileTheAppIsNotInFrontContinuumShowsItsOwnCardWithoutTheMessageText() {
        settings { it.copy(enabled = true, apps = listOf("com.whatsapp"), quietHours = false) }
        val manager = app.getSystemService(NotificationManager::class.java)
        service.onAccessibilityEvent(event("com.whatsapp", whatsApp("Anna", "Kommst du heute um fünf?" to 1_000L)))
        idle(5)
        val item = IncomingMessages.inbox.open().single()
        assertEquals(IncomingState.WAITING, item.state)
        assertEquals(IncomingHold.AWAY, item.held)
        val card = shadowOf(manager).allNotifications.single()
        assertEquals("Continuum · Nachricht", card.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Eine Nachricht von Anna auf WhatsApp – vorlesen?", card.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Vorlesen", "Später"), card.actions.map { it.title.toString() })
        assertEquals(Notification.VISIBILITY_PRIVATE, card.visibility)
        assertEquals("the locked screen names no sender", "Eine Nachricht wartet.", card.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull("no sound of its own", manager.getNotificationChannel(card.channelId).sound)
        // „Vorlesen“ opens the existing screen with the wish; it starts nothing else.
        val open = shadowOf(card.actions[0].actionIntent).savedIntent
        assertEquals(AgentActivity::class.java.name, open.component!!.className)
        assertEquals(item.id, open.getLongExtra(IncomingMessages.EXTRA_ITEM, -1))
        assertTrue(open.getBooleanExtra(IncomingMessages.EXTRA_READ, false))
        assertTrue(open.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        // „Später“ keeps it in the open list and removes the card – without opening anything.
        IncomingMessageActionReceiver().onReceive(app, shadowOf(card.actions[1].actionIntent).savedIntent)
        idle()
        assertEquals(IncomingState.OPEN, item.state)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)

        // Switching the feature off forgets what was held and removes the cards.
        service.onAccessibilityEvent(event("com.whatsapp", whatsApp("Tom", "Servus" to 7_000L)))
        idle(5)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        settings { it.copy(enabled = false) }
        assertTrue(IncomingMessages.inbox.open().isEmpty())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        // The card is the owner's choice as well.
        settings { it.copy(enabled = true, backgroundCard = false) }
        service.onAccessibilityEvent(event("com.whatsapp", whatsApp("Tom", "Bist du da?" to 9_000L)))
        idle(5)
        assertEquals(1, IncomingMessages.inbox.open().size)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertNotNull(IncomingMessages.inbox.lastDecision(reported = true))
    }
}
