package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integrated source check for the original animated orb's bounded luminous core. */
class ContinuumOrbCoreIntegrationTest {
    private fun coreAlpha(signals: ContinuumBrainOrbRenderer.Signals): Float {
        val recorded = mutableListOf<Float>()
        val painter = Proxy.newProxyInstance(
            ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                "fillCircle" -> {
                    if (arguments != null && arguments.size >= 5 &&
                        (arguments[2] as? Float) == 78f) {
                        recorded += arguments[4] as Float
                    }
                    null
                }
                else -> null
            }
        } as ContinuumPainter
        val orb = ContinuumBrainOrbRenderer()
        assertEquals("Continuum", orb.label1)
        assertEquals("Gehirn", orb.label2)
        orb.draw(painter, 0f, 0f, 100f, signals)
        assertEquals("one luminous core ring", 1, recorded.size)
        return recorded.single()
    }

    @Test fun realMemoryPulseAndProcessingIncreaseCoreBrightness() {
        val calm = coreAlpha(ContinuumBrainOrbRenderer.Signals(phase = 0f))
        val active = coreAlpha(ContinuumBrainOrbRenderer.Signals(
            phase = 0f, pulse = 1f, processing = true
        ))
        assertTrue("activity must brighten core: $calm -> $active", active > calm + .2f)
        assertTrue("alpha must stay bounded", active in 0f..1f)
    }

    @Test fun idleMotionWithoutFakeWorkHasBoundedAlpha() {
        val zero = coreAlpha(ContinuumBrainOrbRenderer.Signals(phase = 0f))
        val later = coreAlpha(ContinuumBrainOrbRenderer.Signals(phase = 3f))
        assertTrue(zero in 0f..1f)
        assertTrue(later in 0f..1f)
    }

    @Test fun idleCoreBreathesButStaysBelowRealActivity() {
        val active = coreAlpha(ContinuumBrainOrbRenderer.Signals(
            phase = 0f, pulse = 1f, processing = true
        ))
        val idle = listOf(0f, .7f, 1.4f, 3f).map { phase ->
            coreAlpha(ContinuumBrainOrbRenderer.Signals(phase = phase))
        }
        assertTrue("idle light must breathe", idle.max() - idle.min() > .02f)
        idle.forEach { alpha ->
            assertTrue("idle $alpha must stay below work $active", alpha < active - .15f)
        }
    }
}
