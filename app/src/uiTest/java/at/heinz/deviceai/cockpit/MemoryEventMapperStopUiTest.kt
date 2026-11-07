package at.heinz.deviceai.cockpit

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.ui.CockpitAnimationPresenter
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisSequenceView
import org.junit.Assert.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MemoryEventMapperStopUiTest {
    @Test fun bufferedRecallCannotSurviveTheActualSurfaceStop() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = frame(sequence)
        try {
            withMapper(home) { mapper, seen ->
                mapper.recallHits("synthetic-before-stop", 2, .9f, System.currentTimeMillis())
                val generation = sequence.cueGeneration
                live.setTaskEffectScope(null)
                assertTrue(sequence.cueGeneration > generation)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertTrue(seen.isEmpty())
                now = 2_200L
                val after = frame(sequence)
                try { assertTrue("STOP must discard buffered visuals", empty.sameAs(after)) }
                finally { after.recycle() }
            }
        } finally { empty.recycle() }
    }

    @Test fun freshRecallAfterStopStillReachesTheVisibleSurface() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = frame(sequence)
        try {
            withMapper(home) { mapper, seen ->
                mapper.documentLearned("old-document", "PDF", 2, 100L)
                mapper.memoryStored("old-entry", "CONFIRMED", 101L)
                mapper.recallHits("old-query", 2, .8f, 102L)
                live.setTaskEffectScope(null)
                mapper.recallHits("fresh-query", 3, .9f, 1L)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
                assertEquals(listOf(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 1L, "fresh-query", 3, .9f)), seen)
                sequence.effects.clear()
                now = 2_200L
                val fresh = frame(sequence)
                try { assertFalse("Fresh recall must animate", empty.sameAs(fresh)) }
                finally { fresh.recycle() }
            }
        } finally { empty.recycle() }
    }

    private fun withMapper(home: ContinuumHomeView, check: (MemoryEventMapper, List<CockpitAnimationEvent>) -> Unit) {
        val bus = CockpitAnimationBus()
        val seen = mutableListOf<CockpitAnimationEvent>()
        val presenter = bus.subscribe(CockpitAnimationPresenter(home))
        val observer = bus.subscribe { seen += it }
        val handler = Handler(Looper.getMainLooper())
        val mapper = MemoryEventMapper(bus,
            generationProvider = { home.avatarRenderer?.cueGeneration ?: -1L }) { delay, callback ->
            handler.postDelayed(callback, delay)
        }
        try { check(mapper, seen) }
        finally { mapper.detach(); presenter.close(); observer.close() }
    }

    private fun frame(sequence: JarvisSequenceView): Bitmap =
        Bitmap.createBitmap(sequence.width, sequence.height, Bitmap.Config.ARGB_8888).also { sequence.draw(Canvas(it)) }

    private fun withCockpit(check: (ContinuumHomeView, ContinuumHologramView, JarvisSequenceView) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        activity.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val live = ContinuumHologramView(activity)
        val home = ContinuumHomeView(activity, live) { _, _, _ -> }
        home.configure(884f, true, windowHeightDp = 760f)
        home.setJarvisShellStage(true)
        JarvisAvatarSlot.register(JarvisHoloAvatar(live))
        activity.setContentView(home)
        try {
            repeat(3) {
                home.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
                home.layout(0, 0, 884, 760)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            home.setForegroundActive(true)
            live.setForegroundActive(true)
            val sequence = requireNotNull((home.avatarRenderer as JarvisHoloAvatar).sequence)
            assertSame(live, sequence.parent)
            assertTrue(sequence.isAttachedToWindow && sequence.isShown)
            check(home, live, sequence)
        } finally {
            controller.pause().stop().destroy()
            JarvisAvatarSlot.register(null)
        }
    }
}
