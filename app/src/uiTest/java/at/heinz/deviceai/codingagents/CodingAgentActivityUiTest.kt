package at.heinz.deviceai.codingagents

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.AgentActivity
import at.heinz.deviceai.OwnerWorkToolsDeviceUiTest
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The cloud coding agents in the real AgentActivity (Robolectric): the dialog „Coding-Agenten (Claude, Cloud)“ with
 * its setup, and one complete run – typed into the real input field, sent with the real button, taken by the hook,
 * confirmed with „Ja“, shown in the conversation and the work window. Stand-ins: the Managed Agents account and GitHub
 * (scripted), the token store's cipher. Nothing here proves Anthropic, GitHub, the Android Keystore or the Fold7 –
 * that stays NOT_RUN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CodingAgentActivityUiTest {
    private val rig = Rig()
    private val timelines = CopyOnWriteArrayList<CockpitAnimationEvent.ProcessTimeline>()
    private var panel: AlertDialog? = null

    @Before fun configure() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        AndroidCodingAgents.replaceForTest(rig.controller)
    }

    @After fun restore() = AndroidCodingAgents.replaceForTest(null)

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(activity: AgentActivity) = texts(field(activity, "chatContainer") as View)

    /** Types into the real input field and presses the real send button. */
    private fun type(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        assertTrue((field(activity, "sendButton") as Button).performClick())
        idle()
    }

    private fun openPanel(activity: AgentActivity, sentence: String) {
        type(activity, sentence)
        panel = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(panel!!.isShowing)
    }
    private fun <T : View> tagged(tag: String): T = checkNotNull(panel!!.window!!.decorView.findViewWithTag<T>(tag)) { "no view tagged $tag" }
    private fun tap(tag: String) { assertTrue(tagged<Button>(tag).performClick()); idle() }
    private fun notice() = tagged<TextView>("coding-agent-notice").text.toString()
    private fun status() = tagged<TextView>("coding-agent-status").text.toString()
    private fun panelTexts() = texts(panel!!.window!!.decorView)

    private fun withActivity(check: (AgentActivity) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            idle()
            val activity = controller.get()
            (field(activity, "cockpitBus") as CockpitAnimationBus).subscribe { event ->
                if (event is CockpitAnimationEvent.ProcessTimeline && event.turnId.startsWith("coding-agent-")) timelines += event
            }
            check(activity)
        } finally {
            panel?.dismiss()
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    @Test fun theDialogExplainsStoresTheSetupAndNeverShowsTheToken() = withActivity { activity ->
        openPanel(activity, "Öffne die Coding-Agenten")
        assertEquals(1, chat(activity).count { it == "Öffne die Coding-Agenten" })
        assertTrue(panelTexts().contains(CodingAgentTexts.INTRO))
        assertTrue(status(), status().contains("Zustimmung Coding-Agenten: nicht erteilt"))
        assertTrue(status(), status().contains("GitHub-Token: kein Token gespeichert"))

        // The notice is shown in full and only its confirmation stores the consent.
        tap("coding-agent-consent")
        val consent = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(CodingAgentTexts.CONSENT, shadowOf(consent).message.toString())
        assertFalse(rig.controller.settings.consentGiven)
        assertTrue(consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick())
        idle()
        assertTrue(rig.controller.settings.consentGiven)
        assertTrue(status(), status().contains("Zustimmung Coding-Agenten: erteilt"))

        // The token field is a password field; after saving it is empty and no view carries the token.
        val token = tagged<EditText>("coding-agent-token")
        assertTrue(token.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0)
        token.setText(FAKE_GITHUB_TOKEN)
        tap("coding-agent-token-save")
        assertEquals(CodingAgentTexts.TOKEN_STORED, notice())
        assertEquals("", tagged<EditText>("coding-agent-token").text.toString())
        assertTrue(rig.controller.tokenPresent())
        assertFalse(panelTexts().any { it.contains(FAKE_GITHUB_TOKEN) })
        assertTrue(status(), status().contains("GitHub-Token: Token gespeichert (verschlüsselt, wird nie angezeigt)"))

        // The allow-list takes owner/name only.
        tagged<EditText>("coding-agent-repo").setText("device-ai")
        tap("coding-agent-repo-add")
        assertEquals(CodingAgentTexts.repositoryInvalid("device-ai"), notice())
        tagged<EditText>("coding-agent-repo").setText("https://github.com/Ullmann27/device-ai")
        tap("coding-agent-repo-add")
        assertEquals(listOf("Ullmann27/device-ai"), rig.controller.settings.repositories)
        assertNotNull(tagged<Button>("coding-agent-order:Ullmann27/device-ai"))
        assertNotNull(tagged<Button>("coding-agent-security:Ullmann27/device-ai"))
        assertTrue(panelTexts().contains(CodingAgentTexts.SECURITY_NOTE))

        // A budget above the owner's maximum is refused in the dialog as well.
        tagged<EditText>("coding-agent-budget").setText("9")
        tap("coding-agent-budget-set")
        assertEquals(CodingAgentTexts.budgetOutOfRange(900, rig.controller.settings), notice())
        // Nothing of all this was sent anywhere.
        assertTrue(rig.cloud.calls.isEmpty() && rig.github.calls.isEmpty())
    }

    @Test fun aCompleteRunFromTheOwnersSentenceToTheReportThroughTheRealActivity() = withActivity { activity ->
        rig.controller.giveConsent()
        rig.controller.saveToken(FAKE_GITHUB_TOKEN)
        rig.controller.addRepository("Ullmann27/device-ai")
        rig.github.heads["Ullmann27/device-ai@continuum-agent/run0001"] = "f".repeat(40)
        rig.github.pull = CodingAgentGitHub.PullRequest(912, "https://github.com/Ullmann27/device-ai/pull/912", true, "open", false)
        rig.cloud.onOutcome = Rig.happyScript(rig.cloud)
        val order = "Coding-Agent: behebe in device-ai den Fehler im Parser und mach einen Pull Request"

        type(activity, order)
        var chat = chat(activity)
        val orderAt = chat.indexOf(order)
        val questionAt = chat.indexOfFirst { it.startsWith("Coding-Agent – bitte bestätigen\nRepository: Ullmann27/device-ai\nAusgangszweig: main") }
        assertTrue(chat.joinToString("\n"), orderAt in 0 until questionAt)
        assertTrue(chat[questionAt].contains("öffnet einen Entwurfs-PR und führt nichts zusammen"))
        assertTrue(chat[questionAt].contains("Budget: höchstens 2,00 $"))
        // The question alone starts nothing at Anthropic, and the sentence left the input field.
        assertTrue(rig.cloud.calls.isEmpty())
        assertEquals("", (field(activity, "input") as EditText).text.toString())

        type(activity, "Ja")
        chat = chat(activity)
        val yesAt = chat.lastIndexOf("Ja")
        val startedAt = chat.indexOfFirst { it.startsWith("Coding-Agent gestartet: Ullmann27/device-ai, neuer Zweig continuum-agent/run0001") }
        val reportAt = chat.indexOfFirst { it.startsWith("Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor.") }
        assertTrue(chat.joinToString("\n"), yesAt > questionAt && yesAt < startedAt && startedAt < reportAt)
        assertTrue(chat[reportAt].contains("Pull Request #912: Entwurf, Zustand open · https://github.com/Ullmann27/device-ai/pull/912"))
        assertTrue(chat[reportAt].contains("Bericht des Agenten (seine eigenen Angaben, von der App nicht geprüft):"))
        assertEquals(CodingAgentRunStatus.COMPLETED, rig.run.status)
        assertEquals(1, rig.cloud.sessionRequests.size)
        // The lane was never taken: the agent runs at Anthropic, not on the device.
        assertEquals(false, field(activity, "busy"))

        // The work window got the real steps of this run, ending in what the app found on GitHub.
        assertEquals("coding-agent-run0001", timelines.last().turnId)
        val steps: List<ProcessStep> = timelines.last().steps
        assertTrue(steps.toString(), steps.contains(ProcessStep("step:agent-setup", "Bei Anthropic vorbereitet", StepState.DONE)))
        assertTrue(steps.toString(), steps.contains(ProcessStep("step:agent-report", "Entwurfs-PR #912 liegt vor", StepState.DONE)))
        assertTrue(steps.toString(), steps.none { it.state == StepState.ACTIVE || it.state == StepState.WAITING })

        // The run is in the list of the dialog, and no secret is anywhere on the screen.
        openPanel(activity, "Coding-Agenten öffnen")
        assertNotNull(tagged<TextView>("coding-agent-run:run0001"))
        assertTrue(panelTexts().toString(), panelTexts().any { it == "Ullmann27/device-ai · Coding-Auftrag · abgeschlossen · 1,37 $" })
        assertFalse((panelTexts() + chat(activity)).any { it.contains(FAKE_GITHUB_TOKEN) || it.contains(FAKE_API_KEY) })
    }

    @Test fun anOrderWithoutTheSetupSaysWhatIsMissingAndOtherSentencesAreNotTaken() = withActivity { activity ->
        // Not an order form: the hook does not take it, so it keeps today's route.
        listOf("Was ist ein Coding-Agent?", "Öffne die Agenten", "Ja", "Erlauben", "Stopp").forEach { sentence ->
            assertFalse(sentence, activity.codingAgents.take(sentence))
            assertFalse(sentence, activity.codingAgents.controls(sentence))
        }
        type(activity, "Coding-Agent: behebe in device-ai den Fehler")
        assertTrue(chat(activity).joinToString("\n"), chat(activity).contains(CodingAgentFailure.message(CodingAgentFailure.NO_AGENT_CONSENT)))
        assertTrue(rig.cloud.calls.isEmpty() && rig.github.calls.isEmpty() && rig.store.runs().isEmpty())
    }
}
