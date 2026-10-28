# EXP-02 / EXP-07 / EXP-11: Beweisgebundener Fähigkeitstransfer (v1, VORREGISTRIERT)

Status: RESEARCH_SHADOW / MODEL_NOT_READY (niemals als abgeschlossener Modellversuch darstellen)
Datum: 2026-10-28. Ausgecheckte Basis: `979f1e3c3dafb5e0414ac5c2bbae54991c4369d3` (Draft PR #206, abstammend von #205/#204/#203).
Ziel: vorhandene Android-Haupt-App `at.heinz.deviceai.continuumlocal`. Kein APK, Main-Merge, Gewichtsupdate, Besitzerfakt oder Schlüsselzugriff.

## Verbindliche Bestandsprüfung
- `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json`, Issue #41.
- `app/src/main/java/at/heinz/deviceai/cognitive/ExperienceCompiler.kt`: Review-Kandidaten aus Experience, Standard-Minimum 3, nicht hinreichend für echten Transfer.
- `.github/continuum/ccc1-experience.mjs`: vorhandenes hashverkettetes, append-only Task-ExperienceLog; eine Aufgabe höchstens einmal; ein Logeintrag allein ist keine externe Bestätigung.
- `.github/continuum/ccc1-independent-verifier.mjs`: Dual-Worktree / Test-Identitäts- / Owner-Grant-Gate; kein neues Verifier-System.
- `.github/continuum/ccc1-orchestrator.mjs`, `coding-provider.mjs`, `coding-task.mjs`, `coding-outcome-journal.test.mjs`: bestehende Aufgabe, Kandidat, Modellaufrufbudget und Reviewstatus.
- `app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt`: LiteRT-LM-Android-Chatlaufzeit im Code. Kein Nachweis eines geladenen Modells, eines `.litertlm`-Artefakts oder einer echten Modellantwort auf dem Fold7.
- PR #206 enthält die isolierten Modellimport-Streamtests und einen Fold7-Update-Skriptkandidaten; dies ist noch keine auf Android verifizierte Gemma-Inferenz.

## Eintrittsschranken (fehlgeschlagen = KEINE Modellmessung)
1. `MODEL_NOT_READY`: Version, SHA256 der originalen Modelldatei, reale LiteRT-LM-Runtime, Gerät/Backend und eine echte beobachtete Modellantwort inklusive nichtfabrizierter Laufzeitbelege müssen vorliegen. Quellcode, Dateiauswahl, ein `RESPONDED`-Trace oder synthetischer Runtime-Fake allein genügen NICHT. Modell und CCC1-Coding-Adapter müssen tatsächlich verbunden sein.
2. `ISOLATION_UNAVAILABLE`: eine von `ccc1-independent-verifier.mjs` anerkannte isolierte, vom Besitzer autorisierte Ausführung und ein externer Zustand außerhalb des Source-Trees müssen real funktionieren.
3. `REFERENCE_GATE_UNVERIFIED`: bekannter korrekter Referenzpatch MUSS von der unveränderten unabhängigen CCC1-Teststrecke akzeptiert werden, ein absichtlich sachlich falscher Patch MUSS abgewiesen werden. Referenzpatch ist ausdrücklich KEINE Modellleistung.
4. Alle drei Schranken müssen vor Modelldurchlauf gleichzeitig bestanden sein. Kostenfreie, zeitgebundene lokale Hardware bevorzugt; keine stillschweigende Substitution, Installation, Netzkommunikation oder Modell-Downloads.

## Vorab festgelegte Aufgabenfamilie
Kleine Node-JavaScript-Funktionen: **monotone Begrenzung von geordneten Zahlen- und Zeitintervallen bei strikt festgelegter Semantik**; nur eine Quellcodedatei pro Auftrag. Kein Tests-Datei-Edit, keine Abweichung von öffentlichen Typ-/Fehlerverträgen. Ein Kandidat ist ein ECHTER funktionaler Patch, nicht lediglich ein No-op oder Testtext.

ZWEI Erwerbsfälle (sichtbar nur dem jeweiligen Modellarm):
- E1: Eine vorhandene Ganzzahl-Messwertbegrenzung liefert für untere/obere Grenzfälle falsche Werte; Fix muss Inklusivität, Typ-Validierung und Unverändertheit bei Werten im Intervall bewahren.
- E2: Eine begrenzte Zeitfenster-Normalisierung verarbeitet in-range-Timestamps korrekt, verliert aber die untere Grenze; Fix muss auch exakte Randwerte, Reihenfolge und bestehende Fehlermeldungen erhalten.

PILOT (nicht zur endgültigen Erfolgsschätzung): weitere Aufgabenunterfamilie mit anders aufgebauter Logik und mindestens einem Negations-/Ausnahmefall; Pilot wird NACH Erwerbsfällen zur Verfeinerung der Voraussetzungen verwendet und niemals wieder als final unbekannt gewertet.

FINAL-HOLDOUT: neue Anforderungen mit anderen Datentypen, fehlerhaften Eingaben und interagierenden Vorbedingungen, nicht nur neue Namen/Zahlen. Exakte Aufgaben, Lösungsschlüssel, Tests und Checksumme liegen NUR im getrennt gesperrten lokalen Verifier-/Besitzerzustand, niemals im Checkout, Modellprompt, GitHub-Issue oder Arbeitsverzeichnis des Coding-Agents. Sie werden vor jeder Modellmessung vorab eingefroren; nach Offenlegung verbraucht und als bekannt markiert. **Keine finale A/B/C-Aussage ohne diesen externen versiegelten Bestand.**

GEGENFÄLLE: (i) zyklische Ring-/Stundenarithmetik, bei der clamp sachlich falsch wäre; (ii) halb-offene Intervalle ohne gleichwertige Endpunktsemantik; (iii) ausdrückliche Anforderung, ungültige Eingaben abzulehnen statt stillschweigend zu beschneiden. Der Skill muss die Unpassung erkennen, von Anwendung absehen und einen passenden anderen Untersuchungsweg nennen.

## Budget und Unabhängigkeit (vor Erwerb unveränderlich)
- 2 Erwerbsfälle, maximal 2 Modellkandidaten + 1 begrenzter Reparaturversuch je Fall; bestehende CCC1-DurableTaskJournal-/Coding-Provider-Budgets nicht zurücksetzen.
- Maximal 1 Fähigkeitenskandidat aus tatsächlich unabhängigen CCC1-Reviewbelegen; NIE automatische Promotion. Für diesen Forschungsversuch gelten ausdrücklich 2 Erwerbsfälle, ohne globale `ExperienceCompilerPolicy` zu verändern.
- Pilot maximal 4 Aufgaben. Final 3 Bedingungen A/B/C und höchstens 6 neue echte Transferfälle plus mindestens 3 Gegenfälle; konkrete Aufgaben/Tests extern versiegelt.
- Jede Bedingung pro Aufgabe höchstens 1 Kandidat + 1 begrenzter Reparaturversuch; identische Obergrenzen, tatsächliche Modellaufrufe, Ein-/Ausgabetokens (falls Runtime verfügbar), Zeichen, Zeit und CPU getrennt berichten.
- Reihenfolge vorab ABCCBA / rotierendes Latin-Square-Design pro Aufgabe fixieren; keine Weitergabe von Patches, Chat-Zustand oder Modellkontext zwischen Armen. Modellgewichte bleiben eingefroren.
- Kontrollarm A: keine Erfahrung. B: gewöhnlicher Retrieval-Text der zwei erfolgreichen Fälle. C: eingefrorene, versionierte Fähigkeitszusammenfassung mit Voraussetzungen, Gegenfällen und Widerruf.
- Stop wenn tatsächliche Ausführungsisolation, autorisierte Modellinferenz, Budgetfortschritt oder unabhängige Verifikation fehlt. Infrastrukturfehler im Nenner behalten und getrennt kennzeichnen.
- Keine verbesserte Antwort durch nachträglich geänderte Endbewertung behaupten. Werte pro Fall und Arm: unabhängiger Patch-Pass, no-op, Regressionsfälle, ungerechtfertigte Skill-Nutzung, Enthaltung, Fehler, Tokens, Ausführungsdauer, Aufwand, Neustartpersistenz, manuelle Eingriffe.

## Erfahrung / Wiederanlauf
Nur tatsächlich `COMPLETED` und unabhängig bestätigte CCC1-Tasks mit nachverifizierbaren Receipt-Identitäten dürfen zur Fähigkeitskandidatur beitragen. Referenz-/Lehrlösungen stets separat `REFERENCE_ONLY`. `ExperienceLog` bleibt die einzige Persistenz für CCC1-Erfahrungen; kein zweiter Erfahrungs-Store. Nach Lernfällen Worker- und Chat-Prozess wirklich beenden, mit neuem Prozess ausschließlich die verifizierten ExperienceLog-Einträge aus dem offiziellen Zustand wiederherstellen, den Inhalt hashprüfen; keine versteckten globalen Variablen oder alte Prompts. Fähigkeitszusammenfassung ist ein schreibgeschütztes Forschungsergebnis und verlangt bei Anwendung zusätzliche Präconditions; erzeugt keinerlei ToolGate-/Owner-Rechte.

## Akzeptanz
Arm C muss gegen A und B unter vergleichbarem Budget anhand unbekannter unabhängiger Aufgaben UND korrekter Nichtanwendung in Gegenfällen abschneiden. C=B => kein zusätzlicher Skillwert gezeigt. Ein Pilot, ein korrektes Referenzpatch oder grüne statische Tests zählen nicht als autonome Modellleistung. Keine Behauptung genereller Intelligenz. Alle Rohdaten/Fehler, Git-SHA, echte Kommandos, OS-Geräteprofil, NULL/UNKNOWN-Tokens, Kosten und Revisionen dokumentieren.

## Tatsächliches Ergebnis dieser ersten Sicherung
`MODEL_NOT_READY`: Der Nutzer meldete zuletzt ausdrücklich `Sprachmodell: keines geladen`, und weder eine aktuelle Fold7-Gemma-Laufzeitbestätigung noch eine echte CCC1-Coding-Modellantwort liegt hier vor. `ISOLATION_UNAVAILABLE` bedeutet hier **nicht als ausführbar nachgewiesen**, obwohl Code vorhanden ist. `REFERENCE_GATE_UNVERIFIED`: unabhängige Referenz-/Kontrollpatch-Gegenprobe noch nicht ausgeführt. Damit darf das Erwerbs-/Pilot-/Finalexperiment derzeit NICHT als ausgeführt bezeichnet werden. Keine Modelldatei, lokale Besitzerinformation oder Signing-Datei wurde angefasst.
