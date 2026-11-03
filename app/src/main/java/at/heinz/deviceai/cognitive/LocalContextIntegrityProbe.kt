package at.heinz.deviceai.cognitive

/**
 * Content-free admission check for the existing temporary dialogue path.
 *
 * This verifies that previous-owner dialogue is in the text submitted to the
 * local-model adapter. It does NOT prove model attention or answer accuracy.
 * Never add private tokens, short-token digests, raw session IDs or model text
 * to receipts; numbers/colors can be recovered from truncated token hashes.
 */
internal object LocalContextIntegrityProbe {
    enum class Status {
        OWNER_EVIDENCE_INCOMPLETE,
        NO_PRIOR_OWNER_TURN,
        RENDERED_HISTORY_UNAVAILABLE,
        PROMPT_UNAVAILABLE,
        REQUIRED_HISTORY_NOT_IN_PROMPT,
        HISTORY_IN_PROMPT,
        NO_OPTIONAL_HISTORY,
        OPTIONAL_HISTORY_NOT_IN_PROMPT
    }

    data class Receipt(
        val status: Status,
        val requiresHistory: Boolean,
        val previousOwnerTurns: Int,
        val renderedHistoryBytes: Int,
        val promptBytes: Int
    ) {
        val requiredHistoryAdmitted: Boolean
            get() = !requiresHistory || status == Status.HISTORY_IN_PROMPT

        /** Contains only bounded counts and an enum, never conversation contents. */
        fun diagnosticLine(): String =
            "KONTEXTDIAG · ${status.name} · Vorherige Nutzernachrichten: $previousOwnerTurns · " +
                "Historie: $renderedHistoryBytes Byte · Modellprompt: $promptBytes Byte · " +
                "Modellverständnis nicht geprüft"
    }

    fun inspect(
        requiresHistory: Boolean,
        previousOwnerTurns: List<String>?,
        safeRenderedHistory: String,
        finalPrompt: String?
    ): Receipt {
        val status = when {
            previousOwnerTurns == null -> Status.OWNER_EVIDENCE_INCOMPLETE
            requiresHistory && previousOwnerTurns.isEmpty() -> Status.NO_PRIOR_OWNER_TURN
            requiresHistory && safeRenderedHistory.isBlank() -> Status.RENDERED_HISTORY_UNAVAILABLE
            finalPrompt == null -> Status.PROMPT_UNAVAILABLE
            requiresHistory && !finalPrompt.contains(safeRenderedHistory) ->
                Status.REQUIRED_HISTORY_NOT_IN_PROMPT
            safeRenderedHistory.isBlank() -> Status.NO_OPTIONAL_HISTORY
            finalPrompt.contains(safeRenderedHistory) -> Status.HISTORY_IN_PROMPT
            else -> Status.OPTIONAL_HISTORY_NOT_IN_PROMPT
        }
        return Receipt(
            status = status,
            requiresHistory = requiresHistory,
            previousOwnerTurns = previousOwnerTurns?.size ?: 0,
            renderedHistoryBytes = safeRenderedHistory.toByteArray(Charsets.UTF_8).size,
            promptBytes = finalPrompt?.toByteArray(Charsets.UTF_8)?.size ?: 0
        )
    }
}
