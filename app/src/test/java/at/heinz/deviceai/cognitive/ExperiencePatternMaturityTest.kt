package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperiencePatternMaturityTest {
    private fun exp(id:String, success:Boolean, score:Double=.9, tools:List<String> = listOf("get_storage_info")) =
        Experience(
            id=id,
            taskType="agent_task",
            requestSummary="Prüfe Zustand",
            sourceIds=if(success) listOf("tool-call:$id") else emptyList(),
            toolIds=tools,
            outcome=if(success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
            startedAt=1,
            completedAt=2,
            evaluation=Evaluation(success,score)
        )

    @Test fun oneSuccessStaysQuarantined() {
        val a=ExperiencePatternMaturity.assess(listOf(exp("a",true)),"agent_task",listOf("get_storage_info"))
        assertEquals(ExperiencePatternMaturity.Maturity.QUARANTINED_OBSERVATION,a.maturity)
    }

    @Test fun threeVerifiedRunsCanBecomeValidatedPattern() {
        val items=listOf(exp("a",true),exp("b",true),exp("c",true))
        val a=ExperiencePatternMaturity.assess(items,"agent_task",listOf("get_storage_info"))
        assertEquals(ExperiencePatternMaturity.Maturity.VALIDATED_PATTERN,a.maturity)
        assertEquals(3,a.verifiedSuccesses)
    }

    @Test fun repeatedFailuresWithoutVerifiedSuccessBecomeKnownTrap() {
        val items=listOf(exp("a",false,.2),exp("b",false,.3))
        val a=ExperiencePatternMaturity.assess(items,"agent_task",listOf("get_storage_info"))
        assertEquals(ExperiencePatternMaturity.Maturity.KNOWN_TRAP,a.maturity)
    }

    @Test fun sameTaskWithDifferentToolSequenceDoesNotCrossContaminate() {
        val items=listOf(
            exp("a",true,tools=listOf("get_storage_info")),
            exp("b",true,tools=listOf("get_device_info")),
            exp("c",true,tools=listOf("get_device_info"))
        )
        val storage=ExperiencePatternMaturity.assess(items,"agent_task",listOf("get_storage_info"))
        val device=ExperiencePatternMaturity.assess(items,"agent_task",listOf("get_device_info"))
        assertEquals(1,storage.observations)
        assertEquals(2,device.observations)
        assertEquals(ExperiencePatternMaturity.Maturity.QUARANTINED_OBSERVATION,device.maturity)
    }
}
