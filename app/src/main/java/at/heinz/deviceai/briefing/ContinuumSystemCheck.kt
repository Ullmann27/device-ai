package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.InternetAccessMode
import java.util.Locale

/**
 * The real app and device state at one moment, as the host read it. Every value is a fact the app can observe;
 * `null` always means „could not be read“ and is never treated as fine.
 */
internal data class ContinuumSystemFacts(
    val brain: Brain,
    /** The on-device speech recogniser exists. */
    val speechInputAvailable: Boolean?,
    val microphoneGranted: Boolean?,
    /** The owner's switch for spoken replies. */
    val speechOutputEnabled: Boolean,
    /** A voice can speak right now. */
    val speechOutputReady: Boolean?,
    /** The owner's consent setting. */
    val internetMode: InternetAccessMode?,
    /** Android reports a validated connection. */
    val networkOnline: Boolean?,
    /** „Zugriff auf alle Dateien“; null = this Android has no such grant. */
    val allFilesAccess: Boolean?,
    val accessibilityOn: Boolean?,
    val shizukuReady: Boolean?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    /** PowerManager's thermal status 0..6. */
    val thermalStatus: Int?,
    val storageFreeBytes: Long?,
    val storageTotalBytes: Long?,
    /** Open next steps of the work journal („Quelle: Schritt“). */
    val openWork: List<String> = emptyList(),
    /** Open incoming messages; null = no provider is wired. */
    val openMessages: Int? = null,
    /** Work that really runs right now, each as a short phrase („eine Auftragsfolge“); empty = nothing runs. */
    val running: List<String> = emptyList()
) {
    sealed interface Brain {
        /** The on-device model; [name] as the runtime reports it. */
        data class Local(val state: State, val name: String?) : Brain
        /** The owner selected the cloud brain; [keyPresent]: a key is stored. Reachability is not claimed. */
        data class Cloud(val keyPresent: Boolean) : Brain
        enum class State { UNKNOWN, NOT_LOADED, LOADING, READY, GENERATING, UNLOADING, FAILED }
    }
}

/**
 * The truthful system start report (owner goal 19.11.2026, „Ich fahre die Systeme hoch …“): a real self-check
 * computed from [ContinuumSystemFacts]. Nothing here measures or guesses; a spoken status is only what a check shows.
 *
 * Wording rules (tested): „Alles läuft“ is said only when EVERY check is OK. Problems come first and plainly. What
 * the owner switched off is named as switched off, not as a fault and not as running. Running work is named only
 * from [ContinuumSystemFacts.running].
 */
internal object ContinuumSystemCheck {
    enum class State { OK, WARN, FAIL, OFF }

    /**
     * One check. [detail]: short text for a card („82 % · lädt nicht“). [spoken]: the same fact as a clause for a
     * sentence („der Akku ist bei 82 Prozent“).
     */
    data class Check(val key: String, val label: String, val state: State, val detail: String, val spoken: String)

    const val BRAIN = "brain"
    const val SPEECH_IN = "speech_in"
    const val SPEECH_OUT = "speech_out"
    const val INTERNET = "internet"
    const val FILES = "files"
    const val ACCESSIBILITY = "accessibility"
    const val BRIDGE = "bridge"
    const val BATTERY = "battery"
    const val THERMAL = "thermal"
    const val STORAGE = "storage"
    const val OPEN = "open"
    const val ACTIVITY = "activity"

    const val THERMAL_MODERATE = 2
    const val THERMAL_SEVERE = 3
    const val BATTERY_LOW_PERCENT = 20
    const val BATTERY_CRITICAL_PERCENT = 5
    const val STORAGE_LOW_FRACTION = 0.10
    const val STORAGE_CRITICAL_FRACTION = 0.03
    const val STORAGE_CRITICAL_BYTES = 1_000_000_000L

    @Volatile private var latest: List<Check> = emptyList()

    /** The checks of the last report, for the stage: an immutable list, empty before the first check. */
    fun snapshot(): List<Check> = latest

    /** Computes the checks from [facts] and publishes them as [snapshot]. */
    fun run(facts: ContinuumSystemFacts): List<Check> = evaluate(facts).also { latest = it }

    fun evaluate(facts: ContinuumSystemFacts): List<Check> = buildList {
        add(brain(facts))
        add(speechInput(facts))
        add(speechOutput(facts))
        add(internet(facts))
        add(files(facts))
        add(accessibility(facts))
        add(bridge(facts))
        add(battery(facts))
        add(thermal(facts))
        add(storage(facts))
        add(open(facts))
        if (facts.running.isNotEmpty()) add(Check(ACTIVITY, "Laufende Arbeit", State.OK,
            facts.running.joinToString(", ").replaceFirstChar { it.titlecase(Locale.GERMAN) },
            "gerade läuft " + list(facts.running)))
    }

    private fun modelName(name: String?): String? = name?.trim()
        ?.replace(Regex("[^\\p{L}\\p{N} ._+-]"), "")?.replace(Regex("\\s+"), " ")?.take(40)?.takeIf { it.isNotBlank() }

    private fun brain(f: ContinuumSystemFacts): Check = when (val brain = f.brain) {
        is ContinuumSystemFacts.Brain.Cloud -> when {
            !brain.keyPresent -> Check(BRAIN, "Gehirn", State.FAIL, "Claude gewählt · kein Schlüssel",
                "Claude ist ausgewählt, aber es ist kein Schlüssel hinterlegt")
            f.networkOnline == false -> Check(BRAIN, "Gehirn", State.WARN, "Claude gewählt · Gerät offline",
                "Claude ist ausgewählt, aber das Gerät hat gerade keine Verbindung")
            else -> Check(BRAIN, "Gehirn", State.OK, "Claude gewählt · Schlüssel hinterlegt",
                "Claude ist ausgewählt und der Schlüssel ist hinterlegt")
        }
        is ContinuumSystemFacts.Brain.Local -> {
            val name = modelName(brain.name)
            // Spoken: the family word the model's own identifier starts with („gemma-3n-e4b“ → „Gemma“).
            val model = name?.let { Regex("^\\p{L}{3,}").find(it)?.value }
                ?.let { it.lowercase(Locale.GERMAN).replaceFirstChar { first -> first.titlecase(Locale.GERMAN) } } ?: "das lokale Modell"
            when (brain.state) {
                ContinuumSystemFacts.Brain.State.READY -> Check(BRAIN, "Gehirn", State.OK,
                    (name ?: "Lokales Modell") + " · geladen", "$model ist geladen")
                ContinuumSystemFacts.Brain.State.GENERATING -> Check(BRAIN, "Gehirn", State.OK,
                    (name ?: "Lokales Modell") + " · antwortet gerade", "$model ist geladen und antwortet gerade")
                ContinuumSystemFacts.Brain.State.LOADING -> Check(BRAIN, "Gehirn", State.WARN,
                    "Lokales Modell · wird geladen", "das lokale Modell wird noch geladen")
                ContinuumSystemFacts.Brain.State.UNLOADING -> Check(BRAIN, "Gehirn", State.WARN,
                    "Lokales Modell · wird entladen", "das lokale Modell wird gerade entladen")
                ContinuumSystemFacts.Brain.State.NOT_LOADED -> Check(BRAIN, "Gehirn", State.FAIL,
                    "Kein Modell geladen", "es ist kein lokales Modell geladen")
                ContinuumSystemFacts.Brain.State.FAILED -> Check(BRAIN, "Gehirn", State.FAIL,
                    "Lokales Modell · Laufzeitfehler", "das lokale Modell meldet einen Laufzeitfehler")
                ContinuumSystemFacts.Brain.State.UNKNOWN -> Check(BRAIN, "Gehirn", State.WARN,
                    "Lokales Modell · Zustand unbekannt", "den Zustand des lokalen Modells kann ich nicht lesen")
            }
        }
    }

    private fun speechInput(f: ContinuumSystemFacts): Check = when {
        f.speechInputAvailable == null -> Check(SPEECH_IN, "Spracheingabe", State.WARN, "Verfügbarkeit unbekannt",
            "ob die Spracherkennung verfügbar ist, kann ich nicht lesen")
        f.speechInputAvailable == false -> Check(SPEECH_IN, "Spracheingabe", State.FAIL, "Erkennung nicht verfügbar",
            "die Spracherkennung ist auf dem Gerät nicht verfügbar")
        f.microphoneGranted == true -> Check(SPEECH_IN, "Spracheingabe", State.OK, "Erkennung und Mikrofon bereit",
            "die Spracheingabe steht")
        f.microphoneGranted == false -> Check(SPEECH_IN, "Spracheingabe", State.WARN, "Mikrofon nicht freigegeben",
            "das Mikrofon ist nicht freigegeben")
        else -> Check(SPEECH_IN, "Spracheingabe", State.WARN, "Mikrofonfreigabe unbekannt",
            "die Mikrofonfreigabe kann ich nicht lesen")
    }

    private fun speechOutput(f: ContinuumSystemFacts): Check = when {
        !f.speechOutputEnabled -> Check(SPEECH_OUT, "Sprachausgabe", State.OFF, "ausgeschaltet", "die Sprachausgabe")
        f.speechOutputReady == true -> Check(SPEECH_OUT, "Sprachausgabe", State.OK, "Stimme bereit", "die Sprachausgabe steht")
        f.speechOutputReady == false -> Check(SPEECH_OUT, "Sprachausgabe", State.FAIL, "eingeschaltet · Stimme nicht bereit",
            "die Sprachausgabe ist eingeschaltet, aber die Stimme ist nicht bereit")
        else -> Check(SPEECH_OUT, "Sprachausgabe", State.WARN, "Zustand unbekannt",
            "den Zustand der Sprachausgabe kann ich nicht lesen")
    }

    private fun internet(f: ContinuumSystemFacts): Check {
        val consent = when (f.internetMode) {
            InternetAccessMode.ASK -> "Freigabe: Nachfragen"
            InternetAccessMode.ALWAYS_ON -> "Freigabe: Immer"
            else -> ""
        }
        return when {
            f.internetMode == null -> Check(INTERNET, "Internet", State.WARN, "Freigabe unbekannt",
                "die Internetfreigabe kann ich nicht lesen")
            f.internetMode == InternetAccessMode.OFF -> Check(INTERNET, "Internet", State.OFF, "in der App ausgeschaltet",
                "der Internetzugang")
            f.networkOnline == true -> Check(INTERNET, "Internet", State.OK, "Verbindung steht · $consent",
                "die Internetverbindung steht")
            f.networkOnline == false -> Check(INTERNET, "Internet", State.WARN, "keine Verbindung · $consent",
                "das Gerät hat gerade keine Internetverbindung")
            else -> Check(INTERNET, "Internet", State.WARN, "Verbindung unbekannt · $consent",
                "ob eine Internetverbindung besteht, kann ich nicht lesen")
        }
    }

    private fun files(f: ContinuumSystemFacts): Check = when (f.allFilesAccess) {
        true -> Check(FILES, "Dateizugriff", State.OK, "Zugriff auf alle Dateien erteilt", "der Dateizugriff ist erteilt")
        false -> Check(FILES, "Dateizugriff", State.OFF, "Zugriff auf alle Dateien nicht erteilt",
            "der Zugriff auf alle Dateien")
        null -> Check(FILES, "Dateizugriff", State.WARN, "auf diesem Android nicht verfügbar",
            "den Zugriff auf alle Dateien gibt es auf diesem Android nicht")
    }

    private fun accessibility(f: ContinuumSystemFacts): Check = when (f.accessibilityOn) {
        true -> Check(ACCESSIBILITY, "Bedienungshilfe", State.OK, "eingeschaltet", "die Bedienungshilfe ist eingeschaltet")
        false -> Check(ACCESSIBILITY, "Bedienungshilfe", State.OFF, "ausgeschaltet", "die Bedienungshilfe")
        null -> Check(ACCESSIBILITY, "Bedienungshilfe", State.WARN, "Zustand unbekannt",
            "den Zustand der Bedienungshilfe kann ich nicht lesen")
    }

    private fun bridge(f: ContinuumSystemFacts): Check = when (f.shizukuReady) {
        true -> Check(BRIDGE, "Shizuku", State.OK, "verbunden", "Shizuku ist verbunden")
        false -> Check(BRIDGE, "Shizuku", State.WARN, "nicht erreichbar", "Shizuku läuft nicht")
        null -> Check(BRIDGE, "Shizuku", State.WARN, "Zustand unbekannt", "den Zustand von Shizuku kann ich nicht lesen")
    }

    private fun battery(f: ContinuumSystemFacts): Check {
        val percent = f.batteryPercent?.takeIf { it in 0..100 }
            ?: return Check(BATTERY, "Akku", State.WARN, "nicht lesbar", "den Akkustand kann ich nicht lesen")
        val charging = f.charging == true
        val detail = "$percent %" + when (f.charging) { true -> " · lädt"; false -> " · lädt nicht"; null -> "" }
        return when {
            !charging && percent <= BATTERY_CRITICAL_PERCENT -> Check(BATTERY, "Akku", State.FAIL, detail,
                "der Akku ist fast leer, nur noch $percent Prozent")
            !charging && percent <= BATTERY_LOW_PERCENT -> Check(BATTERY, "Akku", State.WARN, detail,
                "der Akku ist bei nur $percent Prozent und lädt nicht")
            charging -> Check(BATTERY, "Akku", State.OK, detail, "der Akku ist bei $percent Prozent und lädt")
            else -> Check(BATTERY, "Akku", State.OK, detail, "der Akku ist bei $percent Prozent")
        }
    }

    private fun thermal(f: ContinuumSystemFacts): Check = when (val status = f.thermalStatus?.takeIf { it in 0..6 }) {
        null -> Check(THERMAL, "Wärme", State.WARN, "nicht gemeldet", "den Wärmezustand meldet das Gerät nicht")
        0 -> Check(THERMAL, "Wärme", State.OK, "normal", "das Gerät ist kühl")
        1 -> Check(THERMAL, "Wärme", State.OK, "leicht erwärmt", "das Gerät ist leicht erwärmt")
        THERMAL_MODERATE -> Check(THERMAL, "Wärme", State.WARN, "warm", "das Gerät ist schon warm")
        // From SEVERE on the thermal guard really starts no local generation (LocalModelThermalGuard).
        else -> Check(THERMAL, "Wärme", State.FAIL, if (status == THERMAL_SEVERE) "stark erwärmt" else "kritisch heiß",
            if (f.brain is ContinuumSystemFacts.Brain.Local)
                "das Gerät ist stark erwärmt – das lokale Modell antwortet erst wieder, wenn es abgekühlt ist"
            else "das Gerät ist stark erwärmt")
    }

    private fun storage(f: ContinuumSystemFacts): Check {
        val free = f.storageFreeBytes
        val total = f.storageTotalBytes
        if (free == null || total == null || total <= 0 || free !in 0..total)
            return Check(STORAGE, "Speicher", State.WARN, "nicht lesbar", "den freien Speicher kann ich nicht lesen")
        val gigabytes = gigabytes(free)
        val fraction = free.toDouble() / total
        return when {
            fraction < STORAGE_CRITICAL_FRACTION || free < STORAGE_CRITICAL_BYTES -> Check(STORAGE, "Speicher", State.FAIL,
                "$gigabytes GB frei", "der Speicher ist fast voll, nur noch $gigabytes Gigabyte frei")
            fraction < STORAGE_LOW_FRACTION -> Check(STORAGE, "Speicher", State.WARN, "$gigabytes GB frei",
                "der Speicher wird knapp, noch $gigabytes Gigabyte frei")
            else -> Check(STORAGE, "Speicher", State.OK, "$gigabytes GB frei", "$gigabytes Gigabyte Speicher sind frei")
        }
    }

    private fun open(f: ContinuumSystemFacts): Check {
        val work = f.openWork.size
        val messages = f.openMessages?.coerceAtLeast(0) ?: 0
        if (work + messages == 0) return Check(OPEN, "Offenes", State.OK, NOTHING_OPEN, "es ist nichts offen")
        val detail = buildList {
            if (work > 0) add("$work " + if (work == 1) "offene Arbeit" else "offene Arbeiten")
            if (messages > 0) add("$messages " + if (messages == 1) "offene Nachricht" else "offene Nachrichten")
        }
        val spoken = buildList {
            if (work > 0) add(count(work, "eine Arbeit", "Arbeiten"))
            if (messages > 0) add(count(messages, "eine Nachricht", "Nachrichten"))
        }
        return Check(OPEN, "Offenes", State.OK, detail.joinToString(" · "),
            "offen " + (if (work + messages == 1) "ist" else "sind") + " noch " + list(spoken))
    }

    /** German decimal comma, one place below 10 GB („7,5“), whole numbers above. */
    internal fun gigabytes(bytes: Long): String {
        val value = bytes / 1_000_000_000.0
        return if (value >= 10) Math.round(value).toString()
        else String.format(Locale.GERMAN, "%.1f", Math.floor(value * 10) / 10).removeSuffix(",0")
    }

    /** [one] with its article („eine Arbeit“, „ein Termin“); from two on the number word and [many]. */
    internal fun count(n: Int, one: String, many: String): String = when (n) {
        1 -> one
        in 2..12 -> NUMBERS[n] + " " + many
        else -> "$n $many"
    }

    /** „a“, „a und b“, „a, b und c“. */
    internal fun list(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " und " + parts.last()
    }

    private val NUMBERS = listOf("null", "eine", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf")

    private fun capital(text: String): String = text.replaceFirstChar { it.titlecase(Locale.GERMAN) }

    /** Checks that are neither fine nor switched off, the failing ones first. */
    fun problems(checks: List<Check>): List<Check> =
        checks.filter { it.state == State.FAIL } + checks.filter { it.state == State.WARN }

    /**
     * The spoken and written report. [ownerName]: how the owner is addressed, or null. It says only what [checks]
     * show: problems first; „Alles läuft“ only when every check is OK.
     */
    fun report(checks: List<Check>, ownerName: String? = null): String {
        if (checks.isEmpty()) return "Ich konnte den Systemcheck gerade nicht ausführen."
        val name = ownerName?.trim()?.takeIf { it.isNotEmpty() }
        val problems = problems(checks)
        val off = checks.filter { it.state == State.OFF }
        val fine = checks.filter { it.state == State.OK }
        val running = fine.firstOrNull { it.key == ACTIVITY }
        val openItems = fine.firstOrNull { it.key == OPEN && it.detail != NOTHING_OPEN }
        // The highlights of what works: brain, speech and battery – only those that really are OK.
        val highlights = listOf(BRAIN, SPEECH_IN, BATTERY).mapNotNull { key -> fine.firstOrNull { it.key == key } }
        return buildString {
            if (problems.isEmpty() && off.isEmpty()) {
                append("Alles läuft").append(name?.let { ", $it" } ?: "").append(": ")
                append(list(highlights.map { it.spoken })).append('.')
            } else if (problems.isEmpty()) {
                append(name?.let { "$it, was " } ?: "Was ").append("eingeschaltet ist, läuft: ")
                append(list(highlights.map { it.spoken })).append('.')
            } else {
                append(when (problems.size) {
                    1 -> "Ein Punkt"
                    2 -> "Zwei Dinge"
                    3 -> "Drei Dinge"
                    else -> "Mehrere Dinge"
                }).append(name?.let { ", $it" } ?: "").append(": ")
                val shown = problems.take(MAX_SPOKEN_PROBLEMS).map { it.spoken }
                append(capital(if (shown.size == 2) shown[0] + ", und " + shown[1] else list(shown))).append('.')
                if (problems.size > MAX_SPOKEN_PROBLEMS)
                    append(" Dazu ").append(count(problems.size - MAX_SPOKEN_PROBLEMS, "ein weiterer Punkt", "weitere Punkte"))
                        .append(" in der Liste.")
                if (highlights.isNotEmpty()) append(" In Ordnung: ").append(list(highlights.map { it.spoken })).append('.')
            }
            if (off.isNotEmpty()) append(" Ausgeschaltet ").append(if (off.size == 1) "ist " else "sind ")
                .append(list(off.map { it.spoken })).append('.')
            running?.let { append(' ').append(capital(it.spoken)).append('.') }
            openItems?.let { append(' ').append(capital(it.spoken)).append('.') }
        }
    }

    /** The list as plain chat text below the report: one line per check. */
    fun lines(checks: List<Check>): String = checks.joinToString("\n") { check ->
        val mark = when (check.state) {
            State.OK -> "✓"
            State.WARN -> "!"
            State.FAIL -> "✗"
            State.OFF -> "–"
        }
        "$mark ${check.label}: ${check.detail}"
    }

    const val MAX_SPOKEN_PROBLEMS = 4
    private const val NOTHING_OPEN = "nichts offen"
}
