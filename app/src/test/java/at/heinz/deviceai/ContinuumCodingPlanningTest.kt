package at.heinz.deviceai

import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

/** Reflection keeps the tests executable on the unchanged baseline: missing feature is an assertion. */
class ContinuumCodingPlanningTest {
    private val goal = "Bewahre negative Ergebnisse"
    private val commit = "a".repeat(40)
    private val limits = ContinuumCodingTask.Limits(1, 8192)
    private val json = """{"schema":"continuum-coding-plan","version":1,"kind":"BUGFIX","goal":"Bewahre negative Ergebnisse","acceptance":["Negative Werte bleiben negativ"]}"""
    private fun type(): Class<*> {
        val found = runCatching { Class.forName("at.heinz.deviceai.ContinuumCodingPlanning") }.getOrNull()
        assertNotNull("Explicit owner-pinned model planning is missing", found)
        return found!!
    }
    private fun planning(source: String = commit, paths: List<String> = listOf("lib/distance.mjs"),
                         budget: ContinuumCodingTask.Limits = limits): Any? {
        val companion = type().getField("Companion").get(null)
        return companion.javaClass.getDeclaredMethod("create", String::class.java, String::class.java,
            List::class.java, ContinuumCodingTask.Limits::class.java).invoke(companion, goal, source, paths, budget)
    }
    private fun decode(plan: Any, value: String): ContinuumCodingTask.Draft? =
        type().getDeclaredMethod("decode", String::class.java).invoke(plan, value) as ContinuumCodingTask.Draft?
    private fun model(plan: Any, rt: FakeRuntime, cancel: DeliberationCancellation = DeliberationCancellation()): CancellableAgentModel =
        type().getDeclaredMethod("model", LocalModelRuntime::class.java, DeliberationCancellation::class.java)
            .invoke(plan, rt, cancel) as CancellableAgentModel

    @Test fun rejectsIncompleteOrInvalidOwnerMetadataBeforeInference() {
        for (source in listOf("", "a".repeat(12), "A".repeat(40))) assertNull(planning(source))
        for (paths in listOf(emptyList(), listOf("../x.mjs"), listOf("lib/a.mjs", "lib/a.mjs"),
                listOf("lib/a.mjs", "app/src/main/java/a.kt"))) assertNull(planning(paths = paths))
        assertNull(planning(budget = ContinuumCodingTask.Limits(4,8192)))
    }
    @Test fun validCandidateBindsImmutableOwnerScopeAndAppIdentity() {
        val paths = mutableListOf("lib/distance.mjs")
        val p = planning(paths = paths)!!
        paths[0] = "lib/expanded.mjs"
        val draft = decode(p, json)!!
        assertEquals(commit, draft.sourceCommit)
        assertEquals(listOf("lib/distance.mjs"), draft.allowedPaths)
        assertEquals(limits, draft.limits)
        assertEquals(ContinuumCodingTask.Chain.NODE, draft.chain)
        assertTrue(draft.id.startsWith("job-"))
        assertNotEquals(draft.id, decode(planning()!!, json)!!.id)
        assertNotNull(draft.validatedTask())
    }
    @Test fun candidateCannotSupplyAuthoritySourceScopeOrBudgets() {
        val p = planning()!!
        for (field in listOf("id", "sourceCommit", "allowedPaths", "chain", "limits", "approved", "command"))
            assertNull("Unexpected field $field", decode(p, json.dropLast(1) + ",\"$field\":\"attacker\"}"))
    }
    @Test fun rejectsProseDuplicatesTrailingDataWrongTypesAndOverflow() {
        val p = planning()!!
        for (bad in listOf("```json\n$json\n```", "$json trailing", "$json{}", "[]", "{}",
                json.replace("\"version\":1", "\"version\":1,\"version\":1"),
                json.replace("\"version\":1", "\"version\":4294967297"),
                json.replace("\"BUGFIX\"", "\"SHELL\""),
                json.replace("[\"Negative Werte bleiben negativ\"]", "[2]"),
                json.replace("Bewahre negative Ergebnisse", "x".repeat(8001))))
            assertNull(bad.take(80), decode(p, bad))
    }
    @Test fun oneModelInvocationUsesOnlySameRuntimeThinkingAndReturnsNoProposal() {
        val p = planning()!!
        val rt = FakeRuntime { cb -> cb.onEvent(LocalInferenceEvent.TextDelta(json)); cb.onEvent(done()) }
        val out = model(p,rt).generate(goal, emptyList(), null, emptyList())
        assertEquals(1,rt.thinking)
        assertEquals(0,rt.normal)
        assertNull(out.proposal)
        assertEquals(commit,decode(p,out.response)!!.sourceCommit)
        assertTrue(rt.prompt.contains(commit) && rt.prompt.contains("lib/distance.mjs"))
    }
    @Test fun modelOutputOverflowAndToolEventsFailClosed() {
        for (event in listOf(LocalInferenceEvent.TextDelta("x".repeat(8001)),
                LocalInferenceEvent.ToolProposal(ToolProposal("delete_file",mapOf("path" to "/x"))))) {
            val rt = FakeRuntime { cb -> cb.onEvent(event); cb.onEvent(done()) }
            val failure = runCatching { model(planning()!!,rt).generate(goal,emptyList(),null,emptyList()) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(rt.cancelled)
            assertEquals(0,rt.normal)
        }
    }
    @Test fun cancellationAfterNativeCompletionCannotReturnDraft() {
        val cancel = DeliberationCancellation()
        val rt = FakeRuntime { cb -> cb.onEvent(LocalInferenceEvent.TextDelta(json)); cb.onEvent(done()); cancel.cancel() }
        val failure = runCatching { model(planning()!!,rt,cancel).generate(goal,emptyList(),null,emptyList()) }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.CancellationException)
    }
    @Test fun modelSwapDuringGenerationCannotReturnDraft() {
        lateinit var rt: FakeRuntime
        rt = FakeRuntime { cb -> cb.onEvent(LocalInferenceEvent.TextDelta(json)); rt.current = imported("b"); cb.onEvent(done()) }
        assertNotNull(runCatching { model(planning()!!,rt).generate(goal,emptyList(),null,emptyList()) }.exceptionOrNull())
    }
    private fun done() = LocalInferenceEvent.Completed(LocalInferenceMetrics(0,0,1,null,null,null,0))
    private fun imported(hash: String) = ImportedLocalModel("model","v1",hash.repeat(64),1,
        LocalModelCapabilities(true,false,false,false,false,8192,null,setOf(LocalModelBackend.CPU)))
    private inner class FakeRuntime(val behavior: (LocalInferenceCallback)->Unit): LocalModelRuntime {
        var current = imported("a")
        override val loadedModel get() = current
        override val state = LocalModelState.READY
        var thinking=0; var normal=0; var cancelled=false; var prompt=""
        override fun load(id:String,version:String,callback:(Result<ImportedLocalModel>)->Unit)=error("unused")
        override fun unload(callback:(Result<Unit>)->Unit)=callback(Result.success(Unit))
        override fun stream(prompt:String,callback:LocalInferenceCallback) { normal++; error("No ordinary tools-enabled generation") }
        override fun streamThinking(prompt:String,callback:LocalInferenceCallback) { thinking++; this.prompt=prompt; behavior(callback) }
        override fun cancel() { cancelled=true }
    }
}
