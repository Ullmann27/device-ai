# Device AI Continuum / JARVIS – geprüfter Lagebericht und revidierter Meilensteinplan
Stand: 30.10.2026 · Prüfung: ChatGPT-6 anhand privater GitHub-Quellen und des verbundenen Render-Workspaces

> VERTRAG: Forschungs-Host-Tests, simulierte Modelltests, Android-Integration, auf Fold7 verifizierte Funktion und Besitzer-Release sind unterschiedliche Nachweisstufen. Nicht gleichsetzen. Dieser Bericht ergänzt, ersetzt aber nicht die historischen Protokolle oder Vorregistrierungen.

## 1. Identität und Ausgangslage
Repository `Ullmann27/device-ai`, Android-Paket `at.heinz.deviceai.continuumlocal`, Zielgerät Samsung Galaxy Z Fold7, gemeldete installierte App `Device AI 0.3.27` / `versionCode 28`. Die Nutzeranzeige meldet ein lokales Gemma-4-Modell READY, ein Wissensarchiv, Lernereignisse und zeitweise `DEFERRED_THERMAL_LIMIT`; diese UI-Anzeigen sind keine unabhängigen Modell-/Gerätebenchmarks. Alte separate Continuum-App nicht mit der aktiven Haupt-App verwechseln. Kein Deinstallieren oder Löschen von App-Daten als Update-Methode.

GitHub-`main` lag bei der Prüfung auf `190f5eabaa60fc59292b2b80d3086aafd7743a80` (15.10.2026). Die wesentlichen neuen Module liegen in **offenen, nicht gemergten Forschungs-/Integrationszweigen**. Kein Beweis, dass sie auf dem Fold7 installiert sind.

## 2. Aktuelle Branches und belegte Leistungen
| Linie | verifizierte Spitze | Nachweis und Trennung |
|---|---|---|
| EXP-20 Forschung, Draft-PR #256 | `0436f9ed88e5d0eb21ce7da20e4bc93bb90abb9e` | M5/M5b/M5c, EWM-M6-B4-Belege, P2–P4; zusätzlich Fold7-Leistungsdiagnoseskript. Gegen `ed408a7` produktiver Code `app/src/main` unverändert. |
| EWM-v2-Fix | `3ba1ba0bba28e441d2b85ae098371901d3db7407` | Produktivcode-Kandidat in separatem Branch, **nicht** gemergt; berichtete 47/47 EWM-Tests und 788/788 Host-Regression. Entwicklerorakel: 50.976 Entscheidungen mit Precision/Recall 1,0 ist **kein fremder Holdout**. |
| M8-Coding-Agent | `29d7f76d97d1ca8288572bb26689b1cfa32568ef` | Werkzeugschleife + Termux-CLI + Scratch-Testintegration; letzter Test laut Abschluss 15/15 Agent-Tests, 94/94 Nachbarsuiten, **simuliertes Brain**. Kein echtes Code-Modell und kein Android-End-to-End-Nachweis. |
| PR #244 / #245 | `0e672333...` / `29c1e1e1...` | Eigene **Forschungsmeilenstein-Nummerierung M1–M16**, NICHT die Produkt-Meilensteine. Coding-Agent-/Erfahrungs-/Bridge-/Sicherheitsforschung, nicht als freigegebene Appfunktionen zählen. |
| Update-System, Draft-PR #236 | `6bc54545adcef83e801003f5559d377f3a20d9ba` | Button `AUF UPDATES PRÜFEN`, Paket-/Version-/Signatur-/SHA-Prüfpfad, manuelle Android-Installationsfreigabe vorbereitet. Kein originalsignierter Doppelupdate-Nachweis, nicht gemergt. |

Original-Übergaben: `docs/handoff/OPUS-WEITERARBEIT-20261030.md`, `docs/handoff/WEITERARBEIT-M8-20261030.md` (M8-Branch), `docs/handoff/WEITERARBEIT-EWM-20261030.md` (EWM-Branch). Alle historischen Resultate bleiben unverändert.

## 3. Funktionsinventar – was kann der Coding-Agent HEUTE?
- `scripts/continuum-agent.mjs`: interaktive Termux-/Node-CLI `Auftrag> …`, mit `j` erlaubte Dateien bestätigen und `ende` beenden; auch Einzelaufruf mit Text. **Dies ist Source-Code im M8-Branch, noch kein bestätigtes APK-Feature.**
- `scoutRequest`: zuerst **nur lesend** Dateien listen, per `git grep` suchen, nummerierte Quelltexte anzeigen und höchstens 1–3 bearbeitbare Dateien plus Abnahmekriterien vorschlagen. Das Modell kann sich keine Schreibrechte selbst erteilen.
- `coding-agent-loop.mjs`: Werkzeuge `list_files`, `search`, `read_file`, `edit_file` (exakte eindeutige Änderung im Overlay), `check`, `run_tests`, `finish`. Begrenzte Schritte, Zeit, Lesen, Patchgröße, Tests. Geheimnisse, .git, Build-Verzeichnisse und Agent-Kontrolldateien werden gesperrt.
- `CONTINUUM_CODING_AGENT_LOOP=1` verbindet optional den neuen Provider mit dem bestehenden `coding-orchestrator.mjs`; ausgeschaltet bleibt die alte Pipeline.
- Letzter M8-Commit `29d7f76`: `createScratchTestRunner` in `runAgentCommand` verkabelt. Simulierter Verlauf: erster Patch fehlerhaft → `run_tests FAILED` → Korrektur in **derselben** Werkzeugrunde → `ALL PASSED` → unabhängige `repairSession VERIFIED`. `runner.close()` im `finally`, CLI-Label „Geprüft“ an `report.verified`.
- Zur Prüfung gehören Baseline rot, verifizierende Testkette, begrenzter Dateiscope, separater Scratch-Worktree, Belege, Besitzerreview. Allgemeiner generierter Code läuft **nur** mit isoliertem Executor; ohne Isolation nur die eng geprüfte numerische Grammatik.
- `scripts/continuum-agent-benchmark.mjs`: sechs echte fehlerhafte Mini-Repositories; alle Aufgaben vorvalidiert. Skript-Oracle löst drei numerische; drei allgemeine bleiben ohne Isolation BLOCKED. **Kein Schluss auf Qualität generativer lokaler Modellinferenz**.
- Der jetzige CLI-Weg führt noch **direkt** über `repairSession`; durchgehender CCC1-Journal-/Typed-Bridge-/App-Chat-Geräteweg ist als nächster Anschlusspunkt offen. Vorbereitet sind `ContinuumCodingTask`, `ContinuumTypedCodingBridge`, `ContinuumCcc1Bridge`, `ContinuumMobileCodingJob`, `ContinuumWorkerChatRouter`, `ContinuumMobileBuilderActivity`.
- Echte Brain-Backend-Anbindung verlangt separat einen laufenden lokalen OpenAI-kompatiblen llama.cpp-Server (Standard Loopback `127.0.0.1:8089`). Lokale Gemma-READY-Anzeige der App belegt **nicht**, dass diese CLI bereits eine Modellinferenz über die gleiche Runtime erreicht.

## 4. Gedächtnis, Semantik, neurobiologische Forschung
**M4:** reproduzierbares Host-Forschungslabor erreicht. **M5:** NICHT ERREICHT. M5b S 25/64, fern/fern 3/16; gegenüber untrainiert +9,4 Prozentpunkte, Holm-p 0,086, keine belastbare Überlegenheit. M5c zeigte repräsentationsbedingte Grenzen der CRE1-Rollenbindung (z. B. vertauschte Subjekt/Objekt-Rollen) und fehlenden Ferntransfer; neu vorregistriertes, datengetrenntes Experiment nötig. P2: Router A bleibt; EMA/CTRNN kein unabhängiger Erfolg. DG-SDR, sparse etc. ohne positive Belege nicht als produktive Verbesserung führen.

**M6 EWM-v2:** Originalfehler Subjekt/Ort/Adverb bei Ich-Aussagen, unzulässige Ersetzungen historischer Besuche durch spätere Zustände. Fixkandidat führt eine strengere Unterscheidung von Zustandsablösung, ausdrücklicher Ereigniskorrektur und Koexistenz ein. `current()` wurde gegen Wiederauftauchen bereits ersetzter Fakten ohne geladenen Nachfolger gehärtet. Grenzen: Alias, Pronomen, vorangestellte Nebensätze, Preisdomänen, echte Gültigkeitsintervalle, historische Wohnorte; R7-Besitzerentscheidung offen. Fremder, vorregistrierter Holdout (≥200 frei formulierte Paare, Precision ≥0,95, keine verlorenen Unbeteiligten-Erinnerungen), Gradle und Fold7-EWM2 anstehend. Neue Gültigkeitsversionierung und erst anschließend Predictive Coding/selektiver Reset gegen deterministische Referenz.

Neuronal inspirierte Konzepte (EMA-Detektoren, apikal/basales Gating, Predictive Coding, selektiver Reset, EWC/Replay/Fast Weights/assoziative Speicher) sind kontrollierte **Forschungsoptionen**, keine bereits eingerichteten produktiven Synapsen.

## 5. Android- und Updateweg: Render NICHT VERGESSEN
Der Nutzer hat einen eigenständigen **Render-QA-/Distributionsweg** bereits erarbeiten lassen. Render-Workspace `My Workspace` ist verbunden:
- `continuum-p0-updater-qa-20261029`, Service `srv-dbhk0r1srm7s738208n0`, Render-Deployment `dep-dbhklqs9v7es73b725r0` vom 29.10.2026: **live**, Build zum hash-identifizierten **unsignierten** Continuum-Lokal-Haupt-APK-Kandidaten. Das ist ein realer Render-Build-Pfad **ohne GitHub Actions**. Keine Freigabe als installierbares Besitzerupdate.
- `continuum-owner-v23-release-preflight`, Service `srv-dbf1tr2vcj2c73ehpfog`, Deploy `dep-dbhjok7b9u1s73f1miq0`: **live**. Manifest-/Freigabekanal ist derzeit ausdrücklich **fail-closed**, „kein signiertes Release verfügbar“.
- Render-Dienst ist weiterhin mit einer Repository-Branch als Quellquelle verknüpft. Das bedeutet **unabhängig von GitHub Actions**, nicht „Codeverwaltung/Git generell überflüssig“. Zur Distribution einer signierten Datei kann Render nach erfüllten Freigaben unabhängig vom manuellen GitHub-Download dienen.
- PR #236: Button, HTTPS-Manifest, signatur- und Paketgebundene SHA-256-Prüfung und Android PackageInstaller sind programmiert; vollständiges Compile-/Security-Review, originaler P12-Signierer, Versionscode >28 (voraussichtlich 29), zweifacher Besitzer-Update-Durchlauf und Datenerhalt-Abnahme fehlen.
- Keine Deinstallation der aktiven `at.heinz.deviceai.continuumlocal` und kein Austausch der Signatur. Render-Build-Erfolg ist nicht gleich signed release, Update-Button noch nicht auf dem Fold7 bestätigt.

## 6. Fold7-Hänger – nachgewiesene Code-Risiken
Stand EXP-20-Branch `0436f9ed`:
1. `AgentActivity.dashboardRefresh` alle 5 s, während App sichtbar; `ContinuumDashboardCollector.storage()` läuft jedes Mal rekursiv durch `filesDir` (Tiefe 6) und berechnet Verzeichnisgrößen ohne Cache. Das läuft außerhalb des UI-Threads und `dashboardCollecting` verhindert parallele Dashboard-Samplings, verursacht aber möglicherweise wiederkehrende I/O- und CPU-Last. Ursache der tatsächlichen Gerätehänger **NICHT** bewiesen.
2. Vier separat geplante Jobs: Autonomous (24 h), PublicStudy (24 h), DeviceTeamleader (1 h), NeuralPractice (15 min). Interne Ressourcenwächter bestehen; es fehlt eine gemeinsame globale Sperre gegen überlappende schwergewichtige Jobs. Einige Jobs haben lokale Synchronisierung/Atomics, aber keine übergreifende Job-Koordination.
3. `scripts/fold7-diagnose.sh` liest über ADB CPU, RAM, Systemlast, Thermal/Battery, Jobs, Frame-Jank, ANR- und Crash-Kopfzeilen; optional `--stop-continuum` macht einen **bewussten Force-Stop ohne Datenlöschung**. Reine Quell-/Syntaxprüfung, **nicht auf einem Gerät ausgeführt**. Zwei Phasen je `--minutes N`; Ergebnisse sind keine Diagnose ohne Messung. Verteilung von `top`/Prozesslisten kann installierte App-Namen offenbaren, daher vor Weitergabe prüfen.
4. Minimale Fixhypothesen: 5-Minuten-TTL für rekursive Speichergrößen bei weiterhin live abgefragtem `StatFs`; ein belegbar gemeinsames Heavy-Job-Koordinationsgate. Nur nach Rot/Grün-/Gradle-/Gerätevergleich übernehmen. Kein ungetesteter Massenumbau.

## 7. CI-Status und Nachweisgrenzen
`main` GitHub Actions `Continuum Hourly Watch` zeigte am 30.10.2026 wiederholte failures, darunter Run `41080603299` (watch failed, repair/publish skipped). In der Job-API waren **keine einzelnen Steps** verfügbar: Ursache (Runner/Quota/Konfiguration/Code) **unbekannt**, nicht erfinden. M8-/EWM-Zweige haben keine bestätigte automatische GitHub-CI-Abnahme. Grüne Host-Tests beruhen auf Opus-Protokollen, nicht auf erneut selbst ausgeführtem Node/JVM-Test in dieser Prüfung.

## 8. Revidierter PRODUKT-Meilensteinplan
| Stufe | aktueller Stand | nächstes hartes Gate |
|---|---|---|
| **M4 Forschungslabor** | ERREICHT (Host) | Verträge/Regressionen erhalten |
| **P0 Stabilität Fold7** | OFFEN; Code-Risiken & Diagnose vorhanden | ADB A/B 10+10 Minuten, echter Hänger-Zeitstempel, I/O/CPU/thermal; nur belegten Fix testen |
| **M5 robuste Semantik** | NICHT ERREICHT | M5c relationale Rollenbindung + unbekannte Formulierungen, registriertes VAL und fremder SEALED-Holdout, L ≥80 %, S ≥90 % |
| **M6 zeitliches Gedächtnis** | EWM-v2 PATCHKANDIDAT | unabhängiger 200-Paar-Holdout, 47/47 + 788/788 wiederholen, Android-Gradle und Fold7, vorsichtige R2–R7-/Bitemporal-Forschung |
| **M7 Fold7-APK/Updater** | Teilkette via Render und Draft #236 vorhanden; nicht released | exakt **originalsignierte** APK, Code>28, Signatur-/Hash-Manifest, zwei erfolgreiche In-App-Updates ohne Datenverlust |
| **M8 Coding-Agent/Worker** | interaktiver, intern testender HOST-PROTOTYP; simuliertes Brain | echte lokale Modellmessung (30+ fremde Aufgaben, Kotlin), isolierte allgemeine Tests, CCC1-Journal/Android-Chat, Besitzerreview |
| **M9 geprüfte Selbstverbesserung** | OFFEN, sichere Bausteine vorhanden | ≥3 real neu generierte und unabhängig verifizierte Besitzer-angenommene Verbesserungen ohne Rechteausweitung |

## 9. Ab jetzt priorisierte Umsetzungspakete
**P0-A Fold7-Kausaldiagnose:** Nutzer/PC/ADB mit Zustimmung Phase A normal vs B nach Force-Stop; keine Datenlöschung, kein unbelegter Fix. Frühe Option: zunächst reine Cache-/Scheduler-Tests auf Host. Vor einem intensiveren M8-Geräteworker Stabilität nachweisen.

**P0-B M7 über Render sichern:** vorhandene **live** Render-QA statt erneut GitHub Actions als einzige Build-Hoffnung; signierten Original-Besitzerweg prüfen. Authentizität, Certificate-Pinning/Signatur, Vertrauensanker, Download- und Rollback-Gates. Sicherer App-Button wie bei Lumo; keine manuelle APK-Suche als regulärer Update-Prozess.

**P1 M6 EWM-Fix unabhängig abnehmen:** PR/Branch nicht automatisch mergen; ChatGPT-6 verfasst disjunkte 200+ Paare und versiegelt; Entwickler prüft ohne Nachjustierung am SEALED; Besitzer-Review, dann Android-Integration.

**P1 M8 mit wirklicher Modellinferenz testen:** vorregistrierte Modell- und Runtime-Vergleiche 1.5B/3B/7B (nur nach Lizenz/RAM-Prüfung), 30+ Aufgaben inkl. reale Kotlin-Fälle; Vergleich Einmal-Provider vs Loop, Analyse Kosten/Failures. Sandbox ist ein echtes isoliertes Testsystem (PC/Docker, oder nach eigenständiger Sicherheitsevaluation anderer Umgebung). **Render static site allein ist keine allgemeine Laufzeit-Sandbox für untrusted code**; CI als Alternative nur mit nachgewiesener Isolation und vertrauenswürdigem Eigentümer-Gate.

**P2 M5c Rollen/Entitäten/Zeit-Semantik:** relationale Repräsentation, kontrastive Hard Negatives, unabhängige Daten, Prespecified Gates; nachgewiesene Kompetenz statt niedriger Trainingsloss.

**P2–P3 M8 Android-Integration:** bestehende Typed-Bridge/CCC1/Termux/ChatRouter verbinden, autorisierte Dateiscopes; keine zweite Coding-Engine; Gerätelauf nur nach Fold7-Sicherheit/Lastprüfung.

**P3 M9:** lokal wiederholbare Entwicklungszyklen ohne ungeprüfte Main-Änderungen, Akzeptanztest zuerst, verifiziertes Experience Replay; drei wirklich akzeptierte Verbesserungen.

## 10. Entscheidungen und Invarianten
- Owner entscheidet separat: EWM-R7-Semantik, vortrainiertes lokales Satz-Embedding-Modell Arm D, Einsatz eines isolierten Remote-Testsystems, Originalsignatur/Release, Aktivierung von schweren Geräte-Workern.
- Nichts Automatisches nach `main`, kein Force-Push, kein Eigen-Genehmigen, keine Modellcode-Ausführung ohne Isolation, keine Datenlöschung; `allowSelfReplication=false`, `allowHiddenPersistence=false`, `allowShutdownEvasion=false`.
- Dateien / Register nach jeder Änderung mit vollem SHA zurücklesen; **Modell-Simulation**, tatsächliche Modellinferenz, Labor-Host, echtes Fold7 und Owner-Signed-Release im Bericht getrennt ausweisen.
