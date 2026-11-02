package at.heinz.deviceai

data class ToolCallRequest(
    val callId: String,
    val name: String,
    val arguments: Map<String, Any?>
)

data class ToolOutput(
    val callId: String,
    val output: String
)

data class AgentCitation(
    val title: String,
    val url: String
)

data class AgentReply(
    val responseId: String?,
    val message: String?,
    val toolCalls: List<ToolCallRequest>,
    val citations: List<AgentCitation>,
    val status: String
)
