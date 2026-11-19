package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The thin hooks of the owner's conversation manner in the real activity under Robolectric: the greeting when he
 * comes back after a pause (once, by name, with the pause the device clock gives), nothing in „Sachlich“, and his
 * typed sentences answered by the app itself. No model is loaded and nothing is sent anywhere in these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OwnerTalkGreetingUiTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val talk get() = app.getSharedPreferences(OwnerTalkPreferences.FILE, Context.MODE_PRIVATE)

    @Before fun prepare() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit().putBoolean("original_html_main", false).commit()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun chat(a: AgentActivity) = all(field(a, "chatContainer") as LinearLayout).filterIsInstance<TextView>().map { it.text.toString() }
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(message, condition())
    }
    private fun settle() = repeat(20) { advance() }

    private fun withActivity(block: (ActivityController<AgentActivity>, AgentActivity) -> Unit) {
        val controller: ActivityController<AgentActivity> = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            val a = controller.get()
            await("Activity is foreground") { field(a, "foregroundLearningActive") == true }
            block(controller, a)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }

    private fun heinz(lastSeenMinutesAgo: Long) {
        val now = System.currentTimeMillis()
        talk.edit().clear().putString("name", "Heinz").putString("name_statement", "Nenn mich Heinz").putLong("name_at", now - 86_400_000L)
            .putLong("last_seen", now - lastSeenMinutesAgo * 60_000L).commit()
    }

    private fun say(a: AgentActivity, text: String) {
        (field(a, "input") as EditText).setText(text)
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(a, false)
        settle()
    }

    @Test fun comingBackAfterTwoHoursIsGreetedOnceByNameWithThePauseOfTheDeviceClock() {
        heinz(lastSeenMinutesAgo = 121)
        withActivity { controller, a ->
            val greeting = "Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt."
            await("greeting on return") { chat(a).any { it == greeting } }
            // Leaving and coming back at once – a dialog, a rotation – is no new return.
            controller.pause()
            controller.resume()
            settle()
            assertEquals(1, chat(a).count { it.contains("nicht gerührt") })
            // The pause is over: his next greeting says nothing about two hours.
            say(a, "Hallo")
            assertEquals(1, chat(a).count { it.contains("nicht gerührt") })
            assertTrue(chat(a).toString(), chat(a).count { it.contains("Heinz") } >= 2)
        }
    }

    @Test fun inSachlichAndWithTheGreetingSwitchedOffNothingGreets() {
        for (off in listOf("tone" to "FACTUAL", "return_greeting" to "false")) {
            heinz(lastSeenMinutesAgo = 180)
            talk.edit().apply { if (off.first == "tone") putString("tone", off.second) else putBoolean("return_greeting", false) }.commit()
            withActivity { _, a ->
                settle()
                assertFalse(off.toString() + chat(a), chat(a).any { it.contains("Heinz") || it.contains("gerührt") })
            }
        }
    }

    @Test fun hisSentencesAboutTheMannerAreAnsweredByTheAppItself() {
        heinz(lastSeenMinutesAgo = 1)
        withActivity { _, a ->
            settle()
            assertFalse("no greeting after a minute", chat(a).any { it.contains("Heinz") })
            say(a, "Hallo")
            assertTrue(chat(a).toString(), chat(a).any { it == "Hallo Heinz! Schön, dass du da bist. Was liegt an?" })
            say(a, "Nenn mich Franz")
            assertTrue(chat(a).toString(), chat(a).any { it.startsWith("Alles klar, Franz. So spreche ich dich ab jetzt an") })
            assertEquals("Franz", talk.getString("name", null))
            assertEquals("Nenn mich Franz", talk.getString("name_statement", null))
            say(a, "Sprich wieder sachlich")
            assertEquals("FACTUAL", talk.getString("tone", null))
            assertTrue(chat(a).any { it.startsWith("In Ordnung. Ich spreche ab jetzt sachlich mit dir") })
            say(a, "Vergiss meinen Namen")
            assertEquals(null, talk.getString("name", null))
            assertFalse(talk.getBoolean("address_by_name", true))
        }
    }
}
