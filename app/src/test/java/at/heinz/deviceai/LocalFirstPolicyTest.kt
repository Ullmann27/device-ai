package at.heinz.deviceai

import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFirstPolicyTest {
    @Test
    fun autoUsesReadyLocalModelAndNeverSilentlyRoutesOrdinaryChatToCloud() {
        assertEquals(AssistantRoute.LOCAL_MODEL, LocalFirstPolicy.route(AssistantMode.AUTO, true))
        assertEquals(AssistantRoute.MODEL_SETUP_REQUIRED, LocalFirstPolicy.route(AssistantMode.AUTO, false))
        assertEquals(AssistantRoute.CLOUD, LocalFirstPolicy.route(AssistantMode.CLOUD, true))
        assertEquals(AssistantRoute.MODEL_SETUP_REQUIRED, LocalFirstPolicy.route(AssistantMode.LOCAL, false))
    }

    @Test
    fun modelUiExposesNoModelAndRuntimeLifecycleStates() {
        assertEquals(
            LocalModelUiState.NO_MODEL,
            LocalFirstPolicy.modelUiState(LocalModelState.EMPTY, hasInstalledModel = false, localMode = false)
        )
        assertEquals(
            LocalModelUiState.INSTALLED,
            LocalFirstPolicy.modelUiState(LocalModelState.EMPTY, hasInstalledModel = true, localMode = false)
        )
        assertEquals(
            LocalModelUiState.READY,
            LocalFirstPolicy.modelUiState(LocalModelState.READY, hasInstalledModel = true, localMode = false)
        )
        assertEquals(
            LocalModelUiState.OFFLINE,
            LocalFirstPolicy.modelUiState(LocalModelState.READY, hasInstalledModel = true, localMode = true)
        )
        assertEquals(
            LocalModelUiState.LOADING,
            LocalFirstPolicy.modelUiState(LocalModelState.LOADING, hasInstalledModel = true, localMode = false)
        )
        assertEquals(
            LocalModelUiState.GENERATING,
            LocalFirstPolicy.modelUiState(LocalModelState.GENERATING, hasInstalledModel = true, localMode = false)
        )
        assertEquals(
            LocalModelUiState.ERROR,
            LocalFirstPolicy.modelUiState(LocalModelState.FAILED, hasInstalledModel = true, localMode = false)
        )
    }
}
