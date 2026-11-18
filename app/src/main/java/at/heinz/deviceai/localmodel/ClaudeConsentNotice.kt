package at.heinz.deviceai.localmodel

/**
 * What the owner reads and confirms before Claude may become the brain. The text names every kind of data the
 * app places in a request and what it never sends. It is shown again unchanged under "Zustimmung ansehen".
 */
object ClaudeConsentNotice {
    const val TITLE = "Claude als Gehirn – was dann dein Gerät verlässt"

    const val TEXT =
        "Wenn du Claude als Gehirn wählst, beantwortet nicht mehr das Modell auf deinem Gerät deine Nachrichten, " +
        "sondern Claude von Anthropic in der Cloud. Dafür sendet diese App bei jeder Anfrage über das Internet " +
        "an Anthropic (api.anthropic.com):\n\n" +
        "• deine Nachricht (getippt oder aus Sprache erkannt),\n" +
        "• den Gesprächsverlauf, den die App in die Anfrage schreibt – auch frühere Antworten in diesem Chat,\n" +
        "• Auszüge aus deinem Gedächtnis, die die App für die Frage heraussucht,\n" +
        "• Textstellen aus Dokumenten (zum Beispiel PDF), über die du sprichst,\n" +
        "• Ergebnisse von Werkzeugen, die Teil der Anfrage sind (zum Beispiel Geräteinformationen, Dateilisten, " +
        "Webrecherche),\n" +
        "• die Arbeitsanweisungen der App und die Liste ihrer Werkzeuge,\n" +
        "• technische Angaben der Programmbibliothek (Betriebssystem, Versionen).\n\n" +
        "Nicht gesendet werden: Bildschirminhalte anderer Apps, Inhalte von Benachrichtigungen und Nachrichten, " +
        "in denen du Internet oder Cloud ausdrücklich ausschließt. Solche Anfragen lehne ich mit Claude als " +
        "Gehirn ab, statt sie zu senden.\n\n" +
        "Erinnerungen, die als „nur auf dem Gerät“ markiert sind (deine bestätigten Fakten, Gerätebeobachtungen, " +
        "frühere Abläufe), sende ich nur, wenn du das unten ausdrücklich ankreuzt. Was bereits in diesem Chat " +
        "steht, gehört zum Gesprächsverlauf und wird mitgesendet.\n\n" +
        "Die Einstellung „Internetfreigabe“ regelt nur die Webrecherche. Claude als Gehirn sendet auch dann, wenn " +
        "dort „Aus“ steht.\n\n" +
        "Kosten: Jede Anfrage verbraucht Guthaben deines eigenen Anthropic-API-Kontos. Die App zeigt eine " +
        "Schätzung und sendet nichts mehr, sobald dein Monatslimit erreicht ist.\n\n" +
        "Anthropic verarbeitet die Daten nach den Bedingungen deines API-Kontos; für Claude Fable 5.2 verlangt " +
        "Anthropic eine Datenaufbewahrung von 30 Tagen. Lehnt Claude eine Anfrage ab, kann Anthropic sie auf " +
        "seinen Servern mit einem anderen Claude-Modell beantworten.\n\n" +
        "Solange Claude aktiv ist, zeigt die App „CLAUDE · CLOUD“ an. Du kannst die Zustimmung jederzeit " +
        "widerrufen und auf „Lokal“ zurückschalten; die App wechselt nie von selbst."

    const val SHARE_MEMORY_LABEL =
        "Auch Erinnerungen mitsenden, die als „nur auf dem Gerät“ markiert sind"

    const val CONFIRM = "ZUSTIMMEN UND CLAUDE AKTIVIEREN"
    const val DECLINE = "ABBRECHEN · LOKAL BLEIBEN"
}
