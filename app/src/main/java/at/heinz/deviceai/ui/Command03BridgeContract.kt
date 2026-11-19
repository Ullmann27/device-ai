package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.LocalDialogueContextLimits
import at.heinz.deviceai.localmodel.LocalModelGenerationPolicy

/** Bounds for the local presentation transport; none of them grants a model or tool action. */
internal object Command03BridgeContract {
    const val MAX_SAFE_REVISION = 9_007_199_254_740_991L
    const val MAX_COMMAND_CHARACTERS = LocalDialogueContextLimits.MAX_OWNER_CHARACTERS
    const val MAX_COMMAND_BYTES = LocalDialogueContextLimits.MAX_OWNER_CHARACTERS
    const val MAX_DRAFT_CHARACTERS = 262_144
    const val MAX_DRAFT_BYTES = 262_144
    const val MAX_MESSAGE_CHARACTERS = LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS + 4_096
    private val SESSION = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val REQUEST = Regex("[A-Za-z0-9_-]{1,64}")

    fun validSession(value: String) = SESSION.matches(value)
    fun validRequest(value: String) = REQUEST.matches(value)
    fun validRevision(value: Long) = value in 0..MAX_SAFE_REVISION
    fun validBinding(value: Command03Binding) = validSession(value.pageId) && validSession(value.viewId) &&
        validSession(value.sessionId) && validRevision(value.viewRevision)

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

internal data class Command03Binding(
    val pageId: String,
    val viewId: String,
    val viewRevision: Long,
    val sessionId: String
)

internal data class Command03Command(val id: String, val text: String, val draft: Command03Draft) {
    val binding: Command03Binding get() = draft.binding
    val sessionId: String get() = binding.sessionId
}

internal data class Command03Draft(
    val binding: Command03Binding,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val clientEditRevision: Long
) {
    val sessionId: String get() = binding.sessionId
}

/** An edit acknowledgement never grants a command. Native authored text keeps the last admitted edit revision. */
internal class Command03DraftLedger {
    var binding: Command03Binding? = null; private set
    var acknowledged: Long = 0; private set

    fun bind(next: Command03Binding) {
        if (binding == next) return
        binding = next
        acknowledged = 0
    }

    fun accept(edit: Command03Draft, currentText: String, currentStart: Int, currentEnd: Int): Boolean {
        if (edit.binding != binding || !Command03BridgeContract.validBinding(edit.binding) ||
            !Command03BridgeContract.validRevision(edit.clientEditRevision) ||
            !Command03BridgeContract.validDraft(edit.text) || edit.selectionStart !in 0..edit.text.length ||
            edit.selectionEnd !in edit.selectionStart..edit.text.length || edit.clientEditRevision < acknowledged) return false
        if (edit.clientEditRevision == acknowledged && (edit.text != currentText ||
                edit.selectionStart != currentStart || edit.selectionEnd != currentEnd)) return false
        acknowledged = edit.clientEditRevision
        return true
    }
}
