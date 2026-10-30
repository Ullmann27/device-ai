package at.heinz.deviceai.research.exp20.m5b

/**
 * EXP-20 M5b VAL paraphrase families, written AFTER commit 236800ac froze the TRAIN families and the normaliser.
 * No wording is shared with TRAIN. Per slot: [near, far].
 * Days: near = the ordinal construction with another surface ("an dem N-ten Arbeitstag dieser Woche");
 *       far  = new constructions (start/middle/end of week, "einen Tag nach dem X" with a misleading weekday).
 * Places: near = a new sentence that reuses exactly one TRAIN content word; far = no TRAIN content word at all.
 */
object M5bValFamilies {
    val families = M5bData.Families(
        days = listOf(
            listOf("an dem ersten Arbeitstag dieser Woche", "gleich zu Wochenbeginn"),
            listOf("an dem zweiten Arbeitstag dieser Woche", "einen Tag nach dem Montag"),
            listOf("an dem dritten Arbeitstag dieser Woche", "genau in der Wochenmitte"),
            listOf("an dem vierten Arbeitstag dieser Woche", "einen Tag nach dem Mittwoch"),
            listOf("an dem fünften Arbeitstag dieser Woche", "am letzten Werktag vor dem Wochenende")),
        places = listOf(
            listOf("wo die Bücher zum Lesen bereitstehen", "im Raum mit den Romanen und Lexika"),
            listOf("wo es mittags warme Gerichte gibt", "wo das Personal seine Pause beim Speisen verbringt"),
            listOf("wo die Dienstwagen über Nacht stehen", "bei den geparkten Fahrzeugen des Fuhrparks"),
            listOf("wo Proben analysiert werden", "bei den Mikroskopen und Pipetten"),
            listOf("wo Sendungen angenommen werden", "wo Pakete und Kuverts eintreffen"),
            listOf("wo Vorräte aufbewahrt werden", "zwischen den Hochregalen mit Nachschub"),
            listOf("wo man Maschinen wieder instand setzt", "beim Werkzeug und den Drehbänken"),
            listOf("wo die Rechner surren", "bei den blinkenden Netzwerkgeräten")))
}
