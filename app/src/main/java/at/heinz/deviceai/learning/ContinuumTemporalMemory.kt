package at.heinz.deviceai.learning

import java.time.ZoneId

/** WIP/UNVERIFIED stub for the RED run. */
enum class MemoryTier { WORKING, LONG_TERM }
data class WorkingTurn(val text:String, val atMillis:Long)
enum class TemporalAnswerKind { ANSWER, CLARIFY, NO_EVIDENCE, UNCONFIRMED_ONLY }
data class TemporalAnswer(val kind:TemporalAnswerKind, val text:String, val citedIds:List<String>, val tiers:Set<MemoryTier>)
data class TemporalValue(val value:String, val sourceIds:List<String>, val validFromMillis:Long?, val validToMillis:Long?, val tier:MemoryTier)
sealed class TemporalResolution {
    data class Found(val values:List<TemporalValue>):TemporalResolution()
    data class Conflict(val sourceIds:List<String>, val values:List<String>):TemporalResolution()
    data class UnconfirmedOnly(val statements:List<String>):TemporalResolution()
    object NoEvidence:TemporalResolution()
}

class ContinuumTemporalMemory private constructor(val zone:ZoneId) {
    val claimCount:Int get() = 0
    fun valueAt(subject:String, predicate:String, validAtMillis:Long, knownAtMillis:Long):TemporalResolution = TemporalResolution.NoEvidence
    fun answer(question:String, nowMillis:Long):TemporalAnswer? = null

    companion object {
        val VIENNA:ZoneId = ZoneId.of("Europe/Vienna")
        fun fromLedger(records:List<LearningExperience>, working:List<WorkingTurn> = emptyList(), zone:ZoneId = VIENNA) =
            ContinuumTemporalMemory(zone)
    }
}
