package at.heinz.deviceai.cognitive

import java.util.Locale

/** Exact local conversation and on-device read-only intents, before web or memory recall. */
internal enum class OwnerChatLocalIntent { GREETING, CAPABILITIES, STORAGE }

internal object OwnerChatIntentRouter {
    private val whitespace = Regex("\\s+")
    private val storageQuestion = Regex(
        "^(?:wie viel|wieviel) (?:freien? )?(?:speicher|speicherplatz) " +
            "(?:habe ich|ist (?:noch )?frei|habe ich (?:noch )?frei)$"
    )
    private val greetings = setOf("hallo", "hi", "hey", "servus", "guten morgen", "guten tag", "guten abend")
    private val capabilities = setOf("was kannst du", "was kanst du", "wos kannst du", "wos konnst du", "was kannst du alles", "hilfe", "befehle")
    private val storage = setOf("speicherstatus", "zeige meinen speicher", "zeige mir meinen speicher", "prüfe meinen speicher", "prüfe meinen freien speicher")

    fun route(message: String, coachActive: Boolean = false): OwnerChatLocalIntent? {
        // A running Neuron tutor owns its answer/example turn. Ordinary
        // chat shortcuts must not swallow training input or start device tools.
        if (coachActive) return null
        if (message.length !in 1..150 || message.any(Char::isISOControl)) return null
        val normalized = message.trim().lowercase(Locale.GERMAN)
            .replace(whitespace, " ").trimEnd(' ', '?', '!', '.')
        return when {
            normalized in greetings -> OwnerChatLocalIntent.GREETING
            normalized in capabilities -> OwnerChatLocalIntent.CAPABILITIES
            normalized in storage || storageQuestion.matches(normalized) -> OwnerChatLocalIntent.STORAGE
            else -> null
        }
    }
}
