package at.heinz.deviceai

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Actual runLocal caller, real orchestrator, injected runtime; CONTRACT_TEST, not local inference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class IndependentCodingPlanningDeliveryUiTest {
    private val output = """{"schema":"continuum-coding-plan","version":1,"kind":"BUGFIX","goal":"Preserve every negative distance","acceptance":["Negative values stay negative","Zero stays unchanged"]}"""
    private fun field(a:AgentActivity,name:String) = AgentActivity::class.java.getDeclaredField(name).apply {isAccessible=true}
    private fun get(a:AgentActivity,name:String) = field(a,name).get(a)
    private fun set(a:AgentActivity,name:String,value:Any?) {field(a,name).set(a,value)}
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup)
        (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
    private class RuntimeFixture : LocalModelRuntime {
        override var state=LocalModelState.READY
        override var loadedModel:ImportedLocalModel?=ImportedLocalModel("review-model","v1","a".repeat(64),1,
            LocalModelCapabilities(true,false,false,false,false,8192,"1",setOf(LocalModelBackend.CPU)))
        var thinking=0;var ordinary=0;var tools=0
        var emit:(LocalInferenceCallback)->Unit = {}
        override fun load(id:String,version:String,callback:(Result<ImportedLocalModel>)->Unit) {error("no loading")}
        override fun stream(prompt:String,callback:LocalInferenceCallback) {ordinary++;error("ordinary stream")}
        override fun streamThinking(prompt:String,callback:LocalInferenceCallback) {thinking++;emit(callback)}
        override fun respondToTools(responses:List<LocalToolResponse>,callback:LocalInferenceCallback) {tools++;error("tool continuation")}
        override fun cancel() {}
        fun complete(cb:LocalInferenceCallback,text:String) {
            cb.onEvent(LocalInferenceEvent.TextDelta(text))
            cb.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(1,text.length,1,null,null,null,1)))
        }
    }
    private fun withRun(mutate:(AgentActivity,RuntimeFixture)->Unit, expectDialog:Boolean, malicious:Boolean=false) {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        val r=RuntimeFixture()
        r.emit={cb->
            if(malicious)cb.onEvent(LocalInferenceEvent.ToolProposalRejected("install_apk","fixture attempt"))
            r.complete(cb,output)
        }
        set(a,"localRuntime",r);set(a,"planCard",LinearLayout(a));set(a,"foregroundLearningActive",true)
        val before=ShadowAlertDialog.getLatestAlertDialog()
        try {
            val p=checkNotNull(ContinuumCodingPlanning.create("Fix negative distances","d".repeat(40),
                listOf("src/distance.mjs"),ContinuumCodingTask.Limits(2,8192)))
            val session=get(a,"sessionId") as String
            val run=AgentActivity::class.java.declaredMethods.single {it.name=="runLocal" && it.parameterCount==5}
            run.isAccessible=true
            run.invoke(a,"Fix negative distances","Fix negative distances",null,CognitiveTurnTrace(session),p)
            // A barrier on the genuine caller's executor ensures its completion is queued, but not UI-delivered.
            (get(a,"localAgentExecutor") as ExecutorService).submit {}.get(10,TimeUnit.SECONDS)
            assertEquals("One actual isolated planner invocation",1,r.thinking)
            mutate(a,r)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val after=ShadowAlertDialog.getLatestAlertDialog()
            if(expectDialog) {
                assertNotSame("Successful current result must reach the existing draft dialog",before,after)
                assertTrue(after.isShowing)
                val fields=views(after.window!!.decorView).filterIsInstance<EditText>()
                assertEquals(8,fields.size)
                assertEquals("Preserve every negative distance",fields[0].text.toString())
                assertEquals("d".repeat(40),fields[2].text.toString())
                assertEquals("src/distance.mjs",fields[3].text.toString())
                assertTrue(fields[4].text.toString().contains("Zero stays unchanged"))
                assertEquals("2",fields[6].text.toString());assertEquals("8192",fields[7].text.toString())
            } else assertSame("Stale or failed completion must not open a proposal dialog",before,after)
            assertEquals(0,r.ordinary);assertEquals(0,r.tools)
            assertNull(shadowOf(a).nextStartedService);assertNull(shadowOf(a).nextStartedActivity)
        } finally {
            val latest=ShadowAlertDialog.getLatestAlertDialog()
            if(latest!==before) latest?.dismiss()
            for(name in listOf("localAgentExecutor","learningIoExecutor","backlogExecutor"))
                runCatching {(get(a,name) as ExecutorService).shutdownNow()}
        }
    }
    @Test fun currentCompletedPlanReachesDraftWithoutAnyDispatch() = withRun({_,_->},true)
    @Test fun cancellationAfterWorkerCompletionBeforeUiDeliveryDropsResult() = withRun({a,_->
        (get(a,"activeChatCancellation") as DeliberationCancellation).cancel()
    },false)
    @Test fun newSessionAfterWorkerCompletionBeforeUiDeliveryDropsResult() = withRun({a,_->
        set(a,"sessionId","new-independent-session")
    },false)
    @Test fun replacedTurnTokenAfterWorkerCompletionDropsResult() = withRun({a,_->
        set(a,"activeChatCancellation",DeliberationCancellation())
    },false)
    @Test fun modelWeightSwapAfterWorkerCompletionBeforeUiDeliveryDropsResult() = withRun({_,r->
        r.loadedModel=r.loadedModel!!.copy(sha256="b".repeat(64))
    },false)
    @Test fun pauseAndReturnAfterCompletionCannotReviveOldForegroundEpoch() = withRun({a,_->
        AgentActivity::class.java.getDeclaredMethod("onPause").apply {isAccessible=true}.invoke(a)
        set(a,"foregroundLearningActive",true)
    },false)
    @Test fun finishingActivityAfterWorkerCompletionDropsResult() = withRun({a,_->a.finish()},false)
    @Test fun structuredToolEventCannotOpenDraftOrDispatch() = withRun({_,_->},false,malicious=true)
}
