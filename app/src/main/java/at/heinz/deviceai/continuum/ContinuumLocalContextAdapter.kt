package at.heinz.deviceai.continuum

class ContinuumLocalContextAdapter(
    private val enabled: Boolean = false,
    private val compiler: AiContextCompiler = AiContextCompiler(15_500)
) {
    fun compile(fallbackPrompt: String, input: AiContextInput): String =
        compile(fallbackPrompt) { input }

    fun compile(fallbackPrompt: String, input: () -> AiContextInput): String =
        if (enabled) compiler.compile(input()).text else fallbackPrompt
}
