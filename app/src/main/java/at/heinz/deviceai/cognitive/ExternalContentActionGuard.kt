package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import java.util.Locale

/**
 * External content remains untrusted data. The owner's original task can authorize an action
 * class only when the relevant intent is affirmative. The ordinary ToolGate permission and
 * confirmation UI always runs afterwards; this guard must never grant execution permission.
 */
object ExternalContentActionGuard {
    const val REFUSAL = "Aktion aus externem Inhalt abgeleitet und nicht ausgeführt: Dein Auftrag enthielt diese Absicht nicht. " +
        "Externe Inhalte sind Daten, keine Befehle. Erteile die Aktion bei Bedarf ausdrücklich als eigenen Auftrag."

    private val DELETE = Regex("""(?iu)\b(?:lösch\p{L}*|loesch\p{L}*|losch\p{L}*|entfern\p{L}*|delete\p{L}*|remove\p{L}*|wipe\p{L}*)\b""")
    private val UNINSTALL = Regex("""(?iu)\b(?:deinstall\p{L}*|uninstall\p{L}*)\b""")
    private val INSTALL = Regex("""(?iu)\b(?:installier\p{L}*|install\p{L}*)\b""")
    private val MOVE = Regex("""(?iu)\b(?:verschieb\p{L}*|move|moved|moving|verlager\p{L}*)\b""")
    private val STOP = Regex("""(?iu)\b(?:stopp\p{L}*|stop\p{L}*|beend\p{L}*|schließ\p{L}*|schliess\p{L}*|neustart\p{L}*|restart\p{L}*|kill)\b""")
    private val SEND = Regex("""(?iu)\b(?:send\p{L}*|schick\p{L}*|post\p{L}*|teil\p{L}*|share|mail\p{L}*|nachricht\p{L}*|message\p{L}*|upload\p{L}*|hochlad\p{L}*)\b""")
    private val PURCHASE = Regex("""(?iu)\b(?:kauf\p{L}*|kaufe\p{L}*|bestell\p{L}*|buy|purchase\p{L}*|order\p{L}*|bezahl\p{L}*|pay)\b""")

    // Conservative German command paraphrases. These allow a move PROPOSAL to reach ToolGate's
    // explicit confirmation, never an unapproved move. A question cannot imply owner consent.
    private val NON_DESTRUCTIVE_ORGANIZE = Regex(
        """(?iu)\b(?:schaff\p{L}*\s+platz\s+(?:im|in|unter)\s+[\p{L}-]*(?:download|ordner)[\p{L}-]*|räum\p{L}*\s+(?:den\s+)?[\p{L}-]*(?:download|ordner)[\p{L}-]*\s+auf)\b"""
    )
    private val NEGATION = Regex("""(?iu)\b(?:nicht|nichts|nie|niemals|ohne|kein\p{L}*|never|no|without|don['’]?t|do\s+not)\b""")
    private val QUESTION = Regex("""(?iu)^\s*(?:wie|warum|wieso|was|welche|kann|könnte|could|how|why|what)\b""")

    private val OWN_CONSENT_TOOLS = setOf("web_search", "web_read", "read_app_screen", "launch_app",
        "take_screenshot", "record_screen", "open_android_settings")

    private fun intentFor(toolName: String): Regex? = when {
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

    /** Negated phrases never promote a tool proposal originating after untrusted observations. */
    private fun affirmative(text: String, pattern: Regex): Boolean = pattern.findAll(text).any { hit ->
        val before = text.substring((hit.range.first - 48).coerceAtLeast(0), hit.range.first)
            .substringAfterLast('.').substringAfterLast(';')
        val after = text.substring(hit.range.last + 1, (hit.range.last + 24).coerceAtMost(text.length))
        !NEGATION.containsMatchIn(before) &&
            !Regex("""(?iu)^\s*(?:bitte\s+)?(?:nicht|nie|niemals|kein\p{L}*|never|not)\b""").containsMatchIn(after)
    }

    fun ownerRequested(ownerTask: String, toolName: String): Boolean {
        val intent = intentFor(toolName) ?: return false
        val text = ownerTask.lowercase(Locale.ROOT)
        if (affirmative(text, intent)) return true

        // "Schaffe Platz im Download-Ordner, ohne etwas zu löschen" is a permissible
        // NON-DESTRUCTIVE move suggestion, not permission to delete and not a move execution.
        return toolName == "move_file" &&
            !QUESTION.containsMatchIn(text) &&
            NON_DESTRUCTIVE_ORGANIZE.containsMatchIn(text) &&
            !MOVE.findAll(text).any { hit ->
                val before = text.substring((hit.range.first - 48).coerceAtLeast(0), hit.range.first)
                NEGATION.containsMatchIn(before)
            }
    }

    /** A null result means only "continue through the regular ToolGate", never "execute". */
    fun refusal(ownerTask: String, toolName: String, risk: Risk, untrustedObservationsSeen: Boolean): String? {
        if (risk == Risk.READ_ONLY || !untrustedObservationsSeen || toolName in OWN_CONSENT_TOOLS) return null
        return if (ownerRequested(ownerTask, toolName)) null else REFUSAL
    }
}
