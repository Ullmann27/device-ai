package at.heinz.deviceai.learning

/**
 * Deterministic, offline-only research curriculum. All samples are synthetic
 * public language exercises, never user facts or verified external sources.
 *
 * This corpus is deliberately kept separate from owner-confirmed lessons.
 * Train and audit sentences are non-overlapping; the production fixed
 * holdouts in ContinuumLearningCore remain an additional independent gate.
 */
object ContinuumPublicCurriculum {
    const val ID = "public-offline-lab-v1-20261020"

    private val greetings = listOf(
        "hallo und guten tag", "servus ihr lieben", "einen wunderbaren morgen",
        "grüße an alle hier", "schön dich wiederzusehen", "willkommen im gespräch",
        "guten abend zusammen", "hey lange nicht gesehen", "griaß eich miteinand",
        "ein herzliches willkommen", "hallo mein freund", "einen angenehmen freitag",
        "liebe grüße nach hause", "guten tag an die runde", "schön dass du da bist",
        "servus und willkommen", "hallo ihr zwei", "grüß gott und guten morgen",
        "einen freundlichen tag", "ich wünsche einen guten morgen",
        "hey hallo hier bin ich", "herzlich willkommen bei uns",
        "guten abend liebe leute", "einen schönen sonntag",
        "meine besten grüße", "wie schön dich zu sehen", "morgen miteinander",
        "einen entspannten abend", "hallo und willkommen zurück",
        "ein freundliches hallo", "grüße aus der ferne", "servus und bis bald",
        "herzlich grüße ich euch", "schön dass wir sprechen",
        "hallo guten morgen zusammen", "frohen guten abend"
    )

    private val questions = listOf(
        "kannst du den begriff erklären", "wieso ist der himmel manchmal rot",
        "welchen weg soll ich nehmen", "wo finde ich meine notizen",
        "was lernen wir heute", "wann wird die app gestartet",
        "wer hat das modell trainiert", "wie kann ich etwas nachschlagen",
        "warum brauchen wir tests", "ist das gerät noch geladen",
        "dürfte ich eine frage stellen", "was bedeutet diese meldung",
        "welcher versuch war erfolgreich", "wie entstehen neue wörter",
        "kannst du einen fehler suchen", "womit beginnt die analyse",
        "woher stammen diese werte", "weshalb wurde der kandidat verworfen",
        "wie öffnet man ein dokument", "wann prüft das system erneut",
        "welche sprache verstehst du", "ist diese ausgabe aktuell",
        "können wir das genauer prüfen", "welchen schritt empfiehlst du",
        "was ist in dieser datei", "warum wurde nichts gespeichert",
        "wann ist der nächste termin", "wo sind die lokalen gewichte",
        "kannst du das bitte vergleichen", "wie viel speicher ist frei",
        "welche prüfung ist unabhängig", "was geschieht bei einem abbruch",
        "ist die verbindung gesichert", "wie kann ich neu starten",
        "weshalb benötigt das modell daten", "was folgt nach dem experiment"
    )

    private val holdoutGreetings = listOf(
        "freundliche grüße an alle", "hallo ihr da draußen",
        "einen großartigen tag euch", "ein sonniges guten morgen",
        "wieder mal servus", "willkommen in unserer runde",
        "grüße aus dem urlaub", "schönen guten abend hier",
        "liebe grüße euch allen", "ein nettes hallöchen",
        "guten morgen liebe freunde", "einen erfreulichen tag"
    )

    private val holdoutQuestions = listOf(
        "wie lautet der genaue befehl", "könnte das ein problem sein",
        "weshalb ist die ausgabe leer", "welcher speicher ist betroffen",
        "wann läuft die nächste prüfung", "wer kann den test bestätigen",
        "ist der neueste stand gespeichert", "wo finde ich die einstellung",
        "womit vergleicht man das ergebnis", "warum scheitert diese anfrage",
        "wie lange dauert die berechnung", "was ist die sichere alternative"
    )

    val intentTrain: List<SubwordIntentExample> =
        greetings.map { SubwordIntentExample(it, 0) } +
        questions.map { SubwordIntentExample(it, 1) }

    val intentAudit: List<SubwordIntentExample> =
        holdoutGreetings.map { SubwordIntentExample(it, 0) } +
        holdoutQuestions.map { SubwordIntentExample(it, 1) }

    private val positivePairs = listOf(
        "telefon akku laden" to "ein gerät erhält elektrische energie",
        "programm test fehler" to "ein test prüft das verhalten von software",
        "modell gewicht lernen" to "neuronale parameter werden trainiert",
        "foto bild pixel" to "digitale bilder enthalten bildpunkte",
        "text sprache satz" to "eine sprache besteht aus wörtern und sätzen",
        "fahrrad rad fahren" to "ein zweirad wird mit pedalen bewegt",
        "buch bibliothek lesen" to "in der bücherei kann man bücher lesen",
        "wasser regen wolke" to "aus wolken fällt niederschlag",
        "stern planet weltall" to "planeten bewegen sich um einen stern",
        "pflanze erde wachsen" to "pflanzen wachsen in geeigneter erde",
        "musik gitarre saite" to "saiteninstrumente können musik erzeugen",
        "brot teig backen" to "ein brot entsteht im backofen",
        "uhr zeit sekunde" to "eine uhr zeigt die zeit an",
        "netzwerk daten verbindung" to "geräte übertragen informationen",
        "app android bildschirm" to "eine anwendung läuft auf einem mobilgerät",
        "schule mathematik lernen" to "im unterricht werden zahlen behandelt"
    )

    private val negativePairs = listOf(
        "telefon akku laden" to "ein buch steht im regal",
        "programm test fehler" to "wolken bringen niederschlag",
        "modell gewicht lernen" to "ein brot wird im ofen gebacken",
        "foto bild pixel" to "ein fahrrad hat zwei räder",
        "text sprache satz" to "eine gitarre besitzt saiten",
        "fahrrad rad fahren" to "ein stern leuchtet am himmel",
        "buch bibliothek lesen" to "der akku benötigt strom",
        "wasser regen wolke" to "mathematik verwendet zahlen",
        "stern planet weltall" to "digitale bilder enthalten pixel",
        "pflanze erde wachsen" to "programme bestehen aus befehlen",
        "musik gitarre saite" to "eine uhr zeigt sekunden",
        "brot teig backen" to "das netzwerk transportiert daten",
        "uhr zeit sekunde" to "pflanzen nehmen wasser auf",
        "netzwerk daten verbindung" to "ein buch kann gelesen werden",
        "app android bildschirm" to "teig wird zu brot gebacken",
        "schule mathematik lernen" to "planeten kreisen um sterne"
    )

    private val holdoutPositive = listOf(
        "hund tier bellen" to "ein hund ist ein bellendes haustier",
        "computer speichern datei" to "eine datei liegt im computerspeicher",
        "vogel flügel fliegen" to "vögel fliegen mit ihren flügeln",
        "kalender datum termin" to "ein termin steht im kalender",
        "regenbogen farben licht" to "licht erzeugt sichtbare farben",
        "schlüssel tür öffnen" to "mit dem schlüssel lässt sich die tür öffnen"
    )

    private val holdoutNegative = listOf(
        "hund tier bellen" to "ein kalender enthält termine",
        "computer speichern datei" to "ein vogel besitzt flügel",
        "vogel flügel fliegen" to "ein schlüssel öffnet die tür",
        "kalender datum termin" to "der hund kann bellen",
        "regenbogen farben licht" to "dateien befinden sich im speicher",
        "schlüssel tür öffnen" to "ein regenbogen zeigt farben"
    )

    val semanticTrain: List<SubwordSemanticExample> =
        positivePairs.map { (a, b) -> SubwordSemanticExample(a, b, true) } +
        negativePairs.map { (a, b) -> SubwordSemanticExample(a, b, false) }

    val semanticAudit: List<SubwordSemanticExample> =
        holdoutPositive.map { (a, b) -> SubwordSemanticExample(a, b, true) } +
        holdoutNegative.map { (a, b) -> SubwordSemanticExample(a, b, false) }

    fun validate() {
        require(intentTrain.size == 72 && intentAudit.size == 24)
        require(semanticTrain.size == 32 && semanticAudit.size == 12)
        val trainingPhrases = intentTrain.map { it.text.lowercase() }.toSet()
        val auditingPhrases = intentAudit.map { it.text.lowercase() }.toSet()
        require(trainingPhrases.size == intentTrain.size && auditingPhrases.size == intentAudit.size)
        require(trainingPhrases.intersect(auditingPhrases).isEmpty())
        require(intentTrain.count { it.label == 0 } == intentTrain.count { it.label == 1 })
        require(intentAudit.count { it.label == 0 } == intentAudit.count { it.label == 1 })
        val trainPairs = semanticTrain.map { it.left + "\u0000" + it.right }.toSet()
        val auditPairs = semanticAudit.map { it.left + "\u0000" + it.right }.toSet()
        require(trainPairs.size == semanticTrain.size && auditPairs.size == semanticAudit.size)
        require(trainPairs.intersect(auditPairs).isEmpty())
        require(semanticTrain.count { it.similar } == semanticTrain.count { !it.similar })
        require(semanticAudit.count { it.similar } == semanticAudit.count { !it.similar })
    }
}
