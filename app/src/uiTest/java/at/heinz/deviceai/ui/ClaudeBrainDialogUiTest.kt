package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.localmodel.BrainKind
import at.heinz.deviceai.localmodel.ClaudeBrain
import at.heinz.deviceai.localmodel.ClaudeBrainSettings
import at.heinz.deviceai.localmodel.ClaudeConsentNotice
import at.heinz.deviceai.localmodel.ClaudeEffort
import at.heinz.deviceai.localmodel.ClaudeModel
import at.heinz.deviceai.localmodel.ClaudeModelRuntime
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint
import at.heinz.deviceai.localmodel.MapStorage
import at.heinz.deviceai.localmodel.testBrain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog

/** The "Gehirn" settings: selection, key entry, consent and the visible status. Nothing here sends a request. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ClaudeBrainDialogUiTest {
    private val key = FakeAnthropicEndpoint.TEST_KEY

    private class Host : ClaudeBrainDialog.Host {
        var busy = false
        val changes = mutableListOf<BrainKind>()
        val log = mutableListOf<String>()
        override fun busy() = busy
        override fun brainChanged(previous: BrainKind) { changes += previous }
        override fun log(line: String) { log += line }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun texts(view: View): List<String> = when (view) {
        is EditText -> emptyList()
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun <T : View> AlertDialog.tagged(tag: String): T = window!!.decorView.findViewWithTag(tag)
    private fun AlertDialog.tap(tag: String) { tagged<View>(tag).performClick(); idle() }
    private fun AlertDialog.text(tag: String) = tagged<TextView>(tag).text.toString()
    private fun latest(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
    private fun press(dialog: AlertDialog, button: Int) { dialog.getButton(button).performClick(); idle() }

    private fun open(brain: ClaudeBrain, host: Host): AlertDialog {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return ClaudeBrainDialog.show(activity, brain, host).also { idle() }
    }

    @Test fun localIsTheDefaultAndClaudeNeedsKeyThenExplicitConsent() {
        val storage = MapStorage()
        val brain = testBrain(storage = storage)
        val host = Host()
        val dialog = open(brain, host)
        assertTrue(dialog.text("claude-brain-status").startsWith("Aktives Gehirn: LOKAL"))
        assertTrue(dialog.tagged<TextView>("claude-brain-local").isSelected)
        assertEquals("✓ " + ClaudeBrainPresentation.LOCAL_LABEL, dialog.text("claude-brain-local"))
        assertEquals(ClaudeBrainPresentation.CLAUDE_LABEL, dialog.text("claude-brain-claude"))

        // 1. Without a key: nothing is selected, the key dialog opens.
        dialog.tap("claude-brain-claude")
        assertFalse(brain.claudeSelected)
        val keyDialog = latest()
        assertNotSame(dialog, keyDialog)
        val field = keyDialog.tagged<EditText>("claude-key-input")
        // Masked input, no suggestions, and a secure window: no screenshot or recents preview shows the key.
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, field.inputType)
        assertTrue(keyDialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        // A wrong key is not stored.
        field.setText("sk-or-v1-0000000000000000")
        press(keyDialog, AlertDialog.BUTTON_POSITIVE)
        assertFalse(brain.hasKey())
        assertTrue(dialog.text("claude-brain-message").contains("Es wurde nichts gespeichert"))
        assertEquals("the field is emptied after use", "", field.text.toString())

        dialog.tap("claude-key-enter")
        val second = latest()
        second.tagged<EditText>("claude-key-input").setText(key)
        press(second, AlertDialog.BUTTON_POSITIVE)
        assertTrue(brain.hasKey())
        assertTrue(dialog.text("claude-brain-message").contains("Es wurde nichts gesendet"))
        assertTrue(dialog.text("claude-brain-status").contains("Schlüssel gespeichert"))
        assertEquals("", second.tagged<EditText>("claude-key-input").text.toString())
        // Stored encrypted; no visible text anywhere contains the key or a part of it.
        assertTrue(storage.values.values.none { it.contains("sk-ant") })
        assertTrue(texts(dialog.window!!.decorView).none { it.contains("sk-ant-test") || it.contains(key.takeLast(6)) })

        // 2. With a key but without consent: the notice comes first; declining changes nothing.
        dialog.tap("claude-brain-claude")
        val consent = latest()
        assertEquals(ClaudeConsentNotice.TEXT, consent.tagged<TextView>("claude-consent-text").text.toString())
        assertEquals(ClaudeConsentNotice.CONFIRM, consent.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        assertFalse(consent.tagged<CheckBox>("claude-consent-share-memory").isChecked)
        assertFalse("showing the notice is no consent", brain.settings.consentGiven)
        press(consent, AlertDialog.BUTTON_NEGATIVE)
        assertFalse(brain.settings.consentGiven)
        assertFalse(brain.claudeSelected)
        assertTrue(host.changes.isEmpty())

        // 3. Explicit confirmation activates Claude and the visible marker changes.
        dialog.tap("claude-brain-claude")
        press(latest(), AlertDialog.BUTTON_POSITIVE)
        assertTrue(brain.settings.consentGiven)
        assertTrue(brain.claudeSelected)
        assertFalse(brain.settings.shareLocalMemory)
        assertTrue(dialog.text("claude-brain-status").startsWith("Aktives Gehirn: CONTINUUM · CLOUD · claude-opus-5-5"))
        assertTrue(dialog.tagged<TextView>("claude-brain-claude").isSelected)
        assertFalse(dialog.tagged<TextView>("claude-brain-local").isSelected)
        assertEquals(listOf(BrainKind.LOCAL), host.changes)
        assertEquals("GEHIRN · CONTINUUM · CLOUD · claude-opus-5-5", host.log.last())

        // 4. Model, depth of thinking and the separate memory opt-in.
        dialog.tap("claude-model:claude-sonnet-5-5")
        assertEquals(ClaudeModel.SONNET_5_5, brain.settings.model)
        dialog.tap("claude-effort:HIGH")
        assertEquals(ClaudeEffort.HIGH, brain.settings.effortOverride)
        dialog.tap("claude-effort:auto")
        assertNull(brain.settings.effortOverride)
        assertTrue(dialog.text("claude-share-memory").endsWith("AUS"))
        dialog.tap("claude-share-memory")
        assertTrue(brain.settings.shareLocalMemory)
        assertTrue(dialog.text("claude-share-memory").endsWith("EIN"))

        // 5. Back to local: one tap, no restart, and the host is told which brain was active before.
        dialog.tap("claude-brain-local")
        assertFalse(brain.claudeSelected)
        // Activation (before: LOCAL), model change (CLAUDE stays), switch back (before: CLAUDE).
        assertEquals(listOf(BrainKind.LOCAL, BrainKind.CLAUDE, BrainKind.CLAUDE), host.changes)
        assertTrue(dialog.text("claude-brain-status").startsWith("Aktives Gehirn: LOKAL"))
        assertTrue(dialog.text("claude-brain-message").contains("geht keine Nachricht mehr an Claude"))
        assertTrue(host.log.none { it.contains("sk-ant") })
    }

    @Test fun brainIsNotSwitchedUnderARunningTurnAndRevokingConsentReturnsToLocal() {
        val brain = testBrain()
        assertTrue(brain.saveKey(key))
        assertTrue(brain.confirmConsent(shareLocalMemory = true))
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        val host = Host().apply { busy = true }
        val dialog = open(brain, host)
        dialog.tap("claude-brain-local")
        assertTrue("a running turn keeps its brain", brain.claudeSelected)
        assertTrue(dialog.text("claude-brain-message").contains("STOP"))
        dialog.tap("claude-model:claude-haiku-5-5")
        assertEquals(ClaudeModel.OPUS_5_5, brain.settings.model)

        host.busy = false
        dialog.tap("claude-consent")
        val consent = latest()
        assertEquals("ZUSTIMMUNG WIDERRUFEN", consent.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
        press(consent, AlertDialog.BUTTON_NEUTRAL)
        assertFalse(brain.settings.consentGiven)
        assertFalse(brain.settings.shareLocalMemory)
        assertFalse(brain.claudeSelected)
        assertEquals(listOf(BrainKind.CLAUDE), host.changes)
        assertTrue(dialog.text("claude-brain-status").contains("Zustimmung zur Datenübertragung: nicht erteilt"))
    }

    @Test fun deletingTheKeyNeverSwitchesByItselfButOffersIt() {
        val storage = MapStorage()
        val brain = testBrain(storage = storage)
        assertTrue(brain.saveKey(key))
        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        val host = Host()
        val dialog = open(brain, host)
        dialog.tap("claude-key-delete")
        press(latest(), AlertDialog.BUTTON_POSITIVE)
        assertFalse(brain.hasKey())
        assertTrue(storage.values.isEmpty())
        assertTrue("no silent switch", brain.claudeSelected)
        assertTrue(dialog.text("claude-brain-status").contains("Ich schalte nicht von selbst um"))
        val offer = latest()
        assertEquals("AUF LOKAL UMSCHALTEN", offer.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        press(offer, AlertDialog.BUTTON_POSITIVE)
        assertFalse(brain.claudeSelected)
        assertEquals(BrainKind.CLAUDE, host.changes.single())
        // Without a key the connection test sends nothing.
        dialog.tap("claude-key-test")
        assertTrue(dialog.text("claude-brain-message").contains("es wurde nichts gesendet"))
    }

    @Test fun monthlyLimitAndCostTextsAreShown() {
        val brain = testBrain()
        val dialog = open(brain, Host())
        val usage = dialog.text("claude-brain-usage")
        assertTrue(usage.contains("25,00 USD Monatslimit"))
        assertTrue(usage.contains("Stand 03.11.2026, kann sich ändern"))
        assertTrue(usage.contains("Claude Haiku 5.5: 0,10 / 0,50 USD"))
        dialog.tap("claude-limit")
        val limit = latest()
        limit.tagged<EditText>("claude-limit-input").setText("5")
        press(limit, AlertDialog.BUTTON_POSITIVE)
        assertEquals(500, brain.settings.monthlyLimitCents)
        assertTrue(dialog.text("claude-brain-usage").contains("5,00 USD Monatslimit"))

        assertEquals(2_500, ClaudeBrainPresentation.limitCents(" 25 "))
        assertNull(ClaudeBrainPresentation.limitCents("0"))
        assertNull(ClaudeBrainPresentation.limitCents("25,50"))
        assertNull(ClaudeBrainPresentation.limitCents("100000"))
        assertEquals("● CONTINUUM · CLOUD", ClaudeBrainPresentation.BADGE)
        assertEquals("CONTINUUM · CLOUD", ClaudeBrainPresentation.DASHBOARD_MODE)
        assertTrue(ClaudeBrainPresentation.probe(ClaudeModelRuntime.Probe.Reachable("claude-opus-5-5", 14, 3))
            .startsWith("Verbindung in Ordnung: claude-opus-5-5"))
        assertTrue(ClaudeBrainPresentation.probe(ClaudeModelRuntime.Probe.Failed("CLAUDE_NO_CREDIT")).contains("Guthaben"))
        assertTrue(ClaudeBrainPresentation.status(ClaudeBrainSettings(), keyPresent = false)
            .contains("nichts geht an Claude"))
    }
}
