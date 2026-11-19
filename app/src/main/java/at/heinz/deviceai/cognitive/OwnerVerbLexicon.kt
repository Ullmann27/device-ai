package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * One shared list of the verbs the owner gives orders with (review finding B5, 19.11.2026): the computer lane, the
 * mission planner's clause splitter and the coverage check of a model plan each had their own short list, so „und
 * schreib ihr Hallo“ was an order for one of them and search text for another.
 *
 * It only recognises that a clause STARTS an order and what kind of order it is – „schreib“, „schreibe“, „schreibst
 * du“, „kannst du … schreiben“, „fass … zusammen“, „ruf … an“, „leite … weiter“. It decides nothing: which lane takes
 * the clause, and whether it may run, is the lanes' own business. Recognising too much is the safe direction here – a
 * clause read as an order is never typed into a search field and never dropped without a word.
 */
internal object OwnerVerbLexicon {
    /** What kind of order a verb gives. [OTHER]: an order verb no owner lane has an ability for. */
    enum class Family { OPEN, SHOW, SEARCH, READ, LOOK, SUMMARISE, SAVE, CREATE, MAKE, SEND, WRITE, TELL, CALL, CHECK, CLEAN, DELETE, OTHER }

    /** The order verb of a clause: [word] as said, [range] inside the text that was asked about. */
    data class Verb(val word: String, val family: Family, val range: IntRange)

    /**
     * [stems]: the imperative without its ending – „schreib“ covers schreib, schreibe, schreibst (du).
     * [fixed]: irregular forms taken as they are. [infinitives]: for „kannst du … schreiben“ and „… zu schreiben“.
     * [particle]: with this word later in the clause the verb belongs to [particleFamily] („mach … auf“ opens).
     */
    private class Entry(val family: Family, val stems: List<String> = emptyList(), val fixed: List<String> = emptyList(),
                        val infinitives: List<String> = emptyList(), val particle: String? = null,
                        val particleFamily: Family? = null)

    private val verbs = listOf(
        Entry(Family.OPEN, fixed = listOf("öffne", "oeffne", "öffnest", "oeffnest", "öffnet", "oeffnet"),
            infinitives = listOf("öffnen", "oeffnen", "aufmachen")),
        Entry(Family.OPEN, fixed = listOf("wechsle", "wechsel"), infinitives = listOf("wechseln")),
        Entry(Family.OTHER, stems = listOf("start"), infinitives = listOf("starten")),
        Entry(Family.SHOW, stems = listOf("zeig", "hol"), infinitives = listOf("zeigen", "anzeigen", "holen", "ansehen", "sehen")),
        Entry(Family.SEARCH, stems = listOf("such"), infinitives = listOf("suchen", "heraussuchen", "raussuchen")),
        Entry(Family.SEARCH, stems = listOf("find"), infinitives = listOf("finden")),
        Entry(Family.READ, fixed = listOf("lies", "lese", "liest"), infinitives = listOf("lesen", "vorlesen", "durchlesen")),
        Entry(Family.LOOK, stems = listOf("schau", "guck"), fixed = listOf("sieh", "siehst"), infinitives = listOf("schauen",
            "durchschauen", "nachschauen", "anschauen", "gucken", "nachsehen", "durchsehen", "durchgehen"),
            particle = "nach", particleFamily = Family.SEARCH),
        Entry(Family.OTHER, fixed = listOf("geh", "gehe", "gehst"), particle = "durch", particleFamily = Family.LOOK),
        Entry(Family.SUMMARISE, fixed = listOf("fass", "fasse", "fasst"), infinitives = listOf("zusammenfassen")),
        Entry(Family.SUMMARISE, stems = listOf("analysier", "erklär", "erklaer", "erläuter", "erlaeuter", "beschreib"),
            infinitives = listOf("analysieren", "erklären", "erläutern", "beschreiben", "auswerten")),
        Entry(Family.SAVE, stems = listOf("speicher", "exportier"), fixed = listOf("sichere", "sicherst"),
            infinitives = listOf("speichern", "abspeichern", "sichern", "exportieren", "ablegen")),
        Entry(Family.CREATE, stems = listOf("erstell", "erzeug", "generier", "konvertier"), fixed = listOf("wandle", "wandel"),
            infinitives = listOf("erstellen", "erzeugen", "generieren", "konvertieren", "umwandeln", "anlegen")),
        Entry(Family.MAKE, stems = listOf("mach"), infinitives = listOf("machen"), particle = "auf", particleFamily = Family.OPEN),
        Entry(Family.SEND, stems = listOf("schick", "send", "mail", "post", "teil"), infinitives = listOf("schicken", "senden",
            "mailen", "posten", "teilen", "weiterleiten", "verschicken", "abschicken")),
        Entry(Family.SEND, fixed = listOf("leite", "leit", "leitest"), particle = "weiter", particleFamily = Family.SEND),
        Entry(Family.WRITE, stems = listOf("schreib", "tipp"), fixed = listOf("antworte", "antwortest", "antwort", "beantworte"),
            infinitives = listOf("schreiben", "tippen", "antworten", "beantworten")),
        Entry(Family.TELL, stems = listOf("sag", "nenn", "erzähl", "erzaehl"), fixed = listOf("gib", "gibst", "berichte"),
            infinitives = listOf("sagen", "nennen", "erzählen", "geben", "berichten")),
        Entry(Family.CALL, stems = listOf("ruf"), infinitives = listOf("anrufen", "rufen", "telefonieren")),
        Entry(Family.CHECK, stems = listOf("prüf", "pruef", "check", "kontrollier", "überprüf", "ueberpruef", "test"),
            infinitives = listOf("prüfen", "pruefen", "checken", "kontrollieren", "überprüfen", "testen")),
        Entry(Family.CLEAN, stems = listOf("bereinig", "räum", "raeum", "putz"), infinitives = listOf("bereinigen", "aufräumen",
            "aufraeumen", "putzen", "säubern")),
        Entry(Family.DELETE, stems = listOf("lösch", "loesch", "entfern"), fixed = listOf("wirf"), infinitives = listOf("löschen",
            "loeschen", "entfernen", "wegwerfen")),
        Entry(Family.OTHER, stems = listOf("spiel", "stopp", "schalt", "stell", "merk", "erinner", "notier", "übersetz", "uebersetz",
            "vergleich", "druck", "kauf", "bestell", "buch", "lad", "bring", "trag", "plan", "verschieb", "bearbeit",
            "kopier", "benenn", "installier", "aktualisier", "wähl", "waehl", "zähl", "zaehl", "rechn", "navigier", "setz",
            "füg", "fueg", "änder", "aender", "leg", "heb", "dreh", "scroll", "klick", "drück", "drueck", "halt",
            "mess", "wiederhol", "beend", "pausier", "füll", "fuell", "markier", "sortier", "verbind", "trenn",
            "aktivier", "deaktivier", "sperr", "entsperr", "fotografier", "film", "scann", "diktier", "lern"),
            fixed = listOf("nimm", "ordne", "übernimm", "rechne"),
            infinitives = listOf("spielen", "stoppen", "schalten", "stellen", "merken", "erinnern", "notieren", "übersetzen",
                "vergleichen", "drucken", "kaufen", "bestellen", "buchen", "laden", "bringen", "eintragen", "planen",
                "verschieben", "bearbeiten", "kopieren", "umbenennen", "installieren", "aktualisieren"))
    )

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    /** Words that may stand between a connector and the verb, or in front of the first verb. */
    private const val LEAD = "(?:(?:bitte|dann|danach|anschließend|anschliessend|zuerst|zunächst|zunaechst|erst|jetzt|mal|kurz|" +
        "noch|auch|gleich|außerdem|ausserdem|zuletzt|schließlich|schliesslich|und|zum\\s+schluss|als\\s+erstes)\\s+)*"
    private const val POLITE = "(?:kannst|könntest|koenntest|würdest|wuerdest|magst|willst|sollst|kannste)\\s+du|" +
        "(?:können|könnten|würden)\\s+sie|ich\\s+(?:möchte|moechte|will)\\s*,?\\s*dass\\s+du"

    private class Form(val family: Family, val entry: Entry)
    private val imperatives: Map<String, Form> = buildMap {
        for (entry in verbs) {
            val form = Form(entry.family, entry)
            entry.fixed.forEach { putIfAbsent(it, form) }
            // „schreib“, „schreibe“, „schreibst“ (du). The form with -t is left out on purpose: „macht“, „sucht“,
            // „findet“ are far more often said ABOUT someone than TO someone.
            entry.stems.forEach { stem -> listOf("", "e", "st", "est").forEach { putIfAbsent(stem + it, form) } }
        }
    }
    private val infinitives: Map<String, Form> = buildMap {
        for (entry in verbs) entry.infinitives.forEach { putIfAbsent(it, Form(entry.family, entry)) }
    }
    private val firstWord = Regex("(?iu)^($LEAD)([\\p{L}]+)$E")
    private val politeStart = Regex("(?iu)^$LEAD(?:$POLITE)$E")
    private val word = Regex("[\\p{L}]+")

    /**
     * Forms that are also everyday nouns („Suche“, „Teile“, „Sage“, „Stelle“, „Mail“, „Speicher“). After „und“ or a
     * comma inside other text they start an order only when they are written small AND go on like an order („such
     * nach …“, „teile das …“, „mail ihr …“); „Rechnung und Mail von Anna“ stays a search text.
     */
    private val alsoNouns = setOf("suche", "teil", "teile", "sage", "stelle", "ruf", "rufe", "mail", "post", "start",
        "plan", "druck", "test", "check", "spiel", "spiele", "buch", "buche", "kauf", "fass", "schau", "speicher", "antwort",
        "lade", "halt", "stopp", "tipp", "wechsel", "film", "mess", "leite", "trage", "sichere", "lese")
    private val orderGoesOn = Regex("(?iu)^\\s+(?:mir|uns|ihr|ihm|ihnen|es|sie|ihn|das|die|den|dem|der|dies\\p{L}*|" +
        "mein\\p{L}*|dein\\p{L}*|ein\\p{L}*|alle\\p{L}*|bitte|mal|dann|danach|nach|in|im|auf|an|zu|per|mit|" +
        "nichts|jetzt|kurz|noch|was|ob|wer|wie|wo|wann|welche\\p{L}*)$E")

    /**
     * The order verb [clause] starts with, or null: an imperative after courtesy and sequence words („bitte dann
     * schick …“), the same verb said to „du“ („schreibst du ihr dann“), or a polite request with the verb at its end
     * („kannst du mir das PDF öffnen“). A statement („ich schreibe gerade“) or a question word in front is no order.
     */
    fun leading(clause: String): Verb? {
        val text = clause.trimStart()
        val offset = clause.length - text.length
        firstWord.find(text)?.let { match ->
            val said = match.groupValues[2]
            val form = imperatives[said.lowercase(Locale.GERMAN)] ?: return@let
            val start = offset + match.groups[2]!!.range.first
            return Verb(said, family(form, text.substring(match.range.last + 1)), start until start + said.length)
        }
        if (politeStart.containsMatchIn(text)) {
            // The last infinitive of the request carries the order: „kannst du Anna bitte kurz schreiben“.
            word.findAll(text).toList().asReversed().forEach { match ->
                val form = infinitives[match.value.lowercase(Locale.GERMAN)] ?: return@forEach
                return Verb(match.value, form.family, offset + match.range.first..offset + match.range.last)
            }
        }
        return null
    }

    private fun family(form: Form, rest: String): Family {
        val particle = form.entry.particle ?: return form.family
        return if (Regex("(?iu)$S$particle$E").containsMatchIn(rest)) form.entry.particleFamily ?: form.family else form.family
    }

    /** true = [clause] starts with an order verb. */
    fun startsOrder(clause: String): Boolean = leading(clause) != null

    private val furtherLead = Regex("(?iu)(?:\\s*[,;]\\s*(?:und\\s+)?|\\s*[.!?]\\s+(?:und\\s+)?|\\s+und\\s+|" +
        "\\s+(?=(?:dann|danach|anschließend|anschliessend)\\s))")

    /**
     * Where a further order begins inside [text] – a search query, the words after an app name, the name of a
     * recipient: the position of the connector („und“, „dann“, „danach“, „anschließend“, „und dann“, a comma or a
     * sentence end) that is followed by an imperative. Null when the text holds no second order: „Anna und Bernd“,
     * „Rechnung und Mahnung“ stay what they are.
     */
    fun furtherOrderAt(text: String): Int? {
        for (match in furtherLead.findAll(text)) {
            val after = text.substring(match.range.last + 1)
            val verb = leading(after) ?: continue
            val sentenceEnd = match.value.any { it == '.' || it == '!' || it == '?' }
            val polite = politeStart.containsMatchIn(after.trimStart())
            if (!sentenceEnd && !polite && verb.word.lowercase(Locale.GERMAN) in alsoNouns) {
                val behind = after.substring(minOf(after.length, verb.range.last + 1))
                if (verb.word.first().isUpperCase() || !orderGoesOn.containsMatchIn(behind)) continue
            }
            return match.range.first
        }
        return null
    }

    /** true = [text] carries a second order behind its own first words. */
    fun hasFurtherOrder(text: String): Boolean = furtherOrderAt(text) != null

    /**
     * Every kind of order whose verb occurs anywhere in [sentence], in any form („speichere“, „gespeichert“ is not
     * one, „abspeichern“ is). Used to check that a plan asks for nothing the owner's sentence has no verb for.
     */
    fun families(sentence: String): Set<Family> {
        val found = HashSet<Family>()
        var first = true
        // A particle belongs to its verb only inside the same clause: „mach daraus ein PDF und schick es auf WhatsApp“
        // makes something, it opens nothing.
        for (clause in sentence.split(clauseEnd)) {
            val said = word.findAll(clause).toList()
            val words = said.map { it.value.lowercase(Locale.GERMAN) }
            words.forEachIndexed { index, lower ->
                val form = imperatives[lower] ?: infinitives[lower] ?: return@forEachIndexed
                // „den Speicher“, „eine Mail“: a capitalised noun inside the sentence is not the verb.
                if (!(first && index == 0) && said[index].value.first().isUpperCase() && lower in alsoNouns) return@forEachIndexed
                // „Mach das PDF auf“ opens; it does not make anything.
                val particle = form.entry.particle?.takeIf { it in words.drop(index + 1) }
                found += if (particle != null) form.entry.particleFamily ?: form.family else form.family
            }
            if (said.isNotEmpty()) first = false
        }
        return found
    }

    private val clauseEnd = Regex("(?iu)[,;.!?:]|${S}(?:und|oder|dann|danach|anschließend|anschliessend)$E")
}
