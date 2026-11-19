package at.heinz.deviceai.deepclean

import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import java.util.Locale

/**
 * What the owner asked of the storage lane („Speicher & Bereinigung“). The modes are related but carry different
 * rights: only [SAFE_CLEAN], [DEEP_CLEAN] and [TARGETED] may ever reach a delete call, and of those only the first two
 * remove anything without a further confirmation – and then only GREEN items (see [DeepCleanClassifier]).
 */
internal enum class DeepCleanMode {
    /** Inspect and report; never deletes. */
    ANALYSE,
    /** Thorough search for storage hogs, duplicates, large files; never deletes. */
    DEEP_ANALYSE,
    /** After an explicit clean-up order: removes GREEN items only. */
    SAFE_CLEAN,
    /** GREEN items plus the deep analysis; YELLOW findings are offered for selection, never removed by themselves. */
    DEEP_CLEAN,
    /** Temperature, storage, memory and Continuum's own load; never deletes because of the word „optimieren“. */
    OPTIMISE,
    /** Files or groups the owner named exactly: select, show, confirm, then trash. */
    TARGETED,
    /** How much space the last run really freed (measured). */
    REPORT,
    /** Put back what the last runs moved to Android's trash. */
    RESTORE,
    /** Open the native screen „Speicher & Bereinigung“. */
    PANEL,
    /** „Bereinige mein Handy nicht“: nothing is done. */
    DECLINE,
    /** „Räum auf“, „Lösch alles“: not clear enough to act – the lane asks back. */
    CLARIFY,
    /** „Schlag mir wieder alles vor“: forget the protections learned from earlier refusals. */
    FORGET_PROTECTION;

    /** true = this mode may at all lead to a removal; everything else is read-only by construction. */
    val mayRemove: Boolean get() = this == SAFE_CLEAN || this == DEEP_CLEAN || this == TARGETED
}

/** Groups the owner can name in an order or a restriction. */
internal enum class DeepCleanGroup(val label: String) {
    PHOTOS("Fotos"), VIDEOS("Videos"), AUDIO("Musik und Aufnahmen"), DOWNLOADS("Downloads"), DOCUMENTS("Dokumente"),
    WHATSAPP("WhatsApp-Medien"), APKS("Installationsdateien"), DUPLICATES("doppelte Dateien"),
    PARTIAL("abgebrochene Downloads"), LARGE("große Dateien"), PERSONAL("persönliche Dateien")
}

/**
 * One parsed order. [keep]: groups the owner protected in this sentence („Fotos behalten“). [noDelete]: „nur
 * überprüfen“, „lösch nichts“ – or a restriction that was not understood ([unsure]): a restriction can only narrow,
 * so a clause the parser cannot read makes the order read-only instead of being ignored. [target]/[name]: what a
 * [DeepCleanMode.TARGETED] order names.
 */
internal data class DeepCleanCommand(
    val mode: DeepCleanMode,
    val keep: Set<DeepCleanGroup> = emptySet(),
    val noDelete: Boolean = false,
    val unsure: Boolean = false,
    val target: DeepCleanGroup? = null,
    val name: String? = null
) {
    /** The effective right to remove anything: the mode allows it and no restriction forbids it. */
    val mayRemove: Boolean get() = mode.mayRemove && !noDelete

    /** Stable form for the regression sets. */
    val code: String get() = buildString {
        append(mode.name)
        target?.let { append(':').append(it.name) }
        name?.let { append(":NAME:").append(it) }
        if (noDelete) append("|NO_DELETE")
        if (unsure) append("|UNSURE")
        if (keep.isNotEmpty()) append("|KEEP:").append(keep.map { it.name }.sorted().joinToString(","))
    }
}

/**
 * Recognises the orders of the storage lane. Deterministic and fail closed: the main clause must be one of the known
 * forms, a quotation, reported speech or a how-to question is not an order, and two main clauses („prüf meinen
 * Speicher, bereinige …“) are a mission, not one order. Restrictions are read from their own clauses and can only
 * narrow the order: a „nicht löschen“ turns any cleaning order into the matching analysis.
 */
internal object DeepCleanCommands {
    private const val MAX_LENGTH = 240
    private val quotes = Regex("[\"„“”‚‘«»]")
    private val reported = Regex("^(?:er|sie|jemand|man|mein\\s+\\w+)\\s+(?:sagte|sagt|fragte|fragt|schrieb|schreibt|meinte|meint|hat\\s+gesagt)\\b")
    private val greeting = Regex("^(?:guten\\s+(?:morgen|tag|abend)|hallo|hi|hey|servus|moin|gruess\\s+dich)[,!.]?\\s+")
    private val courtesyLead = Regex("^(?:bitte|also|so|ok|okay|na\\s+dann|sag\\s+mal)[,]?\\s+")
    private val courtesyTail = Regex("[,]?\\s+(?:bitte|danke|ja)$")
    private val howTo = Regex("^(?:wie|wo|womit|was\\s+muss)\\s+(?:kann|koennte|soll|sollte|muss|bereinige|loesche|raeume|mache|mach)\\s+ich\\b|" +
        "^(?:was\\s+(?:ist|bedeutet|heisst)|erklaer|erklaere|was\\s+macht)\\b")

    private const val P = "(?:(?:bitte|mal|kurz|doch|jetzt|schnell|eben|noch|wieder|zuerst|zunaechst|erst|einmal|endlich|gleich|ruhig|" +
        "auch|ordentlich|richtig|vollstaendig|komplett|grundlegend)\\s+)*"
    private const val THING = "(?:handy|smartphone|telefon|geraet|fold|speicher|speicherplatz|handyspeicher|telefonspeicher|" +
        "internen\\s+speicher|interner\\s+speicher|galaxy|samsung)"
    private const val DEV = "(?:(?:mein|meinen|meines|das|den|dieses|diesen|unser)\\s+)?$THING"
    private const val AT = "(?:(?:auf|in|von|aus|im)\\s+(?:meinem|dem|meinen|diesem)?\\s*$THING)"
    private const val JUNK = "(?:(?:den|die|das|alle|allen|ganzen|ganze|ganzes|meinen|meine|ueberall)\\s+)*" +
        "(?:(?:unnoetigen?|ueberfluessigen?|nutzlosen?|ueberschuessigen?|alten?|sinnlosen?)\\s+)+" +
        "(?:muell|dateien|daten|kram|zeug|ballast|datenmuell|krempel|reste|dateireste)"
    private const val CLEAN = "(?:bereinig|bereinige|saeuber|saeubere|reinig|reinige|entruempel|entruemple|entruempele|putz|putze)"
    private const val REMOVE = "(?:entfern|entferne|loesch|loesche|beseitig|beseitige|wirf|schmeiss|schmeisse|raeum|raeume)"
    private const val ASK = "(?:kannst|koenntest|wuerdest|magst|willst)\\s+du"
    private const val TEMP = "(?:(?:die|alle|meine|deine|eigenen|sicheren|sicher\\s+entbehrlichen|entbehrlichen|alten|eindeutig\\s+entbehrlichen)\\s+)*" +
        "(?:temporaeren?\\s+(?:dateien|daten)|temp-?dateien|zwischendateien|zwischenspeicher-?dateien|tmp-?dateien|temporaerdateien)"

    private fun rx(pattern: String) = Regex("^$pattern$")

    private val safeClean = listOf(
        rx("$CLEAN\\s+$P$DEV(?:\\s+$P)?"),
        rx("(?:raeum|raeume)\\s+$P$DEV\\s+$P(?:auf|zusammen)"),
        rx("$DEV\\s+(?:aufraeumen|bereinigen|saeubern|entruempeln|sauber\\s+machen)"),
        rx("(?:mach|mache)\\s+$P$DEV\\s+$P(?:sauber|frei|leer|leerer)"),
        rx("$ASK\\s+$P$DEV\\s+$P(?:aufraeumen|bereinigen|saeubern|entruempeln|sauber\\s+machen|sauber\\s+kriegen|freiraeumen|ausmisten)"),
        rx("(?:mist|miste)\\s+$P$DEV\\s+$P(?:aus)"),
        rx("$REMOVE\\s+$P$JUNK(?:\\s+$AT)?(?:\\s+(?:weg|raus|aus))?"),
        rx("$ASK\\s+$P$JUNK(?:\\s+$AT)?\\s+(?:entfernen|loeschen|beseitigen|wegwerfen|wegmachen)"),
        rx("$REMOVE\\s+$P$TEMP(?:\\s+$AT)?(?:\\s+weg)?"),
        rx("$CLEAN\\s+$P$TEMP(?:\\s+$AT)?"),
        rx("(?:schaff|schaffe|mach|mache)\\s+$P(?:mehr\\s+|wieder\\s+)?(?:platz|speicherplatz|freien\\s+speicher)\\s+$AT"),
        rx("(?:mach|mache|starte|fuehr|fuehre)\\s+$P(?:eine|die|einen)\\s+(?:speicherbereinigung|bereinigung|handybereinigung|aufraeumaktion|reinigung)" +
            "(?:\\s+$AT)?(?:\\s+durch)?"),
        rx("(?:speicherbereinigung|handybereinigung|speicher\\s+bereinigen|handy\\s+bereinigen|speicher\\s+aufraeumen|handy\\s+aufraeumen)"))

    private val deepClean = listOf(
        rx("(?:mach|mache|starte|fuehr|fuehre)\\s+$P(?:eine|die|einen)\\s+(?:tiefenbereinigung|tiefenreinigung|gruendliche\\s+bereinigung|" +
            "gruendliche\\s+speicherbereinigung|komplette\\s+bereinigung)(?:\\s+$AT)?(?:\\s+durch)?"),
        rx("(?:tiefenbereinigung|tiefenreinigung)"),
        rx("$CLEAN\\s+$P$DEV\\s+$P(?:gruendlich|tief|tiefgehend|von\\s+grund\\s+auf)"),
        rx("$CLEAN\\s+$P(?:gruendlich|tief)\\s+$DEV"))

    private val deepAnalyse = listOf(
        rx("(?:mach|mache|starte|fuehr|fuehre)\\s+$P(?:eine|die|einen)\\s+(?:tiefenanalyse|tiefe\\s+analyse|gruendliche\\s+(?:speicher)?analyse|" +
            "tiefe\\s+speicheranalyse|speichertiefenanalyse)(?:\\s+$AT)?(?:\\s+durch)?"),
        rx("(?:tiefenanalyse|speichertiefenanalyse)"),
        rx("(?:such|suche|find|finde)\\s+$P(?:nach\\s+)?(?:den\\s+|die\\s+)?(?:speicherfressern|speicherfresser|platzfressern|platzfresser|" +
            "doppelten\\s+dateien|doppelte\\s+dateien|duplikaten|duplikate|identischen\\s+dateien|identische\\s+dateien)(?:\\s+$AT)?"),
        rx("(?:analysier|analysiere|untersuch|untersuche|durchsuch|durchsuche)\\s+$P$DEV\\s+$P(?:gruendlich|genau|tief|komplett)"))

    private val analyse = listOf(
        rx("(?:schau|sieh|guck|pruef|pruefe|ueberpruef|ueberpruefe|check|checke|analysier|analysiere|find|finde)\\s+$P(?:mal\\s+)?(?:nach\\s+)?" +
            "(?:was|welche\\s+dateien|welche\\s+apps|wer|wo)\\s+$P(?:auf\\s+meinem\\s+handy\\s+)?(?:so\\s+|alles\\s+)?" +
            "(?:unnoetig|unnoetigen|ueberfluessig|so\\s+viel|am\\s+meisten|den\\s+meisten|zu\\s+viel|viel)?\\s*(?:speicher|platz|speicherplatz)" +
            "(?:\\s+$AT)?\\s+(?:verbraucht|verbrauchen|belegt|belegen|frisst|fressen|wegnimmt|wegnehmen|braucht|brauchen|kostet|kosten)"),
        rx("(?:schau|sieh|guck|pruef|pruefe|ueberpruef|ueberpruefe|check|checke|find|finde)\\s+$P(?:mal\\s+)?(?:nach\\s+)?(?:heraus\\s+|raus\\s+)?" +
            "(?:warum|wieso|weshalb)\\s+(?:mein|der)\\s+(?:speicher|speicherplatz|handyspeicher|handy|telefon)\\s+(?:so\\s+|schon\\s+|fast\\s+)?" +
            "(?:voll|knapp|zugemuellt)\\s+ist"),
        rx("(?:warum|wieso|weshalb)\\s+ist\\s+(?:mein|der)\\s+(?:speicher|speicherplatz|handyspeicher)\\s+(?:so\\s+|schon\\s+|fast\\s+|staendig\\s+)?(?:voll|knapp)"),
        rx("(?:was|wer)\\s+(?:belegt|frisst|verbraucht|fuellt|blockiert)\\s+(?:so\\s+viel\\s+|den\\s+ganzen\\s+)?(?:meinen|den)\\s+(?:speicher|speicherplatz|handyspeicher)"),
        rx("(?:was|welche\\s+dateien)\\s+(?:belegt|belegen|verbraucht|verbrauchen|frisst|fressen)\\s+(?:so\\s+)?(?:viel|am\\s+meisten)\\s+(?:speicher|platz)" +
            "(?:\\s+$AT)?"),
        rx("(?:pruef|pruefe|ueberpruef|ueberpruefe|analysier|analysiere|check|checke|untersuch|untersuche|kontrollier|kontrolliere)\\s+$P" +
            "(?:meinen|den)\\s+(?:speicher|speicherplatz|handyspeicher|telefonspeicher|internen\\s+speicher)"),
        rx("$ASK\\s+$P(?:meinen|den)\\s+(?:speicher|speicherplatz|handyspeicher)\\s+$P(?:pruefen|ueberpruefen|analysieren|checken|untersuchen)"),
        rx("(?:mach|mache|starte)\\s+$P(?:eine|die)\\s+(?:speicheranalyse|speicherpruefung)"),
        rx("(?:speicheranalyse|speicherpruefung|speichercheck|speicher\\s+pruefen|speicher\\s+analysieren)"),
        rx("wo\\s+ist\\s+(?:mein|der\\s+ganze)\\s+(?:speicher|speicherplatz)\\s+hin"),
        rx("wie\\s+voll\\s+ist\\s+(?:mein|der)\\s+(?:speicher|speicherplatz|handyspeicher)(?:\\s+wirklich)?"))

    private val optimise = listOf(
        rx("(?:optimier|optimiere)\\s+$P$DEV(?:\\s+$P)?"),
        rx("$DEV\\s+optimieren"),
        rx("(?:mach|mache)\\s+$P$DEV\\s+$P(?:wieder\\s+)?(?:schneller|flotter|flüssiger|fluessiger)"),
        rx("$ASK\\s+$P$DEV\\s+$P(?:wieder\\s+)?(?:schneller|flotter|fluessiger)\\s+machen"),
        rx("$ASK\\s+$P$DEV\\s+$P(?:optimieren)"),
        rx("(?:warum|wieso|weshalb)\\s+ist\\s+(?:mein|das)\\s+(?:handy|smartphone|telefon|geraet)\\s+(?:so\\s+|gerade\\s+|in\\s+letzter\\s+zeit\\s+)?(?:langsam|traege|warm|heiss)"),
        rx("(?:handy|telefon|smartphone)\\s+(?:optimieren|schneller\\s+machen)"))

    private const val APK = "(?:(?:die|alle|meine|alten|ueberfluessigen|doppelten)\\s+)*(?:apks|apk-dateien|apk\\s+dateien|apk-files|installationsdateien|" +
        "installationspakete|installationsdateien|setup-dateien)"
    private const val DUP = "(?:(?:die|alle|meine)\\s+)*(?:doppelten\\s+dateien|doppelte\\s+dateien|duplikate|identischen\\s+dateien|identische\\s+dateien|" +
        "doppelten\\s+downloads|mehrfachen\\s+downloads|doppelt\\s+heruntergeladenen\\s+dateien)"
    private const val PART = "(?:(?:die|alle|meine)\\s+)*(?:abgebrochenen|unvollstaendigen|halben|kaputten|haengengebliebenen)\\s+(?:downloads|dateien|herunterladungen)"
    private const val LARGE = "(?:(?:die|alle|meine)\\s+)*(?:grossen|riesigen)\\s+(?:dateien|downloads)(?:\\s+(?:im|in\\s+meinem|aus\\s+dem)\\s+(?:download-ordner|downloadordner|downloads))?"
    private const val FILE_NAME = "([\\p{L}\\p{N}][\\p{L}\\p{N} ._()\\-]{0,80}\\.[a-z0-9]{1,5})"

    private val targeted: List<Pair<Regex, DeepCleanGroup>> = listOf(
        rx("$REMOVE\\s+$P$APK(?:\\s+$AT)?(?:\\s+weg)?") to DeepCleanGroup.APKS,
        rx("$REMOVE\\s+$P$DUP(?:\\s+$AT)?(?:\\s+weg)?") to DeepCleanGroup.DUPLICATES,
        rx("$REMOVE\\s+$P$PART(?:\\s+$AT)?(?:\\s+weg)?") to DeepCleanGroup.PARTIAL,
        rx("$REMOVE\\s+$P$LARGE(?:\\s+$AT)?(?:\\s+weg)?") to DeepCleanGroup.LARGE,
        rx("$ASK\\s+$P$APK\\s+(?:loeschen|entfernen)") to DeepCleanGroup.APKS,
        rx("$ASK\\s+$P$DUP\\s+(?:loeschen|entfernen)") to DeepCleanGroup.DUPLICATES)
    private val targetedName = rx("(?:loesch|loesche|entfern|entferne)\\s+$P(?:die\\s+datei|die\\s+heruntergeladene\\s+datei|den\\s+download)\\s+$FILE_NAME")

    private val showTargets: List<Pair<Regex, DeepCleanGroup>> = listOf(
        rx("(?:zeig|zeige)\\s+(?:mir\\s+)?$P$DUP(?:\\s+$AT)?") to DeepCleanGroup.DUPLICATES,
        rx("(?:zeig|zeige)\\s+(?:mir\\s+)?$P$APK(?:\\s+$AT)?") to DeepCleanGroup.APKS,
        rx("(?:zeig|zeige)\\s+(?:mir\\s+)?$P$PART(?:\\s+$AT)?") to DeepCleanGroup.PARTIAL)

    private val report = listOf(
        rx("(?:sag|sage|nenn|nenne|zeig|zeige)\\s+(?:mir\\s+)?(?:danach|dann|anschliessend|zum\\s+schluss|am\\s+ende|hinterher|nachher)?\\s*" +
            "(?:wie\\s+viel|wieviel)\\s+(?:platz|speicher|speicherplatz)\\s+(?:ich\\s+)?(?:jetzt\\s+|nun\\s+)?(?:wieder\\s+)?" +
            "(?:habe|hab|frei\\s+habe|gewonnen\\s+habe|dazugewonnen\\s+habe|frei\\s+geworden\\s+ist|frei\\s+ist|gewonnen\\s+wurde|du\\s+freigemacht\\s+hast)"),
        rx("(?:wie\\s+viel|wieviel)\\s+(?:platz|speicher|speicherplatz)\\s+(?:habe|hab)\\s+ich\\s+(?:jetzt\\s+|nun\\s+)?(?:wieder|gewonnen|dazugewonnen|mehr)"),
        rx("(?:wie\\s+viel|wieviel)\\s+(?:platz|speicher|speicherplatz)\\s+(?:ist|wurde)\\s+(?:jetzt\\s+)?(?:frei\\s+geworden|freigeworden|freigemacht|gewonnen)"),
        rx("(?:wie\\s+viel|wieviel)\\s+(?:hast\\s+du|wurde)\\s+(?:jetzt\\s+)?(?:freigemacht|frei\\s+gemacht|geloescht|bereinigt|entfernt|aufgeraeumt)"),
        rx("was\\s+hast\\s+du\\s+(?:alles\\s+)?(?:geloescht|entfernt|bereinigt|weggeraeumt|aufgeraeumt)"),
        rx("(?:zeig|zeige)\\s+(?:mir\\s+)?(?:das|dein)\\s+(?:loeschprotokoll|bereinigungsprotokoll|protokoll\\s+der\\s+bereinigung)"))

    private val restore = listOf(
        rx("(?:stell|stelle)\\s+$P(?:die\\s+(?:geloeschten|entfernten)\\s+dateien|das\\s+geloeschte|das|die\\s+dateien|alles)\\s+(?:wieder\\s+)?her"),
        rx("(?:mach|mache)\\s+$P(?:die\\s+(?:letzte\\s+)?bereinigung|das\\s+loeschen|das)\\s+rueckgaengig"),
        rx("(?:hol|hole)\\s+$P(?:die\\s+(?:geloeschten|entfernten)\\s+dateien|das\\s+geloeschte)\\s+(?:wieder\\s+)?zurueck"),
        rx("(?:wiederherstellen|bereinigung\\s+rueckgaengig\\s+machen)"))

    private val panel = listOf(
        rx("(?:oeffne|oeffnen|zeig|zeige)\\s+(?:mir\\s+)?(?:die\\s+|den\\s+)?(?:speicherbereinigung|speicher\\s+(?:&|und)\\s+bereinigung|bereich\\s+speicher\\s+(?:&|und)\\s+bereinigung)"))

    private val forget = listOf(
        rx("(?:schlag|schlage)\\s+(?:mir\\s+)?(?:wieder\\s+alles|alles\\s+wieder)\\s+vor"),
        rx("vergiss\\s+(?:die\\s+)?(?:geschuetzten\\s+ordner|gelernten\\s+ausnahmen|ausnahmen\\s+(?:beim|der)\\s+(?:aufraeumen|bereinigung))"))

    private val clarify = listOf(
        rx("(?:raeum|raeume|mach|mache)\\s+$P(?:auf|sauber|klar\\s+schiff)"),
        rx("(?:aufraeumen|saubermachen|bereinigen|bereinige|bereinig|ausmisten)"),
        rx("$REMOVE\\s+$P(?:alles|alle\\s+dateien|alle\\s+meine\\s+dateien|den\\s+ganzen\\s+speicher|alles\\s+auf\\s+meinem\\s+handy)"),
        rx("$REMOVE\\s+$P(?:alle\\s+|meine\\s+|die\\s+)+(?:fotos|bilder|videos|filme|dokumente|whatsapp-bilder|whatsapp-medien|musik)"),
        rx("(?:mach|mache)\\s+$P(?:platz|speicher\\s+frei)"))

    /** „Bereinige mein Handy nicht“, „Räum bitte nicht auf“: the order is refused by the owner himself. */
    private val declined = listOf(
        rx("$CLEAN\\s+$P(?:$DEV\\s+)?(?:bitte\\s+)?(?:noch\\s+)?nicht"),
        rx("(?:raeum|raeume)\\s+$P(?:$DEV\\s+)?(?:bitte\\s+)?nicht\\s+auf"),
        rx("(?:nicht|keine)\\s+(?:bereinigen|aufraeumen|bereinigung)"),
        rx("$DEV\\s+(?:bitte\\s+)?nicht\\s+(?:bereinigen|aufraeumen|saeubern)"))

    // ── restrictions ───────────────────────────────────────────────────────────────────────────────────────

    private val groupWords: Map<String, Set<DeepCleanGroup>> = mapOf(
        "fotos" to setOf(DeepCleanGroup.PHOTOS), "foto" to setOf(DeepCleanGroup.PHOTOS), "bilder" to setOf(DeepCleanGroup.PHOTOS),
        "photos" to setOf(DeepCleanGroup.PHOTOS), "fotografien" to setOf(DeepCleanGroup.PHOTOS), "urlaubsfotos" to setOf(DeepCleanGroup.PHOTOS),
        "familienfotos" to setOf(DeepCleanGroup.PHOTOS), "videos" to setOf(DeepCleanGroup.VIDEOS), "video" to setOf(DeepCleanGroup.VIDEOS),
        "filme" to setOf(DeepCleanGroup.VIDEOS), "familienvideos" to setOf(DeepCleanGroup.VIDEOS), "musik" to setOf(DeepCleanGroup.AUDIO),
        "lieder" to setOf(DeepCleanGroup.AUDIO), "songs" to setOf(DeepCleanGroup.AUDIO), "aufnahmen" to setOf(DeepCleanGroup.AUDIO),
        "sprachaufnahmen" to setOf(DeepCleanGroup.AUDIO), "downloads" to setOf(DeepCleanGroup.DOWNLOADS),
        "download-ordner" to setOf(DeepCleanGroup.DOWNLOADS), "downloadordner" to setOf(DeepCleanGroup.DOWNLOADS),
        "dokumente" to setOf(DeepCleanGroup.DOCUMENTS), "pdfs" to setOf(DeepCleanGroup.DOCUMENTS), "pdf-dateien" to setOf(DeepCleanGroup.DOCUMENTS),
        "unterlagen" to setOf(DeepCleanGroup.DOCUMENTS), "whatsapp" to setOf(DeepCleanGroup.WHATSAPP),
        "whatsapp-bilder" to setOf(DeepCleanGroup.WHATSAPP), "whatsapp-medien" to setOf(DeepCleanGroup.WHATSAPP),
        "whatsapp-dateien" to setOf(DeepCleanGroup.WHATSAPP), "whatsapp-sachen" to setOf(DeepCleanGroup.WHATSAPP),
        "apks" to setOf(DeepCleanGroup.APKS), "apk-dateien" to setOf(DeepCleanGroup.APKS), "installationsdateien" to setOf(DeepCleanGroup.APKS),
        "duplikate" to setOf(DeepCleanGroup.DUPLICATES),
        "persoenlichen dateien" to setOf(DeepCleanGroup.PERSONAL), "persoenliche dateien" to setOf(DeepCleanGroup.PERSONAL),
        "persoenlichen sachen" to setOf(DeepCleanGroup.PERSONAL), "privaten dateien" to setOf(DeepCleanGroup.PERSONAL),
        "meine dateien" to setOf(DeepCleanGroup.PERSONAL))
    private val groupAlternation = groupWords.keys.sortedByDescending { it.length }
        .joinToString("|") { key -> key.split(' ').joinToString("\\s+") { word -> Regex.escape(word) } }
    private val GW = "(?:(?:die|meine|alle|den|meinen|deine|der|dem|meiner|meinem)\\s+)?(?:$groupAlternation)"
    private val GROUPS = "($GW(?:\\s*(?:,|und|oder|sowie)\\s*$GW)*)"

    private val noDeleteClause = listOf(
        rx("(?:aber\\s+)?$P(?:(?:loesch|loesche|entfern|entferne|wirf)\\s+(?:aber\\s+)?$P(?:bitte\\s+)?(?:noch\\s+)?(?:nichts|gar\\s+nichts|nichts\\s+weg)(?:\\s+weg)?(?:\\s+davon)?)"),
        rx("(?:aber\\s+)?$P(?:noch\\s+|bitte\\s+)?(?:nichts|gar\\s+nichts)\\s+(?:loeschen|entfernen|wegwerfen|anfassen|veraendern)"),
        rx("(?:aber\\s+)?(?:bitte\\s+)?nicht\\s+(?:loeschen|entfernen)"),
        rx("(?:aber\\s+)?(?:bitte\\s+)?(?:erst\\s+mal\\s+|erstmal\\s+|vorerst\\s+|zunaechst\\s+)?nur\\s+(?:ueberpruefen|pruefen|analysieren|anschauen|ansehen|nachsehen|" +
            "schauen|nachschauen|zeigen|auflisten|nachgucken|gucken|checken|untersuchen)"),
        rx("(?:aber\\s+)?ohne\\s+(?:etwas\\s+|was\\s+)?(?:zu\\s+)?loeschen"),
        rx("(?:aber\\s+)?(?:mach|aendere|aender)\\s+(?:aber\\s+)?(?:noch\\s+)?nichts(?:\\s+kaputt)?"),
        rx("(?:aber\\s+)?(?:noch\\s+)?nicht\\s+(?:gleich\\s+)?(?:bereinigen|aufraeumen|loeschen)"))
    private val keepClause = listOf(
        rx("(?:aber\\s+)?(?:bitte\\s+)?(?:behalt|behalte)\\s+$GROUPS"),
        rx("(?:aber\\s+)?$GROUPS\\s+(?:behalten|bleiben|bleiben\\s+da|bleiben\\s+drauf|bleiben\\s+erhalten|nicht\\s+(?:loeschen|anfassen|anruehren|entfernen)|in\\s+ruhe\\s+lassen|lassen)"),
        rx("(?:aber\\s+)?(?:bitte\\s+)?(?:lass|lasse)\\s+(?:mir\\s+)?$GROUPS(?:\\s+(?:bitte\\s+)?(?:in\\s+ruhe|da|drauf|stehen|bleiben|liegen|unangetastet|wie\\s+sie\\s+sind|weg|aus))?"),
        rx("(?:aber\\s+)?(?:nicht|keine)\\s+$GROUPS"),
        rx("(?:aber\\s+)?(?:ausser|bis\\s+auf|ohne)\\s+$GROUPS"),
        rx("(?:aber\\s+)?(?:fass|fasse)\\s+$GROUPS\\s+nicht\\s+an"))
    private val inlineKeep = Regex("\\s+(?:ausser|bis\\s+auf|ohne)\\s+$GROUPS$")
    private val subordinateComma = Regex(",\\s*(?=(?:wie|was|warum|wieso|weshalb|ob|welche|wo|wer)\\b)")
    private val clauseCut = Regex("\\s*(?:[,;]|\\s-\\s|\\s–\\s)\\s*(?:und\\s+|aber\\s+|doch\\s+)?|\\s+aber\\s+|" +
        "\\s+und\\s+(?=(?:lass|lasse|behalt|behalte|nicht|nichts|loesch\\s+(?:aber\\s+)?nichts|ohne|nur|fass|mach\\s+nichts))")

    /** ä → ae and so on, lower case: the patterns above are written in that form. */
    internal fun fold(text: String): String = text.lowercase(Locale.GERMAN).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
        .replace("ß", "ss").replace(Regex("\\s+"), " ").trim()

    /** The owner's sentence without address, greeting and courtesy words, folded; null when it is not a plain order. */
    internal fun plain(utterance: String, assistantName: String?): String? {
        if (utterance.length !in 2..MAX_LENGTH || utterance.any(Char::isISOControl) || quotes.containsMatchIn(utterance)) return null
        var text = utterance.trim().replace(Regex("\\s+"), " ")
        repeat(3) { text = OwnerAppLookupIntent.stripAddress(text, assistantName) }
        text = fold(text)
        repeat(3) {
            text = greeting.replaceFirst(text, "")
            text = courtesyLead.replaceFirst(text, "")
            text = text.replace(Regex("^(?:continuum|jarvis)[,:]?\\s+"), "")
        }
        text = text.trimEnd('.', '?', '!', ' ').trim()
        text = courtesyTail.replaceFirst(text, "").trim().trimEnd('.', '?', '!', ',', ' ')
        if (text.isEmpty() || reported.containsMatchIn(text) || howTo.containsMatchIn(text)) return null
        return text.replace(subordinateComma, " ").replace(Regex("\\s+"), " ")
    }

    private fun groups(raw: String): Set<DeepCleanGroup> {
        val text = raw.replace(Regex("\\s+"), " ")
        return groupWords.entries.filter { (word, _) -> Regex("(?<![\\p{L}-])${Regex.escape(word)}(?![\\p{L}-])").containsMatchIn(text) }
            .flatMap { it.value }.toSet()
    }

    private fun mainCommand(clause: String): DeepCleanCommand? {
        if (declined.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.DECLINE)
        targetedName.matchEntire(clause)?.let { match ->
            return DeepCleanCommand(DeepCleanMode.TARGETED, name = match.groupValues[1].trim())
        }
        targeted.firstOrNull { it.first.matches(clause) }?.let { return DeepCleanCommand(DeepCleanMode.TARGETED, target = it.second) }
        showTargets.firstOrNull { it.first.matches(clause) }?.let {
            return DeepCleanCommand(DeepCleanMode.TARGETED, target = it.second, noDelete = true)
        }
        if (deepClean.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.DEEP_CLEAN)
        if (deepAnalyse.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.DEEP_ANALYSE)
        if (safeClean.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.SAFE_CLEAN)
        if (analyse.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.ANALYSE)
        if (optimise.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.OPTIMISE)
        if (report.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.REPORT)
        if (restore.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.RESTORE)
        if (panel.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.PANEL)
        if (forget.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.FORGET_PROTECTION)
        if (clarify.any { it.matches(clause) }) return DeepCleanCommand(DeepCleanMode.CLARIFY)
        return null
    }

    /** A restriction clause: (noDelete, keep), or null when [clause] is no restriction the parser knows. */
    private fun restriction(clause: String): Pair<Boolean, Set<DeepCleanGroup>>? {
        if (noDeleteClause.any { it.matches(clause) }) return true to emptySet()
        keepClause.firstNotNullOfOrNull { it.matchEntire(clause) }?.let { match ->
            val named = groups(match.groupValues.drop(1).joinToString(" "))
            if (named.isNotEmpty()) return false to named
        }
        return null
    }

    /**
     * The order in [utterance], or null when it is not one for this lane. A clause beside the main order that is
     * neither a known restriction nor empty makes the order read-only ([DeepCleanCommand.unsure]) – unless it looks
     * like a second order, in which case the sentence is not taken at all.
     */
    fun parse(utterance: String, assistantName: String? = null): DeepCleanCommand? {
        val text = plain(utterance, assistantName) ?: return null
        var keep = emptySet<DeepCleanGroup>()
        var noDelete = false
        var unsure = false
        var main: DeepCleanCommand? = null
        val clauses = text.split(clauseCut).map { it.trim().trimEnd(',', '.', ' ') }.filter { it.isNotEmpty() }
        if (clauses.isEmpty() || clauses.size > 4) return null
        for (raw in clauses) {
            var clause = raw
            val inline = inlineKeep.find(clause)
            if (inline != null) {
                val named = groups(inline.groupValues[1])
                if (named.isNotEmpty()) { keep = keep + named; clause = clause.substring(0, inline.range.first).trim() }
            }
            val command = mainCommand(clause)
            if (command != null) {
                // Two orders in one sentence are a mission (or not clear): never merged into one right.
                if (main != null) return null
                main = command
                continue
            }
            val restricted = restriction(clause)
            if (restricted != null) {
                noDelete = noDelete || restricted.first
                keep = keep + restricted.second
                continue
            }
            unsure = true
        }
        val order = main ?: return null
        // A clause that was not understood next to a cleaning order removes the right to clean; next to a read-only
        // order it may be a second order of its own („…, danach bereinige …“) – then the sentence is not taken.
        if (unsure && !order.mode.mayRemove) return null
        if (unsure && clauses.size > 2) return null
        return narrowed(order, keep, noDelete || unsure, unsure)
    }

    /** The restrictions applied: they can only narrow the order, never widen it. */
    private fun narrowed(order: DeepCleanCommand, keep: Set<DeepCleanGroup>, noDelete: Boolean, unsure: Boolean): DeepCleanCommand {
        val mode = if (!noDelete) order.mode else when (order.mode) {
            DeepCleanMode.SAFE_CLEAN -> DeepCleanMode.ANALYSE
            DeepCleanMode.DEEP_CLEAN -> DeepCleanMode.DEEP_ANALYSE
            else -> order.mode
        }
        // A read-only mode keeps a no-delete restriction only where it matters (a targeted list stays a list).
        val keepNoDelete = (noDelete || order.noDelete) && mode == DeepCleanMode.TARGETED
        return order.copy(mode = mode, keep = order.keep + keep, noDelete = keepNoDelete, unsure = unsure)
    }

    // ── follow-ups to the lane's own question ──────────────────────────────────────────────────────────────

    /** What the owner says to a numbered selection or to the lane's confirmation question. */
    sealed interface Reply {
        /** 1-based numbers as said. */
        data class Pick(val numbers: List<Int>) : Reply
        /** „die drei“: the three shown, or number three – the lane decides with the size of the list. */
        data class Count(val count: Int) : Reply
        data object All : Reply
        data object None : Reply
        data object Yes : Reply
        data object No : Reply
        data object Stop : Reply
        data object Restore : Reply
        data class Show(val group: DeepCleanGroup) : Reply
        data class Keep(val groups: Set<DeepCleanGroup>) : Reply
    }

    private val numberWords = mapOf("eins" to 1, "einen" to 1, "eine" to 1, "erste" to 1, "ersten" to 1, "erstes" to 1, "zwei" to 2, "zweite" to 2,
        "zweiten" to 2, "drei" to 3, "dritte" to 3, "dritten" to 3, "vier" to 4, "vierte" to 4, "vierten" to 4, "fuenf" to 5, "fuenfte" to 5,
        "fuenften" to 5, "sechs" to 6, "sechste" to 6, "sechsten" to 6, "sieben" to 7, "siebte" to 7, "siebten" to 7, "acht" to 8,
        "achte" to 8, "achten" to 8, "neun" to 9, "neunte" to 9, "neunten" to 9, "zehn" to 10, "zehnte" to 10, "zehnten" to 10)
    private val ordinal = setOf("erste", "ersten", "erstes", "zweite", "zweiten", "dritte", "dritten", "vierte", "vierten", "fuenfte", "fuenften",
        "sechste", "sechsten", "siebte", "siebten", "achte", "achten", "neunte", "neunten", "zehnte", "zehnten")
    private val yes = Regex("^(?:ja|jawohl|jo|ok|okay|passt|gut|genau|in\\s+ordnung|mach\\s+das|mach\\s+es|mach|los|bitte|ja\\s+bitte|ja\\s+mach\\s+das|" +
        "ja\\s+gerne|gerne|einverstanden|bestaetigt|loeschen|ja\\s+loeschen|ja\\s+in\\s+den\\s+papierkorb|in\\s+den\\s+papierkorb)$")
    private val no = Regex("^(?:nein|nee|noe|ne|nicht|lieber\\s+nicht|doch\\s+nicht|nein\\s+danke|abbrechen|lass\\s+es|lass\\s+das|vergiss\\s+es|" +
        "nichts\\s+loeschen|loesch\\s+nichts|lass\\s+alles|behalte\\s+alles|behalt\\s+alles|nein\\s+lass\\s+es)$")
    private val stop = Regex("^(?:stopp|stop|halt|aufhoeren|hoer\\s+auf|abbruch|sofort\\s+stopp|stopp\\s+sofort|stopp\\s+die\\s+bereinigung|" +
        "bereinigung\\s+stoppen|halt\\s+stopp)$")
    private val all = Regex("^(?:(?:ja\\s*,?\\s*)?(?:alle|alles|alle\\s+davon|die\\s+alle|alle\\s+(?:loeschen|entfernen)|nimm\\s+alle|alle\\s+bitte))$")
    private val none = Regex("^(?:keine|keins|keinen|keine\\s+davon|gar\\s+keine|nichts\\s+davon)$")
    private val showLarge = Regex("^(?:zeig|zeige)\\s+(?:mir\\s+)?(?:nur\\s+)?(?:die\\s+)?(?:grossen|groessten|grossen\\s+dateien|groessten\\s+dateien)$")
    private val showDuplicates = Regex("^(?:zeig|zeige)\\s+(?:mir\\s+)?(?:nur\\s+)?(?:die\\s+)?(?:doppelten|duplikate|doppelten\\s+dateien)$")
    private val showApks = Regex("^(?:zeig|zeige)\\s+(?:mir\\s+)?(?:nur\\s+)?(?:die\\s+)?(?:apks|apk-dateien|installationsdateien)$")
    private val numberList = Regex("^(?:(?:ja|ok|okay|gut)\\s*,?\\s*)?(?:(?:nur\\s+)?(?:die|den|das|nummer|nr\\.?)\\s+)?" +
        "((?:[0-9]{1,3}|[a-z]+)(?:\\s*(?:,|und|sowie|&)\\s*(?:(?:die|den|das|nummer|nr\\.?)\\s+)?(?:[0-9]{1,3}|[a-z]+))*)" +
        "(?:\\s+(?:bitte|loeschen|entfernen|davon|weg))?$")

    fun reply(utterance: String, assistantName: String? = null): Reply? {
        val text = plain(utterance, assistantName)?.replace(",", " ,")?.replace(Regex("\\s+"), " ")?.replace(" ,", ",") ?: return null
        if (stop.matches(text)) return Reply.Stop
        if (restore.any { it.matches(text) } || text in setOf("stell wieder her", "stell es wieder her", "wiederherstellen", "rueckgaengig")) return Reply.Restore
        if (no.matches(text)) return Reply.No
        if (none.matches(text)) return Reply.None
        if (all.matches(text)) return Reply.All
        if (yes.matches(text)) return Reply.Yes
        if (showLarge.matches(text)) return Reply.Show(DeepCleanGroup.LARGE)
        if (showDuplicates.matches(text)) return Reply.Show(DeepCleanGroup.DUPLICATES)
        if (showApks.matches(text)) return Reply.Show(DeepCleanGroup.APKS)
        restriction(text)?.let { (noDelete, keep) ->
            if (noDelete) return Reply.No
            if (keep.isNotEmpty()) return Reply.Keep(keep)
        }
        numberList.matchEntire(text)?.let { match ->
            val parts = match.groupValues[1].split(Regex("\\s*(?:,|und|sowie|&)\\s*")).map { it.trim() }
                .map { it.replace(Regex("^(?:die|den|das|nummer|nr\\.?)\\s+"), "") }.filter { it.isNotEmpty() }
            val numbers = parts.map { it.toIntOrNull() ?: numberWords[it] ?: return null }
            if (numbers.any { it !in 1..500 }) return null
            // „die drei“ (a cardinal after an article, alone) may mean „those three“.
            val bare = parts.singleOrNull()
            if (bare != null && bare.toIntOrNull() == null && bare !in ordinal && Regex("(?:^|\\s)(?:die|den)\\s+$bare$").containsMatchIn(text))
                return Reply.Count(numbers.single())
            return Reply.Pick(numbers.distinct())
        }
        return null
    }
}
