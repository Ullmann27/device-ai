package at.heinz.deviceai

import at.heinz.deviceai.localmodel.LocalModelState

enum class AssistantMode { AUTO, CLOUD, LOCAL }
/** LOCAL_CORE: rules and confirmed memory of the app's own learning core, no language model. */
enum class AssistantRoute { LOCAL_CORE, LOCAL_MODEL, CLOUD, MODEL_SETUP_REQUIRED }
enum class LocalModelUiState { NO_MODEL, INSTALLED, READY, LOADING, GENERATING, ERROR, OFFLINE }

object LocalFirstPolicy {
    fun route(mode: AssistantMode, localModelReady: Boolean): AssistantRoute = when (mode) {
        AssistantMode.CLOUD -> AssistantRoute.CLOUD
        AssistantMode.LOCAL -> if (localModelReady) AssistantRoute.LOCAL_MODEL else AssistantRoute.MODEL_SETUP_REQUIRED
        AssistantMode.AUTO -> if (localModelReady) AssistantRoute.LOCAL_MODEL else AssistantRoute.MODEL_SETUP_REQUIRED
    }

    /**
     * After the memory lookup of a chat turn: an answer grounded in confirmed
     * memory stays exact for direct recall. An explicit synthesis task may use
     * the local model, whose conclusions never acquire memory/fact authority.
     */
    fun afterMemory(grounded: Boolean, localModelReady: Boolean, request: String = ""): AssistantRoute = when {
        localModelReady && (!grounded || explicitlyRequestsSynthesis(request)) -> AssistantRoute.LOCAL_MODEL
        else -> AssistantRoute.LOCAL_CORE
    }

    /** The memory engine accepts only512 characters; that is not the model's task limit. */
    fun localModelQuestion(original: String, scopedRecallPrompt: String): String =
        if (original.length > 512) original else scopedRecallPrompt

    // An explicit, bounded intent rule, not a semantic-understanding claim.
    // Nouns such as "Vergleich" in a direct definition are deliberately excluded.
    private val SYNTHESIS = Regex("(?iu)\\b(warum|wieso|weshalb|begründe|begründen|vergleiche|vergleichen|" +
        "analysiere|analysieren|plane|bewerte|bewerten|schlussfolgere)\\b|\\bleite\\b.{0,80}\\bab\\b")

    // The user may ask to recall a word that coincides with a model command.
    // Quoted data cannot authorize a different answer path. This is a narrow
    // routing guard, not a semantic parser and not an authority escalation.
    private fun explicitlyRequestsSynthesis(request: String): Boolean {
        val outsideQuotes = QUOTED_DATA.replace(request, " ")
        if (SINGLE_WORD_DEFINITION.matches(outsideQuotes.trim())) return false
        return SYNTHESIS.containsMatchIn(outsideQuotes)
    }

    private val QUOTED_DATA = Regex("\"[^\"\\r\\n]*\"|„[^“\\r\\n]*“|'[^'\\r\\n]*'|`[^`\\r\\n]*`")
    private val SINGLE_WORD_DEFINITION = Regex(
        "(?iu)^was\\s+(?:bedeutet|heißt)\\s+(?:das\\s+)?(?:wort|begriff|ausdruck)\\s+[\\p{L}\\p{M}-]+\\?\\s*$"
    )

    fun modelUiState(runtimeState: LocalModelState, hasInstalledModel: Boolean, localMode: Boolean): LocalModelUiState =
        when (runtimeState) {
            LocalModelState.LOADING, LocalModelState.UNLOADING -> LocalModelUiState.LOADING
            LocalModelState.GENERATING -> LocalModelUiState.GENERATING
            LocalModelState.FAILED -> LocalModelUiState.ERROR
            LocalModelState.READY -> if (localMode) LocalModelUiState.OFFLINE else LocalModelUiState.READY
            LocalModelState.EMPTY -> if (hasInstalledModel) LocalModelUiState.INSTALLED else LocalModelUiState.NO_MODEL
        }
}
