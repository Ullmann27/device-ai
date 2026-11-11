package at.heinz.deviceai.research.voice

import at.heinz.deviceai.cognitive.ContextDirectiveKind
import at.heinz.deviceai.cognitive.LocalConversationContext

/** Calls the real existing context. Its monitor makes snapshot + inspection atomic. */
class LocalConversationVoiceAdapter(private val context: LocalConversationContext) : VoiceContextPort {
    override val mutex: Any get() = context
    override fun ownerEvidence(sessionId: String): List<String>? = context.sessionEvidence(sessionId)
    override fun inspect(sessionId: String, request: String): VoiceInspection {
        val decision = context.inspectRequest(sessionId, request)
        return when (decision?.kind) {
            ContextDirectiveKind.SCOPE_UPDATED -> VoiceInspection(VoiceResolution.SCOPE_UPDATED, decision.focus)
            ContextDirectiveKind.CLARIFY_SCOPE -> VoiceInspection(VoiceResolution.CLARIFY)
            else -> VoiceInspection(VoiceResolution.PASS_THROUGH, decision?.focus)
        }
    }
}
