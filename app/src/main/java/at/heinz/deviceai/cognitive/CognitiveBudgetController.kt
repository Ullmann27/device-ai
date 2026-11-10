package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelComputeBudget

enum class CognitiveBudgetMode { FAST, NORMAL, DEEP, TOOL, MEMORY, RESEARCH }
enum class CognitiveBudgetReason { SELECTED, EXISTING_PATH, THERMAL_STOP, THERMAL_REDUCED, THERMAL_UNKNOWN }

data class CognitiveBudgetSignals(
    val profile: ReasoningTaskProfile = ReasoningTaskProfile(),
    val memoryRequested: Boolean = false,
    val thermalStatus: Int? = null,
    val availableProcessors: Int = 1,
    val coolingMode: LocalModelComputeBudget.CoolingMode = LocalModelComputeBudget.CoolingMode.COOL
) {
    init {
        require(thermalStatus == null || thermalStatus in 0..6)
        require(availableProcessors > 0)
    }
}

class CognitiveBudgetDecision internal constructor(
    val mode: CognitiveBudgetMode,
    val strategy: ReasoningStrategy,
    val budget: DeliberationBudget,
    val maxLocalCalls: Int,
    val inferenceThreads: Int,
    val reason: CognitiveBudgetReason
)

/** Experimental policy; grants no capabilities. */
object CognitiveBudgetController {
    fun decide(signals: CognitiveBudgetSignals, requestedMode: CognitiveBudgetMode? = null): CognitiveBudgetDecision {
        val profile = signals.profile
        val selected = ReasoningStrategySelector.select(profile)
        val mode = when {
            profile.expectedToolUse -> CognitiveBudgetMode.TOOL
            profile.factualFreshnessNeeded -> CognitiveBudgetMode.RESEARCH
            signals.memoryRequested -> CognitiveBudgetMode.MEMORY
            requestedMode != null -> requestedMode
            selected == ReasoningStrategy.TOOL_PLAN -> CognitiveBudgetMode.TOOL
            selected == ReasoningStrategy.RESEARCH_BACKED -> CognitiveBudgetMode.RESEARCH
            selected == ReasoningStrategy.FAST -> CognitiveBudgetMode.FAST
            selected == ReasoningStrategy.STANDARD || selected == ReasoningStrategy.VERIFY_ONLY -> CognitiveBudgetMode.NORMAL
            else -> CognitiveBudgetMode.DEEP
        }
        val strategy = when (mode) {
            CognitiveBudgetMode.FAST -> ReasoningStrategy.FAST
            CognitiveBudgetMode.NORMAL, CognitiveBudgetMode.MEMORY -> ReasoningStrategy.STANDARD
            CognitiveBudgetMode.DEEP -> ReasoningStrategy.DEEP
            CognitiveBudgetMode.TOOL -> ReasoningStrategy.TOOL_PLAN
            CognitiveBudgetMode.RESEARCH -> ReasoningStrategy.RESEARCH_BACKED
        }
        val requestedCalls = when (mode) {
            CognitiveBudgetMode.FAST -> 1
            CognitiveBudgetMode.NORMAL -> 2
            CognitiveBudgetMode.DEEP -> 4
            else -> 0
        }
        val reason = when {
            requestedCalls == 0 -> CognitiveBudgetReason.EXISTING_PATH
            signals.thermalStatus != null && signals.thermalStatus >= 3 -> CognitiveBudgetReason.THERMAL_STOP
            signals.thermalStatus == null -> CognitiveBudgetReason.THERMAL_UNKNOWN
            signals.thermalStatus >= LocalModelComputeBudget.MODERATE -> CognitiveBudgetReason.THERMAL_REDUCED
            else -> CognitiveBudgetReason.SELECTED
        }
        val calls = when (reason) {
            CognitiveBudgetReason.EXISTING_PATH, CognitiveBudgetReason.THERMAL_STOP -> 0
            CognitiveBudgetReason.THERMAL_UNKNOWN, CognitiveBudgetReason.THERMAL_REDUCED -> minOf(requestedCalls, 1)
            else -> requestedCalls
        }
        return CognitiveBudgetDecision(mode, strategy, DeliberationBudget.forStrategy(strategy), calls,
            LocalModelComputeBudget.inferenceThreads(signals.availableProcessors, signals.thermalStatus, signals.coolingMode), reason)
    }
}
