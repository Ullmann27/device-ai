package at.heinz.deviceai.cognitive

/**
 * #639: „Jarvis, öffne das Gedächtniszentrum“ is a request for an internal cockpit screen, not for an installed
 * app and never for a file. The resolver maps free German phrasing (dialect, ASR splits such as „Gedächtnis
 * Zentrum“, missing umlauts, small typos) onto the existing route keys of the cockpit (JarvisShellContent.routes /
 * ContinuumWorkAreaNavigationSpec). It only recognises; the Activity performs the navigation through the same
 * path the rail and tiles use. Nothing here starts work, a model call or a network request.
 *
 * A file or folder word („Datei“, „PDF“, „.pdf“, a path) or the explicit word „App“ in the target means the
 * request is NOT internal navigation; the file and app flows keep it.
 */
internal object OwnerInternalNavigationIntent {
    data class Target(val target: String, val label: String) {
        val activityLine: String get() = "NAVIGATION · $label geöffnet · interne Ansicht · kein Web oder Modellaufruf"
    }

    private class Destination(val target: String, val label: String, vararg aliases: String) {
        val aliases: List<String> = aliases.map(::normalize)
    }

    /** Owner-visible names (JarvisTiles.railNav / navigation spec) first, then spoken variants. */
    private val destinations: List<Destination> = listOf(
        Destination("memory", "Gedächtnis", "Gedächtnis", "Gedächtniszentrum", "Gedächtnis Zentrum",
            "Erinnerungen", "Erinnerungszentrum", "Memory", "Memory Center", "Gedächtnisarchiv"),
        Destination("logic", "Denken und Planen", "Denken und Planen", "Logik und Planung", "Logik",
            "Logikzentrum", "Planung", "Planungszentrum", "Denken", "Planen", "Logik & Planung", "Schlussfolgern"),
        Destination("research", "Forschung", "Forschung", "Recherche", "Recherchezentrum", "Forschungszentrum",
            "Evidenz", "Rechercheansicht"),
        Destination("agents", "Assistenten", "Assistenten", "Agenten", "Agentenzentrum", "Helfer", "Bots",
            "Bot-Liste", "Agenten-Pool"),
        Destination("tasks", "Aufgaben", "Aufgaben", "Entwicklungsaufgaben", "Backlog", "Aufgabenliste",
            "Auftragsliste"),
        Destination("web", "Internet", "Internet", "Web", "Webzentrum", "Internetzugang", "Netz"),
        Destination("learning", "Lernen", "Lernen", "Lernzentrum", "Lernbereich"),
        Destination("device", "Gerätesteuerung", "Gerätesteuerung", "Geräte", "Gerätekontrolle"),
        Destination("system", "Systemstatus", "Systemstatus", "System", "Systemzentrum", "Systemansicht"),
        Destination("development", "Entwicklung", "Entwicklungszentrum", "Entwicklung", "Entwickeln",
            "Werkzeuge", "Entwicklerwerkzeuge", "Coding"),
        Destination("model-management", "Modellverwaltung", "Modellverwaltung", "Modelle", "Lokales Modell",
            "Modell", "Modellzentrum", "Sprachmodell"),
        Destination("brain", "Continuum Brain", "Continuum Brain", "Brain", "Gehirnkugel", "Gehirn",
            "Kugel", "Energiekugel"),
        // Bare „Einstellungen“ stays an app lookup (Android settings); the cockpit screen needs its own name.
        Destination("settings", "Einstellungen", "Personalisierung", "Continuum Einstellungen",
            "Jarvis Einstellungen", "Nexus Personalisierung"),
        Destination("privacy", "Datenschutz & Freigaben", "Datenschutz", "Freigaben",
            "Datenschutz und Freigaben", "Privatsphäre"),
        Destination("notifications", "Diagnose", "Diagnose", "Diagnosezentrum", "Meldungen", "Benachrichtigungen"),
        Destination("mobile-builder", "Handy-Builder", "Handy-Builder", "Handy Builder", "Mobile Builder"),
        Destination("home", "Start", "Start", "Startseite", "Cockpit", "Übersicht", "Hauptansicht", "Home"),
        Destination("chat", "Gespräch", "Gespräch", "Chat", "Unterhaltung", "Dialog"),
    )

    val targets: List<String> get() = destinations.map { it.target }

    private val negation = Regex("(?iu)(?<![\\p{L}])(nicht|niemals|kein|keine|keinen|keinem|keiner|ohne)(?![\\p{L}])")
    private val appWord = Regex("(?iu)(?<![\\p{L}])app(?![\\p{L}])")
    private val direct = Regex(
        "(?iu)^(?:öffne|öffnen|starte|zeig|zeige|wechsle|wechsel|wechseln|geh|gehe|spring|springe|navigiere|" +
            "bring\\s+mich|führ\\s+mich|führe\\s+mich|ruf|rufe)\\s+(.+?)(?:\\s+auf)?[.!?]*$"
    )
    private val polite = Regex(
        "(?iu)^(?:kannst|könntest|würdest|magst|kann)\\s+du\\s+(.+?)\\s+" +
            "(?:öffnen|zeigen|anzeigen|aufmachen|aufrufen|starten)(?:\\s+bitte)?[.!?]*$"
    )
    private val desire = Regex(
        "(?iu)^ich\\s+(?:möchte|moechte|will|würde\\s+gern(?:e)?|wuerde\\s+gern(?:e)?)\\s+(.+?)" +
            "(?:\\s+(?:öffnen|sehen|gehen|wechseln|aufrufen|anzeigen|haben))?[.!?]*$"
    )
    private val openUp = Regex("(?iu)^mach(?:e)?\\s+(.+?)\\s+auf[.!?]*$")
    private val bareDirection = Regex("(?iu)^(?:zum|zur|ins|in\\s+das|in\\s+die|auf\\s+die|auf\\s+das)\\s+(.+?)[.!?]*$")
    private val leadingFiller = Regex(
        "(?iu)^(?:mir|mal|bitte|kurz|eben|jetzt|doch|mich|uns|schnell|wieder|nochmal|noch\\s+einmal|" +
            "zu|zum|zur|in|ins|auf|nach|das|die|der|den|dem|mein|meine|meinen|unser|unsere|dein|deine)\\s+"
    )
    private val trailingFiller = Regex("(?iu)\\s+(?:bitte|jetzt|mal|wieder|nochmal|für\\s+mich|danke)$")
    private val viewWord = Regex("(?iu)\\s+(?:zentrum|center|bereich|ansicht|seite|fenster|tab|arbeitswelt|" +
        "arbeitsbereich|menü|menue|modul)$")
    private val compoundSuffix = Regex("(?iu)(?<=\\p{L}{4})(?:zentrum|center|bereich|ansicht)$")

    fun resolve(utterance: String, assistantName: String? = null): Target? {
        if (utterance.length !in 3..200 || utterance.any { it.isISOControl() }) return null
        var text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
        text = text.replace(Regex("(?iu)^(?:hey\\s+|hallo\\s+)?bitte\\s+"), "").trim()
        if (negation.containsMatchIn(text)) return null
        val target = direct.matchEntire(text)?.groupValues?.get(1)
            ?: polite.matchEntire(text)?.groupValues?.get(1)
            ?: desire.matchEntire(text)?.groupValues?.get(1)
            ?: openUp.matchEntire(text)?.groupValues?.get(1)
            ?: bareDirection.matchEntire(text)?.groupValues?.get(1)
        if (target == null) {
            // A bare screen name on its own („Gedächtniszentrum“) counts only when it is exact.
            val bare = strip(text.trimEnd('.', '!', '?'))
            return destinations.firstOrNull { d -> d.aliases.any { it == bare || it.replace(" ", "") == bare.replace(" ", "") } }
                ?.let { Target(it.target, it.label) }
        }
        if (OwnerAppLookupIntent.looksLikeFileTarget(target) || appWord.containsMatchIn(target)) return null
        val phrase = strip(target)
        if (phrase.length < 3) return null
        val candidates = linkedSetOf(phrase, phrase.replace(compoundSuffix, ""), phrase.replace(" ", ""))
        var best: Destination? = null
        var bestDistance = Int.MAX_VALUE
        for (d in destinations) for (alias in d.aliases) for (c in candidates) {
            val forms = listOf(alias, alias.replace(" ", ""))
            for (form in forms) {
                if (kotlin.math.abs(form.length - c.length) > tolerance(form)) continue
                val distance = levenshtein(c, form)
                if (distance <= tolerance(form) && distance < bestDistance) { bestDistance = distance; best = d }
            }
        }
        return best?.let { Target(it.target, it.label) }
    }

    private fun strip(target: String): String {
        var t = normalize(target.trim().trimEnd('.', '!', '?'))
        repeat(6) { t = t.replace(leadingFiller, "").replace(trailingFiller, "").trim() }
        repeat(2) { t = t.replace(viewWord, "").trim() }
        return t
    }

    /** Short names must be exact („Planer“ is not „Planen“); only long compounds absorb two ASR/typo edits. */
    private fun tolerance(reference: String): Int = when {
        reference.length <= 6 -> 0
        reference.length <= 9 -> 1
        else -> 2
    }

    internal fun normalize(text: String): String = text.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace("&", " und ")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }
}
