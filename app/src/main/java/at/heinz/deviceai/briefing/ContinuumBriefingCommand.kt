package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import java.util.Locale

/** Which day an appointment or weather question is about. */
internal enum class BriefingDay { TODAY, TOMORROW }

/** What the owner asked of the briefing lane. Every command is a whole utterance; nothing is guessed. */
internal sealed interface ContinuumBriefingCommand {
    /** „Systemcheck“, „Wie ist dein Status?“ */
    data object SystemCheck : ContinuumBriefingCommand
    /** „Was steht heute an?“, „Gib mir mein Briefing“; [withNews]: „… mit Nachrichten“ for this one time. */
    data class Briefing(val withNews: Boolean) : ContinuumBriefingCommand
    data class Appointments(val day: BriefingDay) : ContinuumBriefingCommand
    /** [place]: a place named in the sentence („… in Wien“), else the owner's stored place. */
    data class Weather(val day: BriefingDay, val place: String?) : ContinuumBriefingCommand
    /** „Mein Ort ist Linz“ – the owner's place for the weather. */
    data class SetPlace(val place: String) : ContinuumBriefingCommand
    /** „Ruf Anna an“ – [target] is the name or number as said. */
    data class Call(val target: String) : ContinuumBriefingCommand
    /** The daily offer: [enabled] and, when given, the hour of day from which it is made. */
    data class Offer(val enabled: Boolean, val fromHour: Int?) : ContinuumBriefingCommand
    /** The standing news part of the briefing. */
    data class NewsPart(val enabled: Boolean) : ContinuumBriefingCommand

    /** The route as the regression sets write it. */
    val code: String get() = when (this) {
        SystemCheck -> "SYSTEM_CHECK"
        is Briefing -> if (withNews) "BRIEFING:NEWS" else "BRIEFING"
        is Appointments -> "APPOINTMENTS:" + day.name
        is Weather -> "WEATHER:" + day.name + (place?.let { "@$it" } ?: "")
        is SetPlace -> "PLACE:$place"
        is Call -> "CALL:$target"
        is Offer -> "OFFER:" + when {
            !enabled -> "OFF"
            fromHour != null -> fromHour.toString()
            else -> "ON"
        }
        is NewsPart -> "NEWS_PART:" + if (enabled) "ON" else "OFF"
    }
}

/**
 * Recognises the orders of the briefing lane (owner goal 19.11.2026: truthful system start report, daily briefing,
 * „Ruf Anna an“). Deterministic and fail closed: the WHOLE utterance has to be one of the known forms. A quotation,
 * reported speech, a negation, a second order in the same sentence („… und sag ihr …“) or one word too many means
 * `null` – the sentence keeps its present route (document questions, news orders, clock, greeting, diagnosis …).
 * An order the news lane takes today stays a news order.
 */
internal object ContinuumBriefingCommands {
    private const val MAX_LENGTH = 120
    private val quotes = Regex("[\"„“”‚‘«»]")
    private val reported = Regex("(?iu)^(?:er|sie|jemand|man)\\s+(?:sagte|sagt|fragte|fragt|schrieb|schreibt|meinte|meint)\\b")
    private val greeting = Regex("(?iu)^(?:guten\\s+(?:morgen|tag|abend)|hallo|hi|hey|servus|moin|grüß\\s+dich|gruess\\s+dich)[,!.]?\\s+")
    private val courtesyLead = Regex("(?iu)^(?:bitte|also|so|ok|okay)[,]?\\s+")
    private val courtesyTail = Regex("(?iu)[,]?\\s+(?:bitte|danke)$")

    private const val P = "(?:(?:bitte|mal|kurz|doch|jetzt|schnell|eben|noch)\\s+)*"
    private const val DAY = "(heute|morgen)"
    private const val NEWS = "(?:nachrichten|schlagzeilen|news)"
    private const val WORD = "[\\p{L}][\\p{L}.'’-]*"
    private const val PLACE = "($WORD(?:\\s+$WORD){0,3})"
    private const val NAME = "((?:$WORD)(?:\\s+$WORD){0,3})"
    private const val NUMBER = "(\\+?[0-9][0-9 /-]{1,20}[0-9])"

    private fun rx(pattern: String) = Regex("(?iu)^$pattern$")

    private val systemCheck = listOf(
        rx("(?:(?:mach|mache|starte|führ|führe|fuehr|fuehre)\\s+)?$P(?:(?:einen|den)\\s+)?" +
            "(?:systemcheck|system-check|system\\s+check|selbsttest|statuscheck|status-check)(?:\\s+durch)?"),
        rx("(?:(?:gib|sag|nenn|nenne)\\s+mir\\s+)?$P(?:(?:einen|den|deinen)\\s+)?(?:statusbericht|systemstatus|status)"),
        rx("wie\\s+ist\\s+(?:dein|der)\\s+(?:status|systemstatus)(?:\\s+(?:gerade|jetzt|heute))?"),
        rx("(?:läuft|laeuft)\\s+alles(?:\\s+(?:rund|gut|normal|bei\\s+dir))?"),
        rx("ist\\s+alles\\s+in\\s+ordnung(?:\\s+bei\\s+dir)?"),
        rx("(?:fahr|fahre)\\s+$P(?:die|deine)\\s+systeme\\s+hoch"),
        rx("wie\\s+(?:geht\\s+es|geht's|gehts)\\s+deinen\\s+systemen"),
        rx("wie\\s+steht\\s+es\\s+um\\s+deine\\s+systeme"),
        rx("bist\\s+du\\s+(?:einsatzbereit|startklar)"),
        rx("sind\\s+(?:alle|deine)\\s+systeme\\s+(?:bereit|einsatzbereit|in\\s+ordnung|ok|okay|online|da)"),
        rx("laufen\\s+(?:alle|deine)\\s+systeme(?:\\s+(?:rund|gut|normal|optimal))?"))

    /** Group 1 (where present): the news part for this one time. */
    private val briefing = listOf(
        rx("(?:(?:gib|sag|mach|zeig|bring|lies)\\s+mir\\s+)?$P(?:(?:mein|das|ein|einen)\\s+)?(?:tages|morgen)?-?briefing" +
            "(?:\\s+vor)?(\\s+mit\\s+(?:den\\s+)?$NEWS)?"),
        rx("was\\s+(?:steht|liegt)\\s+(?:heute\\s+)?(?:alles\\s+)?an(?:\\s+heute)?"),
        rx("wie\\s+(?:sieht|schaut)\\s+mein\\s+tag\\s+(?:heute\\s+)?aus"),
        rx("was\\s+(?:habe|hab)\\s+ich\\s+heute\\s+(?:alles\\s+)?vor"),
        rx("(?:(?:gib|zeig)\\s+mir\\s+)?$P(?:(?:einen|den|meinen)\\s+)?(?:tagesüberblick|tagesueberblick|" +
            "(?:überblick|ueberblick)\\s+(?:über|ueber)\\s+meinen\\s+tag)"))

    private val appointments = listOf(
        rx("(?:welche|was\\s+für)\\s+termine\\s+(?:habe|hab)\\s+ich(?:\\s+$DAY)?(?:\\s+noch)?"),
        rx("welche\\s+termine\\s+stehen\\s+(?:$DAY\\s+)?an"),
        rx("(?:habe|hab)\\s+ich\\s+(?:$DAY\\s+)?(?:noch\\s+)?(?:termine|einen\\s+termin|(?:was|etwas)\\s+im\\s+kalender)(?:\\s+$DAY)?"),
        rx("was\\s+(?:steht|ist)\\s+(?:$DAY\\s+)?(?:in\\s+meinem|im)\\s+kalender(?:\\s+$DAY)?"),
        rx("was\\s+(?:habe|hab)\\s+ich\\s+(?:$DAY\\s+)?für\\s+termine(?:\\s+$DAY)?"),
        rx("(?:meine\\s+)?termine(?:\\s+(?:für\\s+)?$DAY)?"),
        rx("(?:lies|sag|nenn|nenne|zeig|zeige)\\s+mir\\s+$P(?:meine|die)\\s+termine(?:\\s+(?:für\\s+|von\\s+)?$DAY)?(?:\\s+vor)?"))

    /** Each with the group index of an optional place. */
    private val weather: List<Pair<Regex, Int?>> = listOf(
        rx("wie\\s+(?:wird|ist)\\s+(?:$DAY\\s+)?das\\s+wetter(?:\\s+$DAY)?(?:\\s+in\\s+$PLACE)?") to 3,
        rx("(?:wetter|wetterbericht|wettervorhersage)(?:\\s+(?:für\\s+|von\\s+)?$DAY)?(?:\\s+(?:in|für)\\s+$PLACE)?") to 2,
        rx("(?:regnet|schneit)\\s+es(?:\\s+$DAY)?") to null,
        rx("wird\\s+es\\s+(?:$DAY\\s+)?(?:regnen|schneien)") to null,
        rx("(?:brauche|brauch)\\s+ich\\s+(?:$DAY\\s+)?(?:einen|den|meinen)\\s+(?:regenschirm|schirm)(?:\\s+$DAY)?") to null,
        rx("wie\\s+(?:warm|kalt)\\s+(?:wird|ist)\\s+es(?:\\s+$DAY)?(?:\\s+draußen)?") to null)

    private val setPlace = listOf(
        rx("mein\\s+(?:ort|wohnort|wetterort)(?:\\s+für\\s+das\\s+wetter)?\\s+ist\\s+$PLACE"),
        rx("(?:setz|setze|stell|stelle|ändere|aendere)\\s+${P}meinen\\s+(?:ort|wohnort|wetterort)\\s+auf\\s+$PLACE"),
        rx("nimm\\s+$PLACE\\s+als\\s+(?:meinen\\s+)?(?:ort|wohnort|wetterort)"))

    private val call = listOf(
        rx("(?:ruf|rufe)\\s+$P(?:(?:die|die\\s+nummer)\\s+)?$NUMBER\\s+an"),
        rx("(?:ruf|rufe)\\s+$P$NAME\\s+an"),
        rx("(?:kannst|könntest|koenntest|würdest|wuerdest)\\s+du\\s+$P$NAME\\s+anrufen"),
        rx("ich\\s+(?:möchte|moechte|will|muss|würde\\s+gern|würde\\s+gerne)\\s+$P$NAME\\s+anrufen"),
        rx("$NAME\\s+anrufen"))

    private const val DAILY = "(?:jeden\\s+(?:morgen|tag)|täglich|taeglich|morgens)"
    private const val HOUR = "(?:um|ab)\\s+([0-9]{1,2})(?:\\s+uhr)?"
    private val offerHour = listOf(
        rx("$DAILY\\s+$HOUR\\s+(?:das|mein)\\s+briefing\\s+anbieten"),
        rx("biete\\s+mir\\s+(?:das|mein)\\s+briefing\\s+$DAILY\\s+$HOUR\\s+an"),
        rx("biete\\s+mir\\s+(?:das|mein)\\s+briefing\\s+(?:erst\\s+)?$HOUR\\s+an"))
    private val offerOff = listOf(
        rx("biete\\s+mir\\s+(?:kein|das|mein)\\s+briefing\\s+(?:nicht\\s+)?mehr\\s+an"),
        rx("kein\\s+briefing(?:-angebot)?\\s+mehr(?:\\s+anbieten)?"),
        rx("briefing-?angebot\\s+(?:aus|ausschalten|abschalten)"))
    private val offerOn = listOf(
        rx("biete\\s+mir\\s+(?:das|mein)\\s+briefing\\s+(?:wieder|$DAILY)\\s+an"),
        rx("briefing-?angebot\\s+(?:ein|an|einschalten)"))

    private const val STANDING = "(?:künftig|kuenftig|immer|ab\\s+jetzt|wieder)"
    private val newsOn = listOf(
        rx("nimm\\s+$P(?:die\\s+)?$NEWS\\s+(?:$STANDING\\s+)?(?:ins|in\\s+das|in\\s+mein)\\s+briefing(?:\\s+auf)?"),
        rx("briefing\\s+$STANDING\\s+mit\\s+(?:den\\s+)?$NEWS"))
    private val newsOff = listOf(
        rx("lass\\s+$P(?:die\\s+)?$NEWS\\s+(?:$STANDING\\s+)?(?:im|aus\\s+dem|beim)\\s+briefing\\s+(?:weg|raus)"),
        rx("briefing\\s+$STANDING\\s+ohne\\s+(?:die\\s+)?$NEWS"))

    /** Never the first word of a place: articles, pronouns and words that start a description. */
    private val placeStops = setOf("dem", "der", "den", "die", "das", "des", "einem", "einer", "einen", "ein", "eine",
        "meinem", "meiner", "meinen", "deinem", "deiner", "unserem", "unserer", "diesem", "dieser", "diesen", "jenem",
        "ihm", "ihr", "ihnen", "mir", "dir", "uns", "euch", "heute", "morgen", "gestern", "hier", "dort", "da", "und",
        "oder", "nicht", "kein", "keine", "welchem", "welcher")
    /** Never part of a place name. */
    private val placeForeign = setOf("heute", "morgen", "gestern", "nicht", "und", "oder", "dargestellt", "beschrieben",
        "gezeigt", "film", "buch", "roman", "spiel", "serie", "lied", "bild", "text", "dokument", "pdf",
        // A time span is not a place („in zwei Wochen“, „in den nächsten Tagen“).
        "woche", "wochen", "tag", "tage", "tagen", "stunde", "stunden", "minuten", "monat", "monaten", "jahr", "jahren",
        "zukunft", "nacht", "früh", "abend", "nächsten", "naechsten", "kommenden", "paar", "einigen", "zwei", "drei",
        "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "kürze", "kuerze")
    /** Never the first word of a person to call. */
    private val nameStops = setOf("mich", "dich", "uns", "euch", "ihn", "ihm", "ihr", "sie", "es", "jemanden", "jemand",
        "niemanden", "wen", "wer", "wann", "warum", "wieso", "soll", "sollen", "kann", "kannst", "muss", "darf", "will",
        "ich", "du", "wir", "hat", "habe", "hast", "ist", "war", "nicht", "nie", "mal", "bitte", "zurück", "zurueck",
        "später", "spaeter", "gleich", "sofort", "morgen", "heute", "dann", "noch", "wieder", "lieber")
    /** Never any word of a person to call. */
    private val nameForeign = setOf("nicht", "nie", "niemals", "kein", "keine", "keinen", "und", "oder", "dann", "wenn",
        "falls", "aber", "später", "spaeter", "zurück", "zurueck", "an", "auf", "ab", "mal", "bitte", "ich", "du")

    /** The owner's sentence without address, greeting and courtesy words; null when it is not a plain order at all. */
    private fun plain(utterance: String, assistantName: String?): String? {
        if (utterance.length !in 2..MAX_LENGTH || utterance.any(Char::isISOControl) || quotes.containsMatchIn(utterance)) return null
        var text = utterance.trim().replace(Regex("\\s+"), " ")
        repeat(3) {
            text = OwnerAppLookupIntent.stripAddress(text, assistantName)
            text = greeting.replaceFirst(text, "")
            text = courtesyLead.replaceFirst(text, "")
        }
        text = text.trimEnd('.', '?', '!', ' ').trim()
        text = courtesyTail.replaceFirst(text, "").trim()
        if (text.isEmpty() || reported.containsMatchIn(text)) return null
        return text
    }

    private fun day(groups: List<String>): BriefingDay =
        if (groups.any { it.equals("morgen", ignoreCase = true) }) BriefingDay.TOMORROW else BriefingDay.TODAY

    private fun words(text: String): List<String> = text.lowercase(Locale.GERMAN).split(' ').filter { it.isNotEmpty() }

    private fun place(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val tokens = words(value)
        if (tokens.first() in placeStops || tokens.any { it in placeForeign } || value.length > 60) return null
        return value
    }

    private fun person(raw: String): String? {
        val value = raw.trim()
        val tokens = words(value)
        if (tokens.isEmpty() || tokens.first() in nameStops || tokens.any { it in nameForeign } || value.length > 60) return null
        return value
    }

    fun parse(utterance: String, assistantName: String? = null): ContinuumBriefingCommand? {
        val text = plain(utterance, assistantName) ?: return null
        // An order the news lane takes today stays a news order („Gib mir das Nachrichtenbriefing“).
        if (OwnerNewsCommand.parse(utterance, assistantName) != null) return null
        if (systemCheck.any { it.matches(text) }) return ContinuumBriefingCommand.SystemCheck
        briefing.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            return ContinuumBriefingCommand.Briefing(withNews = match.groupValues.getOrNull(1).orEmpty().isNotBlank())
        }
        appointments.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            return ContinuumBriefingCommand.Appointments(day(match.groupValues.drop(1)))
        }
        weather.firstNotNullOfOrNull { (pattern, placeGroup) -> pattern.matchEntire(text)?.let { it to placeGroup } }
            ?.let { (match, placeGroup) ->
                val named = placeGroup?.let { match.groupValues.getOrNull(it) }?.takeIf { it.isNotEmpty() }
                val where = named?.let { place(it) ?: return null }
                val days = match.groupValues.drop(1).filterIndexed { index, _ -> index + 1 != placeGroup }
                return ContinuumBriefingCommand.Weather(day(days), where)
            }
        setPlace.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            return place(match.groupValues[1])?.let { ContinuumBriefingCommand.SetPlace(it) }
        }
        offerHour.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            return ContinuumBriefingCommand.Offer(enabled = true, fromHour = hour)
        }
        if (offerOff.any { it.matches(text) }) return ContinuumBriefingCommand.Offer(enabled = false, fromHour = null)
        if (offerOn.any { it.matches(text) }) return ContinuumBriefingCommand.Offer(enabled = true, fromHour = null)
        if (newsOn.any { it.matches(text) }) return ContinuumBriefingCommand.NewsPart(true)
        if (newsOff.any { it.matches(text) }) return ContinuumBriefingCommand.NewsPart(false)
        call.first().matchEntire(text)?.let { match ->
            return ContinuumBriefingCommand.Call(match.groupValues[1].trim().replace(Regex("\\s+"), " "))
        }
        call.drop(1).firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            return person(match.groupValues[1])?.let { ContinuumBriefingCommand.Call(it) }
        }
        return null
    }

    /** true when [target] of a [ContinuumBriefingCommand.Call] is a phone number rather than a name. */
    fun isNumber(target: String): Boolean = Regex("^$NUMBER$").matches(target.trim())

    private val emergency = setOf("notruf", "euronotruf", "euro-notruf", "notarzt", "rettung", "rettungsdienst", "polizei",
        "feuerwehr", "krankenwagen", "ambulanz")

    /** true when [target] names an emergency service („den Notruf“, „die Rettung“) rather than a contact. */
    fun isEmergency(target: String): Boolean = words(target)
        .dropWhile { it in setOf("den", "die", "der", "das", "dem", "einen", "eine", "bitte") }.let { it.size == 1 && it[0] in emergency }
}
