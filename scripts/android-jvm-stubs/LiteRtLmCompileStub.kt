// Compile-only stand-in for com.google.ai.edge:litertlm (Google Maven is unreachable in the review sandboxes).
// Used ONLY by scripts/android-kotlin-unit-check.sh so the rest of app/src/main compiles for JUnit; never shipped,
// never on an APK classpath; the entry points that would load or run a model throw, so no test can pass through them.
package com.google.ai.edge.litertlm

enum class BackendType { CPU, GPU, NPU }
enum class Modality { TEXT, VISION, AUDIO }
sealed class Backend { class CPU(val threadCount: Int? = null) : Backend() }
sealed class Content {
    class Text(val text: String) : Content()
    class ToolResponse(val name: String, val response: Any?) : Content()
}
class Contents(val contents: List<Content>) {
    companion object {
        fun of(text: String) = Contents(listOf(Content.Text(text)))
        fun of(contents: List<Content>) = Contents(contents)
    }
}
class ToolCall(val name: String, val arguments: Map<String, Any?>)
class Message(val contents: Contents, val toolCalls: List<ToolCall> = emptyList()) {
    companion object {
        fun user(c: Contents) = Message(c)
        fun tool(c: Contents) = Message(c)
    }
}
interface MessageCallback { fun onMessage(message: Message); fun onDone(); fun onError(throwable: Throwable) }
interface OpenApiTool { fun getToolDescriptionJsonString(): String; fun execute(paramsJsonString: String): String }
class ToolProvider
fun tool(t: OpenApiTool): ToolProvider = ToolProvider()
class EngineConfig(val modelPath: String, val backend: Backend, val cacheDir: String)
class ConversationConfig(val systemInstruction: Contents, val tools: List<ToolProvider> = emptyList(),
    val automaticToolCalling: Boolean = true, val maxOutputToken: Int = 0)
class Conversation {
    val isAlive: Boolean = false
    fun getTokenCount(): Int = 0
    fun close() {}
    fun cancelProcess() {}
    fun sendMessageAsync(message: Message, callback: MessageCallback) { throw UnsupportedOperationException("LiteRT-LM stub") }
}
class Engine(val config: EngineConfig) {
    fun initialize() { throw UnsupportedOperationException("LiteRT-LM stub") }
    fun isInitialized() = false
    fun close() {}
    fun createConversation(config: ConversationConfig) = Conversation()
}
class Modalities(val text: Boolean, val vision: Boolean, val audio: Boolean, val video: Boolean)
sealed class ModelInfo : AutoCloseable {
    override fun close() {}
    class Llm : ModelInfo() {
        fun inputModalities() = Modalities(true, false, false, false)
        fun supportsFunctionCalling() = false
        fun maxContextTokens() = 0
        fun minRuntimeVersion(): String? = null
        fun supportedBackends(m: Modality) = listOf(BackendType.CPU)
    }
    companion object { fun from(path: String): ModelInfo = throw UnsupportedOperationException("LiteRT-LM stub") }
}
