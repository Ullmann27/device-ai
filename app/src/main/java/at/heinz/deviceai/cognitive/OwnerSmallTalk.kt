package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * The few turns of small talk the app answers itself (owner wish 19.11.2026: „freundlich, kompetent, menschlich,
 * gelegentlich humorvoll – ohne vorzugeben, ein Mensch zu sein“): his answer to „Wie geht's dir?“, the question back,
 * a thank-you, „warte kurz“, a goodbye. Fixed sentences, no model turn.
 *
 * What these sentences may say about the app is a statement about a system („Bei mir läuft alles rund.“) – never a
 * feeling, a mood or an experience of its own. Courtesy the owner uses himself („Schön, dass du da bist“) is allowed;
 * „Ich freue mich“, „Mir geht es gut“ or „Ich bin auch müde“ are not, and [claimsFeeling] is the check every sentence
 * of this file has to pass in the tests.
 */
internal object OwnerSmallTalk {
    enum class Kind {
        /** He asks how the app is doing. */
        ASKS_BACK,
        GOOD, GOOD_AND_ASKS_BACK, TIRED, SO_SO, BAD,
        THANKS,
        /** „Warte kurz“ – nothing is decided; an open offer stays open. */
        HOLD,
        /** „Vergiss es“ – only an answer while an offer of the app is open. */
        DISMISS,
        FAREWELL
    }

    private const val YOU = "(?:dir|du|bei dir|selbst|dir so|dir heute)"
    private const val LEAD = "(?:(?:na|und|hey|hallo|hi|ach|oh|ja|naja|na ja|also|ok|okay|danke|ganz|eigentlich|heute|gerade|sag mal|sag) )*"
    private val asksBack = listOf(
        Regex("^${LEAD}wie geht(?: es|s)?(?: $YOU)?(?: so)?(?: denn)?$"),
        Regex("^${LEAD}wie gehts(?: $YOU)?(?: so)?(?: denn)?$"),
        Regex("^${LEAD}(?:und )?(?:wie )?(?:läuft|laufts|läufts)(?: es|s)?(?: $YOU| bei dir)?(?: so)?$"),
        Regex("^${LEAD}alles (?:klar|gut|fit|okay|ok|in ordnung)(?: $YOU| bei dir)$"),
        Regex("^${LEAD}(?:und )?(?:wie (?:sieht es|siehts|schaut es|schauts) )?bei dir(?: so)?(?: aus)?$"),
        Regex("^(?:und )?(?:dir|selbst|du so|bei dir)$"))
    private const val GOOD_WORD = "(?:gut|sehr gut|ganz gut|super|bestens|prima|top|bombe|ausgezeichnet|hervorragend|" +
        "passt|passt alles|alles gut|alles bestens|alles klar|alles fit|bin fit|fit|läuft|läuft gut|kann nicht klagen|" +
        "mir geht(?: es|s)? gut|mir gehts gut|gut gut|gut danke|danke gut|ganz ok|ganz okay|okay|ok)"
    private val good = Regex("^${LEAD}$GOOD_WORD(?: danke)?(?: soweit)?$")
    private val goodAndBack = Regex("^${LEAD}$GOOD_WORD(?: danke)?(?: soweit)?,? (?:und )?(?:$YOU|wie geht(?: es|s)? $YOU|wie gehts $YOU)$")
    private val tired = Regex("^${LEAD}(?:ich bin |bin |ich fühl mich |bisschen |ziemlich |total |echt |noch |so |sehr |etwas )*" +
        "(?:müde|kaputt|erledigt|geschafft|platt|ko|fix und fertig|fertig|erschöpft|ausgelaugt|schläfrig|verschlafen)$")
    private val soSo = Regex("^${LEAD}(?:geht so|es geht|geht schon|geht|naja|na ja|muss ja|muss|so lala|mittel|mittelprächtig|" +
        "durchwachsen|passt schon|nicht schlecht|könnte besser sein|war schon besser|wie immer)$")
    private val bad = Regex("^${LEAD}(?:mir geht(?: es|s)? |mir gehts |ich bin |bin |eher |gar |so |ziemlich |echt |total )*" +
        "(?:schlecht|nicht gut|nicht so gut|mies|bescheiden|beschissen|gestresst|im stress|viel stress|stress|krank|" +
        "angeschlagen|genervt|nicht besonders|nicht so toll)$")
    private val thanks = Regex("^(?:(?:ok|okay|super|perfekt|passt|gut|alles klar|vielen|herzlichen|besten|tausend|recht) )*" +
        "(?:danke|dank|dankeschön|danke schön|danke dir|danke sehr|merci|thx|dankschön|vergelts gott)" +
        "(?: dir| schön| sehr| vielmals| dafür| nochmal| erstmal| das wars| das war alles| das passt)*$")
    private val hold = Regex("^(?:(?:ja|ok|okay|äh|ähm|hm) )*(?:warte|wart|warte mal|wart mal|warte kurz|wart kurz|warte bitte|moment|" +
        "moment mal|einen moment|einen moment bitte|moment bitte|momenterl|sekunde|eine sekunde|kurz warten|gleich|" +
        "nicht so schnell|lass mich kurz überlegen|ich überlege|ich überleg noch)$")
    private val dismiss = Regex("^(?:(?:ach|nein|nee|ne|ok|okay) )*(?:vergiss es|vergiss das|egal|ist egal|schon gut|lass gut sein|" +
        "lass mal|lass es|lass|hat sich erledigt|nicht nötig|brauche ich nicht|brauch ich nicht|ein andermal|ein anderes mal)$")
    private val farewell = Regex("^(?:(?:ok|okay|so|na dann|dann|also|gut|danke) )*(?:gute nacht|schlaf gut|bis später|bis morgen|" +
        "bis dann|bis gleich|bis bald|tschüss|tschüs|tschau|ciao|pfiat di|baba|machs gut|mach es gut|ich bin dann weg|" +
        "ich bin dann mal weg|ich gehe jetzt schlafen|ich geh jetzt schlafen|ich gehe schlafen|ich geh schlafen|schönen abend|" +
        "schönen abend noch|schönen tag|schönen tag noch)$")

    private fun normal(utterance: String, assistantName: String?): String {
        val names = (listOf("jarvis", "continuum") + listOfNotNull(assistantName?.lowercase(Locale.GERMAN)))
            .flatMap { it.split(' ') }.filter { it.isNotBlank() }.toSet()
        return utterance.lowercase(Locale.GERMAN).replace(Regex("['’`´]"), "").replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .split(' ').filter { it.isNotEmpty() && it !in names }.joinToString(" ")
    }

    /**
     * The kind of small talk [utterance] is, or null. [afterGreeting]: the app's last line was its greeting with a
     * question how he is – only then a bare „gut“ or „müde“ is his answer; anywhere else such a word keeps its route.
     * [offerOpen]: an offer of the app waits for his answer – only then „warte“ and „vergiss es“ are taken.
     */
    fun parse(utterance: String, assistantName: String? = null, afterGreeting: Boolean = false, offerOpen: Boolean = false): Kind? {
        if (utterance.length !in 2..70 || utterance.any { it.isISOControl() }) return null
        val text = normal(utterance, assistantName)
        if (text.isEmpty() || text.split(' ').size > 9) return null
        if (thanks.matches(text)) return Kind.THANKS
        if (farewell.matches(text)) return Kind.FAREWELL
        if (asksBack.dropLast(1).any { it.matches(text) }) return Kind.ASKS_BACK
        if (offerOpen || afterGreeting) {
            if (hold.matches(text)) return Kind.HOLD
        }
        if (offerOpen && dismiss.matches(text)) return Kind.DISMISS
        if (afterGreeting) {
            if (asksBack.last().matches(text)) return Kind.ASKS_BACK
            if (goodAndBack.matches(text)) return Kind.GOOD_AND_ASKS_BACK
            if (good.matches(text)) return Kind.GOOD
            if (tired.matches(text)) return Kind.TIRED
            if (soSo.matches(text)) return Kind.SO_SO
            if (bad.matches(text)) return Kind.BAD
        }
        return null
    }

    private val pools: Map<Kind, List<String>> = mapOf(
        Kind.ASKS_BACK to listOf(
            "Bei mir läuft alles rund. Und bei dir?",
            "Alles im grünen Bereich – die Systeme laufen. Was steht bei dir an?",
            "Hier läuft alles stabil. Womit kann ich dir helfen?",
            "Soweit alles in Ordnung auf meiner Seite. Was hast du vor?"),
        Kind.GOOD to listOf(
            "Das hört man gern. Was steht an?",
            "Sehr gut. Womit fangen wir an?",
            "Na dann – was packen wir an?",
            "Prima. Was hast du heute vor?"),
        Kind.GOOD_AND_ASKS_BACK to listOf(
            "Das hört man gern. Bei mir läuft alles rund. Womit fangen wir an?",
            "Sehr gut. Hier ist auch alles im grünen Bereich – die Systeme laufen. Was steht an?",
            "Prima. Auf meiner Seite läuft alles stabil. Was packen wir an?"),
        Kind.TIRED to listOf(
            "Dann machen wir es heute kurz. Was brauchst du?",
            "Verstehe – ich halte mich kurz. Womit kann ich dir etwas abnehmen?",
            "Dann nur das Nötigste. Was soll ich dir abnehmen?"),
        Kind.SO_SO to listOf(
            "Verstehe. Sag einfach, womit ich dir etwas abnehmen kann.",
            "Alles klar. Was würde dir jetzt am meisten helfen?",
            "Okay. Dann schauen wir, was ich dir heute abnehmen kann – womit fangen wir an?"),
        Kind.BAD to listOf(
            "Das klingt nach einem schweren Tag. Sag mir, was ich dir abnehmen kann.",
            "Verstanden. Dann halte ich es einfach – womit kann ich dich entlasten?",
            "Okay, dann ohne Umwege: Was soll ich dir heute abnehmen?"),
        Kind.THANKS to listOf("Gern.", "Jederzeit.", "Kein Thema.", "Gern geschehen.", "Dafür bin ich da."),
        Kind.HOLD to listOf("Alles klar, ich warte.", "Kein Stress.", "In Ordnung, sag einfach Bescheid.", "Lass dir Zeit."),
        Kind.DISMISS to listOf("In Ordnung.", "Alles klar, ist vom Tisch.", "Gut, dann nicht."),
        Kind.FAREWELL to listOf("Bis später.", "Mach's gut.", "Bis dann."))

    /** One reply. [index]: the wording used. */
    data class Said(val text: String, val index: Int)

    /**
     * The reply to [kind]. [name]: how the owner is addressed, or null. [variation]: any counter; [previous]: the
     * wording used last time for this kind (-1 = none) – never the same twice in a row. [said]: his own words, so a
     * goodbye answers what he said („Gute Nacht“ gets „Gute Nacht“).
     */
    fun reply(kind: Kind, name: String?, variation: Int, previous: Int = -1, said: String = ""): Said {
        if (kind == Kind.FAREWELL) farewellEcho(said, name)?.let { return Said(it, -1) }
        val pool = pools.getValue(kind)
        var pick = ((variation % pool.size) + pool.size) % pool.size
        if (pool.size > 1 && pick == previous) pick = (pick + 1) % pool.size
        val text = pool[pick]
        // The name now and then, not in every line: with the second wording of a kind.
        return Said(if (name != null && pick == 1 && kind in setOf(Kind.GOOD, Kind.FAREWELL, Kind.THANKS)) text.trimEnd('.', '?').let {
            if (text.endsWith("?")) "$it, $name?" else "$it, $name."
        } else text, pick)
    }

    private fun farewellEcho(said: String, name: String?): String? {
        val text = said.lowercase(Locale.GERMAN)
        val to = name?.let { ", $it" }.orEmpty()
        return when {
            text.contains("gute nacht") || text.contains("schlaf") -> "Gute Nacht$to."
            text.contains("bis morgen") -> "Bis morgen$to."
            text.contains("bis gleich") -> "Bis gleich."
            text.contains("schönen abend") -> "Dir auch einen schönen Abend$to."
            text.contains("schönen tag") -> "Dir auch einen schönen Tag$to."
            else -> null
        }
    }

    private val feeling = Regex("(?iu)(?<![\\p{L}])(?:ich freue mich|freut mich|freue mich|mir geht(?:'|’)?s|mir geht es|ich fühle|ich fühl mich|" +
        "ich bin (?:auch |so |sehr |total |echt )*(?:froh|glücklich|traurig|müde|erschöpft|gut drauf|aufgeregt|stolz|einsam|gelangweilt)|" +
        "ich habe dich vermisst|hab dich vermisst|ich vermisse|ich liebe|ich hasse|macht mich (?:glücklich|traurig|stolz)|" +
        "ich hatte (?:einen|eine) (?:guten|schönen|schlechten|langen) (?:tag|nacht|abend)|ich habe (?:gut |schlecht )?geschlafen|" +
        "bin (?:ich )?ein mensch|als mensch)(?![\\p{L}])")

    /** true = [text] claims a feeling, a mood or a life of its own. No sentence of the app may. */
    fun claimsFeeling(text: String): Boolean = feeling.containsMatchIn(text)

    /** Every fixed sentence of this object, for the checks. */
    fun allSentences(): List<String> = pools.values.flatten() + listOf("Gute Nacht.", "Bis morgen.", "Bis gleich.",
        "Dir auch einen schönen Abend.", "Dir auch einen schönen Tag.")
}

/**
 * The one short line when the app starts longer work on a clear order (owner wish 19.11.2026: „Verstanden. Ich kümmere
 * mich darum.“ instead of „Bitte teile deinen Auftrag in einzelne Befehle.“). Only where the lane does not announce
 * itself and only once the work has really started – the caller decides both; this object only varies the wording.
 */
internal object OwnerAcknowledgement {
    private val pool = listOf("Verstanden. Ich kümmere mich darum.", "Mach ich.", "Bin dran.", "Alles klar, ich fange an.",
        "Geht klar.", "Wird erledigt.")

    data class Said(val text: String, val index: Int)

    fun line(variation: Int, previous: Int = -1): Said {
        var pick = ((variation % pool.size) + pool.size) % pool.size
        if (pick == previous) pick = (pick + 1) % pool.size
        return Said(pool[pick], pick)
    }

    fun allSentences(): List<String> = pool
}
