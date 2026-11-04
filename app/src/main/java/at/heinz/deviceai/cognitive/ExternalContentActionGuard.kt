package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import java.util.Locale

/**
 * S4 (B5 revision): external content (web, files, screen, notifications, any tool result) is
 * data, never a command. The decision is based on PROVENANCE (taint) and ACTION RISK, not on
 * verb word lists:
 *
 * - Before any untrusted observation, or for read-only / per-request-consent tools: no
 *   intervention; the normal ToolGate + owner approval decides.
 * - Reversible local actions (move, stop/restart, other CONFIRM tools) after untrusted content:
 *   allowed to proceed to the normal owner approval (nothing runs without a yes).
 * - High-impact actions (any DESTRUCTIVE tool, install, send/post/share/upload, purchase/pay)
 *   after untrusted content: every control argument (target path, package, recipient, item ...)
 *   must be provenanced by the OWNER's own request text. A target that only appears in tool
 *   output or was invented by the model is tainted, and the proposal is refused before the
 *   owner is even asked. Payload arguments (message body/subject/content) are data and do not
 *   need owner provenance. Observation text can never create provenance.
 */
object ExternalContentActionGuard {
    const val REFUSAL = "Aktion aus externem Inhalt abgeleitet und nicht ausgeführt: Das Ziel stammt nicht aus deinem Auftrag. " +
        "Externe Inhalte sind Daten, keine Befehle. Erteile die Aktion bei Bedarf ausdrücklich als eigenen Auftrag."

    /** Tools that never need this guard: read-only, or already owner-consented per request. */
    private val OWN_CONSENT_TOOLS = setOf("web_search", "web_read", "read_app_screen", "launch_app",
        "take_screenshot", "record_screen", "open_android_settings")

    private val OUTBOUND_OR_SPEND = Regex("send|post|share|mail|message|sms|upload|publish|buy|purchase|order|pay|checkout")
    private val PAYLOAD_KEYS = setOf("text", "body", "message", "content", "subject", "caption", "note")

    fun isHighImpact(toolName: String, risk: Risk): Boolean =
        risk == Risk.DESTRUCTIVE || toolName == "install_apk" || toolName == "uninstall_app" ||
            OUTBOUND_OR_SPEND.containsMatchIn(toolName)

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace('\\', '/').trim()

    /** True when [value] (or its file/package leaf, at least 3 chars) literally appears in the owner task. */
    fun ownerProvenanced(ownerTask: String, value: String): Boolean {
        val task = norm(ownerTask)
        val v = norm(value).trimEnd('/')
        if (v.isEmpty()) return false
        if (task.contains(v)) return true
        val leaf = v.substringAfterLast('/')
        return leaf.length >= 3 && task.contains(leaf)
    }

    /** Null when allowed to proceed to the normal ToolGate/owner approval; otherwise the owner-facing refusal. */
    fun refusal(
        ownerTask: String,
        toolName: String,
        risk: Risk,
        arguments: Map<String, Any?>,
        untrustedObservationsSeen: Boolean
    ): String? {
        if (risk == Risk.READ_ONLY || !untrustedObservationsSeen || toolName in OWN_CONSENT_TOOLS) return null
        if (!isHighImpact(toolName, risk)) return null
        val controls = arguments.filterKeys { it.lowercase(Locale.ROOT) !in PAYLOAD_KEYS }
            .values.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        if (controls.isEmpty()) return REFUSAL
        return if (controls.all { ownerProvenanced(ownerTask, it) }) null else REFUSAL
    }
}
