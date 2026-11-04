package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumTaskEffectsUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Activity setup can start the real avatar loop before fixture code can pause it.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }

    private fun scope(v:ContinuumHologramView,s:String?="turn-1") {
        val method=v.javaClass.methods.firstOrNull { it.name=="setTaskEffectScope" }
        assertNotNull("The actual visible View has no work-event route",method)
        method!!.invoke(v,s)
    }
    private fun event(v:ContinuumHologramView,name:String="Started",at:Long=SystemClock.uptimeMillis(),id:String="synthetic-read",worker:Boolean=false):Boolean {
        val base="at.heinz.deviceai.ui.ContinuumTaskEffects"
        val c=Class.forName("$base\$Event\$$name")
        val e=if(name=="Started") c.constructors.single().newInstance(id,
            Class.forName("$base\$Kind").enumConstants.first { (it as Enum<*>).name==if(worker) "WORKER" else "TOOL" })
            else c.getConstructor(String::class.java).newInstance(id)
        return v.javaClass.methods.single { it.name=="onTaskEffect" }.invoke(v,"turn-1",e,at) as Boolean
    }
    private fun frame(v:ContinuumHologramView,w:Int=412,h:Int=307)=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also {
        assertEquals("Every captured frame uses its labeled width",w,v.width)
        assertEquals("Every captured frame uses its labeled height",h,v.height)
        v.draw(Canvas(it))
    }
    private fun prepare(v:ContinuumHologramView,w:Int=412,h:Int=307) {
        // This Canvas work-effect fixture selects the original orb, without host Filament work.
        v.setNativeHeadEnabled(false)
        // Complete actual decor traversal with stable viewport dimensions before activation.
        v.layoutParams=v.layoutParams.apply { width=w;height=h }
        advanceFrames(40)
        assertTrue("Work effects require an attached real View",v.isAttachedToWindow)
        assertTrue("Work effects require a shown real View",v.isShown)
        assertEquals(View.VISIBLE,v.windowVisibility)
        assertEquals(w,v.width);assertEquals(h,v.height)
        v.setForegroundActive(true)
        assertTrue("Work effects require a live foreground clock",v.motionDiagnostics().contains("LIVE"))
        scope(v)
    }
    @Test fun actualOverlayDrawsAboveAnOpaqueChildWithoutASecondClock() {
        val c=Robolectric.buildActivity(Activity::class.java).setup();val v=ContinuumHologramView(c.get())
        try {
            val child=object:View(c.get()) { override fun onDraw(canvas:Canvas) { canvas.drawColor(Color.rgb(20,30,50)) } }.apply { setWillNotDraw(false) }
            v.addView(child,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            c.get().setContentView(v);prepare(v)
            assertEquals(412,child.width);assertEquals(307,child.height)
            val before=frame(v)
            assertEquals("Opaque child must actually cover the animated background",Color.rgb(20,30,50),before.getPixel(206,117))
            assertTrue(event(v))
            advanceFrames(350)
            val after=frame(v)
            assertFalse("dispatchDraw must place work glyphs above children, including the native TextureView layer",before.sameAs(after))
            scope(v,null)
            assertTrue("Scope end removes every work glyph",before.sameAs(frame(v)))
            before.recycle();after.recycle()
        } finally { c.pause().stop().destroy() }
    }
    @Test fun pauseReducedMotionAndHeatDiscardEventsInsteadOfReplayingOnResume() {
        val c=Robolectric.buildActivity(Activity::class.java).setup();val v=ContinuumHologramView(c.get())
        try {
            c.get().setContentView(v);prepare(v);assertTrue(event(v))
            v.setForegroundActive(false);v.setForegroundActive(true)
            assertFalse("Resume has no retained old event scope",event(v))
            scope(v);v.setAnimationsEnabled(false);assertFalse(event(v));v.setAnimationsEnabled(true)
            assertFalse(event(v))
            scope(v)
            v.javaClass.declaredMethods.single { it.name.startsWith("onThermalStatus") }.apply { isAccessible=true }.invoke(v,3)
            assertFalse(event(v))
            val count=v.sampleMotion().callbackCount
            advanceFrames(500)
            assertEquals(count,v.sampleMotion().callbackCount)
        } finally { c.pause().stop().destroy() }
    }
    @Test fun productionCanvasSequenceUsesExplicitSyntheticEventsOnly() {
        for((w,h) in listOf(412 to 307,410 to 291,412 to 88,412 to 120,412 to 160,720 to 200)) {
            val c=Robolectric.buildActivity(Activity::class.java).setup();val v=ContinuumHologramView(c.get())
            try {
                c.get().setContentView(v);prepare(v,w,h)
                if(h==291)v.setGeometry(at.heinz.deviceai.ui.ContinuumHologramRenderer.Geometry.FOLD)
                fun capture(label:String) {
                    val b=frame(v,w,h)
                    System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                        val out=java.io.File(dir).apply { mkdirs() }
                        java.io.File(out,"synthetic-task-$w-$h-$label.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
                    };b.recycle()
                }
                capture("0-idle")
                assertTrue(event(v,id="synthetic-worker",worker=true))
                advanceFrames(300);capture("1-worker")
                assertTrue(event(v))
                advanceFrames(200);capture("2-tool")
                assertTrue(event(v,"Read"));advanceFrames(350);capture("3-read")
                assertTrue(event(v,"Finished"));assertTrue(event(v,"Finished",id="synthetic-worker"))
                advanceFrames(250);capture("4-terminal")
                advanceFrames(2_000);capture("5-expired")
            } finally { c.pause().stop().destroy() }
        }
    }
}
