package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SovereignReviewRegressionTest {
    private val hardTask="Bitte mache einen Vergleich mehrerer Konzepte mit Widerspruch und Bedingung. "+"x".repeat(700)

    @Test fun completeUngroundedContextIsNeverClippedForInternalAdvisoryPasses() {
        val prompts=mutableListOf<String>()
        val negative="Maren ist nicht in Graz."
        val input=ContextIntegrityFixture.input(hardTask+" Wo ist Maren?",
            listOf(ContextIntegrityFixture.memory("late-review", "Maren ist in Graz. "+"Zusatz. ".repeat(400)+negative)))
        val compiled=AiContextCompiler(AiContextPolicy(maxBytes=10000),{100L}).compile(input)
        assertTrue(compiled.text.contains(negative))
        val r=ChatReasoningRouter(ContinuumReasoningAdapter {
            prompts.add(it.prompt);ReasoningPassResult("fixture","hint",1)
        },{})
        assertNull(r.advise("context-review",hardTask,compiled.text))
        assertTrue("Optional advisory must skip rather than lose evidence",prompts.isEmpty())
    }

    @Test fun overBudgetTaskIsNotShortenedForAnAdvisory() {
        var calls=0
        val r=ChatReasoningRouter(ContinuumReasoningAdapter {
            calls++;ReasoningPassResult("fixture","hint",1)
        },{},ChatReasoningRouter.Config(maxTaskChars=200))
        assertNull(r.advise("task-review",hardTask+" aber sende nichts.","context"))
        assertEquals(0,calls)
    }

    @Test fun observerConsumingInterruptionCannotStartBackendAfterTimeout() {
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        val exited=CountDownLatch(1)
        val backend=CountDownLatch(1)
        val gate=ToolGate(ToolPolicy(readOnlyEnabled=true,timeoutMillis=60),ToolRunner {
            backend.countDown();"must not run after timeout"
        })
        try {
            val result=gate.execute(ToolProposal("get_storage_info",emptyMap()).invocation("late-timeout"),
                onExecutionStarted={
                    entered.countDown()
                    try {release.await(1,TimeUnit.SECONDS)} catch (_:InterruptedException) {
                        // A presentation adapter may consume interruption; this must not revive execution.
                        try {release.await(1,TimeUnit.SECONDS)} catch (_:InterruptedException) { }
                    } finally {exited.countDown()}
                })
            assertEquals(ToolExecutionStatus.TIMED_OUT,result.record.status)
            assertTrue(entered.await(1,TimeUnit.SECONDS))
        } finally {release.countDown()}
        assertTrue(exited.await(1,TimeUnit.SECONDS))
        assertFalse("Terminal timeout must prevent a later backend start",backend.await(250,TimeUnit.MILLISECONDS))
    }

    @Test fun cancellationInsideObserverCannotBeClearedToStartBackend() {
        val backend=CountDownLatch(1)
        lateinit var gate:ToolGate
        gate=ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner {backend.countDown();"must not run"})
        val result=gate.execute(ToolProposal("get_storage_info",emptyMap()).invocation("late-cancel"),
            onExecutionStarted={gate.cancel();Thread.interrupted()})
        assertEquals(ToolExecutionStatus.CANCELLED,result.record.status)
        assertFalse(backend.await(250,TimeUnit.MILLISECONDS))
    }
    @Test fun completeTaskBudgetRejectsTheWholeOversizedRequestIncludingLateNegation() {
        val exact="x".repeat(LocalChatPromptBudget.MAX_TASK_CHARACTERS)
        assertTrue(LocalChatPromptBudget.acceptsCompleteTask(exact))
        assertFalse(LocalChatPromptBudget.acceptsCompleteTask(exact+" aber sende nichts."))
        assertFalse(LocalChatPromptBudget.acceptsCompleteTask(" "))
    }

}
