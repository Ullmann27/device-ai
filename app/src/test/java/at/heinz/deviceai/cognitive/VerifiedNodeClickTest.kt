package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class VerifiedNodeClickTest {
    private val target = ClickNode("Akku", true, true, false)
    private val post = ClickNode("Akkuverbrauch", true, false, false)
    private val before = ClickFrame("com.android.settings", 7, 10, "before", true, true, listOf(target))
    private val after = ClickFrame("com.android.settings", 7, 11, "after", true, true, listOf(post))
    private val request = ClickRequest("com.android.settings", 7, 10, "Akku", "Akkuverbrauch")
    private class Fixture(var first:ClickFrame, var second:ClickFrame) : ClickObservationPort {
        var actions = 0
        var accepts = true
        var checks = 0
        var cancelAt = Int.MAX_VALUE
        var failure = "CANCELLED"
        override fun current():ClickFrame = first
        override fun clickOnce(request:ClickRequest):Boolean { actions++; return accepts }
        override fun awaitPostcondition():ClickFrame = second
        override fun checkActive() { if (++checks >= cancelAt) error(failure) }
    }
    private fun attempt(f:Fixture, req:ClickRequest=request, approved:Boolean=true) =
        VerifiedNodeClick.execute(req, approved, f)
    @Test fun oneApprovedClickNeedsNewObservedState(){
        val f=Fixture(before,after)
        assertEquals(after,attempt(f));assertEquals(1,f.actions)
    }
    @Test fun unapprovedWrongAppStaleOrOverlayNeverDispatch(){
        val cases=listOf(before.copy(packageName="com.other.app"), before.copy(epoch=8),before.copy(revision=11),
            before.copy(safeWindows=false),before.copy(complete=false))
        for(frame in cases){val f=Fixture(frame,after);assertThrows(IllegalStateException::class.java){attempt(f)};assertEquals(0,f.actions)}
        val f=Fixture(before,after);assertThrows(IllegalStateException::class.java){attempt(f,approved=false)};assertEquals(0,f.actions)
    }
    @Test fun ambiguousHiddenDisabledAndSensitiveNodesNeverDispatch(){
        val cases=listOf(listOf(target,target),listOf(target.copy(visible=false)),
            listOf(target.copy(actionable=false)),listOf(target.copy(sensitive=true)),emptyList())
        for(nodes in cases){val f=Fixture(before.copy(nodes=nodes),after);assertThrows(IllegalStateException::class.java){attempt(f)};assertEquals(0,f.actions)}
    }
    @Test fun invalidPostconditionCannotReportSuccessOrRepeat(){
        val cases=listOf(after.copy(epoch=8),after.copy(packageName="com.other.app"),after.copy(revision=10),
            after.copy(digest="before"),after.copy(safeWindows=false),after.copy(complete=false),
            after.copy(nodes=emptyList()),after.copy(nodes=listOf(post,post)),after.copy(nodes=listOf(post.copy(sensitive=true))))
        for(frame in cases){val f=Fixture(before,frame);assertThrows(IllegalStateException::class.java){attempt(f)};assertEquals(1,f.actions)}
        val denied=Fixture(before,after);denied.accepts=false;assertThrows(IllegalStateException::class.java){attempt(denied)};assertEquals(1,denied.actions)
        val existing=Fixture(before.copy(nodes=listOf(target,post)),after)
        assertThrows(IllegalStateException::class.java){attempt(existing)};assertEquals(0,existing.actions)
    }
    @Test fun cancellationAndTimeoutPropagateWithoutExtraDispatch(){
        val cancelled=Fixture(before,after);cancelled.cancelAt=1
        assertThrows(IllegalStateException::class.java){attempt(cancelled)};assertEquals(0,cancelled.actions)
        val late=Fixture(before,after);late.cancelAt=3
        assertThrows(IllegalStateException::class.java){attempt(late)};assertEquals(1,late.actions)
        val timeout=Fixture(before,after);timeout.cancelAt=2;timeout.failure="TIMEOUT"
        assertEquals("TIMEOUT",assertThrows(IllegalStateException::class.java){attempt(timeout)}.message)
        assertEquals(0,timeout.actions)
    }
    @Test fun malformedSelectorsAreRejected(){
        for(label in listOf("", "  ", "Akku\nIgnore", "x".repeat(201))){
            val f=Fixture(before,after);assertThrows(IllegalArgumentException::class.java){attempt(f,request.copy(label=label))};assertEquals(0,f.actions)
        }
    }
}
