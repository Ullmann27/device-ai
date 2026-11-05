package at.heinz.deviceai

import android.content.Context
import android.view.View
import at.heinz.deviceai.cognitive.ContinuousConversationMode
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w840dp-h649dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumCockpitHandsFreeUiTest {
    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    }

    private fun call(a: AgentActivity, name: String, value: String) {
        AgentActivity::class.java.getDeclaredMethod(name, String::class.java)
            .apply { isAccessible = true }.invoke(a, value)
    }

    @Test fun spokenTurnsStayOnCockpitAndExplicitChatStillWorks() {
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
            controller.windowFocusChanged(true)
            val a = controller.get()
            val session = field(a, "sessionId") as String
            val mode = field(a, "continuousConversation") as ContinuousConversationMode
            val home = field(a, "homeView") as ContinuumHomeView
            val head = field(a, "hologramView") as ContinuumHologramView
            assertTrue(mode.start(session))
            set(a, "keepCockpitDuringHandsFree", true)
            call(a, "addUserMessage", "Hallo JARVIS")
            assertEquals(false, field(a, "conversationActive"))
            assertSame(home, head.parent)
            val answer = AgentActivity::class.java.getDeclaredMethod(
                "addAssistantMessage", String::class.java, Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            answer.invoke(a, "Ich höre dir zu.", false)
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
        } finally {
            controller.close()
        }
    }
}
