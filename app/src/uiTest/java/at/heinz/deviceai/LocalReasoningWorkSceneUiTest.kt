package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
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
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalReasoningWorkSceneUiTest {
    @Before fun configureClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }

    private fun invokePresent(a: AgentActivity, turn: String, request: String) =
        AgentActivity::class.java.getDeclaredMethod(
            "presentLocalReasoningWorkScene", String::class.java, String::class.java
        ).apply { isAccessible = true }.invoke(a, turn, request)

    private fun invokeFinish(a: AgentActivity, turn: String, immediate: Boolean) =
        AgentActivity::class.java.getDeclaredMethod(
            "finishLocalReasoningWorkScene", String::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(a, turn, immediate)

    @Test fun ownerLocalTurnGetsRealWindowAndOnlyItsTurnCanCloseIt() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE)
            .edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE)
            .edit().putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE)
            .edit().putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
            .edit().putBoolean("jarvis-shell", true).commit()

        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            repeat(3) { shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS) }
            controller.windowFocusChanged(true)
            val a = controller.get()
            val root = a.window.decorView
            root.measure(
                View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 884, 760)
            val home = field(a, "homeView") as ContinuumHomeView
            home.setJarvisShellStage(true)
            set(a, "busy", true)
            set(a, "activeLocalRequest", true)
            set(a, "activeWorkArea", ContinuumWorkArea.START)

            invokePresent(a, "local-proof", "Analysiere diese Aufgabe lokal und zeige die Bearbeitung.")
            shadowOf(Looper.getMainLooper()).idleFor(64, TimeUnit.MILLISECONDS)

            var views = descendants(root)
            assertTrue(views.any { it.tag == "jarvis-work-window" && it.isShown })
            assertTrue(views.filterIsInstance<TextView>().any {
                it.tag == "jarvis-local-work-status:local-proof" &&
                    it.text.toString().contains("Lokale Bearbeitung läuft")
            })
            assertTrue(views.filterIsInstance<ProgressBar>().any {
                it.tag == "jarvis-local-work-progress:local-proof" && it.isShown && it.isIndeterminate
            })
            assertNotNull(field(a, "localReasoningWorkScene"))

            invokeFinish(a, "fremder-turn", true)
            views = descendants(root)
            assertTrue("foreign turn must not close owner work", views.any {
                it.tag == "jarvis-work-window" && it.isShown
            })

            invokeFinish(a, "local-proof", true)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field(a, "localReasoningWorkScene"))
            assertFalse(descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
        } finally {
            controller.close()
        }
    }
}
