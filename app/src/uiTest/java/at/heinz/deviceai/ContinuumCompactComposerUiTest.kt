package at.heinz.deviceai

import android.app.Activity
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowPopupMenu
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w914dp-h411dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumCompactComposerUiTest {
    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(a, *args)

    private fun withPane(wide: Boolean = true, test: (AgentActivity, View, EditText, Button) -> Unit) {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "voiceEnabled", false)
        val pane = call(a, "buildConversationPane", wide) as View
        val home = get(a, "homeView") as ContinuumHomeView
        val head = get(a, "hologramView") as ContinuumHologramView
        head.setNativeHeadEnabled(false)
        head.setAnimationsEnabled(false)
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            host.get().setContentView(pane)
            shadowOf(Looper.getMainLooper()).idle()
            call(a, "focusChatComposer")
            test(a, pane, get(a, "input") as EditText, get(a, "sendButton") as Button)
        } finally {
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            head.setForegroundActive(false)
            home.setForegroundActive(false)
            (get(a, "cockpitSubscriptions") as List<*>).forEach { (it as AutoCloseable).close() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            host.pause().stop().destroy()
        }
    }

    private fun layout(a: AgentActivity, pane: View, height: Int, width: Int = 760) {
        set(a, "renderedWindowHeightDp", height.toFloat())
        call(a, "refreshConversationLayout")
        pane.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        pane.layout(0, 0, width, height)
    }

    private fun visibleInside(pane: View, control: View) {
        val outer = Rect()
        val actual = Rect()
        assertTrue(pane.getGlobalVisibleRect(outer))
        assertTrue("Control is clipped by keyboard viewport", control.getGlobalVisibleRect(actual))
        assertEquals(control.height, actual.height())
        assertTrue(actual.height() >= 44)
        assertTrue(outer.contains(actual))
    }

    @Test fun shortLandscapeKeepsEditorAndSendFullyVisible() = withPane { a, pane, editor, send ->
        layout(a, pane, 126)
        visibleInside(pane, editor)
        visibleInside(pane, send)
    }

    @Test fun heightChangesKeepDraftSelectionAndOriginalControls() = withPane { a, pane, editor, send ->
        val parent = send.parent
        val draft = "Ursprünglicher Auftrag 🪐 bleibt erhalten"
        editor.setText(draft)
        editor.setSelection(4, 12)
        repeat(3) {
            layout(a, pane, 126)
            visibleInside(pane, editor)
            visibleInside(pane, send)
            layout(a, pane, 360)
            assertSame(parent, send.parent)
            assertSame(editor, get(a, "input"))
            assertSame(send, get(a, "sendButton"))
            assertEquals(draft, editor.text.toString())
            assertEquals(4, editor.selectionStart)
            assertEquals(12, editor.selectionEnd)
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-port-mdpi", fontScale = 2f)
    fun narrowLargeTextKeepsEditingAndOriginalSend() = withPane(false) { a, pane, editor, send ->
        layout(a, pane, 126, 320)
        visibleInside(pane, editor)
        visibleInside(pane, send)
        assertTrue(editor.width >= 120)
        assertEquals(28f, editor.textSize)
        assertTrue("Send glyph is clipped", send.layout.height <=
            send.height - send.compoundPaddingTop - send.compoundPaddingBottom)
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun overflowPreservesActionsLongPressesAndDisabledState() = withPane { a, pane, _, _ ->
        val original = buttons(pane)
        val mic = get(a, "chatMicButton") as Button
        val voice = get(a, "voiceButton") as Button
        val file = original.single { it.text.toString() == "⌁ Datei" }
        val calls = mutableListOf<String>()
        mic.setOnClickListener { calls += "mic" }
        mic.setOnLongClickListener { calls += "dictate"; true }
        voice.setOnLongClickListener { calls += "voice"; true }
        file.setOnLongClickListener { calls += "develop"; true }
        layout(a, pane, 126)
        val more = buttons(pane).single { it.contentDescription == "Weitere Gesprächsfunktionen" }
        assertTrue(more.performClick())
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        val menu = popup.menu
        val items = (0 until menu.size()).map(menu::getItem)
        val titles = items.map { it.title.toString() }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue(titles.containsAll(listOf("DIALOG", "INTERNET", "GERÄT", "Neue Chat-Sitzung",
            "Text diktieren", "Stimme wählen", "App entwickeln", "✧ Denken", "◎ Web", "⌁ Datei")))
        fun tap(title: String) = menu.performIdentifierAction(items.single { it.title == title }.itemId, 0)
        tap(mic.contentDescription.toString())
        tap("Text diktieren")
        tap("Stimme wählen")
        tap("App entwickeln")
        assertEquals(listOf("mic", "dictate", "voice", "develop"), calls)
        mic.isEnabled = false
        tap("Text diktieren")
        assertEquals(4, calls.size)
        popup.dismiss()
        more.performClick()
        val disabledMenu = ShadowPopupMenu.getLatestPopupMenu().menu
        assertFalse((0 until disabledMenu.size()).map(disabledMenu::getItem)
            .single { it.title == "Text diktieren" }.isEnabled)
        ShadowPopupMenu.getLatestPopupMenu().dismiss()
        layout(a, pane, 360)
        assertTrue(mic.isShown)
        assertTrue(file.isShown)
        assertFalse(more.isShown)
    }
}
