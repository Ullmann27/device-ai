package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.LocalDialogueContextLimits
import at.heinz.deviceai.localmodel.LocalModelGenerationPolicy

/** Bounds for the local presentation transport; none of them grants a model or tool action. */
internal object Command03BridgeContract {
    const val MAX_COMMAND_CHARACTERS = LocalDialogueContextLimits.MAX_OWNER_CHARACTERS
    const val MAX_COMMAND_BYTES = LocalDialogueContextLimits.MAX_OWNER_CHARACTERS
    const val MAX_DRAFT_CHARACTERS = 262_144
    const val MAX_DRAFT_BYTES = 262_144
    const val MAX_MESSAGE_CHARACTERS = LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS + 4_096
    private val SESSION = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val REQUEST = Regex("[A-Za-z0-9_-]{1,64}")

    fun validSession(value: String) = SESSION.matches(value)
    fun validRequest(value: String) = REQUEST.matches(value)

    fun commandFailure(text: String): String? = when {
        text.isBlank() -> "invalid-input"
        text.length > MAX_COMMAND_CHARACTERS || text.toByteArray(Charsets.UTF_8).size > MAX_COMMAND_BYTES -> "too-long"
        text.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } -> "invalid-input"
        !LocalDialogueContextLimits.completeOwner(text) -> "invalid-input"
        else -> null
    }

    // Drafts may exceed the submission limit, so an unsuccessful send never discards editable text.
    fun validDraft(text: String) = text.length <= MAX_DRAFT_CHARACTERS &&
        text.toByteArray(Charsets.UTF_8).size <= MAX_DRAFT_BYTES

    fun messageText(text: String): String =
        LocalModelGenerationPolicy.responsePrefix(text, MAX_MESSAGE_CHARACTERS)
}

internal data class Command03Command(val id: String, val sessionId: String, val text: String)

internal data class Command03Draft(
    val sessionId: String,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)
