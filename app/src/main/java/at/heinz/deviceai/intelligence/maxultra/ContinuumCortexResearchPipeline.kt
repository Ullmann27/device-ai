package at.heinz.deviceai.intelligence.maxultra

/**
 * Research-only orchestration. The caller explicitly supplies typed intents and
 * an owner-reviewed graph. This module cannot execute CCC1, train production weights,
 * call LM1, or overwrite the Android chat path.
 */
class ContinuumCortexResearchPipeline(
    private val planner: EvidenceBoundedThoughtSearch,
    private val fastWeights: EphemeralFastWeightCore = EphemeralFastWeightCore(),
    private val sleep: WakeSleepShadowHabitCore = WakeSleepShadowHabitCore(planner)
) {
    enum class Status { TRACEABLE_GRAPH_RESULT_NOT_FACT_CERTIFIED, ABSTAIN_NO_SAFE_PATH }
    data class Turn(
        val status: Status,
        val subject: String?,
        val result: String?,
        val edgeSourceIds: List<String>,
        val hops: Int,
        val updates: Int,
        val fastWeightNorm: Double,
        val renderedText: String
    )

    @Synchronized fun wakeTurn(
        userText: String,
        vector16: DoubleArray,
        explicitSubject: String?,
        typedQuestion: EvidenceBoundedThoughtSearch.Question?
    ): Turn {
        require(userText.isNotBlank() && userText.length <= 1200 && userText.none(Char::isISOControl))
        val focus = explicitSubject?.trim() ?: fastWeights.focusedSubjectFor(userText)
        val norm = fastWeights.observe(vector16, explicitSubject)
        val conclusion = if (focus == null || typedQuestion == null) null
                         else sleep.read(EvidenceBoundedThoughtSearch.Query(focus, typedQuestion))
        return if (conclusion == null) Turn(
            Status.ABSTAIN_NO_SAFE_PATH, focus, null, emptyList(), 0, fastWeights.updates, norm,
            "Für diese Frage ist im begrenzten, besitzermarkierten Wissensgraphen kein eindeutiger Pfad belegt.")
        else Turn(
            Status.TRACEABLE_GRAPH_RESULT_NOT_FACT_CERTIFIED, focus, conclusion.value,
            conclusion.edgeIds, conclusion.hops, fastWeights.updates, norm,
            "Im lokal markierten Wissensgraphen ist für " + focus + " der Eintrag " + conclusion.value +
                " über " + conclusion.hops + " Kante(n) ableitbar; fachliche Richtigkeit wurde nicht unabhängig bestätigt.")
    }

    @Synchronized fun stageShadowHabit(query: EvidenceBoundedThoughtSearch.Query): Boolean = sleep.stage(query)
    @Synchronized fun consolidateShadow(probes: List<WakeSleepShadowHabitCore.Probe>):
        WakeSleepShadowHabitCore.Report = sleep.consolidate(probes)
    @Synchronized fun activeHabits(): Int = sleep.habitCount()

    /** Bound to the Android New Chat action if this research pipeline is ever opted in.
     * Prevents a previous conversation's focused entity and cache leaking into a new one.
     */
    @Synchronized fun resetResearchSession() {
        fastWeights.reset()
        sleep.reset()
    }
}
