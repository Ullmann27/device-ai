package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HollywoodFuiConstructTest {
    @Test
    fun constructUsesExactThreePhaseBoundaries() {
        assertEquals(0f, HollywoodFuiConstruct.frame(0L).brackets, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(120L).brackets, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(120L).grid, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(250L).grid, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(250L).data, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(450L).data, 0.0001f)
    }

    @Test
    fun everyPhaseIsClampedAndMonotonic() {
        val frames = listOf(-200L, 0L, 60L, 120L, 185L, 250L, 350L, 450L, 900L)
            .map(HollywoodFuiConstruct::frame)

        listOf(
            frames.map { it.brackets },
            frames.map { it.grid },
            frames.map { it.data }
        ).forEach { values ->
            assertTrue(values.all { it in 0f..1f })
            assertEquals(values.sorted(), values)
        }
    }

    @Test
    fun closeFactorCollapsesWithoutReopeningPhases() {
        val open = HollywoodFuiConstruct.frame(450L)
        val halfClosed = HollywoodFuiConstruct.frame(450L, closingElapsedMs = 160L)
        val closed = HollywoodFuiConstruct.frame(450L, closingElapsedMs = 320L)

        assertEquals(1f, open.closeFactor, 0.0001f)
        assertEquals(0.5f, halfClosed.closeFactor, 0.0001f)
        assertEquals(0f, closed.closeFactor, 0.0001f)
        assertEquals(open.brackets, halfClosed.brackets, 0.0001f)
        assertEquals(open.grid, halfClosed.grid, 0.0001f)
        assertEquals(open.data, halfClosed.data, 0.0001f)
    }

    @Test
    fun reducedMotionSettlesImmediatelyAndDisablesSweep() {
        val frame = HollywoodFuiConstruct.frame(0L, reducedMotion = true)

        assertEquals(1f, frame.brackets, 0.0001f)
        assertEquals(1f, frame.grid, 0.0001f)
        assertEquals(1f, frame.data, 0.0001f)
        assertFalse(frame.scanEnabled)
    }

    @Test
    fun realSceneKindsSelectDistinctWorkWorlds() {
        assertEquals(JarvisActionScenes.FuiProfile.DOCUMENT_BLUEPRINT, JarvisActionScenes.forDocument().fuiProfile)
        assertEquals(JarvisActionScenes.FuiProfile.APP_CONDUIT, JarvisActionScenes.forTool("launch_app").fuiProfile)
        assertEquals(JarvisActionScenes.FuiProfile.CODE_FORGE, JarvisActionScenes.forTool("read_logcat").fuiProfile)
        assertEquals(JarvisActionScenes.FuiProfile.EVIDENCE_CONSTELLATION, JarvisActionScenes.forTool("web_search").fuiProfile)
        assertEquals(JarvisActionScenes.FuiProfile.SECURITY_XRAY,
            requireNotNull(JarvisActionScenes.forCentre("Systemstatus")).fuiProfile)
        assertEquals(JarvisActionScenes.FuiProfile.SWARM_GRID,
            requireNotNull(JarvisActionScenes.forCentre("Logik und Planung")).fuiProfile)
        assertEquals(JarvisActionScenes.FuiProfile.CHRONO_GAUGE,
            JarvisActionScenes.forCalculation().fuiProfile)
    }
}
