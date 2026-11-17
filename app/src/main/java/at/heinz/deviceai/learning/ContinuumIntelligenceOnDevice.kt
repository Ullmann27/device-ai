package at.heinz.deviceai.learning

/**
 * Opt-in, read-only audit of the active paired SWB1/CDX1 checkpoint.
 * Does not create, train, import, export, promote or alter a checkpoint.
 * The caller holds ContinuumLearningCore's common process lock.
 */
object ContinuumIntelligenceOnDevice {
    fun run(
        core: ContinuumLearningCore,
        cancelled: () -> Boolean = { false }
    ): ContinuumIntelligenceAudit.Report {
        if (cancelled()) return ContinuumIntelligenceAudit.unavailable("INTERRUPTED")
        val before = runCatching { core.pairedDecoderStatus() }
            .getOrElse { return ContinuumIntelligenceAudit.unavailable("UNAVAILABLE_CORRUPT") }
        if (!before.available || before.sha256 == null) {
            return ContinuumIntelligenceAudit.unavailable(
                if (before.conditioningState == "CORRUPT") "UNAVAILABLE_CORRUPT"
                else "UNAVAILABLE_NO_CHECKPOINT"
            )
        }
        if (before.conditioningState == "CORRUPT" ||
            before.conditioningState == "RECOVERED_LKG") {
            return ContinuumIntelligenceAudit.unavailable("UNAVAILABLE_CORRUPT")
        }
        val predictions = ArrayList<String?>(ContinuumIntelligenceAudit.frozenProbes.size)
        for (probe in ContinuumIntelligenceAudit.frozenProbes) {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                return ContinuumIntelligenceAudit.unavailable("INTERRUPTED")
            }
            val generated = runCatching {
                core.experimentalPairedDecoderContinuation(probe.evidence, probe.question, 48)
            }.getOrNull() ?: return ContinuumIntelligenceAudit.unavailable("UNAVAILABLE_NO_INFERENCE")
            predictions.add(generated.text)
        }
        if (cancelled() || Thread.currentThread().isInterrupted) {
            return ContinuumIntelligenceAudit.unavailable("INTERRUPTED")
        }
        val after = runCatching { core.pairedDecoderStatus() }
            .getOrElse { return ContinuumIntelligenceAudit.unavailable("UNAVAILABLE_CORRUPT") }
        if (after.sha256 != before.sha256 || after.updates != before.updates ||
            after.conditioningState != before.conditioningState || !after.available) {
            return ContinuumIntelligenceAudit.unavailable("BLOCKED_MODEL_CHANGED")
        }
        return ContinuumIntelligenceAudit.score(
            predictions, before.sha256, before.updates
        )
    }
}
