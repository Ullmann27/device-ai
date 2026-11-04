package at.heinz.deviceai.motion

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class LipSyncFaceSourceTest {
    private val names = FaceChannels.RENDERER_ORDER
    private val jaw = names.indexOf("JawOpen")

    private fun mouth(out: FloatArray) = (0..14).map { out[it] } + out[jaw]

    @Test fun channelsAreTheRendererOrder() {
        assertEquals(AvatarGlbContract.TARGETS, names)
        assertEquals(21, names.size)
        assertEquals(Viseme.entries.map { it.name }, names.take(15))
    }

    @Test fun silenceKeepsMouthClosed() {
        val f = LipSyncFaceSource(AudioLevelSource { .01f }, VisemeHintSource { 10 })
        val out = FloatArray(21)
        for (t in 0L..3000L step 16) { f.weightsAt(t, out); assertTrue(mouth(out).all { it == 0f }); assertEquals(0f, f.jawOpen, 0f) }
    }

    @Test fun attackAndReleaseEnvelope() {
        var level = .8f
        val f = LipSyncFaceSource(AudioLevelSource { level })
        val out = FloatArray(21)
        f.weightsAt(0, out)
        f.weightsAt(30, out)
        assertEquals(.8f * (1f - exp(-1f)), f.envelope, 1e-4f)
        // same result in many small steps (exact one-pole composition)
        val g = LipSyncFaceSource(AudioLevelSource { .8f }); g.weightsAt(0, out); for (t in 1L..30L) g.weightsAt(t, out)
        assertEquals(f.envelope, g.envelope, 1e-4f)
        f.weightsAt(500, out); val open = f.envelope; assertTrue(open > .79f && out[jaw] > .9f)
        level = 0f
        f.weightsAt(620, out); assertEquals(open * exp(-1f), f.envelope, 1e-3f)
        f.weightsAt(2000, out); assertTrue(mouth(out).all { it == 0f })
        assertTrue(out.all { it in 0f..1f })
    }

    @Test fun rmsOnlyMovesJawOnly() {
        val f = LipSyncFaceSource(AudioLevelSource { .5f }); val out = FloatArray(21)
        for (t in 0L..400L step 16) f.weightsAt(t, out)
        assertTrue(out[jaw] > 0f); assertTrue((0..14).all { out[it] == 0f })
    }

    @Test fun visemeCrossfadeAndJawCompensation() {
        var v = 13 // O
        val f = LipSyncFaceSource(AudioLevelSource { .6f }, VisemeHintSource { v }); val out = FloatArray(21)
        f.weightsAt(0, out); f.weightsAt(35, out)
        assertEquals(.5f, out[13], .02f) // half of the 70 ms crossfade, gate already full
        for (t in 40L..300L step 20) f.weightsAt(t, out)
        assertEquals(1f, out[13], 1e-6f); assertEquals(13, f.dominantViseme)
        assertEquals((f.jawOpen - FaceChannels.SHARED_OPENING[13]).coerceIn(0f, 1f), out[jaw], 1e-5f)
        v = 10 // AA
        f.weightsAt(335, out); assertEquals(.5f, out[13], .02f); assertEquals(.5f, out[10], .02f)
        f.weightsAt(400, out); assertEquals(0f, out[13], 0f); assertEquals(1f, out[10], 0f)
    }

    @Test fun sharedOpeningMatchesAvatarMorphMapper() {
        for (v in Viseme.entries) {
            // Each viseme is an independent sample, not a repeated timestamp in one running blend.
            val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
            mapper.update(AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, 1f, v, 1f, 1f), 1f, true)
            val expected = FloatArray(names.size)
            if (v != Viseme.SIL) expected[names.indexOf(v.name)] = 1f
            // Authored opening coefficients are unchanged. Active PP additionally seals the lips.
            expected[jaw] = if (v == Viseme.PP) 0f else 1f - FaceChannels.SHARED_OPENING[v.ordinal]
            assertArrayEquals(v.name, expected, mapper.weights, 1e-5f)
            assertEquals("${v.name} inner jaw", if (v == Viseme.PP) 0f else 12f, mapper.jawPitch, 1e-5f)
        }
    }

    @Test fun sourcePoseBridgePreservesMeasuredAudioWhileMapperClosesBilabialOnset() {
        var hint = Viseme.AA
        val source = LipSyncFaceSource(AudioLevelSource { .8f }, VisemeHintSource { hint.ordinal })
        val out = FloatArray(names.size)
        val base = AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, 0f, Viseme.SIL, 0f, 0f)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        source.weightsAt(0, out)
        source.weightsAt(300, out)

        val vowel = source.applyTo(base)
        assertEquals(Viseme.AA, vowel.viseme)
        assertEquals(1f, vowel.visemeWeight, 0f)
        assertEquals(1f, vowel.jawOpen, 0f)
        assertEquals(source.envelope, vowel.speakEnergy, 0f)
        mapper.update(vowel, 1f, true)
        val expectedVowel = FloatArray(names.size)
        expectedVowel[names.indexOf("AA")] = 1f
        expectedVowel[jaw] = 1f - FaceChannels.SHARED_OPENING[Viseme.AA.ordinal]
        assertArrayEquals(expectedVowel, mapper.weights, 1e-5f)
        assertEquals(out[jaw], mapper.weights[jaw], 1e-5f)
        assertEquals(12f, mapper.jawPitch, 1e-5f)

        hint = Viseme.PP
        source.weightsAt(400, out)
        val bilabial = source.applyTo(base)
        assertEquals(Viseme.PP, bilabial.viseme)
        assertEquals(1f, bilabial.visemeWeight, 0f)
        assertEquals(1f, bilabial.jawOpen, 0f)
        assertEquals(source.envelope, bilabial.speakEnergy, 0f)
        assertTrue(bilabial.speakEnergy > .79f)
        assertEquals(1f, out[jaw], 0f)

        // The bridge retains measured audio; the renderer owns PP's immediate closure,
        // including when the presentation clock has not advanced since the vowel.
        mapper.update(bilabial, 1f, true)
        val expectedClosure = FloatArray(names.size)
        expectedClosure[names.indexOf("PP")] = 1f
        assertArrayEquals(expectedClosure, mapper.weights, 0f)
        assertEquals(0f, mapper.jawPitch, 0f)
    }

    @Test fun blinkDurationAndGapsDeterministic() {
        val starts = (0L until 200L).map { LipSyncFaceSource.blinkStartMs(it, 7L) }
        starts.zipWithNext { a, b -> assertTrue("gap ${b - a}", b - a in 3000L..6000L) }
        val s = starts[5]
        assertEquals(0f, LipSyncFaceSource.blinkAt(s - 1, 7L), 0f)
        assertEquals(1f, LipSyncFaceSource.blinkAt(s + 75, 7L), 1e-4f)
        assertEquals(0f, LipSyncFaceSource.blinkAt(s + 150, 7L), 0f)
        val closedMs = (s - 10..s + 200).count { LipSyncFaceSource.blinkAt(it, 7L) > 0f }
        assertEquals(149, closedMs) // u = 1..149 > 0, u = 0 is sin 0
        assertTrue(starts != (0L until 200L).map { LipSyncFaceSource.blinkStartMs(it, 8L) })
        val f = LipSyncFaceSource(AudioLevelSource { 0f }, seed = 7L); val out = FloatArray(21)
        f.weightsAt(s + 75, out); assertEquals(1f, out[names.indexOf("BlinkLeft")], 1e-4f); assertEquals(out[15], out[16], 0f)
    }

    @Test fun deterministicReplayAndBackwardTimeResets() {
        val level = AudioLevelSource { t -> ((t / 50) % 3) * .3f }
        val hint = VisemeHintSource { t -> ((t / 90) % 15).toInt() }
        fun run(f: LipSyncFaceSource): FloatArray { val out = FloatArray(21); val acc = FloatArray(21)
            for (t in 0L..2000L step 16) { f.weightsAt(t, out); for (i in 0 until 21) acc[i] += out[i] }; return acc }
        val a = LipSyncFaceSource(level, hint, 3L); val b = LipSyncFaceSource(level, hint, 3L)
        val ra = run(a)
        assertArrayEquals(ra, run(b), 0f)
        assertArrayEquals(ra, run(a), 0f) // time went back to 0 → reset → identical replay
    }

    @Test fun breathAndPoseBridge() {
        assertEquals(1f, LipSyncFaceSource.breath(1000), 1e-5f); assertEquals(0f, LipSyncFaceSource.breath(4000), 1e-5f)
        val f = LipSyncFaceSource(AudioLevelSource { .7f }, VisemeHintSource { 13 }); val out = FloatArray(21)
        for (t in 0L..300L step 16) f.weightsAt(t, out)
        val p = f.applyTo(AvatarPose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f, 0f, Viseme.SIL, 0f, 0f))
        assertEquals(Viseme.O, p.viseme); assertEquals(f.jawOpen, p.jawOpen, 0f); assertEquals(f.envelope, p.speakEnergy, 0f)
    }
}
