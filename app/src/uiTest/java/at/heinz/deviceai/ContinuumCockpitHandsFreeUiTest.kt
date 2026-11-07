package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import at.heinz.deviceai.cognitive.ContinuousConversationMode
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertEquals
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
