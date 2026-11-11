# Continuum Omega – Test- und Reparaturmatrix (Fortsetzung 11.11.2026)

Aktueller Integrationszweig: `integrate/omega-apk54-20261111`, Draft #769.
Die folgende Ausgangsmatrix bleibt als Historie erhalten; aktueller Fortsetzungsstand unten.

Quelle der Beobachtungen: Issue #670 (Fold7-Prüfprotokoll, Code51 → Code53) und verlinkte Issues.
Installierte Referenz: originalsigniertes Code53 / 0.3.52, Quelle `050a57cb`.
Integrationszweig dieser Arbeit: `claude/omega-integration-20261111` (Basis #761 `8505c885`).

**Legende Integration:** INTEGRIERT = im Integrationszweig gemergt · GETESTET = Host-Test grün auf genanntem SHA ·
WIP = gesichert, nicht fertig · BLOCKIERT = Ursache außerhalb erreichbar · NOT_RUN = nicht ausgeführt.
**Fold7-Abnahme** ist für jede Zeile NOT_RUN, außer wo eine Besitzer-Geräteabnahme in #670 dokumentiert ist.
Ein Host-Test ersetzt nie die Geräteabnahme.

| TEST | BEOBACHTUNG (#670) | URSACHE | FIX-COMMIT | REGRESSIONSTEST | INTEGRATION | FOLD7-ABNAHME |
|---|---|---|---|---|---|---|
| T1/T5 (Code51) Gerätefähigkeiten | Generierung brach vor Teiltext ab; Wiederholung mit Kühlmodus vollständig | Ursache nicht bewiesen (Thermik/Laufzeit) | #669 `29aa8c57` (Fehlergrund durchreichen, Fold7-Default COOL) | 7 Hosttests laut #670 | in Code53-Basis | T5 GRÜN (1 Wiederholung); Dauerlast NOT_RUN |
| T6 Akku | echte Werte, aber Roh-`dumpsys` + `Broken pipe` | Shell-Pipeline statt Broadcast | `0252f63b` (#673-Linie, `DeviceBatteryProbe` Broadcast) | 5 Tests laut #670 | in Code53-Basis (#673 `9d7f0fc` ist Vorfahre) | NOT_RUN auf Code53; Rest-Pfad `CommandInterpreter.kt:70` nutzt noch `dumpsys battery` |
| T7 „keine neue Messung“ | erneute Messung statt Vorwert | Negation/Sitzungswert fehlte | `004f6909` (#673, `PreviousBatteryObservation`) | deterministische Tests laut #670 | in Code53-Basis | NOT_RUN auf Code53 |
| T17/T19/T20 ASR de-AT | „Mein“→„wenn“, „Continuum“→„contino“, „Básicamente“ | Android-Recognizer, 1 Kandidat | – | – | OFFEN | ROT (Code51), Code53 nicht erneut gemessen |
| T20 (Code51) Internetfreigabe | „Lokaler Modellfehler: Internetfreigabe geändert“ | Revision bei internem Web-Cleanup | #673 (lokale Inferenz unabhängig von Web-Epoch) | laut #670 4 Helper-Tests | in Code53-Basis | NOT_RUN auf Code53 |
| T23/T24 App-Suche mit Negation | Kontextfenster-Meldung statt `find_app`; „Welche davon…“ | negation-first-Guard in `OwnerAppLookupIntent` | #673 | 12/12 Host laut #670 | in Code53-Basis | NOT_RUN auf Code53 |
| #758 Gedächtnis blau/21/Keller | am Ende falsch trotz zuvor korrekter Werte | offen – Agent C analysiert | WIP `0f2fa3f7a` (Zahlwort-Parser, Agentenzweig C) | NOT_RUN | WIP (nicht integriert) | NOT_RUN |
| #759 Sprachauftrag Geräteanalyse | nur Speicherprobe, keine Arbeitsanimation | offen | – (Agent B ohne Commit) | NOT_RUN | OFFEN | NOT_RUN |
| #767 / Test 22 Bootstrap-Ablehnung | Diagnose + spätere Löschentscheidung als „nachgeahmte Rolle“ abgelehnt | vermutet: `BootstrapBrain.unsafeControlText` matcht mutierende Verben zu breit (#767) | – (Agent B ohne Commit) | NOT_RUN | OFFEN | NOT_RUN |
| Test 22 „Cache“→„Cage“ | Modell fragte nach „Cage“ | ASR-Fehlhörung | – | NOT_RUN | OFFEN | NOT_RUN |
| #765 / Test 20 „Öffne WhatsApp“ | einmal Kontextfenster-Meldung; nach Freigabe der Bedienungshilfe **PASS** (Test 20F) | Routing/Kontext, Gerätebefund ohne ASR-Text | – / – | NOT_RUN | OFFEN | 20F **PASS** (App-Wechsel bestätigt) |
| Kontextfenster bei einfachen Aufträgen | „…passen … nicht vollständig in das lokale Kontextfenster“ | offen (Kandidaten `LOCAL_FOLLOWUP_HISTORY_EXCEEDS_BUDGET` / `LOCAL_MANDATORY_PROMPT_EXCEEDS_BUDGET`) | – | NOT_RUN | OFFEN | NOT_RUN |
| #760 PDF „… und zeige mir jeden Arbeitsschritt“ | generisches `LOKALE KI · DENKEN` statt Dateiauswahl, danach Thermalstopp | Zweitimperativ verworfen | #761 (Präsentationszusatz) | `OwnerDocumentPresentationIntentTest` im Core-Scope grün (`6c538e2e`) | INTEGRIERT (#761 → #768) | NOT_RUN |
| #761 PDF-Szene Core | Lauf am 11.11. abgebrochen (11 PASS, 1 SKIP) | – | 3 Fixture-Reparaturen `120572483` | Core 98 Fälle: 94 PASS / 4 FAIL auf `6c538e2e`; nach Fix Klassen 3/3 + 6/6; Bestätigungslauf läuft | INTEGRIERT (#768) | NOT_RUN |
| #761 strikte PDF-Dekodierung | `NoSuchMethodError FileDescriptor.getOwnerId$()` | Host-JDK ohne Android-libcore-Methode | – (bewusst streng) | 0 PASS / 1 FAIL (Host) | BLOCKIERT (keine Android-Laufzeit hier) | NOT_RUN |
| Stimm-Sperre bei verzögerter Antwort | Node-Vertrag rot nach #761 | `speakNow`-Parameter, Sperre selbst erhalten | `120572483` | Vertrag 7/7 | INTEGRIERT (#768) | NOT_RUN |
| #569 Thermik | Android-Thermalguard-Stopp bei Gemma, Stufe 2, Kühlpack | Dauerinferenz; Konkurrenz Grafik/KI unbelegt | – | – | OFFEN | NOT_RUN (keine Last auf überhitztem Gerät) |
| Team Iris/Archiv/Atlas | Lauf 1 CANCELLED; Lauf 2 FAILED, nur ANALYZE-Quittung | Ursache offen (`ChiefBotTeam.report`, Worker, Thermik?) | – | – | OFFEN | ANALYZE PASS, REVIEW NOT_VERIFIED |
| Test 16 Maya | Stimme hörbar **PASS**; Kiefer abschnittsweise sehr schwach | PCM-RMS/Visem/Timing/Thermik – unbelegt (#764) | – | – | OFFEN | Stimme PASS, Lippen PARTIAL |
| Test 18/19 „gerade Kurve“ | Rückfrage statt Erklärung; mit Anweisung besser, Ende unklar | Modellverhalten | – | – | OFFEN | PARTIAL |
| Handyreiniger | nur eigene Diagnoseberichte bereinigbar | Umfang bewusst begrenzt | – | – | OFFEN (kein Dritt-App-Cleaner) | Vorschau NOT_RUN |
| Hands-Free | Wiederaufnahme nach Antwort, Stille-Timeout **PASS**; kein Barge-in (#641) | half-duplex | – | – | – | PASS (Code53) |
| 15 Cinema-Experimente | Holo-Audit 31 Callbacks / 33 Draws / 1,6 s | – | Matrix `420237ac4` (Agentenzweig A, `docs/architecture/CINEMA-EXPERIMENT-MATRIX-20261111.md`) | siehe Agent-A-Matrix | WIP (nicht integriert) | NOT_RUN (keine GPU-FPS) |
| Lichtverbindungen (Paket, Lese-Faden) | – (neu) | – | `be33c2c97` | Tests `cc2163f6b`/`d7c826c80` auf Agentenzweig A (Ergebnis dort dokumentiert; vom Integrator nicht nachgeprüft) | WIP in #768 | NOT_RUN |

## Testläufe dieser Sitzung (Host, exakte Quellen)

Rohzusammenfassung: `docs/evidence/omega-20261111/test-summary.json`.

| Lauf | Quelle | Ergebnis |
|---|---|---|
| #761 Core-Scope (18 Klassen) | `6c538e2ef` | **98 Fälle: 94 PASS, 4 FAIL, 0 SKIP**, vollständig (suite_end) |
| Fixture-Reparatur | `120572483` | `DocumentStagePageGeometryUiTest` 3/3, `ContinuumPdfSceneAcceptanceUiTest` 6/6 |
| #761 Core-Bestätigung | `120572483` | **UNVOLLSTÄNDIG**: 29 abgeschlossen (28 PASS, 1 SKIP), bei Sitzungsende gestoppt – kein Gesamtbeleg |
| #761 Visual-Scope (7 Klassen) | – | **NOT_RUN** |
| Node Backend | `6c538e2ef` | 64/64 PASS |
| Node-Verträge (252 Dateien) | `6c538e2ef` | 1634: 1584 PASS, 27 FAIL, 23 SKIP; 26 FAIL = Baseline (identisch auf Code53 `050a57cb`, Umgebung uid 0); 1 neu → behoben in `120572483` |
| Android-Lint (`:app:lintDebug`, NewApi) | – | **NOT_RUN** |
| Vollständige Android-Suite (~4 670) | – | **NOT_RUN** |
| Fold7 (alles) | – | **NOT_RUN** |


## Fortsetzung #769 — gemeinsamer Kandidat, Android-Abnahme offen

| Gegenstand | Quelle / Nachweis | Aktueller Stand |
|---|---|---|
| #758 Gesprächsgedächtnis | #770 + #776 `dd0eda082`; 83/83 JVM, 3 neue Activity-Fälle | integriert; Android NOT_RUN |
| #767/#759/#765 Routing | #772 `e20ca3a5`; 94/94 JVM, 7 neue Activity-Fälle; unabhängiger Review | integriert; Android NOT_RUN |
| EXP06/EXP11 | #771 `965ad4c5`; 13/13 JVM + 13/13 Node; 2 neue Canvas-Fälle | integriert; Android NOT_RUN |
| Masterprotokoll #747 | `c00326ced`, nur 145 Zeilen Dokumentation zusätzlich | integriert |
| Core-Ausgangslauf | unverändert `3cb8df27`, vollständiger Lauf mit 98 Fällen | 97 PASS / 1 strikter nativer PDF-Hostfehler / 0 SKIP |
| Node-Ausgangslauf | `3cb8df27`, uid1000; 1634 Fälle | 1612 PASS / 3 FAIL / 19 SKIP |
| Backend-Ausgangslauf | `3cb8df27` | 64 PASS / 0 FAIL / 0 SKIP |
| Drei Node-Prozessfälle isoliert | unverändert `3cb8df27` | 3 PASS; ursprüngliche Ursachen nicht bewiesen |
| Zusätzliche Fehlerdiagnostik | Assertions unverändert; Quittungen jetzt im Fehlertext | 2 PASS / 1 FAIL; echter TIMEOUT im 1000-ms-Scheduler-Fixture nachgewiesen |
| Vollständige Android-/Node-Prüfung, Visual, NewApi-Lint | auf gemeinsamem veröffentlichten Kandidaten geplant | NOT_RUN |
| P3 gemeinsame Filament-Szene | braucht auch Viewport-/Masken-/Ursprungsumbau | ZURÜCKGESTELLT, nicht implementiert |
| Allgemeine Kontextbudget-Anpassung | Direktrouten vermeiden Modellpfad in unterstützten Fällen | weiter OFFEN |
| APK54 / 0.3.53 | Paket/ABI/Build/Signatur/Device-Gates getrennt | GEPLANT, NICHT GEBAUT, NICHT FREIGEGEBEN |

Belege: `docs/evidence/omega-apk54-20261111/`. Kein Gerätebefund wird aus
Hosttests abgeleitet; strikter PDF-Test und alle Sicherheitsprüfungen bleiben bestehen.

### P2-Budgetnachtrag

#776 `dd0eda082` repariert den neu reproduzierten Ledger-Pflichtblock:
16.221 → 1.595 UTF-8-Bytes bei einer normalen objektbezogenen Aufgabe nach
24 Korrekturrunden für sechs Gegenstände. Aktuelle Werte und Unsicherheiten
bleiben im Prompt, ausgelassene Verlaufsdarstellung ist ausdrücklich benannt;
interne Historie und historische Antworten sind unverändert. Neue Regression
zuerst 2 PASS/3 FAIL, anschließend gesamte fokussierte Bestätigung 83/83.
Dritter Activity-Fall ist integriert, Android-Ausführung noch NOT_RUN.
Die Ursache der ursprünglichen kurzen Fold7-Kontextmeldung bleibt davon getrennt.

## #569 — thermische Doppelarbeit, PR #773

Quelle der thermischen Reparaturen: `1ba7b9458fd47184cedaed18e94e9aae1bd76c39`,
Tree `903bca2e718caabe2846bfa76493493cedf10dc3`. Der spätere Zielstand `c02930f10`
ist im Prüfzweig enthalten; alle kompilierten Thermikquellen und neun geprüfte
Leistungs-/Qualitätsvorgaben blieben dabei unverändert.

| Gegenstand | Beleg | Stand |
|---|---|---|
| Modellprüfung beim Laden | zwei vollständige SHA-256-Durchläufe → einer; jedes neue Laden weiter vollständig geprüft | behoben; #774 |
| Unveränderter Lernstatus | redundantes Dekodieren und 14 Holdout-Beispiele entfallen bei identischen Bytes/Korpora; Statistiken frisch | behoben; #777 |
| Lichtberechnungen | 1.526 → 70 Sinusaufrufe in diesem Helfer; 72 Draw-Traces bitgleich; statische Pfade wiederverwendet | behoben; #775 |
| Gemeinsamer Kotlin/JVM-Lauf | 94 PASS / 0 FAIL / 0 SKIP; tatsächliche Produktionsquellen mit Cache-Abhängigkeiten | HOST_VERIFIZIERT |
| Sechs Node-Vertragsdateien | 34 PASS / 0 FAIL / 0 SKIP; sechs negative Integritätsmutanten abgewiesen | HOST_VERIFIZIERT |
| Unabhängiger finaler Review | fünf geänderte Produktionsdateien; keine blockierenden Befunde | geprüft |
| Android-Prüfung und Lint | gemeinsamer lokaler Gradle-Lauf, bestehende exklusive Build-Sperre | RUNNING |
| Native Modellantwort, Wärme, Energie, Geschwindigkeit auf Fold7 | kein verfügbarer Gerätekanal; keine Abkühlung oder Leistungssteigerung behauptet | NOT_RUN |

Ziel bleibt #769. Keine Modell-, Thread-, Kontext-, Grafik- oder Schutzvorgabe wurde
durch den Hitzefix reduziert. Die drei nachgewiesenen Einsparungen beweisen noch
nicht die Hauptursache der starken Erwärmung während bereits geladener Inferenz.
Belege: `docs/evidence/thermal-root-20261111/SUMMARY.json`; Übergabe:
`docs/research/omega/THERMAL-ROOT-HANDOFF-20261111.md`.

### Vollständige Core-Bestätigung und Thermik-Übernahme

Der geerbte Core-Lauf ist vollständig abgeschlossen: **97 PASS / 1 FAIL / 0 SKIP**
auf sauberem `3cb8df27`. Alle 30 Dokumentfluss- und sechs PDF-Szenenfälle bestanden.
Die einzige verbleibende Niederlage ist `FileDescriptor.getOwnerId$()` im strikten
PDF-Dekoder der Host-Laufzeit. Vollständiges Journal, XML, Log und Quellidentität:
`docs/evidence/omega-apk54-20261111/baseline-core-summary.json`.

#773 `8decff576` ist in den gemeinsamen Kandidaten übernommen. Seine drei kleinen
Reparaturen entfernen doppelte Modellprüfung und unveränderte Status-/Lichtarbeit.
Die fünf Produktionsdateien und zugehörigen neuen Tests sind byte-identisch zum
unabhängig geprüften `1ba7b9458`; keine neue Blockade in den drei Gegenprüfungen.
JVM 94/94 und Node 34/34 sind übernommene quellgebundene Thermik-Belege.
Gemeinsame Android-/Visual-/Lint-Abnahme und echte Fold7-Thermik bleiben offen.
