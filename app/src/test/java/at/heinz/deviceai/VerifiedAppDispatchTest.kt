package at.heinz.deviceai

import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test

/** Actual registry→ToolGate→runner→DeviceToolExecutor seam, device ports are TEST DOUBLES. */
class VerifiedAppDispatchTest {
    @Test fun approvedLaunchUsesPostconditionAdapterRatherThanShellDispatchSuccess() {
        var shells=0;var actions=0
        val port=object:DeviceAppActionPort {
            override fun execute(call:ToolCallRequest,callback:(Result<String>)->Unit){
                actions++;callback(Result.success("VERIFIED_FOREGROUND:com.android.settings"))
            }
            override fun cancelPending(){}
        }
        val executor=DeviceToolExecutor(ContextWrapper(null),{true},{_,callback->
            shells++;callback(Result.success("Gestartet: com.android.settings"))},port)
        val request=ToolCallRequest("verified-launch","launch_app",mapOf("packageOrName" to "com.android.settings"))
        val result=ToolGate(ToolPolicy(),DeviceToolExecutorRunner(executor)).execute(ToolInvocation(request.callId,request),true)
        assertEquals(1,actions);assertEquals(0,shells)
        assertEquals(ToolExecutionStatus.SUCCEEDED,result.record.status)
        assertEquals("VERIFIED_FOREGROUND:com.android.settings",result.output)
    }
    @Test fun unobservedForegroundCannotBecomeAClaimOfSuccessfulLaunch() {
        val port=object:DeviceAppActionPort {
            override fun execute(call:ToolCallRequest,callback:(Result<String>)->Unit){
                callback(Result.failure(ToolBackendFailure(ToolExecutionStatus.TIMED_OUT,"POSTCONDITION_NOT_OBSERVED")))
            }
            override fun cancelPending(){}
        }
        val executor=DeviceToolExecutor(ContextWrapper(null),{true},{_,callback->
            callback(Result.success("Gestartet: com.android.settings"))},port)
        val request=ToolCallRequest("wrong-window","launch_app",mapOf("packageOrName" to "com.android.settings"))
        val result=ToolGate(ToolPolicy(),DeviceToolExecutorRunner(executor)).execute(ToolInvocation(request.callId,request),true)
        assertEquals(ToolExecutionStatus.TIMED_OUT,result.record.status);assertNull(result.output)
    }
}
