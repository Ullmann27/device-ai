package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import at.heinz.deviceai.cognitive.ContinuousConversationMode
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import java.util.concurrent.TimeUnit
import java.time.Duration
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w840dp-h649dp-land-mdpi")
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumCockpitHandsFreeUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Same as ContinuumReferenceCockpitUiTest: Activity setup starts the real avatar frame loop;
        // without a paused virtual vsync the PAUSED main looper never becomes idle (22 min hang).
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    }

    private fun call(a: AgentActivity, name: String, value: String) {
        AgentActivity::class.java.getDeclaredMethod(name, String::class.java)
            .apply { isAccessible = true }.invoke(a, value)
    }

    private fun withHandsFreeCockpit(body: (AgentActivity, ContinuousConversationMode, ContinuumHomeView, ContinuumHologramView) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE)
            .edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE)
            .edit().putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        // Same start state as ContinuumReferenceCockpitUiTest: 2D head on, cockpit presentation reset.
        // With native-head unset (head off) setup() never idles; see OPUS55-PR564-VERIFICATION-20261105.md.
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE)
            .edit().putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            // Let the paused virtual vsync run one window traversal so the decor is attached before focus.
            repeat(2) { shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS) }
            assertTrue("decor attached before focus", controller.get().window.decorView.isAttachedToWindow)
            controller.windowFocusChanged(true)
            val a = controller.get()
            body(a, field(a, "continuousConversation") as ContinuousConversationMode,
                field(a, "homeView") as ContinuumHomeView, field(a, "hologramView") as ContinuumHologramView)
        } finally {
            controller.close()
        }
    }

    private fun answer(a: AgentActivity, value: String) {
        AgentActivity::class.java.getDeclaredMethod(
            "addAssistantMessage", String::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(a, value, false)
    }

    private fun applySpeech(a: AgentActivity, token: Long, text: String) {
        AgentActivity::class.java.getDeclaredMethod("applyConversationSpeech",
            Long::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }.invoke(a, token, text)
    }

    private fun callNoArgs(a: AgentActivity, name: String) {
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    }

    private fun chatTexts(view: View): List<String> =
        (if (view is android.widget.TextView) listOf(view.text.toString()) else emptyList()) +
            if (view is android.view.ViewGroup)
                (0 until view.childCount).flatMap { chatTexts(view.getChildAt(it)) } else emptyList()

    private fun runningLookup(a: AgentActivity) {
        set(a, "appLookupActive", true)
        set(a, "activeLocalRequest", true)
        set(a, "busy", true)
    }

    @Test fun spokenCorrectionCancelsBusyLookupAndWaitsForOneClarification() = withHandsFreeCockpit { a, mode, _, _ ->
        val session = field(a, "sessionId") as String
        assertTrue(mode.start(session))
        val token = checkNotNull(mode.listeningTurn(session))
        val tts = field(a, "voiceEngine") as android.speech.tts.TextToSpeech
        tts.setVoice(android.speech.tts.Voice("de-de-local", java.util.Locale.GERMANY,
            android.speech.tts.Voice.QUALITY_HIGH, android.speech.tts.Voice.LATENCY_NORMAL, false, emptySet()))
        set(a, "voiceEnabled", true)
        set(a, "voiceReady", true)
        callNoArgs(a, "installVoiceProgressListener")
        val priorSpeech = shadowOf(tts).spokenTextList.size
        set(a, "ownerReplyAllowsMaya", false)
        runningLookup(a)

        applySpeech(a, token, "Stopp, das meine ich nicht.")

        assertEquals(false, field(a, "appLookupActive"))
        assertEquals(false, field(a, "busy"))
        assertEquals(false, field(a, "ownerReplyAllowsMaya"))
        assertEquals(ContinuousConversationMode.State.SPEAKING, mode.state)
        assertTrue(mode.active)
        val clarification = "Verstanden. Was soll ich anders machen?"
        assertEquals(listOf(clarification), shadowOf(tts).spokenTextList.drop(priorSpeech))
        assertEquals(1, chatTexts(field(a, "chatContainer") as View).count { it == clarification })
        val utterance = field(a, "voiceUtteranceId") as String
        val listener = checkNotNull(shadowOf(tts).utteranceProgressListener)
        listener.onStop("superseded-reply", true)
        assertEquals(ContinuousConversationMode.State.SPEAKING, mode.state)
        listener.onDone(utterance)
        assertEquals(ContinuousConversationMode.State.RESUMING, mode.state)
        assertTrue(mode.active)
        applySpeech(a, token, "Stopp, das meine ich nicht.")
        assertEquals(1, chatTexts(field(a, "chatContainer") as View).count { it == clarification })
    }

    @Test fun spokenCorrectionRetainsUnsentDraftAndPausesBeforeListening() = withHandsFreeCockpit { a, mode, _, _ ->
        val session = field(a, "sessionId") as String
        assertTrue(mode.start(session))
        val token = checkNotNull(mode.listeningTurn(session))
        val input = field(a, "input") as android.widget.EditText
        input.setText("Noch ungesendeter Entwurf")
        runningLookup(a)

        applySpeech(a, token, "Stopp, das meine ich nicht.")

        assertEquals("Noch ungesendeter Entwurf", input.text.toString())
        assertEquals(false, field(a, "appLookupActive"))
        assertEquals(ContinuousConversationMode.State.THINKING, mode.state)
        callNoArgs(a, "checkConversationReplyIdle")
        assertEquals(ContinuousConversationMode.State.PAUSED, mode.state)
        assertEquals("Noch ungesendeter Entwurf", input.text.toString())
    }

    @Test fun typedCorrectionDoesNotReactivatePausedOrInactiveConversation() {
        for (paused in listOf(false, true)) withHandsFreeCockpit { a, mode, _, _ ->
            if (paused) {
                val session = field(a, "sessionId") as String
                assertTrue(mode.start(session))
                assertEquals(ContinuousConversationMode.Decision.PAUSE,
                    mode.recognized(checkNotNull(mode.listeningTurn(session)), session, "Pause"))
            }
            val previousState = mode.state
            val previousTurn = mode.turn
            val priorClarifications = chatTexts(field(a, "chatContainer") as View)
                .count { it == "Verstanden. Was soll ich anders machen?" }
            runningLookup(a)
            (field(a, "input") as android.widget.EditText).setText("Stopp, das meine ich nicht.")

            assertTrue((field(a, "sendButton") as View).performClick())

            assertEquals(false, field(a, "appLookupActive"))
            assertEquals(previousState, mode.state)
            assertEquals(previousTurn, mode.turn)
            callNoArgs(a, "checkConversationReplyIdle")
            assertEquals(previousState, mode.state)
            assertEquals(priorClarifications + 1, chatTexts(field(a, "chatContainer") as View)
                .count { it == "Verstanden. Was soll ich anders machen?" })
        }
    }

    @Test fun spokenTurnsStayOnCockpitAndExplicitChatStillWorks() = withHandsFreeCockpit { a, mode, home, head ->
        val session = field(a, "sessionId") as String
        assertTrue(mode.start(session))
        set(a, "keepCockpitDuringHandsFree", true)
        call(a, "addUserMessage", "Hallo JARVIS")
        assertEquals(false, field(a, "conversationActive"))
        assertSame(home, head.parent)
        answer(a, "Ich höre dir zu.")
        assertEquals(false, field(a, "conversationActive"))
        assertEquals(View.VISIBLE, (field(a, "dashboardSection") as View).visibility)
        assertSame(home, head.parent)
        // A non-navigational status result keeps START visible.
        val statusRoute = AgentActivity::class.java.getDeclaredMethod("showActionResultWithoutForcingChat")
            .apply { isAccessible = true }
        statusRoute.invoke(a)
        assertEquals(false, field(a, "conversationActive"))
        assertEquals(View.VISIBLE, (field(a, "dashboardSection") as View).visibility)
        // Re-entering START after a spoken turn must rearm presentation.
        set(a, "keepCockpitDuringHandsFree", false)
        AgentActivity::class.java.getDeclaredMethod("showHomeDashboard")
            .apply { isAccessible = true }.invoke(a)
        assertEquals(true, field(a, "keepCockpitDuringHandsFree"))
        set(a, "keyboardCompact", true)
        AgentActivity::class.java.getDeclaredMethod("refreshConversationLayout")
            .apply { isAccessible = true }.invoke(a)
        assertEquals(View.VISIBLE, (field(a, "dashboardSection") as View).visibility)
        set(a, "keyboardCompact", false)
        mode.stop()
        call(a, "addUserMessage", "Expliziter Chat")
        assertEquals(true, field(a, "conversationActive"))
    }

    @Test fun stopDuringAppLookupCancelsPendingForegroundRecovery() = withHandsFreeCockpit { a, mode, _, _ ->
        val session = field(a, "sessionId") as String
        assertTrue(mode.start(session))
        mode.suspendForBackground(android.os.SystemClock.elapsedRealtime())
        assertTrue(mode.foregroundRecoveryPending)
        set(a, "appLookupActive", true)
        set(a, "activeLocalRequest", true)
        set(a, "busy", true)

        assertTrue((field(a, "sendButton") as View).performClick())

        assertEquals(false, field(a, "appLookupActive"))
        assertEquals(false, field(a, "busy"))
        assertEquals(ContinuousConversationMode.State.OFF, mode.state)
        assertFalse(mode.foregroundRecoveryPending)
        assertEquals(ContinuousConversationMode.Decision.IGNORE,
            mode.restoreForeground(session, android.os.SystemClock.elapsedRealtime(), canListen = true, busy = false))
        assertEquals(ContinuousConversationMode.State.OFF, mode.state)
    }

    /** PR #564 review: touching the START composer during hands-free must not route the next spoken turn to CHAT. */
    @Test fun composerTouchDuringHandsFreeKeepsTheNextSpokenTurnOnStart() = withHandsFreeCockpit { a, mode, home, head ->
        assertTrue(mode.start(field(a, "sessionId") as String))
        set(a, "keepCockpitDuringHandsFree", true)
        val input = field(a, "input") as android.widget.EditText
        val down = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, 4f, 4f, 0)
        try { input.dispatchTouchEvent(down) } finally { down.recycle() }
        assertEquals("The pinned cockpit stays visible after the touch", View.VISIBLE,
            (field(a, "dashboardSection") as View).visibility)
        call(a, "addUserMessage", "Wie spät ist es?")
        assertEquals("Spoken turn after a composer touch stays on START", ContinuumWorkArea.START, field(a, "activeWorkArea"))
        assertEquals(View.VISIBLE, (field(a, "dashboardSection") as View).visibility)
        assertSame("The live avatar stays in the cockpit", home, head.parent)
        answer(a, "Es ist kurz nach zehn.")
        assertEquals(ContinuumWorkArea.START, field(a, "activeWorkArea"))
        assertEquals(View.VISIBLE, (field(a, "dashboardSection") as View).visibility)
        assertSame(home, head.parent)
        // Explicitly chosen text chat remains independent of the hands-free cockpit.
        AgentActivity::class.java.getDeclaredMethod("focusChatComposer").apply { isAccessible = true }.invoke(a)
        assertEquals(ContinuumWorkArea.CHAT, field(a, "activeWorkArea"))
        assertEquals(View.GONE, (field(a, "dashboardSection") as View).visibility)
        mode.stop()
    }
}
