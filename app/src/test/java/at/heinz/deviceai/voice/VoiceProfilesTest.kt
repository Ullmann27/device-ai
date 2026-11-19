package at.heinz.deviceai.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProfilesTest {
    /** The request body the app sent before profiles existed (AndroidMayaSpeech, c508b5589). */
    private fun formerBody(text: String) = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
        .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.75)
            .put("style", 0.0).put("use_speaker_boost", true)).toString()

    @Test fun formerVoiceIsTheDefaultAndSendsExactlyTheFormerRequest() {
        val default = VoiceProfileSelection()
        assertTrue(default.isFormerVoice)
        assertEquals(ProsodyPreset.BISHERIG, default.preset)
        val spec = VoiceProfiles.elevenLabs(default)
        assertEquals("ii0s2u4R3UFnxKL6DOrz", spec.voiceId)
        assertEquals("eleven_multilingual_v2", spec.modelId)
        val text = "Eine Antwort mit \"Zeichen\" und Umlauten: schön."
        assertEquals(formerBody(text), VoiceProfiles.requestBody(text, spec).toString())
        assertFalse(VoiceProfiles.requestBody(text, spec).getJSONObject("voice_settings").has("speed"))
        // The device voice keeps the former rate and pitch (JarvisVoiceProfile).
        assertEquals(at.heinz.deviceai.cognitive.JarvisVoiceProfile.speechRate, ProsodyPreset.BISHERIG.prosody.deviceRate)
        assertEquals(at.heinz.deviceai.cognitive.JarvisVoiceProfile.pitch, ProsodyPreset.BISHERIG.prosody.devicePitch)
        assertEquals(100, ProsodyPreset.BISHERIG.prosody.pausePercent)
    }

    @Test fun presetsStayInsideTheDocumentedRanges() {
        for (preset in ProsodyPreset.entries) {
            val p = preset.prosody
            assertTrue(preset.name, p.stability in 0.0..1.0 && p.similarityBoost in 0.0..1.0)
            assertEquals("documentation: keep style at 0", 0.0, p.style, 0.0)
            assertTrue(p.useSpeakerBoost)
            p.speed?.let { assertTrue(preset.name, it in 0.7..1.2) }
            assertTrue(p.deviceRate in 0.6f..1.3f)
            assertEquals("pitch is never shifted (resampling)", 1.0f, p.devicePitch)
        }
        val calm = ProsodyPreset.SOUVERAEN.prosody
        assertTrue("Souverän is steadier than the default", calm.stability > ProsodyPreset.BISHERIG.prosody.stability)
        assertTrue("and slightly slower", calm.speed!! < 1.0 && calm.deviceRate < ProsodyPreset.BISHERIG.prosody.deviceRate)
        assertTrue("with longer pauses", calm.pausePercent > 100)
        assertTrue("Warm varies more", ProsodyPreset.WARM.prosody.stability < ProsodyPreset.BISHERIG.prosody.stability)
    }

    @Test fun presetIsAppliedToWhicheverVoiceIsSelected() {
        val s = VoiceProfileSelection(VoiceEngine.ELEVENLABS, "AbCdEf1234567890", "Tiefe Stimme", ProsodyPreset.SOUVERAEN)
        val spec = VoiceProfiles.elevenLabs(s)
        assertEquals("AbCdEf1234567890", spec.voiceId)
        val settings = VoiceProfiles.requestBody("Hallo", spec).getJSONObject("voice_settings")
        assertEquals(0.70, settings.getDouble("stability"), 1e-9)
        assertEquals(0.80, settings.getDouble("similarity_boost"), 1e-9)
        assertEquals(0.94, settings.getDouble("speed"), 1e-9)
        // An invalid stored id never reaches a URL.
        assertEquals(VoiceProfiles.FORMER_VOICE_ID, VoiceProfiles.elevenLabs(s.copy(elevenVoiceId = "../x?y")).voiceId)
    }

    @Test fun spokenStepsAreBoundedAndSayWhatChanged() {
        var s = VoiceProfileSelection(engine = VoiceEngine.ELEVENLABS)
        val slower = VoiceProfiles.step(s, VoiceProfiles.Step.SLOWER)
        assertTrue(slower.changed)
        assertEquals(-1, slower.selection.rateStep)
        assertEquals("Ich spreche jetzt etwas langsamer (Tempo 100 % → 96 %).", slower.sentence)
        repeat(10) { s = VoiceProfiles.step(s, VoiceProfiles.Step.SLOWER).selection }
        assertEquals(VoiceProfiles.MIN_RATE_STEP, s.rateStep)
        val limit = VoiceProfiles.step(s, VoiceProfiles.Step.SLOWER)
        assertFalse(limit.changed)
        assertTrue(limit.sentence.startsWith("Langsamer geht es nicht"))
        repeat(10) { s = VoiceProfiles.step(s, VoiceProfiles.Step.CALMER).selection }
        assertEquals(VoiceProfiles.MAX_CALM_STEP, s.calmStep)
        val p = VoiceProfiles.prosody(s)
        assertTrue(p.speed!! >= 0.7 && p.stability <= 0.9 && p.pausePercent <= 140)
        val normal = VoiceProfiles.step(s, VoiceProfiles.Step.NORMAL).selection
        assertEquals(0, normal.rateStep); assertEquals(0, normal.calmStep)
        val device = VoiceProfiles.step(VoiceProfileSelection(), VoiceProfiles.Step.FASTER)
        assertEquals("Ich spreche jetzt etwas schneller (Tempo 96 % → 101 %).", device.sentence)
    }

    @Test fun formerKeepsTheEngineAndResetsEverythingElse() {
        val s = VoiceProfileSelection(VoiceEngine.ELEVENLABS, "AbCdEf1234567890", "X", ProsodyPreset.WARM, 2, 1)
        val former = VoiceProfiles.former(s)
        assertEquals(VoiceEngine.ELEVENLABS, former.engine)
        assertTrue(former.isFormerVoice)
        assertNull(former.elevenVoiceId)
        assertEquals("Maya – Warm Californian · ElevenLabs · Bisherige Stimme", VoiceProfiles.describe(former))
    }

    @Test fun testSentenceCostIsTheLengthOfWhatIsSent() {
        assertEquals("Guten Tag. Ich habe die Lage geprüft: Alles ist ruhig, der nächste Termin ist um 14 Uhr 30.",
            VoiceProfiles.testSpoken)
        assertEquals(VoiceProfiles.testSpoken.length, VoiceProfiles.testCharacters)
        assertTrue(VoiceProfiles.testCharacters < 120)
    }
}
