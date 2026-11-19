package at.heinz.deviceai.cognitive

/**
 * What the app can do through its fixed owner flows („Lenkungen“) – abilities that are deliberately not registry
 * tools: the document desk, the work tools, the send lane, missions, task chains, the live computer and the web
 * research. One list, so that the cloud brain's ability catalogue, the audit and the tests name the same things.
 *
 * Field finding 19.11.2026: with Claude as the brain the app answered „Eine PDF kann ich mit meinen Werkzeugen nicht
 * erstellen“ although the export flow exists. A brain that only sees the registry tools must be told about these
 * flows – and that the owner starts them with a sentence, not the model with a tool call.
 */
internal object OwnerLaneCatalogue {
    /**
     * [say]: sentences the flow takes when the owner says them. [gate]: what stands between the sentence and the
     * action. [needsBridge]: true when the flow needs the Shizuku device bridge. [mapped]: the cloud brain may map a
     * free sentence to this order ([OwnerMissionModelPlan.orderPrompt]); false = only the owner's own wording starts it.
     */
    data class Ability(val key: String, val title: String, val say: List<String>, val gate: String,
                       val needsBridge: Boolean = false, val mapped: Boolean = true)

    val abilities: List<Ability> = listOf(
        Ability("pdf_search", "Ein PDF auf dem Gerät nach seinem Namen suchen – auch bei ungefährem Namen",
            listOf("Such das PDF <Name>", "Ich suche eine PDF, die heißt <Name>"), "nur lesend, im Programm selbst"),
        Ability("pdf_open", "Ein PDF nach seinem Namen öffnen und lesen",
            listOf("Öffne das PDF <Name>"), "nur lesend; bei mehreren Treffern fragt die App nach"),
        Ability("page_summary", "Eine Seite des geöffneten PDFs zusammenfassen und als Word- oder PDF-Datei speichern",
            listOf("Fass Seite <Zahl> zusammen und speichere es als Word"), "neue Datei im Download-Ordner des Besitzers"),
        Ability("news", "Die Nachrichten des Tages aus öffentlichen Feeds zusammenfassen",
            listOf("Fass mir die Nachrichten von heute zusammen"), "Internetfreigabe des Besitzers"),
        Ability("export", "Das Ergebnis, die letzte Antwort oder den Zusammenfassungsentwurf als PDF oder Word-Datei speichern",
            listOf("Speichere das als PDF", "Erstelle eine PDF davon", "Erstelle eine Word-Datei davon"),
            "neue Datei im Download-Ordner des Besitzers; fehlt ein Inhalt, fragt die App, was hinein soll"),
        Ability("open_saved", "Die gerade gespeicherte Datei öffnen",
            listOf("Öffne die gespeicherte Datei"), "nur lesend"),
        Ability("mail", "Das Ergebnis per E-Mail weitergeben",
            listOf("Schick das per Mail an <Adresse>"),
            "Übergabe an das Mailprogramm; selbst gesendet wird nur mit der Dauerfreigabe des Besitzers für Gmail"),
        Ability("send", "Eine WhatsApp-Nachricht oder E-Mail im Wortlaut des Besitzers selbst senden",
            listOf("Schreib <Name> auf WhatsApp, <Text>"),
            "Dauerfreigabe des Besitzers je Kanal und Prüfung am Bildschirm; nur der eigene Wortlaut des Besitzers",
            mapped = false),
        Ability("mission", "Mehrere dieser Aufträge in einem Satz nacheinander ausführen",
            listOf("Öffne das PDF <Name>, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an <Adresse>"),
            "jeder Schritt mit den Freigaben seines eigenen Ablaufs"),
        Ability("newest_file", "Die neueste Datei eines Ordners suchen",
            listOf("Such das neueste PDF in Downloads"), "nur lesend", needsBridge = true),
        Ability("computer", "Eine andere App im Live-Bild bedienen",
            listOf("Öffne Chrome und suche <Begriff>"), "Bildschirmbeobachtung und Bestätigungen des Besitzers", mapped = false),
        Ability("research", "Im Web recherchieren und Quellen nennen",
            listOf("Recherchiere <Thema>"), "Internetfreigabe des Besitzers", mapped = false),
        Ability("system", "Das Handy im Systemzentrum analysieren (Speicher, Wärme, Akku, eigene Diagnoseablage)",
            listOf("Analysiere mein Handy"), "nur lesend; Bereinigen nur mit eigener Bestätigung des Besitzers", mapped = false))

    /** One line per ability for a prompt: what it is, how the owner says it, what guards it. */
    fun lines(): List<String> = abilities.map { ability ->
        "– ${ability.title}: " + ability.say.joinToString(" / ") { "„$it“" } + " (" + ability.gate +
            (if (ability.needsBridge) "; braucht die Shizuku-Verbindung" else "; ohne Shizuku") + ")"
    }
}
