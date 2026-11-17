package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bot 07 wires GlassBackend.drawBackground / drawPanelGlass so Bot 06's AGSL shaders can run on device. */
class JarvisShellRendererGlassHookTest {
    private class Probe(
        var acceptBackground: Boolean = true,
        var acceptPanel: Boolean = true
    ) : JarvisShellRenderer.GlassBackend {
        var backgrounds = 0
        var lastWidth = 0f
        var lastHeight = 0f
        var lastTime = -1L
        val panels = mutableListOf<Box>()
        override fun drawBackground(width: Float, height: Float, timeMs: Long): Boolean {
            backgrounds++
            lastWidth = width
            lastHeight = height
            lastTime = timeMs
            return acceptBackground
        }
        override fun drawPanelGlass(box: Box, radius: Float, glow: Float): Boolean {
            panels += box
            return acceptPanel
        }
    }

    @Test fun drawInvokesNativeBackgroundAndPanelGlassOnTheCinemaShell() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val probe = Probe()
        JarvisShellRenderer.draw(JarvisPresentationRecordingPainter(), shell, glass = probe, timeMs = 1_250L)
        assertEquals(1, probe.backgrounds)
        assertEquals(884f, probe.lastWidth, 0.01f)
        assertEquals(760f, probe.lastHeight, 0.01f)
        assertEquals(1_250L, probe.lastTime)
        assertTrue("work windows and HUD cards must request panel glass", probe.panels.size >= 3)
        assertTrue(probe.panels.any { it == shell[Slot.INPUT] })
        assertTrue(probe.panels.any { it == shell[Slot.WORK_DOCUMENT] })
        assertTrue(probe.panels.any { it == shell[Slot.WORK_PROCESS] })
        assertTrue(probe.panels.any { it == shell[Slot.WORK_DRAFT] })
    }

    @Test fun compactPhoneCinemaAlsoRequestsPanelGlassForItsWorkWindows() {
        val shell = JarvisShellLayout.create(412f, 915f)
        val probe = Probe()
        JarvisShellRenderer.draw(JarvisPresentationRecordingPainter(), shell, glass = probe, timeMs = 40L)
        assertEquals(1, probe.backgrounds)
        assertEquals(412f, probe.lastWidth, 0.01f)
        assertTrue(probe.panels.any { it == shell[Slot.WORK_DOCUMENT] })
        assertTrue(probe.panels.any { it == shell[Slot.DIALOG] })
    }

    @Test fun decliningBackendStillFallsBackToThePortablePath() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val probe = Probe(acceptBackground = false, acceptPanel = false)
        JarvisShellRenderer.draw(JarvisPresentationRecordingPainter(), shell, glass = probe, timeMs = 0L)
        assertEquals("the hook must be offered even when AGSL is unavailable", 1, probe.backgrounds)
        assertTrue(probe.panels.isNotEmpty())
        JarvisShellRenderer.draw(JarvisPresentationRecordingPainter(), shell, glass = null, timeMs = 0L)
    }
}
