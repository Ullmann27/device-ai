package at.heinz.deviceai

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumVisibleAvatarUiTest {
    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun refresh(a: AgentActivity) = AgentActivity::class.java.getDeclaredMethod("refreshConversationLayout")
        .apply { isAccessible = true }.invoke(a)
    private data class Fixture(val a: AgentActivity, val head: ContinuumHologramView,
                               val home: ContinuumHomeView, val chat: LinearLayout)
    private fun setup(): Fixture {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val head = ContinuumHologramView(a)
        val home = ContinuumHomeView(a, head) { _, _, _ -> }.apply { configure(412f, false) }
        val chat = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; addView(TextView(a)) }
        set(a, "hologramView", head); set(a, "homeView", home)
        set(a, "dashboardSection", home); set(a, "chatColumn", chat)
        return Fixture(a, head, home, chat)
    }
    @Test fun enteringChatKeepsTheSameAvatarInTheVisibleColumn() {
        val f = setup()
        set(f.a, "conversationActive", true); refresh(f.a)
        assertEquals(View.GONE, f.home.visibility)
        assertEquals(View.VISIBLE, f.chat.visibility)
        assertSame("Actual tool/speech effects disappear with the hidden dashboard", f.chat, f.head.parent)
        assertTrue(f.head.layoutParams.height > 0)
    }
    @Test fun returningHomeRestoresTheSameAvatarAndItsAppearanceChoice() {
        val f = setup(); f.head.setNativeHeadEnabled(true)
        set(f.a, "conversationActive", true); refresh(f.a)
        assertSame(f.chat, f.head.parent)
        set(f.a, "conversationActive", false); refresh(f.a)
        assertSame(f.home, f.head.parent)
        assertTrue(f.head.isNativeHeadEnabled())
        assertEquals(1, (0 until f.home.childCount).count { f.home.getChildAt(it) === f.head })
    }
    @Test fun repeatedKeyboardChangesResizeWithoutDuplicatingAvatar() {
        val f = setup()
        set(f.a, "conversationActive", true); refresh(f.a)
        assertSame(f.chat, f.head.parent)
        val fullHeight = f.head.layoutParams.height
        set(f.a, "keyboardCompact", true)
        repeat(4) { refresh(f.a) }
        assertTrue(f.head.layoutParams.height in 1 until fullHeight)
        assertEquals(1, (0 until f.chat.childCount).count { f.chat.getChildAt(it) === f.head })
    }
    @Test fun hiddenHomeCannotRemeasureAnAvatarHostedInChat() {
        val f = setup()
        f.home.removeView(f.head); f.chat.addView(f.head)
        fun exact(n: Int) = View.MeasureSpec.makeMeasureSpec(n, View.MeasureSpec.EXACTLY)
        f.head.measure(exact(180), exact(96)); f.head.layout(0, 0, 180, 96)
        f.home.measure(exact(412), exact(900)); f.home.layout(0, 0, 412, 900)
        assertEquals(180, f.head.measuredWidth); assertEquals(96, f.head.measuredHeight)
        assertEquals(180, f.head.width); assertEquals(96, f.head.height)
    }
}
