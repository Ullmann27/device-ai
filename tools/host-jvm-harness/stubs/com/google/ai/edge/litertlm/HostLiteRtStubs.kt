package com.google.ai.edge.litertlm
/** Host harness stand-ins for the unreachable LiteRT-LM AAR (compile only). */
enum class BackendType { CPU, GPU, NPU }
enum class Modality { TEXT, VISION, AUDIO }
class InputModalities(val text: Boolean, val vision: Boolean, val audio: Boolean, val video: Boolean)
sealed class ModelInfo : AutoCloseable {
    abstract class Llm : ModelInfo() {
        abstract fun inputModalities(): InputModalities
        abstract fun supportsFunctionCalling(): Boolean
        abstract fun maxContextTokens(): Int
        abstract fun minRuntimeVersion(): String
        abstract fun supportedBackends(m: Modality): List<BackendType>
    }
    companion object { fun from(path: String): ModelInfo = throw UnsupportedOperationException("host $path") }
}
