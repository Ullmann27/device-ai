package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CounterfactualWorldOverlayTest {
    @Test fun assumptionsNeverMutateBaseReality() {
        val base=linkedMapOf("render.fps" to "60","thermal.severe" to "false")
        val world=CounterfactualWorldOverlay(base,"what-if-1")
        world.assume("render.fps","30")
        assertEquals("30",world.read("render.fps"))
        assertEquals("60",base["render.fps"])
    }

    @Test fun retractionAndDerivedConsequencesStayInsideOverlay() {
        val world=CounterfactualWorldOverlay(mapOf("module.y" to "enabled"),"what-if-2")
        world.retract("module.y")
        world.derive("feature.x","unavailable")
        assertNull(world.read("module.y"))
        assertEquals("unavailable",world.read("feature.x"))
        assertFalse(world.snapshot().containsKey("module.y"))
    }

    @Test fun labelledSummaryCannotMasqueradeAsActualState() {
        val world=CounterfactualWorldOverlay(mapOf("a" to "1"),"simulation")
        world.assume("a","2")
        val text=world.labelledSummary()
        assertEquals(true,text.startsWith("[COUNTERFACTUAL_SANDBOX simulation]"))
        assertEquals(true,text.contains("a=2"))
    }
}
