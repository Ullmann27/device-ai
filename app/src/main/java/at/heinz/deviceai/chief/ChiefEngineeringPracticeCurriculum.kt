package at.heinz.deviceai.chief

/** Authored rule-application exercises. Scores are not general coding ability or repair receipts. */
object ChiefEngineeringPracticeCurriculum {
    fun exercises(agentId: String): List<ChiefBotPracticeExercise> = when (agentId) {
        "bot-kora" -> kora()
        "bot-link" -> link()
        else -> emptyList()
    }

    private fun prompt(scenario: String, schema: String): String =
        "Synthetische Entscheidungsübung. Analysiere nur die angegebenen Daten; führe nichts aus.\n" +
            scenario.trimIndent() + "\n" + schema.trimIndent() +
            "\nAntworte ausschließlich mit einem flachen JSON-Objekt: genau diese Schlüssel, " +
            "alle Werte als Strings, keine weiteren Schlüssel, keine Erklärung und keine Codeblöcke."

    private fun kora(): List<ChiefBotPracticeExercise> = listOf(
        ChiefBotPracticeExercise(
            id = "kora-reproduce-v1",
            agentId = "bot-kora",
            title = "Einen gemeldeten Fehler zuerst reproduzieren",
            prompt = prompt(
                """
                Meldung: 'Speichern bricht manchmal ab'. Für den aktuellen Quellstand gibt es
                weder einen reproduzierten Ablauf noch einen fehlschlagenden Test.
                Ein plausibel klingender Edit wurde vorgeschlagen, aber nicht ausgeführt.
                Bestimme den nächsten Arbeitsschritt und ob eine geprüfte Reparatur vorliegt.
                """,
                "Schlüssel: next = REPRODUCE | APPLY_UNTESTED | REPORT_FIXED; verified = yes | no."
            ),
            expected = mapOf("next" to "REPRODUCE", "verified" to "no"),
            explanation = "Erst den Fehler am aktuellen Stand reproduzieren. Ein plausibler Edit ist noch kein Reparaturbeleg."
        ),
        ChiefBotPracticeExercise(
            id = "kora-preserve-zero-v1",
            agentId = "bot-kora",
            title = "Eine ausdrücklich gesetzte 0 erhalten",
            prompt = prompt(
                """
                Kotlin: configured(value: Int?) soll nur bei null den Standard 20 liefern.
                Fehlerhafter Ausdruck: if (value == null || value == 0) 20 else value.
                Zur Auswahl stehen ELVIS: value ?: 20; ZERO_DEFAULT: der bisherige Ausdruck;
                ALWAYS_DEFAULT: 20. Wähle die Korrektur und berechne die Ergebnisse für 0 und null.
                """,
                "Schlüssel: fix = ELVIS | ZERO_DEFAULT | ALWAYS_DEFAULT; zero und missing = jeweilige dezimale Ganzzahl."
            ),
            expected = mapOf("fix" to "ELVIS", "zero" to "0", "missing" to "20"),
            explanation = "Der Elvis-Operator ersetzt nur null. Eine ausdrücklich übergebene 0 bleibt 0."
        ),
        ChiefBotPracticeExercise(
            id = "kora-minimal-limit-v1",
            agentId = "bot-kora",
            title = "Die kleinste passende Grenzkorrektur wählen",
            prompt = prompt(
                """
                Kotlin: first(values, limit) soll bei limit >= 0 höchstens limit Elemente
                in unveränderter Reihenfolge zurückgeben. Bisher: values.take(limit + 1).
                Mögliche Ersatz-Ausdrücke: A = values.take(limit),
                B = values.sorted().take(limit), C = emptyList<Int>().
                Wähle den passenden Ausdruck für alle erlaubten Eingaben.
                Berechne danach first(listOf(7, 2, 9), 2).
                """,
                "Schlüssel: patch = A | B | C; items = Zahlen mit Kommas ohne Leerzeichen, bei leerer Liste EMPTY."
            ),
            expected = mapOf("patch" to "A", "items" to "7,2"),
            explanation = "take(limit) korrigiert die zusätzliche Position und erhält die Reihenfolge. Sortieren oder alles entfernen ändert die Anforderung."
        ),
        ChiefBotPracticeExercise(
            id = "kora-independent-check-v1",
            agentId = "bot-kora",
            title = "Kandidaten-Grün von unabhängiger Prüfung trennen",
            prompt = prompt(
                """
                Gleicher Quellstand und gleiche feste Prüfkette: Baseline 6 ausgeführte Tests,
                davon 2 fehlgeschlagen. Im Reparaturarbeitsstand: 6 ausgeführt, 0 fehlgeschlagen.
                Ein zweiter unabhängiger Prüflauf hat noch nicht stattgefunden.
                Bestimme den nächsten Schritt und ob die Reparatur schon unabhängig bestätigt ist.
                """,
                "Schlüssel: next = INDEPENDENT_TEST | REPORT_FIXED | MERGE_MAIN; verified = yes | no."
            ),
            expected = mapOf("next" to "INDEPENDENT_TEST", "verified" to "no"),
            explanation = "Das erste Grün bestätigt den Kandidaten nur in seinem Arbeitsstand. Die unabhängige Prüfung steht aus."
        ),
        ChiefBotPracticeExercise(
            id = "kora-transfer-falsy-v1",
            agentId = "bot-kora",
            title = "Transfer: false und Leerstring als Werte erhalten",
            prompt = prompt(
                """
                Kotlin: value hat Typ Any?. Nur ein fehlender Wert (null) soll durch den String
                "auto" ersetzt werden. false und ein leerer String sind ausdrücklich gesetzte Werte.
                Regel NULL_ONLY: value ?: "auto".
                Regel EMPTY_OR_FALSE: bei null, false oder leerem String "auto", sonst value.
                Wähle die Regel und berechne die Ergebnisse für false, "" und null.
                """,
                "Schlüssel: rule = NULL_ONLY | EMPTY_OR_FALSE; flag, text und missing = Ergebnis als String; einen leeren String als <empty> schreiben."
            ),
            expected = mapOf("rule" to "NULL_ONLY", "flag" to "false", "text" to "<empty>", "missing" to "auto"),
            explanation = "Fehlen ist nicht dasselbe wie false oder Leertext. Nur null wird durch den Standard ersetzt; dies ist eine einzelne Transferübung.",
            transfer = true
        ),
        ChiefBotPracticeExercise(
            id = "kora-transfer-zero-tests-v1",
            agentId = "bot-kora",
            title = "Transfer: Exitcode 0 ohne ausgeführte Tests",
            prompt = prompt(
                """
                Baseline: 9 Tests ausgeführt, 2 fehlgeschlagen. Kandidat: 9 ausgeführt,
                0 fehlgeschlagen. Der unabhängige Prozess endet mit Exitcode 0, meldet aber:
                insgesamt 9 Tests, 9 übersprungen, 0 ausgeführt. Quelle und Auftrag stimmen überein.
                Beurteile den unabhängigen Beleg und den nächsten Schritt.
                """,
                "Schlüssel: verified = yes | no; reason = ZERO_EXECUTED | VERIFIED | SOURCE_MISMATCH; next = INDEPENDENT_TEST | REPORT_FIXED."
            ),
            expected = mapOf("verified" to "no", "reason" to "ZERO_EXECUTED", "next" to "INDEPENDENT_TEST"),
            explanation = "Exitcode 0 ersetzt keine ausgeführten Tests. Der unabhängige Lauf liefert hier keinen ausreichenden Reparaturbeleg.",
            transfer = true
        )
    )

    private fun link(): List<ChiefBotPracticeExercise> = listOf(
        ChiefBotPracticeExercise(
            id = "link-report-identity-v1",
            agentId = "bot-link",
            title = "Einen Bericht an den richtigen Auftrag binden",
            prompt = prompt(
                """
                Vertrag: Der Reporter vergleicht zuerst die Berichts-ID mit workspace- plus
                seiner Job-ID. Bei Abweichung meldet er REPORT_SCOPE_MISMATCH ohne Versand.
                Ein passender, noch nicht zugestellter Bericht darf in eine freie Mailbox gelangen.
                Ein ChiefWorkspaceWiring.mailboxReporter ist an Job A gebunden und erwartet
                die Berichts-ID workspace-A (A und B sind verschiedene, hier abgekürzte gültige IDs).
                Die Chief-Mailbox ist leer. Er erhält WorkerStatus.DONE mit Berichts-ID workspace-B.
                Noch kein Bericht wurde von diesem Reporter zugestellt.
                Welche Zustellung meldet sein Callback, und wie viele Nachrichten liegen danach an?
                """,
                "Schlüssel: delivery = DELIVERED | REPORT_SCOPE_MISMATCH | DUPLICATE_REPORT; queued = Anzahl als dezimale Ganzzahl."
            ),
            expected = mapOf("delivery" to WorkspaceDeliveryStatus.REPORT_SCOPE_MISMATCH.name, "queued" to "0"),
            explanation = "Ein DONE-Text hebt die Auftragsbindung nicht auf. Die fremde Berichts-ID wird vor der Mailbox-Zustellung abgewiesen."
        ),
        ChiefBotPracticeExercise(
            id = "link-idempotent-report-v1",
            agentId = "bot-link",
            title = "Denselben Bericht nur einmal zustellen",
            prompt = prompt(
                """
                Vertrag: Ein Reporter merkt sich nur erfolgreichen Versand als DELIVERED.
                Nach dieser Markierung meldet er bei erneutem Aufruf DUPLICATE_REPORT ohne Versand.
                Eine volle Mailbox ergibt MAILBOX_REJECTED ohne Markierung; die Regel gilt je Reporter.
                Derselbe ChiefWorkspaceWiring.mailboxReporter erhält zweimal nacheinander
                denselben korrekt gebundenen WorkerReport. Die Mailbox hat Platz für 2 Nachrichten
                und ist anfangs leer. Beide Aufrufe erfolgen ohne Nebenläufigkeit.
                Zwischen den Aufrufen wird weder der Reporter ersetzt noch die Mailbox geleert.
                Bestimme beide Callback-Statuswerte und die Zahl der wartenden Nachrichten.
                """,
                "Schlüssel: first und second = DELIVERED | DUPLICATE_REPORT | MAILBOX_REJECTED; queued = Anzahl als dezimale Ganzzahl."
            ),
            expected = mapOf("first" to WorkspaceDeliveryStatus.DELIVERED.name,
                "second" to WorkspaceDeliveryStatus.DUPLICATE_REPORT.name, "queued" to "1"),
            explanation = "Nach erfolgreicher Zustellung erkennt derselbe Reporter das Duplikat. Eine zweite Nachricht wird nicht eingestellt."
        ),
        ChiefBotPracticeExercise(
            id = "link-full-mailbox-v1",
            agentId = "bot-link",
            title = "Eine volle Mailbox als fehlgeschlagene Zustellung behandeln",
            prompt = prompt(
                """
                Vertrag: Die Mailbox verwirft bei voller Kapazität keinen alten Eintrag,
                sondern lehnt den neuen ab. Der Reporter meldet dann MAILBOX_REJECTED ohne
                Zustellmarkierung. Erst erfolgreicher Versand ergibt DELIVERED; spätere Aufrufe
                desselben Reporters ergeben danach DUPLICATE_REPORT ohne weiteren Versand.
                Die Chief-Mailbox fasst 1 Nachricht und ist bereits mit einer INFO-Nachricht voll.
                Ein neuer ChiefWorkspaceWiring.mailboxReporter erhält einen korrekt gebundenen
                DONE-Bericht. Danach wird die Mailbox vollständig geleert und genau derselbe Bericht
                erneut an denselben Reporter übergeben. Kein Worker wird erneut ausgeführt.
                Bestimme die beiden Callback-Statuswerte und die Warteschlangenlänge nach dem zweiten Aufruf.
                """,
                "Schlüssel: first und second = DELIVERED | MAILBOX_REJECTED | DUPLICATE_REPORT; queued = Anzahl als dezimale Ganzzahl."
            ),
            expected = mapOf("first" to WorkspaceDeliveryStatus.MAILBOX_REJECTED.name,
                "second" to WorkspaceDeliveryStatus.DELIVERED.name, "queued" to "1"),
            explanation = "Die volle Mailbox muss die Ablehnung melden. Erst die spätere erfolgreiche Zustellung setzt den Duplikatschutz."
        ),
        ChiefBotPracticeExercise(
            id = "link-cancelled-result-v1",
            agentId = "bot-link",
            title = "Ein spätes Ergebnis nach Abbruch einordnen",
            prompt = prompt(
                """
                Vertrag: job.cancel() setzt ein dauerhaftes Abbruchflag. Der Job prüft dieses
                vor und nach port.execute. Ein gesetztes Flag beendet ihn als CANCELLED statt
                PROFILE_REPORTED; eine später eintreffende Observation setzt das Flag nicht zurück.
                Ein ChiefWorkspaceWiring.job befindet sich in port.execute. Bevor execute
                zurückkehrt, wird job.cancel() aufgerufen; das Abbruchflag ist gesetzt.
                Danach liefert execute eine sonst gültige Observation: gleiche Scope-Daten,
                Exitcode 0 und bestätigter Teardown. Es erfolgt kein neuer Auftrag.
                Wie muss der laufende Job enden, und darf dieser Rücklauf als Erfolg gelten?
                """,
                "Schlüssel: result = CANCELLED | PROFILE_REPORTED | RETRY; success = yes | no."
            ),
            expected = mapOf("result" to WorkerStatus.CANCELLED.name, "success" to "no"),
            explanation = "Das gesetzte Abbruchflag wird nach execute erneut geprüft. Ein spätes gültiges Ergebnis macht den Abbruch nicht rückgängig."
        ),
        ChiefBotPracticeExercise(
            id = "link-transfer-request-binding-v1",
            agentId = "bot-link",
            title = "Transfer: Ein fremder Request-Hash trotz passender Job-ID",
            prompt = prompt(
                """
                Vertrag: Der gesamte Observation-Scope muss exakt dem Auftrag entsprechen,
                einschließlich requestHash. Jede Abweichung blockiert mit
                WORKSPACE_OBSERVATION_SCOPE_MISMATCH. Gleiche Job-ID allein genügt nicht.
                ChiefWorkspaceWiring.job erwartet eine Observation für seinen gespeicherten Scope.
                Worker-, Task-, Job-, Source- und Request-Message-ID stimmen überein.
                Nur der requestHash unterscheidet sich: erwartet sind 64 Zeichen 'c', gemeldet
                werden 64 Zeichen 'd'. Workspace-ID, Evidenzhash, Exitcode 0 und Teardown sind gültig.
                Der Job wurde nicht abgebrochen. Bestimme das Ergebnis dieser Scope-Prüfung.
                """,
                "Schlüssel: result = BLOCKED | PROFILE_REPORTED; reason = WORKSPACE_OBSERVATION_SCOPE_MISMATCH | WORKSPACE_TEARDOWN_UNCONFIRMED | NONE."
            ),
            expected = mapOf("result" to "BLOCKED", "reason" to "WORKSPACE_OBSERVATION_SCOPE_MISMATCH"),
            explanation = "Die gesamte Scope-Bindung muss übereinstimmen. Gleiche Job-ID genügt bei verändertem Request-Hash nicht.",
            transfer = true
        ),
        ChiefBotPracticeExercise(
            id = "link-transfer-teardown-v1",
            agentId = "bot-link",
            title = "Transfer: Rückmeldung ohne bestätigtes Ressourcenende",
            prompt = prompt(
                """
                Vertrag: PROFILE_REPORTED setzt passenden Scope, gültige ID-/Hashformate,
                Exitcode 0 sowie teardownConfirmed=true voraus. Ein fehlender Teardown-Beleg
                blockiert mit WORKSPACE_TEARDOWN_UNCONFIRMED; der Empfang eines Callbacks ersetzt ihn nicht.
                Ein nicht abgebrochener ChiefWorkspaceWiring.job erhält eine Observation mit
                exakt passendem Scope, gültiger Workspace-ID, gültigem Evidenzhash und Exitcode 0.
                Das einzige abweichende Feld ist teardownConfirmed=false. Der Callback ist angekommen,
                die Beendigung der Ressourcen ist aber unbestätigt.
                Bestimme das Ergebnis und ob die Observation einen erfolgreichen Lauf melden darf.
                """,
                "Schlüssel: result = BLOCKED | PROFILE_REPORTED; reason = WORKSPACE_TEARDOWN_UNCONFIRMED | WORKSPACE_OBSERVATION_SCOPE_MISMATCH | NONE; success = yes | no."
            ),
            expected = mapOf("result" to "BLOCKED", "reason" to "WORKSPACE_TEARDOWN_UNCONFIRMED", "success" to "no"),
            explanation = "Ein Callback und Exitcode 0 bestätigen kein Ressourcenende. Ohne bestätigten Teardown blockiert der bestehende Adapter.",
            transfer = true
        )
    )
}
