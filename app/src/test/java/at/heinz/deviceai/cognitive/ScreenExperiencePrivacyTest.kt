package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

class ScreenExperiencePrivacyTest {
    @Test fun screenObservationAndReflectedAnswerCannotBecomePersistentLearningEvidence(){
        val store=InMemoryExperienceStore();val reputation=ModelReputationStore()
        val result=BoundedAgentOrchestrator(
            model=AgentModel{_,_,_,observations-> if(observations.isEmpty())
                AgentModelOutput("mock-model","",ToolProposal("read_app_screen",mapOf("packageOrName" to "com.android.settings")))
                else AgentModelOutput("mock-model","PRIVATE_SCREEN_RESULT")},
            memories={emptyList()},toolGate=ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner{"PRIVATE_SCREEN_RESULT"}),
            evaluator=AgentEvaluator{_,_->Evaluation(true,1.0)},experienceStore=store,
            modelReputation=reputation,evaluationConfirmsOutcome=true,
            approvalProvider=AgentToolApprovalProvider{_,_->AgentToolApproval(true)}
        ).run("Read only this screen")
        assertNull(result.blocker);assertEquals("PRIVATE_SCREEN_RESULT",result.response)
        assertTrue(store.all().isEmpty());assertNull(result.experience)
        assertEquals(0,reputation.get("mock-model",ModelCapability.TOOL_USE,"agent").taskCount)
    }
    @Test fun ordinaryVerifiedDeviceEvidenceStillUsesTheExistingExperienceStore(){
        val store=InMemoryExperienceStore()
        BoundedAgentOrchestrator(
            model=AgentModel{_,_,_,observations->if(observations.isEmpty())AgentModelOutput("model","",ToolProposal("get_device_info",emptyMap()))
                else AgentModelOutput("model","Observed device metadata")},
            memories={emptyList()},toolGate=ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner{"verified metadata"}),
            evaluator=AgentEvaluator{_,_->Evaluation(true,1.0)},experienceStore=store,evaluationConfirmsOutcome=true
        ).run("Inspect the device")
        assertEquals(1,store.all().size);assertEquals(ExperienceOutcome.SUCCESS,store.all().single().outcome)
    }
}
