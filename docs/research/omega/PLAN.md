# Continuum OMEGA – Forschungs- und Implementierungsplan

Autorisierter Auftrag: Besitzeranweisung vom 25.10.2026; ausschließlich Entwicklungsbranch/Draft-PR #133. Umsetzung in dieser Sitzung, abschließende unabhängige Codeprüfung. Basis und Baseline stehen in `HANDOFF.md`.

## Entscheidung und Grenzen

Zuerst die real roten Tests stabilisieren, dann VH-SR an die vorhandene `repairSession` anschließen. Eine zweite Agentenschleife würde CCC1, Receipt-Gates und Budgets duplizieren. Ein unmittelbarer Umbau des Android-Lernkerns wäre vor der Gesamtsuite schlecht messbar. Deshalb opt-in Forschung unter `experimental/omega/`, gleiche Produktionsschnittstellen, keine Aktivierung im Besitzerpfad.

Erstes Ergebnisziel: Scheinreparaturen erkennen, die bestehende Tests bestehen. Kontrollierte Kandidaten messen die Verifikation; sie beweisen keine Modellautonomie. Ein Modellvergleich benötigt später eingefrorene Modellbytes, gleiche Prompts/Temperatur 0, gleicher Aufruf-/Zeit-/Speicherrahmen und neue unabhängige Aufgaben. Bekannte Tests werden nie erneut als blind bezeichnet.

## Arbeitspakete

- [ ] **1 Stabilisierung:** Analysator nimmt reale große App-Datei auf und scheitert bei überschrittenem Bytebudget ausdrücklich. Regressionen: Datei >400000 Bytes, UTF-8-Bytegrenze, existierender Chat→Lernkern-Pfad. Veralteten Bridge-Regex durch Verhaltenstest ersetzen. QA benutzt installierbares `gradle` statt fehlendem Wrapper; npm-Abhängigkeiten ignorieren. Fokussierte Tests, dann vollständige Suite; Commit und Push.
- [ ] **2 Gesamtprüfung:** Node/Backend sowie SDK 36, Gradle 8.11.1, JDK 21 mit JVM-Target 17; nur Unit/Lint/Compile. Fehler als getrennte Befunde sichern. Keine APK. Architektur-/Zweigkarte aus tatsächlichen Quellen und Git-Bäumen speichern.
- [ ] **3 VH-SR:** Default-off `candidateVerifier`-Callback in der bestehenden scoped Repair Loop, nach unveränderten Standardtests. Callback erhält Kopien, kann nur zusätzliche Ablehnung/Enthaltung bewirken. Ergebnis an Basis, Kandidatenhash und eingefrorene Suite binden. Fehler, Timeout, leere Prüfmenge oder ungültiger Beleg dürfen keinen Erfolg erzeugen. Gegenbeispiel wird als begrenzte Diagnose an die nächste vorhandene Revision weitergegeben; Budget bleibt in `taskSpec`.
- [ ] **4 Unabhängiger Prüfer:** endliche numerische Kandidatensprache des vorhandenen `isLocalNumericPatch`, feste numerische Eingaben und Metamorphie-Relationen, externer fester Worker mit Speicher-/Zeitgrenze, kein Modellcode mit Systemrechten. Standardtests bleiben schreibgeschützt gegenüber Kandidaten. Negative Tests: Testmanipulation, Nicht-Zahlen, falsche Bindung, Timeout, fehlende Fälle, überzogene Budgets und falsche positives.
- [ ] **5 Vergleich:** identische Kandidatenfolge und Ressourcenobergrenzen in M6 und VH-SR; separate Entwicklungsfälle und eingefrorene Evaluationsfälle. Wiederholung und unabhängiger Worktree über bestehenden Verifier. Zeit, CPU, RSS, Aufrufe, Abbrüche, Scheinreparaturen und Gegenbeispiele messen. Kein Qualitätsgewinn allein durch mehr Tests als allgemeine Intelligenz ausgeben.
- [ ] **6 Zeilen-Codec:** begrenzte commit-/dateihashgebundene Zeilenanker → kanonische `oldText/newText`-Patches. Exakte Quellenbindung, keine Fuzzy-Anwendung. Vergleich auf denselben Edits: Anwendbarkeit und Bytekosten; Modellqualitätsvorteil bleibt ohne echte Inferenz offen.
- [ ] **7 Abschluss:** neue Tests und vollständige Regression, unabhängige Review, Bericht mit Beleggrenzen, aktueller SHA/PR/Dateiliste/Follow-up. Alle verwendbaren Zwischenstände auf GitHub.

## Einheitliche Forschungsarchitektur A–F

| Bereich | Vorhandene Anschlussstelle | Nächster experimenteller Vertrag / Reifegate |
|---|---|---|
| VH-SR | `coding-task`, `coding-provider`, `repair-loop`, `cefr-independent-verifier` | konkurrierende Hypothesen als Daten, unterscheidende Eingaben; Verifier kann nur vetoieren. Unbekannte Aufgaben und budgetgleiche Modellablation vor aktiver Promotion. |
| H-MAC | Android `FileLearningExperienceStore`, `ExperienceCompiler`; Repair Ledger und CEFR | bestätigte, widerrufbare Provenienz-IDs → Anti-Unification-Skillkandidat. Vergleich: Retrieval vs Regelabstraktion auf neuen Familien. Widerruf invalidiert abhängige Skills, Trainingsartefakte und Caches; bereits gelernte Gewichte verlangen belegtes Unlearning oder Wiederaufbau. |
| EIG-SC | begrenzte `BoundedAgent`-/Research-Orchestrierung; vorhandener CEFR als Rankingbasis | endlicher Operationskatalog mit geschätztem Informationsgewinn/Kosten und expliziter Enthaltung. Unsicherheit kalibrieren; feste Schleife als Baseline. Weder Policy noch latenter Operator erhält neue Tool-Capabilities. |
| OMEGA Arena | vorhandener Critic, Isolated Executor, unabhängiger Worktree | Inquisitor erzeugt ausschließlich Testkandidaten; eingefrorener Verifier entscheidet. Modell kann keine Bewertungsdateien ändern. Metriken: AST-Knoten, Verzweigungen, Patchgröße, Laufzeit; keine Kolmogorov-Zahl. |
| OMEGA Kontinuität | `ModelCheckpointStore`, `LmCheckpointStore`, `AgentCheckpointStore`, CEFR-Checkpoints | konsistenter Snapshot aller beteiligten Stores mit Versionsmanifest, Hash-DAG, autorisiertem Signierer, atomarem Commit, redundanter Speicherung und echtem Restore-Test. Hash allein ist keine Authentizität/Verfügbarkeit. Geräteaufnahme nur Besitzerautorisierung. |
| OMEGA Operator-VM | neue begrenzte Skill-Repräsentation, bestehende Tool-Gates bleiben außen | typisierte Register/Instruktionen, feste Speicher-/Schrittbudgets, deterministische numerische Semantik. Kein `eval`/beliebiger Systemzugriff als Skill. Referenzinterpreter und Mutationstests vor Nutzung. |
| AEGIS | aktuelle Pfad-/Grant-/IPC-/Credential-/Receipt-Gates | unabhängige Sicherheitsregression, Property/Fuzz-/Taint-Experimente gegen tatsächliche Senken. Regex nur Heuristik; `ProcessBuilder` allein ist kein Exploitbeweis. Authentifizierung und Autorisierung getrennt; Tenant-Isolation und spezifizierte HKDF/Envelope-Verschlüsselung fehlen als Gesamtnachweis. |
| ARCHETYP-VOID | vorhandene SWB1/CDX1/CRE1-Forschung, neue Laborartefakte | endliches MDL-/Operatorbibliotheksexperiment mit Parametern und messbaren Kosten. Vergleich fester vs erweiterbarer Repräsentation bei gleichem Budget; Generationen isoliert, neue Holdouts, keine automatische Selbstersetzung. Sättigung/Overfitting explizit messen. |

## Kritische Review-Fragen

1. Kann ein veränderter/gefälschter Verifierbeleg einen Kandidaten freigeben?
2. Kann ein Callback Originalkandidaten, Testmenge oder Budget ändern?
3. Kann Code außerhalb der vorhandenen numerischen Grammatik auf dem Host laufen?
4. Werden Fehler, leere Tests, unendliche/NaN-Werte oder Zeitüberschreitungen als Erfolg behandelt?
5. Werden bekannte synthetische Aufgaben oder kontrollierte Vorschläge als blinde Modellgeneralisierung ausgegeben?

Rollback: opt-in Callback weglassen; neue Forschungsdateien nicht importieren. Kein bestehender Workflow importiert sie automatisch. Noch keine Besitzerfreigabe oder wissenschaftliche Promotion.
