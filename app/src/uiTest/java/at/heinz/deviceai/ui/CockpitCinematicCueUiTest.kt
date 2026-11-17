package at.heinz.deviceai.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.BOOT_TURN
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitCues
import at.heinz.deviceai.cockpit.Cue
import at.heinz.deviceai.cockpit.CueOwner
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.cockpit.MEMORY_TURN
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
class CockpitCinematicCueUiTest {
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
            val avatar = home.avatarRenderer as JarvisHoloAvatar
            val sequence = requireNotNull(avatar.sequence)
            assertTrue(home.ownsAvatarSlot && sequence.isAttachedToWindow && sequence.isShown)
            assertSame(live, sequence.parent)
            check(home, live, sequence)
        } finally {
            controller.pause().stop().destroy()
            JarvisAvatarSlot.register(null)
        }
    }

    private fun frame(sequence: View): Bitmap =
        Bitmap.createBitmap(sequence.width, sequence.height, Bitmap.Config.ARGB_8888).also {
            sequence.draw(Canvas(it))
        }

    private fun entered(home: ContinuumHomeView) {
        CockpitAnimationPresenter(home).render(CockpitAnimationEvent.CockpitEntered(BOOT_TURN, 1L, "cue-test"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun actualPresenterMakesHeadIconsVisibleAboveTheLiveSurface() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = frame(sequence)
        entered(home)
        now = 1_450L
        val flying = frame(sequence)
        assertFalse("Cockpit entry must draw the recovered head icons", empty.sameAs(flying))
        assertEquals("Effects stay above the native surface", live.childCount - 1, live.indexOfChild(sequence))
        now = 5_000L
        assertTrue("The finite burst must settle", empty.sameAs(frame(sequence)))
        empty.recycle(); flying.recycle()
    }

    @Test fun backgroundDropsHeadIconsWithoutResumeReplay() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = frame(sequence)
        entered(home)
        live.setForegroundActive(false)
        now = 1_400L
        live.setForegroundActive(true)
        assertTrue("Background must discard the burst", empty.sameAs(frame(sequence)))
        empty.recycle()
    }

    @Test fun reducedMotionNeverQueuesAHeadBurst() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = frame(sequence)
        live.setAnimationsEnabled(false)
        entered(home)
        live.setAnimationsEnabled(true)
        now = 1_400L
        assertTrue("Reduced motion must not replay on enable", empty.sameAs(frame(sequence)))
        empty.recycle()
    }

    @Test fun delayedBootDoesNotReplayAfterBackground() = withCockpit { home, live, _ ->
        val cues = mutableListOf<Cue>()
        CockpitCues.register(CueOwner.INTEGRATION) { cues += it.cue; true }
        try {
            CockpitAnimationPresenter(home).render(CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, "Bereit"))
            shadowOf(Looper.getMainLooper()).idle()
            home.setForegroundActive(false)
            live.setForegroundActive(false)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            home.setForegroundActive(true)
            live.setForegroundActive(true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4_000))
            assertFalse("A retired presentation must not fly in later", Cue.COCKPIT_FLY_IN in cues)
        } finally { CockpitCues.register(CueOwner.INTEGRATION, null) }
    }

    @Test fun queuedBootDoesNotReplayAfterPauseResume() = withCockpit { home, live, _ ->
        val greetings = mutableListOf<String>()
        CockpitAnimationPresenter(home, onGreeting = { greetings += it })
            .render(CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, "Bereit"))
        home.setForegroundActive(false)
        live.setForegroundActive(false)
        home.setForegroundActive(true)
        live.setForegroundActive(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Queued presentation must belong to its foreground session", greetings.isEmpty())
    }

    private fun memoryFrame(home: ContinuumHomeView): Bitmap =
        frame(home.findViewWithTag<View>("continuum-memory-fullstage"))

    private fun recall(home: ContinuumHomeView, count: Int = 2) {
        CockpitAnimationPresenter(home).render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 1L,
            "synthetic-recall", count, if (count > 0) .9f else null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    @Test fun realRecallStartsRecoveredDocumentTunnel() = withCockpit { home, _, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = memoryFrame(home)
        recall(home)
        sequence.effects.clear()
        now = 2_200L
        val flying = memoryFrame(home)
        assertFalse("Real recall must reach the recovered document tunnel", empty.sameAs(flying))
        now = 6_000L
        assertTrue("The tunnel must finish", empty.sameAs(memoryFrame(home)))
        empty.recycle(); flying.recycle()
    }

    @Test fun actualTaskStopClearsRecoveredTunnel() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = memoryFrame(home)
        recall(home)
        live.setTaskEffectScope(null)
        now = 2_200L
        assertTrue("STOP clears every cinematic overlay", empty.sameAs(memoryFrame(home)))
        empty.recycle()
    }

    @Test fun queuedRecallCannotSurviveAnActualStop() = withCockpit { home, live, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = memoryFrame(home)
        CockpitAnimationPresenter(home).render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 1L,
            "synthetic-recall", 2, .9f))
        live.setTaskEffectScope(null)
        shadowOf(Looper.getMainLooper()).idle()
        now = 2_200L
        assertTrue("A queued recall cannot cross STOP", empty.sameAs(memoryFrame(home)))
        empty.recycle()
    }

    @Test fun emptyRecallDoesNotInventAFileFlight() = withCockpit { home, _, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = memoryFrame(home)
        recall(home, 0)
        now = 2_200L
        assertTrue("No hits means no file flight", empty.sameAs(memoryFrame(home)))
        empty.recycle()
    }

    @Test fun repeatRecallCannotExtendTheActiveTunnel() = withCockpit { home, _, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val empty = memoryFrame(home)
        recall(home)
        now = 3_500L
        recall(home)
        sequence.effects.clear()
        now = 5_900L
        assertTrue("Repeated input cannot prolong this transition", empty.sameAs(memoryFrame(home)))
        empty.recycle()
    }
}
