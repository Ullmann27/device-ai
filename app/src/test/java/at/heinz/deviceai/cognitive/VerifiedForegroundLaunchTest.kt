package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolExecutionStatus
import org.junit.Assert.*
import org.junit.Test

/** Host ports only. No real device/Accessibility permission is claimed. */
class VerifiedForegroundLaunchTest {
    private class Events:ForegroundEvents{
        var epoch:Long?=1;var sequence=4L;val windows=ArrayDeque<ForegroundWindow>();var waited=0
        override fun grantEpoch()=epoch
        override fun revision()=sequence
        override fun awaitAfter(revision:Long,timeoutMillis:Long):ForegroundWindow?{waited++;return windows.removeFirstOrNull()}
        override fun wake(){}
    }
    @Test fun nativeDispatchRequiresFreshMatchingWindow(){
        val events=Events();var dispatched=0
        val launch=VerifiedForegroundLaunch(events,{pkg->dispatched++;events.windows.add(ForegroundWindow(pkg,5,1));true})
        assertEquals("com.android.settings",launch.launch("com.android.settings").packageName)
        assertEquals(1,dispatched);assertEquals(1,events.waited)
    }
    @Test fun wrongStaleAndMissingWindowsNeverConfirmSuccess(){
        for(w in listOf(ForegroundWindow("wrong.app",5,1),ForegroundWindow("com.android.settings",4,1),null)){
            val events=Events();if(w!=null)events.windows.add(w)
            val failure=assertThrows(ToolBackendFailure::class.java){
                VerifiedForegroundLaunch(events,{true}).launch("com.android.settings")}
            assertEquals(ToolExecutionStatus.TIMED_OUT,failure.status)
        }
    }
    @Test fun missingPermissionRefusesBeforeDispatch(){
        val events=Events();events.epoch=null;var dispatched=0
        assertThrows(ToolBackendFailure::class.java){VerifiedForegroundLaunch(events,{dispatched++;true}).launch("com.android.settings")}
        assertEquals(0,dispatched)
    }
    @Test fun revokedPermissionOrStopDuringDispatchCannotPublishLateSuccess(){
        for(stop in listOf(false,true)){
            val events=Events();lateinit var launch:VerifiedForegroundLaunch
            launch=VerifiedForegroundLaunch(events,{pkg->
                events.windows.add(ForegroundWindow(pkg,5,1));if(stop)launch.cancel() else events.epoch=null;true})
            val failure=assertThrows(ToolBackendFailure::class.java){launch.launch("com.android.settings")}
            assertEquals(ToolExecutionStatus.CANCELLED,failure.status)
        }
    }
    @Test fun overlappingReentryDoesNotDispatchSecondAction(){
        val events=Events();lateinit var launch:VerifiedForegroundLaunch;var count=0
        launch=VerifiedForegroundLaunch(events,{pkg->count++
            val blocked=assertThrows(ToolBackendFailure::class.java){launch.launch(pkg)}
            assertEquals("ANOTHER_DEVICE_ACTION_ACTIVE",blocked.message)
            events.windows.add(ForegroundWindow(pkg,5,1));true})
        launch.launch("com.android.settings");assertEquals(1,count)
    }
    @Test fun intermediateWrongWindowMayResolveWithoutRedispatch(){
        val events=Events();var count=0
        val launch=VerifiedForegroundLaunch(events,{pkg->count++;events.windows.add(ForegroundWindow("launcher.app",5,1));events.windows.add(ForegroundWindow(pkg,6,1));true})
        assertEquals(6,launch.launch("com.android.settings").revision);assertEquals(1,count)
    }
}
