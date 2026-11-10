package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelComputeBudget
import org.junit.Assert.*
import org.junit.Test

class CognitiveBudgetControllerTest {
    private fun choose(profile: ReasoningTaskProfile = ReasoningTaskProfile(), memory: Boolean = false,
                       thermal: Int? = 0, requested: CognitiveBudgetMode? = null) =
        CognitiveBudgetController.decide(CognitiveBudgetSignals(profile, memory, thermal, 8), requested)

    @Test fun defaultProfileIsFast() {
        assertEquals(CognitiveBudgetMode.FAST, choose().mode)
        assertEquals(1, choose().maxLocalCalls)
    }
    @Test fun existingSelectorBoundsNormalAndDeep() {
        assertEquals(2, choose(ReasoningTaskProfile(ambiguity = .3)).maxLocalCalls)
        assertEquals(4, choose(ReasoningTaskProfile(ambiguity = .9)).maxLocalCalls)
        assertEquals(ReasoningStrategy.DEEP, choose(requested = CognitiveBudgetMode.DEEP).strategy)
    }
    @Test fun specializedRoutesCannotBeOverriddenByFast() {
        assertEquals(CognitiveBudgetMode.TOOL, choose(ReasoningTaskProfile(expectedToolUse = true), requested = CognitiveBudgetMode.FAST).mode)
        assertEquals(CognitiveBudgetMode.RESEARCH, choose(ReasoningTaskProfile(factualFreshnessNeeded = true), requested = CognitiveBudgetMode.FAST).mode)
        assertEquals(CognitiveBudgetMode.MEMORY, choose(memory = true, requested = CognitiveBudgetMode.FAST).mode)
    }
    @Test fun allSpecializedRoutesExecuteZeroModelCalls() {
        for (mode in listOf(CognitiveBudgetMode.TOOL, CognitiveBudgetMode.MEMORY, CognitiveBudgetMode.RESEARCH)) {
            val decision = choose(requested = mode)
            assertEquals(0, decision.maxLocalCalls)
            assertEquals(CognitiveBudgetReason.EXISTING_PATH, decision.reason)
        }
    }
    @Test fun highHeatStopsEveryLocalMode() {
        for (thermal in 3..6) for (mode in listOf(CognitiveBudgetMode.FAST, CognitiveBudgetMode.NORMAL, CognitiveBudgetMode.DEEP)) {
            assertEquals(0, choose(thermal = thermal, requested = mode).maxLocalCalls)
        }
    }
    @Test fun moderateAndUnknownHeatNeverAuthorizeRepeatedCalls() {
        assertEquals(1, choose(thermal = 2, requested = CognitiveBudgetMode.DEEP).maxLocalCalls)
        assertEquals(1, choose(thermal = null, requested = CognitiveBudgetMode.DEEP).maxLocalCalls)
        assertEquals(CognitiveBudgetReason.THERMAL_UNKNOWN, choose(thermal = null).reason)
    }
    @Test fun threadRecommendationReusesExistingLoadBudget() {
        val signals = CognitiveBudgetSignals(thermalStatus = 2, availableProcessors = 8,
            coolingMode = LocalModelComputeBudget.CoolingMode.BALANCED)
        assertEquals(LocalModelComputeBudget.inferenceThreads(8, 2, signals.coolingMode),
            CognitiveBudgetController.decide(signals).inferenceThreads)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInventedThermalValues() { choose(thermal = 35) }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidCoreCount() { CognitiveBudgetSignals(availableProcessors = 0) }
}
