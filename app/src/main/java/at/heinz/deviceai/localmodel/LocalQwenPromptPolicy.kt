package at.heinz.deviceai.localmodel

/**
 * Legacy compatibility entrypoint for direct local model prompts.
 *
 * Do not automatically append /no_think to Qwen3 input: compatible thinking
 * checkpoints can use reasoning when prompted, and non-Qwen models such as
 * Gemma must receive the original request unchanged. The user may explicitly
 * include a supported model-family switch in their input. No role delimiter
 * or privileged tool proposal is ever synthesized here.
 */
internal object LocalQwenPromptPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun directUserPrompt(modelId: String?, prompt: String, maxCharacters: Int): String {
        require(prompt.isNotBlank() && maxCharacters > 0)
        require(prompt.length <= maxCharacters)
        return prompt
    }
}
