package at.heinz.deviceai.intelligence

/**
 * Only chooses a previously benchmarked language backend. It does not load a model,
 * execute inference, promote weights or grant tools/network permissions.
 */
internal enum class ContinuumLanguageWorkload {
    DIALOGUE, GROUNDED_QUESTION, CODING, INTENT_CLASSIFICATION, SEMANTIC_COMPARISON
}

internal enum class ContinuumLanguageBackend {
    LITERT_LM, GGUF_LOCAL, OWNER_LAN, SWB1_AUXILIARY
}

internal data class ContinuumBenchmarkedCore(
    val backend: ContinuumLanguageBackend,
    val modelSha256: String,
    val ready: Boolean,
    val suiteId: String,
    val score: Double,
    val independentHoldoutPassed: Boolean,
    val ownerNetworkConsent: Boolean = false
) {
    init {
        require(modelSha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid model fingerprint" }
        require(suiteId.isNotBlank() && suiteId.length <= 120)
        require(score.isFinite() && score in 0.0..1.0)
    }
}

internal sealed interface ContinuumLanguageSelection {
    data class Selected(val candidate: ContinuumBenchmarkedCore) : ContinuumLanguageSelection
    data object NoQualifiedCore : ContinuumLanguageSelection
}

internal object ContinuumLanguageCoreSelection {
    /**
     * Scores are comparable only within the exact same independently frozen suite.
     * No untested candidate, revoked network permission or SWB1 text generator is admitted.
     * A tie prefers local inference rather than transmitting the prompt to the owner's LAN.
     */
    fun choose(
        workload: ContinuumLanguageWorkload,
        frozenSuiteId: String,
        candidates: List<ContinuumBenchmarkedCore>,
        allowOwnerLan: Boolean = false
    ): ContinuumLanguageSelection {
        require(frozenSuiteId.isNotBlank() && frozenSuiteId.length <= 120)
        val generative = workload == ContinuumLanguageWorkload.DIALOGUE ||
            workload == ContinuumLanguageWorkload.GROUNDED_QUESTION ||
            workload == ContinuumLanguageWorkload.CODING

        val eligible = candidates.filter { core ->
            core.ready && core.suiteId == frozenSuiteId && core.independentHoldoutPassed &&
                (!generative || core.backend != ContinuumLanguageBackend.SWB1_AUXILIARY) &&
                (core.backend != ContinuumLanguageBackend.OWNER_LAN ||
                    (allowOwnerLan && core.ownerNetworkConsent))
        }
        val selected = eligible.sortedWith(
            compareByDescending<ContinuumBenchmarkedCore> { it.score }
                .thenBy { if (it.backend == ContinuumLanguageBackend.OWNER_LAN) 1 else 0 }
                .thenBy { it.backend.name }
                .thenBy { it.modelSha256 }
        ).firstOrNull()
        return if (selected == null) ContinuumLanguageSelection.NoQualifiedCore
        else ContinuumLanguageSelection.Selected(selected)
    }
}
