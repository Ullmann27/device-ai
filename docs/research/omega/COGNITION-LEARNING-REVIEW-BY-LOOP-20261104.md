# Unabhängiger Nur-Lese-Review: Selbstlernen

Reviewer: AUTONOME SCHLEIFE. Auftrag: Root, 2026-11-04.
Keine Produktionsänderung, kein eigener JVM-Lauf und kein Unteragent für diesen Review.

**Ergebnis: kein blockierender Befund zum vorliegenden Slice.** Die aktuellen Quell- und
Testdateien stimmen mit dem dokumentierten 26/26-GREEN überein. Die gemeinsame Integration
der drei Slices und ein Android-Gerätelauf werden hier nicht bestätigt.

## Geprüfter Stand

- `app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt`:
  SHA256 `3883330655a6f3750da418f758ac39d6c3542a8a34dc9788f6bec749c259e2cf`.
- `app/src/test/java/at/heinz/deviceai/learning/ContinuumSourceReplayCoverageTest.kt`:
  SHA256 `581eb469af8353dfc1fc563aff95ca8f8c96a468605593dcc4c4f5a7f60c1539`.
- `self-learning/summary.json`, V2-RED/GREEN-Manifeste, Compile-/JUnit-Rohlogs,
  Statusdateien und Prüfrunner unter `docs/evidence/cognition-learning-loop-20261104/`.
- Ergänzend: tatsächlicher `appendAudit`-Pfad, SOURCE_TEXT-/Audit-Modell und
  Tokenisierung in `TrainableSubwordEncoder.kt`.

## Fachliche Prüfung

**Aktuelle Quellen werden aufgenommen.** Angeforderte Einträge werden nach ihrer stabilen,
normalisierten Experience-ID dedupliziert. Der komplette angeforderte Satz muss tatsächlich
im Ledger aktiv sein; REVOKED/REJECTED erfüllen diese Bedingung nicht. Vorhandene Replay-Duplikate
werden entfernt, die angeforderten aktiven Einträge kommen an das Ende der höchstens 48 Beispiele.
Damit kann ein älterer, erneut angeforderter Eintrag nicht allein wegen 48 neuerer Beobachtungen
aus der Runde fallen. Auch das abschließende 96-Beispiele-Limit erhält diese letzten Einträge.

Der entsprechende Regressionstest vergleicht den tatsächlich gemessenen Trainingsverlust mit
einer separat zusammengesetzten erwarteten Beispielsammlung. Im V2-RED meldete der alte Code
bereits `completed=true`, 550 Schritte und eine behauptete Quellen-ID, verwendete aber die falsche
Sammlung: erwarteter Verlust `0.6927604443382278`, tatsächlich `0.6927586491041351`.
Im GREEN besteht dieselbe Assertion. Das belegt die Zuordnung der aktuellen Quelle, keinen
allgemeinen Modellqualitätsgewinn.

**Die 17er-Grenze steht vor den Quellenwrites.** Experience-IDs haben 27 Zeichen. Der vorhandene
Audit speichert sie durch Kommas getrennt in `sourceRef`, das höchstens 500 Zeichen erlaubt:
17 IDs benötigen 475 Zeichen, 18 bereits 503. Der neue Abbruch bei mehr als 17 eindeutigen IDs
liegt vor `store.append`, Trainingsphasen und Modelländerung. Die Tests decken 18/48/49 eindeutige
Quellen sowie 49 normalisierte Duplikate ab. Themenvalidierung und Tokenisierbarkeit werden
ebenfalls vor den Quellenwrites geprüft.

**Eine Runde, ein Auditpfad.** Es bleibt bei einem `trainSemantic`-Aufruf und einem
`appendAudit`-Aufruf pro ausgeführter Runde; keine verborgene Aufteilung in zusätzliche Runden.
Der 17er-Test prüft einen einzigen Audit-Eintrag und alle 17 unverlorenen Quellenreferenzen.
Er ist absichtlich abgebrochen und belegt keinen abgeschlossenen 17-Quellen-Modellaudit.

**Qualitäts- und Ressourcengates bleiben erhalten.** Unverändert sind 550 Schritte,
2500 ms Trainingslimit, Cancellation, `stoppedEarly`, bestehender Intent-/Besitzer-Retentionstest,
Sinken des Trainingsverlusts sowie Treffer- und Verlustgrenzen des Semantik-Holdouts.
`saveModel` bleibt von `accepted` abhängig. `completedEvaluation` bleibt an eine ununterbrochene,
nicht vorzeitig beendete Runde gebunden. `replayExamples` zählt nun tatsächlich verwendete Paare.

## Evidenz und verbleibende Grenze

V2-RED: Compile Exit 0, acht Regressionen, fünf Fehler, JUnit Exit 1.
V2-GREEN: Compile Exit 0, 26 Tests bestanden, JUnit Exit 0. Test-SHA im RED und GREEN identisch;
aktuelle Core-/Test-/Runner-SHA stimmen mit Manifest und Summary überein. Vorhandene
Compilerwarnungen betreffen unveränderte sichere Aufrufe an anderen Core-Stellen.
Abhängigkeiten wurden in den aufgezeichneten Läufen als unverändert geprüft.

Nicht blockierender Altbestand-Hinweis: Der historische Replay-Filter prüft Themenlänge,
jedoch nicht deren Tokenisierbarkeit. Ein bereits früher gespeichertes Thema wie `!!!` hat
drei Zeichen und kann weiterhin die Evaluation abbrechen, weil die Tokenisierung leer wird.
Die neue Eingangsvalidierung verhindert neue solche Einträge; eine historische Bereinigung
ist nicht Teil dieses Slice. Dieser Restfall wurde hier nur aus dem Code abgeleitet,
nicht zusätzlich ausgeführt. Er erzeugt keinen falschen erfolgreichen Abschluss.

Die vorhandenen Tests verwenden synthetische temporäre Daten und tatsächliche Kotlin-Klassen.
Kein Fold7-Nachweis, keine produktive Gewichtsänderung und keine Qualitätsfreigabe aus diesem Review.
Root übernimmt die separate gemeinsame Abnahme des eingefrorenen integrierten Standes.
