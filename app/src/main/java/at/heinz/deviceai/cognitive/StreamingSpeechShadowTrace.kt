package at.heinz.deviceai.cognitive

/** Content-free metrics for early-speech candidates. It never approves or plays audio. */
internal class StreamingSpeechShadowTrace {
    data class Snapshot(
        val candidateCount:Int,
        val firstCandidateMillis:Long?,
        val totalSpeakableChars:Int,
        val lastSequence:Int
    )

    private var count=0
    private var first:Long?=null
    private var chars=0
    private var lastSequence=0

    @Synchronized fun observe(
        candidate: ApprovedStreamingSpeechAssembler.Candidate,
        elapsedMillis: Long
    ) {
        require(elapsedMillis>=0)
        if(candidate.sequence<=lastSequence)return
        if(first==null)first=elapsedMillis
        count++
        lastSequence=candidate.sequence
        chars=(chars+candidate.speakableText.length).coerceAtMost(32_000)
    }

    @Synchronized fun snapshot():Snapshot =
        Snapshot(count,first,chars,lastSequence)

    @Synchronized fun reset(){
        count=0;first=null;chars=0;lastSequence=0
    }
}
