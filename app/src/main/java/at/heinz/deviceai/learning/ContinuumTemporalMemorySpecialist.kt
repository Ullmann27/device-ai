package at.heinz.deviceai.learning

import java.io.File

/** WIP/UNVERIFIED stub for the RED run. */
object ContinuumTemporalMemorySpecialist {
    fun decide(appDirectory:File, core:ContinuumLearningCore, request:String, baseline:ContinuumChatOutcome,
               working:List<WorkingTurn> = emptyList(),
               settings:ContinuumBrainSettings = ContinuumBrainSettings(appDirectory),
               nowMillis:Long = System.currentTimeMillis()):ContinuumBrainResult =
        ContinuumBrainResult(baseline, settings.mode(), ContinuumBrainStatus.OFF)
}
