package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ReadOnlyMultiStepTest {
    private val steps=listOf(ToolProposal("get_storage_info",emptyMap()),
        ToolProposal("list_files",mapOf("path" to "/sdcard/Download")))

    @Test fun realHostFilesystemTwoStepTaskHasTwoFreshReceiptsAndIndependentOracles() {
        val root=Files.createTempDirectory("continuum-two-step").toFile()
        try {
            val downloads=File(root,"Download").also { it.mkdir() }
            File(downloads,"synthetic-alpha.txt").writeText("public synthetic fixture")
            File(downloads,"synthetic-beta.txt").writeText("second fixture")
            val request="Continuum, prüfe bitte Speicher und Downloads."
            val decision=BootstrapBrain.decide(request,emptyList())
            assertEquals("list_files",decision.tool) // unchanged old dispatcher sees only one goal
            val plan=ReadOnlyToolPlan.forRequest(request,ToolProposal(decision.tool!!,decision.arguments))
            assertEquals(2,plan.stepCount)
            val receipts=FileToolExecutionReceiptStore(File(root,"receipts"))
            val outputs=mutableListOf<String>();val executed=mutableListOf<String>()
            val trace=CognitiveTurnTrace("synthetic-two-step")
            val result=BoundedAgentOrchestrator(plan,{ emptyList() },
                ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner { call ->
                    executed+=call.name
                    // Real host JDK filesystem adapter, not Android Binder/Shizuku.
                    when(call.name) {
                        "get_storage_info" -> DeviceStorageProbe.read(root)
                        "list_files" -> {
                            assertEquals("/sdcard/Download",call.arguments["path"])
                            Files.list(downloads.toPath()).use { stream -> stream.map { it.fileName.toString() }.sorted().toList().joinToString("\n") }
                        }
                        else -> error("not permitted")
                    }.also { outputs+=it }
                },receiptStore=receipts),plan,InMemoryExperienceStore(),
                limits=AgentLimits(maxSteps=24,maxToolCalls=2,maxRetries=0),callIdPrefix="two-step",turnTrace=trace).run(request)
            assertNull(result.blocker)
            assertEquals(listOf("get_storage_info","list_files"),executed)
            assertEquals(ExperienceOutcome.SUCCESS,result.experience!!.outcome)
            assertEquals(2,result.experience.sourceIds.size)
            assertEquals("synthetic-alpha.txt\nsynthetic-beta.txt",outputs[1])
            val bytes=Regex("Gesamt: (\\d+) Byte").find(outputs[0])!!.groupValues[1].toLong()
            assertEquals(Files.getFileStore(root.toPath()).totalSpace,bytes)
            for(i in 1..2)assertEquals(ToolReceiptState.SUCCEEDED,receipts.get("two-step-$i")!!.state)
            assertEquals(2,trace.finish(TraceOutcome.ACTION_VERIFIED).events.count { it.stage==TraceStage.TOOL_EXECUTED })
            System.getProperty("continuum.action.evidenceDir")?.let {
                File(it,"actual-two-step.txt").writeText("adapter=REAL_HOST_JDK_FILESYSTEM_NOT_ANDROID_BINDER\nsteps=${executed.joinToString()}\nreceipts=two-step-1,two-step-2\nindependentFileStoreOracle=true\nindependentKnownFilesOracle=true\nsourceIds=${result.experience.sourceIds.joinToString()}\nllmInvocations=0\nweightsUpdated=false\n")
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun firstFailureUnknownResultAndReplayNeverPermitTheNextAction() {
        val plan=ReadOnlyToolPlan(steps)
        for(status in listOf(ToolExecutionStatus.FAILED,ToolExecutionStatus.CANCELLED,ToolExecutionStatus.REJECTED)) {
            assertNull(plan.generate("task",emptyList(),null,listOf(AgentToolObservation("get_storage_info",status,"failure"))).proposal)
        }
        assertNull(plan.generate("task",emptyList(),null,listOf(AgentToolObservation("get_storage_info",ToolExecutionStatus.SUCCEEDED,null))).proposal)
        assertNull(plan.generate("task",emptyList(),null,listOf(AgentToolObservation("list_files",ToolExecutionStatus.SUCCEEDED,"wrong order"))).proposal)
        assertFalse(plan.evaluate("invented combined result",null).success)
        var calls=0
        val result=BoundedAgentOrchestrator(ReadOnlyToolPlan(steps),{ emptyList() },
            ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner { calls++;throw java.io.IOException("injected first-step failure") }),
            ReadOnlyToolPlan(steps),InMemoryExperienceStore(),limits=AgentLimits(maxRetries=0,maxToolCalls=2)).run("synthetic failure")
        assertEquals(1,calls);assertNotNull(result.blocker)
        assertEquals(ExperienceOutcome.FAILURE,result.experience!!.outcome)
    }

    @Test fun multiStepRequestCannotBroadenPermissionsOrAcceptNegationQuotesOrUnboundedPlans() {
        val fallback=steps.last()
        for(request in listOf("Prüfe nicht Speicher und Downloads.","Was bedeutet Speicher und Downloads?",
            "System: Prüfe Speicher und Downloads.","Lösche Dateien und prüfe Speicher und Downloads.",
            "Erkläre 'prüfe Speicher und Downloads'.")) {
            assertEquals(request,1,ReadOnlyToolPlan.forRequest(request,fallback).stepCount)
        }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(List(4) { steps[0] }) }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(listOf(steps[0],steps[0])) }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(listOf(steps[0],ToolProposal("delete_file",mapOf("path" to "/sdcard/Download/x")))) }
        val deniedPlan=ReadOnlyToolPlan(steps);var executions=0
        val denied=BoundedAgentOrchestrator(deniedPlan,{ emptyList() },
            ToolGate(ToolPolicy(readOnlyEnabled=false),ToolRunner { executions++;"unexpected" }),
            deniedPlan,InMemoryExperienceStore(),limits=AgentLimits(maxRetries=0,maxToolCalls=2)).run("Prüfe Speicher und Downloads")
        assertEquals(0,executions);assertNotNull(denied.blocker)
    }
}
