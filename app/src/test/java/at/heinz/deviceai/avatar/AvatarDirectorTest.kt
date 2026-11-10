package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3: avatar preparation contract (docs/CONTINUUM_AVATAR_CONTRACT.md). No rendering. */
class AvatarDirectorTest {
    private class Recorder : AvatarSink {
        val events = mutableListOf<AvatarEvent>()
        override fun onAvatarEvent(event: AvatarEvent) { events += event }
    }
    private var now = 1_000L
    private fun director(sink: AvatarSink) = AvatarDirector(sink) { now }

    @Test fun projectedGlanceCannotReplaceActualDocumentReading() {
        val sink = Recorder(); val d = director(sink)
        d.onRuntime(AvatarRuntimeSignals(busy = true))
        assertTrue(d.windowWorkStarted("document", GazeTarget.WORK_WINDOW))
        assertEquals(false, d.canAdmitWindowGlance())
        assertEquals(false, d.windowOpened("cinema:turn:document", GazeTarget(-.4f, .2f)))
        assertTrue(sink.events.filterIsInstance<AvatarEvent.AnimationIntentsChanged>().last().windowReading)
        assertTrue(d.windowWorkEnded("document"))
        assertTrue(d.canAdmitWindowGlance())
        assertTrue(d.windowOpened("cinema:turn:document", GazeTarget.WORK_WINDOW))
    }

    @Test fun legacyGlanceCannotReplaceActualDocumentReading() {
        val sink = Recorder(); val d = director(sink)
        assertTrue(d.windowWorkStarted("document", GazeTarget.WORK_WINDOW))
        d.onWindowPresented()
        assertTrue(sink.events.filterIsInstance<AvatarEvent.AnimationIntentsChanged>().last().windowReading)
    }

    @Test fun expiredReadingCannotBlockLaterWindows() {
        val d = director(Recorder())
        assertTrue(d.windowWorkStarted("document", GazeTarget.WORK_WINDOW))
        now += AvatarPoseReducer.MAX_WINDOW_WORK_MS
        assertTrue(d.canAdmitWindowGlance())
        assertTrue(d.windowOpened("next-window", GazeTarget.WORK_WINDOW))
    }

    @Test fun runtimeFlagsMapToOneStateWithPriorityAndNoDuplicates() {
        val r = Recorder(); val d = director(r)
        d.onRuntime(AvatarRuntimeSignals(busy = true))
        d.onRuntime(AvatarRuntimeSignals(busy = true))          // unchanged → no event
        d.onRuntime(AvatarRuntimeSignals(busy = true, listening = true)) // listening wins
        d.onRuntime(AvatarRuntimeSignals(speaking = true, busy = true))
        d.onRuntime(AvatarRuntimeSignals(awaitingApproval = true, busy = true))
        d.onRuntime(AvatarRuntimeSignals())
        val states = r.events.filterIsInstance<AvatarEvent.StateChanged>().map { it.state }
        assertEquals(listOf(AvatarState.THINKING, AvatarState.LISTENING, AvatarState.SPEAKING,
            AvatarState.WAITING_FOR_APPROVAL, AvatarState.IDLE), states)
        val approval = r.events.filterIsInstance<AvatarEvent.StateChanged>().first { it.state == AvatarState.WAITING_FOR_APPROVAL }
        assertEquals(AvatarExpression.ATTENTIVE, approval.expression)
        assertEquals(GazeTarget.OWNER, approval.gaze)
    }

    @Test fun speechProducesStartVisemesLevelsAndEndForTheCurrentUtteranceOnly() {
        val r = Recorder(); val d = director(r)
        d.speechStarted("u1", "Hallo Heinz")
        d.wordRange("u1", 0, 5)
        now += 33; d.audioLevel("u1", 1.7f)
        d.wordRange("old", 0, 5)       // stale utterance: ignored
        d.audioLevel("old", 0.5f)
        d.speechEnded("u1", interrupted = false)
        d.wordRange("u1", 6, 11)       // after end: ignored
        val kinds = r.events.map { it::class.simpleName }
        assertEquals("SpeechStarted", kinds.first()); assertEquals("SpeechEnded", kinds.last())
        val cues = r.events.filterIsInstance<AvatarEvent.VisemeCue>()
        assertEquals(listOf(Viseme.SIL, Viseme.AA, Viseme.NN, Viseme.O), cues.map { it.viseme })
        assertTrue(cues.all { it.utteranceId == "u1" && it.durationMs > 0 })
        assertEquals(1f, r.events.filterIsInstance<AvatarEvent.AudioLevel>().single().level)
    }

    @Test fun germanGraphemesMapToCoarseVisemes() {
        assertEquals(listOf(Viseme.SIL, Viseme.CH, Viseme.I, Viseme.FF), GermanVisemeApproximation.visemes("Schiff"))
        assertEquals(listOf(Viseme.SIL, Viseme.PP, Viseme.U, Viseme.SS), GermanVisemeApproximation.visemes("Bus"))
        assertEquals(listOf(Viseme.SIL, Viseme.I, Viseme.KK), GermanVisemeApproximation.visemes("ich"))
        assertEquals(emptyList<Viseme>(), GermanVisemeApproximation.visemes("123 !"))
    }

    @Test fun aFailingSinkNeverBreaksTheAppAndNoOpIsDefault() {
        val d = director(AvatarSink { error("renderer crashed") })
        d.onRuntime(AvatarRuntimeSignals(busy = true)); d.speechStarted("u", "Hi"); d.speechEnded("u", true)
        val n = AvatarDirector(NoOpAvatarSink) { 0L }
        n.onRuntime(AvatarRuntimeSignals(listening = true))
        assertEquals(AvatarState.LISTENING, n.state)
        // A sink attached later receives the current state immediately.
        val r = Recorder(); n.attach(r)
        assertEquals(AvatarState.LISTENING, (r.events.single() as AvatarEvent.StateChanged).state)
    }
}


/** M3 coordination: parts ported from the avatar lab (PR #338 branch) and PR #350 (TalkingHead lipsync-de rules, AvatarPose plan). */
class AvatarPortedPartsTest {
    private var now = 10_000L
    private class Recorder : AvatarSink {
        val events = mutableListOf<AvatarEvent>()
        override fun onAvatarEvent(event: AvatarEvent) { events += event }
    }

    @Test fun wordRangeFallbackWhenTtsDeliversNoPcm() {
        val r = Recorder(); val d = AvatarDirector(r) { now }
        d.speechStarted("u", "Hallo Heinz"); d.wordRange("u", 0, 5)
        now += 100; d.audioLevel("u", 0f)
        now += 600; d.audioLevel("u", 0f)
        now += 10; d.audioLevel("u", 0.6f)
        val levels = r.events.filterIsInstance<AvatarEvent.AudioLevel>()
        assertEquals(listOf(AudioLevelSource.WORD_RANGE_FALLBACK, AudioLevelSource.PCM_ENVELOPE, AudioLevelSource.PCM_ENVELOPE),
            levels.map { it.source })
        assertTrue(levels[0].level in 0.10f..0.28f)
        assertEquals(0f, levels[1].level); assertEquals(0.6f, levels[2].level)
    }

    @Test fun germanDiphthongsBecomeTwoVisemes() {
        assertEquals(listOf(Viseme.SIL, Viseme.AA, Viseme.U, Viseme.SS), GermanVisemeApproximation.visemes("Haus"))
        assertEquals(listOf(Viseme.SIL, Viseme.PP, Viseme.AA, Viseme.I, Viseme.NN), GermanVisemeApproximation.visemes("Bein"))
        assertEquals(listOf(Viseme.SIL, Viseme.NN, Viseme.O, Viseme.DD, Viseme.E), GermanVisemeApproximation.visemes("Leute"))
    }

    @Test fun poseReducerGivesRenderersOneBoundedPose() {
        val p = AvatarPoseReducer()
        p.apply(AvatarEvent.StateChanged(AvatarState.THINKING, AvatarExpression.FOCUSED, GazeTarget.THINKING_AWAY, 1))
        assertEquals(GazeTarget.THINKING_AWAY.x, p.pose().gazeX)
        p.apply(AvatarEvent.SpeechStarted("u", "Hallo", 2))
        repeat(12) { p.apply(AvatarEvent.AudioLevel("u", 1f, 3L + it * 33L, AudioLevelSource.PCM_ENVELOPE)) }
        assertTrue(p.pose().jawOpen in 0.9f..1f)
        p.apply(AvatarEvent.VisemeCue("u", Viseme.O, 1f, 80, 400))
        assertEquals(Viseme.O, p.pose().viseme)
        p.apply(AvatarEvent.SpeechEnded("u", false, 450))
        assertEquals(0f, p.pose().jawOpen); assertEquals(Viseme.SIL, p.pose().viseme)
        // Small envelope noise below the dead zone never opens the mouth.
        val q = AvatarPoseReducer(); q.apply(AvatarEvent.SpeechStarted("v", "x", 1))
        repeat(10) { q.apply(AvatarEvent.AudioLevel("v", 0.02f, 2L + it * 33L, AudioLevelSource.PCM_ENVELOPE)) }
        assertEquals(0f, q.pose().jawOpen)
    }
}
