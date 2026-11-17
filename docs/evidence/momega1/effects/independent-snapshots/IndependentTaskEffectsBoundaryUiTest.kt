package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import at.heinz.deviceai.ui.ContinuumTaskEffects
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentTaskEffectsBoundaryUiTest {
    private fun tick(ms:Long=2)=ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
    private fun start(v:ContinuumHologramView,id:String,at:Long=SystemClock.uptimeMillis())=
        v.onTaskEffect("scope",ContinuumTaskEffects.Event.Started(id,ContinuumTaskEffects.Kind.TOOL),at)
    private fun measure(v:View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(307,View.MeasureSpec.EXACTLY))
        v.layout(0,0,412,307)
    }
    private fun withView(block:(ContinuumHologramView)->Unit) {
        val host=Robolectric.buildActivity(Activity::class.java).setup();val v=ContinuumHologramView(host.get())
        try {
            v.layoutParams=FrameLayout.LayoutParams(412,307);host.get().setContentView(v);measure(v);v.setForegroundActive(true)
            tick();v.setTaskEffectScope("scope");tick();block(v)
        }finally {v.setForegroundActive(false);host.pause().stop().destroy()}
    }
    @Test fun pauseBoundaryCannotReplayCapturedArrivalEvenWhenCallerReappliesScope()=withView {v ->
        val beforePause=SystemClock.uptimeMillis();tick()
        v.setForegroundActive(false);tick();v.setForegroundActive(true);v.setTaskEffectScope("scope")
        assertFalse("Captured pre-pause work cannot appear after scope reapplication",start(v,"queued-before-pause",beforePause))
        tick();assertTrue(start(v,"new-after-resume"))
    }
    @Test fun hiddenBoundaryCannotReplayCapturedArrivalAfterBecomingVisible()=withView {v ->
        val beforeHide=SystemClock.uptimeMillis();tick()
        v.visibility=View.GONE;tick();v.visibility=View.VISIBLE;v.setTaskEffectScope("scope")
        assertFalse("Invisible work arrival must not replay on visibility return",start(v,"queued-before-hide",beforeHide))
        tick();assertTrue(start(v,"new-visible"))
    }
    @Test fun explicitScopeEndCannotAcceptAnOlderTimestampWhenSameScopeReturns()=withView {v ->
        val beforeEnd=SystemClock.uptimeMillis();tick();v.setTaskEffectScope(null);tick();v.setTaskEffectScope("scope")
        assertFalse("Ended work scope cannot replay its captured arrival",start(v,"queued-before-end",beforeEnd))
        tick();assertTrue(start(v,"new-scope-event"))
    }
    @Test fun productionOverlayIsAboveOpaqueChildAndScopeEndRestoresExactPixels()=withView {v ->
        val background=Color.rgb(17,29,43)
        v.addView(View(v.context).apply {setBackgroundColor(background)},FrameLayout.LayoutParams(-1,-1))
        fun frame():Bitmap {measure(v);return Bitmap.createBitmap(412,307,Bitmap.Config.ARGB_8888).also {v.draw(Canvas(it))}}
        val before=frame()
        try {
            assertEquals(background,before.getPixel(200,150));assertEquals(412,v.width);assertEquals(307,v.height)
            assertTrue(start(v,"actual-explicit-tool"));tick(350)
            val active=frame()
            try {assertFalse("Actual dispatchDraw must render above opaque child",before.sameAs(active))}finally {active.recycle()}
            v.setTaskEffectScope(null)
            val ended=frame()
            try {assertTrue("Fixed geometry and full child coverage isolate scope-end removal",before.sameAs(ended))}finally {ended.recycle()}
        }finally {before.recycle()}
    }
}
