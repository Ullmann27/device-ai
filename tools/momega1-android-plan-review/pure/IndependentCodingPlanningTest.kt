package at.heinz.deviceai

import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test

/** Frozen from proposed public contract, before candidate implementation/results. Fake runtime = CONTRACT_TEST. */
class IndependentCodingPlanningTest {
    private val sha = "9".repeat(40)
    private val limits = ContinuumCodingTask.Limits(2, 8192)
    private val valid = """{"schema":"continuum-coding-plan","version":1,"kind":"BUGFIX","goal":"Preserve negative values and the last clause","acceptance":["Negative values remain negative","Zero remains unchanged"]}"""
    private fun plan() = checkNotNull(ContinuumCodingPlanning.create("Fix negative distances", sha, listOf("src/distance.mjs"), limits))
    private fun reject(text: String) { assertNull(text.take(100), plan().decode(text)) }

    @Test fun validProposalRetainsOnlyOwnerSuppliedExecutionScope() {
        val d = checkNotNull(plan().decode(valid))
        assertEquals(sha, d.sourceCommit)
        assertEquals(listOf("src/distance.mjs"), d.allowedPaths)
        assertEquals(limits, d.limits)
        assertEquals(ContinuumCodingTask.Chain.NODE, d.chain)
        assertEquals(2, d.acceptance.size)
        assertNotNull(d.validatedTask())
        assertTrue(d.id.isNotBlank())
    }
    @Test fun mutableOwnerCollectionCannotChangePinnedScope() {
        val paths = mutableListOf("src/distance.mjs")
        val p = checkNotNull(ContinuumCodingPlanning.create("Fix distance", sha, paths, limits))
        paths[0] = "src/other.mjs"
        paths += "src/more.mjs"
        assertEquals(listOf("src/distance.mjs"), p.decode(valid)!!.allowedPaths)
    }
    @Test fun missingPartialUppercaseAndMalformedSourceCannotStartPlanning() {
        for (source in listOf("", "a".repeat(12), "A".repeat(40), "g".repeat(40), "a".repeat(41), sha+"\n"))
            assertNull(source, ContinuumCodingPlanning.create("Fix distance", source, listOf("src/distance.mjs"), limits))
    }
    @Test fun emptyExcessDuplicateTraversalAndControlPathsCannotStartPlanning() {
        for (paths in listOf(emptyList(), List(4) { "src/f$it.mjs" }, listOf("src/a.mjs","src/a.mjs"),
            listOf("src/../private/a.mjs"),listOf("/src/a.mjs"),listOf("src/a\nb.mjs"),
            listOf("app/src/main/java/ToolGate.kt"),listOf("src/owner-data/a.mjs"),listOf("src/a.test.mjs")))
            assertNull(paths.toString(), ContinuumCodingPlanning.create("Fix distance", sha, paths, limits))
    }
    @Test fun mixedLanguageAndInvalidBudgetsCannotStartPlanning() {
        assertNull(ContinuumCodingPlanning.create("Fix distance", sha,listOf("src/a.mjs","app/src/main/java/A.kt"),limits))
        for (bad in listOf(ContinuumCodingTask.Limits(0,8192),ContinuumCodingTask.Limits(4,8192),
            ContinuumCodingTask.Limits(Int.MAX_VALUE,8192),ContinuumCodingTask.Limits(1,2047),
            ContinuumCodingTask.Limits(1,24001),ContinuumCodingTask.Limits(1,Int.MAX_VALUE)))
            assertNull(bad.toString(),ContinuumCodingPlanning.create("Fix distance",sha,listOf("src/a.mjs"),bad))
    }
    @Test fun missingOrOversizeOwnerGoalCannotStartPlanning() {
        for (goal in listOf("", " ", "x".repeat(601), "x\u0000"))
            assertNull(ContinuumCodingPlanning.create(goal,sha,listOf("src/a.mjs"),limits))
    }
    @Test fun modelCannotAddAnyAuthorityOrScopeField() {
        for ((key,value) in listOf("id" to "\"model-id\"", "sourceCommit" to "\"${"a".repeat(40)}\"",
            "allowedPaths" to "[\"src/other.mjs\"]", "chain" to "\"android\"", "limits" to "{\"maxModelCalls\":3,\"maxContextChars\":24000}",
            "approved" to "true", "ownerConfirmed" to "true", "shell" to "\"rm -rf\"", "tool" to "\"install_apk\""))
            reject(valid.dropLast(1)+",\"$key\":$value}")
    }
    @Test fun rejectFencesProseConcatenationAndDuplicateKeys() {
        for (text in listOf("```json\n$valid\n```", "Here is the task: $valid", valid+" trailing", valid+valid,
            valid.replace("\"version\":1", "\"version\":1,\"version\":1"))) reject(text)
    }
    @Test fun rejectWrongSchemaVersionAndNumericOverflow() {
        for (text in listOf(valid.replace("continuum-coding-plan","continuum-coding-task"),
            valid.replace("\"version\":1", "\"version\":4294967297"),
            valid.replace("\"version\":1", "\"version\":1.0"),
            valid.replace("\"version\":1", "\"version\":true"))) reject(text)
    }
    @Test fun rejectUnknownKindAndWrongFieldTypes() {
        for (text in listOf(valid.replace("BUGFIX","EXECUTE"),valid.replace("\"BUGFIX\"","false"),
            valid.replace("\"goal\":\"Preserve negative values and the last clause\"","\"goal\":42"),
            valid.replace("[\"Negative values remain negative\",\"Zero remains unchanged\"]", "\"criteria\""),
            valid.replace("[\"Negative values remain negative\",\"Zero remains unchanged\"]", "[1,true]"))) reject(text)
    }
    @Test fun rejectMissingFieldsAndOversizeGoalOrCriteria() {
        reject(valid.replace("\"kind\":\"BUGFIX\",", ""))
        reject(valid.replace("Preserve negative values and the last clause", "x".repeat(601)))
        reject(valid.replace("Negative values remain negative", "x".repeat(301)))
        reject(valid.replace("[\"Negative values remain negative\",\"Zero remains unchanged\"]", "[]"))
        reject(valid.replace("[\"Negative values remain negative\",\"Zero remains unchanged\"]", (1..9).joinToString(",","[","]") { "\"criterion$it\"" }))
    }
    @Test fun rejectLoneSurrogatesControlCharactersAndOversizedWholeResponse() {
        reject(valid.replace("Negative values remain negative", "\\ud800"))
        reject(valid.replace("Negative values remain negative", "\\u0000"))
        reject(valid + " ".repeat(9000))
    }
    @Test fun maximumTaskFieldsRemainLossless() {
        val text = """{"schema":"continuum-coding-plan","version":1,"kind":"BUGFIX","goal":"${"x".repeat(599)}Z","acceptance":[${(1..8).joinToString(",") { "\"$it${"c".repeat(298)}Z\"" }}]}"""
        val p = checkNotNull(ContinuumCodingPlanning.create("Fix distance",sha,listOf("src/a.mjs","src/b.mjs","src/c.mjs"),limits))
        val d=checkNotNull(p.decode(text))
        assertEquals(600,d.goal.length);assertEquals(8,d.acceptance.size)
        assertTrue(d.acceptance.all { it.length==300 && it.endsWith("Z") })
        assertEquals(3,d.allowedPaths.size)
    }

    private class RuntimeFixture : LocalModelRuntime {
        override var state = LocalModelState.READY
        override var loadedModel: ImportedLocalModel? = ImportedLocalModel("independent-model","v1","a".repeat(64),1,
            LocalModelCapabilities(text=true,vision=false,audio=false,video=false,functionCalling=false,
                maxContextTokens=8192,minimumRuntimeVersion="1",supportedBackends=setOf(LocalModelBackend.CPU)))
        var thinking=0;var ordinary=0;var tools=0;var cancelled=0
        var emit: (LocalInferenceCallback)->Unit = {}
        override fun load(id:String,version:String,callback:(Result<ImportedLocalModel>)->Unit) { error("No loading in plan") }
        override fun unload(callback:(Result<Unit>)->Unit) { error("No unloading in planning fixture") }
        override fun stream(prompt:String,callback:LocalInferenceCallback) { ordinary++;error("Ordinary chat stream forbidden in planning") }
        override fun streamThinking(prompt:String,callback:LocalInferenceCallback) { thinking++;emit(callback) }
        override fun respondToTools(responses:List<LocalToolResponse>,callback:LocalInferenceCallback) { tools++;error("No continuation") }
        override fun cancel() { cancelled++ }
        fun complete(callback:LocalInferenceCallback,text:String) {
            callback.onEvent(LocalInferenceEvent.TextDelta(text))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(1,text.length,1,null,null,null,1)))
        }
    }
    @Test fun oneIsolatedGenerationReturnsProposalFreeOutput() {
        val r=RuntimeFixture();r.emit={r.complete(it,valid)}
        val m=plan().model(r,DeliberationCancellation())
        val result=m.generate("Fix negative distances",emptyList(),null,emptyList())
        assertNotNull(plan().decode(result.response));assertNull(result.proposal)
        assertEquals(1,r.thinking);assertEquals(0,r.ordinary);assertEquals(0,r.tools)
        assertTrue(runCatching {m.generate("Again",emptyList(),null,emptyList())}.isFailure)
        assertEquals("No second inference",1,r.thinking)
    }
    @Test fun tokenCancelledBeforeGenerationNeverInvokesRuntime() {
        val r=RuntimeFixture();val c=DeliberationCancellation();c.cancel()
        assertTrue(runCatching {plan().model(r,c).generate("task",emptyList(),null,emptyList())}.isFailure)
        assertEquals(0,r.thinking)
    }
    @Test fun cancellationAfterTextBeforeCompletionRejectsCandidate() {
        val r=RuntimeFixture();val c=DeliberationCancellation()
        r.emit={cb->c.cancel();r.complete(cb,valid)}
        assertTrue(runCatching {plan().model(r,c).generate("task",emptyList(),null,emptyList())}.isFailure)
        assertEquals(1,r.thinking)
    }
    @Test fun modelSwapDuringGenerationRejectsCandidate() {
        val r=RuntimeFixture()
        r.emit={cb->r.loadedModel=r.loadedModel!!.copy(sha256="b".repeat(64));r.complete(cb,valid)}
        assertTrue(runCatching {plan().model(r,DeliberationCancellation()).generate("task",emptyList(),null,emptyList())}.isFailure)
    }
    @Test fun toolEventRejectsEvenWhenValidJsonAlsoCompletes() {
        val r=RuntimeFixture()
        r.emit={cb->cb.onEvent(LocalInferenceEvent.ToolProposalRejected("install_apk","not permitted"));r.complete(cb,valid)}
        assertTrue(runCatching {plan().model(r,DeliberationCancellation()).generate("task",emptyList(),null,emptyList())}.isFailure)
        assertEquals(0,r.tools);assertEquals(0,r.ordinary)
    }
    @Test fun oversizedStreamCannotBeTruncatedIntoAValidCandidate() {
        val r=RuntimeFixture();r.emit={r.complete(it,valid+" ".repeat(9000))}
        assertTrue(runCatching {plan().model(r,DeliberationCancellation()).generate("task",emptyList(),null,emptyList())}.isFailure)
    }
    @Test fun failedOrCancelledRuntimeCannotProduceDraftOutput() {
        for(event in listOf(LocalInferenceEvent.Failed("fixture failure"),LocalInferenceEvent.Cancelled)) {
            val r=RuntimeFixture();r.emit={cb->cb.onEvent(LocalInferenceEvent.TextDelta(valid));cb.onEvent(event)}
            assertTrue(runCatching {plan().model(r,DeliberationCancellation()).generate("task",emptyList(),null,emptyList())}.isFailure)
        }
    }
}
