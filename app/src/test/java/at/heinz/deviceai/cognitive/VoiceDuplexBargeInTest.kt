package at.heinz.deviceai.cognitive

import at.heinz.deviceai.TtsHologramSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 milestone 2 – speech <-> TTS <-> avatar (Argon 4, section 7.1).
 * An active on-device microphone turn must never be talked over by the
 * assistant's own TTS (the recognizer would transcribe JARVIS into the
 * owner's composer), and a barge-in must silence voice AND avatar mouth.
 */
class VoiceDuplexBargeInTest {
    @Test fun assistantNeverSpeaksIntoAnActiveMicrophoneTurn() {
        assertFalse(LocalSpeechInputPolicy.maySpeak(false, microphoneActive = true))
        assertTrue(LocalSpeechInputPolicy.maySpeak(false, microphoneActive = false))
        // Existing offline-voice contract is unchanged by the duplex guard.
        assertTrue(LocalSpeechInputPolicy.maySpeak(false))
        assertFalse(LocalSpeechInputPolicy.maySpeak(null, microphoneActive = false))
        assertFalse(LocalSpeechInputPolicy.maySpeak(true, microphoneActive = false))
    }

    @Test fun bargeInSilencesAvatarMouthAndIgnoresLateCallbacksOfFlushedUtterance() {
        val signal = TtsHologramSignal()
        val pcm = ByteArray(1_600) { if (it % 2 == 0) 0 else 0x40 } // 800 loud 16-bit frames
        signal.activate("utt-1")
        signal.begin("utt-1", 16_000, 2, 1)
        signal.append("utt-1", pcm)
        signal.anchor("utt-1", 0, 1_000)
        assertTrue(signal.level(1_010) > 0f)
        // Barge-in path (startVoiceInput -> stopVoiceAnimation -> voiceSignal.stop()).
        signal.stop()
        assertEquals(0f, signal.level(1_011))
        // Late engine callbacks of the flushed utterance cannot re-open the mouth.
        signal.begin("utt-1", 16_000, 2, 1)
        signal.append("utt-1", pcm)
        signal.anchor("utt-1", 0, 1_020)
        assertEquals(0f, signal.level(1_030))
    }
}
