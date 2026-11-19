package at.heinz.deviceai.codingagents

/** Owner texts of the cloud coding agents; pure, so they are checked without a device. */
object CodingAgentTexts {
    const val TITLE = "Coding-Agenten (Claude, Cloud)"
    const val ROSTER_NOTE = "Läuft bei Anthropic, nicht auf dem Gerät. Braucht deinen API-Schlüssel, ein GitHub-Token und dein Ja zu jedem Lauf."
    const val INTRO = "Ein Coding-Agent arbeitet selbstständig in einem deiner GitHub-Repositories und liefert einen " +
        "Entwurfs-Pull-Request. Er läuft bei Anthropic (Claude Managed Agents, Beta) – nicht auf dem Telefon – und kostet " +
        "Guthaben deines API-Kontos. Nichts startet ohne dein Ja zu genau diesem Auftrag."

    const val CONSENT_TITLE = "Coding-Agenten – was dabei dein Gerät verlässt"
    const val CONSENT =
        "Ein Coding-Agent ist Claude von Anthropic, das selbstständig in einem deiner GitHub-Repositories arbeitet: Code " +
        "lesen und ändern, Tests ausführen, einen Zweig pushen und einen Entwurfs-Pull-Request anlegen. Die Funktion heißt " +
        "bei Anthropic „Claude Managed Agents“ und ist dort als Beta gekennzeichnet.\n\n" +
        "Wo er läuft: bei Anthropic, in einer abgeschotteten Arbeitsumgebung (Sandbox) je Lauf – nicht auf deinem Telefon. " +
        "Die App ist nur die Fernbedienung: Sie startet den Lauf, zeigt den Fortschritt, fragt dich bei Rückfragen und " +
        "stoppt. Der Agent hat kein Werkzeug in dein Telefon und kann hier nichts bedienen.\n\n" +
        "Was dafür an Anthropic geht (api.anthropic.com):\n" +
        "• dein Auftragstext,\n" +
        "• dein GitHub-Token: einmal in den Tresor (Vault) deines Anthropic-Kontos und bei jedem Lauf als Zugang für das " +
        "gewählte Repository. Nach Anthropics Beschreibung bekommt das Modell es nicht zu sehen und es liegt nicht in der " +
        "Sandbox; es wird erst beim Zugriff auf GitHub eingesetzt,\n" +
        "• der Inhalt des gewählten Repositories (Anthropic holt ihn mit dem Token von GitHub),\n" +
        "• die festen Regeln der App für den Agenten.\n" +
        "Nicht gesendet werden: dein Gedächtnis, deine Chats, Dateien vom Telefon, Bildschirminhalte.\n\n" +
        "Was an GitHub geht (api.github.com): Die App fragt mit deinem Token den Standardzweig ab und prüft am Ende selbst, " +
        "welcher Zweig und welcher Pull Request wirklich existieren.\n\n" +
        "Speicherung: Der Verlauf jedes Laufs (Auftrag, Schritte, Ausgaben) bleibt bei Anthropic gespeichert, bis du ihn " +
        "löschst. Das geht in der Liste der Läufe für jeden Lauf einzeln.\n\n" +
        "Kosten: Jeder Lauf verbraucht Guthaben deines Anthropic-API-Kontos – Token des Modells und Laufzeit der Sitzung. " +
        "Jeder Lauf bekommt ein festes Budget in Dollar, das Anthropic durchsetzt: Ist es erreicht, hält die Sitzung an. " +
        "Zusätzlich gilt dein Monatslimit für Claude; ein Lauf, der es überschreiten würde, startet nicht.\n\n" +
        "Kontrolle: Nichts startet ohne dein Ja zu genau diesem Auftrag. Der Agent arbeitet nur in Repositories deiner " +
        "Freigabeliste, nur auf einem neuen Zweig continuum-agent/…, öffnet einen Entwurfs-Pull-Request und führt nichts " +
        "zusammen. Zusammenführen bleibt deine Entscheidung auf GitHub.\n\n" +
        "Sicherheitsprüfungen betreffen nur den Code deiner freigegebenen Repositories. Es werden keine fremden Hosts " +
        "gescannt und keine laufenden Systeme angegriffen."
    const val CONSENT_CONFIRM = "ZUSTIMMEN"
    const val CONSENT_DECLINE = "ABBRECHEN"

    const val TOKEN_TITLE = "GitHub-Token (fein abgestuft)"
    const val TOKEN_HELP =
        "So erzeugst du das Token im Browser:\n" +
        "1. github.com → dein Profilbild → Settings → Developer settings → Personal access tokens → Fine-grained tokens → " +
        "Generate new token.\n" +
        "2. Repository access: „Only select repositories“ – nur die Repositories auswählen, in denen der Agent arbeiten soll.\n" +
        "3. Repository permissions: Contents „Read and write“ und Pull requests „Read and write“. Metadata „Read-only“ " +
        "setzt GitHub von selbst.\n" +
        "4. Ausdrücklich NICHT vergeben: Administration, Secrets, Workflows, Actions. Ohne „Workflows“ lehnt GitHub selbst " +
        "jede Änderung an .github/workflows ab.\n" +
        "5. Ablaufdatum setzen (zum Beispiel 30 Tage), Token erzeugen, kopieren und hier einfügen.\n\n" +
        "Das Token beginnt mit „github_pat_“. Es wird verschlüsselt auf diesem Gerät gespeichert und nie wieder angezeigt. " +
        "Es geht nur an GitHub (Abfragen dieser App) und an Anthropic (Tresor deines Kontos und Zugang des Laufs). " +
        "Beim Speichern wird nichts gesendet."
    const val TOKEN_CLASSIC = "Das ist ein klassisches Token (ghp_…). Bitte erzeuge ein fein abgestuftes Token (github_pat_…): " +
        "Nur dort lässt sich der Zugriff auf einzelne Repositories und einzelne Rechte begrenzen. Es wurde nichts gespeichert."
    const val TOKEN_IMPLAUSIBLE = "Das ist kein fein abgestuftes GitHub-Token (es beginnt mit „github_pat_“). Es wurde nichts gespeichert."
    const val TOKEN_NOT_STORED = "Das Token konnte auf diesem Gerät nicht verschlüsselt gespeichert werden (Android-Keystore). " +
        "Es wurde nichts gespeichert."
    const val TOKEN_STORED = "Token verschlüsselt gespeichert. Es wurde nichts gesendet. In den Tresor deines Anthropic-Kontos " +
        "kommt es mit „Bei Anthropic einrichten“ oder beim ersten Lauf."

    const val SECURITY_NOTE = "Sicherheitsprüfung: Der Agent sieht den Code des gewählten Repositories durch und schreibt " +
        "seine Fundstellen als Bericht in einen Entwurfs-Pull-Request, mit Korrekturen, wo sie klein und eindeutig sind. " +
        "Nur deine freigegebenen Repositories. Kein Scan fremder Hosts, kein Angriff auf laufende Systeme."

    fun tokenStatus(present: Boolean): String =
        if (present) "Token gespeichert (verschlüsselt, wird nie angezeigt)" else "kein Token gespeichert"

    fun repositoryInvalid(input: String): String = "„${input.trim().take(80)}“ ist kein Repository im Format Besitzer/Name " +
        "(zum Beispiel Ullmann27/device-ai). Es wurde nichts eingetragen."

    fun status(settings: CodingAgentSettings, resources: CodingAgentResources, keyPresent: Boolean, claudeConsent: Boolean,
               tokenPresent: Boolean): String = buildString {
        append("Anthropic-API-Schlüssel: ").append(if (keyPresent) "gespeichert" else "fehlt (unter „Gehirn“ eintragen)")
        append("\nZustimmung Claude (Datenübertragung): ").append(if (claudeConsent) "erteilt" else "nicht erteilt (unter „Gehirn“)")
        append("\nZustimmung Coding-Agenten: ").append(if (settings.consentGiven) "erteilt" else "nicht erteilt")
        append("\nGitHub-Token: ").append(tokenStatus(tokenPresent))
        append("\nFreigegebene Repositories: ").append(if (settings.repositories.isEmpty()) "keines" else settings.repositories.joinToString(", "))
        append("\nBudget je Lauf: ").append(CodingAgentMoney.usd(settings.budgetCents)).append(" · höchstens ")
            .append(CodingAgentMoney.usd(settings.maxBudgetCents))
        append("\nLaufzeit je Lauf: höchstens ").append(settings.maxRunMinutes).append(" Minuten (prüft die App, solange sie läuft)")
        append("\nModell: ").append(settings.model)
        append("\nBei Anthropic eingerichtet: ").append(when {
            resources.complete(settings) -> "ja (Agent Version ${resources.agentVersion}, Umgebung, Tresor mit GitHub-Zugang)"
            resources.agentId == null && resources.environmentId == null && resources.vaultId == null -> "noch nichts"
            else -> "unvollständig oder veraltet – wird beim Einrichten oder beim nächsten Lauf aktualisiert"
        })
    }

    /** The one monthly counter of the Claude brain, as the coding agents see it. */
    fun monthly(spentCents: Long, reservedCents: Long, limitCents: Int): String =
        "Monatslimit Claude: geschätzt ${CodingAgentMoney.usd(spentCents)} von ${CodingAgentMoney.usd(limitCents)} verbraucht" +
            (if (reservedCents > 0) ", ${CodingAgentMoney.usd(reservedCents)} für Läufe ohne Endabrechnung reserviert" else "") +
            " (Limit unter „Gehirn“)"

    fun monthlyLimit(budgetCents: Int, spentCents: Long, reservedCents: Long, limitCents: Int): String =
        "Dieser Lauf (Budget ${CodingAgentMoney.usd(budgetCents)}) würde dein Monatslimit für Claude überschreiten: bisher " +
            "geschätzt ${CodingAgentMoney.usd(spentCents)}" +
            (if (reservedCents > 0) ", für einen laufenden Agenten reserviert ${CodingAgentMoney.usd(reservedCents)}" else "") +
            ", Limit ${CodingAgentMoney.usd(limitCents)}. Der Lauf startet nicht. Du kannst das Budget senken oder das " +
            "Monatslimit unter „Gehirn“ ändern."

    fun budgetOutOfRange(budgetCents: Int, settings: CodingAgentSettings): String =
        "Das Budget ${CodingAgentMoney.usd(budgetCents)} liegt außerhalb deiner Grenzen (mindestens " +
            "${CodingAgentMoney.usd(CodingAgentSettings.MIN_BUDGET_CENTS)}, höchstens ${CodingAgentMoney.usd(settings.maxBudgetCents)}). " +
            "Es wurde nichts gestartet."

    fun unknownRepository(said: String, allowed: List<String>): String = "„$said“ steht nicht auf deiner Freigabeliste" +
        (if (allowed.isEmpty()) "." else " (freigegeben: ${allowed.joinToString(", ")}).") +
        " Ich starte dafür keinen Lauf. Du kannst es unter „Agenten → $TITLE“ freigeben."

    fun askRepository(candidates: List<String>): String = "Für welches Repository soll der Coding-Agent arbeiten? " +
        candidates.mapIndexed { index, repository -> "${index + 1}. $repository" }.joinToString(" · ") +
        ". Sag den Namen oder die Nummer."

    fun confirmation(run: CodingAgentRun, onlyAllowed: Boolean): String = buildString {
        append("Coding-Agent – bitte bestätigen\n")
        append("Repository: ").append(run.repository)
        if (onlyAllowed) append(" (du hast keines genannt; es ist dein einziges freigegebenes)")
        append("\nAusgangszweig: ").append(run.baseBranch)
        append(if (run.baseSha != null) " (Stand ${run.baseSha.take(7)}, von GitHub gelesen)" else " (von GitHub gelesen)")
        append("\nAuftragsart: ").append(run.kind.label)
        append("\nAuftrag (dein Wortlaut): „").append(run.task).append("“")
        append("\nBudget: höchstens ").append(CodingAgentMoney.usd(run.budgetCents))
            .append(" für diesen Lauf (harte Obergrenze bei Anthropic) · Laufzeit höchstens ").append(run.maxRunMinutes).append(" Minuten")
        append("\nModell: ").append(run.model)
        append("\nDer Agent läuft bei Anthropic. Er arbeitet auf dem neuen Zweig ").append(run.workBranch)
            .append(", öffnet einen Entwurfs-PR und führt nichts zusammen.")
        if (run.kind == CodingAgentTaskKind.SECURITY)
            append(" Geprüft wird nur der Code dieses Repositories – kein fremder Host, kein laufendes System.")
        append("\nAn Anthropic gehen dafür: dieser Auftrag, dein GitHub-Token als Zugang und der Inhalt des Repositories.")
        append("\nStarten? Antworte „Ja“. Alles andere startet nichts.")
    }

    fun approval(run: CodingAgentRun, approval: CodingAgentApproval): String =
        "Der Coding-Agent (${run.repository}) wartet auf deine Freigabe: ${approval.summary}\n" +
            "Antworte „Erlauben“ oder „Ablehnen“ – oder entscheide im Bereich „$TITLE“. Solange du nicht antwortest, " +
            "arbeitet er nicht weiter."

    fun deniedByRule(summary: String, rule: String): String =
        "Abgelehnt, ohne dich zu fragen (Regel: $rule): $summary"

    /** What the agent is told when the app denies a paused call by rule. */
    fun denyMessage(rule: String): String = "Vom Besitzer nicht erlaubt (feste Regel: $rule). Arbeite nur auf dem Arbeitszweig weiter."

    const val OWNER_DENY_MESSAGE = "Der Besitzer hat diesen Schritt abgelehnt. Wähle einen anderen Weg innerhalb der Regeln " +
        "oder beende die Arbeit mit einem Bericht."
    const val STOP_DENY_MESSAGE = "Der Besitzer hat den Lauf gestoppt."

    const val NOT_STARTED = "Coding-Agent nicht gestartet."
    const val DECLINED = "Coding-Agent nicht gestartet. Es wurde nichts an Anthropic gesendet."
    const val NOTHING_RUNNING = "Es läuft kein Coding-Agent."
    const val STOP_BEFORE_SESSION = "Stopp vorgemerkt: Die Sitzung war bei Anthropic noch nicht angelegt. Für diesen Auftrag wird " +
        "keine angelegt."
    const val AMOUNT_INVALID = "Das ist kein Betrag in Dollar (zum Beispiel 2 oder 2,50). Es wurde nichts geändert."
    const val MAX_BUDGET_RANGE = "Die Obergrenze je Lauf muss zwischen 0,25 $ und 50,00 $ liegen. Es wurde nichts geändert."
    const val RUN_MINUTES_RANGE = "Die Laufzeit muss zwischen 5 und 180 Minuten liegen. Es wurde nichts geändert."
    const val MODEL_INVALID = "Das ist keine Modellkennung von Claude (zum Beispiel claude-opus-5-5). Es wurde nichts geändert."
    const val REPOSITORIES_FULL = "Die Freigabeliste ist voll (20 Repositories). Entferne zuerst eines."

    const val TOKEN_REMOVE_WHILE_RUNNING = "Es läuft ein Coding-Agent. Stoppe ihn zuerst; dann lässt sich das Token entfernen."
    const val TOKEN_REMOVED_LOCAL = "Das GitHub-Token ist auf diesem Gerät gelöscht."
    const val TOKEN_REMOVE_LOCAL_FAILED = "Das GitHub-Token ließ sich auf diesem Gerät nicht sicher löschen."
    const val TOKEN_VAULT_EMPTY = "Im Tresor deines Anthropic-Kontos lag keines."
    const val TOKEN_VAULT_REMOVED = "Der Eintrag im Tresor deines Anthropic-Kontos ist ebenfalls gelöscht."
    const val TOKEN_VAULT_NEEDS_KEY = "Der Eintrag im Tresor deines Anthropic-Kontos besteht noch: Ohne gespeicherten " +
        "API-Schlüssel kann die App ihn nicht löschen. Trage den Schlüssel ein und tippe erneut auf „Token entfernen“ – oder " +
        "lösche ihn in der Anthropic-Konsole unter „Vaults“."
    const val TOKEN_REVOKE_HINT = "Sitzungen, deren Verlauf du nicht gelöscht hast, tragen ihren eigenen Zugang zum Repository " +
        "weiter. Ganz ungültig wird das Token erst, wenn du es bei GitHub widerrufst (Settings → Developer settings)."

    const val HISTORY_NONE = "Für diesen Lauf wurde bei Anthropic keine Sitzung angelegt. Es gibt dort nichts zu löschen."
    const val HISTORY_ALREADY_DELETED = "Der Verlauf dieser Sitzung ist bei Anthropic bereits gelöscht."
    const val HISTORY_WHILE_RUNNING = "Der Agent arbeitet noch. Stoppe ihn zuerst; danach lässt sich der Verlauf löschen."
    const val HISTORY_DELETED = "Bei Anthropic gelöscht: die Sitzung mit Auftrag, Ereignisverlauf und Sandbox. Zweig und Pull " +
        "Request auf GitHub bleiben bestehen."

    fun tokenVaultKept(reason: String): String = "Der Eintrag im Tresor deines Anthropic-Kontos wurde NICHT gelöscht: $reason " +
        "Tippe erneut auf „Token entfernen“, sobald das behoben ist."

    fun historyNotDeleted(reason: String): String = "Der Verlauf wurde bei Anthropic NICHT gelöscht: $reason"

    fun provisioned(resources: CodingAgentResources): String = "Bei Anthropic eingerichtet: Agent (Version " +
        "${resources.agentVersion}), Sandbox-Umgebung und Tresor mit deinem GitHub-Zugang. Dafür sind keine Modellkosten " +
        "entstanden. Ein Lauf startet erst mit deinem Ja zu einem Auftrag."

    fun started(run: CodingAgentRun): String = "Coding-Agent gestartet: ${run.repository}, neuer Zweig ${run.workBranch}, " +
        "Budget höchstens ${CodingAgentMoney.usd(run.budgetCents)}. Ich melde mich mit dem Ergebnis. „Stopp den Coding-Agenten“ " +
        "hält ihn an."

    fun stopNotSent(reason: String): String = "Der Stopp ist bei Anthropic NICHT angekommen: $reason Die Sitzung arbeitet " +
        "möglicherweise weiter; ihr Budget bleibt die Obergrenze. Versuch es noch einmal."

    fun decisionNotSent(reason: String): String = "Deine Entscheidung ist bei Anthropic nicht angekommen: $reason Die Frage ist " +
        "wieder offen."

    fun connectionLost(run: CodingAgentRun, code: String?): String = CodingAgentFailure.message(CodingAgentFailure.STREAM_LOST) +
        (code?.let { " Grund: " + CodingAgentFailure.message(it) } ?: "") + " (Lauf ${run.repository}, Zweig ${run.workBranch})"
}

/** Encrypted store and shape check of the owner's fine-grained GitHub token. */
object CodingAgentGitHubToken {
    private val FINE_GRAINED = Regex("github_pat_[A-Za-z0-9_]{20,255}")
    private val CLASSIC = Regex("gh[pousr]_[A-Za-z0-9]{20,255}")

    /** Shape only; never checked online when it is stored. */
    fun plausible(candidate: String): Boolean = FINE_GRAINED.matches(candidate)
    fun classic(candidate: String): Boolean = CLASSIC.matches(candidate)
}
