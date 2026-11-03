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

