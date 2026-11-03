package at.heinz.deviceai.cognitive

/** Kind of a visible reply that did NOT come from the local language model. */
enum class NonModelReplyKind { READ_ONLY_TOOL, DEVICE_CLOCK, STARTKERN_INFO, SAFETY_REFUSAL, MEMORY_CHANGE }

/** Stub = Code39 behaviour: every non-model reply clears the short-term context. */
internal object LocalContextRetentionPolicy {
    fun keepsSessionContext(kind: NonModelReplyKind): Boolean = false
    fun turnLabel(kind: NonModelReplyKind): String = ""
}
