package at.heinz.deviceai.codingagents

/**
 * The owner's report of a run, composed only from what is known: the session's final state as Anthropic reported
 * it, what the app itself read from GitHub ([CodingAgentGitFacts]) and – clearly marked as the agent's own claim –
 * the agent's last message. Nothing is inferred: a value that was not reported is called "nicht gemeldet" or
 * "ungeprüft".
 */
object CodingAgentReport {
    /** The agent's own words are shown up to this length, so the lines after them always fit into one message. */
    const val MAX_SUMMARY = 3_500

    fun headline(run: CodingAgentRun): String = when (run.status) {
        CodingAgentRunStatus.COMPLETED -> when {
            run.facts?.pullUrl != null && run.facts.pullDraft == true -> "Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor."
            run.facts?.pullUrl != null -> "Coding-Agent fertig: Ein Pull Request liegt vor, er ist aber kein Entwurf."
            run.facts?.problem != null -> "Coding-Agent fertig. Ob ein Pull Request existiert, konnte ich nicht prüfen."
            run.facts?.branchSha != null -> "Coding-Agent fertig, aber ohne Pull Request: Es gibt nur den Zweig."
            else -> "Coding-Agent fertig, aber ohne Ergebnis auf GitHub: weder Zweig noch Pull Request."
        }
        CodingAgentRunStatus.STOPPED -> if (run.stopCause == CodingAgentStopCause.TIME_LIMIT)
            "Coding-Agent gestoppt: Die Laufzeit von ${run.maxRunMinutes} Minuten war erreicht." else "Coding-Agent gestoppt."
        CodingAgentRunStatus.BUDGET_REACHED -> "Coding-Agent angehalten: Das Budget von ${CodingAgentMoney.usd(run.budgetCents)} ist erreicht. " +
            "Anthropic stellt keine weiteren Modellanfragen. Fortsetzen mit höherem Budget ist in dieser Version nicht eingebaut."
        CodingAgentRunStatus.FAILED -> "Coding-Agent fehlgeschlagen: " + CodingAgentFailure.message(run.failure)
        CodingAgentRunStatus.NOT_STARTED -> "Coding-Agent nicht gestartet: " + CodingAgentFailure.message(run.failure)
        CodingAgentRunStatus.CONNECTION_LOST -> CodingAgentFailure.message(CodingAgentFailure.STREAM_LOST)
        CodingAgentRunStatus.WAITING_APPROVAL -> "Der Coding-Agent wartet auf deine Freigabe."
        CodingAgentRunStatus.RUNNING, CodingAgentRunStatus.PREPARING -> "Der Coding-Agent arbeitet noch."
    }

    private fun duration(run: CodingAgentRun, now: Long): String {
        val end = if (run.endedAtMillis > 0) run.endedAtMillis else now
        val seconds = ((end - run.createdAtMillis) / 1000).coerceAtLeast(0)
        return if (seconds < 60) "$seconds Sekunden" else "${seconds / 60} Minuten ${seconds % 60} Sekunden"
    }

    fun cost(run: CodingAgentRun): String = when (val cents = run.costCents) {
        null -> "von Anthropic noch nicht gemeldet (Obergrenze ${CodingAgentMoney.usd(run.budgetCents)})"
        else -> "${CodingAgentMoney.usd(cents)} laut Anthropic (Listenpreis) von höchstens ${CodingAgentMoney.usd(run.budgetCents)}"
    }

    /** What exists on GitHub, as the app read it itself. */
    fun github(run: CodingAgentRun): List<String> {
        val facts = run.facts ?: return listOf("GitHub: noch nicht geprüft.")
        if (facts.problem != null) return listOf("GitHub konnte ich nicht abfragen (${CodingAgentFailure.message(facts.problem)}) " +
            "Zweig und Pull Request sind deshalb ungeprüft.")
        val lines = ArrayList<String>()
        lines += if (facts.branchSha == null) "Zweig ${run.workBranch}: existiert nicht auf GitHub."
            else "Zweig ${run.workBranch}: existiert (Stand ${facts.branchSha.take(7)}" +
                (facts.commitsAhead?.let { ", $it Commit${if (it == 1) "" else "s"} vor ${run.baseBranch}" } ?: "") +
                (facts.changedFiles?.let { ", $it geänderte Datei${if (it == 1) "" else "en"}" } ?: "") + ")."
        lines += when {
            facts.pullUrl == null -> if (facts.branchSha != null && (facts.commitsAhead ?: 0) > 0)
                "Pull Request: keiner gefunden. Du kannst ihn selbst anlegen: " + compareUrl(run)
                else "Pull Request: keiner gefunden."
            facts.pullMerged == true -> "Pull Request #${facts.pullNumber}: ist ZUSAMMENGEFÜHRT – das war nicht erlaubt. Bitte prüfen: ${facts.pullUrl}"
            else -> "Pull Request #${facts.pullNumber}: " + (if (facts.pullDraft == true) "Entwurf" else "KEIN Entwurf") +
                ", Zustand ${facts.pullState ?: "unbekannt"} · ${facts.pullUrl}"
        }
        lines += when {
            run.baseSha == null || facts.baseShaNow == null -> "${run.baseBranch}: Stand vor und nach dem Lauf nicht vergleichbar (ungeprüft)."
            run.baseSha == facts.baseShaNow -> "${run.baseBranch}: unverändert seit dem Start (${run.baseSha.take(7)})."
            else -> "${run.baseBranch}: hat sich seit dem Start geändert (${run.baseSha.take(7)} → ${facts.baseShaNow.take(7)}). " +
                "Das kann von dir oder anderen stammen; bitte prüfen."
        }
        return lines
    }

    fun compareUrl(run: CodingAgentRun): String =
        "${CodingAgentRepository.url(run.repository)}/compare/${run.baseBranch}...${run.workBranch}?expand=1"

    fun compose(run: CodingAgentRun, now: Long): String = buildString {
        append(headline(run)).append('\n')
        append("Repository: ").append(run.repository).append(" · ").append(run.kind.label).append('\n')
        github(run).forEach { append(it).append('\n') }
        run.outcomeResult?.let { result ->
            append("Ergebnisprüfung bei Anthropic (nicht von dieser App): ").append(CodingAgentProgress.evaluation(result))
            run.outcomeExplanation?.takeIf { it.isNotBlank() }?.let { append(" – ").append(it) }
            append('\n')
        }
        if (run.ruleNotes.isNotEmpty()) {
            append("Auffälligkeiten:\n")
            run.ruleNotes.forEach { append("• ").append(it).append('\n') }
        }
        if (run.changedFiles.isNotEmpty())
            append("Vom Agenten bearbeitete Dateien (laut Werkzeugaufrufen): ").append(run.changedFiles.take(12).joinToString(", "))
                .append(if (run.changedFiles.size > 12) " und ${run.changedFiles.size - 12} weitere" else "").append('\n')
        append("Kosten: ").append(cost(run)).append('\n')
        append("Dauer: ").append(duration(run, now))
        run.activeSeconds?.let { append(" (davon ").append(it).append(" Sekunden aktive Sitzung laut Anthropic)") }
        append('\n')
        val summary = run.summary?.takeIf { it.isNotBlank() }
        if (summary != null) append("Bericht des Agenten (seine eigenen Angaben, von der App nicht geprüft):\n")
            .append(if (summary.length <= MAX_SUMMARY) summary else summary.take(MAX_SUMMARY) + " … [gekürzt]").append('\n')
        else append("Der Agent hat keinen Bericht geschrieben.\n")
        if (run.historyDeleted) append("Der Verlauf dieser Sitzung ist bei Anthropic gelöscht.")
        else if (run.sessionId != null) append("Der Verlauf dieser Sitzung liegt bei Anthropic, bis du ihn löschst.")
    }.trim()

    /** The last work-window step of a finished run: what the app itself found on GitHub. */
    fun stepLabel(run: CodingAgentRun): String = when {
        run.facts?.problem != null -> "GitHub nicht prüfbar"
        run.facts?.pullUrl != null && run.facts.pullDraft == true -> "Entwurfs-PR #${run.facts.pullNumber} liegt vor"
        run.facts?.pullUrl != null -> "PR #${run.facts.pullNumber}: kein Entwurf"
        run.facts?.branchSha != null -> "Nur Zweig, kein Pull Request"
        else -> "Kein Ergebnis auf GitHub"
    }

    /** „Was macht der Coding-Agent gerade?“ */
    fun statusLine(run: CodingAgentRun, now: Long): String = buildString {
        append("Coding-Agent in ").append(run.repository).append(": ").append(run.status.label)
        append(", seit ").append(duration(run, now)).append(". Kosten: ").append(cost(run)).append('.')
        run.progress.lastOrNull()?.let { append(" Letzter Schritt: ").append(it).append('.') }
        if (run.approvals.isNotEmpty()) append(" Offen: ").append(run.approvals.first().summary).append(" – „Erlauben“ oder „Ablehnen“.")
    }

    /** One line for the run list. */
    fun listLine(run: CodingAgentRun): String = "${run.repository} · ${run.kind.label} · ${run.status.label}" +
        (run.costCents?.let { " · " + CodingAgentMoney.usd(it) } ?: "")
}
