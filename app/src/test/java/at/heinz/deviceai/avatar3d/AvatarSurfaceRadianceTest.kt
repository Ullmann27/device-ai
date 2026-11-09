package at.heinz.deviceai.avatar3d

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AvatarSurfaceRadianceTest {
    @Test fun preservesAuthoredBaselineWhenDisabledOrInvalid() {
        for (energy in listOf(-1f, 0f, .18f, .8f, 1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, AvatarSurfaceRadiance.gain(energy, false), 0f)
        }
        assertEquals(1f, AvatarSurfaceRadiance.gain(Float.NaN, true), 0f)
    }

    @Test fun boundedMonotoneResponseUsesRealEnergyWithoutAClock() {
        assertEquals(1.10f, AvatarSurfaceRadiance.gain(.18f, true), 0f)
        assertEquals(1.32f, AvatarSurfaceRadiance.gain(1f, true), .00001f)
        var previous = 1f
        for (sample in -10..110) {
            val gain = AvatarSurfaceRadiance.gain(sample / 100f, true)
            assertTrue(gain in 1.10f..1.32001f && gain >= previous)
            assertEquals(gain, AvatarSurfaceRadiance.gain(sample / 100f, true), 0f)
            previous = gain
        }
    }

    @Test fun readsExactOriginalEmissionWithoutChangingTheTextureOrIris() {
        val bytes = File("src/main/assets/avatar3d/continuum-avatar.glb").readBytes()
        val emission = requireNotNull(AvatarGlbContract.validate(bytes).surfaceEmission)
        assertEquals("DeepIndigoSkin", emission.materialName)
        assertEquals(1.35f, emission.red, .00001f)
        assertEquals(emission.red, emission.green, 0f)
        assertEquals(emission.red, emission.blue, 0f)
        assertTrue(emission.red * AvatarSurfaceRadiance.MAX_GAIN < 1.8f)
    }
}
