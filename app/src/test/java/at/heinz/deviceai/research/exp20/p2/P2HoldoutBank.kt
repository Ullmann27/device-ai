package at.heinz.deviceai.research.exp20.p2

/**
 * SEALED_HOLDOUT text bank, written AFTER the encoder was frozen (encoder blob 70ca435b in commit ae73f995).
 * Same author as the DEV bank, so this is a sealed SAME-AUTHOR holdout, not an independent one.
 * Differences on purpose: unseen names and rooms, new sentence forms, about a third of the cue phrasings outside the
 * frozen lexicon (no arm can see those), pauses from 1 s up to 12 h, S5 exactly 5 s against exactly 4 h.
 */
object P2HoldoutBank {
    const val SEED = 990311L

    val bank = Bank(
        id = "hold",
        scale = TimeScale(1.0, 300.0, 2100.0, 43200.0, 8000.0, 30000.0, 660.0, 1680.0, 5.0..5.0, 14400.0..14400.0),
        maleNames = listOf("Obermayr", "Kranzl", "Zöhrer", "Wimmer", "Haslinger", "Draxler", "Peham", "Aigner", "Gschwandtner", "Reisinger"),
        femaleNames = listOf("Stadlmann", "Kogler", "Prinz", "Hörmann", "Ziegler", "Novak", "Lechner", "Ebner", "Strasser", "Rauscher"),
        rooms = listOf("Bad", "Arbeitszimmer", "Vorzimmer", "Dachboden"),
        fillers = listOf("Was gibt es Neues in den Nachrichten?", "Sag mir ein Sprichwort.", "Wie viel Uhr ist es?",
            "Wie schreibt man Rhythmus?", "Was ist ein anderes Wort für schnell?"),
        shortReferents = listOf("Im {ROOM} ist das Licht noch an.", "Im {ROOM} läuft die Waschmaschine noch.",
            "Der Fernseher im {ROOM} ist noch eingeschaltet.", "Das Fenster im {ROOM} steht noch offen."),
        shortDecisions = listOf("Ist das dort immer noch so?", "Was war gleich das zweite?", "Wie war das nochmal genau?",
            "Und was ist damit jetzt?"),
        longPairs = listOf(
            "Der Auftrag für den neuen Zaun ist vergeben." to "Was ist aus dem Auftrag geworden?",
            "Mein Termin beim Augenarzt ist am Freitag." to "Wann war der Arzttermin?",
            "Die Reparatur vom Rasenmäher dauert noch zwei Wochen." to "Ist die Reparatur schon fertig?",
            "Der Vertrag fürs Handy läuft im März aus." to "Wann läuft der Vertrag aus?"),
        longCorrectionTriples = listOf(
            Triple("Der Termin beim Friseur ist am Dienstag.", "Nein, nicht Dienstag, sondern Mittwoch.", "Wann ist der Friseurtermin?"),
            Triple("Die Bestellung kommt am Montag.", "Stimmt nicht, sie kommt erst am Dienstag.", "Wann kommt die Bestellung?"),
            Triple("Das Projekt Dachausbau beginnt im April.", "Korrektur: es beginnt im Mai.", "Wann beginnt das Projekt?")),
        corrections = listOf("Nein, das war am Vormittag, nicht am Abend.", "Moment, das stimmt nicht, es waren zwei.",
            "Ich hab mich vertan, es war der Dritte."),
        switches = listOf("Ich bin jetzt in der Arbeit.", "Bin grad in die Straßenbahn eingestiegen.", "Jetzt im Zug Richtung Linz.",
            "So, ich bin angekommen."),
        presence = listOf("Es hat gerade an der Tür geklingelt.", "Da ist jemand im Garten.", "Wir haben heute Gäste.",
            "Ich bin nicht allein zuhause."),
        devicePairs = listOf("Die Terrassentür ist zugesperrt." to "Sperr sie bitte auf.", "Das Licht im Gang brennt noch." to "Mach es bitte aus.",
            "Die Heizung läuft auf voller Stufe." to "Dreh sie bitte runter.", "Das Garagentor steht offen." to "Öffne die App und mach das Tor zu."),
        selfContained = listOf("Was ist die Wurzel aus 144?", "Wie viele Kontinente gibt es?", "Wer hat die Relativitätstheorie entwickelt?",
            "Wie spät ist es in Tokio, wenn es hier Mittag ist?"),
        maleIntro = listOf("Gestern hab ich Herrn {N} getroffen.", "Mein Nachbar {N} hat angerufen.", "Der Installateur {N} war heute da."),
        femaleIntro = listOf("Frau {N} hat mir eine Mail geschrieben.", "Meine Kollegin {N} ist heute im Urlaub.", "Die Hausverwalterin {N} hat sich gemeldet."),
        maleTwo = listOf("Herr {A} und Herr {B} kommen beide morgen."),
        femaleTwo = listOf("Ich hab mit Frau {A} und Frau {B} Kaffee getrunken."),
        maleCorrection = listOf("Nein, nur Herr {A} kommt, Herr {B} hat abgesagt."),
        femaleCorrection = listOf("Korrektur: nur Frau {A} war dabei."),
        maleDecisions = listOf("Hat er schon zugesagt?", "Weißt du, was er wollte?"),
        femaleDecisions = listOf("Hat sie schon zurückgeschrieben?", "Was wollte sie eigentlich?"),
        mechanicMale = listOf("Der Techniker {N} repariert die Waschmaschine."),
        repairStatements = listOf("Die Reparatur der Spülmaschine ist für Donnerstag angesetzt.")
    )
}
