# Argon 4 – zwei Drive-PDFs, zwölf Experimente und umgesetzte P0-Maßnahmen
Stand 03.11.2026. Gilt nur für Device AI Continuum.

## Herkunft und doppelter Upload
Google Drive Ordner `Continuum_backupDatein` enthält zwei neue PDFs, jeweils 353.677 Bytes:
- [CONTINUUM OMEGA – Fehlende JARVIS-Bausteine und Experimentier-Bauplan](https://drive.google.com/file/d/12O_xyjlhL6SEkZYj9qtXND112CvaXsfe/view?usp=drivesdk)
- [ArgonCONTINUUM OMEGA – Fehlende JARVIS-Bausteine und Experimentier-Bauplan](https://drive.google.com/file/d/1njHkGqauBTzCDBM5w4Apgtv-m8Znx4Fl/view?usp=drivesdk)

Google Drive-Leseabgleich: **identischer extrahierter Text (28.118 Zeichen)**, gleiche Dateigröße. Dies bestätigt Textgleichheit, nicht zwingend bitidentische PDF-Dateien. Das Dossier stellt acht Architekturbausteine und EXP-01 bis EXP-12 vor. Externe Leistungsangaben/Quellen sind Forschungsbehauptungen, keine Fold7-Benchmarks.

## Frischer Repository-Abgleich
- PR #332 (`27bdfcc...`) ist **merged**; der im Dossier geforderte Prompt-Leakage-/Erklärungsrouting-Fix ist daher nicht schlicht „nicht integriert“. Android-Regressionen offen.
- PR #331 (`cc9a56b...`) ist **open/draft**, 121 veränderte Dateien, spezialisierte Shadow-/Holdout-Ergebnisse im PR-Text: 37/37 regelbasiert und 36/37 geprüfter Lernansatz. Forschungsbericht ≠ Haupt-Chat-Abnahme; keinerlei pauschaler Merge.
- PR #365 korrigiert initiales Speicherverb-Fehlrouting und schützt notwendigen Mehrturn-Prompt. PR #366 hängt daran und hat private-textfreie Kontextdiagnose. Beide Draft/ohne neue Fold7-Version.
- Code39 (0.3.38) ist installiert; `gemma4-e4b-it` erzeugte auf Fold7 nachweislich im Direktest eine sinnvolle Offline-Ausgabe (21.389 ms). Erste Einzelringfrage korrekt, Mehrturn fehlgeschlagen. Antwortzeitprognosen unter 100 ms aus Argons PDF sind **nicht verifiziert**.

## Eingebaute Experimente auf diesem abhängigen Forschungsbranch
**EXP-01, bewusst nur task-scoped und experimentell:** `TaskScopedRingStateProjector.kt` projiziert bei explizit hypothetischen Ring-Kisten-Aufgaben (Trigger `Beispiel`/`Testaufgabe`/`Denkaufgabe` im vorherigen Owner-Turn) ursprüngliche, aktuelle und `ab morgen` vorgesehene Werte. Liest ausschließlich die max. vier vollständigen vorherigen Nutzeraussagen derselben Sitzung + aktuellen Turn. Kein Modelltext, kein MemoryStore, kein Netz/Tool/Datei-/Datenbankzugriff, keine persistierte Besitzerfakten. Ist `requiresPriorDialogue` zutreffend, wird der als **unbestätigtes, flüchtiges Beispiel** markierte kompakte Zustand in den existierenden `AgentActivity.runLocal`-Prompt-*Pflichtteil* aufgenommen; alle anderen Domänen bleiben unverändert. Nicht allgemeines Langzeitgedächtnis, keine Garantie, dass Gemma korrekt antwortet. Negierte, unvollständige, zu lange und gemischte Zukunftsformulierungen liefern **keine** projizierten Fakten.
**EXP-05, read-only Negation:** `BootstrapBrain.isStorageReadRequest` zerlegt explizite `sondern`-Kontraste und Satzzeichen, verwirft Speicherleseabsichten in negierten Klauseln und hält eine eigenständige positive Anforderung in einer getrennten Klausel aufrecht. Kein neue Berechtigungen, keine Speicher-Schreiboperationen.

**Tests im Repository:** `TaskScopedRingStateProjectorTest` enthält 7 JUnit-Fälle (Ursprungswert/Korrektur/Historie, Zukunft, No-Save, reale statt hypothetische Angaben, Eingabegrenzen, Negation/Mehrdeutigkeit). `BootstrapBrainTest` erhielt weitere 9 Kontrastfälle.
**Eigene ausgeführte isolierte Kotlin/JVM-Checks:** 8/8 Zustands-Assertions sowie 16/16 lexikalische Negations-/Storage-Assertions bestanden, JDK/kotlinc im isolierten Test-Harness. Diese isolierten Checks kompilieren **nicht** die komplette Android-App und führen die neu hinzugefügten JUnit-Klassen **nicht** aus.

## Entscheidungen zu allen zwölf EXPs
| Experiment | Entscheidung / Grund |
|---|---|
| 01 Bitemporal State Header | Begrenzt auf flüchtige Ringaufgabe eingebaut; allgemeiner Claim-Ledger später nur nach Provenienz-/No-Save-Prüfung |
| 02 PR331 Spezialisten | Shadow-Audit und echte Holdout-Tests; 121-Dateien-PR nicht blind integrieren |
| 03 Prompt-Leakage-Firewall | Bestehende merged PR332 zuerst auf Code39/aktuellen Branch verifizieren, keine zweite Firewall |
| 04 RIS DOM Extraktion | Fachprojekt separat, tatsächliche Normtexte/Tabellen/RIS-Nachweise erforderlich |
| 05 Clause Negation | Kleiner, über Branch abgesicherter P0-Patch; zusätzliche Regressionen |
| 06 CCC1 Micro Slice | Nach P0/QA im bestehenden `CCC1`/ToolGate/Repair-System, kein zweiter Shell-Worker |
| 07 Streaming Audio/Barge-In | Kurzfristig hohe Nutzerwirkung, aber erst ASR/TTS-Lizenz-/Android-Laufzeit und Abbruch-Quittung |
| 08 Shizuku Pre/Post | Systemstatus verbunden; UNKNOWN-Lock muss persistent, atomar und unabhängig verifiziert sein; PDF-RAM-Set nicht zulässig |
| 09 Reuse Cache | Shadow mit echten AST/Diff/Hash-Stufen, keine erfundenen 50%-Energiegewinne |
| 10 Proactive Watchdog | Später, nur mit Android-Batterie-/Thermikrechten, Cooldown, Lifecycle und Einwilligung |
| 11 „Zero Knowledge Masking“ | Platzhalter ersetzen kein kryptografisches ZK oder Transportverschlüsselung; Privatsphäre auf Host/PC prüfen |
| 12 Gaze Avatar | Später opt-in, keine Identitätserkennung; Kamera-Rohbilder nicht aufzeichnen/versenden |

## Beleg- und Releasevertrag
Sämtliche Veränderungen auf `research/continuum-omega-task-scoped-temporal-20261103` auf Basis PR366 (damit auf PR365); `main`, Originalsignierung, Publisher und Besitzerdateien unberührt.
Vor Veröffentlichung: vollständige exakte Android-/JUnit-/Node-/Lint-/Review-Abnahme. Anschließend, nur nach Freigabe, Originalpaket/-signatur und versionCode>=40. Fold7-Gerätetest weiter offline (Webzugriff `Nicht zulassen`), sobald tatsächlich eine geprüfte APK installiert ist. **Kein automatischer Build-/Signatur-Erfolg aus diesem Dokument abzuleiten.**

## Offene Frage an Argon
Wie würdest du den hier experimentell hart begrenzten Projektor zu einem **domänenneutralen, versionierten, flüchtigen Claim-Graphen** mit korrekten Negationen, Ellipsen, Zeitzonen, Zukunft/Unsicherheit und synthetischen 1.000-Tage-Tests erweitern, ohne zweite DB, ohne aus hypothetischen Nutzerbeispielen echte Eigentümerfakten zu machen? Liefere zuerst fünf Falsifikations-Tests und nur anschließend produktionstauglichen Kotlin-Code. Keine proprietären Gewichts-/Hardwarebehauptungen.
