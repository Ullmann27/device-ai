package at.heinz.deviceai.voice

import java.util.Locale

/**
 * Spoken or typed voice controls. Only a WHOLE utterance counts (optionally with „bitte“ or the assistant's name in
 * front), so „sprich langsamer mit meiner Mutter“ or „weiter mit dem Bericht“ stay normal requests.
 * „Stopp“/„Warte“ are not here: the hands-free mode ([at.heinz.deviceai.cognitive.ContinuousConversationMode]) owns them.
 */
object VoiceCommands {
    enum class Command { RESUME, CHOOSE_VOICE, FORMER_VOICE, SLOWER, FASTER, CALMER, NORMAL_PACE }

    private val phrases: Map<String, Command> = buildMap {
        listOf("weiter", "sprich weiter", "lies weiter", "weiter vorlesen", "weitersprechen", "weiter sprechen",
            "red weiter", "rede weiter", "erzähl weiter", "weiterlesen").forEach { put(it, Command.RESUME) }
        listOf("wechsle die stimme", "stimme wechseln", "andere stimme", "wechsel die stimme", "stimme ändern",
            "ändere die stimme", "stimme auswählen", "zeig mir die stimmen").forEach { put(it, Command.CHOOSE_VOICE) }
        listOf("nimm wieder die bisherige stimme", "bisherige stimme", "zurück zur bisherigen stimme",
            "nimm die bisherige stimme", "alte stimme", "nimm wieder die alte stimme", "wieder die alte stimme")
            .forEach { put(it, Command.FORMER_VOICE) }
        listOf("sprich langsamer", "langsamer sprechen", "rede langsamer", "langsamer").forEach { put(it, Command.SLOWER) }
        listOf("sprich schneller", "schneller sprechen", "rede schneller", "schneller").forEach { put(it, Command.FASTER) }
        listOf("sprich ruhiger", "ruhiger sprechen", "rede ruhiger", "ruhiger").forEach { put(it, Command.CALMER) }
        listOf("sprich normal", "normales tempo", "normal sprechen", "sprich wieder normal").forEach { put(it, Command.NORMAL_PACE) }
    }
    private val punctuation = Regex("[\\p{Punct}„“”‚‘’«»…]")
    private val space = Regex("\\s+")
    private val courtesy = Regex("^(?:bitte |jetzt |und |okay |ok )+|(?: bitte| jetzt| mal)+$")

    fun parse(text: String, assistantName: String? = null): Command? {
        if (text.length > 80) return null
        var normalized = text.lowercase(Locale.GERMAN).replace(punctuation, " ").replace(space, " ").trim()
        assistantName?.lowercase(Locale.GERMAN)?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            if (normalized.startsWith("$name ")) normalized = normalized.removePrefix("$name ").trim()
        }
        normalized = normalized.replace(courtesy, "").trim()
        return phrases[normalized]
    }

    fun toStep(command: Command): VoiceProfiles.Step? = when (command) {
        Command.SLOWER -> VoiceProfiles.Step.SLOWER
        Command.FASTER -> VoiceProfiles.Step.FASTER
        Command.CALMER -> VoiceProfiles.Step.CALMER
        Command.NORMAL_PACE -> VoiceProfiles.Step.NORMAL
        else -> null
    }
}
