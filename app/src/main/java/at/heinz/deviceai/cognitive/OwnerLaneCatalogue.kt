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
            "mit der Dauerfreigabe des Besitzers für den Kanal wird nach der Prüfung am Bildschirm gesendet, ohne sie steht nur " +
                "der Entwurf in WhatsApp bereit; nur der eigene Wortlaut des Besitzers",
            mapped = false),
        Ability("mission", "Mehrere dieser Aufträge in einem Satz nacheinander ausführen",
            listOf("Öffne das PDF <Name>, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an <Adresse>",
                "Öffne das PDF <Name> und fass Seite <Zahl> zusammen"),
            "jeder Schritt mit den Freigaben seines eigenen Ablaufs; ohne Speicherauftrag wird nichts gespeichert; was noch " +
                "kein Schritt sein kann (senden, andere App, Mails lesen), sagt die App und bietet den Rest an"),
        Ability("newest_file", "Die neueste Datei eines Ordners suchen",
            listOf("Such das neueste PDF in Downloads"), "nur lesend", needsBridge = true),
        // Review finding B5 (19.11.2026): the list used to name „Öffne Chrome und suche <Begriff>“ although Chrome is one
        // of the apps this flow only opens. Every sentence here is one the flow really carries out (the test asks it).
        Ability("computer", "Eine andere App im Live-Bild öffnen und in manchen davon suchen",
            listOf("Öffne Chrome", "Öffne WhatsApp und suche <Begriff>", "Suche in Gmail nach <Begriff>"),
            "Bildschirmbeobachtung und Bestätigungen des Besitzers; nur die fest angebundenen Apps; Chrome, Einstellungen, " +
                "Kamera, Galerie, Kalender, TikTok und SMS werden nur geöffnet", mapped = false),
        Ability("calendar", "Termine dieser oder der nächsten Woche nennen, freie Zeiten zeigen und nach festen Regeln prüfen, " +
            "was auffällt",
            listOf("Welche Termine habe ich diese Woche?", "Habe ich diese Woche etwas vergessen?"),
            "nur lesend; die Termine bleiben auf dem Gerät und gehen an kein Modell", mapped = false),
        Ability("research", "Im Web recherchieren und Quellen nennen",
            listOf("Recherchiere <Thema>"), "Internetfreigabe des Besitzers", mapped = false),
        Ability("system", "Das Handy im Systemzentrum analysieren (Speicher, Wärme, Akku, eigene Diagnoseablage)",
            listOf("Analysiere mein Handy"), "nur lesend; Bereinigen nur mit eigener Bestätigung des Besitzers", mapped = false),
        Ability("storage", "Den Speicher prüfen, sicher bereinigen, gründlich nach großen Dateien und Duplikaten durchsuchen und " +
            "sagen, wie viel Platz wirklich frei wurde",
            listOf("Schau, was unnötig Speicher verbraucht", "Bereinige mein Handy", "Mach eine Tiefenbereinigung",
                "Wie viel Platz habe ich wieder?"),
            "automatisch nur Continuums eigene, eindeutig entbehrliche Zwischendateien; alles andere nur nach Auswahl des " +
                "Besitzers und über Androids eigenen Papierkorb-Dialog; persönliche Dateien, Modelle und Gedächtnis nie",
            mapped = false),
        // Gmail lane (19.11.2026): the owner's own Google account through Google's consent; mail text is third-party data.
        Ability("mail_connect", "Das Gmail-Konto des Besitzers verbinden, den Verbindungsstatus nennen oder trennen",
            listOf("Verbinde Gmail", "Trenne Gmail"), "Googles eigene Freigabeseite; nur in der original signierten App", mapped = false),
        Ability("mail_read", "Neue oder gesuchte Mails im verbundenen Gmail zeigen und vorlesen",
            listOf("Zeig mir neue Mails", "Suche Mails von <Name>", "Lies die Mail vor"), "Leserecht bei Google; nur lesend", mapped = false),
        Ability("mail_summary", "Mails zusammenfassen, die wichtigsten nach festen Punkten nennen und Fristen wörtlich zitieren",
            listOf("Fass meine wichtigsten Mails zusammen", "Was steht da wegen Fristen?"),
            "Mailtext geht nur mit der eigenen Freigabe des Besitzers an die Cloud, sonst Modell auf dem Gerät", mapped = false),
        Ability("mail_draft", "Einen Mail-Entwurf in Gmail anlegen oder auf die gezeigte Mail antworten",
            listOf("Schreib einen Entwurf an <Name>: <Text>"), "Entwurfsrecht bei Google; gesendet wird nichts", mapped = false),
        Ability("mail_send", "Den Entwurf über Gmail senden",
            listOf("Schick die Mail ab"), "eigenes Senderecht bei Google und Bestätigung von Empfänger, Betreff und Text", mapped = false))

    /** One line per ability for a prompt: what it is, how the owner says it, what guards it. */
    fun lines(): List<String> = abilities.map { ability ->
        "– ${ability.title}: " + ability.say.joinToString(" / ") { "„$it“" } + " (" + ability.gate +
            (if (ability.needsBridge) "; braucht die Shizuku-Verbindung" else "; ohne Shizuku") + ")"
    }
}
