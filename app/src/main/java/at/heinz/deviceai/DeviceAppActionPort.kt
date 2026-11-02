package at.heinz.deviceai

/** Device adapter only; the existing ToolGate owns all user approvals and receipts. */
interface DeviceAppActionPort {
    fun execute(call: ToolCallRequest, callback: (Result<String>) -> Unit)
    fun execute(call:ToolCallRequest,cancelled:()->Boolean,callback:(Result<String>)->Unit) {
        execute(call,callback)
    }
    fun cancelPending()
}
