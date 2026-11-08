package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HollywoodFuiConstructTest {
    @Test
    fun constructFollowsTheOwnerSequenceAnchorBracketsGridGlassData() {
        // #643 C: Lichtanker → Eckklammern → Scanraster → Glasschichten → Daten.
        assertEquals(0f, HollywoodFuiConstruct.frame(0L).anchor, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.ANCHOR_END_MS).anchor, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.ANCHOR_END_MS).brackets, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.BRACKETS_END_MS).brackets, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.BRACKETS_END_MS).grid, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.GRID_END_MS).grid, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.GLASS_START_MS).glass, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.GLASS_END_MS).glass, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_START_MS).data, 0.0001f)
        assertEquals(1f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS).data, 0.0001f)
        assertTrue(HollywoodFuiConstruct.GLASS_START_MS < HollywoodFuiConstruct.GRID_END_MS)
        assertTrue(HollywoodFuiConstruct.DATA_START_MS < HollywoodFuiConstruct.GLASS_END_MS)
        assertTrue(HollywoodFuiConstruct.DATA_END_MS in 800L..1_200L)
    }

    @Test
    fun everyPhaseIsClampedAndMonotonic() {
        val frames = (-200L..1_400L step 20L).map(HollywoodFuiConstruct::frame)
        listOf(
            frames.map { it.anchor },
            frames.map { it.brackets },
            frames.map { it.grid },
            frames.map { it.glass },
            frames.map { it.data }
        ).forEach { values ->
            assertTrue(values.all { it in 0f..1f })
            assertEquals(values.sorted(), values)
        }
    }

    @Test
    fun perspectiveTiltSettlesToFlatOnceTheGlassStands() {
        val start = HollywoodFuiConstruct.frame(0L)
        val mid = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.GRID_END_MS)
        val settled = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.GLASS_END_MS)
        assertEquals(HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES, start.tiltDegrees, 0.0001f)
        assertTrue(mid.tiltDegrees in 0.0001f..HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES)
        assertEquals(0f, settled.tiltDegrees, 0.0001f)
        assertFalse(settled.settled)
        assertTrue(HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS).settled)
        assertFalse(mid.settled)
    }

    @Test
    fun energyPulsePeaksAtTheAnchorAndIsGoneWhenBracketsStand() {
        val peak = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.ANCHOR_END_MS).pulse
        assertTrue(peak > 0.9f)
        assertTrue(HollywoodFuiConstruct.frame(0L).pulse < 0.05f)
        assertEquals(0f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.BRACKETS_END_MS).pulse, 0.0001f)
        assertEquals(0f, HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS).pulse, 0.0001f)
    }

    @Test
    fun closeChoreographyFadesDataFirstThenGlassThenFrameAndNeverReopensPhases() {
        val open = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS)
        val early = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS, closingElapsedMs = 120L)
        val late = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS, closingElapsedMs = 300L)
        val closed = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS,
            closingElapsedMs = HollywoodFuiConstruct.CLOSE_END_MS)

        assertEquals(1f, open.closeFactor, 0.0001f)
        assertEquals(1f, open.closeData, 0.0001f)
        assertTrue(early.closeData < early.closeGlass)
        assertTrue(early.closeGlass <= early.closeFrame)
        assertTrue(late.closeData <= 0.0001f)
        assertTrue(late.closeFrame > 0f)
        assertEquals(0f, closed.closeFactor, 0.0001f)
        assertEquals(0f, closed.closeFrame, 0.0001f)
        // The construction phases are frozen while closing.
        assertEquals(open.brackets, early.brackets, 0.0001f)
        assertEquals(open.grid, early.grid, 0.0001f)
        assertEquals(open.glass, early.glass, 0.0001f)
        assertEquals(open.data, early.data, 0.0001f)
        // The frame swings back out while it folds and the anchor re-ignites once.
        assertTrue(late.tiltDegrees > 0f)
        assertTrue(early.pulse > 0f)
    }

    @Test
    fun reducedMotionSettlesImmediatelyFlatAndWithoutSweep() {
        val frame = HollywoodFuiConstruct.frame(0L, reducedMotion = true)
        assertEquals(1f, frame.anchor, 0.0001f)
        assertEquals(1f, frame.brackets, 0.0001f)
        assertEquals(1f, frame.grid, 0.0001f)
        assertEquals(1f, frame.glass, 0.0001f)
        assertEquals(1f, frame.data, 0.0001f)
        assertEquals(0f, frame.tiltDegrees, 0.0001f)
        assertEquals(0f, frame.pulse, 0.0001f)
        assertFalse(frame.scanEnabled)
        assertTrue(frame.settled)
    }

    @Test
    fun scanOnlyRunsWhileTheGridStandsAndTheWindowIsOpen() {
        assertFalse(HollywoodFuiConstruct.frame(HollywoodFuiConstruct.BRACKETS_END_MS - 1L).scanEnabled)
        assertTrue(HollywoodFuiConstruct.frame(HollywoodFuiConstruct.GRID_END_MS).scanEnabled)
        assertFalse(HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS,
            closingElapsedMs = HollywoodFuiConstruct.CLOSE_END_MS).scanEnabled)
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
