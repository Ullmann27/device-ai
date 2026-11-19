package at.heinz.deviceai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.sending.FakeContacts
import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import at.heinz.deviceai.sending.FakeSendDevice
import at.heinz.deviceai.sending.OwnerSendDirector
import at.heinz.deviceai.sending.OwnerSendOwnerTap
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * The owner send lane in the real AgentActivity (Robolectric): owner turn intake, busy job, chat replies, the
 * contacts explanation and the Gmail take-over of the work tools' mail order. The phone boundary (WhatsApp, Gmail,
 * accessibility) is the fake of the host tests; nothing here proves the real apps – that stays NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerSendActivityFlowUiTest {
    private val book = FakeContacts(listOf(
        phone(1, "Thomas Muster", "+43 000 555 0103"),
        phone(2, "Anna Beispiel", "+43 000 555 0101"),
        phone(3, "Anna Muster", "+43 000 555 0102")))

    @Before fun configure() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
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

    /**
     * Types into the real input field and presses the real send button. A sentence with a comma is first looked up
     * as a bot address (asynchronously) and handed on when no bot has that name; then the busy lane has to finish.
     */
    private fun say(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        assertTrue((field(activity, "sendButton") as Button).performClick())
        idle()
        settle(activity)
    }

    @Test fun withoutTheGrantTheOrderPreparesADraftAndWithItTheMessageIsSentAndReported() = withActivity { activity, device ->
        activity.ownerSendContactsOverride = book
        device.whatsAppWorks()
        assertFalse("default off", activity.ownerSendGrants.enabled(SendChannel.WHATSAPP))
        say(activity, "Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals(chat(activity).toString(),
            listOf(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Ich komme später", send = false)), device.opened)
        assertTrue(device.pressed.isEmpty())
        assertTrue(chat(activity).toString(), chat(activity).any {
            it.startsWith("Der Entwurf an Thomas Muster steht in WhatsApp bereit: „Ich komme später“. Gesendet habe ich nichts") })
        assertNull("no question and no approval dialog", ShadowAlertDialog.getLatestAlertDialog())

        activity.ownerSendGrants.change(SendChannel.WHATSAPP, true, OwnerSendOwnerTap())
        say(activity, "Schreib Thomas auf WhatsApp, ich komme später")
        assertEquals(1, device.pressed.size)
        assertTrue(chat(activity).toString(), chat(activity).contains("Gesendet an Thomas Muster (WhatsApp): „Ich komme später“"))
        assertEquals(1, device.returned)
        assertEquals(1, activity.ownerSendGrants.sentToday())
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertEquals(false, field(activity, "busy"))

        // Fixed rule (b) in the real chat: two „Anna“ are a question, the answer decides.
        say(activity, "Schreib Anna auf WhatsApp, bin gleich da")
        assertTrue(chat(activity).toString(), chat(activity).contains(
            "Zu „Anna“ gibt es mehrere passende Kontakte: Anna Beispiel, Anna Muster. Welchen meinst du?"))
        assertEquals(1, device.pressed.size)
        say(activity, "die zweite")
        assertTrue(chat(activity).toString(), chat(activity).contains("Gesendet an Anna Muster (WhatsApp): „Bin gleich da“"))
        assertEquals(2, device.pressed.size)
        // The reading order and the plain app order still belong to the other lanes.
        assertFalse(activity.ownerSend.handle("Nachricht von Anna vorlesen"))
    }

    @Test fun contactsAreExplainedInPlainGermanBeforeAndroidAsksOnce() = withActivity { activity, device ->
        device.whatsAppWorks()
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CONTACTS)
        activity.ownerSendGrants.change(SendChannel.WHATSAPP, true, OwnerSendOwnerTap())
        say(activity, "Schreib Thomas auf WhatsApp, ich komme später")
        val explanation = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertEquals("Kontakte lesen", shadowOf(explanation).title.toString())
        assertEquals(OwnerSendDirector.CONTACTS_EXPLANATION, shadowOf(explanation).message.toString())
        assertNull("Android is asked only after the explanation", shadowOf(activity).lastRequestedPermission)
        explanation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertTrue(chat(activity).toString(), chat(activity).contains(OwnerSendDirector.CONTACTS_DENIED))
        assertTrue(device.opened.isEmpty())

        say(activity, "Schreib Thomas auf WhatsApp, ich komme später")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        val asked = requireNotNull(shadowOf(activity).lastRequestedPermission)
        assertEquals(listOf(Manifest.permission.READ_CONTACTS), asked.requestedPermissions.toList())
        // The owner allows it: the order continues by itself. This phone's address book is empty, so nothing is guessed.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CONTACTS)
        activity.onRequestPermissionsResult(asked.requestCode, arrayOf(Manifest.permission.READ_CONTACTS),
            intArrayOf(PackageManager.PERMISSION_GRANTED))
        idle()
        settle(activity)
        assertTrue(chat(activity).toString(), chat(activity).any { it.startsWith("Ich habe keinen Kontakt „Thomas“ gefunden.") })
        assertTrue(device.opened.isEmpty() && device.pressed.isEmpty())
    }

    @Test fun theReportedWhatsAppTurnAndItsClarificationStayInTheSendLane() = withActivity { activity, device ->
        activity.ownerSendContactsOverride = book
        activity.ownerSendGrants.change(SendChannel.WHATSAPP, true, OwnerSendOwnerTap())
        device.whatsAppWorks()
        say(activity, "schreib bitte in WhatsApp meinen Papa guten Morgen")
        assertTrue(chat(activity).any { it.startsWith("Ich habe keinen Kontakt „meinen Papa“ gefunden.") })
        assertTrue(device.pressed.isEmpty())
        say(activity, "Whatsapp")
        assertTrue(activity.ownerSend.awaitingAnswer)
        say(activity, "ich habe dir zum dreimal gesagt der Name heißt Thomas Muster")
        // Changed 19.11.2026 (review B3): the named contact is offered to be remembered as the father, and the first
        // send through the relation is confirmed once, also with the standing grant.
        assertTrue(chat(activity).last() == "Soll ich mir merken, dass Thomas Muster dein Vater ist?")
        assertTrue(device.opened.isEmpty())
        say(activity, "Ja")
        assertTrue(chat(activity).last().endsWith("Soll ich das so senden?"))
        assertTrue(device.pressed.isEmpty())
        say(activity, "Ja")
        assertEquals(listOf(SendPlan.WhatsApp("Thomas Muster", "430005550103", "Guten Morgen", send = true)), device.opened)
        assertEquals(1, device.pressed.size)
        assertEquals(1L, activity.ownerRelations.get(at.heinz.deviceai.sending.OwnerRelation.VATER)?.contactId)
    }

    @Test fun withTheGmailGrantTheMailOrderIsTakenOverAndModelTextWaitsForAClearYes() = withActivity { activity, device ->
        val app = shadowOf(RuntimeEnvironment.getApplication())
        device.gmailWorks()
        // A result in the chat: the honest offline reply of the news order.
        say(activity, "Fass mir die Nachrichten von heute zusammen")
        activity.ownerSendGrants.change(SendChannel.GMAIL, true, OwnerSendOwnerTap())
        app.clearNextStartedActivities()
        say(activity, "Schick das per Mail an anna.muster@example.org")
        assertNull("the generic mail hand-over is not started", app.nextStartedActivity)
        assertTrue(device.opened.isEmpty())
        assertTrue(chat(activity).toString(), chat(activity).any {
            it.startsWith("Ich würde an anna.muster@example.org diese E-Mail über Gmail senden:") && it.contains("Soll ich das so senden?") })
        say(activity, "Ja")
        val plan = device.opened.single() as SendPlan.Mail
        assertEquals("anna.muster@example.org", plan.address)
        assertTrue(plan.body.contains("Internetzugang ist AUS"))
        assertNull(plan.attachment)
        assertEquals(1, device.pressed.size)
        assertTrue(chat(activity).toString(), chat(activity).any { it.startsWith("Gesendet an anna.muster@example.org (Gmail): Betreff „") })
        assertEquals(1, activity.ownerSendGrants.sentToday())
    }
}
