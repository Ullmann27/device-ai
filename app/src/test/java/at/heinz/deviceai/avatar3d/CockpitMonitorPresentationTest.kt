package at.heinz.deviceai.avatar3d

import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

/** Actual production matrix/flight math plus a source-wiring guard; not a native GPU/device claim. */
class CockpitMonitorPresentationTest {
    private val layouts = listOf(
        CockpitStageFrame(852f, 480f, 270f, 180f, 590f, 390f),
        CockpitStageFrame(412f, 915f, 15f, 350f, 397f, 650f),
        CockpitStageFrame(1536f, 1024f, 400f, 400f, 1100f, 800f))

    @Test fun rearPresentationDoesNotInvalidateTheStage() {
        val frame = layouts.first()
        val original = frame.copy()
        // Reproduces the rejected production call before the repair, without weakening valid().
        assertFalse(frame.copy(yawDegrees = -103f).valid())
        val turned = frame.monitorTransform(-100f)
        assertTrue(frame.valid())
        assertEquals(original, frame)
        assertFalse(turned.contentEquals(frame.monitorTransform()))
        assertTrue(turned.all(Float::isFinite))
    }

    @Test fun presentationKeepsPivotScaleAndOrthogonalAxesAcrossLayouts() {
        for (base in layouts) for (yaw in listOf(-8f, -3f, 0f, 8f)) for (pitch in listOf(-12f, 0f, 12f)) {
            val frame = base.copy(yawDegrees = yaw, pitchDegrees = pitch)
            val rest = frame.monitorTransform()
            for (step in 0..100) {
                val actual = frame.monitorTransform(-100f + step)
                for (i in 12..15) assertEquals("Monitor pivot must not orbit", rest[i], actual[i], 0f)
                for (column in 0..2) assertEquals("Turn must precede nonuniform scale", norm(rest, column), norm(actual, column), 1e-6)
                for ((a, b) in listOf(0 to 1, 1 to 2, 0 to 2)) {
                    val dot = (0..2).sumOf { actual[a * 4 + it].toDouble() * actual[b * 4 + it] }
                    assertEquals("No shear during presentation", 0.0, dot, 1e-6)
                }
            }
        }
    }

    @Test fun restOverlayAndOrbRemainUnchanged() {
        for (frame in layouts) {
            val snapshot = frame.copy()
            val quad = frame.quad()
            val orb = frame.orbTransform(20f, 20f, 140f, 140f)
            val buffer = frame.bufferSize()
            for (turn in listOf(-100f, -50f, -1f, 0f)) {
                frame.monitorTransform(turn)
                assertEquals(snapshot, frame)
                assertArrayEquals(quad, frame.quad(), 0f)
                assertArrayEquals(orb, frame.orbTransform(20f, 20f, 140f, 140f), 0f)
                assertEquals(buffer, frame.bufferSize())
            }
        }
    }

    @Test fun actualFlightReturnsToTheExactRestMatrix() {
        for (frame in layouts) {
            val rest = frame.monitorTransform()
            val d = (frame.distance * frame.worldScale).toDouble()
            for (step in 0..100) {
                val pose = CockpitCameraFlight.pose(step / 100.0,
                    rest[12].toDouble(), rest[13].toDouble(), rest[14].toDouble(),
                    (frame.monitorWidth * frame.restCompensation * frame.worldScale).toDouble(), d)
                val actual = frame.monitorTransform(pose.monitorYawDegrees.toFloat())
                assertTrue(frame.valid())
                assertTrue(actual.all(Float::isFinite))
                if (step == 100) {
                    assertTrue(CockpitCameraFlight.nearlyRest(pose, d))
                    assertTrue("Rest must be bit-exact", rest.contentEquals(actual))
                }
            }
        }
    }

    @Test fun invalidPresentationValuesAreRejected() {
        val frame = layouts.first()
        for (turn in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -181f, 181f)) {
            assertTrue("Invalid turn $turn", runCatching { frame.monitorTransform(turn) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { frame.copy(yawDegrees = -103f).monitorTransform(100f) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { frame.copy(width = 0f).monitorTransform(0f) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun rendererKeepsTheFrameAndStageTurnsOnlyMonitorGeometry() {
        val renderer = source("FilamentAvatarRenderer.kt")
        val stage = source("FilamentCockpitStage.kt")
        assertFalse("Never put presentation yaw into the stage frame", renderer.contains("frame.copy(yawDegrees"))
        assertTrue(renderer.contains("cockpitStage?.update(frame, cockpitOrb, orbPhase, motion,"))
        assertTrue(renderer.contains("monitorTurnDegrees = (pose?.monitorYawDegrees ?: 0.0).toFloat()"))
        assertTrue(stage.contains("val monitorMatrix = frame.monitorTransform(monitorTurnDegrees)"))
        assertTrue(stage.contains("setTransform(monitorTransform, monitorMatrix)"))
        assertTrue(stage.contains("setTransform(monitorRearTransform, monitorMatrix)"))
        assertTrue(stage.contains("updateGlow(monitorMatrix, now)"))
        assertTrue(stage.contains("CockpitWorldTransforms.room(frame, transform)"))
        assertTrue(stage.contains("CockpitWorldTransforms.orb(frame,"))
        assertTrue(stage.contains("contextPanels?.update(contextContent, frame)"))
    }

    private fun norm(matrix: FloatArray, column: Int): Double =
        sqrt((0..2).sumOf { matrix[column * 4 + it].toDouble() * matrix[column * 4 + it] })

    private fun source(name: String): String {
        val path = "app/src/main/java/at/heinz/deviceai/avatar3d/$name"
        return (File(path).takeIf(File::isFile) ?: File("../$path")).readText()
    }
}
