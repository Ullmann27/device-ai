package at.heinz.deviceai

import at.heinz.deviceai.localmodel.LocalModelState

enum class AssistantMode { AUTO, CLOUD, LOCAL }
enum class AssistantRoute { LOCAL_MODEL, CLOUD, MODEL_SETUP_REQUIRED }
enum class LocalModelUiState { NO_MODEL, INSTALLED, READY, LOADING, GENERATING, ERROR, OFFLINE }

object LocalFirstPolicy {
    fun route(mode: AssistantMode, localModelReady: Boolean): AssistantRoute = when (mode) {
        AssistantMode.CLOUD -> AssistantRoute.CLOUD
        AssistantMode.LOCAL -> if (localModelReady) AssistantRoute.LOCAL_MODEL else AssistantRoute.MODEL_SETUP_REQUIRED
        AssistantMode.AUTO -> if (localModelReady) AssistantRoute.LOCAL_MODEL else AssistantRoute.MODEL_SETUP_REQUIRED
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
