package at.heinz.deviceai

/** Device adapter only; the existing ToolGate owns all user approvals and receipts. */
interface DeviceAppActionPort {
    fun execute(call: ToolCallRequest, callback: (Result<String>) -> Unit)
    fun cancelPending()
}
