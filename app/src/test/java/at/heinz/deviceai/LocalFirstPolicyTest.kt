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
    fun onlyTurnsWithoutConfirmedMemoryReachALoadedLanguageModel() {
        assertEquals(AssistantRoute.LOCAL_CORE, LocalFirstPolicy.afterMemory(grounded = true, localModelReady = true))
        assertEquals(AssistantRoute.LOCAL_MODEL, LocalFirstPolicy.afterMemory(grounded = false, localModelReady = true))
        assertEquals(AssistantRoute.LOCAL_CORE, LocalFirstPolicy.afterMemory(grounded = false, localModelReady = false))
        assertEquals(AssistantRoute.LOCAL_CORE, LocalFirstPolicy.afterMemory(grounded = true, localModelReady = false))
    }

    @Test
    fun aRetrievedFactMustNotSuppressAnExplicitSynthesisTask() {
        for (request in listOf(
            "Warum könnte Miras Wohnort für den Arbeitsweg wichtig sein?",
            "Vergleiche die beiden gespeicherten Projektpläne.",
            "Bitte leite daraus einen vorsichtigen Vorschlag ab.",
            "Analysiere den Widerspruch und begründe deine Einschätzung."
        )) {
            assertEquals(request, AssistantRoute.LOCAL_MODEL,
                LocalFirstPolicy.afterMemory(true, true, request))
            assertEquals(request, AssistantRoute.LOCAL_CORE,
                LocalFirstPolicy.afterMemory(true, false, request))
        }
    }

    @Test
    fun directRecallAndQuotedReasoningWordsKeepTheExactMemoryPath() {
        for (request in listOf("Wo wohnt Mira?", "Was habe ich über Lea gespeichert?",
            "Was ist ORION-47?", "Was bedeutet das Wort Vergleich?")) {
            assertEquals(request, AssistantRoute.LOCAL_CORE,
                LocalFirstPolicy.afterMemory(true, true, request))
        }
    }

    @Test fun retrievalLimitCannotRemoveTheActualLongModelTask() {
        val original = "Gegebener Kontext: " + "Daten ".repeat(100) +
            "\nDie eigentliche Aufgabe: berechne 19 plus 28 und antworte nur mit der Zahl."
        assertEquals(original, LocalFirstPolicy.localModelQuestion(original, original.take(512)))
    }

    @Test fun shortScopedFollowUpStillUsesItsConversationBoundResolution() {
        val scoped = "Aktuelle Frage: Und warum?\nAnschlussfrage aus diesem Gespräch: Wo wohnt Mira? Und warum?"
        assertEquals(scoped, LocalFirstPolicy.localModelQuestion("Und warum?", scoped))
        assertEquals("Hallo", LocalFirstPolicy.localModelQuestion("Hallo", "Hallo"))
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

    @Test
    fun quotedWordsAndLexicalDefinitionsDoNotTurnVerifiedRecallIntoModelClaims() {
        val directRecall = listOf(
            "Was habe ich über „analysiere“ gespeichert?",
            "Was weißt du über \"warum\" aus meiner Erinnerung?",
            "Wo steht 'plane' in meiner Notiz?",
            "Was habe ich zu `vergleiche` gespeichert?",
            "Was bedeutet das Wort analysiere?"
        )
        for (request in directRecall) {
            assertEquals(request, AssistantRoute.LOCAL_CORE,
                LocalFirstPolicy.afterMemory(grounded = true, localModelReady = true, request = request))
        }
        assertEquals(AssistantRoute.LOCAL_MODEL,
            LocalFirstPolicy.afterMemory(true, true,
                "Bitte analysiere den Widerspruch zur zitierten Frage „warum“." ))
        assertEquals(AssistantRoute.LOCAL_MODEL,
            LocalFirstPolicy.afterMemory(true, true,
                "Warum ist die gespeicherte Zahl unplausibel?"))
        assertEquals(AssistantRoute.LOCAL_MODEL,
            LocalFirstPolicy.afterMemory(true, true,
                "Was bedeutet das Wort analysiere und warum ist es wichtig?"))
    }
}
