package at.heinz.deviceai.chief

/** Parses addressing only. A name is never a tool grant or a verified capability. */
object ChiefBotAddressRouter {
    sealed interface Address {
        data class Select(val name: String) : Address
        data class Message(val name: String, val text: String) : Address
        data class Jarvis(val text: String?) : Address
    }

    fun parse(text: String): Address? {
        val value = text.trim()
        if (value.equals("JARVIS", true)) return Address.Jarvis(null)
        val selection = Regex("^(?:JARVIS[, :]\\s*)?rufe\\s+([^\\r\\n]{1,80}?)[.!]?$", RegexOption.IGNORE_CASE)
            .matchEntire(value)
        if (selection != null) {
            val name = selection.groupValues[1].trim()
            return if (name.equals("JARVIS", true)) Address.Jarvis(null) else Address.Select(name)
        }
        val addressed = Regex("^([^,\\r\\n]{1,80}),\\s*(\\S[\\s\\S]*)$").matchEntire(value) ?: return null
        val name = addressed.groupValues[1].trim()
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
