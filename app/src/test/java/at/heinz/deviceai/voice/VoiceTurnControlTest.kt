package at.heinz.deviceai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** „weiter“ after an interruption, whole-utterance voice commands and the once-only fallback notice. */
class VoiceTurnControlTest {
    private var now = 1_000L
    private val pieces = listOf("Erster Satz.", "Zweiter Satz.", "Dritter Satz.")

    @Test fun anInterruptedReplyResumesFromTheInterruptedPiece() {
        val r = ResumableReplySpeech { now }
        r.begin("reply-1", "s1", pieces)
        r.pieceStarted("reply-1", 0); r.pieceFinished("reply-1", 0)
        r.pieceStarted("reply-1", 1)
        r.ended("reply-1", interrupted = true)
        assertTrue(r.canResume("s1"))
        assertFalse("another chat session cannot resume it", r.canResume("s2"))
        assertEquals(listOf("Zweiter Satz.", "Dritter Satz."), r.takeRest("s1"))
        assertNull("taken once", r.takeRest("s1"))
    }

    @Test fun interruptionDuringThePauseResumesWithTheNextPiece() {
        val r = ResumableReplySpeech { now }
        r.begin("reply-1", "s1", pieces)
        r.pieceStarted("reply-1", 0); r.pieceFinished("reply-1", 0)
        r.ended("reply-1", interrupted = true)
        assertEquals(listOf("Zweiter Satz.", "Dritter Satz."), r.takeRest("s1"))
    }

    @Test fun aCompletedReplyANewReplyOrAnOldInterruptionLeaveNothing() {
        val r = ResumableReplySpeech { now }
        r.begin("reply-1", "s1", pieces)
        r.ended("reply-1", interrupted = false)
        assertFalse(r.canResume("s1"))
        r.begin("reply-2", "s1", pieces)
        r.ended("reply-2", interrupted = true)
        r.begin("reply-3", "s1", listOf("Neu."))
        assertFalse("a new reply replaces the old rest", r.canResume("s1"))
        r.ended("reply-3", interrupted = true)
        now += ResumableReplySpeech.TTL_MS + 1
        assertFalse(r.canResume("s1"))
        r.ended("stale-turn", interrupted = true)
        assertFalse(r.canResume("s1"))
        assertEquals(3, ResumableReplySpeech.pieceIndex("reply-9", "reply-9-3"))
        assertNull(ResumableReplySpeech.pieceIndex("reply-9", "reply-8-3"))
    }

    @Test fun onlyWholeUtterancesAreVoiceCommands() {
        assertEquals(VoiceCommands.Command.RESUME, VoiceCommands.parse("Weiter."))
        assertEquals(VoiceCommands.Command.RESUME, VoiceCommands.parse("bitte sprich weiter"))
        assertEquals(VoiceCommands.Command.CHOOSE_VOICE, VoiceCommands.parse("Wechsle die Stimme"))
        assertEquals(VoiceCommands.Command.FORMER_VOICE, VoiceCommands.parse("Nimm wieder die bisherige Stimme!"))
        assertEquals(VoiceCommands.Command.SLOWER, VoiceCommands.parse("Continuum, sprich langsamer", "Continuum"))
        assertEquals(VoiceCommands.Command.FASTER, VoiceCommands.parse("Sprich schneller bitte"))
        assertEquals(VoiceCommands.Command.CALMER, VoiceCommands.parse("Sprich ruhiger."))
        assertNull(VoiceCommands.parse("weiter mit dem Bericht"))
        assertNull(VoiceCommands.parse("sprich langsamer mit meiner Mutter"))
        assertNull("Stopp belongs to the hands-free mode", VoiceCommands.parse("Stopp"))
        assertEquals(VoiceProfiles.Step.CALMER, VoiceCommands.toStep(VoiceCommands.Command.CALMER))
    }

    @Test fun theFallbackIsAnnouncedOnceWithItsReason() {
        val n = VoiceFallbackNotice()
        assertEquals("Maya nicht verfügbar: Android-Offline-Stimme wird verwendet. Grund: kein ElevenLabs-Schlüssel gespeichert.",
            n.unavailable(VoiceProfiles.FORMER_VOICE_NAME, VoiceFallbackNotice.Cause.NO_KEY))
        assertNull(n.unavailable(VoiceProfiles.FORMER_VOICE_NAME, VoiceFallbackNotice.Cause.NO_KEY))
        assertTrue(n.unavailable("Zeta", VoiceFallbackNotice.Cause.INTERNET_NOT_ALWAYS_ON)!!.startsWith("Zeta nicht verfügbar"))
        assertEquals("Maya konnte nicht sprechen. Die Android-Offline-Stimme spricht weiter.",
            n.continuedLocally(VoiceProfiles.FORMER_VOICE_NAME))
        assertNull(n.continuedLocally(VoiceProfiles.FORMER_VOICE_NAME))
        n.onElevenLabsSpoke()
        assertTrue(n.continuedLocally(VoiceProfiles.FORMER_VOICE_NAME) != null)
        assertEquals("silence is always explained",
            "Maya konnte nicht sprechen. Die Antwort bleibt im Chat. Prüfe Schlüssel, Credits und Internet.",
            n.silent(VoiceProfiles.FORMER_VOICE_NAME))
        assertEquals(VoiceFallbackNotice.Cause.EXTERNAL_SPEECH_FORBIDDEN, VoiceFallbackNotice.cause(true, true, false))
        assertEquals(VoiceFallbackNotice.Cause.NO_KEY, VoiceFallbackNotice.cause(false, true, true))
        assertEquals(VoiceFallbackNotice.Cause.INTERNET_NOT_ALWAYS_ON, VoiceFallbackNotice.cause(true, false, true))
        assertNull(VoiceFallbackNotice.cause(true, true, true))
    }
}
