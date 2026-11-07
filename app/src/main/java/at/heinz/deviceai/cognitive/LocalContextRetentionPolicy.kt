package at.heinz.deviceai.cognitive

/** Kind of a visible reply that did NOT come from the local language model. */
enum class NonModelReplyKind { READ_ONLY_TOOL, DEVICE_CLOCK, STARTKERN_INFO, SAFETY_REFUSAL, MEMORY_CHANGE, MEMORY_RECALL }

/**
 * M2: which non-model replies keep the same-session short-term context.
 *
 * Code39 cleared the whole ephemeral dialogue after ANY non-model reply, so a
 * ring task followed by "Wie viel Speicher habe ich?" lost "blau = 7" before the
 * next question. Read-only/informational replies now keep the context and are
 * recorded as a labelled owner turn. A memory change (write/revise/forget) still
 * invalidates it, because older dialogue may contradict the new owner fact.
 * The label never contains tool output or model text.
 */
internal object LocalContextRetentionPolicy {
    fun keepsSessionContext(kind: NonModelReplyKind): Boolean = kind != NonModelReplyKind.MEMORY_CHANGE

    /** The single AgentActivity rule for a non-model reply; returns whether the context was kept. */
    fun apply(context: LocalConversationContext, sessionId: String, request: String, kind: NonModelReplyKind): Boolean {
        val keep = keepsSessionContext(kind)
        if (!keep) context.clear()
        // M3 (Review #370/1): a refused request keeps the harmless earlier context but is itself
        // never recorded, so it cannot return later as owner task data in a follow-up frame.
        else if (request.isNotBlank() && recordsRequest(kind)) context.recordNonModel(sessionId, request, turnLabel(kind))
        return keep
    }

    fun recordsRequest(kind: NonModelReplyKind): Boolean = when (kind) {
        NonModelReplyKind.READ_ONLY_TOOL, NonModelReplyKind.DEVICE_CLOCK, NonModelReplyKind.STARTKERN_INFO,
        NonModelReplyKind.MEMORY_RECALL -> true
        NonModelReplyKind.SAFETY_REFUSAL, NonModelReplyKind.MEMORY_CHANGE -> false
    }

    fun turnLabel(kind: NonModelReplyKind): String = when (kind) {
        NonModelReplyKind.READ_ONLY_TOOL -> "(lokale Geräteprüfung beantwortet; Messwerte nicht im Verlauf)"
        NonModelReplyKind.DEVICE_CLOCK -> "(Uhrzeit aus der Geräteuhr beantwortet)"
        NonModelReplyKind.STARTKERN_INFO -> "(feste Startkern-Antwort; kein Modelltext)"
        NonModelReplyKind.SAFETY_REFUSAL -> "(Sicherheitsprüfung: nichts ausgeführt)"
        NonModelReplyKind.MEMORY_CHANGE -> "(Erinnerung geändert)"
        NonModelReplyKind.MEMORY_RECALL -> "(gespeicherte Erinnerung gelesen; keine Gedächtnisänderung)"
    }
}
