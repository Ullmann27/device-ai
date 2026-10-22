# CCC1 · Continuum Cognitive Continuity – technischer Umsetzungsauftrag (22.10.2026)

**Status:** Forschungs-/Entwicklungsauftrag; noch keine CCC1-Implementierung. **Ziel:** eigenständiger lokaler KI-Mitarbeiter, dessen *autorisierter Entwicklungsauftrag* aus einem überprüfbaren Zustand nach Neustart fortgesetzt werden kann. Dies bedeutet reproduzierbare **Prozesskontinuität**, ausdrücklich **keine Kopie von ChatGPT-/Claude-Gewichten**, kein Selbstschutz, keine heimliche Replikation und keine unbefugte Ausführung.

**Aktuelle Ausgangslage verifizieren:** Repo `Ullmann27/device-ai`; Release v0.3.12 `619ebeb2831c8184a91e3c08e165ed4f4794f969`. PR #74 am `52f3a2210ae563e454b6e3cf0625cfd8febe0848` = Designphase als Quellarbeit beendet, noch kein APK-/Gerätenachweis. PR #73 am `a496f4e9ba88e1b8aaca6f367b5e91c4bdda2adf`: einmaliger FROZEN_TEST 592, Kandidat A4bn **scheitert an Distribution Shift** (im Mittel 137/304 richtig, 40/288 unbelegte Antworten); TEST ist verbraucht. PR #72 = I2-Zertifizierung, kontrolliert Antwortfehler unter beantworteten Fällen, nicht Kompetenz. Aus PR #53, #57, #58 existiert schon die kanonische Teamleiter-/Worktree-/Termux-Coding-Pipeline; **diese erweitern, keine zweite Agentenplattform**. Alle historischen Zweige/PRs stehen im Integrationsaudit von PR #69.

## 1. Neue Komponenten mit klaren Grenzen

1. **TaskInterpreter/Reasoner (Kotlin, bestehende Modelladapter verwenden):** Nutzeranweisung in typisierten `GoalSpec` zerlegen: Ziel, prüfbare Akzeptanzkriterien, Ziel-Repo/Commit, Arbeitsumfang, offene Wissensfragen, verfügbare Capabilities, Besitzerfreigabe. Alternativ mehrere Kandidaten/Hypothesen mit explizitem `ABSTAIN` und Unsicherheit. Keine ungeprüfte Werkzeugoperation aus Sprachtext.
2. **DialogueOrchestrator:** Eigenständiger begrenzter Nachrichten-/Zustandsautomat zwischen Reasoner und *vorhandenem* Coding-Teamleiter. Zustände `DRAFT → VALIDATED → APPROVED → RUNNING → VERIFYING → COMPLETED | FAILED | ABORTED`. Maximal ein aktiver Writer je isoliertem Arbeitsbereich; Leser dürfen parallel lesen; Quittungen sind unveränderlich. Keine Endlosschleife.
3. **DurableTaskJournal:** Append-only Event-Log mit atomaren Festschreibungen, monotonen Seq-Nummern, Versionierung, Hash/Commit-Bindung, De-Duplizierung, idempotentem Replay und geprüftem Crash-Recovery. Speicherbackend aus vorhandenen lokalen Teamleiter-/Task-Persistenzmechanismen übernehmen oder gezielt adaptieren; nicht nebenbei einen zweiten Zustand erfinden. Eine Resume-Instanz liest zunächst **nur** die letzte vertrauenswürdige Quittung, dann reconciliert sie Repo und lokale Arbeitsverzeichnisse.
4. **ReceiptVerifier:** Unabhängige Prüfung: `repoSha`, `baseSha`, `patchSha256`, Testbefehl aus Allowlist, Exitcode, tatsächlicher Log-/Artefakthash, Berechtigungen, Ressourcenbudget, verifizierte Tool-Version, Worktree-Pfad und lokale Ausführungs-ID. Aussagen des Reasoners sind nie Ersatz für diese Quittung.
5. **LearningExperimentAdapter:** Strikt getrennte Shadow-Schicht für SWB1/CDX1/CSX1/Synapse bzw. neue lokale Sprachdecoder. Trainings-/Evaldaten vor Provenienzprüfung keine autoritativen Fakten. Gewichte nur in isolierten Shadow-Checkpoints; Freigabe erst nach FROZEN_TEST und Besitzerzustimmung.

## 2. Ein konkret spezifizierter lokaler Nachrichtenvertrag

`EnvelopeV1` als formatversioniertes JSON-Objekt (exaktes Schema und serialisierte Tests erst aus vorhandener Projekt-Toolchain ableiten):
- `schemaVersion=1`, `sessionId`, `taskId`, `messageId`, `sequence`, `parentMessageId?`, `sender` (`OWNER|REASONER|TEAMLEADER|VERIFIER`), `kind` (`GOAL|HYPOTHESIS|PLAN|TEST_REQUEST|RECEIPT|CRITIQUE|REVISE|ABSTAIN|APPROVAL_REQUEST|ABORT`).
- `payload` als strikt typisiertes strukturiertes Datenobjekt, **kein** beliebiger Shell-Befehl oder Freitext mit impliziten Rechten.
- `repoFullName`, `baseCommitSha`, `worktreeId`, `deadlineMonotonic`, `maxRounds`, `maxCpuMillis`, `maxMemoryBytes`, `permissions`, `checkpointHash?`.
- `createdAt`, `protocolHash`; separate, vom Verifier ausgestellte `verificationReceiptId`. Prüfe Integrität und Herkunft; reine SHA ohne vertrauenswürdigen Issuer ist keine Authentisierung.
- Verifier lehnt unbekannte Versionen/Felder mit sicherheitsrelevanter Bedeutung, illegale Zustandsübergänge, fremde Workspace-IDs, wiederholte `messageId`, unerlaubte Capabilities und fehlende Checkpoint-Bindung ab.

Alle Netzwerk-, Dateisystem-, Git-, Installations- und Signieraktionen müssen aus einer expliziten Besitzerfreigabe beziehungsweise schon vorhandenen eng begrenzten Worker-Capability folgen. Texte aus Repo/Internet/Modellausgabe sind **untrusted**.

## 3. Verarbeitungszyklus

Besitzerziel → `GOAL` → Reasoner `HYPOTHESIS/PLAN` → Validator + Budget/Consent → kanonischer Teamleiter `TEST_REQUEST` in separatem Worktree → Compiler/Testrunner → unabhängige `RECEIPT` → Reasoner `CRITIQUE` → höchstens `maxRounds` erneute Revisionen; andernfalls `COMPLETED/ABSTAIN/FAILED`. **Nie** automatisch `main`, Product/Release oder Besitzergewichte beschreiben. Auch erfolgreiche Code-Patches sind zunächst nur prüfbare Vorschläge.

Nach abruptem Stopp: Journal replay → Hash-/Commit-Prüfung → Statusabgleich lokaler Prozesse → unterbrochene Ausführung als `INTERRUPTED` behandeln → keine vermutete Erfolgsmeldung oder automatische Wiederholung nicht-idempotenter Schritte → Resume nur innerhalb gültiger ursprünglicher Owner-Capability/Budgets oder neue Freigabe anfordern. Sensible Daten redigieren, keine vollständigen vertraulichen Chats unkontrolliert im Journal sichern.

## 4. Eigenes Sprachmodell – Forschungsmeilenstein, nicht vorgeschobene Produktfähigkeit

Parallel zum typisierten Orchestrator eine experimentelle lokale `LanguageCore`-Abstraktion definieren, die auf SWB1/CDX1 basieren kann. Baseline der vorhandenen Byte-Autoregression vs. kleiner selbst trainierbarer Transformer-/State-Space-Decoder nur mit realen Komponenten, dokumentiertem Tokenizer, Optimizer, Checkpoint und Datenprovenienz. Für Planung zunächst kontrollierte strukturierte Ausgaben und feste Grammatik verwenden; unbekannte Handlungsarten mit `ABSTAIN`, keine generierten Shell-Befehle. Lernen nicht mit dem bloßen Speichern von Dialogen verwechseln. Eval: freies Vervollständigen, Instruktion→Plan, neue Wortstellungen, Negation, Multi-Entity, unbekannte Aufgabentypen. Die verbrauchten CSX1- und Synapse-Testdatensätze **nie** erneut zur Architekturauswahl benutzen.

Keine freie generative Sprachkompetenz oder selbstständige Gedanken als bereits erreicht darstellen: diese Fähigkeiten benötigen belastbare Architektur-, Daten- und Hardwareergebnisse. Aus dem negativen PR-#73-FROZEN_TEST neue **shift-VALIDATION** (unbekannte Negations-/Zeitwörter und Relationen) und vollständig neue noch unberührte FROZEN_TEST-Splits vorab registrieren.

## 5. Abnahme-Experimente

**E1 Wiederaufnahme:** 100 kontrollierte zufällige Unterbrechungspunkte bei laufendem reproduzierbarem Entwicklungsauftrag; Vergleich von `taskId`, Commit, Quittung, ausstehendem Auftrag, erlaubten Operationen. Akzeptanz: 0 falsche Erfolgsquittungen, 0 unautorisierte Aktionen, keine doppelt ausgeführten nicht-idempotenten Schritte.

**E2 Reasoner↔Coding-Agent:** Mindestens 10 bisher ungesehene, lokal reproduzierbare Bugfix-/Testaufgaben, nach Schwierigkeit getrennt und vor dem Versuch fixiert; drei Modi vergleichen: `Single-Pass`, `Reasoner+Feedback`, `Reasoner+Feedback+Memory`. Messung von **unabhängig korrekt verifizierten** Patches, unzulässigen Veränderungen, Tests, Laufzeiten, Budgetverbrauch und Abbruchquote. Kein Versprechen einer Verbesserung ohne Daten.

**E3 Sprachlernen:** TRAIN/VALIDATION/FROZEN_TEST strikt gruppengetrennt, mehrere Seeds, unabhängige Prüfung von Gradienten und Checkpoints. Kein Einlesen privater Gewichte oder Quellen ohne passende Besitzerfreigabe. Null unzulässige Fakten und nachgewiesene Korrektheit sind separate Zielgrößen, nicht mit generativer Sprachqualität gleichzusetzen.

**E4 Geräteprofil:** CPU/RAM/Energie/Temperatur, Offline-Modus und Fold/Android-Termux nur bei tatsächlich verfügbarem Gerät messen. Keine Schätzung als Messwert ausgeben.

## 6. Konkrete Reihenfolge / Entwicklungs- und Sicherheitsgate

1. GitHub/Bestand real verifizieren, bestehende Task-/Teamleader-Klassen, APIs, Datenverträge und Testwerkzeuge inventarisieren; die neue Sprachkompetenz nicht als bereits vorhanden interpretieren.
2. Isolierten CCC1-Branch vom **passenden Coding-Agenten-Basiszweig**, nicht vom unintegrierten `main`, anlegen. Kleine `EnvelopeV1`-/State-/Journal-Module + Unit-Tests + Crash-Recovery zuerst.
3. Lesenden/verifizierten Adapter zur **bestehenden** Teamleiter-Pipeline anschließen; deterministischen Planer zuerst verwenden; E1/E2 durchführen und alle Testlogs sichern.
4. Eigenen lokal trainierbaren Sprachkern im Shadow-Modus mit E3 testen; nur wenn stark genug, als optionalen `TaskInterpreter`-Kandidaten verbinden.
5. Teilweise Android/Termux-Anbindung unter denselben Owner-Approval- und Sicherheitsregeln; keine APK-Freigabe ohne echte Android-Build-/Daten-/Signaturprüfungen.

**Definition of Done für ersten PR:** kompiliertes `EnvelopeV1` mit Parser/Validator, persistenter Task-Journal-/Resume-Durchlauf, Adapter zu vorhandener Task-Pipeline (notfalls zunächst read-only), Tests für Replay/Duplikate/ungültige Capabilities/ungültige Commitbindung/Abbruch, reproduzierbarer End-to-End-Sandbox-Test sowie überprüfter Handoff-Commit. Jeder nicht durchgeführte Test explizit `NOT_RUN`.

**Schutz:** keine Fremdmodellgewichte exportieren, kein Eigenkopieren auf fremde Systeme, keine unkontrollierte Netzwerk-/Dateiverteilung. Berechtigtes Backup betrifft nur die eigene Software und vom Besitzer freigegebene Daten. Bestehende SWB1/CDX1/CSX1-Checkpoints, QF2+, I2, v0.3.12 und PR #74 bleiben unberührt.
