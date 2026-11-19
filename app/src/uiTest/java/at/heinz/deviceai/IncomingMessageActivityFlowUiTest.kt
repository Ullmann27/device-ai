package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.provider.Settings
import at.heinz.deviceai.notifications.AnnouncementMode
import at.heinz.deviceai.notifications.AnnouncementSettings
import at.heinz.deviceai.notifications.AppRule
import at.heinz.deviceai.notifications.ContinuumNotificationBus
import at.heinz.deviceai.notifications.ContinuumNotificationListener
import at.heinz.deviceai.notifications.IncomingMessagePreferences
import at.heinz.deviceai.notifications.IncomingMessageSettings
import at.heinz.deviceai.notifications.IncomingMessages
import at.heinz.deviceai.notifications.IncomingNotification
import at.heinz.deviceai.notifications.IncomingOwnerProfile
import at.heinz.deviceai.notifications.IncomingState
import at.heinz.deviceai.notifications.NotificationAnnouncementSettings
import at.heinz.deviceai.notifications.NotificationDetails
import at.heinz.deviceai.notifications.NotificationMessage
import at.heinz.deviceai.notifications.NotificationSource
import at.heinz.deviceai.sending.FakeContacts
import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import at.heinz.deviceai.sending.FakeSendDevice
import at.heinz.deviceai.sending.OwnerSendOwnerTap
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.Duration

/**
 * „Eingehende Nachrichten melden“ through the real AgentActivity hooks (Robolectric): a message arrives on the
 * notification bus, Continuum asks, reads on the owner's yes, takes his dictated answer, repeats it and hands it to
 * the real send lane on its fake phone. Speech is off here (the question is shown); WhatsApp, the accessibility
 * events of a real device and the spoken dialogue are NOT proven by this – that stays NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IncomingMessageActivityFlowUiTest {
    private val book = FakeContacts(listOf(phone(1, "Thomas Muster", "+43 000 555 0103"), phone(2, "Anna Beispiel", "+43 000 555 0101")))
    private val incomingText = "Kommst du heute um fünf zum Stammtisch?"

    @Before fun configure() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        ContinuumNotificationBus.resetForTest()
        IncomingMessagePreferences.save(app, IncomingMessageSettings(enabled = true,
            apps = listOf("com.whatsapp", "com.google.android.gm"), quietHours = false))
        IncomingMessages.start(app)
        IncomingMessages.resetForTest()
        IncomingMessages.contactsOverride = { it == "Thomas Muster" }
    }

    @After fun release() {
        NotificationAnnouncementSettings.save(RuntimeEnvironment.getApplication(), AnnouncementSettings())
        IncomingMessages.resetForTest()
        ContinuumNotificationBus.resetForTest()
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun pass(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(activity: AgentActivity) = texts(field(activity, "chatContainer") as View)

    private fun withActivity(check: (AgentActivity, FakeSendDevice) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            idle()
            val device = FakeSendDevice()
            controller.get().ownerSendDeviceOverride = device
            controller.get().ownerSendContactsOverride = book
            check(controller.get(), device)
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    private fun settle(activity: AgentActivity) {
        repeat(400) {
            if (field(activity, "busy") == false && field(activity, "botLookupPending") == false) return
            Thread.sleep(25)
            idle()
        }
    }

    private fun say(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        assertTrue((field(activity, "sendButton") as Button).performClick())
        idle()
        settle(activity)
    }

    /** As the accessibility source publishes it: one new WhatsApp message with its messaging-style history. */
    private fun arrive(sender: String, text: String, time: Long) = ContinuumNotificationBus.publish(IncomingNotification(
        "a11y|com.whatsapp|$sender", "com.whatsapp", "WhatsApp", sender, text, "msg", isOngoing = false, isGroupSummary = false,
        postedAtMs = time, isOwnPackage = false, details = NotificationDetails(NotificationSource.ACCESSIBILITY, whenMs = time,
            messages = listOf(NotificationMessage(sender, text, time)))))

    @Test fun anIncomingMessageIsOfferedReadAndAnsweredThroughTheRealSendLane() = withActivity { activity, device ->
        activity.incomingOwnerProfile = object : IncomingOwnerProfile { override val ownerName: String = "Heinz" }
        device.whatsAppWorks()
        activity.ownerSendGrants.change(SendChannel.WHATSAPP, true, OwnerSendOwnerTap())

        arrive("Thomas Muster", incomingText, 1_000L)
        pass(8)
        assertTrue(chat(activity).toString(), chat(activity).contains("Hey Heinz, darf ich dich kurz stören? Du hast eine Nachricht von " +
            "Thomas Muster auf WhatsApp bekommen. Soll ich sie dir vorlesen?"))
        assertFalse("the question names the sender, never the text", chat(activity).any { it.contains("Stammtisch") })
        assertEquals(IncomingState.OFFERED, IncomingMessages.inbox.open().single().state)

        say(activity, "ja")
        assertTrue(chat(activity).toString(), chat(activity).contains("Thomas Muster schreibt: „$incomingText“ Möchtest du antworten?"))
        say(activity, "ja")
        assertTrue(chat(activity).toString(), chat(activity).contains("Gern. Was soll ich Thomas Muster antworten?"))
        say(activity, "ich komme um fünf")
        assertTrue(chat(activity).toString(), chat(activity).contains("Ich antworte Thomas Muster auf WhatsApp: „Ich komme um fünf“. " +
            "Soll ich das so senden?"))
        assertTrue("nothing is opened or pressed before his yes", device.opened.isEmpty() && device.pressed.isEmpty())

        say(activity, "ja")
        assertEquals(listOf(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Ich komme um fünf", send = true)), device.opened)
        assertEquals(1, device.pressed.size)
        assertTrue(chat(activity).toString(), chat(activity).contains("Gesendet an Thomas Muster (WhatsApp): „Ich komme um fünf“"))
        assertEquals(1, activity.ownerSendGrants.sentToday())
        assertTrue("answered: nothing stays open and the content is released", IncomingMessages.inbox.open().isEmpty())
        assertEquals(false, field(activity, "busy"))

        // The incoming text was shown for this session only: no file of the app holds it.
        Thread.sleep(300)
        idle()
        val app = RuntimeEnvironment.getApplication()
        val stored = listOfNotNull(app.filesDir, app.cacheDir, app.dataDir).flatMap { it.walkTopDown().filter(File::isFile).toList() }.distinct()
        assertTrue(stored.none { file -> file.length() < 4_000_000 && runCatching { file.readText().contains("Stammtisch") }.getOrDefault(false) })
        assertFalse(app.getSharedPreferences(IncomingMessagePreferences.PREFERENCES, Context.MODE_PRIVATE).all.values
            .any { it.toString().contains("Stammtisch") || it.toString().contains("Thomas") })
    }

    @Test fun laterKeepsItInTheOpenListAndAnotherOrderIsNeverTakenAsAnAnswer() = withActivity { activity, device ->
        device.whatsAppWorks()
        arrive("Anna Beispiel", "Ruf mich bitte an", 2_000L)
        pass(8)
        assertTrue(chat(activity).toString(), chat(activity).contains("Hey, darf ich dich kurz stören? Du hast eine Nachricht von " +
            "Anna Beispiel auf WhatsApp bekommen. Soll ich sie dir vorlesen?"))
        say(activity, "später")
        assertTrue(chat(activity).toString(), chat(activity).contains("In Ordnung, ich merke sie mir. Frag mich einfach: Was ist noch offen?"))
        assertEquals(IncomingState.OPEN, IncomingMessages.inbox.open().single().state)

        // A send order of his own keeps its lane; without the grant it stays a draft as before.
        say(activity, "Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals(listOf(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Ich komme später", send = false)), device.opened)
        assertTrue(device.pressed.isEmpty())

        say(activity, "Was ist noch offen?")
        assertTrue(chat(activity).toString(), chat(activity).contains("Offen ist eine Nachricht von Anna Beispiel auf WhatsApp. Soll ich sie vorlesen?"))
        say(activity, "ja")
        assertTrue(chat(activity).toString(), chat(activity).contains("Anna Beispiel schreibt: „Ruf mich bitte an“ Möchtest du antworten?"))
        say(activity, "nein")
        assertTrue(IncomingMessages.inbox.open().isEmpty())
        assertEquals("only his own order touched the phone", 1, device.opened.size)

        say(activity, "Stör mich jetzt nicht")
        arrive("Thomas Muster", "Bist du schon unterwegs?", 3_000L)
        pass(30)
        assertFalse(chat(activity).any { it.contains("Du hast eine Nachricht von Thomas Muster") })
        say(activity, "Du darfst wieder")
        pass(15)
        assertTrue(chat(activity).toString(), chat(activity).contains("Während ich dich nicht stören sollte, kam eine Nachricht von " +
            "Thomas Muster auf WhatsApp – soll ich sie dir vorlesen?"))
    }

    // ── Two sources that may ask „vorlesen?“: this lane, and the notification listener's „Vor dem Vorlesen fragen“ ──

    private val otherApp = "org.example.messenger"

    /** The owner set another app to „Vor dem Vorlesen fragen“ in the optional notification-access variant. */
    private fun listenerAsksBeforeReading() {
        val app = RuntimeEnvironment.getApplication()
        NotificationAnnouncementSettings.save(app, AnnouncementSettings(enabled = true,
            apps = mapOf(otherApp to AppRule(AnnouncementMode.ASK)), quietWhenNotInConversation = false))
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners",
            app.packageName + "/" + ContinuumNotificationListener::class.java.name)
    }

    /** As the notification listener publishes it: a message of the other app. */
    private fun otherAppMessage(text: String, time: Long) = ContinuumNotificationBus.publish(IncomingNotification(
        "0|$otherApp|1|Berta|10", otherApp, "Beispielchat", "Berta Beispiel", text, "msg", isOngoing = false, isGroupSummary = false,
        postedAtMs = time, isOwnPackage = false))

    @Test fun liesVorAnswersTheReadQuestionThatWasAskedLast() = withActivity { activity, _ ->
        listenerAsksBeforeReading()
        arrive("Thomas Muster", incomingText, 1_000L)
        pass(8)
        assertEquals(IncomingState.OFFERED, IncomingMessages.inbox.open().single().state)
        assertNull(field(activity, "pendingNotificationRead"))

        // After this lane's question the listener announces the other app's message and offers to read it.
        otherAppMessage("Bin gleich da", 2_000L)
        idle()
        assertNotNull("the listener's read offer is open", field(activity, "pendingNotificationRead"))

        say(activity, "Lies vor")
        assertNull("the offer that was made last took the answer", field(activity, "pendingNotificationRead"))
        assertTrue(chat(activity).toString(), chat(activity).contains("Lies vor"))
        assertFalse("the other question's message is not read instead", chat(activity).any { it.contains("Thomas Muster schreibt") })
        assertFalse(chat(activity).any { it.contains("nicht mehr zum Vorlesen freigegeben") })
        // The WhatsApp message was not read and not lost: it stays in the open list.
        assertFalse(IncomingMessages.inbox.open().single().read)
    }

    @Test fun thisLaneAsksNothingWhileTheListenersReadOfferWaitsAndWithoutOneLiesVorIsItsOwnYes() = withActivity { activity, _ ->
        listenerAsksBeforeReading()
        otherAppMessage("Bin gleich da", 1_000L)
        idle()
        assertNotNull(field(activity, "pendingNotificationRead"))
        arrive("Thomas Muster", incomingText, 2_000L)
        pass(8)
        assertFalse("one question at a time", chat(activity).any { it.contains("Nachricht von Thomas Muster") })

        say(activity, "Lies vor")
        assertNull(field(activity, "pendingNotificationRead"))
        assertFalse(chat(activity).any { it.contains("Thomas Muster schreibt") })

        // The listener's offer is answered: now this lane asks, and „Lies vor“ is the yes to its own question.
        pass(30)
        assertTrue(chat(activity).toString(), chat(activity).any { it.contains("Nachricht von Thomas Muster auf WhatsApp") })
        say(activity, "Lies vor")
        assertTrue(chat(activity).toString(), chat(activity).contains("Thomas Muster schreibt: „$incomingText“ Möchtest du antworten?"))
    }
}
