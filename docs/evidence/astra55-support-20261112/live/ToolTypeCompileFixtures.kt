package at.heinz.deviceai

// Compile fixtures; never executed.
enum class Risk { READ_ONLY, CONFIRM, DESTRUCTIVE }
data class ToolCallRequest(val callId: String, val name: String, val arguments: Map<String, Any?>)
enum class ToolReceiptState { APPROVED, DISPATCH_COMMITTED }
data class ToolReceiptTransition(val state: ToolReceiptState)
data class ToolExecutionReceipt(
    val toolName: String,
    val callId: String,
    val risk: Risk,
    val state: ToolReceiptState,
    val transitions: List<ToolReceiptTransition>,
    val argumentFingerprint: String
)
fun toolArgumentFingerprint(arguments: Map<String, Any?>): String =
    error("ToolGate path outside review scope: ${arguments.size}")
