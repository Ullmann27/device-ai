package at.heinz.deviceai.cognitive

/**
 * Chooses the installed, offline Android TTS voice JARVIS speaks with. Pure (no Android types) so the rule is
 * host-testable; the Activity maps `android.speech.tts.Voice` to [Candidate].
 *
 * Rule, in order: the owner's explicitly chosen voice (by engine voice name) if it is still installed and offline;
 * otherwise, among offline + installed voices of the selected language: the Austrian variant (`de-AT`) before
 * `de-DE`/`de-CH`/others, higher engine quality, lower engine latency, then a stable name order so the choice does not
 * change between app starts when the engine lists voices in a different order.
 *
 * Android exposes no gender for a voice, so "female" cannot be selected automatically; the owner picks it once from
 * the list and the name is remembered. Nothing here claims a voice sounds natural – that is heard on the device.
 */
internal object JarvisVoiceSelection {

    enum class Language(val tag: String, val label: String) { GERMAN("de", "Deutsch"), ENGLISH("en", "English");
        companion object { fun fromTag(tag: String?): Language = entries.firstOrNull { it.tag == tag } ?: GERMAN }
    }

    data class Candidate(
        val name: String,
        val language: String,
        val country: String,
        val quality: Int,
        val latency: Int,
        val networkRequired: Boolean,
        val notInstalled: Boolean,
    ) {
        val offlineAndInstalled: Boolean get() = !networkRequired && !notInstalled
    }

    enum class Origin { OWNER_CHOICE, AUTOMATIC }

    data class Choice(val voice: Candidate, val origin: Origin, val ownerChoiceDropped: Boolean)

    /** Voices the owner may pick from for [language]: offline + installed only, best first. */
    fun selectable(candidates: Collection<Candidate>, language: Language): List<Candidate> =
        candidates.filter { it.offlineAndInstalled && it.language.equals(language.tag, ignoreCase = true) }
            .sortedWith(ranking(language))

    fun choose(candidates: Collection<Candidate>, language: Language, ownerVoiceName: String?): Choice? {
        val owner = ownerVoiceName?.takeIf { it.isNotBlank() }
        if (owner != null) {
            val chosen = candidates.firstOrNull { it.name == owner }
            if (chosen != null && chosen.offlineAndInstalled) return Choice(chosen, Origin.OWNER_CHOICE, false)
        }
        val best = selectable(candidates, language).firstOrNull() ?: return null
        return Choice(best, Origin.AUTOMATIC, ownerChoiceDropped = owner != null)
    }

    /** Human-readable row for the picker: language, region, engine quality tier, network/offline. */
    fun describe(c: Candidate): String {
        val region = when (c.country.uppercase()) {
            "AT" -> "Österreich"; "DE" -> "Deutschland"; "CH" -> "Schweiz"
            "GB" -> "Großbritannien"; "US" -> "USA"; "AU" -> "Australien"; "IN" -> "Indien"
            "" -> "ohne Region"; else -> c.country.uppercase()
        }
        val tier = when {
            c.quality >= 400 -> "sehr hohe Qualität"; c.quality >= 300 -> "hohe Qualität"
            c.quality >= 200 -> "normale Qualität"; else -> "einfache Qualität"
        }
        val speed = when {
            c.latency <= 100 -> "sehr schnell"; c.latency <= 200 -> "schnell"; c.latency <= 300 -> "normal"; else -> "langsam"
        }
        return "$region · $tier · $speed · ${c.name}"
    }

    private fun countryRank(language: Language, country: String): Int = when (language) {
        Language.GERMAN -> when (country.uppercase()) { "AT" -> 0; "DE" -> 1; "CH" -> 2; else -> 3 }
        Language.ENGLISH -> when (country.uppercase()) { "GB" -> 0; "US" -> 1; else -> 2 }
    }

    private fun ranking(language: Language): Comparator<Candidate> =
        compareBy<Candidate> { countryRank(language, it.country) }
            .thenByDescending { it.quality }
            .thenBy { it.latency }
            .thenBy { it.name }
}
