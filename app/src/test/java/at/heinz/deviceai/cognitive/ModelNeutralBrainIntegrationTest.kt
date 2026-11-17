package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Two simulated engines through the real Host/runtime/core contracts, no native model. */
class ModelNeutralBrainIntegrationTest {
    private open class TextHostAdapter: LocalModelRuntime {
        override val state=LocalModelState.READY
        override val loadedModel: ImportedLocalModel?=null
        var turns=0
        override fun load(id:String,version:String,callback:(Result<ImportedLocalModel>)->Unit)=error("HOST_SIMULATION")
        override fun unload(callback:(Result<Unit>)->Unit)=callback(Result.success(Unit))
        override fun cancel() {}
        override fun stream(prompt:String,callback:LocalInferenceCallback) {
            turns++
            if(turns==1)callback.onEvent(LocalInferenceEvent.ToolProposal(ToolProposal("get_storage_info",emptyMap())))
            else callback.onEvent(LocalInferenceEvent.TextDelta(prompt))
        }
    }
    private class StructuredHostAdapter: TextHostAdapter() {
        override fun respondToTools(responses:List<LocalToolResponse>,callback:LocalInferenceCallback) {
            val complete=LocalToolContinuation.text(responses)
            if(complete==null)callback.onEvent(LocalInferenceEvent.Failed("LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET"))
            else callback.onEvent(LocalInferenceEvent.TextDelta(complete))
        }
    }

    @Test fun bothHostAdaptersKeepTaskEvidenceAndOperatorPolicyInTheSameCore() {
        val root=Files.createTempDirectory("continuum-model-neutral-host").toFile()
        try {
            val catalog=LocalModelCatalog(root,LocalModelInspector { error("No model import in this host test") })
            for((index,factory) in listOf<()->LocalModelRuntime>({TextHostAdapter()},{StructuredHostAdapter()}).withIndex()) {
                for(enabled in listOf(true,false)) {
                    val runtime=LocalModelHost.Host(catalog,factory()).runtime
                    var executions=0
                    val store=InMemoryExperienceStore()
                    val model=AgentModel {task,_,_,observations->
                        var text="";var proposal:ToolProposal?=null
                        val callback=LocalInferenceCallback { event->when(event) {
                            is LocalInferenceEvent.TextDelta->text+=event.text
                            is LocalInferenceEvent.ToolProposal->proposal=event.proposal
                            is LocalInferenceEvent.Failed->text="Unbestätigt: ${event.message}"
                            else->Unit
                        }}
                        if(observations.isEmpty())runtime.stream(task,callback)
                        else runtime.respondToTools(observations.map {
                            LocalToolContinuation.encode(it.toolName,it.status.name,it.output)!!
                        },callback)
                        AgentModelOutput("HOST_SIMULATED_ADAPTER_$index",text,proposal)
                    }
                    val result=BoundedAgentOrchestrator(model,{emptyList()},
                        ToolGate(ToolPolicy(readOnlyEnabled=enabled),ToolRunner {
                            executions++;DeviceStorageProbe.read(root)
                        }),AgentEvaluator {text,record->
                            Evaluation(record?.status==ToolExecutionStatus.SUCCEEDED&&text.contains("Speicherbeobachtung v1"),1.0)
                        },store,callIdPrefix="same-core-$index-$enabled",
                        limits=AgentLimits(maxRetries=0)).run("Prüfe den App-Speicher. Keine zusätzlichen Rechte.")
                    assertEquals(if(enabled)1 else 0,executions)
                    val proof=result.taskEvidence!!
                    assertEquals("same-core-$index-$enabled",proof.taskId)
                    assertEquals(enabled,proof.outcomeVerified)
                    assertEquals(enabled,proof.calls.single().freshOutput)
                    assertFalse(proof.calls.single().approvalRequested)
                    if(!enabled)assertNull(proof.learningCandidateExperienceId)
                    assertTrue(proof.events.all {it.taskId==proof.taskId})
                }
            }
        } finally {root.deleteRecursively()}
    }
}
