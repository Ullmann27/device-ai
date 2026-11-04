package at.heinz.deviceai.ui

import android.app.Activity
import android.widget.FrameLayout
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisMemorySignalWiringUiTest {
    private fun view(): JarvisSequenceView = JarvisSequenceView(
        Robolectric.buildActivity(Activity::class.java).setup().get()).apply { clock = { 1_000L } }

    @Test fun startingResearchDoesNotInventLearnedDocumentsOrAnArrivalPulse() {
        val v = view()
        v.render(CockpitAnimationEvent.CommandFlyIn("t1", 1L, "Recherchieren"))
        v.render(CockpitAnimationEvent.ActionStarted("t1", 2L, CockpitAction.RESEARCH, "Recherche gestartet"))
        assertEquals("An action start is not a document intake", 0, v.effects.flightDocuments)
        assertEquals(0f, v.effects.orbPulseLevel(1_900L), 0f)
    }

    @Test fun actualMemoryEventsReachTheOrbWhileAConversationTurnIsActive() {
        val v = view()
        v.render(CockpitAnimationEvent.CommandFlyIn("conversation", 1L, "Frage"))
        v.render(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, 2L, "document", "PDF", 3))
        assertEquals("Memory events are independent of the active conversation turn", 3, v.effects.flightDocuments)
        v.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 3L, "hash", 2, .75f))
        assertEquals(2, v.effects.recallCount)
        assertEquals(.75f, v.effects.recallBrightness(1_000L), 0f)
    }

    @Test fun onlyConfirmedWritesFlashAndHypothesesRemainDistinct() {
        val v = view()
        v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 1L, "entry", "UNCONFIRMED"))
        v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 2L, "entry", "REVOKED"))
        assertEquals(0f, v.effects.orbPulseLevel(1_000L), 0f)
        v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 3L, "entry", "HYPOTHETICAL"))
        assertTrue(v.effects.hypothesisVisible(1_000L))
        assertEquals(0f, v.effects.orbPulseLevel(1_000L), 0f)
        v.clear()
        v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 4L, "entry", "CONFIRMED"))
        assertTrue(v.effects.orbPulseLevel(1_000L) > 0f)
    }

    @Test fun detachedSequenceDoesNotAcceptLateMemoryCallbacks() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val host = FrameLayout(controller.get())
            controller.get().setContentView(host)
            val v = JarvisSequenceView(controller.get()).apply { clock = { 1_000L } }
            host.addView(v)
            v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 1L, "entry", "CONFIRMED"))
            host.removeView(v)
            v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 2L, "late", "CONFIRMED"))
            assertEquals(0f, v.effects.orbPulseLevel(1_000L), 0f)
            assertFalse(v.effects.isAnimating(1_000L))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun pausingTheLiveSurfaceClearsEffectsAndRejectsNewBackgroundCallbacks() {
        val v = view()
        v.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 1L, "entry", "CONFIRMED"))
        assertTrue(v.effects.orbPulseLevel(1_000L) > 0f)
        v.setMotionAllowed(false)
        v.render(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, 2L, "late", "PDF", 3))
        assertEquals(0, v.effects.flightDocuments)
        assertEquals(0f, v.effects.orbPulseLevel(1_000L), 0f)
        v.setMotionAllowed(true)
        v.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 3L, "current", 1, .6f))
        assertEquals(.6f, v.effects.recallBrightness(1_000L), 0f)
    }
}
