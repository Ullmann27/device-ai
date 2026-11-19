package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.localmodel.BrainKind
import at.heinz.deviceai.localmodel.ClaudeApiKey
import at.heinz.deviceai.localmodel.ClaudeBrainFailure
import at.heinz.deviceai.localmodel.ClaudeBrainSettings
import at.heinz.deviceai.localmodel.ClaudeEffort
import at.heinz.deviceai.localmodel.ClaudeModel
import at.heinz.deviceai.localmodel.ClaudeUsageLedger
import at.heinz.deviceai.localmodel.ClaudeTokenUsage
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint
import at.heinz.deviceai.localmodel.LocalModelHost
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.PreferencesClaudeLedgerStorage
import at.heinz.deviceai.localmodel.PreferencesClaudeSettingsStore
import at.heinz.deviceai.localmodel.SelectableModelRuntime
import at.heinz.deviceai.localmodel.testBrain
import at.heinz.deviceai.ui.ContinuumDashboardState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The permanent marker of the selected brain in the real activity, the stored settings and the fail-closed key
 * store under Robolectric. No request is sent here: every test brain can only reach a closed local port.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClaudeBrainMarkerUiTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun prepare() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit()
            .putBoolean("original_html_main", false).commit()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Pair<Class<*>, Any?>): Any? =
        AgentActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }.invoke(a, *args.map { it.second }.toTypedArray())
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun texts(view: View) = all(view).filterIsInstance<TextView>().map { it.text.toString() }
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(message, condition())
    }
    private fun withActivity(block: (AgentActivity) -> Unit) {
        val controller: ActivityController<AgentActivity> =
            Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            val a = controller.get()
            await("Activity is foreground") { field(a, "foregroundLearningActive") == true }
            block(a)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }
    private fun localOnly(a: AgentActivity) =
        (call(a, "presentationStatus") as ContinuumDashboardState.PresentationStatus).localOnly

    @Test fun activityStartsWithTheLocalBrainOfTheProcessAndNothingSelected() = withActivity { a ->
        val brain = field(a, "claudeBrain") as at.heinz.deviceai.localmodel.ClaudeBrain
        assertSame(LocalModelHost.brain(a), brain)
        assertFalse(brain.claudeSelected)
        assertFalse(brain.settings.consentGiven)
        assertEquals("LOKAL", brain.marker())
        assertSame(LocalModelHost.get(a).runtime, field(a, "localRuntime"))
        // The host runtime is the on-device runtime as before: no model loaded in this fixture.
        assertEquals(LocalModelState.EMPTY, (field(a, "localRuntime") as LocalModelRuntime).state)
        assertNull((field(a, "localRuntime") as LocalModelRuntime).loadedModel)
        assertFalse((field(a, "localBadge") as TextView).text.contains("CLAUDE"))
    }

    @Test fun badgeModelCentreAndPrivacySegmentFollowTheSelectedBrainAndReturnToLocal() = withActivity { a ->
        val brain = testBrain()
        assertTrue(brain.saveKey(FakeAnthropicEndpoint.TEST_KEY))
        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        set(a, "claudeBrain", brain)
        set(a, "localRuntime", SelectableModelRuntime(field(a, "localRuntime") as LocalModelRuntime, brain.runtime) {
            brain.claudeSelected
        })
        val badge = field(a, "localBadge") as TextView
        call(a, "refreshLocalModelUi")
        assertFalse(badge.text.contains("CLOUD"))
        assertEquals("internet research is OFF in this fixture", true, localOnly(a))

        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        call(a, "onClaudeBrainChanged", BrainKind::class.java to BrainKind.LOCAL)
        assertEquals("● CONTINUUM · CLOUD", badge.text.toString())
        assertTrue(badge.contentDescription.toString().contains("Continuum antwortet über die Cloud"))
        assertFalse(badge.contentDescription.toString().contains("laude"))
        assertEquals("not local-only while the cloud brain answers", false, localOnly(a))
        val runtime = field(a, "localRuntime") as LocalModelRuntime
        assertEquals(LocalModelState.READY, runtime.state)
        assertEquals("claude-opus-5-5", runtime.loadedModel!!.id)

        call(a, "showModelCenter")
        advance()
        val centre = ShadowAlertDialog.getLatestAlertDialog()
        val shown = texts(centre.window!!.decorView)
        assertTrue(shown.toString(), shown.any { it == "GEHIRN WÄHLEN · CONTINUUM · CLOUD" })
        assertTrue(shown.any { it.startsWith("Aktives Gehirn: CONTINUUM · CLOUD · claude-opus-5-5") &&
            it.contains("Das lokale Sprachmodell wird nicht verwendet") })
        assertTrue("the key itself is never displayed", shown.none { it.contains("sk-ant") })
        centre.dismiss()
        advance()

        // A measurement labelled "offline" does not run against the cloud brain.
        call(a, "runLocalSmokeTest", Boolean::class.javaPrimitiveType!! to true)
        advance()
        assertTrue(texts(field(a, "chatContainer") as LinearLayout).any {
            it.contains(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.LOCAL_ONLY_FEATURE))
        })

        assertTrue(brain.selectBrain(BrainKind.LOCAL))
        call(a, "onClaudeBrainChanged", BrainKind::class.java to BrainKind.CLAUDE)
        assertFalse(badge.text.contains("CLOUD"))
        assertEquals("Lokaler Startkern: Status und Sprachmodell erklären", badge.contentDescription.toString())
        assertEquals(true, localOnly(a))
        assertEquals(LocalModelState.EMPTY, runtime.state)
    }

    @Test fun dashboardModeNamesTheCloudBrainOnlyWhileItIsSelected() {
        val dir = java.io.File(app.filesDir, "claude-dashboard").apply { mkdirs() }
        val collector = ContinuumDashboardCollector(app,
            at.heinz.deviceai.diagnostics.ContinuumDiagnostics(java.io.File(dir, "diagnostics.tsv"), "host-qa"))
        fun live(model: String?, mode: String?) = ContinuumDashboardCollector.Live(model, model != null, false, "OFF",
            false, false, false, false, false, false, false, false, false, emptyList(), mode)
        assertEquals("CONTINUUM · CLOUD", collector.collect(live("claude-opus-5-5 cloud", "CONTINUUM · CLOUD")).model.mode)
        assertEquals("claude-opus-5-5 cloud", collector.collect(live("claude-opus-5-5 cloud", "CONTINUUM · CLOUD")).model.name)
        assertEquals("Lokales Sprachmodell", collector.collect(live("gemma-4-e4b 1", null)).model.mode)
        assertEquals("Startkern ohne Sprachmodell", collector.collect(live(null, null)).model.mode)
    }

    @Test fun storedSettingsSurviveARestartAndASelectionWithoutConsentIsNotRestored() {
        val store = PreferencesClaudeSettingsStore(app)
        assertEquals(ClaudeBrainSettings(), store.load())
        val chosen = ClaudeBrainSettings(brain = BrainKind.CLAUDE, model = ClaudeModel.FABLE_5_2,
            effortOverride = ClaudeEffort.HIGH, consentGiven = true, consentAtMillis = 42L, shareLocalMemory = true,
            monthlyLimitCents = 700)
        assertTrue(store.save(chosen))
        assertEquals(chosen, PreferencesClaudeSettingsStore(app).load())
        // Preferences that claim Claude without the consent flag (damaged or restored) read as LOCAL.
        app.getSharedPreferences("continuum.claude.brain", Context.MODE_PRIVATE).edit()
            .putBoolean("consent", false).commit()
        val restored = PreferencesClaudeSettingsStore(app).load()
        assertEquals(BrainKind.LOCAL, restored.brain)
        assertFalse(restored.shareLocalMemory)
        assertEquals(ClaudeModel.FABLE_5_2, restored.model)
        // The settings never contain a key.
        assertTrue(app.getSharedPreferences("continuum.claude.brain", Context.MODE_PRIVATE).all.values
            .none { it.toString().contains("sk-ant") })

        val ledger = ClaudeUsageLedger(PreferencesClaudeLedgerStorage(app)) { "2026-11" }
        ledger.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(1_000, 100))
        assertEquals(1L, ClaudeUsageLedger(PreferencesClaudeLedgerStorage(app)) { "2026-11" }.snapshot().requests)
    }

    @Test fun keyStoreFailsClosedWhereNoAndroidKeystoreExists() {
        // Robolectric has no AndroidKeyStore provider. The production store must then store nothing at all
        // (no plain-text fallback) and report "no key"; the real keystore path is not covered by this test.
        val store = ClaudeApiKey.store(app)
        assertNull(store.load())
        val saved = store.save(FakeAnthropicEndpoint.TEST_KEY)
        val preferences = app.getSharedPreferences("continuum.claude.secret", Context.MODE_PRIVATE).all
        assertTrue(preferences.values.none { it.toString().contains("sk-ant") })
        if (!saved) {
            assertTrue("nothing is written when the key cannot be encrypted", preferences.isEmpty())
            assertNull(store.load())
            assertFalse(store.isPresent())
        } else {
            // An environment with a keystore: the value round-trips and is removed again.
            assertEquals(FakeAnthropicEndpoint.TEST_KEY, store.load()!!.reveal())
            assertTrue(store.clear())
            assertNull(store.load())
        }
    }
}
