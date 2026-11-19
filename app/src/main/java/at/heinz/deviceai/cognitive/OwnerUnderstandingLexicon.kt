package at.heinz.deviceai.cognitive

import java.text.Normalizer
import java.util.Locale

/**
 * The bounded set of names Continuum really knows in this moment: the fixed apps of the computer lane, the
 * products and models the app itself talks about, its own features, the launcher labels of the installed apps,
 * the documents of this chat and the salient nouns of the last owner turns. It is the only repair target of
 * [OwnerUnderstanding]: a word that is not in here can never be „understood“ into an utterance.
 *
 * No contacts, no file contents, no memory records. Everything is held in memory for one owner turn.
 */
internal class OwnerUnderstandingLexicon private constructor(
    val entries: List<Entry>,
    private val knownTokens: Set<String>,
    private val salient: Set<String>
) {
    enum class Kind { APP, PRODUCT, MODEL, ASSISTANT, FEATURE, DOCUMENT, DIALOGUE }

    /**
     * [heardAs]: spellings a German recogniser really produces for a name it does not know (mostly English names).
     * [distinctive]: a product name without German look-alikes; it may be repaired outside a name slot.
     * [contextual]: valid German words that mean this name only when the utterance also matches the context.
     */
    data class Entry(val name: String, val kind: Kind, val heardAs: List<String> = emptyList(),
                     val distinctive: Boolean = false, val contextual: List<Contextual> = emptyList()) {
        val key: String = letters(name)
        val words: List<String> = name.split(Regex("[\\s-]+")).filter { it.isNotBlank() }
    }

    data class Contextual(val word: String, val context: Regex)

    /** A word that must never be repaired: a lexicon name, a frequent German or Austrian word, a recent noun. */
    fun isKnownWord(token: String): Boolean {
        val lower = token.lowercase(Locale.GERMAN)
        return lower in knownTokens || lower in VALID_WORDS || lower in salient || inflectionOfValid(lower)
    }

    fun isName(token: String): Boolean = token.lowercase(Locale.GERMAN) in knownTokens

    fun mentionedRecently(entry: Entry): Boolean = entry.words.any { it.lowercase(Locale.GERMAN) in salient }

    companion object {
        const val MAX_APPS = 400
        const val MAX_DOCUMENTS = 16
        const val MAX_TURNS = 6
        const val MAX_SALIENT = 32

        fun letters(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        private fun inflectionOfValid(lower: String): Boolean =
            listOf("en", "es", "er", "em", "e", "n", "s", "st", "t", "te", "ten").any { ending ->
                lower.length - ending.length >= 3 && lower.endsWith(ending) && lower.dropLast(ending.length) in VALID_WORDS
            }

        private const val B = "(?<![\\p{L}\\p{N}])"
        private const val E = "(?![\\p{L}\\p{N}])"
        /** „Gamma 4“, „Gamma E4B“, „das Modell Gamma“, „Gamma ist geladen“ – never the Greek letter on its own. */
        private val modelContext = Regex("(?iu)${B}gamma[\\s-]*(?:\\d|e[24]b$E)|" +
            "$B(?:modell\\p{L}*|sprachmodell|ki-modell|litert)$E.{0,40}${B}gamma$E|" +
            "${B}gamma$E.{0,24}$B(?:modell\\p{L}*|sprachmodell|geladen|laden|lade|läuft|litert)$E")
        /** „Öffne Wort“ / „die App Wort“: nothing but the app can be meant. */
        private val openContext = Regex("(?iu)^(?:\\P{L}*\\p{L}+[,:]\\s+)?(?:bitte\\s+)?(?:öffne|oeffne|starte)\\s+" +
            "(?:mir\\s+|mal\\s+|bitte\\s+)*(?:(?:die|das)\\s+)?(?:app\\s+)?wort(?:\\s+bitte)?[.!?]*$|" +
            "${B}wort[\\s-]+app$E|${B}app\\s+wort$E")

        /** Names the app itself uses. Spoken variants come from the owner's dictated messages and the app's own UI. */
        val STATIC: List<Entry> = listOf(
            Entry("WhatsApp", Kind.APP, listOf("watsapp", "wotsapp", "wozapp", "whatsup", "wazzap", "wasapp",
                "wottsepp", "watsäpp", "wotsäp", "was app", "whats up", "wots app", "wats app"), distinctive = true),
            Entry("Facebook", Kind.APP, listOf("fäisbuk", "feisbuk", "fesbuk", "faceboock", "fäsbuk")),
            Entry("Eigene Dateien", Kind.APP),
            Entry("Dateien", Kind.APP),
            Entry("Word", Kind.APP, listOf("wörd", "wört"), contextual = listOf(Contextual("wort", openContext))),
            Entry("Excel", Kind.APP, listOf("exel", "exzel", "eksel", "exell")),
            Entry("Chrome", Kind.APP, listOf("krom", "kroom", "chrom", "crom", "kroum", "krohm", "krome")),
            Entry("Einstellungen", Kind.APP),
            Entry("Kamera", Kind.APP),
            Entry("Galerie", Kind.APP),
            Entry("Kalender", Kind.APP),
            Entry("Gmail", Kind.APP, listOf("dschimeil", "tschimeil", "gimeil", "gemail", "gmehl", "dschiemail",
                "dschimail", "g mail", "ge mail", "dschi mail", "jimail"), distinctive = true),
            Entry("YouTube", Kind.APP, listOf("jutjub", "jutub", "youtub", "jutube", "jutiub", "you tube", "ju tjub"),
                distinctive = true),
            Entry("Termux", Kind.PRODUCT, listOf("thermux", "termuks", "tehrmux", "termox", "thermox", "termucks",
                "ter mux", "term ux"), distinctive = true),
            Entry("Shizuku", Kind.PRODUCT, listOf("schisuku", "schizuku", "shisuku", "schisuko", "shizuko", "schisuka",
                "schi suku", "tschisuku"), distinctive = true),
            Entry("Downloads", Kind.FEATURE, listOf("daunlods", "daunloads", "downlods", "daunlots", "daunlodz",
                "down loads", "daun lods")),
            Entry("Play Store", Kind.APP, listOf("pleistor", "pläystore", "playstor", "plei stor", "play stor")),
            Entry("Telegram", Kind.APP),
            Entry("Signal", Kind.APP),
            Entry("Spotify", Kind.APP, listOf("spotifei", "spottifei", "spotifai")),
            Entry("Firefox", Kind.APP, listOf("feierfox", "faierfox", "feuerfox")),
            Entry("Outlook", Kind.APP, listOf("autluk", "outluk", "autlook")),
            Entry("Gemma", Kind.MODEL, listOf("gema", "gemmer", "dschemma", "jemma"),
                contextual = listOf(Contextual("gamma", modelContext))),
            Entry("Claude", Kind.MODEL, listOf("klod", "klohd", "claud", "klaud", "clode", "klood", "klohde")),
            Entry("Gemini", Kind.MODEL, listOf("dschemini", "tschemini", "geminei")),
            Entry("LiteRT", Kind.MODEL),
            Entry("Jarvis", Kind.ASSISTANT, listOf("javis", "dschawis", "tscharvis", "dscharvis", "jarwis", "jawis",
                "charvis", "dschavis", "tschawis"), distinctive = true),
            Entry("Continuum", Kind.ASSISTANT, listOf("kontinuum", "continum", "kontinum", "kontinjuum", "continium")),
            Entry("Akku", Kind.FEATURE),
            Entry("Batterie", Kind.FEATURE),
            Entry("Speicher", Kind.FEATURE),
            Entry("Speicherplatz", Kind.FEATURE),
            Entry("Netzwerk", Kind.FEATURE),
            Entry("WLAN", Kind.FEATURE, listOf("welan", "wehlan", "weelan", "we lan", "w lan")),
            Entry("Bluetooth", Kind.FEATURE, listOf("blutuf", "blutut", "blutus", "bluetuth", "blutuhs", "blu tuf")),
            Entry("Screenshot", Kind.FEATURE, listOf("skrinschot", "screenschot", "skrienshot", "skrinshot",
                "skrienschot", "screen shot", "skrin schot")),
            Entry("Logcat", Kind.FEATURE, listOf("lokkat", "logkat", "lockcat", "logkett", "log cat", "lok kat")),
            Entry("Websuche", Kind.FEATURE),
            Entry("Recherche", Kind.FEATURE),
            Entry("Diagnose", Kind.FEATURE),
            Entry("Gedächtnis", Kind.FEATURE),
            Entry("Fahrmodus", Kind.FEATURE),
            Entry("Gesprächsmodus", Kind.FEATURE),
            Entry("Lernzentrum", Kind.FEATURE),
            Entry("Systemzentrum", Kind.FEATURE),
            Entry("Modellverwaltung", Kind.FEATURE),
            Entry("APK", Kind.FEATURE, listOf("apeka", "a p k", "ape ka")),
            Entry("PDF", Kind.FEATURE, listOf("pedeef", "p d f", "pe de ef")),
            Entry("Update", Kind.FEATURE, listOf("abdeit", "apdeit", "updeit", "apdäit", "ab deit")),
            Entry("Backup", Kind.FEATURE, listOf("bäckap", "beckap", "bekap", "bäck ap"))
        )

        /**
         * [installedApps]: launcher labels as the app-lookup path resolves them. [documents]: names of the documents of
         * this chat. [recentOwnerTurns]: the last owner turns, newest last; only their capitalised nouns are kept.
         */
        fun build(installedApps: List<String> = emptyList(), documents: List<String> = emptyList(),
                  recentOwnerTurns: List<String> = emptyList(), assistantName: String? = null): OwnerUnderstandingLexicon {
            val entries = ArrayList<Entry>(STATIC)
            val seen = STATIC.mapTo(HashSet()) { it.key }
            fun add(name: String, kind: Kind) {
                val clean = name.filterNot { it.isISOControl() }.replace(Regex("\\s+"), " ").trim()
                if (clean.length !in 3..40 || !clean.any { it.isLetter() }) return
                val entry = Entry(clean, kind)
                if (entry.key.length >= 3 && seen.add(entry.key)) entries += entry
            }
            assistantName?.let { add(it, Kind.ASSISTANT) }
            installedApps.asSequence().take(MAX_APPS).forEach { add(it, Kind.APP) }
            documents.asSequence().take(MAX_DOCUMENTS).forEach { name ->
                add(name.substringBeforeLast('.').replace(Regex("[_\\-]+"), " ").replace(Regex("\\d+"), " "), Kind.DOCUMENT)
            }
            val salient = LinkedHashSet<String>()
            recentOwnerTurns.takeLast(MAX_TURNS).forEach { turn ->
                NOUN.findAll(turn.take(600)).forEach { match ->
                    val word = match.value
                    val lower = word.lowercase(Locale.GERMAN)
                    if (salient.size < MAX_SALIENT && lower !in VALID_WORDS) {
                        salient += lower
                        add(word, Kind.DIALOGUE)
                    }
                }
            }
            val known = HashSet<String>()
            entries.forEach { entry -> entry.words.forEach { known += it.lowercase(Locale.GERMAN) } }
            return OwnerUnderstandingLexicon(entries, known, salient)
        }

        /** A capitalised word of at least four letters that is not the first word of its sentence. */
        private val NOUN = Regex("(?<=[\\p{L},;] )\\p{Lu}[\\p{L}]{3,23}")

        /**
         * Frequent German and Austrian colloquial words. A word in here is never repaired, whatever it sounds like.
         * The list is a guard, not a dictionary: a rarer valid word is protected by the name-slot rule and by the
         * requirement of an equal sound key instead.
         */
        val VALID_WORDS: Set<String> = (
            "ab aber alle allem allen aller alles als also alt alte am an andere anderen anders app apps auch auf aus " +
            "außer bald bei beim bereits besser beste bevor bin bis bisher bitte bleiben bleibt brauche brauchen braucht " +
            "da dabei dadurch dafür dagegen daher damit danach dann daran darauf daraus darf darin darum das dass datei " +
            "dateien dein deine deinem deinen deiner dem den denn der des deshalb dessen dich die dies diese diesem " +
            "diesen dieser dieses dir doch dort drei du durch dürfen ein eine einem einen einer eines einfach einmal " +
            "er erst erste ersten es etwas euch euer eure fall fast fertig für ganz gar geben gegen gehen geht gemacht " +
            "genau gerade gern gerne gestern gibt gleich gut gute guten hab habe haben habt hallo hast hat hatte hatten " +
            "heute hier hin hinter ich ihm ihn ihnen ihr ihre ihrem ihren ihrer im immer in ins ist ja jede jedem jeden " +
            "jeder jedes jetzt kann kannst kein keine keinem keinen keiner keines klar klein kommen kommt können könnte " +
            "könntest kurz lang lange lass lassen letzte letzten lieber machen macht mal man manche mehr mein meine " +
            "meinem meinen meiner meines mich mir mit möchte möchtest morgen muss müssen musst nach nachdem nächste " +
            "nächsten nein neu neue neuen neuer neues neueste neuesten nicht nichts nie niemals noch nun nur ob oben " +
            "oder ohne schon sehr sei seid sein seine seinem seinen seiner seit selbst sich sie sind so sofort soll " +
            "sollen sollte sollst sondern später statt über um und uns unser unsere unter viel viele vielleicht vom von " +
            "vor vorher wann war waren warum was weg weil weiter welche welchem welchen welcher welches wenig wenn wer " +
            "werde werden wie wieder will willst wir wird wo wohl wollen wollte worden würde würdest zu zum zur zurück " +
            "zusammen zwei zwischen " +
            // command verbs and their objects
            "öffne oeffne öffnen starte starten start mach mache machst zeig zeige zeigen such suche suchen find finde " +
            "finden prüf prüfe prüfen überprüfe check checke schließ schließe schließen beende beenden stopp stoppe " +
            "stoppen halt installiere installier installieren deinstalliere deinstallieren aktualisiere aktualisieren " +
            "wechsle wechsel wechseln geh gehe schreib schreibe schreiben sende senden schick schicke schicken lies " +
            "lese lesen sag sage sagen frag frage fragen erstelle erstellen speicher speichere speichern lösch lösche " +
            "löschen lade laden hol hole holen ruf rufe rufen spiel spiele spielen merk merke nenne nennen erkläre " +
            "erklären hilf hilfe helfen warte warten antworte antworten mess messe messen miss teste testen test " +
            "aufmachen zumachen anmachen ausmachen runterladen herunterladen downloaden downloade hochladen " +
            "nachricht nachrichten ordner verzeichnis dokument dokumente bild bilder foto fotos video videos musik " +
            "handy telefon gerät geräte smartphone tablet bildschirm seite fenster text wort wörter satz name namen " +
            "version versionen nummer zahl größe groß große kleiner größer neuer älter alt alte liste ergebnis " +
            "ergebnisse fehler problem probleme auftrag aufgabe aufgaben schritt schritte status stand zeit uhr uhrzeit " +
            "datum tag tage woche wochen monat jahr stunde minute sekunde prozent internet netz web suche antwort " +
            "frage hilfe info infos information informationen daten wetter karte karten kontakt kontakte anruf " +
            "mail mails email e-mail post brief termin termine kamera galerie kalender einstellungen rechner notiz " +
            "notizen wecker timer licht ton laut leise lautstärke helligkeit sprache stimme mikrofon lautsprecher " +
            "modell modelle programm programme anwendung anwendungen system zentrum bereich ansicht übersicht " +
            "entwurf zusammenfassung bericht tabelle rechnung vertrag angebot plan pläne idee ideen projekt projekte " +
            "arbeit haus auto weg straße stadt land welt leute mann frau kind kinder freund freunde familie chef " +
            "kollege kollegen kunde kunden firma geld euro preis kosten bank konto essen trinken wasser kaffee " +
            "schlafen arbeiten fahren laufen reden hören sehen schauen denken wissen glauben verstehen verstanden " +
            "kammer vetter gamma krume kram kran krone klaut therme thermik termin gemahl wald wand wort wart cloud " +
            "installiert geöffnet gestartet gespeichert gelöscht gefunden gesucht geschrieben gesendet geschickt " +
            "erledigt offen zu an aus ein fertig bereit aktiv aktuell lokal online offline frei voll leer schnell " +
            "langsam richtig falsch wichtig möglich nötig sicher unsicher zuerst zuletzt danke gell passt super prima " +
            "okay ok servus tschüss morgens abends nachts mittags links rechts unten vorne hinten " +
            // Austrian colloquial forms the recogniser passes through
            "a i is ma ned net nit des wos hob hod hot oba eh bissl bissal gscheit leiwand gö tua tuast moch mochst " +
            "mochs kumm kummst schau schaust sog sogst san samma hamma kemma kennst konnst wü wüst mog donn jetz jo na " +
            "griaß pfiat büdl büdln oida heast eam eana wia woa warat hätt tät dad mecht eini aufi obi ummi aussi auffe " +
            "owe zua mochn schaun gemma gemmas daham dahoam heit gestan muagn glei goa nix nimma no amoi amol wieda " +
            "aufmochn zuamochn schick schickst sads bist seids kannst kaunst kaun muaß muasst deaf deafst wüllst"
            ).split(' ').filter { it.isNotBlank() }.toSet()
    }
}
