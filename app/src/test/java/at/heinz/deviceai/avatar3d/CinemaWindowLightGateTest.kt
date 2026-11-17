package at.heinz.deviceai.avatar3d

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CinemaWindowLightGateTest {
    private val sceneAccent = 0xFFAA40FF.toInt()
    private val projectedAccent = 0xFFFFAA20.toInt()

    @After fun reset() = CinemaWindowLightState.reset()

    @Test fun disabledWindowExperimentRestoresStudioLightDespiteAnOpenProjection() {
        CinemaWindowLightState.apply(projectedAccent, -.7f, true, .6f)
        for (fallbackOpen in listOf(false, true)) {
            val light = CinemaWindowLightState.resolve(false, sceneAccent, fallbackOpen, .3f, -.4f)
            assertEquals(CinemaWindowLightState.Snapshot(), light)
            val tint = SpatialWindowLight.tint(light.accentArgb, .75f, light.open, true,
                light.gazeX, light.gazeY)
            assertEquals(SpatialWindowLight.tint(null, .75f, false, true), tint)
            assertTrue("Core response stays enabled", tint.lumens > AvatarCoreLighting.BASE_LUMENS)
        }
    }

    @Test fun enablingAgainUsesTheCurrentProjectionWithoutReplayingAnEvent() {
        CinemaWindowLightState.apply(projectedAccent, -.7f, true, .6f)
        assertFalse(CinemaWindowLightState.resolve(false, sceneAccent, true).open)
        val light = CinemaWindowLightState.resolve(true, sceneAccent, false, .3f, -.4f)
        assertEquals(CinemaWindowLightState.Snapshot(projectedAccent, -.7f, .6f, true), light)
    }

    @Test fun projectedGeometryRetainsFallbackAccentWhenNoAccentWasPublished() {
        CinemaWindowLightState.apply(null, -.7f, true, .6f)
        assertEquals(CinemaWindowLightState.Snapshot(sceneAccent, -.7f, .6f, true),
            CinemaWindowLightState.resolve(true, sceneAccent, false, .3f, -.4f))
    }

    @Test fun closedProjectionFallsBackOnlyToAStillOpenPhysicalWindow() {
        CinemaWindowLightState.apply(projectedAccent, -.7f, true, .6f)
        CinemaWindowLightState.reset()
        assertEquals(CinemaWindowLightState.Snapshot(sceneAccent, .3f, -.4f, true),
            CinemaWindowLightState.resolve(true, sceneAccent, true, .3f, -.4f))
        assertEquals(CinemaWindowLightState.Snapshot(),
            CinemaWindowLightState.resolve(true, sceneAccent, false, .3f, -.4f))
    }

    @Test fun invalidFallbackGazeCannotMoveTheLightOutOfBounds() {
        assertEquals(CinemaWindowLightState.Snapshot(sceneAccent, 0f, 0f, true),
            CinemaWindowLightState.resolve(true, sceneAccent, true, Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(CinemaWindowLightState.Snapshot(sceneAccent, 1f, -1f, true),
            CinemaWindowLightState.resolve(true, sceneAccent, true, 4f, -3f))
    }
}
