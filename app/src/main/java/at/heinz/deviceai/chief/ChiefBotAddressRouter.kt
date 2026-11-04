package at.heinz.deviceai.chief

/** Parses addressing only. A name is never a tool grant or a verified capability. */
object ChiefBotAddressRouter {
    sealed interface Address {
        data class Select(val name: String) : Address
        data class Message(val name: String, val text: String) : Address
        data class Jarvis(val text: String?) : Address
    }

    private const val JARVIS_PREFIX = "(?:JARVIS[, :]\\s*)?"
    private val selectionPatterns = listOf(
        Regex("^${JARVIS_PREFIX}rufe\\s+([^\\r\\n]{1,80}?)(?:\\s+auf)?[.!]?$", RegexOption.IGNORE_CASE),
        Regex("^${JARVIS_PREFIX}sprich\\s+mit\\s+([^\\r\\n]{1,80}?)[.!]?$", RegexOption.IGNORE_CASE),
        Regex("^${JARVIS_PREFIX}wechsle\\s+zu\\s+([^\\r\\n]{1,80}?)[.!]?$", RegexOption.IGNORE_CASE)
    )
    // Mentions take one name token. Multiword names use the colon/comma forms.
    private val mentionPattern = Regex("^@([^\\s,:]{1,80})(?::\\s+|\\s+)(\\S[\\s\\S]*)$")
    // Require a letter-led name and separated body, avoiding times, URLs and paths.
    private val colonPattern = Regex("^(\\p{L}[^,:\\r\\n]{0,79}):\\s+(\\S[\\s\\S]*)$")
    private val commaPattern = Regex("^([^,\\r\\n]{1,80}),\\s*(\\S[\\s\\S]*)$")

    fun parse(text: String): Address? {
        // Trim only the address prefix. Message content, including its final
        // restriction, indentation and trailing newline, must survive unchanged.
        val value = text.trimStart()
        val command = value.trimEnd()
        if (command.equals("JARVIS", true)) return Address.Jarvis(null)
        for (pattern in selectionPatterns) {
            val selection = pattern.matchEntire(command) ?: continue
            val name = selection.groupValues[1].trim()
            if (name.isEmpty()) return null
            return if (name.equals("JARVIS", true)) Address.Jarvis(null) else Address.Select(name)
        }
        // Match the address before examining punctuation in its body.
        val addressed = if (value.startsWith("@")) {
            mentionPattern.matchEntire(value)
        } else {
            colonPattern.matchEntire(value) ?: commaPattern.matchEntire(value)
        } ?: return null
        val name = addressed.groupValues[1].trim()
        if (name.isEmpty()) return null
        val body = addressed.groupValues[2]
        return if (name.equals("JARVIS", true)) Address.Jarvis(body) else Address.Message(name, body)
    }

    /** Resolve editable names across all pages. Duplicate display names fail closed. */
    fun resolve(store: ChiefBotProfileStore, name: String): ChiefBotProfile? {
        var after: String? = null
        var found: ChiefBotProfile? = null
        do {
            val page = store.page(after, 100)
            for (profile in page.items) {
                if (profile.displayName.equals(name, true) || profile.agentId == name) {
                    check(found == null) { "BOT_NAME_AMBIGUOUS" }
                    found = profile
                }
            }
            after = page.nextAfterId
        } while (after != null)
        return found
    }
}
