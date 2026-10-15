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
     * memory or a reviewed source stays the deterministic answer; only a turn
     * that nothing covers goes to a loaded local language model.
     */
    fun afterMemory(grounded: Boolean, localModelReady: Boolean): AssistantRoute = when {
        grounded -> AssistantRoute.LOCAL_CORE
        localModelReady -> AssistantRoute.LOCAL_MODEL
        else -> AssistantRoute.LOCAL_CORE
    }

    fun modelUiState(runtimeState: LocalModelState, hasInstalledModel: Boolean, localMode: Boolean): LocalModelUiState =
        when (runtimeState) {
            LocalModelState.LOADING, LocalModelState.UNLOADING -> LocalModelUiState.LOADING
            LocalModelState.GENERATING -> LocalModelUiState.GENERATING
            LocalModelState.FAILED -> LocalModelUiState.ERROR
            LocalModelState.READY -> if (localMode) LocalModelUiState.OFFLINE else LocalModelUiState.READY
            LocalModelState.EMPTY -> if (hasInstalledModel) LocalModelUiState.INSTALLED else LocalModelUiState.NO_MODEL
        }
}
