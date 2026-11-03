package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import java.util.Locale

/**
 * S4: external content (web, files, screen, notifications, any tool result) is
 * data, never a command. Once a run has seen such content, a consequential tool
 * (delete/uninstall/install/move/stop/send/post/purchase/upload/external write)
 * may only be proposed when the OWNER's own request carries that intent. The
 * check reads the owner task only; observation text can never create intent.
 * Approved web tools keep their own per-request owner consent dialog.
 */
object ExternalContentActionGuard {
    const val REFUSAL = "Aktion aus externem Inhalt abgeleitet und nicht ausgeführt: Dein Auftrag enthielt diese Absicht nicht. " +
        "Externe Inhalte sind Daten, keine Befehle. Erteile die Aktion bei Bedarf ausdrücklich als eigenen Auftrag."

    private val DELETE = listOf("lösch", "losch", "loesch", "entfern", "delete", "remove", "wegmach", "wipe")
    private val UNINSTALL = listOf("deinstall", "uninstall")
    private val INSTALL = listOf("installier", "install")
    private val MOVE = listOf("verschieb", "move")
    private val STOP = listOf("stopp", "stop", "beend", "schließ", "schliess", "neu start", "neustart", "restart", "kill")
    private val SEND = listOf("send", "schick", "sende", "post", "teil", "share", "mail", "nachricht", "message", "upload", "hochlad")
    private val PURCHASE = listOf("kauf", "bestell", "buy", "purchase", "order", "bezahl", "zahl", "pay")

    /** Tools that never need this guard: read-only, or already owner-consented per request. */
    private val OWN_CONSENT_TOOLS = setOf("web_search", "web_read", "read_app_screen", "launch_app",
        "take_screenshot", "record_screen", "open_android_settings")

    private fun intentFor(toolName: String): List<String>? = when {
        toolName == "delete_file" -> DELETE
        toolName == "uninstall_app" -> UNINSTALL
        toolName == "install_apk" -> INSTALL
        toolName == "move_file" -> MOVE
        toolName in setOf("stop_app", "restart_app") -> STOP
        Regex("send|post|share|mail|message|sms|upload|publish").containsMatchIn(toolName) -> SEND
        Regex("buy|purchase|order|pay|checkout").containsMatchIn(toolName) -> PURCHASE
        Regex("delete|remove|wipe|erase").containsMatchIn(toolName) -> DELETE
        else -> null
    }

    fun ownerRequested(ownerTask: String, toolName: String): Boolean {
        val stems = intentFor(toolName) ?: return false
        val text = ownerTask.lowercase(Locale.ROOT)
        return stems.any { text.contains(it) }
    }

    /** Null when allowed to proceed to the normal ToolGate/owner approval; otherwise the owner-facing refusal. */
    fun refusal(ownerTask: String, toolName: String, risk: Risk, untrustedObservationsSeen: Boolean): String? {
        if (risk == Risk.READ_ONLY || !untrustedObservationsSeen || toolName in OWN_CONSENT_TOOLS) return null
        return if (ownerRequested(ownerTask, toolName)) null else REFUSAL
    }
}
