package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingSpeechShadowTraceTest {
    private fun candidate(seq:Int,text:String)=ApprovedStreamingSpeechAssembler.Candidate(seq,text,text)

    @Test fun firstCandidateTimeIsStableAndSequencesCannotDoubleCount() {
        val trace=StreamingSpeechShadowTrace()
        trace.observe(candidate(1,"Hallo."),420)
        trace.observe(candidate(1,"Hallo."),430)
        trace.observe(candidate(2,"Weiter."),700)
        val s=trace.snapshot()
        assertEquals(2,s.candidateCount)
        assertEquals(420L,s.firstCandidateMillis)
        assertEquals("Hallo.".length+"Weiter.".length,s.totalSpeakableChars)
        assertEquals(2,s.lastSequence)
    }

    @Test fun emptyTraceHasNoInventedTimingAndResetClearsEverything() {
        val trace=StreamingSpeechShadowTrace()
        assertEquals(0,trace.snapshot().candidateCount)
        assertNull(trace.snapshot().firstCandidateMillis)
        trace.observe(candidate(1,"Satz."),100)
        trace.reset()
        assertEquals(0,trace.snapshot().candidateCount)
        assertNull(trace.snapshot().firstCandidateMillis)
    }
}
