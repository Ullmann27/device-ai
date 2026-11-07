package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Diagnostic only: actual full Activity startup in synthetic Robolectric storage, never device proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class IndependentAvatarStartupDiagnosticUiTest {
    private fun get(a:AgentActivity,n:String)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.get(a)
    @Test fun realActivityWindowAndChatClockHavePositiveLifecycleControls() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1",Context.MODE_PRIVATE).edit().putBoolean("voice_enabled",false).commit()
        app.getSharedPreferences("continuum-readiness",Context.MODE_PRIVATE).edit().putBoolean("introduced",true).commit()
        val controller=Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val a=controller.get();val h=get(a,"hologramView") as ContinuumHologramView
            println("FULL_STARTUP_HOME decor=${a.window.decorView.windowVisibility} head=${h.windowVisibility} shown=${h.isShown} ${h.sampleMotion()}")
            assertEquals("Real startup window must be visible before testing frame progression",View.VISIBLE,h.windowVisibility)
            AgentActivity::class.java.getDeclaredMethod("focusChatComposer").apply {isAccessible=true}.invoke(a)
            val before=h.sampleMotion().callbackCount
            shadowOf(Looper.getMainLooper()).idleFor(150,TimeUnit.MILLISECONDS)
            println("FULL_STARTUP_CHAT decor=${a.window.decorView.windowVisibility} head=${h.windowVisibility} shown=${h.isShown} ${h.sampleMotion()}")
            assertTrue(h.isShown);assertEquals(View.VISIBLE,h.windowVisibility)
            assertTrue("Actual visible chat must advance existing clock",h.sampleMotion().callbackCount>before)
            controller.pause();val stopped=h.sampleMotion().callbackCount
            shadowOf(Looper.getMainLooper()).idleFor(150,TimeUnit.MILLISECONDS)
            assertEquals(stopped,h.sampleMotion().callbackCount)
        }finally {
            runCatching {controller.pause()};runCatching {controller.stop()};runCatching {controller.destroy()}
        }
    }
}
