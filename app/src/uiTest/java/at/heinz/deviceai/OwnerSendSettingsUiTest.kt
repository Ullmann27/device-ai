package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import at.heinz.deviceai.sending.AndroidOwnerSendDevice
import at.heinz.deviceai.sending.OwnerSendGrants
import at.heinz.deviceai.sending.OwnerSendPreferences
import at.heinz.deviceai.sending.OwnerSendSettingsDialog
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
import at.heinz.deviceai.ui.ContinuumSettingsHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.LocalDate

/** „Selbst senden“: the standing grant per channel – default off, granted through a clear dialog, stored, revocable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h740dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerSendSettingsUiTest {
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun grants(context: Context) = OwnerSendGrants(OwnerSendPreferences(context)) { LocalDate.of(2026, 11, 18) }
    private fun tagged(dialog: AlertDialog, tag: String) = views(dialog.window!!.decorView).single { it.tag == tag } as TextView

    @Test fun theOwnerGrantsEachChannelThroughAClearDialogAndRevokesItInTheSamePlace() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        var diagnoses = 0
        val grants = grants(activity)
        val dialog = OwnerSendSettingsDialog.show(activity, grants, accessibilityOn = { false }, contactsReadable = { false },
            startDiagnosis = { diagnoses++ }, lastDiagnosis = { null })
        try {
            idle()
            SendChannel.entries.forEach { channel ->
                assertEquals(channel.settingLabel + ": AUS – nur Entwürfe", tagged(dialog, OwnerSendSettingsDialog.TAG_STATUS + channel.name).text.toString())
                assertFalse(grants.enabled(channel))
            }
            assertTrue(tagged(dialog, OwnerSendSettingsDialog.TAG_STATE).text.contains("Heute selbst gesendet: 0 von 30"))
            assertTrue(tagged(dialog, OwnerSendSettingsDialog.TAG_STATE).text.contains("Bedienungshilfe: AUS"))
            // Switching on asks with the full explanation; cancelling changes nothing.
            tagged(dialog, OwnerSendSettingsDialog.TAG_TOGGLE + "WHATSAPP").performClick()
            idle()
            val question = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            assertEquals(OwnerSendGrants.grantDialogText(SendChannel.WHATSAPP), shadowOf(question).message.toString())
            assertEquals("WhatsApp-Nachrichten selbst senden?", shadowOf(question).title.toString())
            question.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertFalse(grants.enabled(SendChannel.WHATSAPP))
            tagged(dialog, OwnerSendSettingsDialog.TAG_TOGGLE + "WHATSAPP").performClick()
            idle()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertTrue(grants.enabled(SendChannel.WHATSAPP))
            assertFalse("the WhatsApp grant is not a Gmail grant", grants.enabled(SendChannel.GMAIL))
            assertEquals("WhatsApp-Nachrichten selbst senden: EIN (seit 2026-11-18)",
                tagged(dialog, OwnerSendSettingsDialog.TAG_STATUS + "WHATSAPP").text.toString())
            // Stored: the app after a restart reads the same grant without asking again.
            assertTrue(grants(RuntimeEnvironment.getApplication()).enabled(SendChannel.WHATSAPP))
            // Revoking needs no second question and takes effect at once.
            assertTrue(tagged(dialog, OwnerSendSettingsDialog.TAG_TOGGLE + "WHATSAPP").text.startsWith("FREIGABE WIDERRUFEN"))
            tagged(dialog, OwnerSendSettingsDialog.TAG_TOGGLE + "WHATSAPP").performClick()
            idle()
            assertFalse(grants(RuntimeEnvironment.getApplication()).enabled(SendChannel.WHATSAPP))
            assertEquals("WhatsApp-Nachrichten selbst senden: AUS – nur Entwürfe",
                tagged(dialog, OwnerSendSettingsDialog.TAG_STATUS + "WHATSAPP").text.toString())
            tagged(dialog, OwnerSendSettingsDialog.TAG_DIAGNOSIS).performClick()
            idle()
            assertEquals(1, diagnoses)
            assertFalse(dialog.isShowing)
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }

    @Test fun theSettingsHubShowsSelfSendUnderDeviceAndControl() {
        val entry = ContinuumSettingsHub.Entry.SELF_SEND
        assertEquals("Gerät & Kontrolle", entry.group)
        assertEquals("Selbst senden", entry.label)
        assertTrue(entry.detail.contains("WhatsApp") && entry.detail.contains("Gmail") && entry.detail.contains("Widerruf"))
    }

    @Test fun theDraftsArePreparedByIntentForExactlyTheTargetApp() {
        val chat = AndroidOwnerSendDevice.whatsAppIntent("430005550101", "Ich komme später & bringe 2 Brote mit?")
        assertEquals(Intent.ACTION_VIEW, chat.action)
        assertEquals("com.whatsapp", chat.`package`)
        assertEquals("wa.me", chat.data!!.host)
        assertEquals("/430005550101", chat.data!!.path)
        assertEquals("Ich komme später & bringe 2 Brote mit?", chat.data!!.getQueryParameter("text"))
        val file = OwnerSavedFile("Bericht.pdf", "Download", "content://fiktiv/1", "application/pdf")
        val mail = AndroidOwnerSendDevice.gmailIntent(SendPlan.Mail("anna.beispiel@example.org", null, "Bericht", "", file, send = true))
        assertEquals(Intent.ACTION_SEND, mail.action)
        assertEquals("com.google.android.gm", mail.`package`)
        assertNull("a package and a mail selector exclude each other", mail.selector)
        assertEquals(listOf("anna.beispiel@example.org"), mail.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals("Bericht", mail.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(mail.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun theDiagnosisIsKeptInAnAppPrivateFile() {
        val app = RuntimeEnvironment.getApplication()
        assertNull(AndroidOwnerSendDevice.lastDiagnosis(app))
        AndroidOwnerSendDevice.saveDiagnosis(app, "CONTINUUM SENDE-DIAGNOSE\nfiktiv")
        assertEquals("CONTINUUM SENDE-DIAGNOSE\nfiktiv", AndroidOwnerSendDevice.lastDiagnosis(app))
        assertTrue(java.io.File(app.filesDir, "owner-send/sende-diagnose.txt").isFile)
    }
}
