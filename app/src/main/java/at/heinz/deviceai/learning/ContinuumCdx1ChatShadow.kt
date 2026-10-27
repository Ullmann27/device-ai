package at.heinz.deviceai.learning

/**
 * Opt-in bridge from ONE existing SWB1/CRE1 conversation turn into the existing
 * CDX1 evidence-paired decoder. This is observation only: it cannot approve
 * model weights, write facts, invoke tools, or replace the production answer.
 *
 * The caller passes only memory hits produced and authorized by that same
 * cognitive turn. A prior chat history, unrestricted source cache or arbitrary
 * user-supplied "evidence" is never a valid authority.
 */
internal object ContinuumCdx1ChatShadow {
    private val COMMAND = Regex(
        """(?iu)^(?:Forschung CDX1|CDX1 Schatten):\s*(.+)$"""
    )

    fun questionOrNull(message: String): String? {
        if (message.length !in 18..198 || message.any { it.code < 32 || it.code == 127 }) return null
        val question = COMMAND.matchEntire(message.trim())?.groupValues?.get(1)?.trim() ?: return null
        if (question.length !in 5..160 || question.toByteArray(Charsets.UTF_8).size > 160) return null
        return question
    }

    data class Observation(
        val state: State,
        val modelHash: String? = null,
        val sourceId: String? = null,
        val generated: String? = null
    ) {
        enum class State {
            NO_AUTHORIZED_EVIDENCE,
            NO_CURRENT_CHECKPOINT,
            INCOMPATIBLE_EVIDENCE,
            NO_GENERATION,
            MODEL_CHANGED,
            SHADOW_GENERATED
        }

        fun display(): String = buildString {
            append("CDX1 · SHADOW-DENKHELFER (keine Chatfreigabe)\n")
            append("Prüfung: ").append(state.name).append('\n')
            if (sourceId != null) append("Bestätigter SWB1-Beleg-ID: ").append(sourceId.take(64)).append('\n')
            if (modelHash != null) append("CDX1-Checkpoint SHA-256: ").append(modelHash).append('\n')
            if (generated != null) {
                append("Experimentelle Ausgabe (unbestätigt):\n")
                append(generated).append('\n')
            }
            append("SWB1-Antwort, Fakten, Berechtigungen und Gewichte bleiben unverändert. ")
            append("CRE1 liefert nur die bestehende sichere Rangfolge. ")
            append("CDX1-Ausgaben werden weder an Gemma noch an Gerätewerkzeuge übergeben.")
        }
    }

    fun observe(
        question: String,
        grounded: Boolean,
        memoryIds: Set<String>,
        authorizedHits: List<ContinuumMemoryHit>,
        currentStatus: () -> ContinuumDecoderBridge.Status,
        infer: (String, String) -> String?
    ): Observation {
        // Never truncate or silently alter a real source fact to fit the lab.
        if (!grounded || memoryIds.isEmpty() ||
            authorizedHits.isEmpty() ||
            authorizedHits.map { it.id }.toSet() != memoryIds ||
            authorizedHits.map { it.id }.distinct().size != authorizedHits.size)
            return Observation(Observation.State.NO_AUTHORIZED_EVIDENCE)
        val hit = authorizedHits.firstOrNull {
            it.kind == "Bestätigte Nutzererinnerung" &&
                it.id.length in 1..64 && it.id.none { c -> c.code < 32 || c.code == 127 } &&
                it.text.isNotBlank() && '\n' !in it.text && '\r' !in it.text &&
                it.text.none { c -> c.code == 127 } &&
                it.text.toByteArray(Charsets.UTF_8).size <= 160
        } ?: return Observation(Observation.State.INCOMPATIBLE_EVIDENCE)
        if (question.toByteArray(Charsets.UTF_8).size !in 1..160 ||
            question.any { it.code < 32 || it.code == 127 })
            return Observation(Observation.State.INCOMPATIBLE_EVIDENCE)
        val before = runCatching { currentStatus() }.getOrNull()
            ?: return Observation(Observation.State.NO_CURRENT_CHECKPOINT)
        if (!before.available || before.updates <= 0 || before.sha256 == null ||
            before.conditioningState != "PINNED_CURRENT")
            return Observation(Observation.State.NO_CURRENT_CHECKPOINT)
        val result = runCatching { infer(hit.text, question) }.getOrNull()
        val after = runCatching { currentStatus() }.getOrNull()
        if (after == null || !after.available || after.sha256 != before.sha256 ||
            after.updates != before.updates || after.conditioningState != before.conditioningState)
            return Observation(Observation.State.MODEL_CHANGED)
        val generated = result?.trim()?.takeIf { it.isNotEmpty() }
            ?.take(120)?.replace(Regex("[\u0000-\u001f\u007f]"), " ")
        if (generated.isNullOrBlank())
            return Observation(Observation.State.NO_GENERATION, before.sha256, hit.id)
        return Observation(Observation.State.SHADOW_GENERATED, before.sha256, hit.id, generated)
    }
}
