package at.heinz.deviceai.learning

import java.io.File

/**
 * Connects the M2 temporal memory to the EXISTING central brain contract instead of building a second AI:
 * same owner switch ([ContinuumBrainSettings], default OFF), same result type ([ContinuumBrainResult]),
 * same canonical ledger ([ContinuumLearningCore.temporalMemoryView]) and the same fail-closed rules as
 * [ContinuumBrainBridge]: OFF/SHADOW never change the shown answer; ON only replaces it for questions inside
 * its bounded grammar; a revocation during the turn yields a neutral answer; any error keeps the baseline.
 *
 * Integration into the chat turn (AgentActivity) is a Grok-1 routing hook; see docs/research/omega/GROK2-HANDOFF.md.
 */
object ContinuumTemporalMemorySpecialist {
    const val EVIDENCE_PREFIX = "CONTINUUM · Zeitgedächtnis"

    /** Fault boundary for JVM regression tests (a revocation racing the turn), never an external callback. */
    internal var beforeRevocationCheck:(()->Unit)? = null

    fun decide(appDirectory:File, core:ContinuumLearningCore, request:String, baseline:ContinuumChatOutcome,
               working:List<WorkingTurn> = emptyList(),
               settings:ContinuumBrainSettings = ContinuumBrainSettings(appDirectory),
               nowMillis:Long = System.currentTimeMillis()):ContinuumBrainResult {
        val mode = settings.mode()
        if (mode == ContinuumBrainMode.OFF) return ContinuumBrainResult(baseline, mode, ContinuumBrainStatus.OFF)
        val question = request.trim().take(512)
        val answer = runCatching { core.temporalMemoryView(working).answer(question, nowMillis) }
        if (mode == ContinuumBrainMode.SHADOW) return ContinuumBrainResult(baseline, mode, ContinuumBrainStatus.SHADOW)
        val a = answer.getOrElse { return ContinuumBrainResult(baseline, mode, ContinuumBrainStatus.FALLBACK_ERROR) }
            ?: return ContinuumBrainResult(baseline, mode, ContinuumBrainStatus.ON_KEPT_BASELINE)
        val ledgerIds = a.citedIds.filter { it.startsWith("lx-") }
        val evaluated = when (a.kind) {
            TemporalAnswerKind.ANSWER -> {
                beforeRevocationCheck?.invoke()
                val again = runCatching { core.temporalMemoryView(working).answer(question, nowMillis) }.getOrNull()
                if (again == null || again.kind != TemporalAnswerKind.ANSWER || again.citedIds != a.citedIds)
                    ContinuumBrainResult(ContinuumChatOutcome(ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER,
                        "$EVIDENCE_PREFIX · Erinnerung während der Antwort geändert", baseline.trace, false,
                        ContinuumBaselineSource.NONE), mode, ContinuumBrainStatus.FALLBACK_REVOKED)
                else ContinuumBrainResult(ContinuumChatOutcome(a.text,
                    "$EVIDENCE_PREFIX · ${a.citedIds.size} Belege" +
                        (if (MemoryTier.WORKING in a.tiers) " · Arbeitsgedächtnis dieses Gesprächs" else ""),
                    baseline.trace, true, ContinuumBaselineSource.MEMORY, ledgerIds), mode, ContinuumBrainStatus.ON_MEMORY)
            }
            TemporalAnswerKind.CLARIFY -> ContinuumBrainResult(ContinuumChatOutcome(a.text,
                "$EVIDENCE_PREFIX · Rückfrage statt Überschreiben", baseline.trace, true,
                ContinuumBaselineSource.NONE), mode, ContinuumBrainStatus.ON_CLARIFY)
            TemporalAnswerKind.UNCONFIRMED_ONLY -> ContinuumBrainResult(ContinuumChatOutcome(a.text,
                "$EVIDENCE_PREFIX · nur unbestätigte Aussagen", baseline.trace, false,
                ContinuumBaselineSource.NONE), mode, ContinuumBrainStatus.ON_ABSTAIN)
            TemporalAnswerKind.NO_EVIDENCE ->
                // Replace only a memory-grounded baseline (it may cite a value that is no longer valid).
                if (baseline.source == ContinuumBaselineSource.MEMORY)
                    ContinuumBrainResult(ContinuumChatOutcome(a.text, "$EVIDENCE_PREFIX · kein gültiger Beleg für diesen Zeitpunkt",
                        baseline.trace, false, ContinuumBaselineSource.NONE), mode, ContinuumBrainStatus.ON_ABSTAIN)
                else ContinuumBrainResult(baseline, mode, ContinuumBrainStatus.ON_KEPT_BASELINE)
        }
        // The owner may switch the brain OFF while this turn is evaluated: never publish ON after that.
        val still = settings.mode()
        return if (evaluated.status == ContinuumBrainStatus.FALLBACK_REVOKED || still == ContinuumBrainMode.ON) evaluated
            else ContinuumBrainResult(baseline, still,
                if (still == ContinuumBrainMode.OFF) ContinuumBrainStatus.OFF else ContinuumBrainStatus.SHADOW)
    }
}
