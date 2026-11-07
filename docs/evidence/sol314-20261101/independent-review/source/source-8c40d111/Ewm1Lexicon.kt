// Product copy of the EWM2 research class (PR #224, code freeze e1669271): same logic; only the package,
// the removed EWM1_TRACE debug output and the removed research-only N0 baseline differ.
package at.heinz.deviceai.intelligence.ewm

import java.util.Locale

/**
 * General German closed-class knowledge for EWM1 (no corpus statistics, no training).
 * Every list is ordinary language knowledge; none is a memory or an owner fact.
 */
internal object Ewm1Lexicon {
    val FUNCTION = setOf(
        "wer", "wie", "was", "wo", "wann", "warum", "wieso", "weshalb", "welche", "welcher", "welches", "welchen",
        "welchem", "woher", "wohin", "womit", "woraus", "worauf", "wozu", "wem", "wen", "wessen", "wieviel", "wieviele",
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines",
        "mein", "meine", "meinen", "meinem", "meiner", "meines", "dein", "deine", "deinen", "deinem", "deiner",
        "unser", "unsere", "unseren", "unserem", "unserer", "unseres", "euer", "eure",
        "sein", "seine", "seinen", "seinem", "seiner", "seines", "ihr", "ihre", "ihren", "ihrem", "ihrer", "ihres",
        "dessen", "deren", "ich", "du", "er", "sie", "es", "wir", "man", "mich", "mir", "dich", "dir", "uns", "ihn", "ihm",
        "und", "oder", "aber", "sondern", "denn", "doch", "nein", "ja", "nicht", "kein", "keine", "keinen", "keinem",
        "keiner", "nie", "auch", "noch", "nur", "schon", "jetzt", "nun", "mal", "eigentlich", "genau", "bitte", "eben",
        "wieder", "zurück", "gerade", "einmal", "dann", "also", "so", "sehr", "gern", "gerne", "lieber", "am", "im", "an",
        "in", "auf", "aus", "bei", "mit", "nach", "von", "vom", "zu", "zum", "zur", "für", "über", "unter", "vor",
        "hinter", "neben", "zwischen", "um", "ab", "bis", "seit", "statt", "ohne", "gegen", "durch", "wegen",
        "ist", "sind", "war", "waren", "wird", "werden", "wurde", "wurden", "hat", "haben", "hatte", "hatten",
        "kann", "kannst", "können", "soll", "sollen", "will", "wollen", "muss", "müssen", "darf", "gibt", "geht",
        "meinte", "meine", "meinst", "gemeint", "da", "dort", "darin", "darauf", "dafür", "dazu", "damit", "daran",
        "davon", "darüber", "dorthin", "hier", "beide", "beiden", "zwei", "dr", "erzähl", "erzähle", "sag", "sage",
        "weißt", "kennst", "beste", "besten", "nächste", "nächster", "nächsten", "viel", "viele", "wenig", "wenige",
        "lange", "oft", "hoch", "weit", "macht", "machen", "mache", "machst", "eigentlich", "denn", "herr", "prof",
        "bekannt", "diese", "dieser", "dieses", "diesen", "diesem", "also", "gemeint", "war", "sieht", "aus", "jetzt"
    )

    /** Attribute, time and unit nouns: they ask for a property, they never name the thing asked about. */
    val GENERIC = setOf(
        "farbe", "alter", "jahre", "jahr", "name", "namen", "arbeit", "nummer", "telefonnummer",
        "adresse", "größe", "gewicht", "preis", "kosten", "euro", "cent", "uhr", "uhrzeit", "zeit", "datum", "tag", "tage",
        "woche", "wochen", "monat", "monate", "wochenende", "morgen", "früh", "vormittag", "mittag", "nachmittag",
        "abend", "nacht", "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag",
        "jänner", "januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober",
        "november", "dezember", "frühling", "sommer", "herbst", "winter", "kilometer", "meter", "liter", "prozent",
        "stunden", "minuten", "sorte", "art", "modell", "marke", "typ", "rasse", "stand", "status", "technik", "sprache",
        "ort", "stadt", "land", "leute", "personen", "mal", "rolle", "grund", "frage", "antwort", "heuer", "geburtstag",
        "leistung", "kapazität", "menge", "anzahl", "stück", "wh", "kwh", "kwp", "gb", "mb", "farblich", "teil"
    )

    val TIME = setOf(
        "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag", "montags", "dienstags",
        "mittwochs", "donnerstags", "freitags", "samstags", "sonntags", "jänner", "januar", "februar", "märz", "april",
        "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember", "uhr", "morgen", "morgens",
        "früh", "vormittag", "mittag", "nachmittag", "abend", "abends", "nacht", "nachts", "heuer", "jeden", "jede",
        "seit", "wochenende", "sommer", "winter", "herbst", "frühling", "täglich", "wöchentlich", "monatlich", "jährlich",
        "mittwochabend", "montagabend", "dienstagabend", "donnerstagabend", "freitagabend", "geburtstag", "heute", "gestern"
    )

    val COLORS = setOf(
        "rot", "blau", "grün", "gelb", "schwarz", "weiß", "grau", "braun", "orange", "lila", "violett", "rosa", "pink",
        "türkis", "beige", "silber", "gold", "golden", "bunt", "getigert", "gefleckt", "mintgrün", "dunkelrot",
        "hellblau", "dunkelblau", "hellgrün", "dunkelgrün", "schwarz-weiß", "schwarzweiß", "kupferfarben", "silbern"
    )

    val NUMBER_WORDS = mapOf(
        "null" to 0, "eins" to 1, "ein" to 1, "eine" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12,
        "dreizehn" to 13, "vierzehn" to 14, "fünfzehn" to 15, "zwanzig" to 20, "dreißig" to 30, "hundert" to 100,
        "erste" to 1, "erster" to 1, "zweite" to 2, "dritte" to 3, "vierte" to 4, "fünfte" to 5, "zweiten" to 2,
        "dritten" to 3, "vierten" to 4
    )

    /** Relation thesaurus: words that ask for or state the same kind of relation. */
    val GROUPS: Map<String, Set<String>> = mapOf(
        "wohnen" to setOf("wohnt", "wohnen", "wohne", "wohnst", "lebt", "leben", "lebe", "wohnort", "wohnung", "unterkunft",
            "zuhause", "daheim", "umgezogen", "umziehen", "gezogen", "zieht", "übersiedelt", "übersiedeln"),
        "arbeiten" to setOf("arbeitet", "arbeiten", "arbeite", "beruflich", "beruf", "job", "tätig", "betreut", "betreuen",
            "studiert", "studieren", "studium"),
        "alter" to setOf("alt", "alter", "jahre", "geboren"),
        "geburtstag" to setOf("geburtstag", "geboren", "geburtstage"),
        "kosten" to setOf("kostet", "kosten", "preis", "euro", "teuer", "zahlen"),
        "heissen" to setOf("heißt", "heißen", "name", "namen", "namens", "nennt", "lautet"),
        "farbe" to setOf("farbe") + COLORS,
        "essen" to setOf("isst", "essen", "frisst", "fressen", "futter", "fleisch", "lieblingsessen", "kocht", "kochen"),
        "trinken" to setOf("trinkt", "trinken", "trinke", "getränk", "kaffee", "tee", "kräutertee", "wein", "bier",
            "alkohol", "wasser", "saft"),
        "instrument" to setOf("instrument", "spielt", "spielen", "gitarre", "schlagzeug", "klavier", "geige", "flöte",
            "bass", "trompete", "harmonika", "akkordeon"),
        "tier" to setOf("haustier", "haustiere", "tier", "tiere", "hund", "katze", "kater", "vogel", "wellensittich",
            "fisch", "fische", "hase", "hamster", "pferd", "meerschweinchen", "papagei"),
        "zeitpunkt" to setOf("wann", "termin", "uhr", "datum", "zeit", "uhrzeit"),
        "oeffnung" to setOf("offen", "geöffnet", "geschlossen", "ordination", "öffnungszeiten", "öffnet", "schließt"),
        "speicher" to setOf("arbeitsspeicher", "ram", "speicher", "gb"),
        "beginn" to setOf("beginnt", "beginnen", "beginn", "fängt", "anfangen", "startet", "starten", "start", "los", "losgehen"),
        "urheber" to setOf("erstellt", "geschrieben", "gemacht", "stammt", "entwickelt", "verfasst", "gebaut", "programmiert",
            "erstellen", "angelegt"),
        "leitung" to setOf("leitet", "geleitet", "leiten", "zuständig", "verantwortlich", "leiter", "leiterin", "chef", "chefin"),
        "inhalt" to setOf("inhalt", "enthält", "enthalten", "beinhaltet", "steht", "drin"),
        "standort" to setOf("steht", "stehen", "aufgestellt", "liegt", "liegen", "befindet", "hängt", "hängen", "untergebracht",
            "abgestellt", "parkt"),
        "besorgen" to setOf("holt", "holen", "kauft", "kaufen", "besorgt", "besorgen", "bekommt", "bezieht"),
        "muster" to setOf("muster", "kariert", "gestreift", "gepunktet", "geblümt", "einfarbig", "gemustert"),
        "verwandt" to setOf("kinder", "kind", "sohn", "söhne", "tochter", "töchter", "enkel", "bruder", "schwester",
            "verheiratet", "frau", "mann", "partner", "partnerin")
    )

    /** Relations that hold one current value: a later statement replaces an earlier one. */
    val SINGLE_VALUED = setOf("wohnen", "alter", "kosten")

    val UPDATE_MARKERS = setOf(
        "seit", "inzwischen", "mittlerweile", "jetzt", "nun", "neuerdings", "heuer", "statt", "anstatt", "verschoben",
        "verlegt", "vorverlegt", "umgezogen", "gezogen", "übersiedelt", "gewechselt", "geändert", "nicht mehr", "nur noch",
        "ab sofort", "zuletzt", "erst"
    )

    /** Tense orientation for supersession: history and future plans never end a present state. */
    val PAST_CUES = setOf("früher", "damals", "ehemals", "einst", "gestern", "vorgestern", "letzte", "letzten", "letztem", "letzter",
        "letztes", "war", "waren", "warst", "wart", "wohnte", "wohnten", "lebte", "lebten", "arbeitete", "arbeiteten", "besuchte",
        "besuchten", "gewohnt", "gelebt", "gearbeitet", "gewesen")
    val FUTURE_CUES = setOf("demnächst", "bald", "künftig", "zukünftig", "nächste", "nächsten", "nächstem", "nächster", "nächstes", "übermorgen")
    val PRESENT_CUES = setOf("seit", "jetzt", "inzwischen", "mittlerweile", "nun", "heute", "neuerdings", "gerade", "derzeit", "momentan")
    /** Degree and frequency adverbs name no predicate ("gerne", "gerade"). */
    val DEGREE = setOf("gerne", "gern", "lieber", "gerade", "auch", "oft", "immer", "sehr", "meistens", "manchmal", "wieder", "noch",
        "schon", "mehr", "derzeit", "momentan")
    /** Verbs of saying: a reported statement is no statement of the speaker. */
    val SAYING = setOf("sagt", "sagte", "meint", "meinte", "erzählt", "erzählte", "behauptet", "behauptete", "schreibt", "schrieb")

    val NEGATIONS = setOf("nicht", "kein", "keine", "keinen", "keinem", "keiner", "keines", "nie", "niemals", "nichts")

    val ANTONYMS = listOf("offen" to "geschlossen", "geöffnet" to "geschlossen", "mehr" to "weniger", "früher" to "später")

    val ROLE_M = setOf(
        "bruder", "vater", "sohn", "onkel", "neffe", "enkel", "cousin", "schwager", "opa", "großvater", "mann", "freund",
        "kollege", "nachbar", "chef", "arzt", "hausarzt", "zahnarzt", "lehrer", "installateur", "kater", "hund", "rüde",
        "wellensittich", "vogel", "papagei", "hamster", "partner", "kursleiter", "trainer", "physiotherapeut", "masseur"
    )
    val ROLE_F = setOf(
        "schwester", "mutter", "tochter", "tante", "nichte", "enkelin", "cousine", "schwägerin", "oma", "großmutter",
        "frau", "freundin", "kollegin", "nachbarin", "chefin", "ärztin", "hausärztin", "zahnärztin", "lehrerin",
        "katze", "partnerin", "kursleiterin", "trainerin", "physiotherapeutin", "masseurin", "apothekerin"
    )

    val PRONOUN_M = setOf("er", "ihn", "ihm", "sein", "seine", "seinen", "seinem", "seiner", "seines", "dessen", "der")
    val PRONOUN_F = setOf("sie", "ihr", "ihre", "ihren", "ihrem", "ihrer", "ihres", "deren", "die")
    val PRONOUN_N = setOf("es")
    val PRONOUN_THING = setOf("das", "da", "dort", "darin", "darauf", "dafür", "dazu", "damit", "daran", "davon", "dorthin")
    val PLACE_PREPOSITIONS = setOf("in", "im", "nach", "aus")
    val QUANTIFIERS = setOf("viel", "viele", "wenig", "wenige", "mehr", "kein", "keine", "keinen", "zwei", "drei", "beide")
    /** "wie viel/groß/lange …" only ask for an amount; "wie schwer/voll …" ask for a property the memory may lack. */
    val TYPE_CUE_ADJ = setOf("viel", "viele", "groß", "lange", "alt", "oft", "hoch", "weit", "spät", "früh", "wenig", "wenige", "teuer")
    val PERSON_VERBS = setOf("wohnt", "wohnen", "lebt", "leben", "arbeitet", "arbeiten", "heißt", "studiert", "spielt", "isst",
        "trinkt", "geht", "kommt", "fährt", "leitet", "betreut", "kocht")
    val KIND_NOUNS = setOf("rasse", "sorte", "modell", "marke", "typ", "art")
    val TITLES = setOf("dr", "prof", "herr", "mag", "ing")
    val TIME_SYNONYMS = listOf(setOf("früh", "morgen", "morgens", "vormittag"), setOf("abend", "abends"),
        setOf("nacht", "nachts"), setOf("jänner", "januar"))
    private val OCCUPATION_END = Regex("(er|erin|in|eur|eurin|ist|istin|ant|antin|ent|entin|arzt|ärztin|führer|führerin|leiter|leiterin|meister|meisterin)$")
    fun occupationLike(word: String): Boolean = word.length >= 5 && OCCUPATION_END.containsMatchIn(lower(word))
    val ARTICLES = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines",
        "mein", "meine", "meinen", "meinem", "meiner", "meines", "unser", "unsere", "unseren", "unserem", "unserer",
        "unseres", "dein", "deine", "deinem", "deinen", "deiner", "sein", "seine", "seinem", "seinen", "seiner", "ihr",
        "ihre", "ihrem", "ihren", "ihrer", "welche", "welcher", "welches", "welchen", "welchem", "jeden", "jede", "jeder")

    fun lower(s: String): String = s.lowercase(Locale.ROOT)

    /** Umlaut-insensitive form for matching inflections ("schläft"/"schlafen", "Halsbänder"/"Halsband"). */
    fun fold(s: String): String = lower(s).replace('ä', 'a').replace('ö', 'o').replace('ü', 'u')

    /** A colour word, also inflected or compounded ("orangen", "türkisfarbenen", "mintgrün"). */
    fun isColor(word: String): Boolean {
        val w = lower(word)
        if (w in COLORS || w.split('-').let { p -> p.size > 1 && p.all { it in COLORS } }) return true
        if (w.contains("farben") || w.endsWith("farbig")) return true
        val base = listOf("en", "em", "er", "es", "e").firstOrNull { w.endsWith(it) && w.length - it.length >= 3 }?.let { w.dropLast(it.length) } ?: w
        return base in COLORS
    }

    /** Light German suffix stripping for matching only (never for display). */
    fun stem(word: String): String {
        var w = fold(word).trimEnd('\'', '’')
        for (suffix in listOf("innen", "ern", "en", "er", "es", "em", "e", "n", "s", "t")) {
            if (w.length - suffix.length >= 4 && w.endsWith(suffix)) { w = w.dropLast(suffix.length); break }
        }
        return w
    }

    fun groupsOf(word: String): Set<String> {
        val l = lower(word)
        return GROUPS.filterValues { l in it || (l.length >= 5 && it.any { g -> g.length >= 5 && stem(g) == stem(l) }) }.keys
    }

    fun numberValue(token: String): String? {
        val l = lower(token).trimEnd('.')
        NUMBER_WORDS[l]?.let { return it.toString() }
        return if (l.isNotEmpty() && l.all { it.isDigit() || it == '.' || it == ',' })
            l.replace(".", "").replace(",", ".").trimEnd('.') else null
    }
}
