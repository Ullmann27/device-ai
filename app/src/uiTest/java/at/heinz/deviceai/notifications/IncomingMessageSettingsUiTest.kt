package at.heinz.deviceai.notifications

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumSettingsHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration

/** „Eingehende Nachrichten melden“: off by default, switched on through a plain explanation, the owner's apps and options. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h740dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class IncomingMessageSettingsUiTest {
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun tagged(dialog: AlertDialog, tag: String) = views(dialog.window!!.decorView).single { it.tag == tag } as TextView
    private fun option(dialog: AlertDialog, key: String) = tagged(dialog, IncomingMessageSettingsDialog.TAG_OPTION + key)

    @Test fun theOwnerSwitchesItOnThroughAPlainExplanationAndChoosesAppsOptionsAndLists() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        ContinuumNotificationBus.resetForTest()
        IncomingMessagePreferences.save(activity, IncomingMessageSettings())
        IncomingMessages.start(activity)
        IncomingMessages.resetForTest()
        var accessibility = false
        var changes = 0
        val installed = listOf("com.whatsapp" to "WhatsApp", "com.google.android.gm" to "Gmail", "org.thoughtcrime.securesms" to "Signal")
        val dialog = IncomingMessageSettingsDialog.show(activity, { accessibility }, { installed }) { changes++ }
        fun stored() = IncomingMessagePreferences.load(activity)
        try {
            idle()
            assertEquals("Eingehende Nachrichten melden: AUS – Continuum sieht keine Benachrichtigung",
                tagged(dialog, IncomingMessageSettingsDialog.TAG_STATUS).text.toString())
            assertTrue(tagged(dialog, IncomingMessageSettingsDialog.TAG_SERVICE).text.contains("Bedienungshilfe ist AUS"))
            assertTrue(tagged(dialog, IncomingMessageSettingsDialog.TAG_APPS).text.contains("noch keine App gewählt"))

            // Switching on tells in plain words what Continuum then sees; cancelling changes nothing.
            tagged(dialog, IncomingMessageSettingsDialog.TAG_TOGGLE).performClick()
            idle()
            val question = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            assertEquals("Eingehende Nachrichten melden?", shadowOf(question).title.toString())
            assertEquals(IncomingMessagePreferences.EXPLANATION, shadowOf(question).message.toString())
            question.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertFalse(stored().enabled)
            assertEquals(0, changes)

            accessibility = true
            tagged(dialog, IncomingMessageSettingsDialog.TAG_TOGGLE).performClick()
            idle()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertTrue(stored().enabled)
            assertEquals("the suggested apps that are installed", listOf("com.whatsapp", "com.google.android.gm"), stored().apps)
            assertEquals("Eingehende Nachrichten melden: EIN", tagged(dialog, IncomingMessageSettingsDialog.TAG_STATUS).text.toString())
            assertEquals("AUSSCHALTEN", tagged(dialog, IncomingMessageSettingsDialog.TAG_TOGGLE).text.toString())
            assertTrue(tagged(dialog, IncomingMessageSettingsDialog.TAG_APPS).text.contains("WhatsApp, Gmail"))
            assertTrue(tagged(dialog, IncomingMessageSettingsDialog.TAG_SERVICE).text.contains("Continuum-Bedienungshilfe ist ein"))
            assertEquals(1, changes)

            // The app list is the owner's: another messenger is added.
            tagged(dialog, IncomingMessageSettingsDialog.TAG_APPS).performClick()
            idle()
            val chooser = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            assertEquals(listOf("Gmail", "WhatsApp", "Signal"), (0 until chooser.listView.adapter.count).map { chooser.listView.adapter.getItem(it).toString() })
            shadowOf(chooser).clickOnItem(2)
            chooser.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertEquals(setOf("com.whatsapp", "com.google.android.gm", "org.thoughtcrime.securesms"), stored().apps.toSet())

            // Options: groups and silent notifications are off until he says otherwise; unclear is reported briefly.
            assertEquals("GRUPPEN-CHATS MELDEN · aus", option(dialog, "groups").text.toString())
            assertEquals("STUMME BENACHRICHTIGUNGEN MELDEN · aus", option(dialog, "silent").text.toString())
            assertEquals("UNKLARE NACHRICHTEN KURZ MELDEN · an", option(dialog, "unclear").text.toString())
            assertEquals("RUHEZEIT 22 BIS 7 UHR · an", option(dialog, "quiet").text.toString())
            assertEquals("HINWEIS-KARTE, WENN DIE APP NICHT OFFEN IST · an", option(dialog, "card").text.toString())
            assertEquals("KURZE ANSAGE, WENN DIE APP NICHT OFFEN IST · aus", option(dialog, "voice").text.toString())
            listOf("groups", "silent", "unclear", "quiet", "card", "voice").forEach { option(dialog, it).performClick(); idle() }
            stored().let {
                assertTrue(it.groups && it.silentNotifications && it.backgroundVoice)
                assertFalse(it.quietHours || it.backgroundCard)
                assertEquals(UnclearHandling.SILENT, it.unclear)
            }
            assertEquals("GRUPPEN-CHATS MELDEN · an", option(dialog, "groups").text.toString())

            // His two lists, one entry per line.
            tagged(dialog, IncomingMessageSettingsDialog.TAG_ALWAYS).performClick()
            idle()
            val list = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            (views(list.window!!.decorView).single { it.tag == "incoming-list-field" } as EditText).setText("Bezirksgericht\nOrdination Dr. Steiner\n\n")
            list.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertEquals(listOf("Bezirksgericht", "Ordination Dr. Steiner"), stored().always)
            assertTrue(tagged(dialog, IncomingMessageSettingsDialog.TAG_ALWAYS).text.contains("Bezirksgericht, Ordination Dr. Steiner"))

            // A held message is forgotten on request and when the feature is switched off – at once, without a question.
            fun arrive(text: String) {
                IncomingMessages.inbox.onNotification(IncomingNotification("k", "com.whatsapp", "WhatsApp", "Anna", text, "msg", false, false, 0L, false))
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            }
            arrive("Hallo")
            assertEquals(1, IncomingMessages.inbox.open().size)
            tagged(dialog, IncomingMessageSettingsDialog.TAG_CLEAR).performClick()
            idle()
            assertTrue(IncomingMessages.inbox.open().isEmpty())
            arrive("Bist du da?")
            assertEquals(1, IncomingMessages.inbox.open().size)
            tagged(dialog, IncomingMessageSettingsDialog.TAG_TOGGLE).performClick()
            idle()
            assertFalse(stored().enabled)
            assertTrue(IncomingMessages.inbox.open().isEmpty())
            assertEquals("the owner's lists and apps are kept for the next time", 3, stored().apps.size)
            // Stored: a fresh read of the preferences gives the same choices.
            assertEquals(stored(), IncomingMessageSettingsCodec.decode(activity.getSharedPreferences(IncomingMessagePreferences.PREFERENCES, 0)
                .getString("incoming-settings-v1", null)))
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            dialog.dismiss()
            IncomingMessages.resetForTest()
            IncomingMessagePreferences.save(activity, IncomingMessageSettings())
            controller.pause().stop().destroy()
        }
    }

    @Test fun theSettingsHubShowsTheSwitchUnderDeviceAndControl() {
        val entry = ContinuumSettingsHub.Entry.INCOMING_MESSAGES
        assertEquals("Gerät & Kontrolle", entry.group)
        assertEquals("Eingehende Nachrichten melden", entry.label)
        assertTrue(entry.detail.contains("WhatsApp") && entry.detail.contains("Gmail") && entry.detail.contains("ohne Werbung"))
        assertEquals("off by default", false, IncomingMessageSettings().enabled)
    }
}
