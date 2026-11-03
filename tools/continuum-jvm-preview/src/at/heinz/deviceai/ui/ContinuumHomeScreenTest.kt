package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File

/** Layout invariants and real-data rendering of every start-screen element on the JVM (Java2D painter). */
class ContinuumHomeScreenTest {
    private val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    @Test fun layoutsKeepEverySlotOnScreenAndCardsDoNotOverlap() {
        for (layout in listOf(ContinuumHomeLayout.phone(412f), ContinuumHomeLayout.phone(360f), ContinuumHomeLayout.wide(840f), ContinuumHomeLayout.wide(1000f),
            ContinuumHomeLayout.wide(840f,1010f), ContinuumHomeLayout.wide(750f,790f))) {
            for ((slot, box) in layout.boxes) {
                assertTrue("$slot inside width", box.left >= 0f && box.right <= layout.width + .01f)
                assertTrue("$slot positive", box.width > 0f && box.height > 0f)
            }
            val cards = listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT, Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY, Slot.WORKERS).mapNotNull { layout[it] }
            for (i in cards.indices) for (j in i + 1 until cards.size) {
                val a = cards[i]; val b = cards[j]
                assertFalse("cards overlap", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
            }
        }
        // Adaptive design uses fixed dp; the previous scaled-reference assertion is archived in ZETA evidence.
        assertEquals(1f, ContinuumHomeLayout.phone(360f).scale, 0f)
        assertTrue(ContinuumHomeLayout.isWide(840f, 600f))
        assertFalse(ContinuumHomeLayout.isWide(412f, 900f))
    }

    @Test fun freshInstallShowsDashesNeverExampleNumbers() {
        val empty = ContinuumDashboardState()
        for (layout in listOf(ContinuumHomeLayout.phone(412f), ContinuumHomeLayout.wide(840f),
            ContinuumHomeLayout.wide(840f,1010f))) {
            for ((slot, element) in ContinuumHomeScreen.elements(layout, 1f)) {
                val text = element.describe(empty)
                for (example in listOf("98%", "320 ms", "1M Tokens", "Continuum Ultra", "247", "432 GB", "Kontinuierlich", "Globale Ressourcen"))
                    assertFalse("$slot shows example value $example: $text", text.contains(example))
                drawAll(layout, element, slot, empty)
                drawAll(layout, element, slot, ReferencePreviewData.state)
            }
        }
        assertEquals("—", ContinuumDashboardState.percent(null))
        assertEquals("—", ContinuumDashboardState.millis(null))
    }

    @Test fun hologramDrawsEveryRealStateWithoutInventedSpeech() {
        val renderer = ContinuumHologramRenderer()
        for (geometry in listOf(ContinuumHologramRenderer.Geometry.PHONE,
            ContinuumHologramRenderer.Geometry.FOLD, ContinuumHologramRenderer.Geometry.FOLD_PORTRAIT)) {
            renderer.geometry = geometry
            renderer.layout(412f, 307f)
            for (mode in ContinuumHologramRenderer.Mode.entries) {
                val image = BufferedImage(206, 154, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                val p = Java2DContinuumPainter(g, assets)
                p.scale(.5f, .5f)
                renderer.draw(p, ContinuumHologramRenderer.Frame.still(mode, phase = 2.5f))
                g.dispose()
            }
        }
        // speaking without real audio energy: the waveform layer stays hidden (frame audio = 0)
        assertEquals(0f, ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.SPEAKING).audio)
    }

    private fun drawAll(layout: ContinuumHomeLayout.Layout, element: ContinuumElement, slot: Slot, state: ContinuumDashboardState) {
        val box = layout[slot] ?: return
        val image = BufferedImage(maxOf(1, box.width.toInt()), maxOf(1, box.height.toInt()), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        element.draw(Java2DContinuumPainter(g, assets), box.width, box.height, state)
        g.dispose()
    }
}
