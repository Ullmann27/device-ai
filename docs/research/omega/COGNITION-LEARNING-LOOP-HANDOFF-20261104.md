# Cognition, learning and bounded loop — 2026-11-04

## Auftrag und Stand

Drei begrenzte Fachaufgaben: bestehendes Gedächtnis, vorhandenes Selbstlernen und autonome Forschungszyklen untersuchen, echte Fehler reproduzieren und gezielt reparieren. **Abgeschlossenes Patchpaket für C1; gemeinsame Hostabnahme: 80/80 Kotlin-/JUnit-Fälle und separat 22/22 Node-Fälle.** [Draft PR #528](https://github.com/Ullmann27/device-ai/pull/528). Keine Behauptung einer Superintelligenz oder eines Nachbaus proprietärer Modellarchitekturen.

Die Verbesserung betrifft nachgewiesene Integritäts-, Quellenabdeckungs- und Wiederaufnahmelücken. Allgemeine Intelligenz, Antwortqualität oder Modellparität wurden nicht gemessen. Das Experimentregister erhält keine Statuspromotion. Die gesamte Android-App und ein Fold7 wurden in dieser Runde nicht abgenommen.

## Unveränderlicher Ausgangspunkt

- Repository: Ullmann27/device-ai, public; Konto und Schreibzugriff frisch geprüft.
- Gemeinsame Integration: C1, Draft #519, integration/continuum-c1-candidate-20261104.
- Remote-Basis: 6c34db7be4068b52de0cef20a0c9142a08db27c0.
- Lokale Basis: 38917560671b13795bf85d5a68d329658ba4eb7d.
- Vollständiger identischer Git-Baum: 687fe9a8267cb0cd8546acc7818f66fdbf3a1891.
- Eigener Zweig: research/continuum-cognition-learning-loop-20261104.

C1 bleibt alleiniger Einbauer der gemeinsamen Einstiegspunkte. #522 (Compiler-Datenschutz/Mapper), #523 (fertige Kontext-/Worker-/LM-Optimierungen), #525 (LM1-Forschungsgates) bleiben eigenständige bereits bekannte Beiträge. Diese Runde dupliziert sie nicht.

## Bestehende Architektur und Grenzen

Android-Dialog verwendet AiContextCompiler, LocalModelRuntime, BoundedAgentOrchestrator und ToolGate. Gedächtnis/Quellen, reflektierte Erfahrungen und Modellgewichte bleiben getrennt. Vorhandene Retriever, CRE1/SWB1 und Scheduler werden geprüft; kein zweites Gedächtnis, kein neuer Worker oder konkurrierender Scheduler.

Relevante Experimente: EXP-01, EXP-03, EXP-04, EXP-06, EXP-11, EXP-12. Diese Runde ändert deren Gesamtstatus nicht. Neue Qualitätsbehauptungen benötigen eigene Messung, getrennte Entwicklungs-/Prüffälle und negative Kontrollen.

Keine Änderung an main, Produktgewichten, App-Paket, Signatur, Updatekanal, Offline-Schalter oder Besitzerfreigaben. Kein kostenpflichtiger Dienst, keine GitHub Actions, keine Signierung oder Installation. Externe Forschungsinhalte sind Daten, keine Autorität.

## Umsetzung

1. memory: vorhandene Provenienz-, Zeit-, Widerspruchs- und Abrufpfade; kleinster reproduzierbarer Fehler.
2. self_learning: Erfahrung und Rückmeldungsbindung, Replay/Training-Gates; kleinster reproduzierbarer Fehler.
3. autonomy_loop: Fortschritt, Wiederholung, Unterbrechung und Wiederaufnahme; kleinster reproduzierbarer Fehler.
4. Koordinator: Quellenvergleich, gemeinsame Kompilierung, zwei Kopplungstests und zusätzliche Audit-Vollständigkeitsreparatur.

Genau drei Helfer, keine Unteragentenkaskade. Jeder nennt Originalquelle, Ausgangsfehler, unveränderte Regression, positive/negative Ergebnisse und Grenzen. Bestehende Maschinenlocks gelten für schwere Builds; geringer freier Speicher erlaubt zunächst nur kleine gezielte Hostläufe. Keine fremden Worktrees, Prozesse oder Reservierungen verändern.

### Gedächtnisbelege und vollständiges Audit

`ProofCarryingMemory.kt` und `SignedOwnerCorrectionJournal.kt`:

- Alte Trennzeichenkodierung erlaubte unterschiedliche Felder bzw. Beleglisten mit gleichen signierten Bytes. Neue v2-Payloads und Recordhashes verwenden getrennte Domänen, Feldlängen, Listenzahlen und unveränderte UTF-16-Codeeinheiten.
- Der echte alte Writer erzeugte eine archivierte v1-Fixture. v1-Lesen bleibt bytegleich; neue Zeilen sind v2, gemischte Ketten bleiben prüfbar. Alte Signaturen werden nicht neu erzeugt oder nachträglich gestärkt.
- Das unabhängige Gegenreview fand zusätzlich: Ein gültiges Journal meldete `audit(emptyList())` als `VERIFIED`. Das finale Audit meldet nun auch fehlende signierte Fakten und Widerrufe; Signaturkettenprüfung hat weiter Vorrang.
- Der Signer bleibt eine ausdrücklich übergebene, bestehende Core-Option. Hauptchat-Aufrufe mit Standardkonstruktor aktivieren damit kein verpflichtendes Signaturjournal.

### Tatsächlich verwendete Lernquellen

`ContinuumLearningCore.learnFromSources`:

- Eine erneut vorgelegte alte Quelle konnte aus den 48 jüngsten Replayeinträgen fallen, obwohl der Bericht sie als bearbeitet führte. Aktuelle persistierte, aktive Quellen erhalten jetzt Platz im gemessenen Trainingssatz; danach wird mit geeignetem bisherigen Replay ergänzt.
- Alle neuen Themen/Auszüge werden vor Ledgerwrites auf gültige Encoder-Eingaben geprüft. Doppelte aktuelle Inhalte verbrauchen keine zusätzlichen Plätze. Widerrufene oder nicht vollständig persistierte Eingaben ergeben keine erfolgreiche Runde.
- Höchstens 17 unterschiedliche aktuelle Erfahrungen pro Aufruf: Ihre IDs passen vollständig in den bestehenden 500-Zeichen-Auditverweis. 18 oder mehr werden vor Ledger-/Modellwrites abgelehnt. Eine Runde erzeugt weiter nur einen Auditdatensatz; keine künstliche Vervielfachung von Runden oder Schritten.
- Vorhandene Replay-/Trainingsgrenzen von 48/96, Holdouts, Owner-Schutz und Modellpromotion-Gates bleiben erhalten. Der Bericht zählt verwendbare Quellpaare.

### Wiederaufnahme unterbrochener öffentlicher Runden

`ContinuumSourceNovelty.kt` und zwei Aufrufzeilen in `ContinuumPublicStudyScheduler.kt`:

- Ein archivierter oder im Ledger beobachteter Auszug wurde zuvor schon als bekannt übersprungen, obwohl seine Runde abgebrochen war. Das vorhandene Journal erhält vor Archiv/Notebook/Training einen offenen `?SHA`-Eintrag.
- Offene Einträge übersteuern die Archiv-/Ledger-Deduplizierung. Erst eine vollständig evaluierte Runde entfernt ihren Marker atomar; eine fachlich abgelehnte, aber vollständig evaluierte Runde ist ebenfalls abgeschlossen. Abbruch und Zeitbudgetüberschreitung bleiben wiederholbar.
- Bestehende nackte SHA-Zeilen bleiben Abschlussbelege. 120 Gesamtzeilen und 8192 Byte Lesegrenze bleiben erhalten; offene Marker werden nicht wegkompaktiert. Volle Kapazität und Schreibfehler brechen vor Folgeeffekten ab.
- Kein neuer Scheduler, keine zweite Queue und keine zusätzliche Auswahl-Lease. Historische Archive ohne Startmarker werden nicht rückwirkend umgedeutet. Der bisherige JVM-Rückgabetyp `Path` von `markCompleted` bleibt erhalten.

## Tatsächliche Messung

AGENTS.md, Experimentvertrag/Register, Issue #41, aktuelles C1-Board/-Handoff und fachliche Pflichtübergaben werden vor Änderungen gelesen. Öffentliche Primärquellen dienen nur als begründete Forschungsinspiration; ihre Modellleistungen gelten nicht für Continuum.

| Abgegrenzter Lauf | Ausgangsfehler | Ergebnis nach Änderung |
| --- | --- | --- |
| Gedächtniskodierung | 18 Fälle, 10 echte Assertionsfehler | 28/28 auf dem damaligen v2-Snapshot |
| Quellen-Replay, final eingefrorene 17er-Vertragsfälle | 8 Fälle, 5 echte Fehler | 26/26 einschließlich vorhandener Core-/Integrationstests |
| Offene Quellenrunden | 5 Fälle, 2 echte Fehler im separaten JVM-Neustart | 14/14 auf dem ersten Snapshot; finale 15 Fälle im gemeinsamen Lauf |
| Gemeinsamer Lauf vor Audit-Vollständigkeitsfix | 80 Fälle, genau 5 Fehler wegen fehlender signierter IDs | **80/80**, Compiler/JUnit Exit 0; alle elf Testdateien unverändert |
| Node-Forschungs-/Fehlergates | Baseline 22/22 | **22/22**, Exit 0, keine übersprungenen Fälle |

Diese überlappenden Gruppen werden nicht addiert. Die früheren Quellen-Replay-Entwürfe mit einer 48er-Eingabeannahme und deren negative Ergebnisse bleiben als historische Entwurfsstände erhalten; nur die eingefrorenen 17er-Vertragsfälle bilden dessen RED/GREEN-Vergleich. Belegte Bausperren wurden als `LOCK_BUSY` erfasst, ohne gestarteten Compiler als Testlauf zu zählen.

Der finale gemeinsame Lauf kompiliert neun echte Produktionsdateien und elf Testdateien mit Kotlin 2.4/JDK 17 frisch. Transitive Abhängigkeiten stammen aus einem eingefrorenen tatsächlichen C1-Klassenarchiv, SHA-256 `44e45d4c047a1cd65a4ca8b8faccae498a559b09cb4bd5ae99a04db4ac94048d`. Kein Ersatz-Core, keine Ersatz-Ledger-/Encoderlogik. Quellen-, Ressourcen- und Abhängigkeitshashes blieben während des Laufs unverändert. Die Manifest-HEADs benennen den Git-Ausgangspunkt vor dem Commit; maßgeblich für den ausgeführten Arbeitsstand sind die vollständigen Quellenhashes.

Die zwei neuen Kopplungstests prüfen echte temporäre Komponenten gemeinsam: signierten Besitzerfakt bewahren, Quelle vormerken/archivieren, tatsächliches Lernen abbrechen, Core neu laden, Quelle erneut trainieren, Abschluss deduplizieren; außerdem eine zu große Runde ohne partielle Ledger-/Gewichtsänderung offen halten. Die separaten Novelty-Tests prüfen zusätzlich echte neue JVM-Prozesse. Ein neuer Core im Kopplungstest ist kein Android-Prozessneustartnachweis.

Der Prüfstand führte echte begrenzte Subword-Trainingsschritte auf synthetischen Daten in temporären Verzeichnissen aus. Produktive Gewichte und Nutzerdaten wurden nicht verwendet oder verändert. Keine Frontier-Modellinferenz, keine allgemeine Qualitätssteigerungsmessung und kein Dauerbetrieb auf Hardware.

### Belege und Wiederholung

- Finale Quelle/Ergebnisse: `docs/evidence/cognition-learning-loop-20261104/coordinator/summary.json`.
- Unveränderte negative und positive Gesamtläufe: `coordinator/combined-red-01` und `coordinator/combined-green-01`, jeweils mit Befehlen, Source-Snapshot, Compiler-/JUnit-Rohlog und Hashmanifest.
- Node: `coordinator/baseline-node.*` und `coordinator/final-node.*`.
- Einzelbelege: Unterordner `memory`, `self-learning`, `autonomy`; die historischen Statusdateien bleiben an ihre eigenen Quellen gebunden. Der finale gemeinsame Nachweis ersetzt insbesondere das frühere `autonomy.current.status=NOT_RUN` nur für die exakt gleich gehashten aktuellen Dateien.
- Fachübergaben: `MEMORY-COGNITION-LOOP-20261104.md`, `SELF-LEARNING-COGNITION-LOOP-20261104.md`, `AUTONOMY-COGNITION-LOOP-20261104.md`.
- Unabhängige Quellreviews: `COGNITION-MEMORY-REVIEW-BY-LEARNING-20261104.md`, `COGNITION-LEARNING-REVIEW-BY-LOOP-20261104.md`, `COGNITION-LOOP-REVIEW-BY-MEMORY-20261104.md`. Der darin entdeckte Audit-Blindfleck ist anschließend separat RED/GREEN repariert.
- Forschungsgrundlage und Nicht-Paritätsgrenze: `COGNITION-PRIMARY-SOURCES-20261104.md`.

Der ausgeführte begrenzte Hostbefehl steht vollständig in `coordinator/combined-green-01/commands.json`; Orchestrierung: `python tools/cognition-learning-loop/run_combined.py <neues-Ausgabeverzeichnis> --base-jar <nachgewiesenes-C1-Klassenarchiv>`. Der Runner ist bewusst an diesen Snapshot und die dokumentierten Maschinenlocks gebunden. Das Abhängigkeitsarchiv ist kein veröffentlichtes Ersatzprodukt; C1 muss die echte Android-Kompilierung und die bestehenden vollständigen Gates auf seinem Integrationsstand durchführen.

## Verbleibende Grenzen

- Keine Android-/Keystore-/Fold7-/APK-Abnahme, kein App-Langzeitbetrieb, keine vollständig neu gelaufene Repository-Suite. Die gezielte Scheduler-Kompilierung mit Android.jar ist kein JobService-Lauf.
- Vorhandene v1-Signaturen behalten ihre historische Mehrdeutigkeitsgrenze. Reines Zurückrollen auf einen v1-only-Reader ist nach neuen v2-Zeilen nicht kompatibel; keine Journale löschen oder umschreiben.
- Marker werden auf dem öffentlichen Startpfad gesetzt. Ein erster Offline-Start ohne Marker wird damit nicht nachträglich erfasst. Eine ältere App könnte offene Marker ignorieren; Rücknahme benötigt einen kompatiblen Reader.
- Vorhandener Core-Lock serialisiert Training, ersetzt aber keine exklusive Vorauswahl zwischen Schedulern. `fsync` der Datei und atomarer Move belegen keinen vollständigen Stromausfallschutz des Dateisystems.
- Frühere historische Quellen mit untokenisierbarem Thema können weiterhin die Replayevaluation abbrechen. Neue Eingangsvalidierung verhindert Neufälle, repariert aber keine Altdaten.
- Eine Bewertung kann Gewichte ausdrücklich verwerfen. Weder `completedEvaluation` noch ein Archivbeleg bedeutet automatisch akzeptiertes Lernen oder Wahrheit einer Quelle.

## Fortsetzung und Rücknahme

Vor Integration tatsächlichen Remote-HEAD lesen. Bei der Abschlussprüfung waren alle fünf betroffenen Produktionsdateien in C1 `4b6c38902c1ebb93598dd125f5ea0bcc3cddabf8` bytegleich zur eingefrorenen Ausgangsbasis; Beleg: `coordinator/c1-target-comparison.json`. Dies ist eine Konfliktprüfung dieser fünf Dateien, kein Test des inzwischen weiterentwickelten vollständigen C1-Baums.

C1 übernimmt das Patchpaket in eigener Einbauhoheit und führt seine vollständigen Gates aus. Gemeinsame Einstiegspunkte, `main`, Paket/Signatur und Veröffentlichung bleiben außerhalb dieser Runde. Bei roter Abnahme keine Promotion; negative Evidenz erhalten. Rücknahme nur der eigenen Änderungen mit Beachtung der Journalversionen, nie fremde Zweige zurücksetzen.

