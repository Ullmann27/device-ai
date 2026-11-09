package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class AvatarCoreLightingTest {
    @Test fun idleAndFallbackPreserveTheOriginalStudioFill() {
        for (energy in listOf(-1f, 0f, .12f, .18f)) {
            assertEquals(24_000f, AvatarCoreLighting.intensity(energy, true), 0f)
        }
        for (energy in listOf(0f, .42f, .78f, 1f, 2f)) {
            assertEquals(24_000f, AvatarCoreLighting.intensity(energy, false), 0f)
        }
    }

    @Test fun realWorkEnergyRaisesOnlyTheBoundedCyanFill() {
        val listening = AvatarCoreLighting.intensity(.42f, true)
        val thinking = AvatarCoreLighting.intensity(.78f, true)
        val pulse = AvatarCoreLighting.intensity(1f, true)
        assertTrue(listening > 24_000f)
        assertTrue(thinking > listening)
        assertTrue(pulse > thinking)
        assertEquals(30_000f, pulse, 0f)
    }

    @Test fun responseIsMonotonicClampedAndDeterministic() {
        val samples = (-100..200).map { AvatarCoreLighting.intensity(it / 100f, true) }
        assertEquals(samples.sorted(), samples)
        assertTrue(samples.all { it in 24_000f..30_000f })
        repeat(100) { assertEquals(AvatarCoreLighting.intensity(.78f, true),
            AvatarCoreLighting.intensity(.78f, true), 0f) }
    }

    @Test fun invalidPresentationSamplesNeverCorruptTheLight() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(24_000f, AvatarCoreLighting.intensity(value, true), 0f)
        }
    }

    @Test fun activationAndSaturationHaveSoftEdges() {
        val onset = AvatarCoreLighting.intensity(.181f, true) - AvatarCoreLighting.intensity(.18f, true)
        val middle = AvatarCoreLighting.intensity(.591f, true) - AvatarCoreLighting.intensity(.59f, true)
        val end = AvatarCoreLighting.intensity(1f, true) - AvatarCoreLighting.intensity(.999f, true)
        assertTrue(onset < middle * .01f)
        assertTrue(end < middle * .01f)
    }
}
