package at.heinz.deviceai.cognitive

import java.util.Locale

/** Exact local conversation and on-device read-only intents, before web or memory recall. */
/** [MODEL_IDENTITY] (Code56): „Welches lokale KI-Modell verwendest du?“ – answered from the loaded runtime model. */
internal enum class OwnerChatLocalIntent { GREETING, CAPABILITIES, STORAGE, CLOCK, MODEL_IDENTITY }

internal object OwnerChatIntentRouter {
    private val whitespace = Regex("\\s+")
    private val storageQuestion = Regex(
        "^(?:wie viel|wieviel) (?:freien? )?(?:speicher|speicherplatz) " +
            "(?:habe ich|ist (?:noch )?frei|habe ich (?:noch )?frei)$"
    )
    private val greetings = setOf("hallo", "hi", "hey", "servus", "guten morgen", "guten tag", "guten abend")
    private val capabilities = setOf("was kannst du", "was kanst du", "wos kannst du", "wos konnst du", "was kannst du alles", "hilfe", "befehle",
        // Code55 field P0: follow-up forms went to memory/web instead of the live capability list.
        "was kannst du sonst noch", "was kannst du noch", "was kannst du sonst", "was kannst du noch alles",
        "was kannst du alles noch", "was kannst du sonst noch alles", "was kannst du denn noch", "was kannst du denn sonst noch")
    /** Whole-utterance question about the local model in use; nothing else in the sentence. */
    private val modelIdentity = Regex(
        "^(?:welches|was für ein|welcher art von) (?:lokales? )?(?:ki-|ki )?(?:sprach)?modell " +
            "(?:verwendest|nutzt|benutzt|hast|verwendet|nutzest) du(?: gerade| aktuell| jetzt)?(?: lokal)?$|" +
            "^mit welchem (?:lokalen )?(?:ki-|ki )?(?:sprach)?modell (?:arbeitest|läufst|antwortest) du(?: gerade| aktuell| jetzt)?$|" +
            "^welches (?:lokale )?(?:ki-|ki )?(?:sprach)?modell (?:läuft|ist geladen|ist aktiv)(?: gerade| aktuell| jetzt)?$"
    )
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
            LocalClockIntent.isClockQuestion(message) -> OwnerChatLocalIntent.CLOCK
            modelIdentity.matches(normalized) -> OwnerChatLocalIntent.MODEL_IDENTITY
            else -> null
        }
    }
}
