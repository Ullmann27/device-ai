# Workflow-Unterstützung · 12.11.2026

## Ergebnis

Der Reviewkandidat schließt drei auf dem eingefrorenen Commit `bf7a52ceaccc85ecc3ac5df8ed6b7af9af33b6a6` reproduzierte Reducerfehler.
`repair.patch` ändert ausschließlich `CockpitWorkEvent.kt` (+5/-2). Er wurde nur auf einer temporären Kopie angewandt.
Der aktive Workflow-Quellbaum und Git-Refs wurden nicht verändert. Der zuständige Implementierer übernimmt die Integration.

| Stand | Original-Aktionstests | Reviewfälle | Kompilierung |
|---|---:|---:|---|
| Erstes WIP-Snapshot über `b16fbcfca52b330bf81b1ac04163c6e7d4f4b0a0` | 10/10 PASS | 2 PASS, 4 FAIL | PASS |
| Exakt `bf7a52ceaccc85ecc3ac5df8ed6b7af9af33b6a6` | 10/10 PASS | 5 PASS, 3 FAIL | PASS |
| Derselbe Commit + `repair.patch` | 10/10 PASS | 8/8 PASS | PASS |

Die zwei zusätzlichen Reviewfälle prüfen zulässigen Folgefortschritt und doppelte Dispatches.
Der Parserfehler ist bereits im Basiscommit korrigiert; deshalb enthält der Kandidat keinen Parserpatch.
Das erste WIP-Ergebnis darf nicht dem Basiscommit zugeschrieben werden: Der Parser änderte sich während dieses ersten Laufs.
`initial-wip/source-manifest.json` zeigt Vor-/Nach-Hashes; die nachfolgenden Läufe lesen sämtliche Quellen über den unveränderlichen Commit.

**HOST-VERIFIZIERT:** isolierte Kotlin/JUnit-Logik. **ANDROID/GRADLE: NOT_RUN. Fold7: NOT_RUN.**
Keine Geräteaktion, keine neue Aufgabenverwaltung, keine neuen Berechtigungen, keine APK und keine Versandfreigabe.
Der Patch ist ein lokal geprüfter Reviewkandidat; PORTIERT in den aktiven Workflow ist er durch diesen Review nicht.

## Konkrete Befunde und kleinste Reparatur

1. **Veralteter Dispatch erbt die Bestätigung eines früheren Schritts.**
   `CockpitWorkReducer.apply`, Zweige `DISPATCHED` und `VERIFIED`.
   Folge: A dispatch rev10 → A observed rev11 → A verified → B dispatch rev10 → B verified rev11.
   Beide B-Ereignisse wurden angenommen, obwohl B keine neue Beobachtung hatte.
   Der Kandidat verlangt, dass die Ausgangsrevision mindestens der zuletzt angenommenen Beobachtung entspricht.
   Die vorhandene Prüfung `observationRevision > beforeRevision` verhindert dann die geerbte Bestätigung.

2. **Eine spätere Nachricht kann eine ältere Beobachtung zurückschreiben.**
   `CockpitWorkReducer.apply`, Zweig `OBSERVED`.
   A dispatch rev10 → observed rev12 → verspätet observed rev11 wurde akzeptiert und setzte den Snapshot zurück.
   Der Kandidat verlangt eine gegenüber der zuletzt angenommenen Beobachtung streng steigende Revision.

3. **Verspätetes UNCLEAR eines alten Schritts pausiert den Nachfolger.**
   `LiveActionCockpitAdapter` emittiert für A ein `PAUSED(stepId=A)`; der Reducer nahm dieses auch während B an.
   Der Kandidat bindet schrittbezogene `PAUSED`-/`ASK_OWNER`-Ereignisse an den aktuellen `pendingStep`.
   Globale Pause/Rückfrage ohne `stepId` bleibt erhalten. Ein zweiter Adapterzustand ist nicht erforderlich.

4. **Bereits durch den Hauptimplementierer korrigiert: Entwurfstext verändert.**
   Das ursprüngliche `trimEnd('.', '?', '!')` vor `parseMessage()` machte aus `Hallo!` den Text `Hallo`.
   Der Basiscommit normalisiert den Header getrennt und verarbeitet Nachrichten vor dem Satzzeichen-Trimmen.
   Regressionen für `Hallo!`, `Kommst du?` und `Hallo.` bestehen auf dem Basiscommit und Kandidaten.

Die drei Reducerfehler betreffen sichtbaren Auftrags-/Bestätigungszustand. Der Reducer besitzt keine Geräteautorität;
mit den Repros ist kein ToolGate-Bypass nachgewiesen.

## Bestehende Sicherheitsgrenzen

`LiveComputerActionsTest` wurde unverändert aus dem Basiscommit ausgeführt (10 Tests).
Darin bestehen die Prüfungen für ausbleibende Sende-Freigabe, exakte einmalige Freigabebindung,
veraltete/mehrdeutige/sensitive Ziele, unbekannte Wirkung und Doppelaktionen.
`SendApproval.SEND_TOOLS` ist im geprüften Original leer. `OwnerComputerWorkflow` plant bei Sendeabsicht
nur den Entwurf und endet mit `ASK_OWNER`; es ruft keine SEND-Aktion auf.
Der zusätzliche Parserfall „Öffne WhatsApp und suche Anna“ bleibt SEARCH ohne Entwurfs-/Sendeschritt;
eine angehängte Sendeaktion wird abgewiesen.

Stop, fremde `taskId`/`sessionId`, erneute Dispatches desselben Schritts und eine gültige Folgebeobachtung
sind in den acht Reviewfällen enthalten. Das sind synthetische Hostfälle, keine UI- oder Geräteabnahme.

## Neu beobachteter Avatar-WIP-Hinweis

Während der Prüfung entstehen `ComputerWorkAvatarBridge`, `ComputerWorkCuePlayer` und Änderungen an
`AvatarDirector`/`AvatarContract`. Diese sind nicht Bestandteil des getesteten Commits.
Die fehlende fertige Activity-Verdrahtung wird deshalb nicht als abgeschlossener Fehler behauptet.

Der aktuelle Bridge-Entwurf verwendet für `workInputPerformed` nur `sessionId:stepId`.
Wenn zwei Aufgaben dieselbe Sitzung nutzen und jeweils mit `computer-1` anfangen,
kann `AvatarDirector.seenWorkInputs` den zweiten Impuls als Duplikat verwerfen.
Kleinster Hinweis an den Implementierer: `taskId` in die Aktions-ID aufnehmen.
Dieser zusätzliche Hinweis ist quellenbasiert und in diesem Paket noch **NOT_RUN**.

## Reproduktion

`run-review.py` akzeptiert einen Repositorypfad, einen exakten Commit, das vorhandene Gradle-8.11.1-`lib`-Verzeichnis
als Compilerquelle, einen Ausgabeordner und optional `--patch repair.patch`.
Es startet keinen Gradle-Prozess und verwendet kein Netzwerk. Compiler und Test-JVM: 512 MB, ein Prozessor.

Es kompiliert vollständige Originaldateien für Bus, Reducer, Adapter, Parser, Workflow,
Live-Aktionen, Click-Verifier, ToolRegistry, ToolReceiptStore und AgentProtocol.
Sechs unveränderte Enum-/Datendeklarationen werden aus ihren Originaldateien extrahiert,
damit deren Android-Oberflächen nicht mitkompiliert werden müssen. Keine Aktions-, Gate- oder Reducerlogik wird simuliert.
Die beiden Testklassen heißen `LiveComputerActionsTest` und `WorkflowReviewTest`.

`committed-baseline/` und `repair-candidate/` enthalten echte JUnit-Logs, Compile-Exitcodes,
Compiler-/JVM-Angaben sowie eingefrorene und tatsächlich kompilierte Quellhashes.
`WorkflowReviewTest.kt` enthält die übernehmbaren Regressionen.
`final-original-comparison.json` hält den letzten Vergleich mit dem aktiven Arbeitsbaum fest.

## Übernahme und Rückfall

Vor Übernahme den aktuellen Reducer mit dem Basis-Hash vergleichen und `git apply --check` für den Patch ausführen.
Falls der Implementierer dieselben Guards bereits ergänzt hat, ist der Kandidat überholt; dann nur die Regressionen übernehmen.
Eine Rücknahme betrifft nur diese drei Guards. Package, Originalsignatur, Assets, ToolGate und Aktionsausführung bleiben unverändert.

Registerbezug: EXP-07 (unabhängige Negativtests), EXP-11 (bestehende Orchestrierung/Gates).
Kein neues Experiment, kein Trainings-/Promotionsschritt. Die aktuelle Onlinefassung von Issue #41 wurde in diesem
begrenzten lokalen Review nicht erneut abgefragt; maßgeblich waren lokale AGENTS.md, Experimentvertrag und Code55-Übergabe.
