package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gedächtnis-owned orb inputs (Muster E): only real memory events drive flight, pulse, recall light, hypothesis mark. */
class BrainOrbSignalsTest {
    private var now=10_000L
    private val fx=JarvisSequenceEffects()
    private val r=BrainOrbAnimationRenderer(fx) { now }

    @Test fun emptyStateIsCalm() {
        assertFalse(fx.isAnimating(now)); assertEquals(0f,fx.orbPulseLevel(now),0f)
        assertEquals(0f,fx.recallBrightness(now),0f); assertFalse(fx.hypothesisVisible(now))
    }

    @Test fun documentLearnedFliesInWithin600to800msThenPulses() {
        assertTrue(JarvisSequenceEffects.FLIGHT_MS in 600L..800L)
        r.render(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN,now,"doc1","WIKIPEDIA_EXCERPT",2))
        assertTrue(fx.isAnimating(now)); assertEquals(2,fx.flightDocuments)
        assertEquals("no pulse before arrival",0f,fx.orbPulseLevel(now+100),0f)
        assertTrue("pulse on arrival",fx.orbPulseLevel(now+JarvisSequenceEffects.FLIGHT_MS+50)>0f)
        assertEquals(0f,fx.orbPulseLevel(now+JarvisSequenceEffects.FLIGHT_MS*4),0f)
    }

    @Test fun alreadyKnownDocumentDoesNotFly() {
        r.render(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN,now,"doc1","ARCHIVE_IMPORT",0))
        assertFalse(fx.isAnimating(now))
    }

    @Test fun recallHitsBrightnessEqualsRealTopScore() {
        r.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN,now,"ab".repeat(32),3,0.4237f))
        assertEquals(0.4237f,fx.recallBrightness(now+10),0f); assertEquals(3,fx.recallCount)
        r.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN,now,"cd".repeat(32),0,null))
        assertEquals(0f,fx.recallBrightness(now+10),0f)
    }

    @Test fun hypothesisIsMarkedUnconfirmedAndNeverFlashesAsConfirmed() {
        r.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN,now,"lx-1","HYPOTHETICAL"))
        assertTrue(fx.hypothesisVisible(now+10)); assertEquals(0f,fx.orbPulseLevel(now+10),0f)
        now+=10_000
        r.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN,now,"lx-2","CONFIRMED"))
        assertTrue(fx.orbPulseLevel(now+10)>0f); assertFalse(fx.hypothesisVisible(now+10))
    }
}
